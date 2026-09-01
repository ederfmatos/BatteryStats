package dev.ederfmatos.batterystats.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.ForegroundReason
import dev.ederfmatos.batterystats.domain.model.PlugType

@Entity(
    tableName = "battery_sample",
    indices = [Index(value = ["timestampMs"])],
)
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMs: Long,
    val levelPct: Int,
    val chargeCounterUah: Long?,
    val currentNowRaw: Long?,
    val temperatureDeciC: Int?,
    val voltageMv: Int?,
    val status: String,
    val plugType: String,
    val screenOn: Boolean,
    @ColumnInfo(name = "foregroundPackage") val foregroundPackage: String?,
    /** Nome de [ForegroundReason], ou null quando há um pacote resolvido. Ver migração 1 → 2. */
    @ColumnInfo(name = "foregroundReason") val foregroundReason: String? = null,
)

fun BatterySnapshot.toEntity(): BatterySampleEntity = BatterySampleEntity(
    timestampMs = timestampMs,
    levelPct = levelPct,
    chargeCounterUah = chargeCounterUah,
    currentNowRaw = currentNowRaw,
    temperatureDeciC = temperatureDeciC,
    voltageMv = voltageMv,
    status = status.name,
    plugType = plugType.name,
    screenOn = screenOn,
    foregroundPackage = foregroundPackage,
    foregroundReason = foregroundReason?.name,
)

fun BatterySampleEntity.toSnapshot(): BatterySnapshot = BatterySnapshot(
    timestampMs = timestampMs,
    levelPct = levelPct,
    chargeCounterUah = chargeCounterUah,
    currentNowRaw = currentNowRaw,
    temperatureDeciC = temperatureDeciC,
    voltageMv = voltageMv,
    status = runCatching { BatteryStatus.valueOf(status) }.getOrDefault(BatteryStatus.UNKNOWN),
    plugType = runCatching { PlugType.valueOf(plugType) }.getOrDefault(PlugType.UNKNOWN),
    screenOn = screenOn,
    foregroundPackage = foregroundPackage,
    foregroundReason = foregroundReason
        ?.let { name -> runCatching { ForegroundReason.valueOf(name) }.getOrNull() },
)
