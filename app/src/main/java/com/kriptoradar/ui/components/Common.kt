package com.kriptoradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.theme.changeColor

@Composable
fun PctPill(v: Double, modifier: Modifier = Modifier) {
    val bg by androidx.compose.animation.animateColorAsState(changeColor(v), androidx.compose.animation.core.tween(400), label = "pill")
    Box(modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.75f))), RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(Format.signedPct(v), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Skor rozeti: dolan halka. */
@Composable
fun ScoreBadge(score: Int, color: Color, modifier: Modifier = Modifier) = ScoreRing(score, color, modifier, 46.dp)

const val DISCLAIMER = "Bu uygulamadaki skorlar ve öneriler yalnızca geçmiş fiyat/hacim istatistiklerinden " +
    "otomatik hesaplanır; yatırım tavsiyesi değildir. Kripto paralar çok yüksek risk taşır."
