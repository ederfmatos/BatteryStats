package dev.ederfmatos.batterystats.domain.attribution

/**
 * Quanto tempo um app manteve um foreground service ativo dentro de janelas de tela apagada.
 *
 * Existe para dar nome ao bucket "Sistema / segundo plano" sem violar a regra que sustenta o app:
 * nenhum mAh é atribuído aqui. A tela mostra tempo, que é um fato conferível, em vez de dividir
 * consumo entre apps que ninguém consegue medir separadamente.
 */
data class BackgroundActivity(
    val packageName: String,
    val activeMs: Long,
    /** Fração do tempo de tela apagada em que o serviço esteve ativo. */
    val fractionOfScreenOff: Double,
)

object BackgroundActivityCalculator {

    /**
     * Cruza os intervalos de foreground service com as janelas de tela apagada.
     *
     * Só conta o que cai **dentro** de janelas medidas: um serviço ativo durante um buraco de
     * amostragem não é atribuível a nada, porque nesse período não houve medição de consumo com
     * que correlacionar.
     */
    fun calculate(
        screenOffWindows: List<LongRange>,
        serviceIntervals: List<ForegroundInterval>,
    ): List<BackgroundActivity> {
        val totalScreenOffMs = screenOffWindows.sumOf { it.last - it.first }
        if (totalScreenOffMs <= 0L) return emptyList()

        val activeByPackage = mutableMapOf<String, Long>()
        for (interval in serviceIntervals) {
            var overlap = 0L
            for (window in screenOffWindows) {
                overlap += interval.overlapMs(window.first, window.last)
            }
            if (overlap > 0L) activeByPackage.merge(interval.packageName, overlap, Long::plus)
        }

        return activeByPackage
            .map { (packageName, activeMs) ->
                BackgroundActivity(
                    packageName = packageName,
                    activeMs = activeMs,
                    fractionOfScreenOff = activeMs.toDouble() / totalScreenOffMs,
                )
            }
            .sortedByDescending { it.activeMs }
    }
}
