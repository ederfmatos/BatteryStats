package dev.ederfmatos.batterystats.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.StatsPeriod
import dev.ederfmatos.batterystats.data.prefs.SamplingInterval
import dev.ederfmatos.batterystats.ui.apps.AppsScreen
import dev.ederfmatos.batterystats.ui.diagnostics.DiagnosticsScreen
import dev.ederfmatos.batterystats.ui.history.HistoryScreen
import dev.ederfmatos.batterystats.ui.home.HomeScreen
import dev.ederfmatos.batterystats.ui.settings.SettingsScreen

private enum class Tab(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    NOW(R.string.tab_now, R.drawable.ic_tab_now),
    APPS(R.string.tab_apps, R.drawable.ic_tab_apps),
    HISTORY(R.string.tab_history, R.drawable.ic_tab_history),
    DIAGNOSTICS(R.string.tab_diagnostics, R.drawable.ic_tab_diagnostics),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    state: MainUiState,
    onStartSampling: () -> Unit,
    onStopSampling: () -> Unit,
    onRefresh: () -> Unit,
    onPeriodChange: (StatsPeriod) -> Unit,
    onIntervalChange: (SamplingInterval) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onForceCalibration: (Int, Boolean) -> Unit,
    onRecalibrate: () -> Unit,
    onExport: (android.net.Uri, Boolean) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(Tab.NOW) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(selectedTab.titleRes)) })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            onRefresh()
                        },
                        icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                        label = { Text(stringResource(tab.titleRes)) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when (selectedTab) {
            Tab.NOW -> HomeScreen(
                state = state,
                onStartSampling = onStartSampling,
                onStopSampling = onStopSampling,
                onPermissionsChanged = onRefresh,
                modifier = contentModifier,
            )

            Tab.APPS -> AppsScreen(
                state = state,
                onPeriodChange = onPeriodChange,
                modifier = contentModifier,
            )

            Tab.HISTORY -> HistoryScreen(state = state, modifier = contentModifier)

            Tab.DIAGNOSTICS -> DiagnosticsScreen(
                state = state,
                onForceCalibration = onForceCalibration,
                onRecalibrate = onRecalibrate,
                onPermissionsChanged = onRefresh,
                modifier = contentModifier,
            )

            Tab.SETTINGS -> SettingsScreen(
                state = state,
                onIntervalChange = onIntervalChange,
                onStartOnBootChange = onStartOnBootChange,
                onExport = onExport,
                modifier = contentModifier,
            )
        }
    }
}
