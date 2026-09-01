package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.PlugType

const val MINUTE_MS = 60_000L
const val BASE_MS = 1_700_000_000_000L

/** Degrau medido em campo num Galaxy: 4076 µAh, ou 0,099% de uma bateria de 4130 mAh. */
const val REAL_STEP_UAH = 4076L

fun sample(
    atMs: Long,
    levelPct: Int = 80,
    chargeCounterUah: Long? = null,
    currentNowRaw: Long? = null,
    status: BatteryStatus = BatteryStatus.DISCHARGING,
    plugType: PlugType = PlugType.NONE,
    screenOn: Boolean = false,
): BatterySnapshot = BatterySnapshot(
    timestampMs = atMs,
    levelPct = levelPct,
    chargeCounterUah = chargeCounterUah,
    currentNowRaw = currentNowRaw,
    temperatureDeciC = 300,
    voltageMv = 3900,
    status = status,
    plugType = plugType,
    screenOn = screenOn,
)

/**
 * Série de descarga com contador **contínuo** — o caso ideal que os aparelhos reais não entregam.
 */
fun dischargeSeries(
    count: Int,
    intervalMs: Long = MINUTE_MS,
    startCounterUah: Long = 3_000_000L,
    drainMilliAmps: Double = 300.0,
    currentMultiplier: Long = 1000L,
    currentSign: Long = -1L,
    screenOn: Boolean = false,
    startLevelPct: Int = 90,
): List<BatterySnapshot> {
    val perStepUah = (drainMilliAmps * 1000.0 * (intervalMs / 3_600_000.0)).toLong()
    return (0 until count).map { index ->
        sample(
            atMs = BASE_MS + index * intervalMs,
            levelPct = (startLevelPct - index / 10).coerceAtLeast(0),
            chargeCounterUah = startCounterUah - index * perStepUah,
            currentNowRaw = currentSign * (drainMilliAmps.toLong() * currentMultiplier),
            screenOn = screenOn,
        )
    }
}

/**
 * Série de descarga com o contador **quantizado**: a carga real cai continuamente, mas o valor
 * reportado é sempre o múltiplo do degrau imediatamente abaixo. É o que o aparelho real faz.
 */
fun quantizedDischargeSeries(
    count: Int,
    intervalMs: Long = MINUTE_MS,
    startCounterUah: Long = 3_000_000L,
    drainMilliAmps: Double = 60.0,
    stepUah: Long = REAL_STEP_UAH,
    screenOn: Boolean = false,
    currentNowRaw: Long? = -60L,
    startLevelPct: Int = 90,
): List<BatterySnapshot> = (0 until count).map { index ->
    val elapsedHours = (index * intervalMs) / 3_600_000.0
    val trueConsumedUah = (drainMilliAmps * 1000.0 * elapsedHours).toLong()
    val trueCounter = startCounterUah - trueConsumedUah
    val quantized = (trueCounter / stepUah) * stepUah
    sample(
        atMs = BASE_MS + index * intervalMs,
        levelPct = (startLevelPct - index / 20).coerceAtLeast(0),
        chargeCounterUah = quantized,
        currentNowRaw = currentNowRaw,
        screenOn = screenOn,
    )
}
