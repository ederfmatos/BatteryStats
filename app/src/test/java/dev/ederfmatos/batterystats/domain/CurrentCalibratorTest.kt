package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.drain.CurrentCalibrator
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentCalibratorTest {

    private val calibrator = CurrentCalibrator()

    @Test
    fun `aparelho em microamperes com sinal documentado`() {
        val samples = dischargeSeries(
            count = 60,
            drainMilliAmps = 300.0,
            currentMultiplier = 1000L,
            currentSign = -1L,
        )

        val calibration = calibrator.calibrate(samples)

        assertEquals(1000, calibration?.divisor)
        assertFalse(calibration?.inverted ?: true)
        assertEquals(CurrentCalibration.Source.AUTO, calibration?.source)
    }

    @Test
    fun `aparelho que reporta em miliamperes`() {
        val samples = dischargeSeries(
            count = 60,
            drainMilliAmps = 300.0,
            currentMultiplier = 1L,
            currentSign = -1L,
        )

        val calibration = calibrator.calibrate(samples)

        assertEquals(1, calibration?.divisor)
    }

    @Test
    fun `aparelho com o sinal invertido`() {
        val samples = dischargeSeries(
            count = 60,
            drainMilliAmps = 300.0,
            currentMultiplier = 1000L,
            currentSign = 1L,
        )

        val calibration = calibrator.calibrate(samples)

        assertEquals(1000, calibration?.divisor)
        assertTrue(calibration?.inverted ?: false)
    }

    @Test
    fun `sem contador de carga nao ha como calibrar`() {
        val samples = (0 until 12).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                chargeCounterUah = null,
                currentNowRaw = -300_000L,
            )
        }

        assertNull(calibrator.calibrate(samples))
    }

    @Test
    fun `poucas janelas nao geram calibracao`() {
        val samples = dischargeSeries(count = 3, drainMilliAmps = 300.0)

        assertNull(calibrator.calibrate(samples))
    }

    @Test
    fun `reproduz o aparelho real que reporta em miliamperes`() {
        // Razão mediana raw/derivado medida em campo: 1,07 — ou seja, mA, não µA.
        val samples = quantizedDischargeSeries(
            count = 90,
            drainMilliAmps = 800.0,
            currentNowRaw = -856L,
            screenOn = true,
        )

        val calibration = calibrator.calibrate(samples)

        assertEquals(1, calibration?.divisor)
        assertFalse(calibration?.inverted ?: true)
    }

    @Test
    fun `janelas de baixa confianca nao entram na calibracao`() {
        // 20 mA: toda janela fecha por tempo com um degrau só. Nada confiável para comparar.
        val samples = quantizedDischargeSeries(count = 60, drainMilliAmps = 20.0)

        assertNull(calibrator.calibrate(samples))
    }
}
