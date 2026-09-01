package dev.ederfmatos.batterystats.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dev.ederfmatos.batterystats.MainActivity
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.domain.update.UpdateManifest

/**
 * Avisa que existe versão nova.
 *
 * Canal separado do da amostragem, e com importância normal em vez de mínima: a notificação da
 * amostragem existe porque o Android exige uma, e o usuário não deve olhar para ela; esta aqui
 * pede uma ação e precisa aparecer.
 */
class UpdateNotifications(private val context: Context) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.update_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyAvailable(manifest: UpdateManifest) {
        if (!canPostNotifications()) {
            Log.i(TAG, "Sem permissão de notificação; versão nova não foi anunciada")
            return
        }
        ensureChannel()

        // Abre o app já na tela de Atualização, não na home.
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(R.string.update_notification_title, manifest.versionName)
            )
            .setContentText(context.getString(R.string.update_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(manifest.changelog.lineSequence().firstOrNull().orEmpty())
            )
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** POST_NOTIFICATIONS passou a ser exigida em runtime na API 33. */
    private fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    companion object {
        private const val TAG = "UpdateNotifications"
        const val CHANNEL_ID = "atualizacao"
        const val NOTIFICATION_ID = 2
        private const val REQUEST_CODE = 200
    }
}
