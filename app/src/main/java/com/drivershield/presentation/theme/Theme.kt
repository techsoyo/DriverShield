package com.drivershield.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = CasioColors.illuminatorNight,  // #00E5FF — acento interactivo
    onPrimary        = Color.Black,
    primaryContainer = CasioColors.legendBlue,        // #1B4F72 — contenedor primario
    secondary        = CasioColors.legendGold,        // #C5A059 — texto de etiquetas
    onSecondary      = Color.Black,
    background       = CasioColors.caseResinDark,     // #212121 — carcasa de plástico
    onBackground     = CasioColors.legendGold,        // #C5A059 — texto sobre carcasa
    surface          = Color(0xFF1A1A1A),              // ligeramente más claro que carcasa
    onSurface        = CasioColors.legendGold,        // #C5A059 — texto sobre superficie
    surfaceVariant   = CasioColors.legendBlue,        // #1B4F72 — botones +/- y selectores
    onSurfaceVariant = CasioColors.legendGold,        // #C5A059 — texto en variante
    error            = DriverShieldColors.DangerRed,
    onError          = Color.Black
)

@Composable
fun DriverShieldTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
