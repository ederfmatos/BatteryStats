package dev.ederfmatos.batterystats.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R

/**
 * O modo avançado.
 *
 * Um comando, uma vez na vida, e o app troca estimativa por correlação por contadores medidos por
 * app. O grant sobrevive a reinício e às auto-atualizações; só não sobrevive a desinstalar, o que
 * de todo modo já apagaria o banco.
 *
 * Deliberadamente **não** instrui a mexer em `hidden_api_policy`, que é o que GSam e
 * BetterBatteryStats pedem: aquilo desliga a proteção de API oculta do aparelho inteiro, para
 * todos os apps, em troca de números que continuam sendo modelo.
 */
@Composable
fun AdvancedModeCard(
    granted: Boolean,
    command: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.advanced_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (granted) R.string.advanced_granted else R.string.advanced_not_granted
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!granted) {
                Text(
                    text = stringResource(R.string.advanced_rationale),
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                TextButton(onClick = { copyCommand(context, command) }) {
                    Text(stringResource(R.string.advanced_copy_command))
                }
                Text(
                    text = stringResource(R.string.advanced_persistence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.advanced_still_not_mah),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun copyCommand(context: Context, command: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("adb", command))
    Toast.makeText(context, R.string.advanced_command_copied, Toast.LENGTH_SHORT).show()
}
