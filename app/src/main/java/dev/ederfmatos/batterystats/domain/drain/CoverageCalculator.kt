package dev.ederfmatos.batterystats.domain.drain

/**
 * Quanto de um período foi de fato medido.
 *
 * Sem isto todo agregado mente por omissão: numa coleta real, 59% do tempo caiu dentro de buracos
 * de amostragem, e o "dreno médio do dia" era a média dos 41% em que o serviço estava vivo.
 */
data class Coverage(
    val periodMs: Long,
    val gapMs: Long,
    val gapCount: Int,
) {
    val coveredMs: Long get() = (periodMs - gapMs).coerceAtLeast(0L)
    val fraction: Double get() = if (periodMs > 0) coveredMs.toDouble() / periodMs else 0.0
    val percent: Double get() = fraction * 100.0

    /** Abaixo disto não vale concluir nada dos agregados; a UI avisa. */
    val isPoor: Boolean get() = fraction < POOR_THRESHOLD

    companion object {
        const val POOR_THRESHOLD = 0.70
        val EMPTY = Coverage(0L, 0L, 0)
    }
}

class CoverageCalculator {

    fun coverage(fromMs: Long, toMs: Long, gaps: List<MeasurementGap>): Coverage {
        val periodMs = (toMs - fromMs).coerceAtLeast(0L)
        if (periodMs == 0L) return Coverage.EMPTY

        val relevant = gaps
            .map { it.overlapMs(fromMs, toMs) }
            .filter { it > 0L }

        return Coverage(
            periodMs = periodMs,
            gapMs = relevant.sum().coerceAtMost(periodMs),
            gapCount = relevant.size,
        )
    }
}
