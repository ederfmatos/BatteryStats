package dev.ederfmatos.batterystats.data

import dev.ederfmatos.batterystats.data.db.BatteryDao
import dev.ederfmatos.batterystats.data.db.DailyAggregateEntity
import dev.ederfmatos.batterystats.data.db.toGap
import dev.ederfmatos.batterystats.data.db.toSnapshot
import dev.ederfmatos.batterystats.data.prefs.SettingsRepository
import dev.ederfmatos.batterystats.data.usage.ForegroundAppResolver
import dev.ederfmatos.batterystats.domain.attribution.AppAttributionCalculator
import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.domain.drain.AdaptiveWindowBuilder
import dev.ederfmatos.batterystats.domain.drain.Coverage
import dev.ederfmatos.batterystats.domain.drain.CoverageCalculator
import dev.ederfmatos.batterystats.domain.drain.CurrentCalibrator
import dev.ederfmatos.batterystats.domain.drain.DrainAggregator
import dev.ederfmatos.batterystats.domain.drain.DrainAnalysis
import dev.ederfmatos.batterystats.domain.drain.DrainStats
import dev.ederfmatos.batterystats.domain.drain.MeasurementGap
import dev.ederfmatos.batterystats.domain.drain.QuantizationDetector
import dev.ederfmatos.batterystats.domain.drain.RuntimeProjection
import dev.ederfmatos.batterystats.domain.drain.RuntimeProjector
import dev.ederfmatos.batterystats.domain.health.BatteryHealthEstimate
import dev.ederfmatos.batterystats.domain.health.BatteryHealthEstimator
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Período de consulta das telas de análise. */
enum class StatsPeriod(val days: Long) {
    TODAY(1),
    LAST_7_DAYS(7),
}

data class PeriodStats(
    val stats: DrainStats,
    val analysis: DrainAnalysis,
    val projection: RuntimeProjection,
    val calibration: CurrentCalibration,
    val coverage: Coverage,
)

/**
 * Junta banco, preferências e a camada de domínio. É a única classe que sabe ao mesmo tempo de
 * Room e de cálculo — o domínio continua sem enxergar nada disso.
 */
