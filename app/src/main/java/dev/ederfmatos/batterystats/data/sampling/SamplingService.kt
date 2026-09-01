package dev.ederfmatos.batterystats.data.sampling

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.db.toEntity
import dev.ederfmatos.batterystats.domain.drain.DrainWindow
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Amostra a bateria em intervalo fixo e grava tudo no banco.
 *
 * Precisa ser foreground service: em background o Android congela o processo e a amostragem para
 * exatamente nos períodos mais interessantes (tela desligada, madrugada). O `specialUse` é o tipo
 * honesto aqui — monitorar a própria bateria não é dataSync nem health nem location.
 */
class SamplingService : Service() {

    private val job = SupervisorJob()
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Loop de amostragem falhou", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Default + job + errorHandler)

    private lateinit var notifications: SamplingNotifications
    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = SamplingNotifications(this)
        notifications.ensureChannel()
        application.appContainer.screenStateTracker.start()
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

        while (scope.isActive) {
            val settings = container.settingsRepository.settings.first()
            val snapshot = container.batteryReader.read()?.copy(
                screenOn = container.screenStateTracker.isScreenOn,
                foregroundPackage = container.foregroundAppResolver.currentPackage(),
            )

            if (snapshot != null) {
                try {
                    dao.insert(snapshot.toEntity())
                } catch (e: android.database.sqlite.SQLiteException) {
                    Log.e(TAG, "Falha ao gravar amostra em ${snapshot.timestampMs}", e)
                }
                updateNotification(snapshot)
            } else {
                Log.w(TAG, "Amostra descartada: leitura da bateria indisponível")
            }

            delay(settings.samplingInterval.millis)
        }
    }

    private suspend fun updateNotification(snapshot: BatterySnapshot) {
        val container = application.appContainer
        val text = try {
            container.statsRepository.notificationSummary(snapshot)
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Falha ao calcular o resumo da notificação", e)
            getString(R.string.notification_level_only, snapshot.levelPct)
        }
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.notify(SamplingNotifications.NOTIFICATION_ID, notifications.build(text))
    }

    override fun onDestroy() {
        samplingJob = null
        _isRunning.value = false
        job.cancel()
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

        /** Milissegundos em uma hora, reexportado para quem só precisa da constante. */
        const val MILLIS_PER_HOUR = DrainWindow.MILLIS_PER_HOUR

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
