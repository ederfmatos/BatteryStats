package dev.ederfmatos.batterystats.domain.health

/**
 * Um retrato dos contadores de um app num instante, já traduzido para tipos próprios.
 *
 * Tudo aqui é **tempo e contagem medidos** pelo serviço de bateria do sistema, não modelo. É a
 * diferença central entre isto e a atribuição por correlação: "47 minutos de wakelock parcial" é
 * um fato verificável; "180 mAh" seria uma multiplicação por constante de catálogo.
 *
 * Kotlin puro de propósito — a matemática de diferença entre dois retratos precisa ser testável
 * sem aparelho.
 */
data class UidHealthSnapshot(
    val uid: Int,
    val timestampMs: Long,
    /** Wakelocks parciais por tag: quanto tempo o app segurou o aparelho acordado. */
    val partialWakelocks: Map<String, TimerValue> = emptyMap(),
    val jobs: Map<String, TimerValue> = emptyMap(),
    val syncs: Map<String, TimerValue> = emptyMap(),
    val gps: TimerValue = TimerValue.ZERO,
    val camera: TimerValue = TimerValue.ZERO,
    val flashlight: TimerValue = TimerValue.ZERO,
    val audio: TimerValue = TimerValue.ZERO,
    val video: TimerValue = TimerValue.ZERO,
    val wifiScan: TimerValue = TimerValue.ZERO,
    val bluetoothScan: TimerValue = TimerValue.ZERO,
    val mobileRadioActive: TimerValue = TimerValue.ZERO,
    /** Tempo em cada estado de processo, segundo o próprio serviço de bateria. */
    val topMs: Long = 0L,
    val foregroundMs: Long = 0L,
    val foregroundServiceMs: Long = 0L,
    val backgroundMs: Long = 0L,
    val cachedMs: Long = 0L,
    val userCpuTimeMs: Long = 0L,
    val systemCpuTimeMs: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
)

/** Um temporizador do HealthStats: quantas vezes aconteceu e por quanto tempo no total. */
data class TimerValue(val count: Int, val timeMs: Long) {
    operator fun minus(other: TimerValue): TimerValue = TimerValue(
        // Contadores só crescem; um valor menor significa que o sistema reiniciou a contagem
        // (reboot, ou reset das estatísticas de bateria). Nesse caso o delta é o valor atual.
        count = if (count >= other.count) count - other.count else count,
        timeMs = if (timeMs >= other.timeMs) timeMs - other.timeMs else timeMs,
    )

    val isEmpty: Boolean get() = count == 0 && timeMs == 0L

    companion object {
        val ZERO = TimerValue(0, 0L)
    }
}
