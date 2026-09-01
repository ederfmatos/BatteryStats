package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.drain.DrainCalculator
import dev.ederfmatos.batterystats.domain.drain.DrainSource
import dev.ederfmatos.batterystats.domain.drain.ScreenRegime
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import dev.ederfmatos.batterystats.domain.model.PlugType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainCalculatorTest {

    private val calculator = DrainCalculator()
    private val microAmpCalibration = CurrentCalibration(divisor = 1000, inverted = false)

    @Test
    fun `descarga normal usa o contador de carga`() {
        val samples = dischargeSeries(count = 10, drainMilliAmps = 300.0)

        val analysis = calculator.analyze(samples, microAmpCalibration)

        assertEquals(9, analysis.windows.size)
        assertTrue(analysis.windows.all { it.source == DrainSource.CHARGE_COUNTER })
        analysis.windows.forEach { assertEquals(300.0, it.milliAmps, 1.0) }
    }

    @Test
    fun `janelas com o aparelho carregando sao descartadas`() {
        val samples = listOf(
            sample(BASE_MS, chargeCounterUah = 3_000_000L, currentNowRaw = -300_000L),
            sample(
                BASE_MS + MINUTE_MS,
                chargeCounterUah = 3_010_000L,
                currentNowRaw = 300_000L,
                status = BatteryStatus.CHARGING,
                plugType = PlugType.AC,
            ),
            sample(BASE_MS + 2 * MINUTE_MS, chargeCounterUah = 3_020_000L, currentNowRaw = 300_000L,
                status = BatteryStatus.CHARGING, plugType = PlugType.AC),
        )

        val analysis = calculator.analyze(samples, microAmpCalibration)

        assertTrue(analysis.windows.isEmpty())
        assertEquals(2, analysis.chargingWindows)
    }

    @Test
    fun `gap de amostragem vira buraco e nao janela`() {
        val samples = listOf(
            sample(BASE_MS, chargeCounterUah = 3_000_000L, currentNowRaw = -300_000L),
            // O aparelho dormiu por duas horas.
            sample(BASE_MS + 120 * MINUTE_MS, chargeCounterUah = 2_400_000L, currentNowRaw = -300_000L),
            sample(BASE_MS + 121 * MINUTE_MS, chargeCounterUah = 2_395_000L, currentNowRaw = -300_000L),
        )

        val analysis = calculator.analyze(samples, microAmpCalibration)

        assertEquals(1, analysis.gaps.size)
        assertEquals(1, analysis.windows.size)
    }

    @Test
    fun `contador travado cai para CURRENT_NOW`() {
        val samples = (0 until 5).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                chargeCounterUah = 3_000_000L,
                currentNowRaw = -250_000L,
            )
        }

        val analysis = calculator.analyze(samples, microAmpCalibration)

        assertEquals(4, analysis.windows.size)
        assertTrue(analysis.windows.all { it.source == DrainSource.CURRENT_NOW })
        analysis.windows.forEach { assertEquals(250.0, it.milliAmps, 0.001) }
    }

    @Test
    fun `CURRENT_NOW invertido produz dreno positivo com a calibracao correta`() {
        val invertedCalibration = CurrentCalibration(divisor = 1000, inverted = true)
        val samples = (0 until 4).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                chargeCounterUah = 3_000_000L,
                // Aparelho que reporta positivo durante a descarga.
                currentNowRaw = 250_000L,
            )
        }

        val analysis = calculator.analyze(samples, invertedCalibration)

        assertTrue(analysis.windows.isNotEmpty())
        analysis.windows.forEach { assertEquals(250.0, it.milliAmps, 0.001) }
    }

    @Test
    fun `mudanca de estado da tela no meio da janela marca regime misto`() {
        val samples = listOf(
            sample(BASE_MS, chargeCounterUah = 3_000_000L, screenOn = false),
            sample(BASE_MS + MINUTE_MS, chargeCounterUah = 2_995_000L, screenOn = true),
            sample(BASE_MS + 2 * MINUTE_MS, chargeCounterUah = 2_990_000L, screenOn = true),
        )

        val analysis = calculator.analyze(samples, microAmpCalibration)

        assertEquals(ScreenRegime.MIXED, analysis.windows[0].screen)
        assertEquals(ScreenRegime.ON, analysis.windows[1].screen)
    }

    @Test
    fun `serie com menos de duas amostras nao produz nada`() {
        val analysis = calculator.analyze(listOf(sample(BASE_MS)), microAmpCalibration)

        assertTrue(analysis.windows.isEmpty())
        assertTrue(analysis.gaps.isEmpty())
    }
}
