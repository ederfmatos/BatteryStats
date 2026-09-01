package dev.ederfmatos.batterystats.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.ederfmatos.batterystats.R
import dev.ederfmatos.batterystats.data.prefs.SamplingInterval

@Composable
fun SamplingInterval.label(): String = stringResource(
    when (this) {
        SamplingInterval.THIRTY_SECONDS -> R.string.interval_30s
        SamplingInterval.ONE_MINUTE -> R.string.interval_1m
        SamplingInterval.FIVE_MINUTES -> R.string.interval_5m
    }
)

/** Duração em milissegundos como "2h05". Usa horas mesmo quando são zero, para alinhar a lista. */
@Composable
fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000L
    return stringResource(R.string.duration_hm, totalMinutes / 60, totalMinutes % 60)
}

@Composable
fun formatHours(hours: Double): String {
    val whole = hours.toInt()
    val minutes = ((hours - whole) * 60).toInt()
    return stringResource(R.string.duration_hm, whole, minutes)
}
