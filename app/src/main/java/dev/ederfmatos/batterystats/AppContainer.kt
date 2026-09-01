package dev.ederfmatos.batterystats

import android.content.Context
import dev.ederfmatos.batterystats.data.StatsRepository
import dev.ederfmatos.batterystats.data.battery.AndroidBatteryReader
import dev.ederfmatos.batterystats.data.battery.BatteryReader
import dev.ederfmatos.batterystats.data.battery.BatteryStateSource
import dev.ederfmatos.batterystats.data.battery.CurrentNowSampler
import dev.ederfmatos.batterystats.data.battery.DeviceStateReader
import dev.ederfmatos.batterystats.data.sampling.InteractiveTimeCounter
import dev.ederfmatos.batterystats.data.sampling.SamplingWatchdog
import dev.ederfmatos.batterystats.data.db.BatteryDatabase
import dev.ederfmatos.batterystats.data.prefs.SettingsRepository
import dev.ederfmatos.batterystats.data.receiver.ScreenStateTracker
import dev.ederfmatos.batterystats.data.usage.AppLabelResolver
import dev.ederfmatos.batterystats.data.usage.ForegroundAppResolver

/**
 * Injeção manual. O projeto é pequeno demais para justificar Hilt ou Koin — o container é criado
 * uma vez em [BatteryStatsApplication] e lido de lá por quem precisa.
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val androidBatteryReader: AndroidBatteryReader by lazy { AndroidBatteryReader(appContext) }

    val batteryReader: BatteryReader get() = androidBatteryReader

    val currentNowSampler: CurrentNowSampler by lazy { CurrentNowSampler(androidBatteryReader) }

    val deviceStateReader: DeviceStateReader by lazy { DeviceStateReader(appContext) }

    val interactiveTimeCounter: InteractiveTimeCounter by lazy { InteractiveTimeCounter() }

    val samplingWatchdog: SamplingWatchdog by lazy { SamplingWatchdog(appContext) }

    val batteryStateSource: BatteryStateSource by lazy {
        BatteryStateSource(appContext, batteryReader)
    }

    val database: BatteryDatabase by lazy { BatteryDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val screenStateTracker: ScreenStateTracker by lazy { ScreenStateTracker(appContext) }

    val foregroundAppResolver: ForegroundAppResolver by lazy { ForegroundAppResolver(appContext) }

    val appLabelResolver: AppLabelResolver by lazy { AppLabelResolver(appContext) }

    val statsRepository: StatsRepository by lazy {
        StatsRepository(
            dao = database.batteryDao(),
            settingsRepository = settingsRepository,
            foregroundAppResolver = foregroundAppResolver,
        )
    }
}
