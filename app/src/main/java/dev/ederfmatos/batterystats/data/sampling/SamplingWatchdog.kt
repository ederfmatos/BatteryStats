package dev.ederfmatos.batterystats.data.sampling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.ederfmatos.batterystats.data.receiver.SamplingWatchdogReceiver

/**
 * Alarme redundante que ressuscita o serviço de amostragem.
 *
 * Foreground service não é garantia de nada: num Galaxy com gerenciamento agressivo de bateria,
 * 59% do tempo de uma coleta real caiu em buracos porque o sistema derrubou o serviço. O alarme é
 * reagendado a cada amostra para daqui a dois intervalos — se a amostra seguinte acontecer, o
 * alarme é substituído e nunca dispara; se o serviço morreu, ele dispara e traz o serviço de volta.
 *
 * `setExactAndAllowWhileIdle` é o único que atravessa o Doze. Ele é limitado a um disparo a cada
 * ~9 minutos em Doze profundo, o que é aceitável: o objetivo não é amostrar pelo alarme, é
 * perceber que a amostragem parou.
 */
class SamplingWatchdog(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * A partir da API 31 alarmes exatos exigem permissão. Em 33+ a permissão é `USE_EXACT_ALARM`,
     * concedida automaticamente; em 31–32 é `SCHEDULE_EXACT_ALARM`, que o usuário pode revogar.
     */
    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(samplingIntervalMs: Long) {
        val manager = alarmManager ?: return
        val triggerAtMs = System.currentTimeMillis() + samplingIntervalMs * WATCHDOG_MULTIPLIER

        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent(),
                )
            } else {
                // Sem permissão de alarme exato o watchdog ainda vale a pena, só menos pontual.
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent(),
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sistema recusou agendar o watchdog", e)
        }
    }

    fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SamplingWatchdogReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "SamplingWatchdog"
        private const val REQUEST_CODE = 100

        /** Dois intervalos de folga: um atraso de um intervalo é jitter normal, não morte. */
        const val WATCHDOG_MULTIPLIER = 2
    }
}
