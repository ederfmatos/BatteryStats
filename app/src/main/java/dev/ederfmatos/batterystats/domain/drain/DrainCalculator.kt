package dev.ederfmatos.batterystats.domain.drain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import kotlin.math.abs

/** Resultado de uma passada do [DrainCalculator] sobre uma série de amostras. */
data class DrainAnalysis(
    val windows: List<DrainWindow>,
    val gaps: List<SamplingGap>,
    val chargingWindows: Int,
)

/**
 * Converte uma série de [BatterySnapshot] em janelas de dreno.
 *
 * Duas fontes independentes, nesta ordem:
 *  - A (preferida): Δ CHARGE_COUNTER / Δt. É carga de verdade saindo da bateria.
 *  - B (fallback):  média de CURRENT_NOW nas duas pontas, para aparelhos onde o contador não se
 *    move (fica travado no mesmo valor) ou simplesmente não existe.
 *
 * Janelas com o aparelho carregando são descartadas — misturar carga e descarga no mesmo número
 * produziria um dreno médio sem significado.
 */
class DrainCalculator(
    private val maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    private val minWindowMs: Long = DEFAULT_MIN_WINDOW_MS,
) {

    fun analyze(
        samples: List<BatterySnapshot>,
        calibration: CurrentCalibration,
    ): DrainAnalysis {
        if (samples.size < 2) return DrainAnalysis(emptyList(), emptyList(), 0)

        val ordered = samples.sortedBy { it.timestampMs }
        val windows = mutableListOf<DrainWindow>()
        val gaps = mutableListOf<SamplingGap>()
        var chargingWindows = 0

        for (i in 0 until ordered.lastIndex) {
            val start = ordered[i]
            val end = ordered[i + 1]
            val durationMs = end.timestampMs - start.timestampMs

            if (durationMs > maxGapMs) {
                gaps += SamplingGap(start.timestampMs, end.timestampMs)
                continue
            }
            if (durationMs < minWindowMs) continue
            if (start.isCharging || end.isCharging) {
                chargingWindows++
                continue
            }

            val window = buildWindow(start, end, durationMs, calibration)
            if (window != null) windows += window
        }

        return DrainAnalysis(windows, gaps, chargingWindows)
    }

    private fun buildWindow(
        start: BatterySnapshot,
        end: BatterySnapshot,
        durationMs: Long,
        calibration: CurrentCalibration,
    ): DrainWindow? {
        val hours = durationMs / DrainWindow.MILLIS_PER_HOUR
        val fromCounter = counterDrainMilliAmps(start, end, hours)
        val milliAmps = fromCounter ?: currentNowDrainMilliAmps(start, end, calibration) ?: return null
        val source = if (fromCounter != null) DrainSource.CHARGE_COUNTER else DrainSource.CURRENT_NOW

        return DrainWindow(
            startMs = start.timestampMs,
            endMs = end.timestampMs,
            milliAmps = milliAmps,
            source = source,
            screen = screenRegime(start, end),
            startLevelPct = start.levelPct,
            endLevelPct = end.levelPct,
        )
    }

    /**
     * Fonte A. O contador é em µAh e decresce durante a descarga. Um Δ de zero significa contador
     * travado (comum em vários aparelhos) e devolve null para cair no fallback. Um Δ positivo
     * durante descarga significa que a bateria ganhou carga sem estar carregando — dado incoerente,
     * também descartado.
     */
    private fun counterDrainMilliAmps(
        start: BatterySnapshot,
        end: BatterySnapshot,
        hours: Double,
    ): Double? {
        val startUah = start.chargeCounterUah ?: return null
        val endUah = end.chargeCounterUah ?: return null
        val consumedUah = startUah - endUah
        if (consumedUah <= 0L) return null
        return (consumedUah / 1000.0) / hours
    }

    /** Fonte B. Sempre devolve consumo positivo, já com a calibração de unidade e sinal aplicada. */
    private fun currentNowDrainMilliAmps(
        start: BatterySnapshot,
        end: BatterySnapshot,
        calibration: CurrentCalibration,
    ): Double? {
        val values = listOfNotNull(
            calibration.drainMilliAmps(start.currentNowRaw),
            calibration.drainMilliAmps(end.currentNowRaw),
        ).filter { it > 0.0 }
        if (values.isEmpty()) return null
        return values.average()
    }

    private fun screenRegime(start: BatterySnapshot, end: BatterySnapshot): ScreenRegime = when {
        start.screenOn != end.screenOn -> ScreenRegime.MIXED
        start.screenOn -> ScreenRegime.ON
        else -> ScreenRegime.OFF
    }

    companion object {
        /** Acima disso a janela vira buraco: o aparelho dormiu e não dá para saber o que houve. */
        const val DEFAULT_MAX_GAP_MS = 10 * 60_000L

        /** Janelas curtas demais amplificam ruído de arredondamento do contador. */
        const val DEFAULT_MIN_WINDOW_MS = 15_000L
    }
}

/** Utilitário compartilhado: mediana de uma lista não vazia. */
internal fun List<Double>.median(): Double {
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}

internal fun Double.isCloseTo(other: Double, tolerance: Double): Boolean =
    abs(this - other) <= tolerance
