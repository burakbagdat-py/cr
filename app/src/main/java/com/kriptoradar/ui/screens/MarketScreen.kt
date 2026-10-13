package com.kriptoradar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.kriptoradar.ui.components.ShimmerList
import com.kriptoradar.ui.components.bouncyClick
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.Down
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.MarketFilter
import com.kriptoradar.ui.MarketRow
import com.kriptoradar.ui.components.PctPill
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.changeColor

@Composable
fun MarketScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Coin ara (BTC, SOL…)") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Clear, null) } },
            singleLine = true,
        )
        LazyRow(
            Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            items(MarketFilter.entries) { mf ->
                FilterChip(selected = f == mf, onClick = { vm.filter.value = mf }, label = { Text(mf.label) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Çift / Hacim", Modifier.weight(1f), fontSize = 11.sp, color = Muted)
            Text("Fiyat / 5dk", Modifier.weight(1f), fontSize = 11.sp, color = Muted)
            Text("24s", fontSize = 11.sp, color = Muted)
        }
        if (rows.isEmpty()) {
            if (f == MarketFilter.FAV) Text("Henüz favori yok. Listede ☆ simgesine dokunun.", Modifier.padding(24.dp), color = Muted)
            else Column(Modifier.padding(12.dp)) { ShimmerList(8, 52.dp) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.t.symbol }) { r ->
                Column(Modifier.animateItem()) {
                    Row_(r, onOpen = { onOpen(r.t.symbol) }, onFav = { vm.toggleFav(r.t.symbol) })
                    HorizontalDivider(color = Color(0x14FFFFFF))
                }
            }
        }
    }
}

@Composable
private fun Row_(r: MarketRow, onOpen: () -> Unit, onFav: () -> Unit) {
    val t = r.t
    // Fiyat değişince satır kısa süre yeşil/kırmızı parlar
    val prev = remember { mutableDoubleStateOf(t.price) }
    val flash = remember { Animatable(0f) }
    var flashUp by remember { mutableStateOf(true) }
    LaunchedEffect(t.price) {
        if (t.price != prev.doubleValue) {
            flashUp = t.price > prev.doubleValue
            prev.doubleValue = t.price
            flash.snapTo(1f)
            flash.animateTo(0f, tween(900))
        }
    }
    val flashColor = (if (flashUp) Up else Down).copy(alpha = 0.16f * flash.value)
    Row(
        Modifier.fillMaxWidth().background(flashColor).bouncyClick(onOpen).padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val starScale by animateFloatAsState(if (r.fav) 1.25f else 1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium), label = "star")
        IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
            Icon(if (r.fav) Icons.Filled.Star else Icons.Outlined.Star, null, tint = if (r.fav) Accent else Muted,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = starScale; scaleY = starScale })
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(t.base, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("/${t.quote}", fontSize = 11.sp, color = Muted)
            }
            Text("Hcm ${Format.compact(t.quoteVolume)}", fontSize = 11.sp, color = Muted)
        }
        Column(Modifier.weight(1f)) {
            Text(Format.price(t.price), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = changeColor(r.change5m ?: 0.0).takeIf { r.change5m != null && kotlin.math.abs(r.change5m) > 0.01 } ?: Color.Unspecified)
            Text(r.change5m?.let { "5dk " + Format.signedPct(it) } ?: "5dk —", fontSize = 11.sp, color = changeColor(r.change5m ?: 0.0))
        }
        Spacer(Modifier.width(8.dp))
        PctPill(t.change24hPct)
    }
}
