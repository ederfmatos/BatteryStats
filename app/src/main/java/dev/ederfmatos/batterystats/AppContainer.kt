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
import dev.ederfmatos.batterystats.data.health.HealthStatsReader
import dev.ederfmatos.batterystats.data.health.PowerProfileReader
import dev.ederfmatos.batterystats.data.prefs.SettingsRepository
import dev.ederfmatos.batterystats.data.receiver.ScreenStateTracker
import dev.ederfmatos.batterystats.data.usage.AppLabelResolver
import dev.ederfmatos.batterystats.data.usage.ForegroundAppResolver
import dev.ederfmatos.batterystats.data.update.ApkInstaller
import dev.ederfmatos.batterystats.data.update.ApkVerifier
import dev.ederfmatos.batterystats.data.update.CrashGuard
import dev.ederfmatos.batterystats.data.update.RemoteConfigRepository
import dev.ederfmatos.batterystats.data.update.UpdateChecker
import dev.ederfmatos.batterystats.data.update.UpdateNotifications
import dev.ederfmatos.batterystats.data.report.ReportBuilder
import dev.ederfmatos.batterystats.data.report.ReportSharer
import dev.ederfmatos.batterystats.data.update.UpdateRepository

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

    val healthStatsReader: HealthStatsReader by lazy { HealthStatsReader(appContext) }

    /** Lido uma vez: o power_profile.xml do aparelho não muda em tempo de execução. */
    val powerProfile: PowerProfileReader.PowerProfile by lazy { PowerProfileReader().read() }

    val updateChecker: UpdateChecker by lazy { UpdateChecker(appContext) }

    val remoteConfigRepository: RemoteConfigRepository by lazy {
        RemoteConfigRepository(appContext)
    }

    val crashGuard: CrashGuard by lazy { CrashGuard(appContext) }

    val updateNotifications: UpdateNotifications by lazy { UpdateNotifications(appContext) }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(
            context = appContext,
            dao = database.batteryDao(),
            checker = updateChecker,
            verifier = ApkVerifier(appContext),
            installer = ApkInstaller(appContext),
        )
    }

    val statsRepository: StatsRepository by lazy {
        StatsRepository(
            dao = database.batteryDao(),
            settingsRepository = settingsRepository,
            foregroundAppResolver = foregroundAppResolver,
        ).apply {
            declaredCapacityMah = powerProfile.batteryCapacityMah
            cycleCount = androidBatteryReader.cycleCount()
        }
    }

    val reportBuilder: ReportBuilder by lazy {
        ReportBuilder(statsRepository, settingsRepository, foregroundAppResolver)
    }

    val reportSharer: ReportSharer by lazy { ReportSharer(appContext) }
}
