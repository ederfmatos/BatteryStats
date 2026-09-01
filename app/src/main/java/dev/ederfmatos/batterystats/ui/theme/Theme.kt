package dev.ederfmatos.batterystats.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import dev.ederfmatos.batterystats.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = BatteryStatsPalette.PrimaryLight,
    onPrimary = BatteryStatsPalette.OnPrimaryLight,
    primaryContainer = BatteryStatsPalette.PrimaryContainerLight,
    onPrimaryContainer = BatteryStatsPalette.OnPrimaryContainerLight,
    secondary = BatteryStatsPalette.SecondaryLight,
    onSecondary = BatteryStatsPalette.OnSecondaryLight,
    secondaryContainer = BatteryStatsPalette.SecondaryContainerLight,
    onSecondaryContainer = BatteryStatsPalette.OnSecondaryContainerLight,
    tertiary = BatteryStatsPalette.TertiaryLight,
    onTertiary = BatteryStatsPalette.OnTertiaryLight,
    tertiaryContainer = BatteryStatsPalette.TertiaryContainerLight,
    onTertiaryContainer = BatteryStatsPalette.OnTertiaryContainerLight,
    error = BatteryStatsPalette.ErrorLight,
    onError = BatteryStatsPalette.OnErrorLight,
    errorContainer = BatteryStatsPalette.ErrorContainerLight,
    onErrorContainer = BatteryStatsPalette.OnErrorContainerLight,
    background = BatteryStatsPalette.BackgroundLight,
    onBackground = BatteryStatsPalette.OnBackgroundLight,
    surface = BatteryStatsPalette.SurfaceLight,
    onSurface = BatteryStatsPalette.OnSurfaceLight,
    surfaceVariant = BatteryStatsPalette.SurfaceVariantLight,
    onSurfaceVariant = BatteryStatsPalette.OnSurfaceVariantLight,
    outline = BatteryStatsPalette.OutlineLight,
    outlineVariant = BatteryStatsPalette.OutlineVariantLight,
    surfaceContainer = BatteryStatsPalette.SurfaceContainerLight,
    surfaceContainerHigh = BatteryStatsPalette.SurfaceContainerHighLight,
    surfaceContainerHighest = BatteryStatsPalette.SurfaceContainerHighestLight,
    surfaceContainerLow = BatteryStatsPalette.SurfaceContainerLowLight,
    surfaceContainerLowest = BatteryStatsPalette.SurfaceContainerLowestLight,
    inverseSurface = BatteryStatsPalette.InverseSurfaceLight,
    inverseOnSurface = BatteryStatsPalette.InverseOnSurfaceLight,
    inversePrimary = BatteryStatsPalette.InversePrimaryLight,
)

private val DarkColors = darkColorScheme(
    primary = BatteryStatsPalette.PrimaryDark,
    onPrimary = BatteryStatsPalette.OnPrimaryDark,
    primaryContainer = BatteryStatsPalette.PrimaryContainerDark,
    onPrimaryContainer = BatteryStatsPalette.OnPrimaryContainerDark,
    secondary = BatteryStatsPalette.SecondaryDark,
    onSecondary = BatteryStatsPalette.OnSecondaryDark,
    secondaryContainer = BatteryStatsPalette.SecondaryContainerDark,
    onSecondaryContainer = BatteryStatsPalette.OnSecondaryContainerDark,
    tertiary = BatteryStatsPalette.TertiaryDark,
    onTertiary = BatteryStatsPalette.OnTertiaryDark,
    tertiaryContainer = BatteryStatsPalette.TertiaryContainerDark,
    onTertiaryContainer = BatteryStatsPalette.OnTertiaryContainerDark,
    error = BatteryStatsPalette.ErrorDark,
    onError = BatteryStatsPalette.OnErrorDark,
    errorContainer = BatteryStatsPalette.ErrorContainerDark,
    onErrorContainer = BatteryStatsPalette.OnErrorContainerDark,
    background = BatteryStatsPalette.BackgroundDark,
    onBackground = BatteryStatsPalette.OnBackgroundDark,
    surface = BatteryStatsPalette.SurfaceDark,
    onSurface = BatteryStatsPalette.OnSurfaceDark,
    surfaceVariant = BatteryStatsPalette.SurfaceVariantDark,
    onSurfaceVariant = BatteryStatsPalette.OnSurfaceVariantDark,
    outline = BatteryStatsPalette.OutlineDark,
    outlineVariant = BatteryStatsPalette.OutlineVariantDark,
    surfaceContainer = BatteryStatsPalette.SurfaceContainerDark,
    surfaceContainerHigh = BatteryStatsPalette.SurfaceContainerHighDark,
    surfaceContainerHighest = BatteryStatsPalette.SurfaceContainerHighestDark,
    surfaceContainerLow = BatteryStatsPalette.SurfaceContainerLowDark,
    surfaceContainerLowest = BatteryStatsPalette.SurfaceContainerLowestDark,
    inverseSurface = BatteryStatsPalette.InverseSurfaceDark,
    inverseOnSurface = BatteryStatsPalette.InverseOnSurfaceDark,
    inversePrimary = BatteryStatsPalette.InversePrimaryDark,
)

/** Resolve o modo escolhido pelo usuário contra o do sistema. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BatteryStatsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()
    val context = LocalContext.current
    val colorScheme = when {
        // Material You só existe a partir do Android 12.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalChartColors provides if (darkTheme) ChartColors.Dark else ChartColors.Light
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
