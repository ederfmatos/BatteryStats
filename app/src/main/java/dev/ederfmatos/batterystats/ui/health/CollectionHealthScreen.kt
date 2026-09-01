package dev.ederfmatos.batterystats.ui.health

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import dev.ederfmatos.batterystats.ui.MainUiState
import dev.ederfmatos.batterystats.ui.common.formatDuration

/**
 * O quanto a coleta está de pé. É a primeira tela a olhar quando os números parecerem estranhos:
 * um dreno médio calculado sobre 41% do tempo não é o dreno médio do dia.
 */
@Composable
fun CollectionHealthScreen(state: MainUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coverage = state.periodStats?.coverage

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.health_collection_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (coverage == null) {
                    Text(
                        text = stringResource(R.string.health_collection_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.coverage_value,
                            coverage.percent.toInt(),
                            stringResource(R.string.coverage_period_24h),
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    LinearProgressIndicator(
                        progress = { coverage.fraction.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InfoRow(
                        stringResource(R.string.health_collection_gaps),
                        stringResource(
                            R.string.coverage_gaps,
                            coverage.gapCount,
                            formatDuration(coverage.gapMs),
                        ),
                    )
                    InfoRow(
                        stringResource(R.string.health_collection_service_deaths),
                        state.serviceKillCount.toString(),
                    )
                }
            }
        }

        if (coverage?.isPoor == true) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.health_collection_poor_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.health_collection_samsung_steps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.health_collection_actions),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { openBatterySettings(context) }) {
                    Text(stringResource(R.string.health_collection_open_battery_settings))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !state.canScheduleExactAlarms) {
                    Text(
                        text = stringResource(R.string.health_collection_exact_alarm_rationale),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { openExactAlarmSettings(context) }) {
                        Text(stringResource(R.string.health_collection_exact_alarm_action))
                    }
                }
            }
        }
    }
}

private fun openBatterySettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        android.util.Log.e(TAG, "Aparelho sem tela de otimização de bateria", e)
    }
}

private fun openExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(android.net.Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        android.util.Log.e(TAG, "Aparelho sem tela de alarmes exatos", e)
    }
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

private const val TAG = "CollectionHealthScreen"
