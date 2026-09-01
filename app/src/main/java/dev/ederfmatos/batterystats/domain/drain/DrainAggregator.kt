package dev.ederfmatos.batterystats.domain.drain

import java.util.TimeZone

/**
 * Agrega janelas de dreno em médias por regime de tela e por hora do dia.
 *
 * Toda média é ponderada pela duração da janela: uma janela de 5 minutos não pode pesar igual a uma
 * de 30 segundos. Janelas [ScreenRegime.MIXED] entram no total geral, mas ficam fora das médias por
 * regime — não dá para saber que parte do consumo foi com a tela ligada.
 */
class DrainAggregator(
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {

    fun aggregate(windows: List<DrainWindow>): DrainStats {
        if (windows.isEmpty()) return DrainStats.EMPTY

        return DrainStats(
            screenOn = regimeStats(windows.filter { it.screen == ScreenRegime.ON }),
            screenOff = regimeStats(windows.filter { it.screen == ScreenRegime.OFF }),
            overallMilliAmps = weightedAverageMilliAmps(windows),
            totalMilliAmpHours = windows.sumOf { it.milliAmpHours },
            idleBaselineMilliAmps = idleBaseline(windows),
            hourly = hourly(windows),
            windowCount = windows.size,
            highConfidenceWindowCount = windows.count { !it.lowConfidence },
            quantizationStepUah = windows.firstOrNull()?.quantizationStepUah ?: 0L,
        )
    }

    private fun regimeStats(windows: List<DrainWindow>): RegimeStats {
        if (windows.isEmpty()) return RegimeStats.EMPTY
        val durationMs = windows.sumOf { it.durationMs }
        val hours = durationMs / DrainWindow.MILLIS_PER_HOUR
        val levelDrop = windows.sumOf { it.levelDropPct }
        return RegimeStats(
            averageMilliAmps = weightedAverageMilliAmps(windows),
            percentPerHour = if (hours > 0) levelDrop / hours else 0.0,
            durationMs = durationMs,
            windowCount = windows.size,
            highConfidenceWindowCount = windows.count { !it.lowConfidence },
            uncertaintyMilliAmps = weightedUncertainty(windows),
        )
    }

    private fun weightedUncertainty(windows: List<DrainWindow>): Double {
        val totalMs = windows.sumOf { it.durationMs }
        if (totalMs <= 0L) return 0.0
        return windows.sumOf { it.uncertaintyMilliAmps * it.durationMs } / totalMs
    }

    private fun weightedAverageMilliAmps(windows: List<DrainWindow>): Double {
        val totalMs = windows.sumOf { it.durationMs }
        if (totalMs <= 0L) return 0.0
        return windows.sumOf { it.milliAmps * it.durationMs } / totalMs
    }

    /**
     * O piso de consumo em repouso: percentil 10 do dreno com a tela desligada, considerando só
     * janelas de pelo menos [MIN_BASELINE_WINDOW_MS].
     *
     * O corte de duração não é detalhe. Com o contador quantizado, janelas curtas em repouso só
     * produzem múltiplos do degrau (0, 244, 488 mA…); foi a partir de ~300 s que os valores
     * medidos convergiram para os 40–100 mA que o aparelho realmente consome parado. Usar o
     * percentil 10, e não o mínimo, evita que um único zero de arredondamento vire a linha de base.
     */
    private fun idleBaseline(windows: List<DrainWindow>): Double? {
        val offValues = windows
            .filter {
                it.screen == ScreenRegime.OFF &&
                    it.milliAmps > 0.0 &&
                    it.durationMs >= MIN_BASELINE_WINDOW_MS
            }
            .map { it.milliAmps }
            .sorted()
        if (offValues.size < MIN_WINDOWS_FOR_BASELINE) return null
        val index = ((offValues.size - 1) * BASELINE_PERCENTILE).toInt()
        return offValues[index]
    }

    private fun hourly(windows: List<DrainWindow>): List<HourlyDrain> {
        val buckets = windows.groupBy { hourOfDay(it.startMs) }
        return (0..23).mapNotNull { hour ->
            val inHour = buckets[hour] ?: return@mapNotNull null
            HourlyDrain(
                hourOfDay = hour,
                averageMilliAmps = weightedAverageMilliAmps(inHour),
                durationMs = inHour.sumOf { it.durationMs },
            )
        }
    }

    private fun hourOfDay(timestampMs: Long): Int {
        val calendar = java.util.Calendar.getInstance(timeZone)
        calendar.timeInMillis = timestampMs
        return calendar.get(java.util.Calendar.HOUR_OF_DAY)
    }

    companion object {
        const val MIN_WINDOWS_FOR_BASELINE = 5
        const val BASELINE_PERCENTILE = 0.10

        /** Abaixo de 300 s a quantização do contador domina o valor medido. */
        const val MIN_BASELINE_WINDOW_MS = 300_000L
    }
}
