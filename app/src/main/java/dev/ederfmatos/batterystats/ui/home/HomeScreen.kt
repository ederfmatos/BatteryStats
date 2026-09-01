package dev.ederfmatos.batterystats.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.domain.drain.Coverage
import dev.ederfmatos.batterystats.domain.drain.RegimeStats
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.ui.MainUiState
import dev.ederfmatos.batterystats.ui.common.ConfidenceChip
import dev.ederfmatos.batterystats.ui.common.LabeledRow
import dev.ederfmatos.batterystats.ui.common.PermissionCard
import dev.ederfmatos.batterystats.ui.common.formatDuration
import dev.ederfmatos.batterystats.ui.common.formatHours
import dev.ederfmatos.batterystats.ui.common.groupedDigits
import dev.ederfmatos.batterystats.ui.common.label

@Composable
fun HomeScreen(
    state: MainUiState,
    onStartSampling: () -> Unit,
    onStopSampling: () -> Unit,
    onPermissionsChanged: () -> Unit,
    onFixCoverage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionsChanged()
        if (granted) onStartSampling()
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onPermissionsChanged() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverageBanner(state, onFixCoverage)

        val snapshot = state.snapshot
        if (snapshot == null) {
            UnavailableCard()
        } else {
            LevelCard(snapshot, state)
            ReadingsCard(snapshot, state.currentMilliAmps)
        }

        SamplingCard(state, onStartSampling, onStopSampling) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onStartSampling()
            }
        }

        DrainCard(state)

        if (!state.ignoringBatteryOptimizations) {
            PermissionCard(
                title = stringResource(R.string.permission_battery_title),
                rationale = stringResource(R.string.permission_battery_rationale),
                actionLabel = stringResource(R.string.permission_battery_action),
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    batteryOptimizationLauncher.launch(intent)
                },
            )
        }
    }
}

/**
 * Banner persistente de cobertura baixa. Fica no topo da home de propósito: com 59% do tempo em
 * buracos, todo número abaixo dele descreve outra coisa que não o dia do usuário.
 */
/**
 * Banner de cobertura baixa.
 *
 * Afirma o fato e oferece a ação. As instruções de 40 palavras vivem só em Saúde da coleta — antes
 * o mesmo parágrafo aparecia nas duas telas, e repetir um texto longo na tela diária é o método
 * mais eficiente de treinar alguém a não lê-lo.
 */
@Composable
private fun CoverageBanner(state: MainUiState, onFix: () -> Unit) {
    val coverage = state.periodStats?.coverage ?: return
    if (!coverage.isPoor) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.home_coverage_banner, coverage.percent.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.coverage_fix_action))
            }
        }
    }
}

@Composable
private fun UnavailableCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_reading_unavailable),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.home_reading_unavailable_detail),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * O card herói, e o único elevado da tela.
 *
 * Junta o nível com a autonomia projetada porque é essa a pergunta que faz alguém abrir um app de
 * bateria. Antes a projeção estava em `bodyLarge`, enterrada no quarto card, depois de duas linhas
 * de regime.
 */
@Composable
private fun LevelCard(snapshot: BatterySnapshot, state: MainUiState) {
    val levelDescription = stringResource(R.string.level_description, snapshot.levelPct)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.unit_percent, snapshot.levelPct),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { snapshot.levelPct.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = levelDescription },
            )
            Text(
                text = "${snapshot.status.label()} · ${snapshot.plugType.label()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            val hours = state.periodStats?.projection?.hoursRemaining
            if (hours != null && hours.isFinite() && hours > 0) {
                HorizontalDivider()
                Text(
                    // "≈" porque a projeção é estimativa, não leitura.
                    text = stringResource(R.string.drain_projection_short, formatHours(hours)),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReadingsCard(snapshot: BatterySnapshot, currentMilliAmps: Double?) {
    val unavailable = stringResource(R.string.value_unavailable)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledRow(
                label = stringResource(R.string.home_temperature),
                value = snapshot.temperatureCelsius
                    ?.let { stringResource(R.string.unit_celsius, it) } ?: unavailable,
            )
            LabeledRow(
                label = stringResource(R.string.home_current_instant),
                value = currentMilliAmps
                    ?.let { stringResource(R.string.unit_milliamps, it) } ?: unavailable,
            )
            LabeledRow(
                label = stringResource(R.string.home_screen),
                value = stringResource(
                    if (snapshot.screenOn) R.string.screen_on else R.string.screen_off
                ),
            )
        }
    }
}

@Composable
private fun SamplingCard(
    state: MainUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestNotificationThenStart: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.sampling_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (state.samplingRunning) {
                    stringResource(
                        R.string.sampling_running,
                        stringResource(intervalLabelRes(state)),
                    )
                } else {
                    stringResource(R.string.sampling_stopped)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.samplingRunning) {
                Text(
                    text = stringResource(R.string.permission_notifications_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = if (state.samplingRunning) onStop else onRequestNotificationThenStart,
            ) {
                Text(
                    stringResource(
                        if (state.samplingRunning) R.string.sampling_stop
                        else R.string.sampling_start
                    )
                )
            }
        }
    }
}

private fun intervalLabelRes(state: MainUiState): Int = when (state.settings.samplingInterval) {
    dev.ederfmatos.batterystats.data.prefs.SamplingInterval.THIRTY_SECONDS -> R.string.interval_30s
    dev.ederfmatos.batterystats.data.prefs.SamplingInterval.ONE_MINUTE -> R.string.interval_1m
    dev.ederfmatos.batterystats.data.prefs.SamplingInterval.FIVE_MINUTES -> R.string.interval_5m
}

/**
 * O bloco de dreno.
 *
 * Enquanto não há janela nenhuma fechada, mostra o que falta em vez de zeros: um app que exibe
 * "0 mA · 0,0 %/h" na primeira hora está afirmando uma medição que não fez, que é exatamente o
 * defeito que o resto do app existe para evitar.
 */
@Composable
private fun DrainCard(state: MainUiState) {
    val period = state.periodStats ?: return
    if (period.stats.windowCount == 0) {
        NoMeasurementYetCard(state)
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.drain_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.drain_projection_basis_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DrainComparison(
                screenOn = period.stats.screenOn,
                screenOff = period.stats.screenOff,
            )
            // Uma peça de incerteza na tela; o resto da explicação vive no bottom sheet dela.
            ConfidenceChip(period)
            if (state.wakelockSuspicion) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            R.string.drain_wakelock_warning,
                            period.stats.screenOff.averageMilliAmps,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * O que aparece no lugar do bloco de dreno enquanto nenhuma janela fechou.
 *
 * Um app que exibe "0 mA · 0,0 %/h" na primeira hora está afirmando uma medição que não fez —
 * exatamente o defeito que o resto do app existe para evitar.
 */
@Composable
private fun NoMeasurementYetCard(state: MainUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.drain_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.sampleCount == 0) {
                Text(
                    text = stringResource(R.string.empty_never_measured),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.empty_progress_to_ranking,
                        formatDuration(System.currentTimeMillis() - state.firstSampleMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
