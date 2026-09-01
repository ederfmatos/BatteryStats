package dev.ederfmatos.batterystats.data.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dev.ederfmatos.batterystats.domain.export.ExportFormatter
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.report.ReportFormatter
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * Envia o relatório.
 *
 * O caminho principal é o **share sheet**, que funciona com qualquer app instalado e não depende
 * de esquema de URL de terceiro. O deeplink é secundário justamente porque nada garante que ele
 * seja capturado — ver [claudeChatIntent].
 */
class ReportSharer(private val context: Context) {

    /** Compartilha o relatório em texto, opcionalmente com o JSON cru anexado. */
    fun shareIntent(markdown: String, rawJsonFile: File? = null): Intent {
        val text = "${ReportFormatter.PREAMBLE}\n\n$markdown"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (rawJsonFile != null) "*/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        rawJsonFile?.let { file ->
            uriFor(file)?.let { uri ->
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(intent, null)
    }

    fun copyToClipboard(markdown: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("Relatório BatteryStats", markdown)
        )
    }

    /**
     * Deeplink para uma conversa nova com o relatório pré-preenchido.
     *
     * Usa `https://claude.ai/new?q=...`, que é a URL web de chat. No Android ela **pode** ser
     * capturada pelo app do Claude ou abrir no navegador; não há garantia documentada de captura,
     * então isto é o botão secundário e nunca o principal. O esquema `claude://` não é usado: a
     * documentação só o descreve para rotas do Claude Code no mobile e para desktop.
     *
     * Devolve null quando o texto codificado passa do limite prático de URL — nesse caso a UI
     * oferece a versão curta e avisa que foi truncada.
     */
    fun claudeChatIntent(markdown: String): Intent? {
        val text = "${ReportFormatter.PREAMBLE}\n\n$markdown"
        val encoded = try {
            URLEncoder.encode(text, Charsets.UTF_8.name())
        } catch (e: java.io.UnsupportedEncodingException) {
            Log.e(TAG, "UTF-8 indisponível", e)
            return null
        }
        if (encoded.length > MAX_ENCODED_LENGTH) return null

        return Intent(Intent.ACTION_VIEW, Uri.parse("$CLAUDE_CHAT_BASE$encoded"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Grava o JSON cru num arquivo do cache para poder ser anexado ao share. */
    fun writeRawJson(samples: List<BatterySnapshot>): File? = try {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, "batterystats-amostras.json")
        file.writeText(ExportFormatter.samplesToJson(samples))
        file
    } catch (e: IOException) {
        Log.e(TAG, "Falha ao gravar o JSON cru para compartilhar", e)
        null
    }

    private fun uriFor(file: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Arquivo fora dos caminhos do FileProvider", e)
        null
    }

    companion object {
        private const val TAG = "ReportSharer"
        const val SHARE_DIR = "share"
        const val CLAUDE_CHAT_BASE = "https://claude.ai/new?q="

        /**
         * A documentação cita truncamento por volta de 14.000 caracteres no desktop, e o Android
         * tem limites próprios de tamanho de Intent. 8.000 é folgado o bastante para não esbarrar
         * em nenhum dos dois.
         */
        const val MAX_ENCODED_LENGTH = 8_000
    }
}
