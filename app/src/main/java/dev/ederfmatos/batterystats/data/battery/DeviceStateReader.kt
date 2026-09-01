package dev.ederfmatos.batterystats.data.battery

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dev.ederfmatos.batterystats.domain.model.NetworkType

/**
 * Leituras baratas que explicam o dreno sem custar permissão sensível nenhuma.
 *
 * Brilho, rede e localização são os maiores multiplicadores de consumo depois da própria tela;
 * sem eles, "829 mA com a tela ligada" é um número sem explicação.
 */
class DeviceStateReader(context: Context) {

    private val appContext = context.applicationContext

    private val powerManager: PowerManager? =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /** 0–255. Só leitura: o app nunca escreve em Settings.System e não pede WRITE_SETTINGS. */
    fun screenBrightness(): Int? = try {
        Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Settings.SettingNotFoundException) {
        Log.d(TAG, "Aparelho não expõe SCREEN_BRIGHTNESS", e)
        null
    }

    fun isAutoBrightness(): Boolean? = try {
        Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (e: Settings.SettingNotFoundException) {
        Log.d(TAG, "Aparelho não expõe SCREEN_BRIGHTNESS_MODE", e)
        null
    }

    fun networkType(): NetworkType {
        val capabilities = activeCapabilities() ?: return NetworkType.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }

    fun isNetworkMetered(): Boolean? {
        val capabilities = activeCapabilities() ?: return null
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun activeCapabilities(): NetworkCapabilities? {
        val manager = connectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)
    }

    /** `isLocationEnabled` só existe na API 28; antes disso é preciso perguntar por provedor. */
    fun isLocationEnabled(): Boolean? {
        val manager = locationManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun isPowerSaveMode(): Boolean? = powerManager?.isPowerSaveMode

    fun isDeviceIdleMode(): Boolean? = powerManager?.isDeviceIdleMode

    private companion object {
        const val TAG = "DeviceStateReader"
    }
}
