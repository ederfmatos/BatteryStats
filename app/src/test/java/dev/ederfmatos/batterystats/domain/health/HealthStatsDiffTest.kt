package dev.ederfmatos.batterystats.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthStatsDiffTest {

    private val baseMs = 1_700_000_000_000L
    private val minute = 60_000L

    private fun snapshot(
        atMs: Long,
        uid: Int = 10123,
        wakelocks: Map<String, TimerValue> = emptyMap(),
        gps: TimerValue = TimerValue.ZERO,
        topMs: Long = 0L,
        foregroundServiceMs: Long = 0L,
        backgroundMs: Long = 0L,
        cpuMs: Long = 0L,
    ) = UidHealthSnapshot(
        uid = uid,
        timestampMs = atMs,
        partialWakelocks = wakelocks,
        gps = gps,
        topMs = topMs,
        foregroundServiceMs = foregroundServiceMs,
        backgroundMs = backgroundMs,
        userCpuTimeMs = cpuMs,
    )

    @Test
    fun `diferenca de contadores monotonicos`() {
        val before = snapshot(baseMs, topMs = 10 * minute, cpuMs = 5_000L)
        val after = snapshot(baseMs + 30 * minute, topMs = 25 * minute, cpuMs = 9_000L)

        val delta = HealthStatsDiff.diff(before, after)

        assertEquals(15 * minute, delta?.topMs)
        assertEquals(4_000L, delta?.cpuTimeMs)
        assertEquals(30 * minute, delta?.spanMs)
        assertFalse(delta?.countersReset ?: true)
    }

    @Test
    fun `wakelock por tag e diferenciado individualmente`() {
        val before = snapshot(
            baseMs,
            wakelocks = mapOf(
                "*job*/com.exemplo/.Worker" to TimerValue(3, 2 * minute),
                "AlarmManager" to TimerValue(10, 30_000L),
            ),
        )
        val after = snapshot(
            baseMs + 60 * minute,
            wakelocks = mapOf(
                "*job*/com.exemplo/.Worker" to TimerValue(9, 47 * minute),
                "AlarmManager" to TimerValue(10, 30_000L),
            ),
        )

        val delta = HealthStatsDiff.diff(before, after)

        // A tag que não se moveu some do delta; só o que aconteceu no intervalo fica.
        assertEquals(1, delta?.partialWakelocks?.size)
        val worker = delta?.partialWakelocks?.get("*job*/com.exemplo/.Worker")
        assertEquals(6, worker?.count)
        assertEquals(45 * minute, worker?.timeMs)
        assertEquals(45 * minute, delta?.totalPartialWakelockMs)
    }

    @Test
    fun `o maior wakelock e o que responde quem acordou o aparelho`() {
        val after = snapshot(
            baseMs + 60 * minute,
            wakelocks = mapOf(
                "curto" to TimerValue(2, minute),
                "vilao" to TimerValue(1, 47 * minute),
            ),
        )

        val delta = HealthStatsDiff.diff(snapshot(baseMs), after)

        assertEquals("vilao", delta?.topWakelock?.first)
    }

    @Test
    fun `contador andando para tras e tratado como reinicio, nao como delta negativo`() {
        // Reinício do aparelho ou reset das estatísticas de bateria.
        val before = snapshot(baseMs, topMs = 90 * minute, cpuMs = 60_000L)
        val after = snapshot(baseMs + 10 * minute, topMs = 2 * minute, cpuMs = 1_000L)

        val delta = HealthStatsDiff.diff(before, after)

        assertTrue(delta?.countersReset ?: false)
        assertEquals(2 * minute, delta?.topMs)
        assertTrue((delta?.topMs ?: -1L) >= 0L)
    }

    @Test
    fun `retratos de UIDs diferentes nao sao comparaveis`() {
        val before = snapshot(baseMs, uid = 10123)
        val after = snapshot(baseMs + minute, uid = 10456)

        assertNull(HealthStatsDiff.diff(before, after))
    }

    @Test
    fun `retratos fora de ordem sao recusados`() {
        val before = snapshot(baseMs + minute)
        val after = snapshot(baseMs)

        assertNull(HealthStatsDiff.diff(before, after))
    }

    @Test
    fun `separa tempo visivel de tempo ativo sem estar visivel`() {
        // O caso que interessa numa janela de tela apagada.
        val after = snapshot(
            baseMs + 3 * 60 * minute,
            topMs = 0L,
            foregroundServiceMs = 167 * minute,
            backgroundMs = 5 * minute,
        )

        val delta = HealthStatsDiff.diff(snapshot(baseMs), after)

        assertEquals(0L, delta?.userVisibleMs)
        assertEquals(172 * minute, delta?.invisibleActiveMs)
        assertFalse(delta?.isIdle ?: true)
    }

    @Test
    fun `app que nao fez nada e marcado como ocioso`() {
        val delta = HealthStatsDiff.diff(snapshot(baseMs), snapshot(baseMs + 60 * minute))

        assertTrue(delta?.isIdle ?: false)
    }

    @Test
    fun `GPS e diferenciado com contagem e tempo`() {
        val before = snapshot(baseMs, gps = TimerValue(1, minute))
        val after = snapshot(baseMs + 60 * minute, gps = TimerValue(4, 21 * minute))

        val delta = HealthStatsDiff.diff(before, after)

        assertEquals(3, delta?.gps?.count)
        assertEquals(20 * minute, delta?.gps?.timeMs)
    }
}
