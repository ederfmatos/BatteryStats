package dev.ederfmatos.batterystats.data.update

import android.content.Context
import android.os.Build
import android.util.Log
import dev.ederfmatos.batterystats.domain.update.RemoteConfig
import dev.ederfmatos.batterystats.domain.update.RemoteConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Busca e guarda a config remota, com queda para os valores compilados.
 *
 * Nunca falha para fora: sem rede usa o cache; sem cache usa o compilado; com JSON quebrado mantém
 * o que já tinha. O app precisa continuar medindo mesmo que o GitHub esteja fora do ar.
 */
class RemoteConfigRepository(
    private val context: Context,
    private val downloader: HttpDownloader = HttpDownloader(),
) {

    private val cacheFile: File get() = File(context.filesDir, CACHE_NAME)

    fun cached(): RemoteConfig {
        val file = cacheFile
        if (!file.exists()) return RemoteConfig.COMPILED_DEFAULT
        return try {
            RemoteConfigParser.parse(file.readText()) ?: RemoteConfig.COMPILED_DEFAULT
        } catch (e: IOException) {
            Log.w(TAG, "Cache de config ilegível; usando os valores compilados", e)
            RemoteConfig.COMPILED_DEFAULT
        }
    }

    /** Baixa e valida. Devolve a config em vigor depois da tentativa, nunca null. */
    suspend fun refresh(): RemoteConfig = withContext(Dispatchers.IO) {
        val body = downloader.getText(UpdateEndpoints.REMOTE_CONFIG_URL)
        if (body == null) {
            Log.i(TAG, "Config remota inacessível; mantendo a atual")
            return@withContext cached()
        }

        val parsed = RemoteConfigParser.parse(body)
        if (parsed == null) {
            Log.w(TAG, "Config remota malformada; mantendo a atual")
            return@withContext cached()
        }

        try {
            cacheFile.writeText(body)
        } catch (e: IOException) {
            Log.w(TAG, "Não consegui gravar o cache da config", e)
        }
        parsed
    }

    /** O ajuste conhecido para este aparelho, se a config remota trouxer um. */
    fun overrideForThisDevice(config: RemoteConfig) =
        config.overrideFor(Build.MANUFACTURER, Build.MODEL)

    private companion object {
        const val TAG = "RemoteConfigRepository"
        const val CACHE_NAME = "remote-config.json"
    }
}
