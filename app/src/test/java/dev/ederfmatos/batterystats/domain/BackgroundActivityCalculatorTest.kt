package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.attribution.BackgroundActivityCalculator
import dev.ederfmatos.batterystats.domain.attribution.ForegroundInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundActivityCalculatorTest {

    private val hour = 3_600_000L

    @Test
    fun `conta o tempo de servico dentro das janelas de tela apagada`() {
        val screenOff = listOf(BASE_MS..(BASE_MS + 3 * hour))
        val services = listOf(
            ForegroundInterval("com.exemplo.sync", BASE_MS, BASE_MS + 2 * hour + 47 * MINUTE_MS),
        )

        val result = BackgroundActivityCalculator.calculate(screenOff, services)

        assertEquals(1, result.size)
        assertEquals(2 * hour + 47 * MINUTE_MS, result.first().activeMs)
        assertEquals(0.92, result.first().fractionOfScreenOff, 0.02)
    }

    @Test
    fun `servico fora das janelas medidas nao conta`() {
        // Rodou durante um buraco de amostragem: não há consumo medido com que correlacionar.
        val screenOff = listOf(BASE_MS..(BASE_MS + hour))
        val services = listOf(
            ForegroundInterval("com.exemplo.sync", BASE_MS + 5 * hour, BASE_MS + 6 * hour),
        )

        assertTrue(BackgroundActivityCalculator.calculate(screenOff, services).isEmpty())
    }

    @Test
    fun `soma o servico ao longo de varias janelas separadas`() {
        val screenOff = listOf(
            BASE_MS..(BASE_MS + hour),
            (BASE_MS + 2 * hour)..(BASE_MS + 3 * hour),
        )
        val services = listOf(
            ForegroundInterval("com.exemplo.sync", BASE_MS, BASE_MS + 3 * hour),
        )

        val result = BackgroundActivityCalculator.calculate(screenOff, services)

        // Só as duas horas medidas contam; a hora entre elas não foi medida.
        assertEquals(2 * hour, result.first().activeMs)
        assertEquals(1.0, result.first().fractionOfScreenOff, 0.001)
    }

    @Test
    fun `ordena do mais ativo para o menos`() {
        val screenOff = listOf(BASE_MS..(BASE_MS + 4 * hour))
        val services = listOf(
            ForegroundInterval("pouco", BASE_MS, BASE_MS + 10 * MINUTE_MS),
            ForegroundInterval("muito", BASE_MS, BASE_MS + 3 * hour),
        )

        val result = BackgroundActivityCalculator.calculate(screenOff, services)

        assertEquals("muito", result.first().packageName)
    }

    @Test
    fun `sem janela de tela apagada nao ha o que apurar`() {
        val services = listOf(ForegroundInterval("qualquer", BASE_MS, BASE_MS + hour))

        assertTrue(BackgroundActivityCalculator.calculate(emptyList(), services).isEmpty())
    }
}
