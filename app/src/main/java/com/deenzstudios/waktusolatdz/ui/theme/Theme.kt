package com.deenzstudios.waktusolatdz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Gold800,
    onPrimary = Cream,
    secondary = Gold500,
    onSecondary = Gold900,
    tertiary = Gold300,
    background = Gold500,
    onBackground = Gold900,
    surface = Cream,
    onSurface = BrownText,
)

private val DarkColorScheme = darkColorScheme(
    primary = Gold300,
    onPrimary = Gold900,
    secondary = Gold500,
    onSecondary = Gold900,
    background = Gold900,
    onBackground = Cream,
    surface = Gold800,
    onSurface = Cream,
)

@Composable
fun WaktuSolatDZTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
