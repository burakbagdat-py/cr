package com.kriptoradar.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.InboxItem
import com.kriptoradar.data.InboxKind
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.components.Card
import com.kriptoradar.ui.components.bouncyClick
import com.kriptoradar.ui.components.enterAnim
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.changeColor

private enum class InboxTab(val label: String) { FEED("Gelenler"), ALERTS("Alarmlarım") }
private enum class InboxFilter(val label: String) { ALL("Tümü"), BUY("⚡ Al fırsatları"), MINE("Portföyüm"), ALERT("Alarmlar") }

@Composable
fun InboxScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(InboxTab.FEED) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            InboxTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }) }
        }
        when (tab) {
            InboxTab.FEED -> Feed(vm, onOpen)
            InboxTab.ALERTS -> AlertsScreen(vm)
        }
    }
}

@Composable
private fun Feed(vm: MainViewModel, onOpen: (String) -> Unit) {
    val items by vm.inbox.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(InboxFilter.ALL) }
    // Ekrandan çıkınca hepsini okundu say
    DisposableEffect(Unit) { onDispose { vm.markInboxRead() } }

    val list = remember(items, filter) {
        when (filter) {
            InboxFilter.ALL -> items
            InboxFilter.BUY -> items.filter { it.buySymbol != null }
            InboxFilter.MINE -> items.filter { it.kind == InboxKind.PAPER || it.kind == InboxKind.SUMMARY }
            InboxFilter.ALERT -> items.filter { it.kind == InboxKind.ALERT }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InboxFilter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ Al düğmesi ${Format.num(settings.quickBuyAmount)} TL'lik sanal alım yapar" +
                    if (settings.autoProtect) " (otomatik zarar-kes/kâr-al ile)" else "", fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) TextButton(onClick = vm::clearInbox) { Text("Temizle") }
            }
        }
        if (list.isEmpty()) item {
            Text("Burada henüz bir şey yok. Uygulamanın gönderdiği tüm bildirimler (al fırsatları, portföy, alarmlar) burada birikir.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
        }
        items(list, key = { it.id }) { item ->
            Box(Modifier.animateItem().enterAnim()) { InboxCard(item, tickers[item.buySymbol ?: item.symbol]?.price, settings.quickBuyAmount, vm, onOpen) }
        }
    }
}

@Composable
private fun InboxCard(item: InboxItem, priceNow: Double?, amount: Double, vm: MainViewModel, onOpen: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val icon = when (item.kind) {
        InboxKind.SIGNAL -> "📊"; InboxKind.REVERSAL -> "🔄"; InboxKind.MOVE -> "⚡"; InboxKind.VOLUME -> "📈"
        InboxKind.ALERT -> "🔔"; InboxKind.PAPER -> "🎮"; InboxKind.SUMMARY -> "📒"
    }
    Card(Modifier.fillMaxWidth().animateContentSize().bouncyClick { expanded = !expanded }, tint = if (!item.read) Accent.copy(alpha = 0.10f) else null) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold, fontSize = 14.sp)
                    Text(item.text, fontSize = 12.sp, color = Muted, maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis)
                    Text(Format.ago(item.at), fontSize = 10.sp, color = Muted)
                }
            }
            // Bildirimden bu yana ne oldu?
            if (item.priceAt > 0 && priceNow != null) {
                val ch = (priceNow - item.priceAt) / item.priceAt * 100
                Text("Bildirimden beri: ${Format.signedPct(ch)}  (${Format.price(item.priceAt)} → ${Format.price(priceNow)} $)",
                    fontSize = 12.sp, color = changeColor(ch), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                item.buySymbol?.let { sym ->
                    Button(onClick = { vm.quickBuy(sym) }) { Text("⚡ Al (${Format.num(amount)} TL)") }
                    Spacer(Modifier.width(8.dp))
                }
                (item.buySymbol ?: item.symbol)?.let { sym -> TextButton(onClick = { onOpen(sym) }) { Text("Grafik") } }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.deleteInbox(item.id) }) { Text("Sil", color = Muted) }
            }
        }
    }
}
