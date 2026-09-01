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
        UpdateAttemptEntity::class,
    ],
    version = 4,
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

        /**
         * 2 → 3: leituras cruas de CURRENT_NOW e o contexto que explica o dreno (brilho, rede,
         * localização, economia de energia, Doze, tempo de tela no dia).
         *
         * Todas as colunas entram nulas nas linhas antigas — e isso é correto: não há como inventar
         * retroativamente o brilho de uma amostra de duas semanas atrás.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN currentNowSamples TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN screenBrightness INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN autoBrightness INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN networkType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN networkMetered INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN locationEnabled INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN powerSaveMode INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE battery_sample ADD COLUMN deviceIdleMode INTEGER DEFAULT NULL")
                db.execSQL(
                    "ALTER TABLE battery_sample ADD COLUMN interactiveMsToday INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** 3 → 4: histórico de tentativas de atualização. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS update_attempt (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        versionCode INTEGER NOT NULL,
                        step TEXT NOT NULL,
                        succeeded INTEGER NOT NULL,
                        failure TEXT,
                        detail TEXT,
                        timestampMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_update_attempt_timestampMs " +
                        "ON update_attempt (timestampMs)"
                )
            }
        }

        fun build(context: Context): BatteryDatabase = Room.databaseBuilder(
            context.applicationContext,
            BatteryDatabase::class.java,
            "batterystats.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
}
