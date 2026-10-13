package com.kriptoradar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoradar.analysis.MonteCarlo
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up

/**
 * Monte Carlo animasyonu: önce olası gelecek yolları soldan sağa "akarak" çizilir,
 * ardından %10–%90 ihtimal yelpazesi ve ortanca çizgi belirir.
 */
@Composable
fun MonteCarloChart(mc: MonteCarlo, invested: Double, animKey: Any, modifier: Modifier = Modifier, format: (Double) -> String) {
    val flow = remember(animKey) { Animatable(0f) }
    val fan = remember(animKey) { Animatable(0f) }
    LaunchedEffect(animKey) {
        flow.animateTo(1f, tween(1700, easing = LinearEasing))
        fan.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    // Uç değerler grafiği ezmesin diye ölçeği yüzdeliklere göre kur
    val hi = maxOf(mc.p90.max() * 1.25, invested * 1.05)
    val lo = minOf(mc.p10.min() * 0.85, invested * 0.95)
    Box(modifier.fillMaxWidth().height(220.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(end = 64.dp)) {
            val h = mc.horizon
            fun x(d: Float) = d / h * size.width
            fun y(v: Double) = ((hi - v) / (hi - lo) * size.height).toFloat().coerceIn(-20f, size.height + 20f)
            for (g in 0..3) drawLine(Color(0x14FFFFFF), Offset(0f, size.height * g / 3), Offset(size.width, size.height * g / 3), 1f)
            // Yatırılan tutar çizgisi
            drawLine(Color(0x99FFFFFF), Offset(0f, y(invested)), Offset(size.width, y(invested)), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))

            // 1) Yollar
            val upTo = flow.value * h
            val pathAlpha = 0.45f - 0.25f * fan.value
            for (p in mc.paths) {
                val path = Path()
                path.moveTo(0f, y(p[0]))
                val last = upTo.toInt()
                for (d in 1..last.coerceAtMost(h)) path.lineTo(x(d.toFloat()), y(p[d]))
                if (last < h) {
                    val frac = upTo - last
                    val v = p[last] + (p[last + 1] - p[last]) * frac
                    path.lineTo(x(upTo), y(v))
                }
                val end = p[upTo.toInt().coerceAtMost(h)]
                drawPath(path, (if (end >= invested) Up else Down).copy(alpha = pathAlpha), style = Stroke(1.3f))
            }
            // Akış ucunda parlayan dikey çizgi
            if (flow.value < 1f) drawLine(Brush.verticalGradient(listOf(Color.Transparent, Accent.copy(alpha = 0.7f), Color.Transparent)),
                Offset(x(upTo), 0f), Offset(x(upTo), size.height), 2f)

            // 2) Yelpaze (%10–%90) + ortanca
            if (fan.value > 0f) {
                val reach = (fan.value * h).toInt().coerceAtLeast(1)
                val band = Path().apply {
                    moveTo(0f, y(mc.p90[0]))
                    for (d in 1..reach) lineTo(x(d.toFloat()), y(mc.p90[d]))
                    for (d in reach downTo 0) lineTo(x(d.toFloat()), y(mc.p10[d]))
                    close()
                }
                drawPath(band, Brush.verticalGradient(listOf(Up.copy(alpha = 0.28f), Accent.copy(alpha = 0.18f), Down.copy(alpha = 0.28f))))
                val med = Path().apply { moveTo(0f, y(mc.p50[0])); for (d in 1..reach) lineTo(x(d.toFloat()), y(mc.p50[d])) }
                drawPath(med, Accent, style = Stroke(3f))
                val p90 = Path().apply { moveTo(0f, y(mc.p90[0])); for (d in 1..reach) lineTo(x(d.toFloat()), y(mc.p90[d])) }
                val p10 = Path().apply { moveTo(0f, y(mc.p10[0])); for (d in 1..reach) lineTo(x(d.toFloat()), y(mc.p10[d])) }
                drawPath(p90, Up.copy(alpha = 0.8f), style = Stroke(1.5f))
                drawPath(p10, Down.copy(alpha = 0.8f), style = Stroke(1.5f))
            }
        }
        if (fan.value >= 1f) {
            val h = mc.horizon
            fun yFrac(v: Double) = ((hi - v) / (hi - lo)).toFloat().coerceIn(0.03f, 0.95f)
            Text(format(mc.p90[h]), Modifier.align(Alignment.TopEnd).padding(top = (220 * yFrac(mc.p90[h]) - 8).dp.coerceAtLeast(0.dp)), fontSize = 10.sp, color = Up)
            Text(format(mc.p50[h]), Modifier.align(Alignment.TopEnd).padding(top = (220 * yFrac(mc.p50[h]) - 8).dp.coerceAtLeast(0.dp)), fontSize = 10.sp, color = Accent)
            Text(format(mc.p10[h]), Modifier.align(Alignment.TopEnd).padding(top = (220 * yFrac(mc.p10[h]) - 8).dp.coerceAtLeast(0.dp)), fontSize = 10.sp, color = Down)
        } else {
            Text("simüle ediliyor…", Modifier.align(Alignment.TopEnd), fontSize = 10.sp, color = Muted)
        }
    }
}
