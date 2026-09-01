package dev.ederfmatos.batterystats.data.sampling

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.IBinder
import android.util.Log
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.db.toEntity
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Amostra a bateria em intervalo fixo e grava tudo no banco.
 *
 * Precisa ser foreground service: em background o Android congela o processo e a amostragem para
 * exatamente nos períodos mais interessantes (tela desligada, madrugada). O `specialUse` é o tipo
 * honesto aqui — monitorar a própria bateria não é dataSync nem health nem location.
 *
 * Duas coisas disparam uma amostra: o tick periódico e uma transição da tela. A segunda existe
 * porque ligar ou apagar a tela muda o consumo em uma ordem de grandeza, e amostrar só no tick
 * deixaria o instante da transição perdido no meio de uma janela.
 */
class SamplingService : Service() {

    private val job = SupervisorJob()
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Loop de amostragem falhou", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Default + job + errorHandler)

    /** Capacidade 1 com CONFLATED: duas transições de tela seguidas viram uma amostra só. */
    private val immediateSampleRequests = Channel<Unit>(Channel.CONFLATED)

    private lateinit var notifications: SamplingNotifications
    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = SamplingNotifications(this)
        notifications.ensureChannel()

        val container = application.appContainer
        container.screenStateTracker.onScreenChanged = { screenOn ->
            if (!screenOn) container.foregroundAppResolver.onScreenOff()
            immediateSampleRequests.trySend(Unit)
        }
        container.screenStateTracker.start()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(getString(R.string.notification_starting))
        if (samplingJob == null) samplingJob = scope.launch { sampleLoop() }
        return START_STICKY
    }

    private fun startForegroundCompat(text: String) {
        val notification = notifications.build(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    SamplingNotifications.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(SamplingNotifications.NOTIFICATION_ID, notification)
            }
        } catch (e: IllegalStateException) {
            // Acontece quando o sistema recusa a promoção a foreground (app em background restrito).
            Log.e(TAG, "Não foi possível promover o serviço a foreground", e)
            stopSelf()
        }
    }

    private suspend fun sampleLoop() {
        val container = application.appContainer
        val dao = container.database.batteryDao()
        val settings = container.settingsRepository.settings.first()

        container.foregroundAppResolver.restoreState(
            lastEventMs = settings.lastProcessedEventMs,
            lastPackage = settings.lastKnownForegroundPackage,
        )

        var afterGap = recordStartupGap(settings.samplingInterval.millis)

        while (scope.isActive) {
            val current = container.settingsRepository.settings.first()
            sampleOnce(dao, afterGap)
            afterGap = false

            // O que vier primeiro: o tick ou uma transição de tela.
            withTimeoutOrNull(current.samplingInterval.millis) {
                immediateSampleRequests.receive()
            }
        }
    }

    /**
     * Grava o buraco entre a última amostra conhecida e agora, se houver.
     * @return true quando houve buraco, para que a primeira amostra nova não herde o app em
     * primeiro plano de antes do buraco.
     */
    private suspend fun recordStartupGap(samplingIntervalMs: Long): Boolean {
        val container = application.appContainer
        val dao = container.database.batteryDao()
        return try {
            val lastSampleMs = dao.latestSampleOnce()?.timestampMs
            val gap = GapDetector(this).detect(
                lastSampleMs = lastSampleMs,
                nowMs = System.currentTimeMillis(),
                samplingIntervalMs = samplingIntervalMs,
                samplingWasEnabled = true,
            ) ?: return false

            dao.insertGap(gap.toEntity())
            Log.i(TAG, "Buraco de ${gap.durationMs} ms registrado como ${gap.reason}")
            true
        } catch (e: SQLiteException) {
            Log.e(TAG, "Falha ao registrar o buraco de medição", e)
            false
        }
    }

    private suspend fun sampleOnce(
        dao: dev.ederfmatos.batterystats.data.db.BatteryDao,
        afterGap: Boolean,
    ) {
        val container = application.appContainer
        val screenOn = container.screenStateTracker.isScreenOn
        val nowMs = System.currentTimeMillis()

        val foreground = container.foregroundAppResolver.resolveCurrent(
            nowMs = nowMs,
            screenOn = screenOn,
            afterGap = afterGap,
        )

        val snapshot = container.batteryReader.read()?.copy(
            screenOn = screenOn,
            foregroundPackage = foreground.packageName,
            foregroundReason = foreground.reason,
        )

        if (snapshot == null) {
            Log.w(TAG, "Amostra descartada: leitura da bateria indisponível")
            return
        }

        try {
            dao.insert(snapshot.toEntity())
        } catch (e: SQLiteException) {
            Log.e(TAG, "Falha ao gravar amostra em ${snapshot.timestampMs}", e)
            return
        }

        container.settingsRepository.setForegroundWatermark(
            lastEventMs = container.foregroundAppResolver.lastProcessedEventMs(),
            lastPackage = container.foregroundAppResolver.lastKnownPackage(),
        )
        container.statsRepository.refreshQuantizationStep()
        updateNotification(snapshot)
    }

    private suspend fun updateNotification(snapshot: BatterySnapshot) {
        val container = application.appContainer
        val text = try {
            container.statsRepository.notificationSummary(snapshot)
        } catch (e: SQLiteException) {
            Log.e(TAG, "Falha ao calcular o resumo da notificação", e)
            getString(R.string.notification_level_only, snapshot.levelPct)
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(SamplingNotifications.NOTIFICATION_ID, notifications.build(text))
    }

    override fun onDestroy() {
        samplingJob = null
        _isRunning.value = false
        job.cancel()
        immediateSampleRequests.close()
        application.appContainer.screenStateTracker.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SamplingService"
        const val ACTION_STOP = "dev.ederfmatos.batterystats.STOP_SAMPLING"

        private val _isRunning = MutableStateFlow(false)

        /** Estado observável do serviço. A UI precisa saber se a medição está de pé. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SamplingService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Sistema recusou iniciar o serviço de amostragem", e)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SamplingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
