package dev.ederfmatos.batterystats.domain.update

import org.json.JSONException
import org.json.JSONObject

/**
 * Lê o `latest.json`. Um manifesto malformado nunca pode virar instalação, então qualquer campo
 * obrigatório ausente derruba o parse inteiro em vez de assumir um default.
 *
 * `org.json` está no runtime do Android e tem um stub no classpath de teste do AGP, então isto
 * roda em JVM pura sem trazer biblioteca nenhuma.
 */
object UpdateManifestParser {

    fun parse(json: String): UpdateManifest? = try {
        val root = JSONObject(json)
        val sha256 = root.getString("sha256").lowercase()
        require(sha256.matches(SHA256_PATTERN)) { "sha256 fora do formato hexadecimal de 64 chars" }
        val apkUrl = root.getString("apkUrl")
        require(apkUrl.startsWith("https://")) { "apkUrl precisa ser https" }

        UpdateManifest(
            versionCode = root.getLong("versionCode"),
            versionName = root.getString("versionName"),
            minSdk = root.getInt("minSdk"),
            apkUrl = apkUrl,
            sha256 = sha256,
            sizeBytes = root.getLong("sizeBytes"),
            publishedAtMs = root.optLong("publishedAtMs", 0L),
            changelog = root.optString("changelog", ""),
            mandatory = root.optBoolean("mandatory", false),
        )
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
}
