package dev.ederfmatos.batterystats.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dev.ederfmatos.batterystats.domain.update.InstallOutcome
import dev.ederfmatos.batterystats.domain.update.InstallStatusMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A cascata de instalação, degrau por degrau.
 *
 * A regra que rege tudo aqui: nunca deixar o usuário num beco sem saída. Cada degrau só é
 * oferecido quando o anterior falha, e cada falha vira uma ação concreta na tela.
 */
class ApkInstaller(private val context: Context) {

    /** Degrau 3 — sem isto a sessão nem deve ser tentada. */
    fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Abre a tela onde o usuário concede "instalar apps desconhecidos" para este app. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Degraus 1 e 2. Abre uma sessão do `PackageInstaller` e escreve o APK nela.
     *
     * Com [silent] a sessão pede `USER_ACTION_NOT_REQUIRED` (API 31+), que só é aceito quando este
     * app já é o *installer of record* de si mesmo e a assinatura confere — ou seja, da segunda
     * auto-atualização em diante. Quando o sistema recusa, ele devolve `STATUS_PENDING_USER_ACTION`
     * em vez de erro, e aí o degrau 2 assume: dispara-se o Intent que vem em `EXTRA_INTENT`.
     *
     * O resultado real não volta por aqui — chega assíncrono em [InstallResultReceiver].
     */
    suspend fun startSession(apk: File, silent: Boolean): InstallOutcome =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            var sessionId = -1
            try {
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite(APK_ENTRY, 0, apk.length()).use { output ->
                        apk.inputStream().use { input -> input.copyTo(output) }
                        session.fsync(output)
                    }
                    session.commit(resultPendingIntent(sessionId).intentSender)
                }
                // O commit é assíncrono: o desfecho chega no receiver.
                InstallOutcome.NeedsUserAction
            } catch (e: IOException) {
                Log.e(TAG, "Falha de E/S na sessão de instalação", e)
                if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
                InstallStatusMapper.map(InstallStatusMapper.STATUS_FAILURE, e.message)
            } catch (e: SecurityException) {
                Log.e(TAG, "Sistema recusou a sessão de instalação", e)
                if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
                InstallStatusMapper.map(InstallStatusMapper.STATUS_FAILURE_BLOCKED, e.message)
            }
        }

    private fun resultPendingIntent(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    /**
     * Degrau 4 — abre o APK já em cache no instalador do sistema.
     *
     * Precisa ser `content://` via FileProvider: passar `file://` estoura `FileUriExposedException`
     * desde a API 24, e a permissão de leitura precisa ser concedida ao instalador no próprio
     * Intent.
     */
    fun openApkIntent(apk: File): Intent? = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "APK fora dos caminhos declarados no FileProvider", e)
        null
    }

    /** Degrau 5 — link direto, também botão permanente na tela Sobre. */
    fun directDownloadIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateEndpoints.LATEST_APK_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val TAG = "ApkInstaller"
        private const val APK_ENTRY = "batterystats.apk"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
