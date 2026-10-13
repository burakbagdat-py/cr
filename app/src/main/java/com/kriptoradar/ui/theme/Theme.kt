package com.kriptoradar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Up = Color(0xFF2EBD85)
val Down = Color(0xFFF6465D)
val Accent = Color(0xFFF0B90B)
val Bg = Color(0xFF0E1116)
val Card = Color(0xFF181C23)
val Muted = Color(0xFF8A919E)

fun changeColor(v: Double) = when { v > 0 -> Up; v < 0 -> Down; else -> Muted }

fun scoreColor(score: Int) = when {
    score >= 75 -> Up
    score >= 60 -> Color(0xFF8BC34A)
    score >= 45 -> Accent
    score >= 30 -> Color(0xFFFF9800)
    else -> Down
}

@Composable
fun KriptoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black,
            secondary = Up, background = Color.Transparent, surface = Color.Transparent,
            surfaceVariant = Color(0x1FFFFFFF), surfaceContainer = Aero.bar, surfaceContainerHigh = Color(0xF2141A26),
            surfaceContainerHighest = Color(0x1FFFFFFF), secondaryContainer = Color(0x33F0B90B),
            outline = Color(0x40FFFFFF), outlineVariant = Color(0x22FFFFFF),
            onSurface = Color(0xFFEAECEF), onSurfaceVariant = Muted,
        ),
        content = content,
    )
}
