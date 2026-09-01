package dev.ederfmatos.batterystats.domain.health

/**
 * Estimativa **relativa** de saúde da bateria.
 *
 * Não existe API pública que devolva a capacidade de projeto do aparelho. O que dá para fazer é
 * comparar o CHARGE_COUNTER observado perto de 100% com o maior valor que o app já viu perto de
 * 100%: se o aparelho carregava até 4200 mAh e hoje só chega a 3700, a bateria perdeu capacidade
 * relativa. Isso é uma tendência dentro do histórico do próprio app, não um percentual absoluto
 * de "saúde" comparável com o de fábrica.
 */
data class BatteryHealthEstimate(
    val currentFullChargeUah: Long?,
    val bestObservedFullChargeUah: Long?,
    val relativeRatio: Double?,
    val observationDays: Int,
) {
    val hasEnoughHistory: Boolean get() = observationDays >= MIN_OBSERVATION_DAYS

    companion object {
        /** Abaixo disso a comparação é ruído: o contador varia com temperatura e com o carregador. */
        const val MIN_OBSERVATION_DAYS = 7

        /** Só leituras acima deste nível contam como "bateria cheia". */
        const val FULL_LEVEL_THRESHOLD_PCT = 95
    }
}
