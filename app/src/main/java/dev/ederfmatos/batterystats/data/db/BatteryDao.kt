package dev.ederfmatos.batterystats.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    @Insert
    suspend fun insert(sample: BatterySampleEntity): Long

    @Query("SELECT * FROM battery_sample WHERE timestampMs >= :fromMs ORDER BY timestampMs ASC")
    suspend fun samplesSince(fromMs: Long): List<BatterySampleEntity>

    @Query(
        "SELECT * FROM battery_sample WHERE timestampMs BETWEEN :fromMs AND :toMs " +
            "ORDER BY timestampMs ASC"
    )
    suspend fun samplesBetween(fromMs: Long, toMs: Long): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_sample ORDER BY timestampMs ASC")
    suspend fun allSamples(): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_sample ORDER BY timestampMs DESC LIMIT 1")
    fun latestSample(): Flow<BatterySampleEntity?>

    @Query("SELECT COUNT(*) FROM battery_sample")
    fun sampleCount(): Flow<Int>

    @Query("SELECT MAX(chargeCounterUah) FROM battery_sample WHERE levelPct >= :minLevelPct")
    suspend fun maxChargeCounterAtHighLevel(minLevelPct: Int): Long?

    @Query("DELETE FROM battery_sample WHERE timestampMs < :beforeMs")
    suspend fun deleteSamplesBefore(beforeMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyAggregate(aggregate: DailyAggregateEntity)

    @Query("SELECT * FROM daily_aggregate ORDER BY dayEpochDay ASC")
    fun dailyAggregates(): Flow<List<DailyAggregateEntity>>

    @Query("SELECT * FROM daily_aggregate ORDER BY dayEpochDay ASC")
    suspend fun dailyAggregatesOnce(): List<DailyAggregateEntity>
}
