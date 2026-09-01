package dev.ederfmatos.batterystats.domain.report

import dev.ederfmatos.batterystats.domain.attribution.AppEnergyUsage
import dev.ederfmatos.batterystats.domain.drain.Coverage
import dev.ederfmatos.batterystats.domain.drain.HourlyDrain
import dev.ederfmatos.batterystats.domain.drain.RegimeStats
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import dev.ederfmatos.batterystats.domain.model.NetworkType

/** Identificação do aparelho. Vem de Build.*, coletada na camada de dados. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/** Médias do contexto que explica o dreno. */
data class ContextAverages(
    val screenBrightness: Int?,
    val autoBrightnessFraction: Double?,
    val networkShare: Map<NetworkType, Double>,
    val locationEnabledFraction: Double?,
    val temperatureMinCelsius: Double?,
    val temperatureMaxCelsius: Double?,
)

/** Tudo que o relatório precisa, já agregado. Nenhuma amostra crua entra aqui. */
data class BatteryReport(
    val device: DeviceInfo,
    val impliedCapacityMah: Double?,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val coverage: Coverage,
    val quantizationStepUah: Long,
    val samplingIntervalMs: Long,
    val calibration: CurrentCalibration,
    val screenOn: RegimeStats,
    val screenOff: RegimeStats,
    val totalMilliAmpHours: Double,
    val idleBaselineMilliAmps: Double?,
    val hourly: List<HourlyDrain>,
    val topApps: List<AppEnergyUsage>,
    val systemBucket: AppEnergyUsage?,
    val context: ContextAverages,
    val hasUsageAccess: Boolean,
    val windowCount: Int,
    val highConfidenceWindowCount: Int,
) {
    /** Incerteza imposta pela quantização no intervalo de amostragem configurado. */
    val quantizationUncertaintyMilliAmps: Double
        get() {
            val hours = samplingIntervalMs / 3_600_000.0
            return if (hours > 0) (quantizationStepUah / 1000.0) / hours else 0.0
        }

    val lowConfidenceFraction: Double
        get() = if (windowCount > 0) {
            (windowCount - highConfidenceWindowCount).toDouble() / windowCount
        } else {
            0.0
        }
}
