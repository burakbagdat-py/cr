package com.kriptoradar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoradar.ui.theme.Aero

private val GlassShape = RoundedCornerShape(18.dp)

/**
 * Aero cam kart: yarı saydam degrade gövde, parlak üst kenar ve hafif yansıma.
 * material3 Card ile aynı şekilde kullanılır; [colors] verilirse gövde o renge boyanır.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = tint ?: colors?.containerColor
    val body = if (c != null) listOf(c.copy(alpha = (c.alpha + 0.10f).coerceAtMost(0.45f)), c.copy(alpha = c.alpha * 0.5f))
    else listOf(Aero.glassTop, Aero.glassBottom)
    Column(
        modifier
            .clip(GlassShape)
            .background(Brush.verticalGradient(body))
            .border(1.dp, Brush.verticalGradient(listOf(Aero.edgeTop, Aero.edgeBottom)), GlassShape)
            .drawWithContent {
                drawContent()
                // Üst yarıda cam yansıması
                drawRect(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color.Transparent), endY = size.height * 0.45f))
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides Color(0xFFEAECEF)) { content() }
    }
}

/** Basınca yaylanarak hafifçe küçülen tıklama. */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
}

/** İlk göründüğünde aşağıdan süzülerek ve belirerek gelir; [index] ile sıralı gecikme. */
fun Modifier.enterAnim(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(450, delayMillis = (index.coerceAtMost(8)) * 55, easing = FastOutSlowInEasing))
    }
    this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 40f
        val s = 0.96f + 0.04f * a.value
        scaleX = s; scaleY = s
    }
}

/** Yükleniyor iskeleti: üzerinde ışık süzülen gri blok. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, height: Dp = 56.dp) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    Box(
        modifier.fillMaxWidth().height(height).clip(GlassShape).background(Color(0x14FFFFFF))
            .drawWithContent {
                val w = size.width
                drawRect(Brush.linearGradient(
                    listOf(Color.Transparent, Color(0x22FFFFFF), Color.Transparent),
                    start = Offset(w * x - w * 0.4f, 0f), end = Offset(w * x, size.height),
                ))
            }
    )
}

@Composable
fun ShimmerList(count: Int = 6, height: Dp = 56.dp) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        repeat(count) { Shimmer(height = height) }
    }
}
