package com.kriptoradar.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.Holding
import com.kriptoradar.data.LedgerType
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperOrder
import com.kriptoradar.data.PaperPortfolio
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.components.AnimatedBar
import com.kriptoradar.ui.components.AnimatedNumber
import com.kriptoradar.ui.components.Card
import com.kriptoradar.ui.components.PctPill
import com.kriptoradar.ui.components.Sparkline
import com.kriptoradar.ui.components.bouncyClick
import com.kriptoradar.ui.components.enterAnim
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.changeColor

private fun tl(v: Double) = Format.num(v) + " TL"
private fun stl(v: Double) = (if (v >= 0) "+" else "") + Format.num(v) + " TL"
private enum class TrackSort(val label: String) { PNL("Kâra göre"), VALUE("Değere göre"), NEW("En yeni") }

/** Sade ve detaylı kâr/zarar takibi: aldığın her coin tek kartta. */
@Composable
fun TrackScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val p by vm.paper.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val prices = remember(tickers) { vm.paperPrices() }
    var sort by rememberSaveable { mutableStateOf(TrackSort.PNL) }
    var trade by remember { mutableStateOf<Pair<String?, Boolean>?>(null) }

    val invested = p.holdings.sumOf { it.costTry }
    val value = p.holdings.sumOf { h -> prices[h.symbol]?.let { h.qty * it } ?: h.costTry }
    val open = value - invested
    val sells = p.ledger.filter { it.type == LedgerType.SELL }
    val list = when (sort) {
        TrackSort.PNL -> p.holdings.sortedByDescending { h -> prices[h.symbol]?.let { (h.qty * it - h.costTry) / h.costTry } ?: 0.0 }
        TrackSort.VALUE -> p.holdings.sortedByDescending { h -> prices[h.symbol]?.let { h.qty * it } ?: h.costTry }
        TrackSort.NEW -> p.holdings.sortedByDescending { it.openedAt }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth().enterAnim(0)) {
                Column(Modifier.padding(14.dp)) {
                    Text("📈 Takip ettiğin coinler", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Koyduğun", fontSize = 12.sp, color = Muted)
                            AnimatedNumber(invested, { tl(it) }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("→", color = Muted, fontSize = 20.sp)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Şu an", fontSize = 12.sp, color = Muted)
                            AnimatedNumber(value, { tl(it) }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = changeColor(open))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth(), tint = changeColor(open).copy(alpha = 0.15f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (open >= 0) "Açık kârın" else "Açık zararın", fontSize = 12.sp, color = Muted)
                            AnimatedNumber(open, { stl(it) }, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = changeColor(open))
                            Text(Format.signedPct(if (invested > 0) open / invested * 100 else 0.0), color = changeColor(open), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Satıp cebe koyduğun", Modifier.weight(1f), fontSize = 12.sp, color = Muted)
                        Text(stl(p.realizedPnl), fontSize = 12.sp, color = changeColor(p.realizedPnl), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Hepsi birlikte (açık + kapanan)", Modifier.weight(1f), fontSize = 12.sp, color = Muted)
                        Text(stl(open + p.realizedPnl), fontSize = 12.sp, color = changeColor(open + p.realizedPnl), fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Boştaki nakit", Modifier.weight(1f), fontSize = 12.sp, color = Muted)
                        Text(tl(p.cash), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackSort.entries.forEach { s -> FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) }) }
            }
        }
        if (p.holdings.isEmpty()) item {
            Text("Henüz takip ettiğin coin yok. Bildirim kutusundaki ⚡ Al düğmesiyle, Rehber'den ya da Simülatör'den sanal alım yapınca burada görünür.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        items(list, key = { it.symbol }) { h ->
            Box(Modifier.animateItem().enterAnim()) {
                HoldingCard(h, prices[h.symbol], p, vm, onOpen, onTrade = { buy -> trade = h.symbol to buy })
            }
        }
        if (sells.isNotEmpty()) {
            item {
                val wins = sells.count { it.pnlTry > 0 }
                Text("Kapanan işlemler • ${sells.size} satış, ${wins} kârlı (%${wins * 100 / sells.size})",
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            items(sells.take(30), key = { "s" + it.id }) { e ->
                Row(Modifier.fillMaxWidth().animateItem().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.base} • ${Format.price(e.qty)} adet", fontSize = 13.sp)
                        Text("${Format.price(e.priceTry)} TL'den satıldı • ${Format.ago(e.at)}" + if (e.note.isNotBlank()) " • ${e.note}" else "",
                            fontSize = 11.sp, color = Muted)
                    }
                    Text(stl(e.pnlTry), color = changeColor(e.pnlTry), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
    trade?.let { (sym, isBuy) -> TradeDialog(vm, sym, isBuy) { trade = null } }
    val party by vm.celebrate.collectAsStateWithLifecycle()
    com.kriptoradar.ui.components.ConfettiBurst(party?.first)
    androidx.compose.runtime.LaunchedEffect(party) { party?.let { vm.toast.value = "🎉 Kârla sattın: ${stl(it.second)} (sanal)" } }
}

@Composable
private fun HoldingCard(h: Holding, price: Double?, p: PaperPortfolio, vm: MainViewModel, onOpen: (String) -> Unit, onTrade: (Boolean) -> Unit) {
    var expanded by rememberSaveable(h.symbol) { mutableStateOf(false) }
    val value = price?.let { h.qty * it } ?: h.costTry
    val pnl = value - h.costTry
    val pct = if (h.costTry > 0) pnl / h.costTry * 100 else 0.0
    val fee = p.feePct / 100; val slip = p.slippagePct / 100
    val netIfSold = value * (1 - slip) * (1 - fee)
    // Başa baş: komisyon ve kayma sonrası yatırdığını geri aldığın satış fiyatı
    val breakEven = if (h.qty > 0) h.costTry / (h.qty * (1 - slip) * (1 - fee)) else 0.0
    val orders = p.orders.filter { it.symbol == h.symbol }
    val hist = remember(price) { MarketRepository.history(h.symbol)?.snapshot()?.map { it.second }?.takeLast(240) ?: emptyList() }
    val ch24 = vm.tickers.value[h.symbol]?.change24hPct

    Card(Modifier.fillMaxWidth().bouncyClick { expanded = !expanded }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.base, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${tl(h.costTry)} koydun → ${tl(value)}", fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedNumber(pnl, { stl(it) }, fontWeight = FontWeight.Bold, color = changeColor(pnl))
                    PctPill(pct)
                }
            }
            if (hist.size >= 3) Sparkline(hist, Modifier.fillMaxWidth().height(44.dp).padding(top = 8.dp), animate = false)
            // Zarar-kes ile kâr-al arasında fiyatın nerede olduğu
            val stop = orders.firstOrNull { it.kind == OrderKind.STOP_LOSS }
            val take = orders.firstOrNull { it.kind == OrderKind.TAKE_PROFIT }
            if (price != null && stop != null && take != null && take.triggerTry > stop.triggerTry) {
                val f = ((price - stop.triggerTry) / (take.triggerTry - stop.triggerTry)).toFloat()
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🛑", fontSize = 12.sp)
                    AnimatedBar(f, if (f > 0.5f) Up else Down, Modifier.weight(1f).padding(horizontal = 6.dp))
                    Text("🎯", fontSize = 12.sp)
                }
                Text("Zarar-kes ${Format.price(stop.triggerTry)} ←  şu an  → Kâr-al ${Format.price(take.triggerTry)} TL", fontSize = 10.sp, color = Muted)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    Line("Adet", Format.price(h.qty))
                    Line("Ortalama alış fiyatı", "${Format.price(h.avgCostTry)} TL")
                    Line("Şu anki fiyat", price?.let { "${Format.price(it)} TL" } ?: "—")
                    ch24?.let { Line("Son 24 saat", Format.signedPct(it), changeColor(it)) }
                    Line("Başa baş fiyatı", "${Format.price(breakEven)} TL" + (price?.let { "  (${Format.signedPct((breakEven - it) / it * 100)})" } ?: ""))
                    Line("Şimdi satarsan eline geçer", tl(netIfSold), Accent)
                    Line("Şimdi satarsan net kâr/zarar", stl(netIfSold - h.costTry), changeColor(netIfSold - h.costTry))
                    Line("Ne zamandır elinde", Format.ago(h.openedAt).replace(" önce", ""))
                    orders.forEach { o -> Line("⏳ ${orderLabel(o.kind)}", "${Format.price(o.triggerTry)} TL" + (price?.let { " (${Format.signedPct((o.triggerTry - it) / it * 100)})" } ?: "")) }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { vm.paperSell(h.symbol, 1.0)?.let { vm.toast.value = it } }) { Text("Hepsini sat") }
                        OutlinedButton(onClick = { vm.paperSell(h.symbol, 0.5)?.let { vm.toast.value = it } }) { Text("Yarısı") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { vm.protect(h.symbol) }) { Text("🛡️ Kârı koru") }
                        TextButton(onClick = { onTrade(false) }) { Text("Emir") }
                        TextButton(onClick = { onTrade(true) }) { Text("Daha al") }
                        TextButton(onClick = { onOpen(h.symbol) }) { Text("Grafik") }
                    }
                }
            }
            if (!expanded) Text("Detay için dokun", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Line(k: String, v: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, Modifier.weight(1f), fontSize = 13.sp, color = Muted)
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
