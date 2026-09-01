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
import kotlinx.coroutines.flow.first
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
                is UpdateCheck.Available -> notifyOnce(container, check)
                is UpdateCheck.Failed -> Log.i(TAG, "Checagem falhou: ${check.reason}")
                else -> Unit
            }
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Checagem de atualização sem rede", e)
            Result.retry()
        }
    }

    /**
     * Anuncia uma versão só uma vez. Sem essa marca d'água, a mesma atualização geraria uma
     * notificação a cada checagem até o usuário instalar — que é como um app ensina alguém a
     * silenciar o canal inteiro.
     */
    private suspend fun notifyOnce(
        container: dev.ederfmatos.batterystats.AppContainer,
        check: UpdateCheck.Available,
    ) {
        val settings = container.settingsRepository.settings.first()
        if (!settings.updateNotificationsEnabled) return
        if (settings.lastNotifiedVersionCode >= check.manifest.versionCode) return

        Log.i(TAG, "Anunciando a versão ${check.manifest.versionName}")
        container.updateNotifications.notifyAvailable(check.manifest)
        container.settingsRepository.setLastNotifiedVersionCode(check.manifest.versionCode)
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "checagem-atualizacao"

        /** Quatro checagens por dia. Cada uma é um GET de 1 KB, só em rede não medida. */
        const val CHECK_INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                CHECK_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            // UPDATE, não KEEP: se o intervalo mudar numa versão futura, o trabalho já agendado
            // precisa ser substituído em vez de manter o antigo para sempre.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
