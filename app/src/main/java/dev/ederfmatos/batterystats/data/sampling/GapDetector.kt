package dev.ederfmatos.batterystats.data.sampling

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import dev.ederfmatos.batterystats.domain.drain.GapReason
import dev.ederfmatos.batterystats.domain.drain.MeasurementGap

/**
 * Descobre se houve um buraco de medição desde a última amostra e por quê.
 *
 * O gatilho é o início do serviço: se a última amostra é mais velha que três intervalos de
 * amostragem, alguma coisa interrompeu a coleta. Numa coleta real, 59% do tempo caiu em buracos
 * assim, todos invisíveis nos agregados.
 */
class GapDetector(private val context: Context) {

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * @param lastSampleMs timestamp da última amostra gravada, ou null se o banco está vazio.
     * @param samplingIntervalMs intervalo configurado.
     * @param samplingWasEnabled se o usuário tinha a amostragem ligada quando o buraco começou.
     */
    fun detect(
        lastSampleMs: Long?,
        nowMs: Long,
        samplingIntervalMs: Long,
        samplingWasEnabled: Boolean,
    ): MeasurementGap? {
        if (lastSampleMs == null) return null
        val elapsed = nowMs - lastSampleMs
        if (elapsed <= samplingIntervalMs * GAP_INTERVAL_MULTIPLIER) return null

        return MeasurementGap(
            startMs = lastSampleMs,
            endMs = nowMs,
            reason = reasonFor(lastSampleMs, nowMs, samplingWasEnabled),
        )
    }

    /**
     * O aparelho reiniciou se o instante do boot — agora menos o tempo desde o boot — é posterior
     * à última amostra: nada que aconteceu antes do boot pode ter sido gravado depois dele.
     */
    private fun reasonFor(lastSampleMs: Long, nowMs: Long, samplingWasEnabled: Boolean): GapReason {
        val bootMs = nowMs - SystemClock.elapsedRealtime()
        return when {
            bootMs > lastSampleMs -> GapReason.REBOOT
            powerManager?.isDeviceIdleMode == true -> GapReason.DOZE
            samplingWasEnabled -> GapReason.SERVICE_KILLED
            else -> GapReason.UNKNOWN
        }
    }

    companion object {
        /** Um atraso de até 3 intervalos é jitter normal do scheduler, não buraco. */
        const val GAP_INTERVAL_MULTIPLIER = 3
    }
}
