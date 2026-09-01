package dev.ederfmatos.batterystats.data.usage

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

/** Nome e ícone de um pacote, com cache — a consulta ao PackageManager é cara para uma lista. */
class AppLabelResolver(context: Context) {
    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val labelCache = mutableMapOf<String, String>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    fun label(packageName: String): String = labelCache.getOrPut(packageName) {
        try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Pacote $packageName não está mais instalado", e)
            packageName
        }
    }

    fun icon(packageName: String): Drawable? = iconCache.getOrPut(packageName) {
        try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Sem ícone para $packageName", e)
            null
        }
    }

    private companion object {
        const val TAG = "AppLabelResolver"
    }
}
