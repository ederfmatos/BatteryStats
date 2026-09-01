package dev.ederfmatos.batterystats.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.PeriodStats

/** O grau de confiança de uma tela inteira, resumido num rótulo só. */
enum class ConfidenceLevel { HIGH, PARTIAL, LOW }

/**
 * Resume a confiança do período. Combina as duas coisas que corroem a medição de formas diferentes:
 * cobertura (quanto do tempo foi realmente medido) e proporção de janelas grosseiras (quanto do que
 * foi medido tem resolução suficiente).
 */
fun PeriodStats.confidenceLevel(): ConfidenceLevel {
    val coverageOk = !coverage.isPoor
    val windows = stats.windowCount
    val coarseFraction = if (windows > 0) {
        (windows - stats.highConfidenceWindowCount).toDouble() / windows
    } else {
        1.0
    }
    return when {
        coverageOk && coarseFraction <= 0.30 -> ConfidenceLevel.HIGH
        !coverageOk && coarseFraction > 0.30 -> ConfidenceLevel.LOW
        else -> ConfidenceLevel.PARTIAL
    }
}

/**
 * Uma peça de incerteza por tela, em repouso.
 *
 * Antes a tela empilhava seis blocos de texto de aviso, cinco deles `bodySmall` na mesma cor — e
 * um deles repetia, palavra por palavra, um parágrafo de 40 palavras que também estava em outra
 * tela. Seis avisos concorrentes não são seis vezes mais informação: são zero, porque a pessoa
 * aprende a pular todos.
 *
 * Aqui o chip afirma o grau, e o toque abre a explicação inteira — que continua completa, só deixa
 * de competir consigo mesma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfidenceChip(period: PeriodStats, modifier: Modifier = Modifier) {
    var sheetOpen by remember { mutableStateOf(false) }
    val level = period.confidenceLevel()

    AssistChip(
        onClick = { sheetOpen = true },
        label = { Text(stringResource(level.labelRes())) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = when (level) {
                ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.onSurfaceVariant
                ConfidenceLevel.PARTIAL -> MaterialTheme.colorScheme.onSurface
                ConfidenceLevel.LOW -> MaterialTheme.colorScheme.error
            },
        ),
        modifier = modifier,
    )

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            ConfidenceDetails(period)
        }
    }
}

@Composable
private fun ConfidenceDetails(period: PeriodStats) {
    val windows = period.stats.windowCount
    val coarse = windows - period.stats.highConfidenceWindowCount
    val stepUah = period.analysis.quantizationStepUah

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.confidence_sheet_title),
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider()

        LabeledRow(
            label = stringResource(R.string.coverage_label),
            value = stringResource(
                R.string.coverage_value,
                period.coverage.percent.toInt(),
                stringResource(R.string.coverage_period_24h),
            ),
            supporting = stringResource(R.string.confidence_coverage_explanation),
        )
        LabeledRow(
            label = stringResource(R.string.confidence_windows_label),
            value = "$coarse / $windows",
            supporting = stringResource(R.string.confidence_windows_explanation),
        )
        LabeledRow(
            label = stringResource(R.string.quantization_label),
            value = "$stepUah µAh",
            supporting = stringResource(R.string.confidence_step_explanation),
        )
        Text(
            text = stringResource(R.string.confidence_estimate_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ConfidenceLevel.labelRes(): Int = when (this) {
    ConfidenceLevel.HIGH -> R.string.confidence_high
    ConfidenceLevel.PARTIAL -> R.string.confidence_partial
    ConfidenceLevel.LOW -> R.string.confidence_low
}
