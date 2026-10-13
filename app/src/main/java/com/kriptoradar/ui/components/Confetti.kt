package com.kriptoradar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float, val drift: Float)

/** Ekranın ortasından patlayan konfeti; [trigger] her değiştiğinde yeniden oynar. */
@Composable
fun ConfettiBurst(trigger: Any?, modifier: Modifier = Modifier) {
    if (trigger == null) return
    val colors = listOf(Color(0xFFF0B90B), Color(0xFF2EBD85), Color(0xFF1E6BFF), Color(0xFFFF4D9D), Color(0xFF00C2A8), Color.White)
    val parts = remember(trigger) {
        List(90) {
            Particle(
                angle = (Random.nextFloat() * 2 * Math.PI).toFloat(), speed = 600f + Random.nextFloat() * 900f,
                color = colors.random(), size = 8f + Random.nextFloat() * 10f, spin = Random.nextFloat() * 720f - 360f,
                drift = Random.nextFloat() * 2f - 1f,
            )
        }
    }
    val t = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(2000, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val cx = size.width / 2; val cy = size.height * 0.35f
        val time = t.value * 2f // saniye
        for (p in parts) {
            val x = cx + cos(p.angle) * p.speed * time * 0.6f + p.drift * 60f * time
            val y = cy + sin(p.angle) * p.speed * time * 0.6f + 900f * time * time // yerçekimi
            val alpha = (1f - t.value).coerceIn(0f, 1f)
            rotate(p.spin * time, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - p.size / 2, y - p.size / 4), Size(p.size, p.size / 2))
            }
        }
    }
}
