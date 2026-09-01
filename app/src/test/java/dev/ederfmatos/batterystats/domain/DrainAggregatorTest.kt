package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.drain.DrainAggregator
import dev.ederfmatos.batterystats.domain.drain.DrainCalculator
import dev.ederfmatos.batterystats.domain.drain.DrainSource
import dev.ederfmatos.batterystats.domain.drain.DrainWindow
import dev.ederfmatos.batterystats.domain.drain.RuntimeProjector
import dev.ederfmatos.batterystats.domain.drain.ScreenRegime
import dev.ederfmatos.batterystats.domain.drain.WakelockSuspicionDetector
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainAggregatorTest {

    private val aggregator = DrainAggregator()

    private fun window(
        startMs: Long,
        durationMs: Long,
        milliAmps: Double,
        screen: ScreenRegime,
        startLevelPct: Int = 80,
        endLevelPct: Int = 80,
    ) = DrainWindow(
        startMs = startMs,
        endMs = startMs + durationMs,
        milliAmps = milliAmps,
        source = DrainSource.CHARGE_COUNTER,
        screen = screen,
        startLevelPct = startLevelPct,
        endLevelPct = endLevelPct,
    )

    @Test
    fun `media por regime e ponderada pela duracao`() {
        val windows = listOf(
            window(BASE_MS, 60 * MINUTE_MS, 100.0, ScreenRegime.OFF),
            window(BASE_MS + 60 * MINUTE_MS, 1 * MINUTE_MS, 1000.0, ScreenRegime.OFF),
        )

        val stats = aggregator.aggregate(windows)

        // Sem ponderação a média seria 550; com ponderação fica perto de 114.
        assertEquals(114.75, stats.screenOff.averageMilliAmps, 1.0)
    }

    @Test
    fun `janelas mistas ficam fora das medias por regime`() {
        val windows = listOf(
            window(BASE_MS, 10 * MINUTE_MS, 100.0, ScreenRegime.OFF),
            window(BASE_MS + 10 * MINUTE_MS, 10 * MINUTE_MS, 900.0, ScreenRegime.MIXED),
        )

        val stats = aggregator.aggregate(windows)

        assertEquals(100.0, stats.screenOff.averageMilliAmps, 0.001)
        assertEquals(0, stats.screenOn.windowCount)
        // A mista continua contando no total geral.
        assertEquals(500.0, stats.overallMilliAmps, 0.001)
    }

    @Test
    fun `percentual por hora usa a queda de nivel do regime`() {
        val windows = listOf(
            window(BASE_MS, 60 * MINUTE_MS, 200.0, ScreenRegime.ON, startLevelPct = 80, endLevelPct = 70),
        )

        val stats = aggregator.aggregate(windows)

        assertEquals(10.0, stats.screenOn.percentPerHour, 0.001)
    }

    @Test
    fun `linha de base de repouso sai das janelas de tela desligada`() {
        val windows = (0 until 10).map { index ->
            window(
                startMs = BASE_MS + index * 10 * MINUTE_MS,
                durationMs = 10 * MINUTE_MS,
                milliAmps = 10.0 + index,
                screen = ScreenRegime.OFF,
            )
        }

        val stats = aggregator.aggregate(windows)

        assertNotNull(stats.idleBaselineMilliAmps)
        assertTrue((stats.idleBaselineMilliAmps ?: 0.0) <= 12.0)
    }

    @Test
    fun `projecao usa o padrao das ultimas horas e nao o ultimo minuto`() {
        val calculator = DrainCalculator()
        val samples = dischargeSeries(count = 30, drainMilliAmps = 200.0)
        val analysis = calculator.analyze(samples, CurrentCalibration(divisor = 1000))
        val stats = aggregator.aggregate(analysis.windows)

        val projection = RuntimeProjector().project(
            stats = stats,
            currentLevelPct = 50,
            chargeCounterUah = 2_000_000L,
        )

        // 2000 mAh restantes / 200 mA = 10 horas.
        assertEquals(10.0, projection.hoursRemaining ?: 0.0, 0.2)
    }

    @Test
    fun `dreno alto com tela desligada dispara suspeita de wakelock`() {
        val windows = (0 until 20).map { index ->
            window(
                startMs = BASE_MS + index * 5 * MINUTE_MS,
                durationMs = 5 * MINUTE_MS,
                milliAmps = 120.0,
                screen = ScreenRegime.OFF,
            )
        }

        val stats = aggregator.aggregate(windows)

        assertTrue(WakelockSuspicionDetector().isSuspicious(stats))
    }

    @Test
    fun `repouso normal nao dispara suspeita`() {
        val windows = (0 until 20).map { index ->
            window(
                startMs = BASE_MS + index * 5 * MINUTE_MS,
                durationMs = 5 * MINUTE_MS,
                milliAmps = 12.0,
                screen = ScreenRegime.OFF,
            )
        }

        val stats = aggregator.aggregate(windows)

        assertFalse(WakelockSuspicionDetector().isSuspicious(stats))
    }
}
