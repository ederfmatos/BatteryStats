package dev.ederfmatos.batterystats.ui.update

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.update.InstallResultReceiver
import dev.ederfmatos.batterystats.data.update.UpdateCheck
import dev.ederfmatos.batterystats.data.update.UpdateState
import dev.ederfmatos.batterystats.domain.update.InstallFailure
import dev.ederfmatos.batterystats.domain.update.InstallOutcome
import dev.ederfmatos.batterystats.domain.update.InstallStatusMapper
import dev.ederfmatos.batterystats.domain.update.InstallStep
import dev.ederfmatos.batterystats.domain.update.UpdateAttempt
import dev.ederfmatos.batterystats.domain.update.UpdateManifest
import dev.ederfmatos.batterystats.domain.update.VerificationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateUiState(
    val state: UpdateState = UpdateState.Idle,
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0L,
    val attempts: List<UpdateAttempt> = emptyList(),
    val canRequestPackageInstalls: Boolean = true,
)

/**
 * Conduz a máquina de estados da atualização.
 *
 * A cascata de degraus vive aqui e não no repositório porque cada transição precisa aparecer na
 * tela: a promessa é que nenhuma falha vire um toast genérico.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repository = container.updateRepository

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            installedVersionName = container.updateChecker.installedVersionName(),
            installedVersionCode = container.updateChecker.installedVersionCode(),
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var workJob: Job? = null

    init {
        viewModelScope.launch {
            repository.attempts.collect { list ->
                _uiState.value = _uiState.value.copy(attempts = list)
            }
        }
        viewModelScope.launch {
            InstallResultReceiver.results.collect(::onInstallOutcome)
        }
    }

    fun check() {
        workJob?.cancel()
        workJob = viewModelScope.launch {
            update(UpdateState.Checking)
            when (val result = repository.check()) {
                is UpdateCheck.UpToDate -> update(UpdateState.UpToDate)
                is UpdateCheck.Available -> update(UpdateState.Available(result.manifest))
                is UpdateCheck.Incompatible -> update(
                    UpdateState.Failed(
                        failure = InstallFailure.INCOMPATIBLE,
                        detail = "minSdk ${result.manifest.minSdk}",
                        nextStep = null,
                        manifest = result.manifest,
                    )
                )

                is UpdateCheck.Failed -> update(
                    UpdateState.Failed(InstallFailure.UNKNOWN, result.reason, null, null)
                )
            }
        }
    }

    /** Baixa, verifica e entra na cascata de instalação pelo degrau silencioso. */
    fun downloadAndInstall(manifest: UpdateManifest) {
        workJob?.cancel()
        workJob = viewModelScope.launch {
            update(UpdateState.Downloading(manifest, 0f))
            val downloaded = repository.download(manifest) { progress ->
                update(UpdateState.Downloading(manifest, progress))
            }
            if (!downloaded) {
                fail(manifest, InstallStep.SILENT, InstallFailure.CORRUPTED, "download falhou")
                return@launch
            }

            update(UpdateState.Verifying(manifest))
            when (val verification = repository.verify(manifest)) {
                is VerificationResult.Failed -> {
                    val failure = repository.failureOf(verification)
                    // Hash divergente significa arquivo inútil: apaga para que a próxima tentativa
                    // baixe de novo em vez de reaproveitar o corrompido.
                    if (failure == InstallFailure.CORRUPTED) repository.discardDownload(manifest)
                    fail(manifest, InstallStep.SILENT, failure, verification.detail)
                }

                VerificationResult.Ok -> startInstall(manifest, InstallStep.SILENT)
            }
        }
    }

    fun startInstall(manifest: UpdateManifest, step: InstallStep) {
        viewModelScope.launch {
            if (!repository.canRequestPackageInstalls()) {
                _uiState.value = _uiState.value.copy(canRequestPackageInstalls = false)
                update(UpdateState.NeedsUnknownSources(manifest))
                repository.record(
                    manifest.versionCode,
                    InstallStep.UNKNOWN_SOURCES_PERMISSION,
                    succeeded = false,
                    failure = InstallFailure.BLOCKED,
                    detail = "canRequestPackageInstalls falso",
                )
                return@launch
            }

            update(UpdateState.Installing(manifest, step))
            pendingManifest = manifest
            pendingStep = step

            when (val outcome = repository.install(manifest, step)) {
                is InstallOutcome.Failed ->
                    fail(manifest, step, outcome.failure, outcome.detail)

                else -> Unit // o desfecho real chega pelo InstallResultReceiver
            }
        }
    }

    private var pendingManifest: UpdateManifest? = null
    private var pendingStep: InstallStep = InstallStep.SILENT

    private fun onInstallOutcome(outcome: InstallOutcome) {
        val manifest = pendingManifest ?: return
        viewModelScope.launch {
            when (outcome) {
                InstallOutcome.Success -> {
                    repository.record(manifest.versionCode, pendingStep, succeeded = true)
                    repository.discardDownload(manifest)
                    update(UpdateState.Installed)
                }

                InstallOutcome.NeedsUserAction -> {
                    // O sistema recusou o silencioso e já abriu o diálogo: é o degrau 2.
                    if (pendingStep == InstallStep.SILENT) {
                        repository.record(
                            manifest.versionCode,
                            InstallStep.SILENT,
                            succeeded = false,
                            failure = null,
                            detail = "sistema pediu confirmação do usuário",
                        )
                        pendingStep = InstallStep.SYSTEM_DIALOG
                        update(UpdateState.Installing(manifest, InstallStep.SYSTEM_DIALOG))
                    }
                }

                is InstallOutcome.Failed ->
                    fail(manifest, pendingStep, outcome.failure, outcome.detail)
            }
        }
    }

    private suspend fun fail(
        manifest: UpdateManifest?,
        step: InstallStep,
        failure: InstallFailure,
        detail: String?,
    ) {
        manifest?.let {
            repository.record(it.versionCode, step, succeeded = false, failure = failure, detail = detail)
        }
        val next = InstallStatusMapper.nextStepAfter(step, failure)
        Log.w(TAG, "Instalação falhou em $step por $failure; próximo degrau: $next")
        update(UpdateState.Failed(failure, detail, next, manifest))
    }

    fun retryAt(manifest: UpdateManifest, step: InstallStep) {
        when (step) {
            InstallStep.SILENT, InstallStep.SYSTEM_DIALOG -> startInstall(manifest, step)
            else -> update(UpdateState.Failed(InstallFailure.UNKNOWN, null, step, manifest))
        }
    }

    fun refreshPermissionState() {
        _uiState.value = _uiState.value.copy(
            canRequestPackageInstalls = repository.canRequestPackageInstalls(),
        )
    }

    fun cancel() {
        workJob?.cancel()
        update(UpdateState.Idle)
    }

    private fun update(state: UpdateState) {
        _uiState.value = _uiState.value.copy(state = state)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UpdateViewModel::class.java)) {
                "Factory não sabe criar ${modelClass.name}"
            }
            return UpdateViewModel(application) as T
        }
    }

    private companion object {
        const val TAG = "UpdateViewModel"
    }
}
