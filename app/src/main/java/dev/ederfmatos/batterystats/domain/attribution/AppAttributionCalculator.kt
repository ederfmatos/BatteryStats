package dev.ederfmatos.batterystats.domain.attribution

import dev.ederfmatos.batterystats.domain.drain.DrainWindow
import dev.ederfmatos.batterystats.domain.drain.ScreenRegime

/**
 * Distribui o consumo medido entre os apps que estavam em primeiro plano.
 *
 * Isto é **estimativa por correlação**, não medição. O sistema não expõe consumo por app para um
 * APK sideloadado (BATTERY_STATS é signature|privileged), então o que se pode fazer é: medir o
 * dreno real de uma janela de tempo e dividi-lo entre os apps que ocuparam a tela nessa janela.
 *
 * Três regras evitam que o número vire ficção:
 *  1. Janelas com a tela desligada nunca são creditadas a um app. Sem tela não há "app em primeiro
 *     plano" no sentido que importa, e o UsageEvents não distingue quem realmente gastou energia.
 *  2. Antes de dividir, subtrai-se a linha de base de repouso do aparelho — senão todo app levaria
 *     a culpa pelo consumo que existiria mesmo com o celular parado.
 *  3. O tempo da janela não coberto por nenhum intervalo de primeiro plano vai para o bucket de
 *     sistema, não é redistribuído entre os apps.
 *  4. Janelas [DrainWindow.lowConfidence] são ignoradas por completo. Elas fecharam por tempo com
 *     um único degrau de quantização, o que significa que o valor pode estar a 100% de distância do
 *     real — dividir isso entre apps produziria um ranking inventado.
 */
class AppAttributionCalculator {

    fun attribute(
        windows: List<DrainWindow>,
        foregroundIntervals: List<ForegroundInterval>,
        idleBaselineMilliAmps: Double?,
    ): List<AppEnergyUsage> {
        val mahByPackage = mutableMapOf<String, Double>()
        val foregroundMsByPackage = mutableMapOf<String, Long>()
        val windowsByPackage = mutableMapOf<String, Int>()
        var systemMah = 0.0
        var systemWindows = 0

        val baseline = (idleBaselineMilliAmps ?: 0.0).coerceAtLeast(0.0)

        for (window in windows) {
            if (window.lowConfidence) continue
            val windowMah = window.milliAmpHours
            if (windowMah <= 0.0) continue

            if (window.screen != ScreenRegime.ON) {
                systemMah += windowMah
                systemWindows++
                continue
            }

            // A parcela de repouso é do aparelho, não do app que estava aberto.
            val baselineMah = (baseline * window.durationHours).coerceAtMost(windowMah)
            systemMah += baselineMah
            val attributableMah = windowMah - baselineMah
            if (attributableMah <= 0.0) continue

            val overlaps = foregroundIntervals
                .map { it.packageName to it.overlapMs(window.startMs, window.endMs) }
                .filter { it.second > 0L }

            val coveredMs = overlaps.sumOf { it.second }
            if (coveredMs <= 0L) {
                systemMah += attributableMah
                continue
            }

            // Tempo de tela sem nenhum app registrado (launcher, telas do sistema, buracos do
            // UsageEvents) não é redistribuído — vira sistema.
            val uncoveredMs = (window.durationMs - coveredMs).coerceAtLeast(0L)
            if (uncoveredMs > 0L) {
                systemMah += attributableMah * (uncoveredMs.toDouble() / window.durationMs)
            }

            for ((packageName, overlapMs) in overlaps) {
                val share = overlapMs.toDouble() / window.durationMs
                mahByPackage.merge(packageName, attributableMah * share, Double::plus)
                foregroundMsByPackage.merge(packageName, overlapMs, Long::plus)
                windowsByPackage.merge(packageName, 1, Int::plus)
            }
        }

        val apps = mahByPackage.map { (packageName, mah) ->
            val foregroundMs = foregroundMsByPackage[packageName] ?: 0L
            val hours = foregroundMs / DrainWindow.MILLIS_PER_HOUR
            AppEnergyUsage(
                packageName = packageName,
                estimatedMilliAmpHours = mah,
                foregroundMs = foregroundMs,
                averageMilliAmpsInForeground = if (hours > 0) mah / hours else 0.0,
                windowCount = windowsByPackage[packageName] ?: 0,
            )
        }

        val system = AppEnergyUsage(
            packageName = SYSTEM_BUCKET_PACKAGE,
            estimatedMilliAmpHours = systemMah,
            foregroundMs = 0L,
            averageMilliAmpsInForeground = 0.0,
            isSystemBucket = true,
            windowCount = systemWindows,
        )

        return (apps + system).sortedByDescending { it.estimatedMilliAmpHours }
    }
}
