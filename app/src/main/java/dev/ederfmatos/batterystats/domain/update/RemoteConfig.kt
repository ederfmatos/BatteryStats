package dev.ederfmatos.batterystats.domain.update

import dev.ederfmatos.batterystats.domain.drain.WindowConfig

/** Ajuste de calibração conhecido para um modelo específico, vindo da config remota. */
data class DeviceOverride(
    val currentNowDivisor: Int,
    val currentNowSignInverted: Boolean,
)

/**
 * Os parâmetros que mais mudam, servidos junto da Release.
 *
 * Existe para que ajustar um limiar não exija um APK novo: descobrir que o degrau de quantização de
 * um modelo pede uma janela maior é o tipo de coisa que se aprende olhando dados, não escrevendo
 * código.
 */
data class RemoteConfig(
    val configVersion: Int,
    val minStepsToClose: Int,
    val maxWindowMs: Long,
    val idleBaselineMaxMA: Double,
    val highIdleWarnMA: Double,
    val samplingIntervalMs: Long,
    val deviceOverrides: Map<String, DeviceOverride>,
) {
    fun windowConfig(): WindowConfig = WindowConfig(
        minStepsToClose = minStepsToClose,
        maxWindowMs = maxWindowMs,
    )

    /** A chave é "fabricante/modelo", tudo minúsculo. */
    fun overrideFor(manufacturer: String, model: String): DeviceOverride? =
        deviceOverrides["${manufacturer.lowercase()}/${model.lowercase()}"]

    companion object {
        /** Os valores compilados. É para cá que se volta quando a config remota some ou quebra. */
        val COMPILED_DEFAULT = RemoteConfig(
            configVersion = 0,
            minStepsToClose = WindowConfig.DEFAULT_MIN_STEPS,
            maxWindowMs = WindowConfig.DEFAULT_MAX_WINDOW_MS,
            idleBaselineMaxMA = 120.0,
            highIdleWarnMA = 150.0,
            samplingIntervalMs = 60_000L,
            deviceOverrides = emptyMap(),
        )
    }
}
