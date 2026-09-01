package dev.ederfmatos.batterystats.domain.drain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration

/** Parâmetros da janela adaptativa. Vêm da config remota, com estes valores como fallback. */
data class WindowConfig(
    val minStepsToClose: Int = DEFAULT_MIN_STEPS,
    val maxWindowMs: Long = DEFAULT_MAX_WINDOW_MS,
) {
    companion object {
        /** Quatro degraus deixam a incerteza em ±25% do valor medido, que já é utilizável. */
        const val DEFAULT_MIN_STEPS = 4

        /** Acima disso a janela fecha mesmo grosseira, senão o app não reporta nada em repouso. */
        const val DEFAULT_MAX_WINDOW_MS = 300_000L
    }
}

data class DrainAnalysis(
    val windows: List<DrainWindow>,
    val gaps: List<MeasurementGap>,
    val chargingWindows: Int,
    val quantizationStepUah: Long,
    val discardedWindows: Int = 0,
) {
    val highConfidenceWindows: List<DrainWindow> get() = windows.filter { !it.lowConfidence }
}

/**
 * Acumula amostras até que a carga consumida seja grande o bastante para virar uma medição.
 *
 * O cálculo antigo — uma janela por par de amostras consecutivas — só funciona se o contador for
 * contínuo. Com o contador quantizado em degraus de ~4 mAh, uma janela de 60 s em repouso mede
 * "0 mA" ou "245 mA" conforme o degrau caiu dentro ou fora dela, e nada entre os dois. A média
 * disso converge, mas cada valor individual é ruído — e é sobre valores individuais que a
 * atribuição por app trabalha.
 *
 * Aqui a janela fica aberta acumulando até uma de duas condições:
 *  - acumulou [WindowConfig.minStepsToClose] degraus → medição de confiança normal;
 *  - passou de [WindowConfig.maxWindowMs] com pelo menos 1 degrau → medição grosseira, marcada
 *    [DrainWindow.lowConfidence], que aparece na UI como faixa e **nunca** alimenta o ranking.
 *
 * Nenhuma janela atravessa um gap: uma janela aberta que encontra um buraco é **descartada**, não
 * fechada — o consumo que aconteceu durante o buraco não foi medido e não pode ser diluído no que
 * foi.
 */
