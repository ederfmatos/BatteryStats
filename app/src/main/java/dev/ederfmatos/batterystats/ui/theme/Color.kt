package dev.ederfmatos.batterystats.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta completa gerada a partir da semente #2E6B4F.
 *
 * Os ~30 papéis existem por inteiro de propósito. Definir só primary/secondary/tertiary deixava o
 * resto no baseline arroxeado do Material 3 — e `secondaryContainer`, que é o indicador do item
 * selecionado da barra de navegação, ficava lavanda num app verde sempre que o Material You
 * estivesse desligado ou o aparelho fosse anterior ao Android 12.
 */
object BatteryStatsPalette {
    // Claro
    val PrimaryLight = Color(0xFF2E6B4F)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFB2F1CB)
    val OnPrimaryContainerLight = Color(0xFF00210F)
    val SecondaryLight = Color(0xFF4F6354)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFD2E8D5)
    val OnSecondaryContainerLight = Color(0xFF0D1F14)
    val TertiaryLight = Color(0xFF3B6470)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFBEEAF8)
    val OnTertiaryContainerLight = Color(0xFF001F27)
    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)
    val BackgroundLight = Color(0xFFF6FBF4)
    val OnBackgroundLight = Color(0xFF181D19)
    val SurfaceLight = Color(0xFFF6FBF4)
    val OnSurfaceLight = Color(0xFF181D19)
    val SurfaceVariantLight = Color(0xFFDBE5DB)
    val OnSurfaceVariantLight = Color(0xFF404942)
    val OutlineLight = Color(0xFF707972)
    val OutlineVariantLight = Color(0xFFBFC9C0)
    val SurfaceContainerLight = Color(0xFFEAEFE8)
    val SurfaceContainerHighLight = Color(0xFFE4EAE2)
    val SurfaceContainerHighestLight = Color(0xFFDFE4DD)
    val SurfaceContainerLowLight = Color(0xFFF0F5EE)
    val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
    val InverseSurfaceLight = Color(0xFF2D322E)
    val InverseOnSurfaceLight = Color(0xFFEEF2EC)
    val InversePrimaryLight = Color(0xFF95D5B0)

    // Escuro
    val PrimaryDark = Color(0xFF95D5B0)
    val OnPrimaryDark = Color(0xFF003921)
    val PrimaryContainerDark = Color(0xFF135232)
    val OnPrimaryContainerDark = Color(0xFFB2F1CB)
    val SecondaryDark = Color(0xFFB6CCBA)
    val OnSecondaryDark = Color(0xFF223528)
    val SecondaryContainerDark = Color(0xFF384B3E)
    val OnSecondaryContainerDark = Color(0xFFD2E8D5)
    val TertiaryDark = Color(0xFFA4CDDB)
    val OnTertiaryDark = Color(0xFF053541)
    val TertiaryContainerDark = Color(0xFF224C58)
    val OnTertiaryContainerDark = Color(0xFFBEEAF8)
    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)
    val BackgroundDark = Color(0xFF101511)
    val OnBackgroundDark = Color(0xFFDFE4DD)
    val SurfaceDark = Color(0xFF101511)
    val OnSurfaceDark = Color(0xFFDFE4DD)
    val SurfaceVariantDark = Color(0xFF404942)
    val OnSurfaceVariantDark = Color(0xFFBFC9C0)
    val OutlineDark = Color(0xFF8A938B)
    val OutlineVariantDark = Color(0xFF404942)
    val SurfaceContainerDark = Color(0xFF1C211D)
    val SurfaceContainerHighDark = Color(0xFF262B27)
    val SurfaceContainerHighestDark = Color(0xFF313631)
    val SurfaceContainerLowDark = Color(0xFF181D19)
    val SurfaceContainerLowestDark = Color(0xFF0B0F0C)
    val InverseSurfaceDark = Color(0xFFDFE4DD)
    val InverseOnSurfaceDark = Color(0xFF2D322E)
    val InversePrimaryDark = Color(0xFF2E6B4F)
}

/**
 * Cores dos gráficos.
 *
 * Deliberadamente **fora** do ColorScheme. Sob Material You, `primary` e `tertiary` derivam do
 * mesmo papel de parede e podem cair em matizes vizinhos — e aí os marcadores de início de carga
 * somem contra a linha de nível. Aqui a cor carrega significado, então ela não pode depender do
 * papel de parede de quem instalou.
 */
data class ChartColors(
    val levelLine: Color,
    val screenOnBand: Color,
    val chargeMarker: Color,
    val drainBar: Color,
    val grid: Color,
) {
    companion object {
        val Light = ChartColors(
            levelLine = Color(0xFF1B6B42),
            screenOnBand = Color(0x1F2E6B4F),
            chargeMarker = Color(0xFFC2571A),
            drainBar = Color(0xFF2E6B4F),
            grid = Color(0xFFBFC9C0),
        )
        val Dark = ChartColors(
            levelLine = Color(0xFF7FE0AB),
            screenOnBand = Color(0x2695D5B0),
            chargeMarker = Color(0xFFFFB77C),
            drainBar = Color(0xFF95D5B0),
            grid = Color(0xFF404942),
        )
    }
}

val LocalChartColors = androidx.compose.runtime.staticCompositionLocalOf { ChartColors.Light }
