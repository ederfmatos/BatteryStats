package dev.ederfmatos.batterystats.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.appContainerFromContext
import dev.ederfmatos.batterystats.data.StatsPeriod
import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.ui.MainUiState
import dev.ederfmatos.batterystats.ui.common.PermissionCard
import dev.ederfmatos.batterystats.ui.common.formatDuration

/**
 * Ranking de consumo estimado. O rodapé com a ressalva é fixo e não rola junto com a lista — a
 * distinção entre estimativa e medição não pode depender do usuário rolar até o fim.
 */
@Composable
fun AppsScreen(
    state: MainUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onOpenUsageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        // Escolha exclusiva: SegmentedButton, não FilterChip — FilterChip comunica filtros
        // múltiplos e independentes, que não é o caso aqui.
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = state.appPeriod == StatsPeriod.TODAY,
                onClick = { onPeriodChange(StatsPeriod.TODAY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.apps_period_today))
            }
            SegmentedButton(
                selected = state.appPeriod == StatsPeriod.LAST_7_DAYS,
                onClick = { onPeriodChange(StatsPeriod.LAST_7_DAYS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.apps_period_7d))
            }
        }

        // A permissão é pedida aqui, e não numa aba de manutenção: este é o momento em que ela
        // faz falta — o usuário abriu o ranking e ele está vazio.
        if (!state.hasUsageAccess) {
            PermissionCard(
                title = stringResource(R.string.permission_usage_title),
                rationale = stringResource(R.string.permission_usage_rationale),
                actionLabel = stringResource(R.string.permission_usage_action),
                onAction = onOpenUsageSettings,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val ranking = state.appRanking.filter { it.estimatedMilliAmpHours > 0.0 }
        if (ranking.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Uma espera com prazo é tolerável; um "ainda não" indefinido faz qualquer um
                // concluir que o app está quebrado.
                Text(
                    text = if (state.sampleCount == 0) {
                        stringResource(R.string.empty_never_measured)
                    } else {
                        stringResource(
                            R.string.empty_progress_to_ranking,
                            formatDuration(System.currentTimeMillis() - state.firstSampleMs),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ranking, key = { it.packageName }) { usage -> AppRow(usage) }
            }
        }

        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = stringResource(R.string.apps_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun AppRow(usage: AppEnergyUsage) {
    val context = LocalContext.current
    val container = remember(context) { appContainerFromContext(context) }
    val label = if (usage.isSystemBucket) {
        stringResource(R.string.apps_system_bucket)
    } else {
        remember(usage.packageName) { container.appLabelResolver.label(usage.packageName) }
    }
    val iconPainter: Painter? = if (usage.isSystemBucket) {
        null
    } else {
        remember(usage.packageName) {
            container.appLabelResolver.icon(usage.packageName)
                ?.let { drawable ->
                    runCatching { drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
                }
                ?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (usage.isSystemBucket) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Box(Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleSmall)
                if (!usage.isSystemBucket) {
                    Text(
                        text = stringResource(
                            R.string.apps_foreground_time,
                            formatDuration(usage.foregroundMs),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.apps_avg_ma,
                            usage.averageMilliAmpsInForeground,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // O "≈" marca estimativa no próprio número. Um glifo repetido em toda estimativa é
            // aprendido em um dia; um parágrafo de aviso em toda tela é ignorado em uma semana.
            Text(
                text = stringResource(R.string.apps_mah_estimated, usage.estimatedMilliAmpHours),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private const val ICON_PX = 96
