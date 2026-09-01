package dev.ederfmatos.batterystats.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigParserTest {

    private val validJson = """
        {
          "configVersion": 7,
          "minStepsToClose": 4,
          "maxWindowMs": 300000,
          "idleBaselineMaxMA": 120,
          "highIdleWarnMA": 150,
          "samplingIntervalMs": 60000,
          "deviceOverrides": {
            "samsung/SM-S911B": { "currentNowUnit": "mA", "currentNowSignInverted": false }
          }
        }
    """.trimIndent()

    @Test
    fun `le uma config valida`() {
        val config = RemoteConfigParser.parse(validJson)

        assertNotNull(config)
        assertEquals(7, config?.configVersion)
        assertEquals(4, config?.minStepsToClose)
        assertEquals(300_000L, config?.maxWindowMs)
    }

    @Test
    fun `override por modelo e case insensitive`() {
        val config = RemoteConfigParser.parse(validJson)
        requireNotNull(config)

        val override = config.overrideFor("Samsung", "sm-s911b")

        assertNotNull(override)
        assertEquals(1, override?.currentNowDivisor)
    }

    @Test
    fun `unidade em microamperes vira divisor mil`() {
        val json = validJson.replace("\"mA\"", "\"uA\"")

        val override = RemoteConfigParser.parse(json)?.overrideFor("samsung", "SM-S911B")

        assertEquals(1000, override?.currentNowDivisor)
    }

    @Test
    fun `unidade desconhecida descarta so aquele override`() {
        val json = validJson.replace("\"mA\"", "\"gigaampere\"")

        val config = RemoteConfigParser.parse(json)

        assertNotNull(config)
        assertTrue(config?.deviceOverrides?.isEmpty() ?: false)
    }

    @Test
    fun `janela de zero milissegundo e recusada`() {
        // O tipo está certo mas o valor destruiria a medição; validar só o tipo não basta.
        val json = validJson.replace("\"maxWindowMs\": 300000", "\"maxWindowMs\": 0")

        assertNull(RemoteConfigParser.parse(json))
    }

    @Test
    fun `intervalo de amostragem absurdo e recusado`() {
        val json = validJson.replace("\"samplingIntervalMs\": 60000", "\"samplingIntervalMs\": 10")

        assertNull(RemoteConfigParser.parse(json))
    }

    @Test
    fun `json quebrado nao derruba nada`() {
        assertNull(RemoteConfigParser.parse("{ isto nao fecha"))
    }

    @Test
    fun `a config compilada e um fallback valido`() {
        val compiled = RemoteConfig.COMPILED_DEFAULT

        assertEquals(4, compiled.minStepsToClose)
        assertEquals(300_000L, compiled.maxWindowMs)
        assertTrue(compiled.deviceOverrides.isEmpty())
    }
}
