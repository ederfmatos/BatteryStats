package dev.ederfmatos.batterystats.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R

data class ReportUiState(
    val markdown: String = "",
    val generating: Boolean = false,
    val truncatedForLink: Boolean = false,
    val attachRawJson: Boolean = false,
)

/**
 * O relatório e as duas formas de enviá-lo.
 *
 * "Compartilhar" é o botão principal porque sempre funciona: o share sheet do Android atinge
 * qualquer app instalado. "Abrir no Claude" é secundário porque depende de a URL web ser capturada
 * pelo app, o que não é garantido em lugar nenhum da documentação.
 */
@Composable
fun ReportScreen(
    state: ReportUiState,
    onGenerate: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpenInClaude: () -> Unit,
    onToggleAttachRaw: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShare, enabled = state.markdown.isNotEmpty()) {
                        Text(stringResource(R.string.report_share))
                    }
                    TextButton(onClick = onCopy, enabled = state.markdown.isNotEmpty()) {
                        Text(stringResource(R.string.report_copy))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenInClaude, enabled = state.markdown.isNotEmpty()) {
                        Text(stringResource(R.string.report_open_claude))
                    }
                    TextButton(onClick = onGenerate) {
                        Text(stringResource(R.string.report_regenerate))
                    }
                }
                // Switch em vez de TextButton com "✓" concatenado: o estado precisa existir para
                // o leitor de tela, e o role certo vem de graça no Switch.
                ListItem(
                    headlineContent = { Text(stringResource(R.string.report_attach_raw_label)) },
                    trailingContent = {
                        Switch(
                            checked = state.attachRawJson,
                            onCheckedChange = onToggleAttachRaw,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (state.markdown.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.report_size, state.markdown.length),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.truncatedForLink) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = stringResource(R.string.report_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            if (state.generating) {
                Text(
                    text = stringResource(R.string.report_generating),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                // Markdown cru numa fonte monoespaçada: é exatamente o texto que vai ser enviado,
                // sem renderização que esconda diferenças.
                Text(
                    text = state.markdown,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    // Sem scroll horizontal: texto que rola na horizontal quebra o reflow e fica
                    // ilegível com fonte grande. O relatório é prosa em Markdown, pode quebrar linha.
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
