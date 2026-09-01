package dev.ederfmatos.batterystats.domain.drain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import kotlin.math.abs

/**
 * Descobre a unidade e o sinal de CURRENT_NOW comparando-o com o dreno derivado do CHARGE_COUNTER.
 *
 * A ideia: o contador de carga não mente sobre magnitude. Se numa janela de descarga ele diz
 * 300 mA e CURRENT_NOW diz -300000, o aparelho reporta em microampères com o sinal documentado.
 * Se diz -300, reporta em miliampères. Se diz +300000, microampères com o sinal invertido.
 *
 * Usa apenas janelas adaptativas de **alta confiança**: comparar contra uma janela grosseira, que
 * pode estar a um degrau inteiro de distância do valor real, produziria uma razão sem sentido.
 *
 * Onde o contador fica travado não há como calibrar automaticamente — o resultado fica em
 * [CurrentCalibration.Source.DEFAULT] e o usuário pode forçar em Configurações → Diagnóstico.
 */
class CurrentCalibrator(
    private val windowBuilder: AdaptiveWindowBuilder = AdaptiveWindowBuilder(),
    private val minWindows: Int = DEFAULT_MIN_WINDOWS,
) {

    fun calibrate(
        samples: List<BatterySnapshot>,
        gaps: List<MeasurementGap> = emptyList(),
    ): CurrentCalibration? {
        if (samples.size < 2) return null
        val ordered = samples.sortedBy { it.timestampMs }

        val windows = windowBuilder.analyze(ordered, gaps)
            .windows
            .filter { it.source == DrainSource.CHARGE_COUNTER && !it.lowConfidence }
        if (windows.isEmpty()) return null

        val ratios = mutableListOf<Double>()
        var positiveWindows = 0

        for (window in windows) {
            if (window.milliAmps <= 0.0) continue
            val raws = ordered
                .filter { it.timestampMs in window.startMs..window.endMs }
                .mapNotNull { it.currentNowRaw }
                .filter { it != 0L }
            if (raws.isEmpty()) continue

            val rawMedian = raws.map { it.toDouble() }.medianOrNull() ?: continue
            ratios += abs(rawMedian) / window.milliAmps
            if (rawMedian >= 0) positiveWindows++
        }

        if (ratios.size < minWindows) return null

        val medianRatio = ratios.medianOrNull() ?: return null
        val divisor = if (medianRatio >= MICRO_AMP_THRESHOLD) 1000 else 1

        // Durante descarga o sinal documentado é negativo. Maioria positiva ⇒ aparelho invertido.
        val inverted = positiveWindows * 2 > ratios.size

        return CurrentCalibration(
            divisor = divisor,
            inverted = inverted,
            source = CurrentCalibration.Source.AUTO,
            sampleCount = ratios.size,
        )
    }

    companion object {
        const val DEFAULT_MIN_WINDOWS = 3

        /**
         * A razão |CURRENT_NOW| / mA fica perto de 1000 em microampères e perto de 1 em
         * miliampères. 100 é uma fronteira folgada o bastante para absorver ruído sem risco de
         * confundir os dois regimes.
         */
        const val MICRO_AMP_THRESHOLD = 100.0
    }
}
