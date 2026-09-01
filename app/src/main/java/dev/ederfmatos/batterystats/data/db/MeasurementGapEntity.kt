package dev.ederfmatos.batterystats.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ederfmatos.batterystats.domain.drain.GapReason
import dev.ederfmatos.batterystats.domain.drain.MeasurementGap

/**
 * Um período em que o app não estava medindo. Gravado explicitamente porque um agregado calculado
 * sobre 41% do tempo, sem dizer que são 41%, mente por omissão.
 */
@Entity(
    tableName = "measurement_gap",
    indices = [Index(value = ["startMs"])],
)
data class MeasurementGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMs: Long,
    val endMs: Long,
    val reason: String,
)

fun MeasurementGapEntity.toGap(): MeasurementGap = MeasurementGap(
    startMs = startMs,
    endMs = endMs,
    reason = runCatching { GapReason.valueOf(reason) }.getOrDefault(GapReason.UNKNOWN),
)

fun MeasurementGap.toEntity(): MeasurementGapEntity = MeasurementGapEntity(
    startMs = startMs,
    endMs = endMs,
    reason = reason.name,
)
