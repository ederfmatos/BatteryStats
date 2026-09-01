package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.model.ForegroundReason
import dev.ederfmatos.batterystats.domain.model.ForegroundResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ForegroundResolutionTest {

    @Test
    fun `resolucao com pacote nao carrega motivo`() {
        val resolution = ForegroundResolution.of("app.exemplo")

        assertEquals("app.exemplo", resolution.packageName)
        assertNull(resolution.reason)
    }

    @Test
    fun `ausencia sempre tem motivo`() {
        val resolution = ForegroundResolution.absent(ForegroundReason.SCREEN_OFF)

        assertNull(resolution.packageName)
        assertEquals(ForegroundReason.SCREEN_OFF, resolution.reason)
    }

    @Test
    fun `pacote e motivo ao mesmo tempo e estado invalido`() {
        assertThrows(IllegalArgumentException::class.java) {
            ForegroundResolution("app.exemplo", ForegroundReason.GAP)
        }
    }
}
