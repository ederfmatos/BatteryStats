package dev.ederfmatos.batterystats.data.update

import android.content.Context
import android.util.Log
import dev.ederfmatos.batterystats.data.db.BatteryDao
import dev.ederfmatos.batterystats.data.db.toAttempt
import dev.ederfmatos.batterystats.data.db.toEntity
import dev.ederfmatos.batterystats.domain.update.InstallFailure
import dev.ederfmatos.batterystats.domain.update.InstallOutcome
import dev.ederfmatos.batterystats.domain.update.InstallStep
import dev.ederfmatos.batterystats.domain.update.UpdateAttempt
import dev.ederfmatos.batterystats.domain.update.UpdateManifest
import dev.ederfmatos.batterystats.domain.update.VerificationFailure
import dev.ederfmatos.batterystats.domain.update.VerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Estado da tela de Atualização. Explícito de ponta a ponta, sem spinner infinito. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    data class Verifying(val manifest: UpdateManifest) : UpdateState
    data class Installing(val manifest: UpdateManifest, val step: InstallStep) : UpdateState
    data class NeedsUnknownSources(val manifest: UpdateManifest) : UpdateState
    data object Installed : UpdateState
    data class Failed(
        val failure: InstallFailure,
        val detail: String?,
        val nextStep: InstallStep?,
        val manifest: UpdateManifest?,
    ) : UpdateState
}

/**
 * Orquestra checar, baixar, verificar e instalar.
 *
 * O APK baixado só é apagado depois de instalação confirmada. Se travar no meio, o degrau
 * "Instalar manualmente" precisa achar o arquivo lá.
 */
class UpdateRepository(
    private val context: Context,
    private val dao: BatteryDao,
    private val checker: UpdateChecker,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val downloader: HttpDownloader = HttpDownloader(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val attempts: Flow<List<UpdateAttempt>> =
        dao.updateAttempts().map { list -> list.map { it.toAttempt() } }

    suspend fun check(): UpdateCheck = checker.check()

    fun apkFor(manifest: UpdateManifest): File = checker.apkFileFor(manifest)

    /**
     * Baixa se ainda não houver um arquivo íntegro em cache. Um APK já baixado e com o hash certo
     * é reaproveitado: depois de conceder a permissão de fontes desconhecidas, o usuário não deve
     * pagar o download de novo.
     */
    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val target = apkFor(manifest)
        if (target.exists() && verifier.sha256(target).equals(manifest.sha256, ignoreCase = true)) {
            onProgress(1f)
            return true
        }
        checker.pruneOldApks(manifest.versionCode)
        val ok = downloader.download(manifest.apkUrl, target, manifest.sizeBytes, onProgress)
        if (!ok) target.delete()
        return ok
    }

    suspend fun verify(manifest: UpdateManifest): VerificationResult =
        verifier.verify(apkFor(manifest), manifest)

    /** Traduz uma falha de verificação para a mesma linguagem de falha da instalação. */
    fun failureOf(result: VerificationResult.Failed): InstallFailure = when (result.failure) {
        VerificationFailure.HASH_MISMATCH -> InstallFailure.CORRUPTED
        VerificationFailure.SIGNATURE_MISMATCH -> InstallFailure.CONFLICT
        VerificationFailure.PACKAGE_MISMATCH -> InstallFailure.CONFLICT
        VerificationFailure.NOT_NEWER -> InstallFailure.UNKNOWN
        VerificationFailure.INCOMPATIBLE_SDK -> InstallFailure.INCOMPATIBLE
        VerificationFailure.UNREADABLE -> InstallFailure.CORRUPTED
    }

    suspend fun install(manifest: UpdateManifest, step: InstallStep): InstallOutcome {
        val apk = apkFor(manifest)
        return when (step) {
            InstallStep.SILENT -> installer.startSession(apk, silent = true)
            InstallStep.SYSTEM_DIALOG -> installer.startSession(apk, silent = false)
            else -> InstallOutcome.NeedsUserAction
        }
    }

    fun canRequestPackageInstalls(): Boolean = installer.canRequestPackageInstalls()

    fun unknownSourcesIntent() = installer.unknownSourcesSettingsIntent()

    fun openApkIntent(manifest: UpdateManifest) = installer.openApkIntent(apkFor(manifest))

    fun directDownloadIntent() = installer.directDownloadIntent()

    /** Só depois da instalação confirmada. Antes disso o arquivo ainda pode ser necessário. */
    fun discardDownload(manifest: UpdateManifest) {
        val apk = apkFor(manifest)
        if (apk.exists() && !apk.delete()) Log.w(TAG, "Não consegui apagar ${apk.name}")
    }

    suspend fun record(
        versionCode: Long,
        step: InstallStep,
        succeeded: Boolean,
        failure: InstallFailure? = null,
        detail: String? = null,
    ) {
        val attempt = UpdateAttempt(
            versionCode = versionCode,
            step = step,
            succeeded = succeeded,
            failure = failure,
            detail = detail,
            timestampMs = clock(),
        )
        try {
            dao.insertUpdateAttempt(attempt.toEntity())
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Falha ao registrar a tentativa de atualização", e)
        }
    }

    private companion object {
        const val TAG = "UpdateRepository"
    }
}
