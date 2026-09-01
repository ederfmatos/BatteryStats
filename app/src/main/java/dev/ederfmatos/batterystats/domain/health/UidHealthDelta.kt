package dev.ederfmatos.batterystats.domain.health

/**
 * O que um app fez entre dois retratos.
 *
 * Diferença de contadores monotônicos. Quando o valor novo é menor que o antigo — reinício do
 * aparelho, ou reset das estatísticas — o intervalo é marcado [countersReset] e o delta passa a
 * ser o valor absoluto atual, que é o melhor palpite disponível sem inventar nada.
 */
data class UidHealthDelta(
    val uid: Int,
    val fromMs: Long,
    val toMs: Long,
    val partialWakelocks: Map<String, TimerValue>,
    val jobs: Map<String, TimerValue>,
    val syncs: Map<String, TimerValue>,
    val gps: TimerValue,
    val camera: TimerValue,
    val flashlight: TimerValue,
    val audio: TimerValue,
    val video: TimerValue,
    val wifiScan: TimerValue,
    val bluetoothScan: TimerValue,
    val mobileRadioActive: TimerValue,
    val topMs: Long,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
    val backgroundMs: Long,
    val cachedMs: Long,
    val cpuTimeMs: Long,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val countersReset: Boolean,
) {
    val spanMs: Long get() = toMs - fromMs

    /** Tempo total de wakelock parcial no intervalo, somando todas as tags. */
    val totalPartialWakelockMs: Long get() = partialWakelocks.values.sumOf { it.timeMs }

    /** O wakelock mais longo, que é o que responde "quem acordou o aparelho". */
    val topWakelock: Pair<String, TimerValue>? get() =
        partialWakelocks.maxByOrNull { it.value.timeMs }?.toPair()

    /** Tempo em que o app esteve visível ao usuário. */
    val userVisibleMs: Long get() = topMs + foregroundMs

    /** Tempo em que o app rodou sem estar visível — o que interessa em janelas de tela apagada. */
    val invisibleActiveMs: Long get() = foregroundServiceMs + backgroundMs

    /** Nada relevante aconteceu; a linha não merece espaço na tela. */
    val isIdle: Boolean
        get() = totalPartialWakelockMs == 0L &&
            userVisibleMs == 0L &&
            invisibleActiveMs == 0L &&
            gps.isEmpty &&
            jobs.isEmpty()
}

object HealthStatsDiff {

    /**
     * Diferença entre dois retratos do mesmo UID. Devolve null quando os retratos não são do mesmo
     * app ou estão fora de ordem — comparar UIDs diferentes produziria números sem sentido.
     */
    fun diff(before: UidHealthSnapshot, after: UidHealthSnapshot): UidHealthDelta? {
        if (before.uid != after.uid) return null
        if (after.timestampMs <= before.timestampMs) return null

        val reset = detectReset(before, after)

        return UidHealthDelta(
            uid = after.uid,
            fromMs = before.timestampMs,
            toMs = after.timestampMs,
            partialWakelocks = diffTimers(before.partialWakelocks, after.partialWakelocks),
            jobs = diffTimers(before.jobs, after.jobs),
            syncs = diffTimers(before.syncs, after.syncs),
            gps = after.gps - before.gps,
            camera = after.camera - before.camera,
            flashlight = after.flashlight - before.flashlight,
            audio = after.audio - before.audio,
            video = after.video - before.video,
            wifiScan = after.wifiScan - before.wifiScan,
            bluetoothScan = after.bluetoothScan - before.bluetoothScan,
            mobileRadioActive = after.mobileRadioActive - before.mobileRadioActive,
            topMs = delta(before.topMs, after.topMs),
            foregroundMs = delta(before.foregroundMs, after.foregroundMs),
            foregroundServiceMs = delta(before.foregroundServiceMs, after.foregroundServiceMs),
            backgroundMs = delta(before.backgroundMs, after.backgroundMs),
            cachedMs = delta(before.cachedMs, after.cachedMs),
            cpuTimeMs = delta(before.userCpuTimeMs, after.userCpuTimeMs) +
                delta(before.systemCpuTimeMs, after.systemCpuTimeMs),
            mobileBytes = delta(before.mobileRxBytes, after.mobileRxBytes) +
                delta(before.mobileTxBytes, after.mobileTxBytes),
            wifiBytes = delta(before.wifiRxBytes, after.wifiRxBytes) +
                delta(before.wifiTxBytes, after.wifiTxBytes),
            countersReset = reset,
        )
    }

    /**
     * Os contadores do serviço de bateria só crescem. Qualquer um deles andar para trás significa
     * que a contagem foi reiniciada, e aí a série anterior não é comparável com a nova.
     */
    private fun detectReset(before: UidHealthSnapshot, after: UidHealthSnapshot): Boolean =
        after.topMs < before.topMs ||
            after.foregroundServiceMs < before.foregroundServiceMs ||
            after.backgroundMs < before.backgroundMs ||
            after.userCpuTimeMs < before.userCpuTimeMs ||
            after.gps.timeMs < before.gps.timeMs

    private fun delta(before: Long, after: Long): Long =
        if (after >= before) after - before else after

    private fun diffTimers(
        before: Map<String, TimerValue>,
        after: Map<String, TimerValue>,
    ): Map<String, TimerValue> = after
        .mapValues { (tag, value) -> value - (before[tag] ?: TimerValue.ZERO) }
        .filterValues { !it.isEmpty }
}
