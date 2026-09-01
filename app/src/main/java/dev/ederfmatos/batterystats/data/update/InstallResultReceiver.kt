package dev.ederfmatos.batterystats.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import dev.ederfmatos.batterystats.domain.update.InstallOutcome
import dev.ederfmatos.batterystats.domain.update.InstallStatusMapper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Recebe o desfecho da sessão do `PackageInstaller`.
 *
 * `STATUS_PENDING_USER_ACTION` não é erro: é o sistema dizendo que a instalação silenciosa não foi
 * aceita e que existe um Intent pronto em `EXTRA_INTENT` para pedir o toque do usuário. É a
 * transição do degrau 1 para o degrau 2.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            InstallStatusMapper.STATUS_FAILURE,
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = userActionIntent(intent)
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context?.startActivity(confirmation)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.e(TAG, "Sistema não abriu o diálogo de confirmação", e)
                }
            } else {
                Log.w(TAG, "STATUS_PENDING_USER_ACTION sem EXTRA_INTENT")
            }
        }

        _results.tryEmit(InstallStatusMapper.map(status, message))
    }

    @Suppress("DEPRECATION")
    private fun userActionIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val ACTION_INSTALL_RESULT = "dev.ederfmatos.batterystats.INSTALL_RESULT"

        private val _results = MutableSharedFlow<InstallOutcome>(
            replay = 1,
            extraBufferCapacity = 4,
        )

        /** Desfechos das sessões, para a tela de Atualização acompanhar sem spinner infinito. */
        val results: SharedFlow<InstallOutcome> = _results.asSharedFlow()
    }
}
