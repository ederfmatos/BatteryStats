package dev.ederfmatos.batterystats.domain.drain

/**
 * Projeta quanto tempo de bateria resta.
 *
 * Deliberadamente NÃO extrapola o último minuto: o consumo instantâneo oscila uma ordem de grandeza
 * entre tela ligada e desligada, e projetar a partir dele produz números que pulam de 3h para 40h
 * a cada leitura. A projeção usa o dreno médio ponderado das últimas 24h, que já embute a proporção
 * real de tela ligada e desligada do usuário.
 *
 * Quando há CHARGE_COUNTER a conta é direta (carga restante ÷ dreno). Sem ele, cai para o
 * %/hora observado, que é mais grosseiro porque o nível anda em degraus de 1%.
 */
class RuntimeProjector {

    fun project(
        stats: DrainStats,
        currentLevelPct: Int,
        chargeCounterUah: Long?,
    ): RuntimeProjection {
        val averageMilliAmps = stats.overallMilliAmps.takeIf { it > 0.0 }
        val percentPerHour = weightedPercentPerHour(stats)

        val fromCounter = if (averageMilliAmps != null && chargeCounterUah != null) {
            (chargeCounterUah / 1000.0) / averageMilliAmps
        } else {
            null
        }
        val fromLevel = if (percentPerHour != null && percentPerHour > 0.0) {
            currentLevelPct / percentPerHour
        } else {
            null
        }

        return RuntimeProjection(
            hoursRemaining = fromCounter ?: fromLevel,
            basedOnMilliAmps = averageMilliAmps,
            basedOnPercentPerHour = percentPerHour,
        )
    }

    private fun weightedPercentPerHour(stats: DrainStats): Double? {
        val onMs = stats.screenOn.durationMs
        val offMs = stats.screenOff.durationMs
        val totalMs = onMs + offMs
        if (totalMs <= 0L) return null
        val weighted =
            (stats.screenOn.percentPerHour * onMs + stats.screenOff.percentPerHour * offMs) / totalMs
        return weighted.takeIf { it > 0.0 }
    }
}

/**
 * Dreno com a tela desligada acima do limiar é o sintoma clássico de wakelock preso: alguma coisa
 * está segurando o aparelho acordado quando ele deveria estar em repouso profundo.
 */
class WakelockSuspicionDetector(
    private val thresholdMilliAmps: Double = DEFAULT_THRESHOLD_MA,
) {
    fun isSuspicious(stats: DrainStats): Boolean {
        val screenOff = stats.screenOff
        if (screenOff.windowCount < MIN_WINDOWS) return false
        if (screenOff.durationMs < MIN_DURATION_MS) return false
        return screenOff.averageMilliAmps > thresholdMilliAmps
    }

    companion object {
        /**
         * Um aparelho moderno em repouso profundo fica na casa de 5-20 mA. Passar de 50 mA
         * sustentados com a tela desligada não é normal.
         */
        const val DEFAULT_THRESHOLD_MA = 50.0
        const val MIN_WINDOWS = 10
        const val MIN_DURATION_MS = 30 * 60_000L
    }
}
