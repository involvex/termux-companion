package com.termux.companion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TerminalGreen = Color(0xFF4AF626)
private val TerminalDarkBg = Color(0xFF1A1A2E)
private val TerminalDarkerBg = Color(0xFF0F0F1A)
private val TerminalAccent = Color(0xFF6C63FF)
private val TerminalError = Color(0xFFE57373)
private val TerminalWarning = Color(0xFFFFB74D)
private val TerminalSurface = Color(0xFF252540)

private val DarkColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2D5A1E),
    onPrimaryContainer = TerminalGreen,
    secondary = TerminalAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2660),
    onSecondaryContainer = Color(0xFFB8B3FF),
    tertiary = Color(0xFF4FC3F7),
    background = TerminalDarkBg,
    onBackground = Color(0xFFE0E0E0),
    surface = TerminalSurface,
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A45),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = TerminalError,
    onError = Color.Black,
    outline = Color(0xFF4A4A6A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF5C6BC0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5CAE9),
    onSecondaryContainer = Color(0xFF303F9F),
    tertiary = Color(0xFF0288D1),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = Color(0xFF79747E)
)

@Composable
fun TermuxCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
