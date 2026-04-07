package com.drivershield.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DriverShieldColors.Accent,
    onPrimary = Color.Black,
    primaryContainer = DriverShieldColors.AccentDim,
    secondary = DriverShieldColors.WorkGreen,
    onSecondary = Color.Black,
    background = DriverShieldColors.AmoledBlack,
    onBackground = DriverShieldColors.OnSurface,
    surface = DriverShieldColors.Surface,
    onSurface = DriverShieldColors.OnSurface,
    surfaceVariant = DriverShieldColors.SurfaceHigh,
    onSurfaceVariant = DriverShieldColors.OnSurfaceMid,
    error = DriverShieldColors.DangerRed,
    onError = Color.Black
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
