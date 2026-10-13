package com.kriptoradar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Değer değiştiğinde eskisinden yenisine sayaç gibi akan sayı (Double hassasiyetiyle).
 */
@Composable
fun animatedDouble(target: Double, durationMs: Int = 700): Double {
    val from = remember { mutableDoubleStateOf(target) }
    val shown = remember { mutableDoubleStateOf(target) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(target) {
        from.doubleValue = shown.doubleValue
        p.snapTo(0f)
        p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) {
            shown.doubleValue = from.doubleValue + (target - from.doubleValue) * value
        }
        shown.doubleValue = target
    }
    return shown.doubleValue
}

@Composable
fun AnimatedNumber(
    value: Double, format: (Double) -> String, modifier: Modifier = Modifier,
    color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, durationMs: Int = 700,
) {
    Text(format(animatedDouble(value, durationMs)), modifier, color = color, fontSize = fontSize, fontWeight = fontWeight)
}

/**
 * Kayan rakamlar (kilometre sayacı gibi): değişen her karakter yukarı/aşağı kayarak değişir.
 */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default, color: Color = Color.Unspecified, up: Boolean = true) {
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (up) 1 else -1
                    (slideInVertically(tween(260)) { it * dir } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(260)) { -it * dir } + fadeOut(tween(200)))
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Canlı bağlantı göstergesi: dalga yayan nokta. */
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, 2.6f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "a")
    Box(Modifier.size(size * 2.6f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.clip(CircleShape).background(color))
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Skoru dolan halka olarak gösterir (0-100). */
@Composable
fun ScoreRing(score: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val p by animateFloatAsState(score / 100f, tween(1100, easing = FastOutSlowInEasing), label = "ring")
    val shown = (p * 100).toInt()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color, color.copy(alpha = 0.5f))), -90f, 360f * p, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$shown", color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.32f).sp)
    }
}

/** Dolarak gelen ilerleme çubuğu. */
@Composable
fun AnimatedBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, delayMs: Int = 0) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, delayMillis = delayMs, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(Color(0x22FFFFFF))) {
        Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.001f)).height(height).clip(RoundedCornerShape(height))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
    }
}
