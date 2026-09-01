package dev.ederfmatos.batterystats.domain

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.PlugType

const val MINUTE_MS = 60_000L
const val BASE_MS = 1_700_000_000_000L

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
 * Série sintética de descarga: o contador cai [drainMilliAmps] mA de forma constante e CURRENT_NOW
 * acompanha, na unidade e no sinal pedidos.
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
