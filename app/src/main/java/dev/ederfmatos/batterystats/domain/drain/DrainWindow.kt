package dev.ederfmatos.batterystats.domain.drain

/** De onde veio o número de dreno de uma janela. */
enum class DrainSource {
    /** Δ CHARGE_COUNTER acumulado numa janela adaptativa. É a fonte de verdade dos relatórios. */
    CHARGE_COUNTER,

    /**
     * Média de CURRENT_NOW. Mantida apenas para aparelhos cujo contador nunca se move; sofre de
     * viés do observador (a leitura acontece no instante em que o app acorda o aparelho e acaba
     * medindo o próprio custo da amostragem), então nunca alimenta relatório.
     */
    CURRENT_NOW,
}

/** Regime de tela de uma janela. MIXED = a tela mudou de estado no meio dela. */
enum class ScreenRegime { ON, OFF, MIXED }

/** Por que uma janela adaptativa fechou. */
enum class WindowCloseReason {
    /** Acumulou degraus suficientes para a medição ser confiável. */
    STEPS_REACHED,

    /** Estourou o tempo máximo com poucos degraus. O valor é grosseiro. */
    TIME_LIMIT,
}

/**
 * Uma medição de dreno fechada por [AdaptiveWindowBuilder].
 *
 * Diferente da versão anterior — que fechava uma janela por par de amostras — esta só existe
 * depois de acumular carga suficiente para o número significar alguma coisa. Ver
 * [QuantizationDetector] para o porquê.
 *
 * [milliAmps] é sempre positivo: representa consumo. Janelas com o aparelho carregando não são
 * emitidas.
 */
data class DrainWindow(
    val startMs: Long,
    val endMs: Long,
    val milliAmps: Double,
    val source: DrainSource,
    val screen: ScreenRegime,
    val startLevelPct: Int,
    val endLevelPct: Int,
    val consumedUah: Long = 0L,
    val stepsAccumulated: Int = 0,
    val quantizationStepUah: Long = QuantizationDetector.FALLBACK_STEP_UAH,
    val closeReason: WindowCloseReason = WindowCloseReason.STEPS_REACHED,
    val lowConfidence: Boolean = false,
) {
    val spanMs: Long get() = endMs - startMs
    val durationMs: Long get() = spanMs
    val durationHours: Double get() = spanMs / MILLIS_PER_HOUR
    val milliAmpHours: Double get() = milliAmps * durationHours
    val levelDropPct: Int get() = startLevelPct - endLevelPct

    /**
     * Incerteza em mA imposta pela quantização: um degrau inteiro distribuído no tempo da janela.
     * Quanto mais longa a janela, menor — é exatamente por isso que a janela é adaptativa.
     */
    val uncertaintyMilliAmps: Double
        get() = if (durationHours > 0) (quantizationStepUah / 1000.0) / durationHours else 0.0

    /**
     * Faixa honesta do valor. O contador reporta múltiplos do degrau, então a carga real está a até
     * um degrau de distância do observado, para mais ou para menos.
     */
    val rangeLowMilliAmps: Double get() = (milliAmps - uncertaintyMilliAmps).coerceAtLeast(0.0)
    val rangeHighMilliAmps: Double get() = milliAmps + uncertaintyMilliAmps

    companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}

/** Um buraco na medição: o serviço morreu, o aparelho dormiu ou reiniciou. */
enum class GapReason { SERVICE_KILLED, DOZE, REBOOT, UNKNOWN }

data class MeasurementGap(
    val startMs: Long,
    val endMs: Long,
    val reason: GapReason = GapReason.UNKNOWN,
) {
    val durationMs: Long get() = endMs - startMs

    /** True se o intervalo [fromMs, toMs) encosta neste buraco de qualquer forma. */
    fun intersects(fromMs: Long, toMs: Long): Boolean = startMs < toMs && endMs > fromMs

    /** Milissegundos deste buraco que caem dentro de [fromMs, toMs). */
    fun overlapMs(fromMs: Long, toMs: Long): Long =
        (minOf(endMs, toMs) - maxOf(startMs, fromMs)).coerceAtLeast(0L)
}

/** Mantido como alias do nome antigo para não quebrar quem só precisa do par de timestamps. */
typealias SamplingGap = MeasurementGap
