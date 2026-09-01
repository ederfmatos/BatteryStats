package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.attribution.AppAttributionCalculator
import dev.ederfmatos.batterystats.domain.attribution.ForegroundInterval
import dev.ederfmatos.batterystats.domain.attribution.SYSTEM_BUCKET_PACKAGE
import dev.ederfmatos.batterystats.domain.drain.DrainSource
import dev.ederfmatos.batterystats.domain.drain.DrainWindow
import dev.ederfmatos.batterystats.domain.drain.ScreenRegime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAttributionCalculatorTest {

    private val calculator = AppAttributionCalculator()

    private fun window(
        startMs: Long,
        durationMs: Long,
        milliAmps: Double,
        screen: ScreenRegime,
    ) = DrainWindow(
        startMs = startMs,
        endMs = startMs + durationMs,
        milliAmps = milliAmps,
        source = DrainSource.CHARGE_COUNTER,
        screen = screen,
        startLevelPct = 80,
        endLevelPct = 80,
    )

    @Test
    fun `divide o consumo proporcionalmente ao tempo de cada app`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 600.0, ScreenRegime.ON))
        val intervals = listOf(
            ForegroundInterval("app.a", BASE_MS, BASE_MS + 40 * MINUTE_MS),
            ForegroundInterval("app.b", BASE_MS + 40 * MINUTE_MS, BASE_MS + 60 * MINUTE_MS),
        )

        val result = calculator.attribute(windows, intervals, idleBaselineMilliAmps = null)

        val appA = result.first { it.packageName == "app.a" }
        val appB = result.first { it.packageName == "app.b" }
        // 600 mAh na hora: 2/3 para o app A, 1/3 para o B.
        assertEquals(400.0, appA.estimatedMilliAmpHours, 0.5)
        assertEquals(200.0, appB.estimatedMilliAmpHours, 0.5)
    }

    @Test
    fun `janela com a tela desligada nunca vai para um app`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 100.0, ScreenRegime.OFF))
        val intervals = listOf(
            ForegroundInterval("app.a", BASE_MS, BASE_MS + 60 * MINUTE_MS),
        )

        val result = calculator.attribute(windows, intervals, idleBaselineMilliAmps = null)

        assertNull(result.firstOrNull { it.packageName == "app.a" })
        val system = result.first { it.isSystemBucket }
        assertEquals(100.0, system.estimatedMilliAmpHours, 0.5)
    }

    @Test
    fun `linha de base de repouso e descontada antes de atribuir`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 300.0, ScreenRegime.ON))
        val intervals = listOf(
            ForegroundInterval("app.a", BASE_MS, BASE_MS + 60 * MINUTE_MS),
        )

        val result = calculator.attribute(windows, intervals, idleBaselineMilliAmps = 50.0)

        val appA = result.first { it.packageName == "app.a" }
        val system = result.first { it.isSystemBucket }
        assertEquals(250.0, appA.estimatedMilliAmpHours, 0.5)
        assertEquals(50.0, system.estimatedMilliAmpHours, 0.5)
    }

    @Test
    fun `tempo de tela sem app registrado vai para o sistema`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 600.0, ScreenRegime.ON))
        val intervals = listOf(
            ForegroundInterval("app.a", BASE_MS, BASE_MS + 30 * MINUTE_MS),
        )

        val result = calculator.attribute(windows, intervals, idleBaselineMilliAmps = null)

        val appA = result.first { it.packageName == "app.a" }
        val system = result.first { it.isSystemBucket }
        assertEquals(300.0, appA.estimatedMilliAmpHours, 0.5)
        assertEquals(300.0, system.estimatedMilliAmpHours, 0.5)
    }

    @Test
    fun `sem timeline de primeiro plano tudo cai no bucket de sistema`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 200.0, ScreenRegime.ON))

        val result = calculator.attribute(windows, emptyList(), idleBaselineMilliAmps = null)

        assertEquals(1, result.size)
        assertEquals(SYSTEM_BUCKET_PACKAGE, result.first().packageName)
        assertEquals(200.0, result.first().estimatedMilliAmpHours, 0.5)
    }

    @Test
    fun `ranking sai ordenado do maior para o menor`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 600.0, ScreenRegime.ON))
        val intervals = listOf(
            ForegroundInterval("app.pequeno", BASE_MS, BASE_MS + 10 * MINUTE_MS),
            ForegroundInterval("app.grande", BASE_MS + 10 * MINUTE_MS, BASE_MS + 60 * MINUTE_MS),
        )

        val result = calculator.attribute(windows, intervals, idleBaselineMilliAmps = null)

        assertTrue(
            result.map { it.estimatedMilliAmpHours } ==
                result.map { it.estimatedMilliAmpHours }.sortedDescending()
        )
    }

    @Test
    fun `conta quantas janelas sustentam cada linha do ranking`() {
        // Uma linha apurada em 1 janela não pode parecer idêntica a uma apurada em 4.
        val windows = (0 until 4).map { index ->
            window(BASE_MS + index * 60 * MINUTE_MS, 60 * MINUTE_MS, 300.0, ScreenRegime.ON)
        }
        val intervals = listOf(
            ForegroundInterval("app.constante", BASE_MS, BASE_MS + 4 * 60 * MINUTE_MS),
            ForegroundInterval("app.pontual", BASE_MS, BASE_MS + 30 * MINUTE_MS),
        )

        val result = calculator.attribute(windows, intervals, idleBaselineMilliAmps = null)

        assertEquals(4, result.first { it.packageName == "app.constante" }.windowCount)
        assertEquals(1, result.first { it.packageName == "app.pontual" }.windowCount)
    }

    @Test
    fun `evidencia magra e sinalizada`() {
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 300.0, ScreenRegime.ON))
        val intervals = listOf(
            ForegroundInterval("app.unico", BASE_MS, BASE_MS + 60 * MINUTE_MS),
        )

        val usage = calculator.attribute(windows, intervals, null)
            .first { it.packageName == "app.unico" }

        assertTrue(usage.hasThinEvidence)
    }

    @Test
    fun `bucket de sistema nunca e marcado como evidencia magra`() {
        // Ele agrega tudo que não deu para atribuir; "pouca evidência" não se aplica a ele.
        val windows = listOf(window(BASE_MS, 60 * MINUTE_MS, 100.0, ScreenRegime.OFF))

        val system = calculator.attribute(windows, emptyList(), null).first { it.isSystemBucket }

        assertFalse(system.hasThinEvidence)
    }
}
