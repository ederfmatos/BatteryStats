package dev.ederfmatos.batterystats.domain.health

/**
 * Saúde da bateria em números absolutos, quando há base para isso.
 *
 * Diferente da [BatteryHealthEstimate], que só compara o CHARGE_COUNTER de hoje com o maior já
 * visto pelo próprio app. Aqui o denominador é a capacidade que o **aparelho declara**, e o
 * numerador é a capacidade **medida numa sessão de carga longa**.
 */
data class AbsoluteHealth(
    /** Mediana da capacidade implícita das sessões válidas, em mAh. */
    val measuredCapacityMah: Double?,
    /** `battery.capacity` do power_profile.xml do aparelho, em mAh. */
    val declaredCapacityMah: Double?,
    /** Incerteza da medição, em mAh, imposta pela quantização do contador. */
    val uncertaintyMah: Double,
    val sessionCount: Int,
    /** `EXTRA_CYCLE_COUNT` do broadcast de bateria, quando o aparelho reporta. */
    val cycleCount: Int?,
) {
    /** Fração da capacidade declarada que a bateria ainda entrega. Null sem os dois números. */
    val healthFraction: Double?
        get() {
            val measured = measuredCapacityMah ?: return null
            val declared = declaredCapacityMah ?: return null
            if (declared <= 0.0) return null
            return measured / declared
        }

    val healthPercent: Double? get() = healthFraction?.let { it * 100.0 }

    /**
     * Uma única sessão é um ponto, não uma tendência: a capacidade implícita varia com temperatura
     * e com o carregador usado. Abaixo de [MIN_SESSIONS] o número aparece como faixa.
     */
    val isPreliminary: Boolean get() = sessionCount < MIN_SESSIONS

    val rangeLowMah: Double? get() = measuredCapacityMah?.minus(uncertaintyMah)
    val rangeHighMah: Double? get() = measuredCapacityMah?.plus(uncertaintyMah)

    companion object {
        const val MIN_SESSIONS = 3
        val EMPTY = AbsoluteHealth(null, null, 0.0, 0, null)
    }
}

object AbsoluteHealthCalculator {

    /**
     * Usa a **mediana** das sessões, e não a média: uma sessão com o aparelho quente, ou com um
     * carregador que corta cedo, produz um valor fora da curva que a média arrastaria junto.
     */
    fun calculate(
        sessions: List<ChargeSession>,
        declaredCapacityMah: Double?,
        quantizationStepUah: Long,
        cycleCount: Int?,
    ): AbsoluteHealth {
        if (sessions.isEmpty()) {
            return AbsoluteHealth(
                measuredCapacityMah = null,
                declaredCapacityMah = declaredCapacityMah,
                uncertaintyMah = 0.0,
                sessionCount = 0,
                cycleCount = cycleCount,
            )
        }

        val capacities = sessions.map { it.impliedFullCapacityMah }.sorted()
        val median = capacities[capacities.size / 2]
        // A incerteza da estimativa é a da melhor sessão: a que cobriu a maior faixa de nível.
        val bestSession = sessions.maxByOrNull { it.levelGainPct }

        return AbsoluteHealth(
            measuredCapacityMah = median,
            declaredCapacityMah = declaredCapacityMah,
            uncertaintyMah = bestSession?.uncertaintyMah(quantizationStepUah) ?: 0.0,
            sessionCount = sessions.size,
            cycleCount = cycleCount,
        )
    }
}
