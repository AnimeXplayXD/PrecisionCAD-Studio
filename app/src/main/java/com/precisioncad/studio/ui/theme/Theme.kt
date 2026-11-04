package com.precisioncad.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CadDarkColorScheme = darkColorScheme(
    primary = CadCyanPrimary,
    onPrimary = Color(0xFF050608),
    primaryContainer = Color(0xFF151D26),
    onPrimaryContainer = CadTextPrimary,
    secondary = CadAmberSnap,
    onSecondary = Color(0xFF050608),
    secondaryContainer = Color(0xFF211C13),
    onSecondaryContainer = CadTextPrimary,
    tertiary = CadEmeraldCrossing,
    onTertiary = Color(0xFF050608),
    background = CadModelSpaceBg,
    onBackground = CadTextPrimary,
    surface = CadPanelSurface,
    onSurface = CadTextPrimary,
    surfaceVariant = CadPanelElevated,
    onSurfaceVariant = CadTextSecondary,
    outline = CadBorderHairline,
    outlineVariant = CadBorderStrong,
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
