package dev.ederfmatos.batterystats.domain.health

import dev.ederfmatos.batterystats.domain.model.BatterySnapshot

class BatteryHealthEstimator {

    /**
     * [samples] deve conter o histórico mais longo disponível. O "cheio atual" usa apenas as
     * leituras dos últimos [RECENT_WINDOW_DAYS] dias para que a queda apareça, e o "melhor
     * observado" varre tudo.
     */
    fun estimate(samples: List<BatterySnapshot>, nowMs: Long): BatteryHealthEstimate {
        val full = samples.filter {
            it.levelPct >= BatteryHealthEstimate.FULL_LEVEL_THRESHOLD_PCT &&
                it.chargeCounterUah != null
        }
        if (full.isEmpty()) {
            return BatteryHealthEstimate(null, null, null, observationDays = 0)
        }

        val best = full.mapNotNull { it.chargeCounterUah }.maxOrNull()
        val recentCutoff = nowMs - RECENT_WINDOW_DAYS * MILLIS_PER_DAY
        val current = full
            .filter { it.timestampMs >= recentCutoff }
            .mapNotNull { it.chargeCounterUah }
            .maxOrNull()

        val spanMs = full.maxOf { it.timestampMs } - full.minOf { it.timestampMs }
        val observationDays = (spanMs / MILLIS_PER_DAY).toInt()

        val ratio = if (best != null && best > 0 && current != null) {
            current.toDouble() / best.toDouble()
        } else {
            null
        }

        return BatteryHealthEstimate(
            currentFullChargeUah = current,
            bestObservedFullChargeUah = best,
            relativeRatio = ratio,
            observationDays = observationDays,
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val RECENT_WINDOW_DAYS = 14L
    }
}
