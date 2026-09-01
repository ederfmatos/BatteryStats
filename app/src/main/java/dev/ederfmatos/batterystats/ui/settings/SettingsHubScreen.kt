package dev.ederfmatos.batterystats.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.prefs.SamplingInterval
import dev.ederfmatos.batterystats.ui.MainUiState
import dev.ederfmatos.batterystats.ui.SubScreen
import dev.ederfmatos.batterystats.ui.common.label

/**
 * Hub de manutenção.
 *
 * Existe para tirar da barra de navegação tudo que não é consultado todo dia. Antes eram 8 abas
 * disputando 360dp de largura, o que dava 45dp por item — abaixo do alvo mínimo de toque de 48dp,
 * com os rótulos truncando. Agora são 4 abas e o resto vive aqui.
 */
@Composable
fun SettingsHubScreen(
    state: MainUiState,
    onNavigate: (SubScreen) -> Unit,
    onIntervalChange: (SamplingInterval) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onUpdateNotificationsChange: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    updateAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_interval),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    SamplingInterval.entries.forEachIndexed { index, interval ->
                        SegmentedButton(
                            selected = state.settings.samplingInterval == interval,
                            onClick = { onIntervalChange(interval) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SamplingInterval.entries.size,
                            ),
                        ) {
                            Text(interval.label())
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_boot)) },
                    trailingContent = {
                        Switch(
                            checked = state.settings.startOnBoot,
                            onCheckedChange = onStartOnBootChange,
                        )
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_update_notifications))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.settings.updateNotificationsEnabled,
                            onCheckedChange = onUpdateNotificationsChange,
                        )
                    },
                )
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column {
                HubRow(
                    label = stringResource(R.string.settings_appearance),
                    onClick = { onNavigate(SubScreen.APPEARANCE) },
                )
                HorizontalDivider()
                HubRow(
                    label = stringResource(R.string.settings_collection_health),
                    supporting = state.periodStats?.coverage?.let {
                        stringResource(
                            R.string.coverage_value,
                            it.percent.toInt(),
                            stringResource(R.string.coverage_period_24h),
                        )
                    },
                    showBadge = state.periodStats?.coverage?.isPoor == true,
                    onClick = { onNavigate(SubScreen.COLLECTION_HEALTH) },
                )
                HorizontalDivider()
                HubRow(
                    label = stringResource(R.string.settings_report),
                    onClick = { onNavigate(SubScreen.REPORT) },
                )
                HorizontalDivider()
                HubRow(
                    label = stringResource(R.string.settings_update),
                    showBadge = updateAvailable,
                    onClick = { onNavigate(SubScreen.UPDATE) },
                )
                HorizontalDivider()
                HubRow(
                    label = stringResource(R.string.settings_diagnostics),
                    onClick = { onNavigate(SubScreen.DIAGNOSTICS) },
                )
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_export),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onExportCsv) {
                        Text(stringResource(R.string.settings_export_csv))
                    }
                    TextButton(onClick = onExportJson) {
                        Text(stringResource(R.string.settings_export_json))
                    }
                }
                Text(
                    text = stringResource(R.string.settings_retention),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HubRow(
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    showBadge: Boolean = false,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (showBadge) {
            { Badge() }
        } else {
            null
        },
    )
}
