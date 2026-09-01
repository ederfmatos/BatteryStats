package dev.ederfmatos.batterystats.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestParserTest {

    private val validJson = """
        {
          "versionCode": 42,
          "versionName": "1.4.0",
          "minSdk": 26,
          "apkUrl": "https://github.com/u/r/releases/download/v1.4.0/app-release.apk",
          "sha256": "${"a".repeat(64)}",
          "sizeBytes": 8123456,
          "publishedAtMs": 1788271356442,
          "changelog": "- Janela adaptativa",
          "mandatory": false
        }
    """.trimIndent()

    @Test
    fun `le um manifesto valido`() {
        val manifest = UpdateManifestParser.parse(validJson)

        assertNotNull(manifest)
        assertEquals(42L, manifest?.versionCode)
        assertEquals("1.4.0", manifest?.versionName)
        assertEquals(8123456L, manifest?.sizeBytes)
        assertFalse(manifest?.mandatory ?: true)
    }

    @Test
    fun `recusa apkUrl que nao seja https`() {
        val json = validJson.replace("https://github.com", "http://github.com")

        assertNull(UpdateManifestParser.parse(json))
    }

    @Test
    fun `recusa sha256 fora do formato`() {
        val json = validJson.replace("a".repeat(64), "abc123")

        assertNull(UpdateManifestParser.parse(json))
    }

    @Test
    fun `recusa manifesto sem campo obrigatorio`() {
        val json = validJson.replace("\"versionCode\": 42,", "")

        assertNull(UpdateManifestParser.parse(json))
    }

    @Test
    fun `recusa json invalido`() {
        assertNull(UpdateManifestParser.parse("isto nao e json"))
    }

    @Test
    fun `normaliza o sha para minusculas`() {
        val json = validJson.replace("a".repeat(64), "A".repeat(64))

        assertEquals("a".repeat(64), UpdateManifestParser.parse(json)?.sha256)
    }

    @Test
    fun `so ha atualizacao quando o versionCode e maior`() {
        val manifest = UpdateManifestParser.parse(validJson)
        requireNotNull(manifest)

        assertTrue(UpdateDecision.hasUpdate(manifest, installedVersionCode = 41))
        assertFalse(UpdateDecision.hasUpdate(manifest, installedVersionCode = 42))
        assertFalse(UpdateDecision.hasUpdate(manifest, installedVersionCode = 43))
    }

    @Test
    fun `manifesto que exige Android mais novo e incompativel`() {
        val manifest = UpdateManifestParser.parse(validJson.replace("\"minSdk\": 26", "\"minSdk\": 34"))
        requireNotNull(manifest)

        assertFalse(UpdateDecision.isCompatible(manifest, deviceSdkInt = 33))
        assertTrue(UpdateDecision.isCompatible(manifest, deviceSdkInt = 34))
    }
}
