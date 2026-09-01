package dev.ederfmatos.batterystats.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mantém o estado da tela em memória.
 *
 * SCREEN_ON/SCREEN_OFF só são entregues a receivers registrados em runtime — o manifest é ignorado
 * para essas ações. USER_PRESENT entra junto porque em alguns aparelhos o desbloqueio chega antes
 * do SCREEN_ON efetivo.
 *
 * Separar dreno de tela ligada e desligada é o corte mais informativo do app inteiro, então o
 * estado é lido do [PowerManager] no start em vez de assumir um valor inicial.
 */
class ScreenStateTracker(private val context: Context) {

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val screenOn = AtomicBoolean(powerManager?.isInteractive ?: false)
    private var receiver: BroadcastReceiver? = null

    /**
     * Chamado a cada transição da tela. O serviço usa isto para disparar uma amostra imediata:
     * ligar e apagar a tela são as duas maiores descontinuidades de consumo que existem, e esperar
     * o próximo tick de 60 s embaralha os dois regimes na mesma janela.
     */
    var onScreenChanged: ((Boolean) -> Unit)? = null

    val isScreenOn: Boolean get() = screenOn.get()

    fun start() {
        if (receiver != null) return
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> notifyChange(true)
                    Intent.ACTION_SCREEN_OFF -> notifyChange(false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(newReceiver, filter)
        receiver = newReceiver
        screenOn.set(powerManager?.isInteractive ?: screenOn.get())
    }

    private fun notifyChange(on: Boolean) {
        val changed = screenOn.getAndSet(on) != on
        if (changed) onScreenChanged?.invoke(on)
    }

    fun stop() {
        val current = receiver ?: return
        receiver = null
        onScreenChanged = null
        try {
            context.unregisterReceiver(current)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver de tela já estava desregistrado", e)
        }
    }

    private companion object {
        const val TAG = "ScreenStateTracker"
    }
}
