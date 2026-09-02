package dev.ederfmatos.batterystats.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeSessionAnalyzerTest {

    private val analyzer = ChargeSessionAnalyzer()
    private val baseMs = 1_700_000_000_000L
    private val minute = 60_000L
    private val stepUah = 4076L

    /**
     * Carga linear de [fromPct] a [toPct] numa bateria de [capacityMah], com o contador quantizado
     * como o aparelho real faz.
     */
    private fun chargingSeries(
        fromPct: Int,
        toPct: Int,
        capacityMah: Double,
        startMs: Long = baseMs,
        stepMinutes: Long = 2,
    ): List<UidChargeSample> = (fromPct..toPct).map { pct ->
        val trueUah = (capacityMah * 1000.0 * (pct / 100.0)).toLong()
        UidChargeSample(
            timestampMs = startMs + (pct - fromPct) * stepMinutes * minute,
            levelPct = pct,
            chargeCounterUah = (trueUah / stepUah) * stepUah,
            isCharging = true,
        )
    }

    @Test
    fun `sessao longa devolve a capacidade real do aparelho`() {
        // Bateria de 3800 mAh degradada, carregando de 25% a 95%.
        val samples = chargingSeries(25, 95, capacityMah = 3800.0)

        val sessions = analyzer.sessions(samples)

        assertEquals(1, sessions.size)
        assertEquals(3800.0, sessions.first().impliedFullCapacityMah, 30.0)
        assertEquals(70, sessions.first().levelGainPct)
    }

    @Test
    fun `a quantizacao praticamente nao atrapalha durante a carga`() {
        // O ponto inteiro de medir carregando: com 70 pontos de nível, dois degraus de 4076 µAh
        // dão poucos mAh de incerteza sobre uma bateria de milhares.
        val session = analyzer.sessions(chargingSeries(25, 95, 3800.0)).first()

        val uncertainty = session.uncertaintyMah(stepUah)

        assertTrue("incerteza de $uncertainty mAh", uncertainty < 20.0)
    }

    @Test
    fun `sessao curta e descartada, nao exibida com ressalva`() {
        // É de onde saem os prints de "saúde 256%": faixa estreita, erro relativo enorme.
        val samples = chargingSeries(70, 85, 3800.0)

        assertTrue(analyzer.sessions(samples).isEmpty())
    }

    @Test
    fun `descarga no meio fecha a sessao`() {
        val charging = chargingSeries(30, 90, 3800.0)
        val unplugged = listOf(
            UidChargeSample(
                timestampMs = charging.last().timestampMs + minute,
                levelPct = 90,
                chargeCounterUah = 3_420_000L,
                isCharging = false,
            )
        )
        val chargingAgain = chargingSeries(
            40, 95, 3800.0,
            startMs = charging.last().timestampMs + 10 * minute,
        )

        val sessions = analyzer.sessions(charging + unplugged + chargingAgain)

        assertEquals(2, sessions.size)
    }

    @Test
    fun `buraco de amostragem no meio invalida a sessao`() {
        // Não dá para saber quanta carga entrou enquanto ninguém media.
        val first = chargingSeries(20, 40, 3800.0)
        val second = chargingSeries(
            41, 95, 3800.0,
            startMs = first.last().timestampMs + 40 * minute,
        )

        val sessions = analyzer.sessions(first + second)

        // A primeira metade é curta demais; só a segunda sobrevive, e sozinha.
        assertEquals(1, sessions.size)
        assertEquals(41, sessions.first().startLevelPct)
    }

    @Test
    fun `sem contador de carga nao ha sessao`() {
        val samples = (30..95).map { pct ->
            UidChargeSample(baseMs + pct * minute, pct, null, isCharging = true)
        }

        assertTrue(analyzer.sessions(samples).isEmpty())
    }

    @Test
    fun `usa a mediana das sessoes, nao a media`() {
        // Uma sessão fora da curva (aparelho quente) não pode arrastar o resultado.
        val sessions = listOf(
            session(capacityMah = 3800.0),
            session(capacityMah = 3820.0),
            session(capacityMah = 5200.0),
        )

        val health = AbsoluteHealthCalculator.calculate(sessions, 4130.0, stepUah, cycleCount = 412)

        assertEquals(3820.0, health.measuredCapacityMah ?: 0.0, 1.0)
    }

    @Test
    fun `percentual usa a capacidade declarada pelo aparelho`() {
        val health = AbsoluteHealthCalculator.calculate(
            listOf(session(3800.0), session(3800.0), session(3800.0)),
            declaredCapacityMah = 4130.0,
            quantizationStepUah = stepUah,
            cycleCount = null,
        )

        assertEquals(92.0, health.healthPercent ?: 0.0, 0.5)
    }

    @Test
    fun `sem capacidade declarada nao inventa percentual`() {
        val health = AbsoluteHealthCalculator.calculate(
            listOf(session(3800.0)),
            declaredCapacityMah = null,
            quantizationStepUah = stepUah,
            cycleCount = null,
        )

        assertNull(health.healthPercent)
        assertEquals(3800.0, health.measuredCapacityMah ?: 0.0, 1.0)
    }

    @Test
    fun `poucas sessoes ficam marcadas como preliminares`() {
        val health = AbsoluteHealthCalculator.calculate(
            listOf(session(3800.0)), 4130.0, stepUah, null,
        )

        assertTrue(health.isPreliminary)
    }

    private fun session(capacityMah: Double) = ChargeSession(
        startMs = baseMs,
        endMs = baseMs + 120 * minute,
        startLevelPct = 25,
        endLevelPct = 95,
        chargedUah = (capacityMah * 1000.0 * 0.70).toLong(),
        sampleCount = 71,
    )
}
