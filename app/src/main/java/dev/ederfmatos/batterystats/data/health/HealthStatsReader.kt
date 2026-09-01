package dev.ederfmatos.batterystats.data.health

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.health.HealthStats
import android.os.health.SystemHealthManager
import android.os.health.TimerStat
import android.os.health.UidHealthStats
import android.util.Log
import dev.ederfmatos.batterystats.domain.health.TimerValue
import dev.ederfmatos.batterystats.domain.health.UidHealthSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lê os contadores de bateria por app, quando o sistema deixa.
 *
 * `SystemHealthManager.takeUidSnapshot` é **SDK público desde a API 24** — não há reflection nem
 * API oculta aqui. O que ele exige é `BATTERY_STATS` para qualquer UID que não seja o do próprio
 * app; o serviço só faz `enforceCallingOrSelfPermission` quando `requestUid != callingUid`.
 *
 * E `BATTERY_STATS` **não é inalcançável**: seu `protectionLevel` no AOSP é
 * `signature|privileged|development`, e `development` significa concedível por
 * `adb shell pm grant`. Uma premissa das fases anteriores dizia que ela era só
 * `signature|privileged`, o que estava errado. O grant sobrevive a reinício e às atualizações do
 * próprio app.
 *
 * Sem o grant, tudo aqui devolve vazio e o app continua funcionando com a estimativa por
 * correlação — que segue sendo o modo padrão.
 */
class HealthStatsReader(private val context: Context) {

    private val systemHealthManager: SystemHealthManager? =
        context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as? SystemHealthManager

    private val packageManager: PackageManager = context.applicationContext.packageManager

    /**
     * Se o modo avançado está ativo. Checado a cada consulta e não guardado: o usuário pode rodar
     * o comando com o app aberto, e a tela precisa refletir isso ao voltar.
     */
    fun hasBatteryStatsPermission(): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** O comando exato, com o pacote real, para o usuário copiar. */
    fun grantCommand(): String = "adb shell pm grant ${context.packageName} $PERMISSION"

    /**
     * Retrato dos apps instalados que têm UID próprio. Apps de sistema compartilham UID entre si,
     * então o mapa é por UID, não por pacote.
     */
    suspend fun snapshotAll(nowMs: Long): Map<Int, UidHealthSnapshot> =
        withContext(Dispatchers.Default) {
            val manager = systemHealthManager ?: return@withContext emptyMap()
            if (!hasBatteryStatsPermission()) return@withContext emptyMap()

            val uids = installedUids()
            if (uids.isEmpty()) return@withContext emptyMap()

            try {
                manager.takeUidSnapshots(uids.toIntArray())
                    .mapIndexedNotNull { index, stats ->
                        val uid = uids.getOrNull(index) ?: return@mapIndexedNotNull null
                        stats?.let { uid to it.toSnapshot(uid, nowMs) }
                    }
                    .toMap()
            } catch (e: SecurityException) {
                Log.w(TAG, "BATTERY_STATS foi revogada entre a checagem e a leitura", e)
                emptyMap()
            }
        }

    /** Sempre disponível: o próprio app não precisa de permissão nenhuma para se medir. */
    suspend fun snapshotSelf(nowMs: Long): UidHealthSnapshot? = withContext(Dispatchers.Default) {
        val manager = systemHealthManager ?: return@withContext null
        try {
            manager.takeMyUidSnapshot().toSnapshot(android.os.Process.myUid(), nowMs)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Falha ao ler os próprios contadores", e)
            null
        }
    }

    /** Nome legível de um UID. Vários pacotes podem compartilhar um; devolve o primeiro. */
    fun packagesFor(uid: Int): List<String> =
        packageManager.getPackagesForUid(uid)?.toList().orEmpty()

    private fun installedUids(): List<Int> = try {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ApplicationInfoFlags.of(0L)
        } else {
            null
        }
        val apps = if (flags != null) {
            packageManager.getInstalledApplications(flags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }
        apps.map(ApplicationInfo::uid).distinct()
    } catch (e: RuntimeException) {
        Log.e(TAG, "Não consegui listar os apps instalados", e)
        emptyList()
    }

    private fun HealthStats.toSnapshot(uid: Int, nowMs: Long) = UidHealthSnapshot(
        uid = uid,
        timestampMs = nowMs,
        partialWakelocks = timers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL),
        jobs = timers(UidHealthStats.TIMERS_JOBS),
        syncs = timers(UidHealthStats.TIMERS_SYNCS),
        gps = timer(UidHealthStats.TIMER_GPS_SENSOR),
        camera = timer(UidHealthStats.TIMER_CAMERA),
        flashlight = timer(UidHealthStats.TIMER_FLASHLIGHT),
        audio = timer(UidHealthStats.TIMER_AUDIO),
        video = timer(UidHealthStats.TIMER_VIDEO),
        wifiScan = timer(UidHealthStats.TIMER_WIFI_SCAN),
        bluetoothScan = timer(UidHealthStats.TIMER_BLUETOOTH_SCAN),
        mobileRadioActive = timer(UidHealthStats.TIMER_MOBILE_RADIO_ACTIVE),
        topMs = measurement(UidHealthStats.TIMER_PROCESS_STATE_TOP_MS),
        foregroundMs = measurement(UidHealthStats.TIMER_PROCESS_STATE_FOREGROUND_MS),
        foregroundServiceMs = measurement(
            UidHealthStats.TIMER_PROCESS_STATE_FOREGROUND_SERVICE_MS
        ),
        backgroundMs = measurement(UidHealthStats.TIMER_PROCESS_STATE_BACKGROUND_MS),
        cachedMs = measurement(UidHealthStats.TIMER_PROCESS_STATE_CACHED_MS),
        userCpuTimeMs = measurement(UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS),
        systemCpuTimeMs = measurement(UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS),
        mobileRxBytes = measurement(UidHealthStats.MEASUREMENT_MOBILE_RX_BYTES),
        mobileTxBytes = measurement(UidHealthStats.MEASUREMENT_MOBILE_TX_BYTES),
        wifiRxBytes = measurement(UidHealthStats.MEASUREMENT_WIFI_RX_BYTES),
        wifiTxBytes = measurement(UidHealthStats.MEASUREMENT_WIFI_TX_BYTES),
    )

    /**
     * As chaves de estado de processo são declaradas como TIMER_* mas o serviço as reporta como
     * medida em milissegundos, então `hasTimer` falha nelas. Tenta os dois, em vez de assumir.
     */
    private fun HealthStats.measurement(key: Int): Long = when {
        hasMeasurement(key) -> getMeasurement(key)
        hasTimer(key) -> getTimerTime(key)
        else -> 0L
    }

    private fun HealthStats.timer(key: Int): TimerValue =
        if (hasTimer(key)) getTimer(key).toValue() else TimerValue.ZERO

    private fun HealthStats.timers(key: Int): Map<String, TimerValue> =
        if (hasTimers(key)) {
            getTimers(key).mapValues { (_, timer) -> timer.toValue() }
        } else {
            emptyMap()
        }

    private fun TimerStat.toValue() = TimerValue(count = count, timeMs = time)

    companion object {
        private const val TAG = "HealthStatsReader"
        const val PERMISSION = "android.permission.BATTERY_STATS"
    }
}
