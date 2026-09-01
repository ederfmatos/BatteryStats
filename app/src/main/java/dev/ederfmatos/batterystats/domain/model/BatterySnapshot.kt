package dev.ederfmatos.batterystats.domain.model

/** Status de carga da bateria, normalizado a partir de [android.os.BatteryManager]. */
enum class BatteryStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

/** Fonte de energia conectada. */
enum class PlugType { NONE, AC, USB, WIRELESS, DOCK, UNKNOWN }

/** Transporte de rede ativo. Um dos maiores multiplicadores de dreno depois da tela. */
enum class NetworkType { WIFI, CELLULAR, OTHER, NONE, UNKNOWN }

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
    /** Por que [foregroundPackage] é null. Null aqui significa que há um pacote resolvido. */
    val foregroundReason: ForegroundReason? = null,

    /**
     * As leituras cruas de CURRENT_NOW que geraram [currentNowRaw], que é a mediana delas.
     * Guardadas para auditar a dispersão: uma leitura única sai enviesada porque acontece no
     * instante em que o app acorda o aparelho e acaba medindo o próprio custo da amostragem.
     */
    val currentNowSamples: List<Long> = emptyList(),

    /** Brilho da tela, 0–255 conforme Settings.System.SCREEN_BRIGHTNESS. */
    val screenBrightness: Int? = null,
    val autoBrightness: Boolean? = null,
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val networkMetered: Boolean? = null,
    val locationEnabled: Boolean? = null,
    val powerSaveMode: Boolean? = null,
    val deviceIdleMode: Boolean? = null,
    /** Tempo acumulado de tela ligada no dia local desta amostra. */
    val interactiveMsToday: Long = 0L,
) {
    val temperatureCelsius: Double? get() = temperatureDeciC?.let { it / 10.0 }
    val voltageVolts: Double? get() = voltageMv?.let { it / 1000.0 }
    val isCharging: Boolean get() = status == BatteryStatus.CHARGING || status == BatteryStatus.FULL
}
