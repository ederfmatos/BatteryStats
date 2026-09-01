package dev.ederfmatos.batterystats.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.StatsRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Rede de segurança diária. Não amostra nada — amostragem é responsabilidade do foreground service,
 * e o WorkManager não tem granularidade para isso. Aqui só acontece o que pode rodar uma vez por
 * dia: consolidar o dia anterior, rodar a autocalibração e apagar amostras cruas vencidas.
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as android.app.Application).appContainer
        return try {
            val zone = ZoneId.systemDefault()
            val yesterday = LocalDate.now(zone).minusDays(1)
            val dayStartMs = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
            container.statsRepository.aggregateDay(dayStartMs)

            container.statsRepository.runAutoCalibration()

            val cutoffMs = System.currentTimeMillis() -
                StatsRepository.RAW_RETENTION_DAYS * StatsRepository.MILLIS_PER_DAY
            val deleted = container.database.batteryDao().deleteSamplesBefore(cutoffMs)
            Log.i(TAG, "Manutenção diária concluída; $deleted amostras cruas removidas")
            Result.success()
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Manutenção diária falhou no acesso ao banco", e)
            Result.retry()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Manutenção diária falhou", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "MaintenanceWorker"
        private const val WORK_NAME = "manutencao-diaria"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
