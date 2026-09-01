package dev.ederfmatos.batterystats.data.work

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.ederfmatos.batterystats.appContainer
import dev.ederfmatos.batterystats.data.update.UpdateCheck
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checagem diária de versão e refresh da config remota.
 *
 * Constraints deliberadas: **só em rede não medida e com bateria acima do mínimo**. Um app cuja
 * função é medir consumo não pode ser ele próprio a causa de um pico de consumo ou de uma conta de
 * dados. O download do APK em si só acontece quando o usuário decide na tela de Atualização.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as Application).appContainer
        return try {
            container.remoteConfigRepository.refresh()
            when (val check = container.updateRepository.check()) {
                is UpdateCheck.Available ->
                    Log.i(TAG, "Versão ${check.manifest.versionName} disponível")

                is UpdateCheck.Failed -> Log.i(TAG, "Checagem falhou: ${check.reason}")
                else -> Unit
            }
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Checagem de atualização sem rede", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "checagem-atualizacao"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
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
