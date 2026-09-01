package dev.ederfmatos.batterystats.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.StatsPeriod
import dev.ederfmatos.batterystats.data.prefs.SamplingInterval
import dev.ederfmatos.batterystats.data.prefs.ThemeMode
import dev.ederfmatos.batterystats.data.update.UpdateState
import dev.ederfmatos.batterystats.domain.update.InstallStep
import dev.ederfmatos.batterystats.domain.update.UpdateManifest
import dev.ederfmatos.batterystats.ui.apps.AppsScreen
import dev.ederfmatos.batterystats.ui.diagnostics.DiagnosticsScreen
import dev.ederfmatos.batterystats.ui.health.CollectionHealthScreen
import dev.ederfmatos.batterystats.ui.history.HistoryScreen
import dev.ederfmatos.batterystats.ui.home.HomeScreen
import dev.ederfmatos.batterystats.ui.report.ReportScreen
import dev.ederfmatos.batterystats.ui.report.ReportUiState
import dev.ederfmatos.batterystats.ui.settings.AppearanceScreen
import dev.ederfmatos.batterystats.ui.settings.SettingsHubScreen
import dev.ederfmatos.batterystats.ui.update.UpdateScreen
import dev.ederfmatos.batterystats.ui.update.UpdateUiState

/**
 * Os destinos de nível superior.
 *
 * Quatro, e não oito: numa tela de 360dp, oito itens davam 45dp cada — abaixo do alvo mínimo de
 * toque de 48dp, com "Diagnóstico" e "Atualizar" truncando. O critério é frequência de uso: aqui
 * ficam as três telas de consulta diária mais o hub de manutenção.
 */
private enum class Tab(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    NOW(R.string.tab_now, R.drawable.ic_tab_now),
    APPS(R.string.tab_apps, R.drawable.ic_tab_apps),
    HISTORY(R.string.tab_history, R.drawable.ic_tab_history),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings),
}

/** Telas de manutenção, alcançadas pelo hub ou por um atalho contextual. */
enum class SubScreen(@param:StringRes val titleRes: Int) {
    APPEARANCE(R.string.settings_appearance),
    COLLECTION_HEALTH(R.string.health_collection_title),
    REPORT(R.string.report_title),
    UPDATE(R.string.update_title),
    DIAGNOSTICS(R.string.diagnostics_title),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    initialSubScreen: SubScreen? = null,
    state: MainUiState,
    updateState: UpdateUiState,
    reportState: ReportUiState,
    snackbarHostState: SnackbarHostState,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstall: (UpdateManifest) -> Unit,
    onRetryInstallAt: (UpdateManifest, InstallStep) -> Unit,
    onCancelUpdate: () -> Unit,
    onExportRequested: () -> Unit,
    onGenerateReport: () -> Unit,
    onShareReport: () -> Unit,
    onCopyReport: () -> Unit,
    onOpenReportInClaude: () -> Unit,
    onToggleAttachRaw: (Boolean) -> Unit,
    onStartSampling: () -> Unit,
    onStopSampling: () -> Unit,
    onRefresh: () -> Unit,
    onPeriodChange: (StatsPeriod) -> Unit,
    onIntervalChange: (SamplingInterval) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onUpdateNotificationsChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onForceCalibration: (Int, Boolean) -> Unit,
    onRecalibrate: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    // rememberSaveable, não remember: sem isso o app volta para "Agora" a cada rotação ou troca
    // de tema, e num aparelho que mata processo agressivamente isso acontece o tempo todo.
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    var subScreen by rememberSaveable { mutableStateOf(initialSubScreen) }

    BackHandler(enabled = subScreen != null) { subScreen = null }

    val updateAvailable = updateState.state is UpdateState.Available
    val coveragePoor = state.periodStats?.coverage?.isPoor == true

