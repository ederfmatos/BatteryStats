package dev.ederfmatos.batterystats.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ederfmatos.batterystats.domain.model.CurrentCalibration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Intervalos de amostragem oferecidos. Mais curto custa mais bateria para medir bateria. */
enum class SamplingInterval(val millis: Long) {
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(300_000L);

    companion object {
        val DEFAULT = ONE_MINUTE

        fun fromMillis(millis: Long): SamplingInterval =
            entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}

data class AppSettings(
    val samplingInterval: SamplingInterval = SamplingInterval.DEFAULT,
    val startOnBoot: Boolean = false,
    val calibration: CurrentCalibration = CurrentCalibration.DEFAULT,
    /** Degrau de quantização do CHARGE_COUNTER detectado neste aparelho, em µAh. */
    val quantizationStepUah: Long = 0L,
    /** Marca d'água do último evento de uso já processado, para não reconsultar o passado. */
    val lastProcessedEventMs: Long = 0L,
    /** Último app visto em primeiro plano; sobrevive a reinícios do serviço. */
    val lastKnownForegroundPackage: String? = null,
    /** O usuário quer a amostragem ligada. O watchdog usa isto para decidir se deve ressuscitar. */
    val samplingEnabled: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            samplingInterval = SamplingInterval.fromMillis(
                prefs[KEY_SAMPLING_INTERVAL_MS]?.toLong() ?: SamplingInterval.DEFAULT.millis
            ),
            startOnBoot = prefs[KEY_START_ON_BOOT] ?: false,
            calibration = CurrentCalibration(
                divisor = prefs[KEY_CALIBRATION_DIVISOR] ?: 1000,
                inverted = prefs[KEY_CALIBRATION_INVERTED] ?: false,
                source = prefs[KEY_CALIBRATION_SOURCE]
                    ?.let { name ->
                        runCatching { CurrentCalibration.Source.valueOf(name) }
                            .getOrDefault(CurrentCalibration.Source.DEFAULT)
                    }
                    ?: CurrentCalibration.Source.DEFAULT,
                sampleCount = prefs[KEY_CALIBRATION_SAMPLES] ?: 0,
            ),
            quantizationStepUah = prefs[KEY_QUANTIZATION_STEP_UAH] ?: 0L,
            lastProcessedEventMs = prefs[KEY_LAST_EVENT_MS] ?: 0L,
            lastKnownForegroundPackage = prefs[KEY_LAST_FOREGROUND_PACKAGE],
            samplingEnabled = prefs[KEY_SAMPLING_ENABLED] ?: false,
        )
    }

    suspend fun setSamplingEnabled(enabled: Boolean) {
        store.edit { it[KEY_SAMPLING_ENABLED] = enabled }
    }

    suspend fun setQuantizationStepUah(stepUah: Long) {
        store.edit { it[KEY_QUANTIZATION_STEP_UAH] = stepUah }
    }

    suspend fun setForegroundWatermark(lastEventMs: Long, lastPackage: String?) {
        store.edit { prefs ->
            prefs[KEY_LAST_EVENT_MS] = lastEventMs
            if (lastPackage == null) {
                prefs.remove(KEY_LAST_FOREGROUND_PACKAGE)
            } else {
                prefs[KEY_LAST_FOREGROUND_PACKAGE] = lastPackage
            }
        }
    }

    suspend fun setSamplingInterval(interval: SamplingInterval) {
        store.edit { it[KEY_SAMPLING_INTERVAL_MS] = interval.millis.toInt() }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        store.edit { it[KEY_START_ON_BOOT] = enabled }
    }

    suspend fun setCalibration(calibration: CurrentCalibration) {
        store.edit { prefs ->
            prefs[KEY_CALIBRATION_DIVISOR] = calibration.divisor
            prefs[KEY_CALIBRATION_INVERTED] = calibration.inverted
            prefs[KEY_CALIBRATION_SOURCE] = calibration.source.name
            prefs[KEY_CALIBRATION_SAMPLES] = calibration.sampleCount
        }
    }

    private companion object {
        // Guardado como Int porque nenhum intervalo oferecido passa de 300000 ms.
        val KEY_SAMPLING_INTERVAL_MS = intPreferencesKey("sampling_interval_ms")
        val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val KEY_CALIBRATION_DIVISOR = intPreferencesKey("calibration_divisor")
        val KEY_CALIBRATION_INVERTED = booleanPreferencesKey("calibration_inverted")
        val KEY_CALIBRATION_SOURCE = stringPreferencesKey("calibration_source")
        val KEY_CALIBRATION_SAMPLES = intPreferencesKey("calibration_samples")
        val KEY_QUANTIZATION_STEP_UAH = longPreferencesKey("quantization_step_uah")
        val KEY_LAST_EVENT_MS = longPreferencesKey("last_processed_event_ms")
        val KEY_LAST_FOREGROUND_PACKAGE = stringPreferencesKey("last_foreground_package")
        val KEY_SAMPLING_ENABLED = booleanPreferencesKey("sampling_enabled")
    }
}
