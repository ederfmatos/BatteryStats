package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.drain.AdaptiveWindowBuilder
import dev.ederfmatos.batterystats.domain.drain.DrainSource
import dev.ederfmatos.batterystats.domain.drain.GapReason
import dev.ederfmatos.batterystats.domain.drain.MeasurementGap
import dev.ederfmatos.batterystats.domain.drain.ScreenRegime
import dev.ederfmatos.batterystats.domain.drain.WindowCloseReason
import dev.ederfmatos.batterystats.domain.drain.WindowConfig
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import dev.ederfmatos.batterystats.domain.model.PlugType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWindowBuilderTest {

    private val builder = AdaptiveWindowBuilder()

    @Test
    fun `em repouso a janela vai ate o limite de tempo e o valor real cai dentro da faixa`() {
        // 60 mA: um degrau de 4076 µAh leva ~4 min, então o limite de 5 min chega antes dos 4
        // degraus. A janela fecha grosseira — e é justamente por isso que ela vira faixa na UI.
        val samples = quantizedDischargeSeries(count = 60, drainMilliAmps = 60.0)

        val analysis = builder.analyze(samples)

        assertTrue(analysis.windows.isNotEmpty())
        analysis.windows.forEach { window ->
            assertEquals(DrainSource.CHARGE_COUNTER, window.source)
            assertTrue("janela curta demais: ${window.spanMs}", window.spanMs >= 5 * MINUTE_MS)
            assertTrue(
                "60 mA deveria estar em ${window.rangeLowMilliAmps}..${window.rangeHighMilliAmps}",
                60.0 in window.rangeLowMilliAmps..window.rangeHighMilliAmps,
            )
        }
    }

    @Test
    fun `nenhuma janela sai como multiplo do degrau por minuto`() {
        // O bug original: em janelas de 60s todo valor virava múltiplo de ~244 mA.
        val samples = quantizedDischargeSeries(count = 60, drainMilliAmps = 60.0)

        val analysis = builder.analyze(samples)

        val stepPerMinuteMa = (REAL_STEP_UAH / 1000.0) / (MINUTE_MS / 3_600_000.0)
        analysis.windows.forEach { window ->
            val ratio = window.milliAmps / stepPerMinuteMa
            assertNotEquals(
                "valor colado no degrau por minuto: ${window.milliAmps}",
                0.0,
                ratio - ratio.toInt(),
                0.001,
            )
        }
    }

    @Test
    fun `janela que fecha por tempo com um degrau e de baixa confianca`() {
        // 20 mA: um degrau leva ~12 min, então a janela fecha pelo limite de tempo com 1 degrau.
        val samples = quantizedDischargeSeries(
            count = 60,
            intervalMs = MINUTE_MS,
            drainMilliAmps = 20.0,
        )

        val analysis = builder.analyze(samples)

        assertTrue(analysis.windows.isNotEmpty())
        analysis.windows.forEach { window ->
            assertEquals(WindowCloseReason.TIME_LIMIT, window.closeReason)
            assertTrue(window.lowConfidence)
            assertEquals(1, window.stepsAccumulated)
        }
    }

    @Test
    fun `janela de baixa confianca expoe faixa larga`() {
        val samples = quantizedDischargeSeries(count = 40, drainMilliAmps = 20.0)

        val window = builder.analyze(samples).windows.first()

        assertTrue(window.uncertaintyMilliAmps > 0.0)
        assertTrue(window.rangeHighMilliAmps > window.milliAmps)
        assertTrue(window.rangeLowMilliAmps < window.milliAmps)
        assertTrue("faixa deve começar em zero ou perto", window.rangeLowMilliAmps >= 0.0)
    }

    @Test
    fun `dreno alto fecha janelas por degraus e com alta confianca`() {
        // 800 mA (tela ligada em uso pesado): 4 degraus em ~1 min.
        val samples = quantizedDischargeSeries(
            count = 30,
            drainMilliAmps = 800.0,
            screenOn = true,
        )

        val analysis = builder.analyze(samples)

        assertTrue(analysis.windows.isNotEmpty())
        analysis.windows.forEach { window ->
            assertEquals(WindowCloseReason.STEPS_REACHED, window.closeReason)
            assertFalse(window.lowConfidence)
            assertEquals(ScreenRegime.ON, window.screen)
            // Mesmo com 4 degraus a quantização ainda pesa; o que importa é o real cair na faixa.
            assertTrue(
                "800 mA fora de ${window.rangeLowMilliAmps}..${window.rangeHighMilliAmps}",
                800.0 in window.rangeLowMilliAmps..window.rangeHighMilliAmps,
            )
        }
    }

    @Test
    fun `gap no meio descarta a janela aberta em vez de fecha-la`() {
        val before = quantizedDischargeSeries(count = 6, drainMilliAmps = 60.0)
        val gapStart = before.last().timestampMs
        val gapEnd = gapStart + 19 * MINUTE_MS
        val after = quantizedDischargeSeries(count = 30, drainMilliAmps = 60.0)
            .map { it.copy(timestampMs = it.timestampMs + (gapEnd - BASE_MS)) }
        val gap = MeasurementGap(gapStart, gapEnd, GapReason.SERVICE_KILLED)

        val analysis = builder.analyze(before + after, listOf(gap))

        assertTrue(analysis.discardedWindows >= 1)
        analysis.windows.forEach { window ->
            assertFalse(
                "janela atravessou o buraco",
                window.startMs < gapEnd && window.endMs > gapStart,
            )
        }
    }

    @Test
    fun `contador travado cai para CURRENT_NOW sempre em baixa confianca`() {
        val samples = (0 until 10).map { index ->
            sample(
                atMs = BASE_MS + index * MINUTE_MS,
                chargeCounterUah = 3_000_000L,
                currentNowRaw = -250_000L,
            )
        }

        val analysis = builder.analyze(samples, emptyList(), CurrentCalibration(divisor = 1000))

        assertTrue(analysis.windows.isNotEmpty())
        analysis.windows.forEach { window ->
            assertEquals(DrainSource.CURRENT_NOW, window.source)
            assertTrue(window.lowConfidence)
            assertEquals(250.0, window.milliAmps, 0.001)
        }
    }

    @Test
    fun `janelas com o aparelho carregando nao viram medicao`() {
        val samples = quantizedDischargeSeries(count = 30, drainMilliAmps = 60.0).map {
            it.copy(status = BatteryStatus.CHARGING, plugType = PlugType.AC)
        }

        val analysis = builder.analyze(samples)

        assertTrue(analysis.windows.isEmpty())
        assertTrue(analysis.chargingWindows > 0)
    }

    @Test
    fun `tela mudando no meio marca o regime como misto`() {
        val first = quantizedDischargeSeries(count = 10, drainMilliAmps = 800.0, screenOn = false)
        val second = quantizedDischargeSeries(count = 20, drainMilliAmps = 800.0, screenOn = true)
            .map {
                it.copy(
                    timestampMs = it.timestampMs + 10 * MINUTE_MS,
                    chargeCounterUah = it.chargeCounterUah?.minus(500_000L),
                )
            }

        val analysis = builder.analyze(first + second)

        assertTrue(analysis.windows.any { it.screen == ScreenRegime.MIXED })
    }

    @Test
    fun `configuracao mais exigente produz janelas mais longas`() {
        // Precisa de dreno alto: em repouso o limite de tempo fecha a janela antes dos degraus,
        // e aí mexer em minStepsToClose não muda nada.
        val samples = quantizedDischargeSeries(count = 120, drainMilliAmps = 800.0)

        val padrao = builder.analyze(samples).windows
        val exigente = AdaptiveWindowBuilder(WindowConfig(minStepsToClose = 12))
            .analyze(samples).windows

        assertTrue(exigente.isNotEmpty())
        assertTrue(
            "janelas exigentes deveriam ser em menor número",
            exigente.size < padrao.size,
        )
    }

    @Test
    fun `serie curta demais nao produz janela`() {
        val analysis = builder.analyze(listOf(sample(BASE_MS, chargeCounterUah = 3_000_000L)))

        assertTrue(analysis.windows.isEmpty())
    }
}
