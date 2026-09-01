package dev.ederfmatos.batterystats.domain.report

import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.domain.attribution.SYSTEM_BUCKET_PACKAGE
import dev.ederfmatos.batterystats.domain.drain.Coverage
import dev.ederfmatos.batterystats.domain.drain.HourlyDrain
import dev.ederfmatos.batterystats.domain.drain.RegimeStats
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import dev.ederfmatos.batterystats.domain.model.NetworkType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatterTest {

    private fun report(
        coverage: Coverage = Coverage(24 * 3_600_000L, 0L, 0),
        quantizationStepUah: Long = 4076L,
        windowCount: Int = 100,
        highConfidence: Int = 90,
        hasUsageAccess: Boolean = true,
        calibration: CurrentCalibration = CurrentCalibration(
            divisor = 1,
            inverted = false,
            source = CurrentCalibration.Source.AUTO,
            sampleCount = 12,
        ),
    ) = BatteryReport(
        device = DeviceInfo("samsung", "SM-S911B", "15", 35),
        impliedCapacityMah = 4130.0,
        periodStartMs = 0L,
        periodEndMs = 7 * 86_400_000L,
        coverage = coverage,
        quantizationStepUah = quantizationStepUah,
        samplingIntervalMs = 60_000L,
        calibration = calibration,
        screenOn = RegimeStats(829.0, 12.0, 4 * 3_600_000L, 40, 38, 20.0),
        screenOff = RegimeStats(60.0, 1.2, 20 * 3_600_000L, 60, 52, 60.0),
        totalMilliAmpHours = 1357.0,
        idleBaselineMilliAmps = 58.0,
        hourly = (0..23).map { HourlyDrain(it, 50.0 + it, 3_600_000L) },
        topApps = listOf(
            AppEnergyUsage("com.exemplo.mapa", 420.0, 45 * 60_000L, 560.0),
            AppEnergyUsage("com.exemplo.chat", 180.0, 30 * 60_000L, 360.0),
        ),
        systemBucket = AppEnergyUsage(SYSTEM_BUCKET_PACKAGE, 757.0, 0L, 0.0, isSystemBucket = true),
        context = ContextAverages(
            screenBrightness = 180,
            autoBrightnessFraction = 0.8,
            networkShare = mapOf(NetworkType.CELLULAR to 0.7, NetworkType.WIFI to 0.3),
            locationEnabledFraction = 0.45,
            temperatureMinCelsius = 28.0,
            temperatureMaxCelsius = 39.3,
        ),
        hasUsageAccess = hasUsageAccess,
        windowCount = windowCount,
        highConfidenceWindowCount = highConfidence,
    )

    @Test
    fun `o relatorio cabe na faixa de 1 a 3 KB`() {
        val markdown = ReportFormatter.format(report())

        val bytes = markdown.toByteArray(Charsets.UTF_8).size
        assertTrue("relatório com $bytes bytes", bytes in 1_000..3_500)
    }

    @Test
    fun `traz o degrau de quantizacao e a incerteza resultante`() {
        val markdown = ReportFormatter.format(report())

        assertTrue(markdown.contains("4076 µAh"))
        // 4076 µAh em 60s = 244,56 mA de incerteza.
        assertTrue(markdown.contains("±245 mA") || markdown.contains("±244 mA"))
    }

    @Test
    fun `o bucket de sistema aparece sempre`() {
        val markdown = ReportFormatter.format(report())

        assertTrue(markdown.contains("Sistema / segundo plano"))
    }

    @Test
    fun `deixa explicito que a atribuicao e estimativa`() {
        val markdown = ReportFormatter.format(report())

        assertTrue(markdown.contains("Estimativa por correlação"))
        assertTrue(markdown.contains("Não é medição de consumo por app"))
    }

    @Test
    fun `cobertura baixa vira ressalva`() {
        val poor = report(coverage = Coverage(100 * 3_600_000L, 59 * 3_600_000L, 14))

        val markdown = ReportFormatter.format(poor)

        assertTrue(markdown.contains("Cobertura de 41%"))
    }

    @Test
    fun `excesso de janelas grosseiras vira ressalva`() {
        val coarse = report(windowCount = 100, highConfidence = 40)

        val markdown = ReportFormatter.format(coarse)

        assertTrue(markdown.contains("60% das janelas são de baixa confiança"))
    }

    @Test
    fun `falta de permissao de uso vira ressalva`() {
        val markdown = ReportFormatter.format(report(hasUsageAccess = false))

        assertTrue(markdown.contains("Permissão de acesso ao uso não concedida"))
    }

    @Test
    fun `calibracao nao feita vira ressalva`() {
        val markdown = ReportFormatter.format(
            report(calibration = CurrentCalibration.DEFAULT)
        )

        assertTrue(markdown.contains("não foi calibrado"))
    }

    @Test
    fun `o degrau real do aparelho sempre dispara a ressalva de quantizacao`() {
        // 4076 µAh em 60 s dão ±245 mA. Num aparelho assim a ressalva é permanente, e isso é
        // correto: janelas curtas ali medem arredondamento.
        val markdown = ReportFormatter.format(report())

        assertTrue(markdown.contains("Degrau de quantização grosseiro"))
    }

    @Test
    fun `contador fino e cobertura boa nao inventam ressalvas`() {
        // Aparelho cujo contador anda de 100 µAh em 100 µAh: ±6 mA no intervalo de 60 s.
        val markdown = ReportFormatter.format(report(quantizationStepUah = 100L))

        assertTrue(markdown.contains("Nenhuma: cobertura boa"))
    }

    @Test
    fun `versao curta corta o detalhamento mas mantem as ressalvas`() {
        val full = ReportFormatter.format(report(hasUsageAccess = false))
        val short = ReportFormatter.formatShort(report(hasUsageAccess = false))

        assertTrue(short.length < full.length)
        assertTrue(short.contains("Tela ligada vs desligada"))
        assertTrue(short.contains("Permissão de acesso ao uso não concedida"))
        assertFalse(short.contains("Dreno por hora do dia"))
    }
}