class AdaptiveWindowBuilder(
    private val config: WindowConfig = WindowConfig(),
    private val quantizationDetector: QuantizationDetector = QuantizationDetector(),
) {

    fun analyze(
        samples: List<BatterySnapshot>,
        gaps: List<MeasurementGap> = emptyList(),
        calibration: CurrentCalibration = CurrentCalibration.DEFAULT,
    ): DrainAnalysis {
        val stepUah = quantizationDetector.detectStepUah(samples)
            ?: QuantizationDetector.FALLBACK_STEP_UAH

        if (samples.size < 2) {
            return DrainAnalysis(emptyList(), gaps, 0, stepUah)
        }

        val ordered = samples.sortedBy { it.timestampMs }
        val windows = mutableListOf<DrainWindow>()
        var chargingWindows = 0
        var discarded = 0
        var open: OpenWindow? = null

        for (index in 1 until ordered.size) {
            val previous = ordered[index - 1]
            val current = ordered[index]

            if (crossesGap(previous.timestampMs, current.timestampMs, gaps)) {
                if (open != null) discarded++
                open = null
                continue
            }

            if (previous.isCharging || current.isCharging) {
                if (open != null) discarded++
                open = null
                chargingWindows++
                continue
            }

            val accumulator = open ?: OpenWindow(previous).also { open = it }
            accumulator.add(previous, current)

            val closed = accumulator.tryClose(stepUah, config)
            if (closed != null) {
                windows += closed
                open = null
            }
        }

        // Uma janela que ficou aberta no fim da série ainda não acumulou o suficiente. Ela não é
        // uma medição; fica de fora até chegarem mais amostras.
        if (open != null) discarded++

        val fallbackWindows = if (windows.isEmpty()) {
            currentNowFallback(ordered, gaps, calibration)
        } else {
            emptyList()
        }

        return DrainAnalysis(
            windows = windows + fallbackWindows,
            gaps = gaps,
            chargingWindows = chargingWindows,
            quantizationStepUah = stepUah,
            discardedWindows = discarded,
        )
    }

    private fun crossesGap(fromMs: Long, toMs: Long, gaps: List<MeasurementGap>): Boolean =
        gaps.any { it.intersects(fromMs, toMs) }

    /**
     * Aparelhos cujo contador nunca se move não produzem nenhuma janela. Para não deixar a tela
     * vazia neles, cai-se em CURRENT_NOW — sempre como [DrainSource.CURRENT_NOW] e sempre
     * [DrainWindow.lowConfidence], porque o valor sofre viés do observador.
     */
    private fun currentNowFallback(
        ordered: List<BatterySnapshot>,
        gaps: List<MeasurementGap>,
        calibration: CurrentCalibration,
    ): List<DrainWindow> {
        val result = mutableListOf<DrainWindow>()
        for (index in 1 until ordered.size) {
            val previous = ordered[index - 1]
            val current = ordered[index]
            if (previous.isCharging || current.isCharging) continue
            if (crossesGap(previous.timestampMs, current.timestampMs, gaps)) continue

            val values = listOfNotNull(
                calibration.drainMilliAmps(previous.currentNowRaw),
                calibration.drainMilliAmps(current.currentNowRaw),
            ).filter { it > 0.0 }
            if (values.isEmpty()) continue

            result += DrainWindow(
                startMs = previous.timestampMs,
                endMs = current.timestampMs,
                milliAmps = values.average(),
                source = DrainSource.CURRENT_NOW,
                screen = screenRegime(listOf(previous, current)),
                startLevelPct = previous.levelPct,
                endLevelPct = current.levelPct,
                closeReason = WindowCloseReason.TIME_LIMIT,
                lowConfidence = true,
            )
        }
        return result
    }

    private class OpenWindow(private val first: BatterySnapshot) {
        private val members = mutableListOf(first)
        private var consumedUah = 0L
        private var last: BatterySnapshot = first

        fun add(previous: BatterySnapshot, current: BatterySnapshot) {
            val previousUah = previous.chargeCounterUah
            val currentUah = current.chargeCounterUah
            if (previousUah != null && currentUah != null) {
                // Delta negativo (contador subiu sem estar carregando) é incoerente e vale zero;
                // delta zero é contador travado e simplesmente não acumula.
                consumedUah += (previousUah - currentUah).coerceAtLeast(0L)
            }
            members += current
            last = current
        }

        fun tryClose(stepUah: Long, config: WindowConfig): DrainWindow? {
            val spanMs = last.timestampMs - first.timestampMs
            if (spanMs <= 0L) return null

            val effectiveStep = stepUah.coerceAtLeast(1L)
            val steps = (consumedUah / effectiveStep).toInt()

            val closeReason = when {
                steps >= config.minStepsToClose -> WindowCloseReason.STEPS_REACHED
                spanMs >= config.maxWindowMs && steps >= 1 -> WindowCloseReason.TIME_LIMIT
                else -> return null
            }

            val hours = spanMs / DrainWindow.MILLIS_PER_HOUR
            return DrainWindow(
                startMs = first.timestampMs,
                endMs = last.timestampMs,
                milliAmps = (consumedUah / 1000.0) / hours,
                source = DrainSource.CHARGE_COUNTER,
                screen = screenRegimeOf(members),
                startLevelPct = first.levelPct,
                endLevelPct = last.levelPct,
                consumedUah = consumedUah,
                stepsAccumulated = steps,
                quantizationStepUah = effectiveStep,
                closeReason = closeReason,
                lowConfidence = closeReason == WindowCloseReason.TIME_LIMIT,
            )
        }

        private fun screenRegimeOf(samples: List<BatterySnapshot>): ScreenRegime = when {
            samples.all { it.screenOn } -> ScreenRegime.ON
            samples.none { it.screenOn } -> ScreenRegime.OFF
            else -> ScreenRegime.MIXED
        }
    }

    private fun screenRegime(samples: List<BatterySnapshot>): ScreenRegime = when {
        samples.all { it.screenOn } -> ScreenRegime.ON
        samples.none { it.screenOn } -> ScreenRegime.OFF
        else -> ScreenRegime.MIXED
    }
}
