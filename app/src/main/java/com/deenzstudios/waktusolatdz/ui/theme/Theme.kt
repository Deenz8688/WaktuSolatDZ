package com.deenzstudios.waktusolatdz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Purple800,
    onPrimary = SurfaceWhite,
    primaryContainer = Purple100,
    onPrimaryContainer = Purple900,
    secondary = Purple500,
    onSecondary = SurfaceWhite,
    secondaryContainer = PurpleSoft,
    onSecondaryContainer = Purple900,
    tertiary = Purple300,
    background = AppBackgroundBottom,
    onBackground = InkText,
    surface = SurfaceWhite,
    onSurface = InkText,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = PurpleTextMuted,
    outline = PurpleTextMuted,
    outlineVariant = Purple300,
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple300,
    onPrimary = Purple900,
    secondary = Purple500,
    onSecondary = Purple900,
    background = Purple900,
    onBackground = SurfaceWhite,
    surface = Purple800,
    onSurface = SurfaceWhite,
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
