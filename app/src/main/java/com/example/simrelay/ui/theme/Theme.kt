package com.example.simrelay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SimRelayColorScheme = darkColorScheme(
    primary = SimRelayColors.Accent,
    secondary = SimRelayColors.Cyan,
    tertiary = SimRelayColors.Success,
    background = SimRelayColors.Background,
    surface = SimRelayColors.Surface,
)

@Composable
fun SimrelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme || !dynamicColor) SimRelayColorScheme else SimRelayColorScheme,
        typography = Typography,
        content = content,
    )
}