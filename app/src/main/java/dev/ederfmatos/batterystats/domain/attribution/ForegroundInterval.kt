package dev.ederfmatos.batterystats.domain.attribution

/** Um trecho contínuo em que um pacote esteve em primeiro plano. */
data class ForegroundInterval(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs

    /** Interseção com [startMs, endMs), em milissegundos. Zero quando não há sobreposição. */
    fun overlapMs(windowStartMs: Long, windowEndMs: Long): Long {
        val start = maxOf(startMs, windowStartMs)
        val end = minOf(endMs, windowEndMs)
        return (end - start).coerceAtLeast(0L)
    }
}

/** Consumo estimado atribuído a um app (ou ao bucket de sistema) num período. */
data class AppEnergyUsage(
    val packageName: String,
    val estimatedMilliAmpHours: Double,
    val foregroundMs: Long,
    val averageMilliAmpsInForeground: Double,
    val isSystemBucket: Boolean = false,
    /**
     * Sobre quantas janelas de medição esta linha foi construída.
     *
     * Janelas de baixa confiança já são descartadas na atribuição, então o que resta de incerteza
     * aqui é **quantidade de evidência** — e uma linha apurada em 2 janelas não pode parecer
     * idêntica a uma apurada em 40.
     */
    val windowCount: Int = 0,
) {
    /** Abaixo disso a linha é sugestiva, não conclusiva. */
    val hasThinEvidence: Boolean get() = !isSystemBucket && windowCount < MIN_WINDOWS_FOR_CONFIDENCE

    companion object {
        const val MIN_WINDOWS_FOR_CONFIDENCE = 3
    }
}

/**
 * Tudo que não dá para creditar a um app específico: tela desligada, janelas sem timeline de
 * primeiro plano, e a linha de base de repouso subtraída das demais janelas.
 */
const val SYSTEM_BUCKET_PACKAGE = "__system__"
