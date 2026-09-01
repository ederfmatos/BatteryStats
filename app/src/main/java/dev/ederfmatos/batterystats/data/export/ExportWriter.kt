package dev.ederfmatos.batterystats.data.export

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.ederfmatos.batterystats.domain.export.ExportFormatter
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Grava o export no Uri devolvido por ACTION_CREATE_DOCUMENT. O usuário escolhe onde salvar;
 * o app não pede permissão de armazenamento nenhuma para isso.
 */
class ExportWriter(private val context: Context) {

    suspend fun write(uri: Uri, samples: List<BatterySnapshot>, asJson: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val content = if (asJson) {
                ExportFormatter.samplesToJson(samples)
            } else {
                ExportFormatter.samplesToCsv(samples)
            }
            try {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    Log.e(TAG, "ContentResolver devolveu stream nulo para $uri")
                    return@withContext false
                }
                stream.use { it.write(content.toByteArray()) }
                true
            } catch (e: IOException) {
                Log.e(TAG, "Falha ao escrever o export em $uri", e)
                false
            } catch (e: SecurityException) {
                Log.e(TAG, "Sem permissão para escrever em $uri", e)
                false
            }
        }

    private companion object {
        const val TAG = "ExportWriter"
    }
}
