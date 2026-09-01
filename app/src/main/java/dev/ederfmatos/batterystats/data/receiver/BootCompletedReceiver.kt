package dev.ederfmatos.batterystats.data.receiver

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
 * Religa a amostragem depois do boot — mas só se o usuário tiver ligado o toggle. Um app de
 * medição que sobe sozinho sem ser pedido é exatamente o tipo de app que este aqui procura.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context?.applicationContext ?: return
        val application = appContext as? android.app.Application ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = application.appContainer.settingsRepository.settings.first()
                if (settings.startOnBoot) SamplingService.start(appContext)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Falha ao religar a amostragem após o boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
