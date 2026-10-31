package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CadDarkColorScheme = darkColorScheme(
    primary = CadCyanPrimary,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004550),
    onPrimaryContainer = Color(0xFFB8F6FF),
    secondary = CadAmberSnap,
    onSecondary = Color(0xFF2B1C00),
    secondaryContainer = Color(0xFF4C3300),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = CadEmeraldCrossing,
    onTertiary = Color(0xFF002C1E),
    background = CadModelSpaceBg,
    onBackground = CadTextPrimary,
    surface = CadPanelSurface,
    onSurface = CadTextPrimary,
    surfaceVariant = CadPanelElevated,
    onSurfaceVariant = CadTextSecondary,
    outline = CadBorderHairline,
    error = CadCrimsonAlert
)

@Composable
fun PrecisionCADTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CadDarkColorScheme,
        typography = Typography,
        content = content
    )
}
