package dev.ederfmatos.batterystats.domain.update

/** O `latest.json` publicado junto do APK em cada Release. */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val publishedAtMs: Long,
    val changelog: String,
    val mandatory: Boolean,
)

/** Por que uma verificação pré-instalação falhou. Cada uma aborta a instalação. */
enum class VerificationFailure {
    /** SHA-256 do arquivo baixado difere do publicado. Download corrompido ou adulterado. */
    HASH_MISMATCH,

    /** O APK está assinado com outro certificado. Nunca conseguiria substituir o instalado. */
    SIGNATURE_MISMATCH,

    /** O APK é de outro pacote. */
    PACKAGE_MISMATCH,

    /** versionCode menor ou igual ao instalado. */
    NOT_NEWER,

    /** O APK exige um Android mais novo que o do aparelho. */
    INCOMPATIBLE_SDK,

    /** O arquivo não pôde sequer ser lido como APK. */
    UNREADABLE,
}

sealed interface VerificationResult {
    data object Ok : VerificationResult
    data class Failed(val failure: VerificationFailure, val detail: String? = null) :
        VerificationResult
}

/**
 * Compara a versão publicada com a instalada.
 *
 * Kotlin puro: a decisão "tem atualização?" não precisa de Context nenhum e é a parte que merece
 * teste.
 */
object UpdateDecision {

    fun hasUpdate(manifest: UpdateManifest, installedVersionCode: Long): Boolean =
        manifest.versionCode > installedVersionCode

    fun isCompatible(manifest: UpdateManifest, deviceSdkInt: Int): Boolean =
        manifest.minSdk <= deviceSdkInt
}
