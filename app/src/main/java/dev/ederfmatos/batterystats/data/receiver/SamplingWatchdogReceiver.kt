package dev.ederfmatos.batterystats.data.receiver

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.sampling.SamplingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Disparado pelo alarme do watchdog. Se o usuário quer a amostragem ligada e o serviço não está de
 * pé, o serviço volta — e o buraco entre a última amostra e agora vira um registro em
 * `measurement_gap`, feito pelo próprio serviço ao iniciar.
 */
class SamplingWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        val application = appContext as? Application ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = application.appContainer.settingsRepository.settings.first()
                if (!settings.samplingEnabled) return@launch
                if (SamplingService.isRunning.value) return@launch

                Log.i(TAG, "Watchdog encontrou o serviço morto; religando")
                SamplingService.start(appContext)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Watchdog não conseguiu religar a amostragem", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SamplingWatchdogReceiver"
    }
}
