package dev.ederfmatos.batterystats.domain.report

import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import dev.ederfmatos.batterystats.domain.model.NetworkType
import java.util.Locale

/**
 * Monta o relatório em Markdown.
 *
 * A versão anterior deste app exportava 80 amostras cruas em 18 KB de JSON, e achar o degrau de
 * quantização ali dentro exigiu escrever scripts. Este relatório entrega as conclusões já
 * calculadas em 1 a 3 KB — inclusive o degrau, que é o número que muda a leitura de todo o resto.
 */
object ReportFormatter {

    /** A frase que acompanha o texto enviado, para quem receber saber o que está lendo. */
    const val PREAMBLE = """Relatório do meu app de monitoramento de bateria (Android, sideload).
Os dados são estimativa por correlação, não medição por app.
Leia as ressalvas no fim antes de concluir. O que dá para melhorar?"""

    fun format(report: BatteryReport, shortVersion: Boolean = false): String = buildString {
        appendLine("# Relatório de bateria")
        appendLine()
        appendDevice(report)
        appendLine()
        appendPeriod(report)
        appendLine()
        appendMeasurementQuality(report)
        appendLine()
        appendScreenSplit(report)
        appendLine()

        if (!shortVersion) {
            appendHourly(report)
            appendLine()
        }

        appendApps(report, limit = if (shortVersion) 5 else 10)
        appendLine()

        if (!shortVersion) {
            appendContext(report)
            appendLine()
        }

        appendCaveats(report)
    }

    /** Versão curta para caber num deeplink. Mantém cabeçalho, split, top 5 e ressalvas. */
    fun formatShort(report: BatteryReport): String = format(report, shortVersion = true)

    private fun StringBuilder.appendDevice(report: BatteryReport) {
        val device = report.device
        appendLine("## Aparelho")
        appendLine("- ${device.manufacturer} ${device.model}")
        appendLine("- Android ${device.androidRelease} (API ${device.sdkInt})")
        report.impliedCapacityMah?.let {
            appendLine("- Capacidade implícita mediana: ${fmt(it, 0)} mAh")
        }
    }

    private fun StringBuilder.appendPeriod(report: BatteryReport) {
        val hours = (report.periodEndMs - report.periodStartMs) / 3_600_000.0
        appendLine("## Período")
        appendLine("- Duração: ${fmt(hours, 1)} h")
        appendLine(
            "- Cobertura real: ${(report.coverage.fraction * 100).toInt()}% " +
                "(${report.coverage.gapCount} buracos, ${fmt(report.coverage.gapMs / 3_600_000.0, 1)} h sem medição)"
        )
    }

    private fun StringBuilder.appendMeasurementQuality(report: BatteryReport) {
        appendLine("## Qualidade da medição")
        appendLine(
            "- Degrau de quantização do CHARGE_COUNTER: ${report.quantizationStepUah} µAh " +
                "(±${fmt(report.quantizationUncertaintyMilliAmps, 0)} mA no intervalo de " +
                "${report.samplingIntervalMs / 1000} s)"
        )
        appendLine(
            "- Janelas: ${report.windowCount}, das quais ${report.highConfidenceWindowCount} de alta confiança"
        )
        val unit = if (report.calibration.divisor == 1000) "µA" else "mA"
        val sign = if (report.calibration.inverted) "invertido" else "documentado"
        val source = when (report.calibration.source) {
            CurrentCalibration.Source.AUTO -> "detectada automaticamente"
            CurrentCalibration.Source.MANUAL -> "forçada manualmente"
            CurrentCalibration.Source.DEFAULT -> "não calibrada (padrão)"
        }
        appendLine("- CURRENT_NOW: reportado em $unit, sinal $sign — $source")
    }

    private fun StringBuilder.appendScreenSplit(report: BatteryReport) {
        appendLine("## Tela ligada vs desligada")
        appendLine()
        appendLine("| Regime | Horas | mAh | mA médio | Janelas de alta confiança |")
        appendLine("|---|---|---|---|---|")
        appendRegime("Ligada", report.screenOn)
        appendRegime("Desligada", report.screenOff)
        appendLine()
        appendLine("- Total: ${fmt(report.totalMilliAmpHours, 0)} mAh")
        report.idleBaselineMilliAmps?.let {
            appendLine(
                "- Baseline de repouso (percentil 10 com tela desligada, janelas ≥ 300 s): " +
                    "${fmt(it, 0)} mA"
            )
        }
    }

