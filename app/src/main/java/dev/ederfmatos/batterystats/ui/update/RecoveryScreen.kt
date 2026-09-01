package dev.ederfmatos.batterystats.ui.update

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.update.UpdateEndpoints

/**
 * Aparece quando o app falhou nos dois primeiros arranques depois de uma atualização.
 *
 * Não existe downgrade automático: o Android recusa instalar um versionCode menor por cima. A
 * única saída real é desinstalar, e isso apaga o banco — por isso o botão de exportar vem antes de
 * qualquer outra coisa nesta tela.
 */
@Composable
fun RecoveryScreen(
    currentVersionCode: Long,
    previousVersionCode: Long,
    onExport: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.recovery_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = if (previousVersionCode > 0) {
                        stringResource(
                            R.string.recovery_body,
                            currentVersionCode,
                            previousVersionCode,
                        )
                    } else {
                        stringResource(R.string.recovery_no_previous)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.recovery_downgrade_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onExport) {
                    Text(stringResource(R.string.recovery_export))
                }
                TextButton(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(UpdateEndpoints.RELEASES_PAGE_URL),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        Log.e("RecoveryScreen", "Sem navegador para abrir as releases", e)
                    }
                }) {
                    Text(stringResource(R.string.recovery_open_releases))
                }
                TextButton(onClick = onContinue) {
                    Text(stringResource(R.string.recovery_continue))
                }
            }
        }
    }
}
