package com.example.barakah.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Night,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Mist,
    secondary = Gold,
    onSecondary = Night,
    background = Night,
    onBackground = Mist,
    surface = NightSurface,
    onSurface = Mist,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = MistMuted,
    outline = MistMuted
)

private val LightColors = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = PaperSurface,
    primaryContainer = Emerald,
    onPrimaryContainer = PaperSurface,
    secondary = Gold,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    outline = InkMuted
)

@Composable
fun BarakahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
