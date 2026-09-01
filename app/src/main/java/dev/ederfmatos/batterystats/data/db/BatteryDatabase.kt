package dev.ederfmatos.batterystats.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BatterySampleEntity::class, DailyAggregateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao

    companion object {
        fun build(context: Context): BatteryDatabase = Room.databaseBuilder(
            context.applicationContext,
            BatteryDatabase::class.java,
            "batterystats.db",
        ).build()
    }
}
