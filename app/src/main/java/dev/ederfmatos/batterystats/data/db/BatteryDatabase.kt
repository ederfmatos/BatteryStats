package dev.ederfmatos.batterystats.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BatterySampleEntity::class,
        DailyAggregateEntity::class,
        MeasurementGapEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao

    companion object {
        /**
         * 1 → 2: motivo da ausência de app em primeiro plano e a tabela de buracos de medição.
         *
         * Escrita à mão de propósito. As amostras já coletadas no aparelho são o único registro
         * que existe do comportamento real da bateria — recriar o banco perderia tudo.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN foregroundReason TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS measurement_gap (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        reason TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_measurement_gap_startMs " +
                        "ON measurement_gap (startMs)"
                )
            }
        }

        fun build(context: Context): BatteryDatabase = Room.databaseBuilder(
            context.applicationContext,
            BatteryDatabase::class.java,
            "batterystats.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
