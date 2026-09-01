package dev.ederfmatos.batterystats.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import dev.ederfmatos.batterystats.domain.model.BatterySnapshot
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.PlugType

/**
 * Implementação real do [BatteryReader].
 *
 * Combina duas fontes: o broadcast sticky ACTION_BATTERY_CHANGED (nível, status, temperatura,
 * voltagem, fonte) e o [BatteryManager] (CURRENT_NOW e CHARGE_COUNTER, que não vêm no Intent).
 * O sticky é lido com `registerReceiver(null, filter)`, que devolve o último Intent sem registrar
 * receiver nenhum — registrar no manifest não funciona desde a API 26.
 */
class AndroidBatteryReader(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : BatteryReader {

    private val batteryManager: BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun read(): BatterySnapshot? {
        val intent = readStickyBatteryIntent() ?: run {
            Log.w(TAG, "ACTION_BATTERY_CHANGED sticky indisponível; leitura descartada")
            return null
        }

        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val levelPct = when {
            rawLevel >= 0 && scale > 0 -> (rawLevel * 100f / scale).toInt()
            else -> batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }

        return BatterySnapshot(
            timestampMs = clock(),
            levelPct = levelPct,
            chargeCounterUah = longProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentNowRaw = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            temperatureDeciC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE && it > 0 },
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).toBatteryStatus(),
            plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0).toPlugType(),
            screenOn = isScreenOn(),
        )
    }

    private fun readStickyBatteryIntent(): Intent? = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Falha ao ler o sticky ACTION_BATTERY_CHANGED", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "Sem permissão para ler o sticky ACTION_BATTERY_CHANGED", e)
        null
    }

    /**
     * CURRENT_NOW é documentado como Int (microampères). Alguns aparelhos devolvem Int.MIN_VALUE ou
     * 0 quando a propriedade não existe — nesses casos vale mais reportar ausência do que um zero
     * que a UI mostraria como "sem consumo".
     */
    private fun intProperty(property: Int): Long? {
        val manager = batteryManager ?: return null
        val value = manager.getIntProperty(property)
        return if (value == Int.MIN_VALUE) null else value.toLong()
    }

    private fun longProperty(property: Int): Long? {
        val manager = batteryManager ?: return null
        val value = manager.getLongProperty(property)
        return if (value == Long.MIN_VALUE || value == 0L) null else value
    }

    private fun isScreenOn(): Boolean = powerManager?.isInteractive ?: false

    private fun Int.toBatteryStatus(): BatteryStatus = when (this) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        else -> BatteryStatus.UNKNOWN
    }

    private fun Int.toPlugType(): PlugType = when (this) {
        0 -> PlugType.NONE
        BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
        BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
        else -> PlugType.UNKNOWN
    }

    private companion object {
        const val TAG = "AndroidBatteryReader"
    }
}