    private fun StringBuilder.appendRegime(
        label: String,
        regime: dev.ederfmatos.batterystats.domain.drain.RegimeStats,
    ) {
        val hours = regime.durationMs / 3_600_000.0
        val mah = regime.averageMilliAmps * hours
        appendLine(
            "| $label | ${fmt(hours, 1)} | ${fmt(mah, 0)} | ${fmt(regime.averageMilliAmps, 0)} " +
                "| ${regime.highConfidenceWindowCount} |"
        )
    }

    private fun StringBuilder.appendHourly(report: BatteryReport) {
        if (report.hourly.isEmpty()) return
        appendLine("## Dreno por hora do dia (7 dias)")
        appendLine()
        appendLine("| Hora | mA médio |")
        appendLine("|---|---|")
        report.hourly.sortedBy { it.hourOfDay }.forEach { hour ->
            appendLine("| ${hour.hourOfDay.toString().padStart(2, '0')}h | ${fmt(hour.averageMilliAmps, 0)} |")
        }
    }

    private fun StringBuilder.appendApps(report: BatteryReport, limit: Int) {
        appendLine("## Consumo estimado por app")
        appendLine()
        appendLine("| App | mAh | Min. em 1º plano | mA médio | Confiança |")
        appendLine("|---|---|---|---|---|")
        report.topApps.take(limit).forEach { usage -> appendApp(usage) }
        report.systemBucket?.let { bucket ->
            appendLine(
                "| **Sistema / segundo plano** | ${fmt(bucket.estimatedMilliAmpHours, 0)} | — | — | — |"
            )
        }
        appendLine()
        appendLine(
            "> Estimativa por correlação: dreno medido da janela × app em primeiro plano naquela " +
                "janela. Não é medição de consumo por app."
        )
    }

    private fun StringBuilder.appendApp(usage: AppEnergyUsage) {
        val minutes = usage.foregroundMs / 60_000
        // Janelas de baixa confiança não alimentam a atribuição, então tudo aqui é de alta.
        appendLine(
            "| ${usage.packageName} | ${fmt(usage.estimatedMilliAmpHours, 0)} | $minutes " +
                "| ${fmt(usage.averageMilliAmpsInForeground, 0)} | alta |"
        )
    }

    private fun StringBuilder.appendContext(report: BatteryReport) {
        val context = report.context
        appendLine("## Contexto")
        context.screenBrightness?.let { appendLine("- Brilho médio: $it/255") }
        context.autoBrightnessFraction?.let {
            appendLine("- Brilho automático ativo em ${(it * 100).toInt()}% do tempo")
        }
        if (context.networkShare.isNotEmpty()) {
            val share = context.networkShare.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${label(it.key)} ${(it.value * 100).toInt()}%" }
            appendLine("- Rede: $share")
        }
        context.locationEnabledFraction?.let {
            appendLine("- Localização ativa em ${(it * 100).toInt()}% do tempo")
        }
        if (context.temperatureMinCelsius != null && context.temperatureMaxCelsius != null) {
            appendLine(
                "- Temperatura: ${fmt(context.temperatureMinCelsius, 1)}–" +
                    "${fmt(context.temperatureMaxCelsius, 1)} °C"
            )
        }
    }

    private fun StringBuilder.appendCaveats(report: BatteryReport) {
        val caveats = ReportCaveats.generate(report)
        appendLine("## Ressalvas")
        if (caveats.isEmpty()) {
            appendLine("- Nenhuma: cobertura boa, janelas confiáveis e permissões concedidas.")
        } else {
            caveats.forEach { appendLine("- ${it.text}") }
        }
    }

    private fun label(type: NetworkType): String = when (type) {
        NetworkType.WIFI -> "Wi-Fi"
        NetworkType.CELLULAR -> "celular"
        NetworkType.OTHER -> "outra"
        NetworkType.NONE -> "sem rede"
        NetworkType.UNKNOWN -> "desconhecida"
    }

    private fun fmt(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)
}
