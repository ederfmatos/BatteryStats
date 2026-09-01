package dev.ederfmatos.batterystats.domain.report

/** Uma ressalva sobre onde o dado é fraco. */
data class Caveat(val text: String)

/**
 * Gera automaticamente as ressalvas do fim do relatório.
 *
 * Quem for analisar precisa saber onde o dado é fraco **antes** de concluir. Um relatório com 41%
 * de cobertura e 80% de janelas grosseiras parece igual a um bom relatório se ninguém disser.
 */
object ReportCaveats {

    const val LOW_COVERAGE_THRESHOLD = 0.70
    const val LOW_CONFIDENCE_THRESHOLD = 0.30

    /** Acima disso um degrau único já domina o valor de uma janela do intervalo de amostragem. */
    const val COARSE_STEP_UNCERTAINTY_MA = 100.0

    fun generate(report: BatteryReport): List<Caveat> {
        val caveats = mutableListOf<Caveat>()

        if (report.coverage.fraction < LOW_COVERAGE_THRESHOLD) {
            caveats += Caveat(
                "Cobertura de ${percent(report.coverage.fraction)} do período: " +
                    "${report.coverage.gapCount} buracos de medição. Os agregados descrevem apenas " +
                    "o tempo em que o app estava vivo, não o período inteiro."
            )
        }

        if (report.lowConfidenceFraction > LOW_CONFIDENCE_THRESHOLD) {
            caveats += Caveat(
                "${percent(report.lowConfidenceFraction)} das janelas são de baixa confiança " +
                    "(fecharam por tempo com um único degrau do contador). Elas entram nas médias " +
                    "mas não alimentam o ranking por app."
            )
        }

        if (report.quantizationUncertaintyMilliAmps > COARSE_STEP_UNCERTAINTY_MA) {
            caveats += Caveat(
                "Degrau de quantização grosseiro: ${report.quantizationStepUah} µAh, o que dá " +
                    "±${report.quantizationUncertaintyMilliAmps.toInt()} mA no intervalo de " +
                    "amostragem. Valores de janelas curtas são arredondamento, não medição."
            )
        }

        if (!report.hasUsageAccess) {
            caveats += Caveat(
                "Permissão de acesso ao uso não concedida: nenhum consumo pôde ser atribuído a um " +
                    "app específico. Tudo está no bucket de sistema."
            )
        }

        if (report.calibration.source == dev.ederfmatos.batterystats.domain.model
                .CurrentCalibration.Source.DEFAULT
        ) {
            caveats += Caveat(
                "CURRENT_NOW não foi calibrado neste aparelho; o valor instantâneo pode estar " +
                    "em unidade ou sinal errados. Não afeta os números derivados do contador de carga."
            )
        }

        return caveats
    }

    private fun percent(fraction: Double): String = "${(fraction * 100).toInt()}%"
}
