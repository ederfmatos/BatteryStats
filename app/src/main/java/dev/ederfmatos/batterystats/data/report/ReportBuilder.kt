package dev.ederfmatos.batterystats.data.report

import android.os.Build
import dev.ederfmatos.batterystats.data.StatsPeriod
import dev.ederfmatos.batterystats.data.StatsRepository
import dev.ederfmatos.batterystats.data.prefs.SettingsRepository
import dev.ederfmatos.batterystats.data.usage.ForegroundAppResolver
import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.NetworkType
import dev.ederfmatos.batterystats.domain.report.BatteryReport
import dev.ederfmatos.batterystats.domain.report.ContextAverages
import dev.ederfmatos.batterystats.domain.report.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reúne os dados agregados que o relatório precisa. Nenhuma amostra crua sai daqui. */
class ReportBuilder(
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val foregroundAppResolver: ForegroundAppResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun build(period: StatsPeriod = StatsPeriod.LAST_7_DAYS): BatteryReport =
        withContext(Dispatchers.Default) {
            val nowMs = clock()
            val fromMs = nowMs - period.days * StatsRepository.MILLIS_PER_DAY
            val settings = settingsRepository.settings.first()
            val periodStats = statsRepository.periodStats(period)
            val ranking = statsRepository.appRanking(period)
            val samples = statsRepository.snapshotsSince(fromMs)

            BatteryReport(
                device = DeviceInfo(
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidRelease = Build.VERSION.RELEASE.orEmpty(),
                    sdkInt = Build.VERSION.SDK_INT,
                ),
                impliedCapacityMah = impliedCapacityMah(samples),
                periodStartMs = fromMs,
                periodEndMs = nowMs,
                coverage = periodStats.coverage,
                quantizationStepUah = periodStats.analysis.quantizationStepUah,
                samplingIntervalMs = settings.samplingInterval.millis,
                calibration = settings.calibration,
                screenOn = periodStats.stats.screenOn,
                screenOff = periodStats.stats.screenOff,
                totalMilliAmpHours = periodStats.stats.totalMilliAmpHours,
                idleBaselineMilliAmps = periodStats.stats.idleBaselineMilliAmps,
                hourly = periodStats.stats.hourly,
                topApps = ranking.filter { !it.isSystemBucket },
                systemBucket = ranking.firstOrNull(AppEnergyUsage::isSystemBucket),
                context = contextAverages(samples),
                hasUsageAccess = foregroundAppResolver.hasAccess(),
                windowCount = periodStats.stats.windowCount,
                highConfidenceWindowCount = periodStats.stats.highConfidenceWindowCount,
            )
        }

    /**
     * Capacidade implícita: o contador de carga num nível conhecido diz quanto a bateria teria a
     * 100%. A mediana das leituras filtra os extremos de temperatura e de carga parcial.
     */
    private fun impliedCapacityMah(samples: List<BatterySnapshot>): Double? {
        val estimates = samples
            .filter { it.levelPct in 20..100 }
            .mapNotNull { sample ->
                sample.chargeCounterUah?.let { counter ->
                    (counter / 1000.0) / (sample.levelPct / 100.0)
                }
            }
            .sorted()
        if (estimates.isEmpty()) return null
        return estimates[estimates.size / 2]
    }

    private fun contextAverages(samples: List<BatterySnapshot>): ContextAverages {
        if (samples.isEmpty()) {
            return ContextAverages(null, null, emptyMap(), null, null, null)
        }

        val brightnesses = samples.mapNotNull { it.screenBrightness }
        val autoFlags = samples.mapNotNull { it.autoBrightness }
        val locationFlags = samples.mapNotNull { it.locationEnabled }
        val temperatures = samples.mapNotNull { it.temperatureCelsius }

        val networkCounts = samples
            .filter { it.networkType != NetworkType.UNKNOWN }
            .groupingBy { it.networkType }
            .eachCount()
        val networkTotal = networkCounts.values.sum()

        return ContextAverages(
            screenBrightness = brightnesses.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            autoBrightnessFraction = autoFlags.takeIf { it.isNotEmpty() }
                ?.count { it }?.toDouble()?.div(autoFlags.size),
            networkShare = if (networkTotal > 0) {
                networkCounts.mapValues { (_, count) -> count.toDouble() / networkTotal }
            } else {
                emptyMap()
            },
            locationEnabledFraction = locationFlags.takeIf { it.isNotEmpty() }
                ?.count { it }?.toDouble()?.div(locationFlags.size),
            temperatureMinCelsius = temperatures.minOrNull(),
            temperatureMaxCelsius = temperatures.maxOrNull(),
        )
    }
}
