package com.airchecklists.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.airchecklists.app.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = AviationBlue,
    onPrimary = LightSurface,
    primaryContainer = AviationBlueLight,
    onPrimaryContainer = LightSurface,
    secondary = SkyCyan,
    tertiary = WarningAmber,
    background = LightBackground,
    surface = LightSurface,
)

private val DarkColors = darkColorScheme(
    primary = AviationBlueLight,
    onPrimary = DarkBackground,
    primaryContainer = AviationBlueDark,
    onPrimaryContainer = LightSurface,
    secondary = SkyCyan,
    tertiary = WarningAmber,
    background = DarkBackground,
    surface = DarkSurface,
)

@Composable
fun AirDetenteTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        // LIGHT is retired from the UI; any legacy persisted value falls back to system.
        ThemeMode.LIGHT -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
