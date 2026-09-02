package dev.ederfmatos.batterystats.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.PeriodStats
import dev.ederfmatos.batterystats.data.StatsPeriod
import dev.ederfmatos.batterystats.data.prefs.AppSettings
import dev.ederfmatos.batterystats.data.prefs.SamplingInterval
import dev.ederfmatos.batterystats.data.sampling.SamplingService
import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.domain.drain.WakelockSuspicionDetector
import dev.ederfmatos.batterystats.domain.health.AbsoluteHealth
import dev.ederfmatos.batterystats.domain.health.BatteryHealthEstimate
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val snapshot: BatterySnapshot? = null,
    val settings: AppSettings = AppSettings(),
    val samplingRunning: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val periodStats: PeriodStats? = null,
    val wakelockSuspicion: Boolean = false,
    val appRanking: List<AppEnergyUsage> = emptyList(),
    val appPeriod: StatsPeriod = StatsPeriod.TODAY,
    val history: List<BatterySnapshot> = emptyList(),
    val sampleCount: Int = 0,
    /** Instante da amostra mais antiga ainda no banco. 0 quando não há nenhuma. */
    val firstSampleMs: Long = 0L,
    val health: BatteryHealthEstimate? = null,
    val absoluteHealth: AbsoluteHealth? = null,
    val serviceKillCount: Int = 0,
    val canScheduleExactAlarms: Boolean = true,
    val hasBatteryStatsPermission: Boolean = false,
    val batteryStatsGrantCommand: String = "",
    val loading: Boolean = true,
) {
    val currentMilliAmps: Double?
        get() = settings.calibration.toMilliAmps(snapshot?.currentNowRaw)
}

/**
 * ViewModel único do app. Com cinco telas pequenas que compartilham quase todo o estado, quebrar em
 * cinco ViewModels só duplicaria o carregamento dos mesmos dados.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val wakelockDetector = WakelockSuspicionDetector()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.batteryStateSource.snapshots().collect { snapshot ->
                _uiState.value = _uiState.value.copy(snapshot = snapshot)
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            container.database.batteryDao().sampleCount().collect { count ->
                _uiState.value = _uiState.value.copy(sampleCount = count)
            }
        }
        viewModelScope.launch {
            SamplingService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(samplingRunning = running)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val period = _uiState.value.appPeriod
            val stats = runCatching { container.statsRepository.periodStats(StatsPeriod.TODAY) }
                .onFailure { Log.e(TAG, "Falha ao calcular estatísticas do dia", it) }
                .getOrNull()
            val ranking = runCatching { container.statsRepository.appRanking(period) }
                .onFailure { Log.e(TAG, "Falha ao montar o ranking de apps", it) }
                .getOrDefault(emptyList())
            val history = runCatching {
                container.statsRepository.snapshotsSince(
                    System.currentTimeMillis() - HISTORY_WINDOW_MS
                )
            }
                .onFailure { Log.e(TAG, "Falha ao carregar o histórico", it) }
                .getOrDefault(emptyList())
            val health = runCatching { container.statsRepository.healthEstimate() }
                .onFailure { Log.e(TAG, "Falha ao estimar a saúde da bateria", it) }
                .getOrNull()
            val absolute = runCatching { container.statsRepository.absoluteHealth() }
                .onFailure { Log.e(TAG, "Falha ao medir a capacidade pelas sessões de carga", it) }
                .getOrNull()
            val serviceKills = runCatching { container.statsRepository.serviceKillCount() }
                .onFailure { Log.e(TAG, "Falha ao contar mortes do serviço", it) }
                .getOrDefault(0)

            _uiState.value = _uiState.value.copy(
                firstSampleMs = history.firstOrNull()?.timestampMs ?: 0L,
                periodStats = stats,
                wakelockSuspicion = stats?.stats?.let { wakelockDetector.isSuspicious(it) } ?: false,
                appRanking = ranking,
                history = history,
                health = health,
                absoluteHealth = absolute,
                serviceKillCount = serviceKills,
                canScheduleExactAlarms = container.samplingWatchdog.canScheduleExact(),
                hasBatteryStatsPermission = container.healthStatsReader.hasBatteryStatsPermission(),
                batteryStatsGrantCommand = container.healthStatsReader.grantCommand(),
                hasUsageAccess = container.foregroundAppResolver.hasAccess(),
                ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
                loading = false,
            )
        }
    }

    fun setAppPeriod(period: StatsPeriod) {
        _uiState.value = _uiState.value.copy(appPeriod = period)
        refresh()
    }

    fun startSampling() = SamplingService.start(getApplication<Application>())

    fun stopSampling() = SamplingService.stop(getApplication<Application>())

    fun setSamplingInterval(interval: SamplingInterval) {
        viewModelScope.launch { container.settingsRepository.setSamplingInterval(interval) }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setStartOnBoot(enabled) }
    }

    fun setThemeMode(mode: dev.ederfmatos.batterystats.data.prefs.ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDynamicColorEnabled(enabled) }
    }

    fun setUpdateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setUpdateNotificationsEnabled(enabled)
        }
    }

    fun openUsageSettings() {
        dev.ederfmatos.batterystats.data.usage.UsageAccess.openSettings(getApplication())
    }

    fun forceCalibration(divisor: Int, inverted: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setCalibration(
                CurrentCalibration(
                    divisor = divisor,
                    inverted = inverted,
                    source = CurrentCalibration.Source.MANUAL,
                )
            )
            refresh()
        }
    }

    fun recalibrate() {
        viewModelScope.launch {
            container.settingsRepository.setCalibration(CurrentCalibration.DEFAULT)
            container.statsRepository.runAutoCalibration()
            refresh()
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val context: Context = getApplication<Application>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Só faz sentido pedir POST_NOTIFICATIONS a partir da API 33; antes disso é implícita. */
    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                "Factory não sabe criar ${modelClass.name}"
            }
            return MainViewModel(application) as T
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val HISTORY_WINDOW_MS = 7 * 86_400_000L
    }
}
