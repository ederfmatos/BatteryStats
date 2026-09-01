package dev.ederfmatos.batterystats.ui.update

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material3.LinearProgressIndicator
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
import dev.ederfmatos.batterystats.data.update.UpdateState
import dev.ederfmatos.batterystats.domain.update.InstallFailure
import dev.ederfmatos.batterystats.domain.update.InstallStep
import dev.ederfmatos.batterystats.domain.update.UpdateAttempt
import dev.ederfmatos.batterystats.domain.update.UpdateManifest

/**
 * Estado da atualização, explícito de ponta a ponta. Cada falha traz a ação que a resolve — a
 * regra da cascata é nunca deixar o usuário sem próximo passo.
 */
@Composable
fun UpdateScreen(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownloadAndInstall: (UpdateManifest) -> Unit,
    onRetryAt: (UpdateManifest, InstallStep) -> Unit,
    onCancel: () -> Unit,
    onExportRequested: () -> Unit,
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.update_installed,
                        state.installedVersionName,
                        state.installedVersionCode,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                StateBlock(state.state, onDownloadAndInstall, onCancel)
                TextButton(onClick = onCheck) { Text(stringResource(R.string.update_check_now)) }
            }
        }

        (state.state as? UpdateState.Failed)?.let { failed ->
            FailureCard(
                failed = failed,
                context = context,
                onRetryAt = onRetryAt,
                onExportRequested = onExportRequested,
            )
        }

        (state.state as? UpdateState.NeedsUnknownSources)?.let { needs ->
            UnknownSourcesCard(needs.manifest, context, onRetryAt)
        }

        DirectLinkCard(context)
        HistoryCard(state.attempts)
    }
}

@Composable
private fun StateBlock(
    state: UpdateState,
    onDownloadAndInstall: (UpdateManifest) -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> Text(stringResource(R.string.update_state_checking))
        UpdateState.UpToDate -> Text(stringResource(R.string.update_state_up_to_date))

        is UpdateState.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    R.string.update_state_available,
                    state.manifest.versionName,
                    formatBytes(state.manifest.sizeBytes),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.manifest.changelog.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_changelog),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(text = state.manifest.changelog, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onDownloadAndInstall(state.manifest) }) {
                Text(stringResource(R.string.update_download_install))
            }
        }

        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.update_state_downloading, (state.progress * 100).toInt())
            )
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) }
        }

        is UpdateState.Verifying -> Text(stringResource(R.string.update_state_verifying))

        is UpdateState.Installing -> Text(
            stringResource(
                if (state.step == InstallStep.SILENT) {
                    R.string.update_state_installing_silent
                } else {
                    R.string.update_state_installing_dialog
                }
            )
        )

        is UpdateState.NeedsUnknownSources -> Unit
        UpdateState.Installed -> Text(stringResource(R.string.update_state_installed))
        is UpdateState.Failed -> Unit
    }
}

@Composable
private fun FailureCard(
    failed: UpdateState.Failed,
    context: Context,
    onRetryAt: (UpdateManifest, InstallStep) -> Unit,
    onExportRequested: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(messageFor(failed.failure)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            failed.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            when (failed.failure) {
                InstallFailure.CONFLICT -> {
                    Text(
                        text = stringResource(R.string.update_error_conflict_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onExportRequested) {
                        Text(stringResource(R.string.recovery_export))
                    }
                }

                InstallFailure.INCOMPATIBLE -> Text(
                    text = stringResource(
                        R.string.update_error_incompatible_detail,
                        failed.manifest?.minSdk ?: 0,
                        Build.VERSION.SDK_INT,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )

                InstallFailure.STORAGE -> failed.manifest?.let { manifest ->
                    Text(
                        text = stringResource(
                            R.string.update_error_storage_detail,
                            formatBytes(manifest.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                InstallFailure.ABORTED -> failed.manifest?.let { manifest ->
                    TextButton(onClick = { onRetryAt(manifest, InstallStep.SYSTEM_DIALOG) }) {
                        Text(stringResource(R.string.update_error_aborted_action))
                    }
                }

                else -> Unit
            }

            // Degrau seguinte da cascata, quando existe.
            val manifest = failed.manifest
            when (failed.nextStep) {
                InstallStep.OPEN_APK -> if (manifest != null) {
                    Text(
                        text = stringResource(R.string.update_step_open_apk_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = { onRetryAt(manifest, InstallStep.OPEN_APK) }) {
                        Text(stringResource(R.string.update_step_open_apk))
                    }
                }

                InstallStep.DIRECT_LINK -> TextButton(onClick = { openLink(context) }) {
                    Text(stringResource(R.string.update_step_direct_link))
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun UnknownSourcesCard(
    manifest: UpdateManifest,
    context: Context,
    onRetryAt: (UpdateManifest, InstallStep) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.update_unknown_sources_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.update_unknown_sources_rationale),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onRetryAt(manifest, InstallStep.UNKNOWN_SOURCES_PERMISSION) }) {
                Text(stringResource(R.string.update_unknown_sources_action))
            }
        }
    }
}

@Composable
private fun DirectLinkCard(context: Context) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.update_step_direct_link),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openLink(context) }) {
                    Text(stringResource(R.string.update_step_direct_link))
                }
                TextButton(onClick = { copyLink(context) }) {
                    Text(stringResource(R.string.update_step_copy_link))
                }
                TextButton(onClick = { shareLink(context) }) {
                    Text(stringResource(R.string.update_step_share_link))
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(attempts: List<UpdateAttempt>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.update_history_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (attempts.isEmpty()) {
                Text(
                    text = stringResource(R.string.update_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                attempts.forEach { attempt ->
                    Text(
                        text = stringResource(
                            R.string.update_history_entry,
                            attempt.versionCode,
                            stringResource(stepLabel(attempt.step)),
                            attempt.failure?.name
                                ?: stringResource(R.string.update_history_ok),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun messageFor(failure: InstallFailure): Int = when (failure) {
    InstallFailure.CONFLICT -> R.string.update_error_conflict
    InstallFailure.INCOMPATIBLE -> R.string.update_error_incompatible
    InstallFailure.STORAGE -> R.string.update_error_storage
    InstallFailure.ABORTED -> R.string.update_error_aborted
    InstallFailure.BLOCKED -> R.string.update_error_blocked
    InstallFailure.CORRUPTED -> R.string.update_error_corrupted
    InstallFailure.UNKNOWN -> R.string.update_error_unknown
}

private fun stepLabel(step: InstallStep): Int = when (step) {
    InstallStep.SILENT -> R.string.update_step_silent
    InstallStep.SYSTEM_DIALOG -> R.string.update_step_system_dialog
    InstallStep.UNKNOWN_SOURCES_PERMISSION -> R.string.update_step_permission
    InstallStep.OPEN_APK -> R.string.update_step_apk
    InstallStep.DIRECT_LINK -> R.string.update_step_link
}

private fun openLink(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateEndpoints.LATEST_APK_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        Log.e(TAG, "Sem navegador para abrir o link de download", e)
    }
}

private fun copyLink(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(
        ClipData.newPlainText("BatteryStats APK", UpdateEndpoints.LATEST_APK_URL)
    )
    Toast.makeText(context, R.string.update_link_copied, Toast.LENGTH_SHORT).show()
}

private fun shareLink(context: Context) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, UpdateEndpoints.LATEST_APK_URL)
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: android.content.ActivityNotFoundException) {
        Log.e(TAG, "Nenhum app para compartilhar o link", e)
    }
}

private fun formatBytes(bytes: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

private const val TAG = "UpdateScreen"