    Scaffold(
        topBar = {
            val current = subScreen
            if (current == null) {
                TopAppBar(title = { Text(stringResource(tab.titleRes)) })
            } else {
                TopAppBar(
                    title = { Text(stringResource(current.titleRes)) },
                    navigationIcon = {
                        IconButton(onClick = { subScreen = null }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
        // A barra some nas sub-telas: elas não são destinos de nível superior.
        bottomBar = {
            if (subScreen == null) {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = {
                                val showBadge = entry == Tab.SETTINGS &&
                                    (updateAvailable || coveragePoor)
                                if (showBadge) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(
                                            painter = painterResource(entry.iconRes),
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    Icon(
                                        painter = painterResource(entry.iconRes),
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = { Text(stringResource(entry.titleRes)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        val current = subScreen
        if (current != null) {
            SubScreenContent(
                subScreen = current,
                state = state,
                updateState = updateState,
                reportState = reportState,
                modifier = contentModifier,
                onCheckUpdate = onCheckUpdate,
                onDownloadAndInstall = onDownloadAndInstall,
                onRetryInstallAt = onRetryInstallAt,
                onCancelUpdate = onCancelUpdate,
                onExportRequested = onExportRequested,
                onGenerateReport = onGenerateReport,
                onShareReport = onShareReport,
                onCopyReport = onCopyReport,
                onOpenReportInClaude = onOpenReportInClaude,
                onToggleAttachRaw = onToggleAttachRaw,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onForceCalibration = onForceCalibration,
                onRecalibrate = onRecalibrate,
                onOpenUsageSettings = onOpenUsageSettings,
                onRefresh = onRefresh,
            )
            return@Scaffold
        }

        when (tab) {
            Tab.NOW -> HomeScreen(
                state = state,
                onStartSampling = onStartSampling,
                onStopSampling = onStopSampling,
                onPermissionsChanged = onRefresh,
                onFixCoverage = { subScreen = SubScreen.COLLECTION_HEALTH },
                modifier = contentModifier,
            )

            Tab.APPS -> AppsScreen(
                state = state,
                onPeriodChange = onPeriodChange,
                onOpenUsageSettings = onOpenUsageSettings,
                modifier = contentModifier,
            )

            Tab.HISTORY -> HistoryScreen(
                state = state,
                onOpenReport = { subScreen = SubScreen.REPORT },
                modifier = contentModifier,
            )

            Tab.SETTINGS -> SettingsHubScreen(
                state = state,
                onNavigate = { subScreen = it },
                onIntervalChange = onIntervalChange,
                onStartOnBootChange = onStartOnBootChange,
                onUpdateNotificationsChange = onUpdateNotificationsChange,
                onExportCsv = onExportCsv,
                onExportJson = onExportJson,
                updateAvailable = updateAvailable,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun SubScreenContent(
    subScreen: SubScreen,
    state: MainUiState,
    updateState: UpdateUiState,
    reportState: ReportUiState,
    modifier: Modifier,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstall: (UpdateManifest) -> Unit,
    onRetryInstallAt: (UpdateManifest, InstallStep) -> Unit,
    onCancelUpdate: () -> Unit,
    onExportRequested: () -> Unit,
    onGenerateReport: () -> Unit,
    onShareReport: () -> Unit,
    onCopyReport: () -> Unit,
    onOpenReportInClaude: () -> Unit,
    onToggleAttachRaw: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onForceCalibration: (Int, Boolean) -> Unit,
    onRecalibrate: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (subScreen) {
        SubScreen.APPEARANCE -> AppearanceScreen(
            themeMode = state.settings.themeMode,
            dynamicColorEnabled = state.settings.dynamicColorEnabled,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            modifier = modifier,
        )

        SubScreen.COLLECTION_HEALTH -> CollectionHealthScreen(state = state, modifier = modifier)

        SubScreen.REPORT -> ReportScreen(
            state = reportState,
            onGenerate = onGenerateReport,
            onShare = onShareReport,
            onCopy = onCopyReport,
            onOpenInClaude = onOpenReportInClaude,
            onToggleAttachRaw = onToggleAttachRaw,
            modifier = modifier,
        )

        SubScreen.UPDATE -> UpdateScreen(
            state = updateState,
            onCheck = onCheckUpdate,
            onDownloadAndInstall = onDownloadAndInstall,
            onRetryAt = onRetryInstallAt,
            onCancel = onCancelUpdate,
            onExportRequested = onExportRequested,
            modifier = modifier,
        )

        SubScreen.DIAGNOSTICS -> DiagnosticsScreen(
            state = state,
            onForceCalibration = onForceCalibration,
            onRecalibrate = onRecalibrate,
            onOpenUsageSettings = onOpenUsageSettings,
            onPermissionsChanged = onRefresh,
            modifier = modifier,
        )
    }
}
