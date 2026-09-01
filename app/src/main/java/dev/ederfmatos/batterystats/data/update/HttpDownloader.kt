package dev.ederfmatos.batterystats.data.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/**
 * HTTP mínimo em cima de `HttpsURLConnection`. Sem OkHttp nem Ktor de propósito: o app faz três
 * requisições GET na vida inteira, e cada dependência de rede a mais é superfície nova num app
 * cuja premissa é não falar com ninguém além do GitHub.
 *
 * Só HTTPS: um redirect para http seria recusado aqui e também pelo network_security_config.
 */
class HttpDownloader {

    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        openConnection(url)?.use { connection ->
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "GET $url devolveu ${connection.responseCode}")
                return@withContext null
            }
            connection.inputStream.bufferedReader().readText()
        }
    }

    /**
     * Baixa para [target], relatando progresso em 0..1. Cancelamento da corrotina interrompe o
     * download e o arquivo parcial fica onde está — quem chamou decide apagar.
     */
    suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val connection = openConnection(url) ?: return@withContext false
        try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Download de $url devolveu ${connection.responseCode}")
                return@withContext false
            }
            val total = if (expectedBytes > 0) expectedBytes else connection.contentLengthLong
            var written = 0L

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Falha ao baixar $url", e)
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection? = try {
        val parsed = URL(url)
        require(parsed.protocol == "https") { "só HTTPS" }
        (parsed.openConnection() as HttpsURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
    } catch (e: IOException) {
        Log.e(TAG, "Não foi possível abrir $url", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "URL recusada: $url", e)
        null
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private companion object {
        const val TAG = "HttpDownloader"
        const val TIMEOUT_MS = 20_000
        const val BUFFER_BYTES = 64 * 1024
    }
}
