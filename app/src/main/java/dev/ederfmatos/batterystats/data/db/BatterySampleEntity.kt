package dev.ederfmatos.batterystats.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.ForegroundReason
import dev.ederfmatos.batterystats.domain.model.NetworkType
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

    // Campos da migração 2 → 3. Ver DeviceStateReader para de onde cada um vem.
    /** Leituras cruas de CURRENT_NOW separadas por vírgula; [currentNowRaw] é a mediana delas. */
    @ColumnInfo(name = "currentNowSamples") val currentNowSamples: String? = null,
    @ColumnInfo(name = "screenBrightness") val screenBrightness: Int? = null,
    @ColumnInfo(name = "autoBrightness") val autoBrightness: Boolean? = null,
    @ColumnInfo(name = "networkType") val networkType: String? = null,
    @ColumnInfo(name = "networkMetered") val networkMetered: Boolean? = null,
    @ColumnInfo(name = "locationEnabled") val locationEnabled: Boolean? = null,
    @ColumnInfo(name = "powerSaveMode") val powerSaveMode: Boolean? = null,
    @ColumnInfo(name = "deviceIdleMode") val deviceIdleMode: Boolean? = null,
    @ColumnInfo(name = "interactiveMsToday") val interactiveMsToday: Long = 0L,
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
    currentNowSamples = currentNowSamples.takeIf { it.isNotEmpty() }?.joinToString(","),
    screenBrightness = screenBrightness,
    autoBrightness = autoBrightness,
    networkType = networkType.name,
    networkMetered = networkMetered,
    locationEnabled = locationEnabled,
    powerSaveMode = powerSaveMode,
    deviceIdleMode = deviceIdleMode,
    interactiveMsToday = interactiveMsToday,
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
    currentNowSamples = currentNowSamples
        ?.split(',')
        ?.mapNotNull { it.trim().toLongOrNull() }
        .orEmpty(),
    screenBrightness = screenBrightness,
    autoBrightness = autoBrightness,
    networkType = networkType
        ?.let { name -> runCatching { NetworkType.valueOf(name) }.getOrNull() }
        ?: NetworkType.UNKNOWN,
    networkMetered = networkMetered,
    locationEnabled = locationEnabled,
    powerSaveMode = powerSaveMode,
    deviceIdleMode = deviceIdleMode,
    interactiveMsToday = interactiveMsToday,
)
