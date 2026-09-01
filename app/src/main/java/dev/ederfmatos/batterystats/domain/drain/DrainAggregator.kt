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
        )
    }

    private fun weightedAverageMilliAmps(windows: List<DrainWindow>): Double {
        val totalMs = windows.sumOf { it.durationMs }
        if (totalMs <= 0L) return 0.0
        return windows.sumOf { it.milliAmps * it.durationMs } / totalMs
    }

    /**
     * O piso de consumo em repouso. Usa o percentil 10 das janelas de tela desligada em vez do
     * mínimo absoluto: o mínimo é uma amostra só e pega qualquer artefato de arredondamento do
     * contador, enquanto o percentil baixo representa um patamar que o aparelho realmente sustenta.
     */
    private fun idleBaseline(windows: List<DrainWindow>): Double? {
        val offValues = windows
            .filter { it.screen == ScreenRegime.OFF && it.milliAmps > 0.0 }
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
    }
}
