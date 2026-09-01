package dev.ederfmatos.batterystats.domain.update

/**
 * Traduz os códigos do `PackageInstaller` para uma falha com ação associada.
 *
 * Os valores numéricos são reproduzidos aqui — e não importados de `android.content.pm` — para que
 * o mapeamento continue sendo Kotlin puro e testável em JVM. Eles fazem parte do contrato público
 * da plataforma desde a API 21 e não mudam; o teste
 * `InstallStatusMapperTest` trava cada um.
 */
object InstallStatusMapper {

    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = 1
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_ABORTED = 3
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7
    const val STATUS_FAILURE_TIMEOUT = 8
    const val STATUS_PENDING_USER_ACTION = -1

    fun map(status: Int, message: String? = null): InstallOutcome = when (status) {
        STATUS_SUCCESS -> InstallOutcome.Success
        STATUS_PENDING_USER_ACTION -> InstallOutcome.NeedsUserAction
        STATUS_FAILURE_CONFLICT -> InstallOutcome.Failed(InstallFailure.CONFLICT, message)
        STATUS_FAILURE_INCOMPATIBLE -> InstallOutcome.Failed(InstallFailure.INCOMPATIBLE, message)
        STATUS_FAILURE_STORAGE -> InstallOutcome.Failed(InstallFailure.STORAGE, message)
        STATUS_FAILURE_ABORTED -> InstallOutcome.Failed(InstallFailure.ABORTED, message)
        STATUS_FAILURE_BLOCKED -> InstallOutcome.Failed(InstallFailure.BLOCKED, message)
        else -> InstallOutcome.Failed(InstallFailure.UNKNOWN, message ?: "status $status")
    }

    /** O degrau para onde cair depois de uma falha. Null quando não há mais para onde ir. */
    fun nextStepAfter(step: InstallStep, failure: InstallFailure): InstallStep? = when (failure) {
        // Assinatura divergente não se resolve trocando de degrau: nenhum instalador aceita.
        InstallFailure.CONFLICT -> null
        InstallFailure.INCOMPATIBLE -> null
        // Cancelamento explícito não deve escalar sozinho para outro caminho.
        InstallFailure.ABORTED -> null
        InstallFailure.STORAGE -> null
        InstallFailure.CORRUPTED -> InstallStep.DIRECT_LINK
        InstallFailure.BLOCKED -> InstallStep.OPEN_APK
        InstallFailure.UNKNOWN -> when (step) {
            InstallStep.SILENT -> InstallStep.SYSTEM_DIALOG
            InstallStep.SYSTEM_DIALOG -> InstallStep.OPEN_APK
            InstallStep.UNKNOWN_SOURCES_PERMISSION -> InstallStep.OPEN_APK
            InstallStep.OPEN_APK -> InstallStep.DIRECT_LINK
            InstallStep.DIRECT_LINK -> null
        }
    }
}
