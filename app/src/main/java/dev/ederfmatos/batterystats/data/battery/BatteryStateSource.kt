package dev.ederfmatos.batterystats.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Emite leituras contínuas da bateria.
 *
 * Duas coisas disparam uma emissão: o broadcast ACTION_BATTERY_CHANGED (registrado em runtime,
 * porque desde a API 26 o registro no manifest é ignorado) e um tick periódico — CURRENT_NOW e
 * CHARGE_COUNTER mudam o tempo todo sem gerar broadcast nenhum.
 */
class BatteryStateSource(
    private val context: Context,
    private val reader: BatteryReader,
) {
    fun snapshots(intervalMs: Long = DEFAULT_INTERVAL_MS): Flow<BatterySnapshot> = callbackFlow {
        fun emit() {
            val snapshot = reader.read()
            if (snapshot == null) {
                Log.w(TAG, "Leitura da bateria indisponível neste tick")
                return
            }
            trySend(snapshot)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = emit()
        }

        // O sticky é entregue no próprio registerReceiver, então a primeira emissão já sai daqui.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val ticker = launch {
            while (isActive) {
                emit()
                delay(intervalMs)
            }
        }

        awaitClose {
            ticker.cancel()
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver de bateria já estava desregistrado", e)
            }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 2_000L
        private const val TAG = "BatteryStateSource"
    }
}
