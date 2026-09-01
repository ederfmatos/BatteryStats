package dev.ederfmatos.batterystats.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import dev.ederfmatos.batterystats.domain.attribution.ForegroundInterval

/**
 * Reconstrói a linha do tempo de apps em primeiro plano a partir de [UsageEvents].
 *
 * Usa `queryEvents` com ACTIVITY_RESUMED/ACTIVITY_PAUSED em vez de `queryUsageStats`: o agregado
 * devolve totais por app num intervalo, o que é inútil aqui — a atribuição precisa saber *quando*
 * cada app esteve na frente, para cruzar com as janelas de dreno.
 *
 * Sem a permissão concedida, tudo devolve vazio e o app segue em modo degradado.
 */
class ForegroundAppResolver(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun hasAccess(): Boolean = UsageAccess.isGranted(context)

    /** O pacote em primeiro plano agora, ou null sem permissão / sem eventos recentes. */
    fun currentPackage(): String? {
        if (!hasAccess()) return null
        val now = System.currentTimeMillis()
        val events = queryEvents(now - RECENT_LOOKBACK_MS, now) ?: return null

        var current: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED ->
                    if (current == event.packageName) current = null
            }
        }
        return current
    }

    /**
     * Intervalos de primeiro plano dentro de [fromMs, toMs).
     *
     * Um RESUMED sem PAUSED correspondente é fechado em [toMs] — é o caso do app que está aberto
     * neste instante. Um PAUSED sem RESUMED anterior (a janela começou no meio de uma sessão) é
     * ancorado em [fromMs].
     */
    fun intervals(fromMs: Long, toMs: Long): List<ForegroundInterval> {
        if (!hasAccess() || toMs <= fromMs) return emptyList()
        val events = queryEvents(fromMs, toMs) ?: return emptyList()

        val intervals = mutableListOf<ForegroundInterval>()
        val openedAt = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(fromMs, toMs)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> openedAt[event.packageName] = timestamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = openedAt.remove(event.packageName) ?: fromMs
                    if (timestamp > start) {
                        intervals += ForegroundInterval(event.packageName, start, timestamp)
                    }
                }
            }
        }

        for ((packageName, start) in openedAt) {
            if (toMs > start) intervals += ForegroundInterval(packageName, start, toMs)
        }

        return intervals.sortedBy { it.startMs }
    }

    private fun queryEvents(fromMs: Long, toMs: Long): UsageEvents? {
        val manager = usageStatsManager ?: return null
        return try {
            manager.queryEvents(fromMs, toMs)
        } catch (e: SecurityException) {
            Log.w(TAG, "Acesso ao uso revogado durante a consulta", e)
            null
        }
    }

    private companion object {
        const val TAG = "ForegroundAppResolver"

        /** Janela curta o bastante para ser barata e longa o bastante para achar o último RESUMED. */
        const val RECENT_LOOKBACK_MS = 60_000L
    }
}
