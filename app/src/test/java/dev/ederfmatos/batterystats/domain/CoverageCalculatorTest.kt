package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.drain.Coverage
import dev.ederfmatos.batterystats.domain.drain.CoverageCalculator
import dev.ederfmatos.batterystats.domain.drain.GapReason
import dev.ederfmatos.batterystats.domain.drain.MeasurementGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageCalculatorTest {

    private val calculator = CoverageCalculator()
    private val hourMs = 3_600_000L

    @Test
    fun `sem buracos a cobertura e total`() {
        val coverage = calculator.coverage(BASE_MS, BASE_MS + 24 * hourMs, emptyList())

        assertEquals(1.0, coverage.fraction, 0.0001)
        assertFalse(coverage.isPoor)
    }

    @Test
    fun `reproduz a cobertura de 41 por cento da coleta real`() {
        // 59% do tempo dentro de buracos.
        val periodMs = 100 * hourMs
        val gaps = listOf(
            MeasurementGap(BASE_MS, BASE_MS + 59 * hourMs, GapReason.SERVICE_KILLED),
        )

        val coverage = calculator.coverage(BASE_MS, BASE_MS + periodMs, gaps)

        assertEquals(41.0, coverage.percent, 0.5)
        assertTrue(coverage.isPoor)
    }

    @Test
    fun `buraco que comeca antes do periodo conta so a parte de dentro`() {
        val gaps = listOf(
            MeasurementGap(BASE_MS - 10 * hourMs, BASE_MS + 2 * hourMs, GapReason.REBOOT),
        )

        val coverage = calculator.coverage(BASE_MS, BASE_MS + 10 * hourMs, gaps)

        assertEquals(2 * hourMs, coverage.gapMs)
        assertEquals(0.8, coverage.fraction, 0.0001)
    }

    @Test
    fun `buraco inteiramente fora do periodo e ignorado`() {
        val gaps = listOf(
            MeasurementGap(BASE_MS - 20 * hourMs, BASE_MS - 10 * hourMs, GapReason.DOZE),
        )

        val coverage = calculator.coverage(BASE_MS, BASE_MS + 10 * hourMs, gaps)

        assertEquals(0L, coverage.gapMs)
        assertEquals(0, coverage.gapCount)
    }

    @Test
    fun `limiar de cobertura pobre e setenta por cento`() {
        val quaseBom = calculator.coverage(
            BASE_MS,
            BASE_MS + 100 * hourMs,
            listOf(MeasurementGap(BASE_MS, BASE_MS + 29 * hourMs, GapReason.UNKNOWN)),
        )
        val ruim = calculator.coverage(
            BASE_MS,
            BASE_MS + 100 * hourMs,
            listOf(MeasurementGap(BASE_MS, BASE_MS + 31 * hourMs, GapReason.UNKNOWN)),
        )

        assertFalse(quaseBom.isPoor)
        assertTrue(ruim.isPoor)
        assertEquals(0.70, Coverage.POOR_THRESHOLD, 0.0001)
    }
}
