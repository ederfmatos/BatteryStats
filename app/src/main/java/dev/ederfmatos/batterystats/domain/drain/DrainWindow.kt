package dev.ederfmatos.batterystats.domain.drain

/** De onde veio o número de dreno de uma janela. */
enum class DrainSource {
    /** Δ CHARGE_COUNTER / Δt. É medição real de carga consumida. */
    CHARGE_COUNTER,

    /** Média de CURRENT_NOW nas pontas da janela. Usado quando o contador não se move. */
    CURRENT_NOW,
}

/** Regime de tela de uma janela. MIXED = a tela mudou de estado no meio dela. */
enum class ScreenRegime { ON, OFF, MIXED }

/**
 * O dreno medido entre duas amostras consecutivas.
 *
 * [milliAmps] é sempre positivo: representa consumo. Janelas em que o aparelho estava carregando
 * não viram [DrainWindow] — são descartadas antes.
 */
data class DrainWindow(
    val startMs: Long,
    val endMs: Long,
    val milliAmps: Double,
    val source: DrainSource,
    val screen: ScreenRegime,
    val startLevelPct: Int,
    val endLevelPct: Int,
) {
    val durationMs: Long get() = endMs - startMs
    val durationHours: Double get() = durationMs / MILLIS_PER_HOUR
    val milliAmpHours: Double get() = milliAmps * durationHours
    val levelDropPct: Int get() = startLevelPct - endLevelPct

    companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}

/** Um buraco na amostragem: o aparelho dormiu, o serviço morreu, ou o usuário desligou o app. */
data class SamplingGap(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}
