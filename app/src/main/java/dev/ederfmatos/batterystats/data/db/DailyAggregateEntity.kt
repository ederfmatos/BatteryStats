package dev.ederfmatos.batterystats.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Resumo de um dia. Sobrevive à limpeza das amostras cruas — o histórico longo do app vive aqui.
 * [dayEpochDay] é o dia local em [java.time.LocalDate.toEpochDay].
 */
@Entity(tableName = "daily_aggregate")
data class DailyAggregateEntity(
    @PrimaryKey val dayEpochDay: Long,
    val screenOnMilliAmps: Double,
    val screenOffMilliAmps: Double,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val screenOnPercentPerHour: Double,
    val screenOffPercentPerHour: Double,
    val totalMilliAmpHours: Double,
    val idleBaselineMilliAmps: Double?,
    val maxChargeCounterUah: Long?,
    val sampleCount: Int,
    val computedAtMs: Long,
)
