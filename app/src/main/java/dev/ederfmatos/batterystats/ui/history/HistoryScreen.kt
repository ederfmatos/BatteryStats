package dev.ederfmatos.batterystats.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.ui.MainUiState
import dev.ederfmatos.batterystats.ui.common.groupedDigits
import dev.ederfmatos.batterystats.ui.theme.LocalChartColors

@Composable
fun HistoryScreen(
    state: MainUiState,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chartColors = LocalChartColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.history.size < 2) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.history_level_chart),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Cores próprias, fora do ColorScheme: sob Material You primary e tertiary
                    // derivam do mesmo papel de parede e os marcadores de carga sumiriam na linha.
                    LevelOverTimeChart(
                        samples = state.history,
                        lineColor = chartColors.levelLine,
                        screenOnColor = chartColors.screenOnBand,
                        chargeMarkerColor = chartColors.chargeMarker,
                        gridColor = chartColors.grid,
                    )
                    Text(
                        text = stringResource(R.string.history_level_legend),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val hourly = state.periodStats?.stats?.hourly.orEmpty()
        if (hourly.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.history_hourly_chart),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HourlyDrainChart(
                        hourly = hourly,
                        barColor = chartColors.drainBar,
                        gridColor = chartColors.grid,
                    )
                    Text(
                        text = stringResource(R.string.history_hourly_legend),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HealthCard(state)

        // O relatório é o payoff do app, mas não é diário: fica a um toque de onde os dados que
        // ele resume estão sendo olhados, em vez de ocupar uma aba permanente.
        TextButton(onClick = onOpenReport) {
            Text(stringResource(R.string.report_share))
        }
    }
}

@Composable
private fun HealthCard(state: MainUiState) {
    val health = state.health ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.health_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val ratio = health.relativeRatio
            if (!health.hasEnoughHistory || ratio == null) {
                Text(
                    text = stringResource(R.string.health_insufficient),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.health_ratio, ratio * 100),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(
                        R.string.health_detail,
                        (health.currentFullChargeUah ?: 0L).groupedDigits(),
                        (health.bestObservedFullChargeUah ?: 0L).groupedDigits(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.health_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
