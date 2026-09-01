package dev.ederfmatos.batterystats.data

import dev.ederfmatos.batterystats.data.sampling.InteractiveTimeCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveTimeCounterTest {

    private var now = 1_700_000_000_000L
    private val counter = InteractiveTimeCounter { now }

    private fun advance(ms: Long) {
        now += ms
    }

    @Test
    fun `soma apenas o tempo com a tela ligada`() {
        counter.onScreenOn()
        advance(10 * 60_000L)
        counter.onScreenOff()
        advance(30 * 60_000L)

        assertEquals(10 * 60_000L, counter.totalTodayMs())
    }

    @Test
    fun `inclui o trecho em curso quando a tela ainda esta ligada`() {
        counter.onScreenOn()
        advance(5 * 60_000L)

        assertEquals(5 * 60_000L, counter.totalTodayMs())
    }

    @Test
    fun `ligar duas vezes seguidas nao reinicia a contagem em curso`() {
        counter.onScreenOn()
        advance(3 * 60_000L)
        counter.onScreenOn()
        advance(2 * 60_000L)

        assertEquals(5 * 60_000L, counter.totalTodayMs())
    }

    @Test
    fun `contagem zera na virada do dia`() {
        counter.onScreenOn()
        advance(60_000L)
        counter.onScreenOff()
        assertTrue(counter.totalTodayMs() > 0)

        advance(26 * 3_600_000L)

        assertEquals(0L, counter.totalTodayMs())
    }
}
