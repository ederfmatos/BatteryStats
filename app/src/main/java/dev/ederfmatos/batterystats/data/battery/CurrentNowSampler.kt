package dev.ederfmatos.batterystats.data.battery

import kotlinx.coroutines.delay

/**
 * Tira várias leituras de CURRENT_NOW e devolve todas para que o chamador use a mediana.
 *
 * Uma leitura única sofre **viés do observador**: ela acontece no exato instante em que o app
 * acordou o aparelho para amostrar, e portanto mede o próprio custo da amostragem junto com o
 * consumo real. Em janelas longas de tela desligada, o valor instantâneo saiu de 2 a 4 vezes maior
 * que o dreno derivado do contador de carga — 119 contra 40 mA reais num caso, 287 contra 65 em
 * outro.
 *
 * A espera inicial deixa o pico de acordar passar; a mediana de várias leituras descarta o que
 * sobrou de transiente. Mesmo assim, isto serve para o número ao vivo na tela — a fonte de verdade
 * dos relatórios continua sendo Δ CHARGE_COUNTER em janela adaptativa.
 */
class CurrentNowSampler(
    private val reader: AndroidBatteryReader,
    private val settleMs: Long = DEFAULT_SETTLE_MS,
    private val spacingMs: Long = DEFAULT_SPACING_MS,
    private val readings: Int = DEFAULT_READINGS,
) {

    suspend fun sample(): List<Long> {
        delay(settleMs)
        val values = mutableListOf<Long>()
        repeat(readings) { index ->
            reader.readCurrentNowRaw()?.let { values += it }
            if (index < readings - 1) delay(spacingMs)
        }
        return values
    }

    companion object {
        /** Tempo para o pico de acordar o aparelho passar antes da primeira leitura. */
        const val DEFAULT_SETTLE_MS = 800L
        const val DEFAULT_SPACING_MS = 150L
        const val DEFAULT_READINGS = 5
    }
}
