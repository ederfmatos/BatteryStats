package dev.ederfmatos.batterystats.domain.update

/** Os degraus da cascata de instalação, do mais automático ao mais manual. */
enum class InstallStep {
    /** Sessão silenciosa. Só funciona da segunda auto-atualização em diante. */
    SILENT,

    /** Diálogo do sistema, disparado pelo Intent que a sessão devolve. Um toque. */
    SYSTEM_DIALOG,

    /** Falta a permissão de fontes desconhecidas; o usuário precisa concedê-la primeiro. */
    UNKNOWN_SOURCES_PERMISSION,

    /** Abre o APK já baixado no instalador do sistema, via FileProvider. */
    OPEN_APK,

    /** Link direto de download. Último recurso, e botão permanente na tela Sobre. */
    DIRECT_LINK,
}

/**
 * O que aconteceu numa tentativa. Cada falha tem uma ação concreta associada na UI — a regra é
 * nunca deixar o usuário num beco sem saída com um toast genérico.
 */
enum class InstallFailure {
    /** Assinado com outra chave; não pode substituir o instalado. */
    CONFLICT,

    /** Versão incompatível com este Android. */
    INCOMPATIBLE,

    /** Espaço insuficiente. */
    STORAGE,

    /** O usuário cancelou. */
    ABORTED,

    /** Bloqueado pelo sistema ou por app de segurança. */
    BLOCKED,

    /** Hash divergente: download corrompido. */
    CORRUPTED,

    /** Qualquer outra recusa do PackageInstaller. */
    UNKNOWN,
}

sealed interface InstallOutcome {
    data object Success : InstallOutcome

    /** A sessão precisa de um toque do usuário. Não é erro: é o degrau 2. */
    data object NeedsUserAction : InstallOutcome

    data class Failed(val failure: InstallFailure, val detail: String? = null) : InstallOutcome
}

/** Uma tentativa registrada, para a tela de histórico de atualizações. */
data class UpdateAttempt(
    val versionCode: Long,
    val step: InstallStep,
    val succeeded: Boolean,
    val failure: InstallFailure?,
    val detail: String?,
    val timestampMs: Long,
)
