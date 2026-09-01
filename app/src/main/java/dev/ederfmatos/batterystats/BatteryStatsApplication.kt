package dev.ederfmatos.batterystats

import android.app.Application

class BatteryStatsApplication : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * True quando esta versão já falhou nos dois primeiros arranques. Avaliado aqui, no onCreate,
     * porque a contagem precisa ser gravada antes de qualquer coisa que possa crashar.
     */
    var needsRecovery: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        needsRecovery = container.crashGuard.onAppStart(
            container.updateChecker.installedVersionCode()
        )
    }
}

val Application.appContainer: AppContainer
    get() = (this as BatteryStatsApplication).container

/** Atalho para Composables, que só têm um Context em mãos. */
fun appContainerFromContext(context: android.content.Context): AppContainer =
    (context.applicationContext as BatteryStatsApplication).container
