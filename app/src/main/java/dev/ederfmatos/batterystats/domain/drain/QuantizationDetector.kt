package dev.ederfmatos.batterystats.domain.drain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot

/**
 * Descobre o degrau em que o aparelho move o CHARGE_COUNTER.
 *
 * O contador não é contínuo: os aparelhos reportam a carga em múltiplos de um degrau fixo — num
 * Galaxy medido em campo, 4076 µAh, ou 0,099% da capacidade. Numa janela de 60 s um único degrau
 * aparece como 245 mA, então toda medição de janela curta vira múltiplo de 244 mA. Isso é
 * arredondamento, não consumo, e envenena qualquer atribuição por app.
 *
 * O degrau é o **MDC** dos deltas não-nulos observados: se o contador só anda de 4076 em 4076,
 * todo delta é múltiplo de 4076 e o MDC devolve exatamente esse valor.
 *
 * Com poucos deltas o MDC pode sair **maior** que o degrau real — dois saltos de 8152 dão MDC 8152.
 * Isso é aceitável de propósito: superestimar o degrau faz as janelas fecharem mais tarde e a
 * incerteza reportada ficar maior, que é o lado seguro do erro. Subestimar traria de volta o bug
 * original, em que janelas de 60 s pareciam medição.
 */
class QuantizationDetector(private val maxSamples: Int = DEFAULT_MAX_SAMPLES) {

    /**
     * Devolve o degrau em µAh, ou null quando o contador não se mexeu nenhuma vez — nesse caso não
     * há o que estimar, e o dreno terá de vir de outra fonte.
     *
     * Um resultado de 1 significa "sem quantização perceptível": o contador anda de µAh em µAh.
     */
    fun detectStepUah(samples: List<BatterySnapshot>): Long? {
        val recent = samples
            .sortedBy { it.timestampMs }
            .takeLast(maxSamples)

        var step = 0L
        var deltaCount = 0

        for (index in 1 until recent.size) {
            val previous = recent[index - 1].chargeCounterUah ?: continue
            val current = recent[index].chargeCounterUah ?: continue
            val delta = kotlin.math.abs(current - previous)
            if (delta == 0L) continue
            step = gcd(step, delta)
            deltaCount++
            // MDC 1 já não vai diminuir mais; parar cedo evita varrer o resto à toa.
            if (step == 1L) break
        }

        return if (deltaCount >= MIN_DELTAS) step else null
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    companion object {
        const val DEFAULT_MAX_SAMPLES = 200

        /**
         * Um único delta já é uma estimativa utilizável — e, pelo argumento acima, uma estimativa
         * que erra para o lado conservador.
         */
        const val MIN_DELTAS = 1

        /**
         * Usado quando o contador nunca se moveu. Assume contador contínuo porque não há degrau
         * observado; nesse cenário o dreno vem de CURRENT_NOW e sempre em baixa confiança.
         */
        const val FALLBACK_STEP_UAH = 1L
    }
}
