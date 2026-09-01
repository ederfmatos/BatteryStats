package dev.ederfmatos.batterystats.domain.model

/**
 * Como converter [BatterySnapshot.currentNowRaw] em miliampères com convenção fixa
 * (negativo = descarregando, positivo = carregando).
 *
 * [divisor] é 1000 quando o aparelho reporta em microampères (o documentado) e 1 quando reporta
 * direto em miliampères. [inverted] é true quando o aparelho usa positivo para descarga.
 * [confidence] indica se isso foi deduzido de dados reais, forçado pelo usuário ou é só o padrão.
 */
data class CurrentCalibration(
    val divisor: Int = 1000,
    val inverted: Boolean = false,
    val source: Source = Source.DEFAULT,
    val sampleCount: Int = 0,
) {
    enum class Source { DEFAULT, AUTO, MANUAL }

    fun toMilliAmps(raw: Long?): Double? {
        if (raw == null) return null
        val ma = raw.toDouble() / divisor
        return if (inverted) -ma else ma
    }

    /** Valor absoluto em mA do dreno; sempre positivo, independente da convenção do aparelho. */
    fun drainMilliAmps(raw: Long?): Double? = toMilliAmps(raw)?.let { kotlin.math.abs(it) }

    companion object {
        val DEFAULT = CurrentCalibration()
    }
}
