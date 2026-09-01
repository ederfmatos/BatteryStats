package dev.ederfmatos.batterystats.data.sampling

import java.util.concurrent.atomic.AtomicLong

/**
 * Acumula quanto tempo a tela ficou ligada no dia local corrente.
 *
 * Contado em memória a partir das transições de tela, não derivado das amostras: entre duas
 * amostras de 60 s a tela pode ter ligado e desligado várias vezes, e o campo `screenOn` da amostra
 * só registra o estado no instante da leitura.
 */
class InteractiveTimeCounter(private val clock: () -> Long = System::currentTimeMillis) {

    private val accumulatedMs = AtomicLong(0L)
    private val currentDay = AtomicLong(-1L)
    private val screenOnSinceMs = AtomicLong(0L)

    fun onScreenOn() {
        rolloverIfNeeded()
        screenOnSinceMs.compareAndSet(0L, clock())
    }

    fun onScreenOff() {
        rolloverIfNeeded()
        val since = screenOnSinceMs.getAndSet(0L)
        if (since > 0L) accumulatedMs.addAndGet((clock() - since).coerceAtLeast(0L))
    }

    /** Total do dia até agora, incluindo o trecho ainda em curso se a tela estiver ligada. */
    fun totalTodayMs(): Long {
        rolloverIfNeeded()
        val since = screenOnSinceMs.get()
        val ongoing = if (since > 0L) (clock() - since).coerceAtLeast(0L) else 0L
        return accumulatedMs.get() + ongoing
    }

    /** Vira o contador à meia-noite local, sem depender de nenhum agendamento externo. */
    private fun rolloverIfNeeded() {
        val nowMs = clock()
        val day = localDayOf(nowMs)
        val previous = currentDay.getAndSet(day)
        if (previous != day && previous != -1L) {
            accumulatedMs.set(0L)
            // Se a tela está ligada na virada, o trecho recomeça agora.
            if (screenOnSinceMs.get() > 0L) screenOnSinceMs.set(nowMs)
        }
    }

    private fun localDayOf(timestampMs: Long): Long = java.time.Instant.ofEpochMilli(timestampMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()
}
