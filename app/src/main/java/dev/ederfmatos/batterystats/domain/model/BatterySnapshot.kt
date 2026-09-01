package dev.ederfmatos.batterystats.domain.model

/** Status de carga da bateria, normalizado a partir de [android.os.BatteryManager]. */
enum class BatteryStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

/** Fonte de energia conectada. */
enum class PlugType { NONE, AC, USB, WIRELESS, DOCK, UNKNOWN }

/**
 * Uma leitura instantânea da bateria.
 *
 * [currentNowRaw] é o valor cru devolvido pelo aparelho. A unidade e o sinal NÃO são confiáveis:
 * a documentação diz microampères com negativo = descarregando, mas vários OEMs reportam em
 * miliampères e/ou invertem o sinal. A conversão para mA acontece na camada de domínio, via
 * [CurrentCalibration]. Nunca use este campo direto na UI.
 */
data class BatterySnapshot(
    val timestampMs: Long,
    val levelPct: Int,
    val chargeCounterUah: Long?,
    val currentNowRaw: Long?,
    val temperatureDeciC: Int?,
    val voltageMv: Int?,
    val status: BatteryStatus,
    val plugType: PlugType,
    val screenOn: Boolean,
    val foregroundPackage: String? = null,
) {
    val temperatureCelsius: Double? get() = temperatureDeciC?.let { it / 10.0 }
    val voltageVolts: Double? get() = voltageMv?.let { it / 1000.0 }
    val isCharging: Boolean get() = status == BatteryStatus.CHARGING || status == BatteryStatus.FULL
}
