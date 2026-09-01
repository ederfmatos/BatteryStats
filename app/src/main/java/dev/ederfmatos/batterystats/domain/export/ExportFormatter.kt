package dev.ederfmatos.batterystats.domain.export

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot

/**
 * Serializa amostras e agregados. Kotlin puro e sem dependência de biblioteca de JSON: o formato
 * é uma lista plana de campos escalares, escrever à mão é menos código do que configurar um
 * serializador.
 */
object ExportFormatter {

    private val SAMPLE_COLUMNS = listOf(
        "timestampMs", "levelPct", "chargeCounterUah", "currentNowRaw",
        "temperatureDeciC", "voltageMv", "status", "plugType", "screenOn", "foregroundPackage",
    )

    fun samplesToCsv(samples: List<BatterySnapshot>): String = buildString {
        appendLine(SAMPLE_COLUMNS.joinToString(","))
        for (sample in samples) {
            appendLine(
                listOf(
                    sample.timestampMs.toString(),
                    sample.levelPct.toString(),
                    sample.chargeCounterUah?.toString().orEmpty(),
                    sample.currentNowRaw?.toString().orEmpty(),
                    sample.temperatureDeciC?.toString().orEmpty(),
                    sample.voltageMv?.toString().orEmpty(),
                    sample.status.name,
                    sample.plugType.name,
                    if (sample.screenOn) "1" else "0",
                    csvEscape(sample.foregroundPackage.orEmpty()),
                ).joinToString(",")
            )
        }
    }

    fun samplesToJson(samples: List<BatterySnapshot>): String = buildString {
        appendLine("[")
        samples.forEachIndexed { index, sample ->
            append("  {")
            append(""""timestampMs":${sample.timestampMs},""")
            append(""""levelPct":${sample.levelPct},""")
            append(""""chargeCounterUah":${sample.chargeCounterUah ?: "null"},""")
            append(""""currentNowRaw":${sample.currentNowRaw ?: "null"},""")
            append(""""temperatureDeciC":${sample.temperatureDeciC ?: "null"},""")
            append(""""voltageMv":${sample.voltageMv ?: "null"},""")
            append(""""status":"${sample.status.name}",""")
            append(""""plugType":"${sample.plugType.name}",""")
            append(""""screenOn":${sample.screenOn},""")
            append(""""foregroundPackage":${jsonStringOrNull(sample.foregroundPackage)}""")
            append("}")
            if (index != samples.lastIndex) append(",")
            appendLine()
        }
        append("]")
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun jsonStringOrNull(value: String?): String =
        if (value == null) "null" else "\"" + jsonEscape(value) + "\""

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
