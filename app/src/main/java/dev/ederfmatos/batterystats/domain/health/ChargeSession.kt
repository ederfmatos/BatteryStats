package dev.ederfmatos.batterystats.domain.health

/**
 * Uma sessão de carga fechada, com a capacidade que ela implica.
 *
 * Medir capacidade **carregando** é muito melhor do que descarregando, e o motivo é o degrau de
 * quantização: durante a carga o CHARGE_COUNTER anda cerca de dez vezes mais rápido, então os
 * 4076 µAh que dominam qualquer janela curta de repouso viram ruído irrelevante. Uma sessão de
 * 30% → 90% entrega um número absoluto em algumas horas — contra os sete dias que a comparação
 * relativa precisa para dizer só "está pior que o melhor dia já visto".
 */
data class ChargeSession(
    val startMs: Long,
    val endMs: Long,
    val startLevelPct: Int,
    val endLevelPct: Int,
    val chargedUah: Long,
    val sampleCount: Int,
) {
    val durationMs: Long get() = endMs - startMs
    val levelGainPct: Int get() = endLevelPct - startLevelPct

    /**
     * Capacidade cheia implícita, em mAh: se subir [levelGainPct] pontos custou [chargedUah],
     * então 100 pontos custariam proporcionalmente.
     */
    val impliedFullCapacityMah: Double
        get() = if (levelGainPct > 0) (chargedUah / 1000.0) * (100.0 / levelGainPct) else 0.0

    /**
     * Incerteza em mAh imposta pela quantização do contador. Dois degraus: um em cada ponta da
     * sessão. Quanto maior a faixa de nível coberta, menor — é por isso que sessões curtas são
     * descartadas em vez de exibidas com ressalva.
     */
    fun uncertaintyMah(quantizationStepUah: Long): Double =
        if (levelGainPct > 0) {
            (2.0 * quantizationStepUah / 1000.0) * (100.0 / levelGainPct)
        } else {
            0.0
        }
}

/**
 * Extrai sessões de carga de uma série de amostras.
 *
 * Regra emprestada do AccuBattery e que existe por bom motivo: sessão que cobre pouco do ciclo é
 * descartada, não é exibida com ressalva. Com uma faixa estreita de nível, o erro relativo do
 * degrau explode e o número resultante oscila o suficiente para parecer que a bateria melhorou de
 * um dia para o outro — que é a origem daqueles prints de "saúde 256%" que circulam por aí.
 */
class ChargeSessionAnalyzer(
    private val minLevelGainPct: Int = DEFAULT_MIN_LEVEL_GAIN_PCT,
    private val maxGapMs: Long = DEFAULT_MAX_GAP_MS,
) {

    fun sessions(samples: List<UidChargeSample>): List<ChargeSession> {
        if (samples.size < 2) return emptyList()
        val ordered = samples.sortedBy { it.timestampMs }

        val sessions = mutableListOf<ChargeSession>()
        var open: MutableList<UidChargeSample>? = null

        fun close() {
            val members = open ?: return
            open = null
            if (members.size < 2) return
            val first = members.first()
            val last = members.last()
            val gain = last.levelPct - first.levelPct
            if (gain < minLevelGainPct) return

            val startUah = first.chargeCounterUah ?: return
            val endUah = last.chargeCounterUah ?: return
            val charged = endUah - startUah
            if (charged <= 0L) return

            sessions += ChargeSession(
                startMs = first.timestampMs,
                endMs = last.timestampMs,
                startLevelPct = first.levelPct,
                endLevelPct = last.levelPct,
                chargedUah = charged,
                sampleCount = members.size,
            )
        }

        for (sample in ordered) {
            val previous = open?.lastOrNull()
            val gapTooBig = previous != null &&
                sample.timestampMs - previous.timestampMs > maxGapMs

            when {
                !sample.isCharging || sample.chargeCounterUah == null -> close()

                // Um buraco no meio da carga inviabiliza a sessão: não dá para saber quanta carga
                // entrou enquanto ninguém estava medindo.
                gapTooBig -> {
                    close()
                    open = mutableListOf(sample)
                }

                else -> {
                    if (open == null) open = mutableListOf()
                    open?.add(sample)
                }
            }
        }
        close()

        return sessions
    }

    companion object {
        /**
         * Abaixo disso o erro relativo do degrau de quantização domina o resultado. O AccuBattery
         * usa 60% para a mesma finalidade; 40 é um meio-termo que produz mais sessões válidas sem
         * cair na faixa em que o número vira ruído.
         */
        const val DEFAULT_MIN_LEVEL_GAIN_PCT = 40

        /** Um buraco de amostragem no meio da carga invalida a sessão. */
        const val DEFAULT_MAX_GAP_MS = 10 * 60_000L
    }
}

/** O mínimo que o analisador precisa de uma amostra. Existe para o domínio não depender de Room. */
data class UidChargeSample(
    val timestampMs: Long,
    val levelPct: Int,
    val chargeCounterUah: Long?,
    val isCharging: Boolean,
)
