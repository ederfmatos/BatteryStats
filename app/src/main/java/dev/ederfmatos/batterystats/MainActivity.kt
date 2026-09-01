package dev.ederfmatos.batterystats

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ederfmatos.batterystats.data.export.ExportWriter
import dev.ederfmatos.batterystats.data.work.MaintenanceWorker
import dev.ederfmatos.batterystats.ui.AppRoot
import dev.ederfmatos.batterystats.ui.MainViewModel
import dev.ederfmatos.batterystats.ui.theme.BatteryStatsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MaintenanceWorker.schedule(this)

        setContent {
            BatteryStatsTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(application)
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                AppRoot(
                    state = state,
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
            }
        }
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
}
