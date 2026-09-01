package dev.ederfmatos.batterystats.ui.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.usage.UsageAccess
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import dev.ederfmatos.batterystats.ui.MainUiState
import dev.ederfmatos.batterystats.ui.common.PermissionCard
import dev.ederfmatos.batterystats.ui.common.groupedDigits
import dev.ederfmatos.batterystats.ui.common.label

@Composable
fun DiagnosticsScreen(
    state: MainUiState,
    onForceCalibration: (divisor: Int, inverted: Boolean) -> Unit,
    onRecalibrate: () -> Unit,
    onPermissionsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(
                    stringResource(R.string.diagnostics_device),
                    "${Build.MANUFACTURER} ${Build.MODEL}",
                )
                InfoRow(
                    stringResource(R.string.diagnostics_android),
                    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                )
                InfoRow(
                    stringResource(R.string.diagnostics_samples),
                    state.sampleCount.toString(),
                )
                InfoRow(
                    stringResource(R.string.diagnostics_gaps),
                    (state.periodStats?.analysis?.gaps?.size ?: 0).toString(),
                )
                val coverage = state.periodStats?.coverage
                if (coverage != null) {
                    InfoRow(
                        stringResource(R.string.coverage_label),
                        stringResource(
                            R.string.coverage_value,
                            coverage.percent.toInt(),
                            stringResource(R.string.coverage_period_24h),
                        ),
                    )
                }
                QuantizationRow(state)
                val stats = state.periodStats?.stats
                if (stats != null) {
                    InfoRow(
                        stringResource(R.string.diagnostics_windows),
                        stringResource(
                            R.string.windows_confidence,
                            stats.windowCount,
                            stats.highConfidenceWindowCount,
                        ),
                    )
                }
            }
        }

        CalibrationCard(state, onForceCalibration, onRecalibrate)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.diagnostics_permissions),
                    style = MaterialTheme.typography.titleMedium,
                )
                InfoRow(
                    stringResource(R.string.permission_usage_title),
                    stringResource(
                        if (state.hasUsageAccess) R.string.diagnostics_granted
                        else R.string.diagnostics_denied
                    ),
                )
                InfoRow(
                    stringResource(R.string.permission_battery_title),
                    stringResource(
                        if (state.ignoringBatteryOptimizations) R.string.diagnostics_granted
                        else R.string.diagnostics_denied
                    ),
                )
            }
        }

        if (!state.hasUsageAccess) {
            PermissionCard(
                title = stringResource(R.string.permission_usage_title),
                rationale = stringResource(R.string.permission_usage_rationale),
                actionLabel = stringResource(R.string.permission_usage_action),
                onAction = {
                    UsageAccess.openSettings(context)
                    onPermissionsChanged()
                },
            )
        }
    }
}

/**
 * O degrau de quantização é o número mais importante desta tela: ele define o piso de incerteza de
 * toda medição, e é ele que explica por que janelas curtas devolvem múltiplos de um mesmo valor.
 */
@Composable
private fun QuantizationRow(state: MainUiState) {
    val stepUah = state.settings.quantizationStepUah
    val intervalMs = state.settings.samplingInterval.millis
    InfoRow(
        stringResource(R.string.quantization_label),
        if (stepUah <= 0L) {
            stringResource(R.string.quantization_none)
        } else {
            val intervalHours = intervalMs / 3_600_000.0
            stringResource(
                R.string.quantization_value,
                stepUah.groupedDigits(),
                (stepUah / 1000.0) / intervalHours,
                state.settings.samplingInterval.label(),
            )
        },
    )
}

@Composable
private fun CalibrationCard(
    state: MainUiState,
    onForceCalibration: (divisor: Int, inverted: Boolean) -> Unit,
    onRecalibrate: () -> Unit,
) {
    val calibration = state.settings.calibration
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.diagnostics_calibration),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_calibration_value,
                    calibration.divisor,
                    stringResource(
                        if (calibration.inverted) R.string.diagnostics_sign_inverted
                        else R.string.diagnostics_sign_normal
                    ),
                    stringResource(calibration.source.labelRes()),
                    calibration.sampleCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.diagnostics_force_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = calibration.divisor == 1000,
                    onClick = { onForceCalibration(1000, calibration.inverted) },
                    label = { Text(stringResource(R.string.diagnostics_force_ua)) },
                )
                FilterChip(
                    selected = calibration.divisor == 1,
                    onClick = { onForceCalibration(1, calibration.inverted) },
                    label = { Text(stringResource(R.string.diagnostics_force_ma)) },
                )
            }
            FilterChip(
                selected = calibration.inverted,
                onClick = { onForceCalibration(calibration.divisor, !calibration.inverted) },
                label = { Text(stringResource(R.string.diagnostics_force_invert)) },
            )
            TextButton(onClick = onRecalibrate) {
                Text(stringResource(R.string.diagnostics_recalibrate))
            }
        }
    }
}

private fun CurrentCalibration.Source.labelRes(): Int = when (this) {
    CurrentCalibration.Source.DEFAULT -> R.string.diagnostics_source_default
    CurrentCalibration.Source.AUTO -> R.string.diagnostics_source_auto
    CurrentCalibration.Source.MANUAL -> R.string.diagnostics_source_manual
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}
