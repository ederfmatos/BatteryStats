package dev.ederfmatos.batterystats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ederfmatos.batterystats.data.export.ExportWriter
import dev.ederfmatos.batterystats.data.update.UpdateRepository
import dev.ederfmatos.batterystats.data.work.MaintenanceWorker
import dev.ederfmatos.batterystats.data.work.UpdateCheckWorker
import dev.ederfmatos.batterystats.domain.update.InstallStep
import dev.ederfmatos.batterystats.domain.update.UpdateManifest
import dev.ederfmatos.batterystats.ui.AppRoot
import dev.ederfmatos.batterystats.ui.MainViewModel
import dev.ederfmatos.batterystats.ui.theme.BatteryStatsTheme
import dev.ederfmatos.batterystats.ui.update.RecoveryScreen
import dev.ederfmatos.batterystats.ui.update.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingExportUri: Uri? = null

    private val createDocument = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> if (uri != null) export(uri, asJson = uri.toString().endsWith(".json")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MaintenanceWorker.schedule(this)
        UpdateCheckWorker.schedule(this)

        val container = application.appContainer
        val needsRecovery = application.let { it as BatteryStatsApplication }.needsRecovery

        setContent {
            BatteryStatsTheme {
                var recoveryDismissed by remember { mutableStateOf(false) }

                if (needsRecovery && !recoveryDismissed) {
                    RecoveryScreen(
                        currentVersionCode = container.updateChecker.installedVersionCode(),
                        previousVersionCode = container.crashGuard.previousVersionCode(),
                        onExport = { createDocument.launch("batterystats.json") },
                        onContinue = {
                            container.crashGuard.confirmHealthy()
                            recoveryDismissed = true
                        },
                    )
                    return@BatteryStatsTheme
                }

                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(application)
                )
                val updateViewModel: UpdateViewModel = viewModel(
                    factory = UpdateViewModel.Factory(application)
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

                AppRoot(
                    state = state,
                    updateState = updateState,
                    onCheckUpdate = updateViewModel::check,
                    onDownloadAndInstall = updateViewModel::downloadAndInstall,
                    onRetryInstallAt = ::onRetryInstallAt,
                    onCancelUpdate = updateViewModel::cancel,
                    onExportRequested = { createDocument.launch("batterystats.json") },
                    onStartSampling = viewModel::startSampling,
                    onStopSampling = viewModel::stopSampling,
                    onRefresh = viewModel::refresh,
                    onPeriodChange = viewModel::setAppPeriod,
                    onIntervalChange = viewModel::setSamplingInterval,
                    onStartOnBootChange = viewModel::setStartOnBoot,
                    onForceCalibration = viewModel::forceCalibration,
                    onRecalibrate = viewModel::recalibrate,
                    onExport = ::export,
                )

                // A UI subiu inteira: a versão instalada é considerada boa.
                container.crashGuard.confirmHealthy()
            }
        }
    }

    /**
     * Degraus 3, 4 e 5 da cascata precisam de uma Activity para disparar Intents; os degraus 1 e 2
     * ficam no ViewModel.
     */
    private fun onRetryInstallAt(manifest: UpdateManifest, step: InstallStep) {
        val repository: UpdateRepository = application.appContainer.updateRepository
        when (step) {
            InstallStep.UNKNOWN_SOURCES_PERMISSION ->
                startActivitySafely(repository.unknownSourcesIntent())

            InstallStep.OPEN_APK -> {
                val intent = repository.openApkIntent(manifest)
                if (intent == null) {
                    Toast.makeText(this, R.string.update_error_unknown, Toast.LENGTH_SHORT).show()
                } else {
                    startActivitySafely(intent)
                }
            }

            InstallStep.DIRECT_LINK -> startActivitySafely(repository.directDownloadIntent())

            InstallStep.SILENT, InstallStep.SYSTEM_DIALOG -> Unit
        }
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "Nenhuma Activity atende ${intent.action}", e)
            Toast.makeText(this, R.string.update_error_unknown, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        pendingExportUri = null
    }

    private fun export(uri: Uri, asJson: Boolean) {
        lifecycleScope.launch {
            val container = application.appContainer
            val samples = container.statsRepository.snapshotsSince(0L)
            val ok = ExportWriter(this@MainActivity).write(uri, samples, asJson)
            Toast.makeText(
                this@MainActivity,
                getString(
                    if (ok) R.string.settings_export_done else R.string.settings_export_failed
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
