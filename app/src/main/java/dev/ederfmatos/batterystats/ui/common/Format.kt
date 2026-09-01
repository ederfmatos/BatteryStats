package dev.ederfmatos.batterystats.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.domain.model.BatteryStatus
import dev.ederfmatos.batterystats.domain.model.PlugType
import java.util.Locale

@Composable
fun BatteryStatus.label(): String = stringResource(
    when (this) {
        BatteryStatus.CHARGING -> R.string.status_charging
        BatteryStatus.DISCHARGING -> R.string.status_discharging
        BatteryStatus.FULL -> R.string.status_full
        BatteryStatus.NOT_CHARGING -> R.string.status_not_charging
        BatteryStatus.UNKNOWN -> R.string.status_unknown
    }
)

@Composable
fun PlugType.label(): String = stringResource(
    when (this) {
        PlugType.NONE -> R.string.plug_none
        PlugType.AC -> R.string.plug_ac
        PlugType.USB -> R.string.plug_usb
        PlugType.WIRELESS -> R.string.plug_wireless
        PlugType.DOCK -> R.string.plug_dock
        PlugType.UNKNOWN -> R.string.plug_unknown
    }
)

fun Long.groupedDigits(): String = String.format(Locale.getDefault(), "%,d", this)
