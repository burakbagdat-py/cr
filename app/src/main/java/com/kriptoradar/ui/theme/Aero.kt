package com.kriptoradar.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/** Aero cam teması renkleri. */
object Aero {
    val glassTop = Color(0x26FFFFFF)
    val glassBottom = Color(0x0DFFFFFF)
    val edgeTop = Color(0x55FFFFFF)
    val edgeBottom = Color(0x10FFFFFF)
    val bar = Color(0x99101622)
    val blobA = Color(0xFF1E6BFF) // mavi
    val blobB = Color(0xFF00C2A8) // turkuaz
    val blobC = Color(0xFF8A2BE2) // mor
    val blobD = Color(0xFFF0B90B) // altın
}

/**
 * Uygulamanın arkasında yavaşça süzülen renkli ışık bulutları (aurora).
 * Üstteki yarı saydam cam kartlar bu ışığı geçirdiği için "Aero" görünümü oluşur.
 */
@Composable
fun AeroBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(28_000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(6_000), RepeatMode.Reverse), label = "breathe")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF0B1220), Color(0xFF070A12))))
        fun blob(color: Color, cx: Float, cy: Float, r: Float, a: Float) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = a * 0.35f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        val w = size.width; val h = size.height
        blob(Aero.blobA, w * (0.25f + 0.15f * cos(phase)), h * (0.18f + 0.08f * sin(phase * 2)), w * 0.75f * breathe, 0.30f)
        blob(Aero.blobB, w * (0.85f + 0.12f * sin(phase)), h * (0.55f + 0.12f * cos(phase)), w * 0.70f, 0.22f)
        blob(Aero.blobC, w * (0.15f + 0.10f * sin(phase + 1.5f)), h * (0.85f + 0.06f * cos(phase * 1.5f)), w * 0.65f / breathe, 0.24f)
        blob(Aero.blobD, w * (0.70f + 0.10f * cos(phase + 2f)), h * (0.05f + 0.05f * sin(phase)), w * 0.40f, 0.10f)
    }
}
