package dev.ederfmatos.batterystats

import android.app.Application

class BatteryStatsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Application.appContainer: AppContainer
    get() = (this as BatteryStatsApplication).container

/** Atalho para Composables, que só têm um Context em mãos. */
fun appContainerFromContext(context: android.content.Context): AppContainer =
    (context.applicationContext as BatteryStatsApplication).container
