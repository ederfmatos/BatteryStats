package dev.ederfmatos.batterystats.domain.update

import org.json.JSONException
import org.json.JSONObject

/**
 * Lê o `config.json`.
 *
 * Config malformada é ignorada **em silêncio**, mantendo a anterior: um JSON quebrado publicado por
 * engano não pode virar uma janela de medição de zero milissegundo no aparelho de ninguém. Cada
 * campo é validado dentro de uma faixa plausível, não só quanto ao tipo.
 */
object RemoteConfigParser {

    fun parse(json: String): RemoteConfig? = try {
        val root = JSONObject(json)

        val minSteps = root.getInt("minStepsToClose")
        val maxWindowMs = root.getLong("maxWindowMs")
        val samplingIntervalMs = root.getLong("samplingIntervalMs")
        val idleBaselineMaxMA = root.getDouble("idleBaselineMaxMA")
        val highIdleWarnMA = root.getDouble("highIdleWarnMA")

        require(minSteps in MIN_STEPS_RANGE) { "minStepsToClose fora da faixa" }
        require(maxWindowMs in MAX_WINDOW_RANGE) { "maxWindowMs fora da faixa" }
        require(samplingIntervalMs in SAMPLING_RANGE) { "samplingIntervalMs fora da faixa" }
        require(idleBaselineMaxMA in MA_RANGE) { "idleBaselineMaxMA fora da faixa" }
        require(highIdleWarnMA in MA_RANGE) { "highIdleWarnMA fora da faixa" }

        RemoteConfig(
            configVersion = root.getInt("configVersion"),
            minStepsToClose = minSteps,
            maxWindowMs = maxWindowMs,
            idleBaselineMaxMA = idleBaselineMaxMA,
            highIdleWarnMA = highIdleWarnMA,
            samplingIntervalMs = samplingIntervalMs,
            deviceOverrides = parseOverrides(root.optJSONObject("deviceOverrides")),
        )
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun parseOverrides(node: JSONObject?): Map<String, DeviceOverride> {
        if (node == null) return emptyMap()
        val result = mutableMapOf<String, DeviceOverride>()
        for (key in node.keys()) {
            val entry = node.optJSONObject(key) ?: continue
            val unit = entry.optString("currentNowUnit", "uA").lowercase()
            val divisor = when (unit) {
                "ma" -> 1
                "ua", "µa" -> 1000
                else -> continue
            }
            result[key.lowercase()] = DeviceOverride(
                currentNowDivisor = divisor,
                currentNowSignInverted = entry.optBoolean("currentNowSignInverted", false),
            )
        }
        return result
    }

    private val MIN_STEPS_RANGE = 1..64
    private val MAX_WINDOW_RANGE = 60_000L..3_600_000L
    private val SAMPLING_RANGE = 15_000L..900_000L
    private val MA_RANGE = 1.0..5_000.0
}
