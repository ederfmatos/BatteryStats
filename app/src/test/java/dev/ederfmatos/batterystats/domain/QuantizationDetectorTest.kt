package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.drain.QuantizationDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantizationDetectorTest {

    private val detector = QuantizationDetector()

    @Test
    fun `encontra o degrau real do aparelho`() {
        val samples = quantizedDischargeSeries(count = 60, stepUah = REAL_STEP_UAH)

        assertEquals(REAL_STEP_UAH, detector.detectStepUah(samples))
    }

    @Test
    fun `contador continuo devolve degrau unitario`() {
        val samples = (0 until 20).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                // Deltas primos entre si: 4999, 5001, 4999... MDC 1.
                chargeCounterUah = 3_000_000L - index * 5000L - (index % 2),
            )
        }

        assertEquals(1L, detector.detectStepUah(samples))
    }

    @Test
    fun `contador travado nao produz degrau`() {
        val samples = (0 until 20).map { index ->
            sample(atMs = BASE_MS + index * MINUTE_MS, chargeCounterUah = 3_000_000L)
        }

        assertNull(detector.detectStepUah(samples))
    }

    @Test
    fun `poucos deltas ja produzem estimativa conservadora`() {
        val samples = (0 until 4).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                chargeCounterUah = 3_000_000L - index * REAL_STEP_UAH,
            )
        }

        assertEquals(REAL_STEP_UAH, detector.detectStepUah(samples))
    }

    @Test
    fun `saltos duplos superestimam o degrau em vez de subestimar`() {
        // Ver só saltos de 2 degraus faz o MDC devolver o dobro. Errar para cima é o lado seguro:
        // janelas fecham mais tarde e a incerteza reportada fica maior.
        val samples = (0 until 6).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                chargeCounterUah = 3_000_000L - index * (2 * REAL_STEP_UAH),
            )
        }

        assertEquals(2 * REAL_STEP_UAH, detector.detectStepUah(samples))
    }

    @Test
    fun `so olha as amostras mais recentes`() {
        val detectorComJanelaCurta = QuantizationDetector(maxSamples = 10)
        val antigas = (0 until 30).map { index ->
            sample(atMs = BASE_MS + index * MINUTE_MS, chargeCounterUah = 5_000_000L - index * 7L)
        }
        val recentes = (0 until 10).map { index ->
            sample(
                atMs = BASE_MS + (30 + index) * MINUTE_MS,
                chargeCounterUah = 4_999_790L - index * REAL_STEP_UAH,
            )
        }

        assertEquals(REAL_STEP_UAH, detectorComJanelaCurta.detectStepUah(antigas + recentes))
    }
}
