package com.kriptoradar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoradar.data.Kline
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up

/** Mum grafiği + EMA20 çizgisi + hacim çubukları. */
@Composable
fun CandleChart(klines: List<Kline>, ema: DoubleArray?, modifier: Modifier = Modifier) {
    if (klines.isEmpty()) {
        Box(modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { Text("Grafik yükleniyor…", color = Muted) }
        return
    }
    val hi = klines.maxOf { it.high }; val lo = klines.minOf { it.low }
    val maxVol = klines.maxOf { it.volume }.takeIf { it > 0 } ?: 1.0
    Box(modifier.fillMaxWidth().height(260.dp)) {
        Canvas(Modifier.fillMaxWidth().height(260.dp).padding(end = 56.dp)) {
            val volH = size.height * 0.18f
            val priceH = size.height - volH - 6f
            val n = klines.size
            val step = size.width / n
            val bodyW = (step * 0.7f).coerceAtLeast(1f)
            fun y(p: Double) = ((hi - p) / (hi - lo).coerceAtLeast(1e-12) * priceH).toFloat()
            for (g in 0..3) {
                val gy = priceH * g / 3f
                drawLine(Color(0x22FFFFFF), Offset(0f, gy), Offset(size.width, gy), 1f)
            }
            klines.forEachIndexed { i, k ->
                val cx = step * i + step / 2
                val col = if (k.close >= k.open) Up else Down
                drawLine(col, Offset(cx, y(k.high)), Offset(cx, y(k.low)), 1.2f)
                val top = y(maxOf(k.open, k.close)); val bot = y(minOf(k.open, k.close))
                drawRect(col, Offset(cx - bodyW / 2, top), androidx.compose.ui.geometry.Size(bodyW, (bot - top).coerceAtLeast(1f)))
                val vh = (k.volume / maxVol * volH).toFloat()
                drawRect(col.copy(alpha = 0.35f), Offset(cx - bodyW / 2, size.height - vh), androidx.compose.ui.geometry.Size(bodyW, vh))
            }
            if (ema != null && ema.size == n) {
                val p = Path(); var started = false
                for (i in 0 until n) {
                    if (ema[i].isNaN()) continue
                    val pt = Offset(step * i + step / 2, y(ema[i]))
                    if (!started) { p.moveTo(pt.x, pt.y); started = true } else p.lineTo(pt.x, pt.y)
                }
                drawPath(p, Color(0xFFF0B90B), style = Stroke(1.5f))
            }
        }
        Text(Format.price(hi), Modifier.align(Alignment.TopEnd), fontSize = 10.sp, color = Muted)
        Text(Format.price((hi + lo) / 2), Modifier.align(Alignment.CenterEnd).padding(bottom = 40.dp), fontSize = 10.sp, color = Muted)
        Text(Format.price(lo), Modifier.align(Alignment.BottomEnd).padding(bottom = 46.dp), fontSize = 10.sp, color = Muted)
    }
}

/** Küçük çizgi grafik (liste satırları için). */
@Composable
fun Sparkline(points: List<Double>, modifier: Modifier = Modifier) {
    if (points.size < 2) { Box(modifier); return }
    val up = points.last() >= points.first()
    val col = if (up) Up else Down
    Canvas(modifier) {
        val mx = points.max(); val mn = points.min()
        val r = (mx - mn).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX; val y = ((mx - v) / r * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(path, col, style = Stroke(2f))
    }
}
