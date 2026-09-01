package dev.ederfmatos.batterystats.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.ederfmatos.batterystats.domain.update.UpdateDecision
import dev.ederfmatos.batterystats.domain.update.UpdateManifest
import dev.ederfmatos.batterystats.domain.update.UpdateManifestParser
import java.io.File

/** O que a checagem descobriu. */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val manifest: UpdateManifest) : UpdateCheck
    data class Incompatible(val manifest: UpdateManifest) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Descobre se há versão nova. Não baixa nada e não instala nada — só compara números.
 */
class UpdateChecker(
    private val context: Context,
    private val downloader: HttpDownloader = HttpDownloader(),
) {

    fun installedVersionCode(): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "O próprio pacote não foi encontrado", e)
        0L
    }

    fun installedVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "O próprio pacote não foi encontrado", e)
        ""
    }

    suspend fun check(): UpdateCheck {
        val body = downloader.getText(UpdateEndpoints.LATEST_MANIFEST_URL)
            ?: return UpdateCheck.Failed(REASON_UNREACHABLE)
        val manifest = UpdateManifestParser.parse(body)
            ?: return UpdateCheck.Failed(REASON_MALFORMED)

        return when {
            !UpdateDecision.hasUpdate(manifest, installedVersionCode()) -> UpdateCheck.UpToDate
            !UpdateDecision.isCompatible(manifest, Build.VERSION.SDK_INT) ->
                UpdateCheck.Incompatible(manifest)

            else -> UpdateCheck.Available(manifest)
        }
    }

    /**
     * Onde o APK baixado fica. Em `cacheDir` de propósito: o sistema pode limpar se faltar espaço,
     * e não é dado do usuário. O arquivo só é apagado depois de instalação confirmada — se algo
     * travar no meio, o degrau "Instalar manualmente" precisa achar o arquivo aqui.
     */
    fun apkFileFor(manifest: UpdateManifest): File =
        File(updateDir(), "batterystats-${manifest.versionCode}.apk")

    fun updateDir(): File = File(context.cacheDir, UPDATE_DIR_NAME).apply { mkdirs() }

    /** Remove APKs de versões que não são mais a candidata atual. */
    fun pruneOldApks(keepVersionCode: Long) {
        updateDir().listFiles()?.forEach { file ->
            if (file.name.endsWith(".apk") && !file.name.contains("-$keepVersionCode.")) {
                if (!file.delete()) Log.w(TAG, "Não consegui apagar ${file.name}")
            }
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"
        const val UPDATE_DIR_NAME = "updates"
        const val REASON_UNREACHABLE = "unreachable"
        const val REASON_MALFORMED = "malformed"

        /** Não checar mais de uma vez a cada 6h na abertura do app. */
        const val CHECK_INTERVAL_MS = 6 * 3_600_000L
    }
}
