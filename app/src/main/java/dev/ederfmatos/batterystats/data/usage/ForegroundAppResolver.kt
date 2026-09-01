package dev.ederfmatos.batterystats.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import dev.ederfmatos.batterystats.domain.attribution.ForegroundInterval
import dev.ederfmatos.batterystats.domain.model.ForegroundReason
import dev.ederfmatos.batterystats.domain.model.ForegroundResolution
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Reconstrói a linha do tempo de apps em primeiro plano a partir de [UsageEvents].
 *
 * Usa `queryEvents` com ACTIVITY_RESUMED/ACTIVITY_PAUSED em vez de `queryUsageStats`: o agregado
 * devolve totais por app num intervalo, o que é inútil aqui — a atribuição precisa saber *quando*
 * cada app esteve na frente.
 *
 * Ponto crítico: a consulta vai de [lastProcessedEventMs] até agora, **nunca** de "agora menos 60
 * segundos". Um app aberto há dez minutos não gera nenhum evento novo nesse meio tempo, e a janela
 * curta devolvia vazio — foi assim que 22% do consumo de uma coleta real ficou sem app atribuído.
 * Sem evento novo, o app em primeiro plano continua sendo o último conhecido.
 */
class ForegroundAppResolver(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private val lastProcessedEventMs = AtomicLong(0L)
    private val lastKnownForegroundPackage = AtomicReference<String?>(null)

    fun hasAccess(): Boolean = UsageAccess.isGranted(context)

    /** Restaura o estado persistido entre execuções do serviço. */
    fun restoreState(lastEventMs: Long, lastPackage: String?) {
        lastProcessedEventMs.set(lastEventMs)
        lastKnownForegroundPackage.set(lastPackage)
    }

    fun lastProcessedEventMs(): Long = lastProcessedEventMs.get()

    fun lastKnownPackage(): String? = lastKnownForegroundPackage.get()

    /** A tela apagou: não há mais app em primeiro plano e o último conhecido deixa de valer. */
    fun onScreenOff() {
        lastKnownForegroundPackage.set(null)
    }

    /**
     * Resolve o app em primeiro plano agora.
     *
     * [afterGap] força o motivo [ForegroundReason.GAP]: depois de um buraco de amostragem não dá
     * para afirmar que o app de antes continuou na frente o tempo todo.
     */
    fun resolveCurrent(
        nowMs: Long,
        screenOn: Boolean,
        afterGap: Boolean = false,
    ): ForegroundResolution {
        if (!hasAccess()) return ForegroundResolution.absent(ForegroundReason.NO_PERMISSION)
        if (!screenOn) {
            lastKnownForegroundPackage.set(null)
            return ForegroundResolution.absent(ForegroundReason.SCREEN_OFF)
        }

        consumeEventsUpTo(nowMs)

        if (afterGap) {
            lastKnownForegroundPackage.set(null)
            return ForegroundResolution.absent(ForegroundReason.GAP)
        }

        val current = lastKnownForegroundPackage.get()
        return if (current != null) {
            ForegroundResolution.of(current)
        } else {
            ForegroundResolution.absent(ForegroundReason.GAP)
        }
    }

    /**
     * Lê todos os eventos desde a última leitura e atualiza o app corrente. Um PAUSED de um pacote
     * que não é o corrente não apaga o corrente — trocas rápidas emitem PAUSED do app antigo depois
     * do RESUMED do novo em alguns aparelhos.
     */
    private fun consumeEventsUpTo(nowMs: Long) {
        val from = lastProcessedEventMs.get().takeIf { it > 0L } ?: (nowMs - INITIAL_LOOKBACK_MS)
        if (nowMs <= from) return

        val events = queryEvents(from, nowMs) ?: return
        val event = UsageEvents.Event()
        var latestEventMs = from

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            latestEventMs = maxOf(latestEventMs, event.timeStamp)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    lastKnownForegroundPackage.set(event.packageName)

                UsageEvents.Event.ACTIVITY_PAUSED ->
                    lastKnownForegroundPackage.compareAndSet(event.packageName, null)
            }
        }

        lastProcessedEventMs.set(maxOf(latestEventMs, nowMs))
    }

    /**
     * Intervalos de primeiro plano dentro de [fromMs, toMs).
     *
     * Um RESUMED sem PAUSED correspondente é fechado em [toMs] — é o caso do app aberto neste
     * instante. Um PAUSED sem RESUMED anterior (a janela começou no meio de uma sessão) é ancorado
     * em [fromMs].
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

        /** Na primeira amostra não há marca d'água; olha uma hora para trás para achar o RESUMED. */
        const val INITIAL_LOOKBACK_MS = 3_600_000L
    }
}
