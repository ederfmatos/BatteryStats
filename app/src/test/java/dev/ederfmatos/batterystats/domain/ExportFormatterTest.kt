package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.export.ExportFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatterTest {

    @Test
    fun `csv tem cabecalho e uma linha por amostra`() {
        val samples = dischargeSeries(count = 3)

        val csv = ExportFormatter.samplesToCsv(samples).trim().lines()

        assertEquals(4, csv.size)
        assertTrue(csv.first().startsWith("timestampMs,levelPct"))
    }

    @Test
    fun `campos ausentes viram vazio no csv e null no json`() {
        val samples = listOf(sample(BASE_MS, chargeCounterUah = null, currentNowRaw = null))

        val csv = ExportFormatter.samplesToCsv(samples)
        val json = ExportFormatter.samplesToJson(samples)

        assertTrue(csv.contains(",,"))
        assertTrue(json.contains("\"chargeCounterUah\":null"))
    }

    @Test
    fun `nome de pacote com virgula e escapado no csv`() {
        val samples = listOf(sample(BASE_MS).copy(foregroundPackage = "app,estranho"))

        val csv = ExportFormatter.samplesToCsv(samples)

        assertTrue(csv.contains("\"app,estranho\""))
    }
}
