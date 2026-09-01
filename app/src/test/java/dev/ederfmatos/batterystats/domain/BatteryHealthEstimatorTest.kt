package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.health.BatteryHealthEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryHealthEstimatorTest {

    private val estimator = BatteryHealthEstimator()
    private val dayMs = 86_400_000L

    @Test
    fun `compara a carga cheia recente com a melhor ja observada`() {
        val old = sample(BASE_MS, levelPct = 100, chargeCounterUah = 4_000_000L)
        val recentMs = BASE_MS + 30 * dayMs
        val recent = sample(recentMs, levelPct = 100, chargeCounterUah = 3_600_000L)

        val estimate = estimator.estimate(listOf(old, recent), nowMs = recentMs)

        assertEquals(0.9, estimate.relativeRatio ?: 0.0, 0.001)
        assertTrue(estimate.hasEnoughHistory)
    }

    @Test
    fun `historico curto demais nao vira estimativa`() {
        val samples = listOf(
            sample(BASE_MS, levelPct = 100, chargeCounterUah = 4_000_000L),
            sample(BASE_MS + dayMs, levelPct = 100, chargeCounterUah = 3_990_000L),
        )

        val estimate = estimator.estimate(samples, nowMs = BASE_MS + dayMs)

        assertFalse(estimate.hasEnoughHistory)
    }

    @Test
    fun `leituras longe de 100 por cento sao ignoradas`() {
        val samples = listOf(
            sample(BASE_MS, levelPct = 40, chargeCounterUah = 1_600_000L),
            sample(BASE_MS + dayMs, levelPct = 55, chargeCounterUah = 2_200_000L),
        )

        val estimate = estimator.estimate(samples, nowMs = BASE_MS + dayMs)

        assertNull(estimate.relativeRatio)
        assertEquals(0, estimate.observationDays)
    }
}
