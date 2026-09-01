package dev.ederfmatos.batterystats.domain.drain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import kotlin.math.abs

/**
 * Descobre a unidade e o sinal de CURRENT_NOW comparando-o com o dreno derivado do CHARGE_COUNTER.
 *
 * A ideia: o contador de carga não mente sobre magnitude. Se em uma janela de descarga o contador
 * diz 300 mA e CURRENT_NOW diz -300000, o aparelho reporta em microampères com o sinal documentado.
 * Se diz -300, reporta em miliampères. Se diz +300000, reporta em microampères com o sinal
 * invertido.
 *
 * Só funciona em aparelhos onde o contador se move. Onde ele fica travado não há como calibrar
 * automaticamente — o resultado fica em [CurrentCalibration.Source.DEFAULT] e o usuário pode
 * forçar manualmente em Configurações → Diagnóstico.
 */
class CurrentCalibrator(
    private val drainCalculator: DrainCalculator = DrainCalculator(),
    private val minPairs: Int = DEFAULT_MIN_PAIRS,
) {

    fun calibrate(samples: List<BatterySnapshot>): CurrentCalibration? {
        if (samples.size < 2) return null
        val ordered = samples.sortedBy { it.timestampMs }

        val ratios = mutableListOf<Double>()
        val rawSigns = mutableListOf<Long>()

        // Roda com a calibração default só para reaproveitar o filtro de janelas válidas; o valor
        // de CURRENT_NOW usado aqui é sempre o cru, nunca o convertido.
        val analysis = drainCalculator.analyze(ordered, CurrentCalibration.DEFAULT)
        val counterWindows = analysis.windows.filter { it.source == DrainSource.CHARGE_COUNTER }
        if (counterWindows.isEmpty()) return null

        val byStart = ordered.associateBy { it.timestampMs }
        for (window in counterWindows) {
            if (window.milliAmps <= 0.0) continue
            val start = byStart[window.startMs] ?: continue
            val end = byStart[window.endMs] ?: continue
            val raws = listOfNotNull(start.currentNowRaw, end.currentNowRaw).filter { it != 0L }
            if (raws.isEmpty()) continue

            val rawAvg = raws.map { it.toDouble() }.average()
            ratios += abs(rawAvg) / window.milliAmps
            rawSigns += if (rawAvg >= 0) 1L else -1L
        }

        if (ratios.size < minPairs) return null

        val medianRatio = ratios.median()
        val divisor = if (medianRatio >= MICRO_AMP_THRESHOLD) 1000 else 1

        // Durante descarga o sinal documentado é negativo. Maioria positiva ⇒ aparelho invertido.
        val positives = rawSigns.count { it > 0 }
        val inverted = positives * 2 > rawSigns.size

        return CurrentCalibration(
            divisor = divisor,
            inverted = inverted,
            source = CurrentCalibration.Source.AUTO,
            sampleCount = ratios.size,
        )
    }

    companion object {
        const val DEFAULT_MIN_PAIRS = 5

        /**
         * A razão |CURRENT_NOW| / mA fica perto de 1000 em microampères e perto de 1 em
         * miliampères. 100 é uma fronteira folgada o bastante para absorver ruído sem risco de
         * confundir os dois regimes.
         */
        const val MICRO_AMP_THRESHOLD = 100.0
    }
}