class StatsRepository(
    private val dao: BatteryDao,
    private val settingsRepository: SettingsRepository,
    private val foregroundAppResolver: ForegroundAppResolver,
    private val windowBuilder: AdaptiveWindowBuilder = AdaptiveWindowBuilder(),
    private val aggregator: DrainAggregator = DrainAggregator(),
    private val projector: RuntimeProjector = RuntimeProjector(),
    private val calibrator: CurrentCalibrator = CurrentCalibrator(),
    private val attributionCalculator: AppAttributionCalculator = AppAttributionCalculator(),
    private val healthEstimator: BatteryHealthEstimator = BatteryHealthEstimator(),
    private val coverageCalculator: CoverageCalculator = CoverageCalculator(),
    private val quantizationDetector: QuantizationDetector = QuantizationDetector(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun snapshotsSince(fromMs: Long): List<BatterySnapshot> = withContext(Dispatchers.IO) {
        dao.samplesSince(fromMs).map { it.toSnapshot() }
    }

    suspend fun gapsSince(fromMs: Long): List<MeasurementGap> = withContext(Dispatchers.IO) {
        dao.gapsSince(fromMs).map { it.toGap() }
    }

    suspend fun periodStats(period: StatsPeriod): PeriodStats = withContext(Dispatchers.Default) {
        val calibration = settingsRepository.settings.first().calibration
        val nowMs = clock()
        val fromMs = nowMs - period.days * MILLIS_PER_DAY
        val samples = snapshotsSince(fromMs)
        val gaps = gapsSince(fromMs)

        val analysis = windowBuilder.analyze(samples, gaps, calibration)
        val stats = aggregator.aggregate(analysis.windows)
        val latest = samples.lastOrNull()

        PeriodStats(
            stats = stats,
            analysis = analysis,
            projection = projector.project(
                stats = stats,
                currentLevelPct = latest?.levelPct ?: 0,
                chargeCounterUah = latest?.chargeCounterUah,
            ),
            calibration = calibration,
            coverage = coverageCalculator.coverage(fromMs, nowMs, gaps),
        )
    }

    suspend fun coverage(period: StatsPeriod): Coverage = withContext(Dispatchers.Default) {
        val nowMs = clock()
        val fromMs = nowMs - period.days * MILLIS_PER_DAY
        coverageCalculator.coverage(fromMs, nowMs, gapsSince(fromMs))
    }

    suspend fun appRanking(period: StatsPeriod): List<AppEnergyUsage> =
        withContext(Dispatchers.Default) {
            val nowMs = clock()
            val fromMs = nowMs - period.days * MILLIS_PER_DAY
            val calibration = settingsRepository.settings.first().calibration
            val samples = snapshotsSince(fromMs)
            val gaps = gapsSince(fromMs)
            val analysis = windowBuilder.analyze(samples, gaps, calibration)
            val stats = aggregator.aggregate(analysis.windows)
            val intervals = foregroundAppResolver.intervals(fromMs, nowMs)
            attributionCalculator.attribute(
                windows = analysis.windows,
                foregroundIntervals = intervals,
                idleBaselineMilliAmps = stats.idleBaselineMilliAmps,
            )
        }

    /** Recalcula e persiste o degrau de quantização a partir das amostras recentes. */
    suspend fun refreshQuantizationStep(): Long? = withContext(Dispatchers.Default) {
        val samples = snapshotsSince(clock() - MILLIS_PER_DAY)
        val step = quantizationDetector.detectStepUah(samples) ?: return@withContext null
        if (step != settingsRepository.settings.first().quantizationStepUah) {
            settingsRepository.setQuantizationStepUah(step)
        }
        step
    }

    /**
     * Roda a autocalibração sobre as últimas 24h e persiste o resultado.
     * Não sobrescreve uma calibração forçada manualmente pelo usuário.
     */
    suspend fun runAutoCalibration(): CurrentCalibration? = withContext(Dispatchers.Default) {
        val existing = settingsRepository.settings.first().calibration
        if (existing.source == CurrentCalibration.Source.MANUAL) return@withContext null

        val fromMs = clock() - MILLIS_PER_DAY
        val calibration = calibrator.calibrate(snapshotsSince(fromMs), gapsSince(fromMs))
            ?: return@withContext null
        settingsRepository.setCalibration(calibration)
        calibration
    }

    suspend fun healthEstimate(): BatteryHealthEstimate = withContext(Dispatchers.Default) {
        val samples = withContext(Dispatchers.IO) { dao.allSamples().map { it.toSnapshot() } }
        healthEstimator.estimate(samples, clock())
    }

    /** Texto curto da notificação persistente: dreno atual, projeção e cobertura. */
    suspend fun notificationSummary(latest: BatterySnapshot): String {
        val period = periodStats(StatsPeriod.TODAY)
        val milliAmps = period.stats.overallMilliAmps
        val hours = period.projection.hoursRemaining
        return buildString {
            append("${latest.levelPct}%")
            if (milliAmps > 0.0) append(" · ${milliAmps.toInt()} mA")
            if (hours != null && hours.isFinite() && hours > 0) {
                val wholeHours = hours.toInt()
                val minutes = ((hours - wholeHours) * 60).toInt()
                append(" · resta ~${wholeHours}h${minutes.toString().padStart(2, '0')}")
            }
            if (period.coverage.isPoor) {
                append(" · cobertura ${period.coverage.percent.toInt()}%")
            }
        }
    }

    /** Consolida um dia inteiro em uma linha de [DailyAggregateEntity]. */
    suspend fun aggregateDay(dayStartMs: Long): DailyAggregateEntity? =
        withContext(Dispatchers.Default) {
            val dayEndMs = dayStartMs + MILLIS_PER_DAY
            val calibration = settingsRepository.settings.first().calibration
            val samples = withContext(Dispatchers.IO) {
                dao.samplesBetween(dayStartMs, dayEndMs).map { it.toSnapshot() }
            }
            if (samples.size < 2) return@withContext null

            val gaps = gapsSince(dayStartMs).filter { it.startMs < dayEndMs }
            val analysis = windowBuilder.analyze(samples, gaps, calibration)
            val stats = aggregator.aggregate(analysis.windows)
            val entity = DailyAggregateEntity(
                dayEpochDay = Instant.ofEpochMilli(dayStartMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                screenOnMilliAmps = stats.screenOn.averageMilliAmps,
                screenOffMilliAmps = stats.screenOff.averageMilliAmps,
                screenOnMs = stats.screenOn.durationMs,
                screenOffMs = stats.screenOff.durationMs,
                screenOnPercentPerHour = stats.screenOn.percentPerHour,
                screenOffPercentPerHour = stats.screenOff.percentPerHour,
                totalMilliAmpHours = stats.totalMilliAmpHours,
                idleBaselineMilliAmps = stats.idleBaselineMilliAmps,
                maxChargeCounterUah = samples.mapNotNull { it.chargeCounterUah }.maxOrNull(),
                sampleCount = samples.size,
                computedAtMs = clock(),
            )
            withContext(Dispatchers.IO) { dao.upsertDailyAggregate(entity) }
            entity
        }

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L

        /** Amostras cruas vivem 14 dias; os agregados diários ficam para sempre. */
        const val RAW_RETENTION_DAYS = 14L
    }
}
