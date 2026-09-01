package dev.ederfmatos.batterystats.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.ederfmatos.batterystats.domain.update.UpdateManifest
import dev.ederfmatos.batterystats.domain.update.VerificationFailure
import dev.ederfmatos.batterystats.domain.update.VerificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * As quatro verificações obrigatórias antes de instalar, na ordem, falhando fechado.
 *
 * Sem elas isto não é auto-atualização, é um canal de execução remota de código no aparelho: um
 * APK qualquer baixado de uma URL seria instalado com as permissões que este app já tem. Nenhuma
 * das quatro está atrás de flag e nenhuma pode ser pulada.
 */
class ApkVerifier(private val context: Context) {

    suspend fun verify(apk: File, manifest: UpdateManifest): VerificationResult =
        withContext(Dispatchers.IO) {
            // 1. Integridade do download.
            val actualSha = sha256(apk)
                ?: return@withContext VerificationResult.Failed(VerificationFailure.UNREADABLE)
            if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                return@withContext VerificationResult.Failed(
                    VerificationFailure.HASH_MISMATCH,
                    "esperado ${manifest.sha256}, obtido $actualSha",
                )
            }

            val archiveInfo = packageArchiveInfo(apk)
                ?: return@withContext VerificationResult.Failed(VerificationFailure.UNREADABLE)
            val installedInfo = installedPackageInfo()
                ?: return@withContext VerificationResult.Failed(VerificationFailure.UNREADABLE)

            // 2. Mesma chave de assinatura. Chave diferente nunca substituiria o app instalado —
            // e um APK de outra origem é exatamente o que esta checagem existe para barrar.
            if (!signaturesMatch(archiveInfo, installedInfo)) {
                return@withContext VerificationResult.Failed(
                    VerificationFailure.SIGNATURE_MISMATCH,
                )
            }

            // 3. Mesmo pacote e versão realmente mais nova.
            if (archiveInfo.packageName != context.packageName) {
                return@withContext VerificationResult.Failed(
                    VerificationFailure.PACKAGE_MISMATCH,
                    archiveInfo.packageName,
                )
            }
            if (versionCodeOf(archiveInfo) <= versionCodeOf(installedInfo)) {
                return@withContext VerificationResult.Failed(VerificationFailure.NOT_NEWER)
            }

            // 4. Compatível com este Android.
            val requiredSdk = minSdkOf(archiveInfo) ?: manifest.minSdk
            if (requiredSdk > Build.VERSION.SDK_INT) {
                return@withContext VerificationResult.Failed(
                    VerificationFailure.INCOMPATIBLE_SDK,
                    "APK exige API $requiredSdk; aparelho tem ${Build.VERSION.SDK_INT}",
                )
            }

            VerificationResult.Ok
        }

    fun sha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: IOException) {
        Log.e(TAG, "Não foi possível ler ${file.name} para o hash", e)
        null
    }

    private fun packageArchiveInfo(apk: File): PackageInfo? = try {
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, signingFlags())
    } catch (e: RuntimeException) {
        // getPackageArchiveInfo devolve null para arquivo inválido, mas pode estourar em APK
        // truncado; um download pela metade não pode derrubar o app.
        Log.e(TAG, "APK baixado ilegível", e)
        null
    }

    private fun installedPackageInfo(): PackageInfo? = try {
        context.packageManager.getPackageInfo(context.packageName, signingFlags())
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "O próprio pacote não foi encontrado", e)
        null
    }

    /** `GET_SIGNING_CERTIFICATES` só existe na API 28; antes disso é `GET_SIGNATURES`. */
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun signaturesMatch(candidate: PackageInfo, installed: PackageInfo): Boolean {
        val candidateCerts = certificateHashes(candidate)
        val installedCerts = certificateHashes(installed)
        if (candidateCerts.isEmpty() || installedCerts.isEmpty()) return false
        return candidateCerts == installedCerts
    }

    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()

        val digest = MessageDigest.getInstance("SHA-256")
        return signatures
            .filterNotNull()
            .map { signature ->
                digest.reset()
                digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }
            .toSet()
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun minSdkOf(info: PackageInfo): Int? = info.applicationInfo?.minSdkVersion

    private companion object {
        const val TAG = "ApkVerifier"
    }
}
