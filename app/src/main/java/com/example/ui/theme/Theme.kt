package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScannerDarkColorScheme = darkColorScheme(
    primary = LaserCyan,
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = EmeraldLock,
    onSecondary = Color(0xFF00210E),
    secondaryContainer = Color(0xFF005227),
    onSecondaryContainer = Color(0xFF85F8B2),
    tertiary = AmberHandle,
    onTertiary = Color(0xFF261900),
    background = ObsidianBackground,
    onBackground = PaperWhite,
    surface = ObsidianSurface,
    onSurface = PaperWhite,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateBorder,
    error = CoralAlert
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScannerDarkColorScheme,
        typography = Typography,
        content = content
    )
}
