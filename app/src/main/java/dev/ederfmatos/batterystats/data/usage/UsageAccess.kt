package dev.ederfmatos.batterystats.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * PACKAGE_USAGE_STATS não é uma permissão normal: o usuário concede numa tela de Configurações,
 * app por app. Não dá para pedir com requestPermissions.
 */
object UsageAccess {

    // unsafeCheckOpNoThrow está marcado como deprecated, mas não há substituto público para
    // consultar o estado de GET_USAGE_STATS sem disparar a checagem de acesso.
    @Suppress("DEPRECATION")
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Abre a tela onde o usuário concede o acesso. Não existe caminho direto para o app. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openSettings(context: Context) {
        try {
            context.startActivity(settingsIntent())
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e("UsageAccess", "Aparelho sem tela de acesso ao uso", e)
        }
    }
}
