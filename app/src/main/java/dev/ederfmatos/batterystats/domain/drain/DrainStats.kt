package dev.ederfmatos.batterystats.domain.drain

/** Média de dreno de um regime de tela, ponderada pelo tempo de cada janela. */
data class RegimeStats(
    val averageMilliAmps: Double,
    val percentPerHour: Double,
    val durationMs: Long,
    val windowCount: Int,
    val highConfidenceWindowCount: Int = 0,
    /** Incerteza média imposta pela quantização do contador, ponderada pela duração. */
    val uncertaintyMilliAmps: Double = 0.0,
) {
    /** Sem nenhuma janela de alta confiança, o número só é honesto apresentado como faixa. */
    val isCoarse: Boolean get() = windowCount > 0 && highConfidenceWindowCount == 0

    val rangeLowMilliAmps: Double
        get() = (averageMilliAmps - uncertaintyMilliAmps).coerceAtLeast(0.0)
    val rangeHighMilliAmps: Double get() = averageMilliAmps + uncertaintyMilliAmps

    companion object {
        val EMPTY = RegimeStats(0.0, 0.0, 0L, 0)
    }
}

/** Dreno médio de uma hora do dia (0..23), agregado sobre vários dias. */
data class HourlyDrain(
    val hourOfDay: Int,
    val averageMilliAmps: Double,
    val durationMs: Long,
)

/** Projeção de autonomia. [hoursRemaining] é null quando não há dados suficientes. */
data class RuntimeProjection(
    val hoursRemaining: Double?,
    val basedOnMilliAmps: Double?,
    val basedOnPercentPerHour: Double?,
)

data class DrainStats(
    val screenOn: RegimeStats,
    val screenOff: RegimeStats,
    val overallMilliAmps: Double,
    val totalMilliAmpHours: Double,
    /** Menor dreno sustentado observado com a tela desligada. É o piso do aparelho em repouso. */
    val idleBaselineMilliAmps: Double?,
    val hourly: List<HourlyDrain>,
    val windowCount: Int,
    val highConfidenceWindowCount: Int = 0,
    val quantizationStepUah: Long = 0L,
) {
    companion object {
        val EMPTY = DrainStats(
            screenOn = RegimeStats.EMPTY,
            screenOff = RegimeStats.EMPTY,
            overallMilliAmps = 0.0,
            totalMilliAmpHours = 0.0,
            idleBaselineMilliAmps = null,
            hourly = emptyList(),
            windowCount = 0,
        )
    }
}
