package com.kriptoradar.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.LedgerEntry
import com.kriptoradar.data.LedgerType
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.components.PctPill
import com.kriptoradar.ui.components.Sparkline
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.changeColor

private fun tl(v: Double) = Format.num(v) + " TL"
private fun signedTl(v: Double) = (if (v >= 0) "+" else "") + Format.num(v) + " TL"

fun orderLabel(k: OrderKind) = when (k) {
    OrderKind.LIMIT_BUY -> "Limit alım"; OrderKind.LIMIT_SELL -> "Limit satış"
    OrderKind.STOP_LOSS -> "Zarar-kes"; OrderKind.TAKE_PROFIT -> "Kâr-al"
}

@Composable
fun PortfolioTab(vm: MainViewModel, onOpen: (String) -> Unit) {
    val p by vm.paper.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val prices = remember(tickers) { vm.paperPrices() }
    val equity = PaperEngine.equity(p, prices)
    val pnl = equity - p.totalDeposited
    val unrealized = p.holdings.sumOf { h -> prices[h.symbol]?.let { h.qty * it - h.costTry } ?: 0.0 }
    val stats = remember(p.ledger) { PaperEngine.stats(p) }
    var trade by remember { mutableStateOf<Pair<String?, Boolean>?>(null) } // (sembol, alım mı)
    var showAllLedger by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("🎮 Sanal Portföy", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Gerçek para yok; gerçek fiyatlarla al-sat, emir ver, kendini test et.", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text("Toplam değer", fontSize = 11.sp, color = Muted)
                    Text(tl(equity), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = changeColor(pnl))
                    Text("${if (pnl >= 0) "Kâr" else "Zarar"}: ${signedTl(pnl)} (${Format.signedPct(if (p.totalDeposited > 0) pnl / p.totalDeposited * 100 else 0.0)})",
                        color = changeColor(pnl), fontWeight = FontWeight.SemiBold)
                    val points = p.equity.map { it.value } + equity
                    if (points.size >= 3) Sparkline(points, Modifier.fillMaxWidth().height(70.dp).padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Kv2("Nakit", tl(p.cash), Modifier.weight(1f))
                        Kv2("Coinlerde", tl(equity - p.cash), Modifier.weight(1f))
                        Kv2("Net yatırılan", tl(p.totalDeposited), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Kv2("Gerçekleşen K/Z", signedTl(p.realizedPnl), Modifier.weight(1f), changeColor(p.realizedPnl))
                        Kv2("Açık pozisyon K/Z", signedTl(unrealized), Modifier.weight(1f), changeColor(unrealized))
                        Kv2("Ödenen komisyon", tl(p.feesPaid), Modifier.weight(1f))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { trade = null to true }) { Text("🛒 Al") }
                        OutlinedButton(onClick = vm::openAdmin) { Text("💰 Para yükle") }
                    }
                }
            }
        }

        item { Text("Coinlerim (${p.holdings.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        if (p.holdings.isEmpty()) item { Text("Henüz coinin yok. \"Al\"a dokun ya da Rehber/Sinyaller'den bir coin seç.", color = Muted, fontSize = 13.sp) }
        items(p.holdings.sortedByDescending { h -> prices[h.symbol]?.let { h.qty * it } ?: h.costTry }, key = { it.symbol }) { h ->
            val price = prices[h.symbol]
            val value = price?.let { h.qty * it } ?: h.costTry
            val hp = value - h.costTry
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onOpen(h.symbol) }) {
                            Text(h.base, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${Format.price(h.qty)} adet • ort. maliyet ${Format.price(h.avgCostTry)} TL", fontSize = 11.sp, color = Muted)
                            Text("Şu an ${price?.let { Format.price(it) + " TL" } ?: "—"}", fontSize = 11.sp, color = Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(tl(value), fontWeight = FontWeight.SemiBold)
                            Text(signedTl(hp), fontSize = 12.sp, color = changeColor(hp))
                            PctPill(if (h.costTry > 0) hp / h.costTry * 100 else 0.0)
                        }
                    }
                    Row {
                        TextButton(onClick = { trade = h.symbol to true }) { Text("Al") }
                        TextButton(onClick = { trade = h.symbol to false }) { Text("Sat / Emir") }
                    }
                }
            }
        }

        if (p.orders.isNotEmpty()) {
            item { Text("⏳ Bekleyen emirler (${p.orders.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
            items(p.orders, key = { "o" + it.id }) { o ->
                val cur = prices[o.symbol]
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${orderLabel(o.kind)} • ${o.base}", fontWeight = FontWeight.SemiBold)
                            Text("Tetik: ${Format.price(o.triggerTry)} TL" + if (o.kind == OrderKind.LIMIT_BUY) " • ${tl(o.amountTry)}" else " • %${(o.fraction * 100).toInt()}'i",
                                fontSize = 12.sp)
                            cur?.let { Text("Şu an ${Format.price(it)} TL (tetiğe ${Format.signedPct((o.triggerTry - it) / it * 100)})", fontSize = 11.sp, color = Muted) }
                        }
                        TextButton(onClick = { vm.paperCancel(o.id) }) { Text("İptal") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("📊 Performansın", fontWeight = FontWeight.Bold)
                    Text("Alım: ${stats.buys} • Satış: ${stats.sells} • Kârlı satış: ${stats.wins}" +
                        if (stats.sells > 0) " (%${stats.wins * 100 / stats.sells})" else "", fontSize = 13.sp)
                    stats.best?.takeIf { it.pnlTry > 0 }?.let { Text("En iyi işlem: ${it.base} ${signedTl(it.pnlTry)}", fontSize = 13.sp, color = Up) }
                    stats.worst?.takeIf { it.pnlTry < 0 }?.let { Text("En kötü işlem: ${it.base} ${signedTl(it.pnlTry)}", fontSize = 13.sp, color = Down) }
                }
            }
        }

        if (p.ledger.isNotEmpty()) {
            item { Text("🧾 Hesap hareketleri", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
            items(if (showAllLedger) p.ledger else p.ledger.take(15), key = { "l" + it.id }) { LedgerRow(it) }
            if (!showAllLedger && p.ledger.size > 15) item { TextButton(onClick = { showAllLedger = true }) { Text("Tümünü göster (${p.ledger.size})") } }
        }
    }

    trade?.let { (sym, isBuy) -> TradeDialog(vm, sym, isBuy) { trade = null } }
}

@Composable
private fun Kv2(k: String, v: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Column(modifier) {
        Text(k, fontSize = 10.sp, color = Muted)
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun LedgerRow(e: LedgerEntry) {
    val (icon, title, color) = when (e.type) {
        LedgerType.DEPOSIT -> Triple("💰", "Para yüklendi", Up)
        LedgerType.WITHDRAW -> Triple("🏧", "Para çekildi", Down)
        LedgerType.BUY -> Triple("🛒", "${e.base} alındı", androidx.compose.ui.graphics.Color.Unspecified)
        LedgerType.SELL -> Triple("💱", "${e.base} satıldı", changeColor(e.pnlTry))
        LedgerType.ADJUST -> Triple("🛠", e.note.ifBlank { "Düzeltme" }, Accent)
        LedgerType.RESET -> Triple("♻️", "Portföy sıfırlandı", Accent)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title + if (e.note.isNotBlank() && e.type in setOf(LedgerType.BUY, LedgerType.SELL, LedgerType.DEPOSIT)) " (${e.note})" else "", fontSize = 13.sp)
            val detail = when (e.type) {
                LedgerType.BUY, LedgerType.SELL -> "${Format.price(e.qty)} × ${Format.price(e.priceTry)} TL • komisyon ${Format.num(e.feeTry)} TL"
                else -> ""
            }
            Text(listOf(detail, Format.ago(e.at)).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 11.sp, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(when (e.type) {
                LedgerType.WITHDRAW, LedgerType.BUY -> "−" + tl(e.amountTry)
                LedgerType.ADJUST -> signedTl(e.amountTry)
                else -> "+" + tl(e.amountTry)
            }, fontSize = 13.sp, color = color)
            if (e.type == LedgerType.SELL) Text("K/Z ${signedTl(e.pnlTry)}", fontSize = 11.sp, color = changeColor(e.pnlTry))
        }
    }
}

private enum class Mode(val label: String, val buy: Boolean) {
    MARKET_BUY("Hemen al", true), LIMIT_BUY("Limit alım", true),
    MARKET_SELL("Hemen sat", false), LIMIT_SELL("Limit satış", false), STOP("Zarar-kes", false), TP("Kâr-al", false),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeDialog(vm: MainViewModel, initialSymbol: String?, buy: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val p by vm.paper.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf(initialSymbol) }
    var search by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if (buy) Mode.MARKET_BUY else Mode.MARKET_SELL) }
    var amountText by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(1.0) }
    var triggerText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val sym = symbol
    val t = sym?.let { tickers[it] }
    val price = sym?.let { vm.priceTry(it) }
    val holding = p.holdings.firstOrNull { it.symbol == sym }
    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val trigger = triggerText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val modes = Mode.entries.filter { it.buy || holding != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t?.let { "${it.base} – sanal işlem" } ?: "Coin seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sym == null) {
                    OutlinedTextField(search, { search = it }, label = { Text("Coin ara (BTC, ETH…)") }, singleLine = true)
                    val q = search.trim().uppercase()
                    val list = tickers.values.filter { it.quote == "USDT" && BinanceApi.isTradable(it.base) && (q.isEmpty() || it.base.startsWith(q)) }
                        .sortedByDescending { it.quoteVolume }.take(8)
                    list.forEach { c ->
                        Text("${c.base} • ${vm.priceTry(c.symbol)?.let { Format.price(it) + " TL" } ?: ""}",
                            Modifier.fillMaxWidth().clickable { symbol = c.symbol }.padding(8.dp))
                    }
                    return@Column
                }
                Text("Fiyat: ${price?.let { Format.price(it) + " TL" } ?: "—"} • Nakit: ${tl(p.cash)}", fontSize = 12.sp, color = Muted)
                holding?.let { Text("Elindeki: ${Format.price(it.qty)} ${it.base} (≈ ${price?.let { pr -> tl(it.qty * pr) } ?: "—"})", fontSize = 12.sp, color = Muted) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { m -> FilterChip(selected = mode == m, onClick = {
                        mode = m; error = null
                        if (price != null) triggerText = when (m) {
                            Mode.STOP -> Format.price(price * 0.95).replace(".", "")
                            Mode.TP -> Format.price(price * 1.10).replace(".", "")
                            Mode.LIMIT_BUY -> Format.price(price * 0.97).replace(".", "")
                            Mode.LIMIT_SELL -> Format.price(price * 1.05).replace(".", "")
                            else -> ""
                        }
                    }, label = { Text(m.label, fontSize = 12.sp) }) }
                }
                if (mode.buy) {
                    OutlinedTextField(amountText, { amountText = it }, label = { Text("Tutar (TL)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.25, 0.5, 1.0).forEach { f ->
                            FilterChip(selected = false, onClick = { amountText = "%.2f".format(java.util.Locale.US, p.cash * f) }, label = { Text("%${(f * 100).toInt()}") })
                        }
                    }
                } else {
                    Text("Ne kadarını?", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.25, 0.5, 0.75, 1.0).forEach { f -> FilterChip(selected = fraction == f, onClick = { fraction = f }, label = { Text("%${(f * 100).toInt()}") }) }
                    }
                }
                if (mode != Mode.MARKET_BUY && mode != Mode.MARKET_SELL) {
                    OutlinedTextField(triggerText, { triggerText = it }, label = { Text("Tetik fiyatı (TL)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text(when (mode) {
                        Mode.LIMIT_BUY -> "Fiyat bu seviyeye İNERSE otomatik alınır."
                        Mode.LIMIT_SELL, Mode.TP -> "Fiyat bu seviyeye ÇIKARSA otomatik satılır."
                        Mode.STOP -> "Fiyat bu seviyeye DÜŞERSE otomatik satılır; kaybını sınırlar."
                        else -> ""
                    }, fontSize = 11.sp, color = Muted)
                }
                // Önizleme
                if (price != null) {
                    val fee = p.feePct / 100; val slip = p.slippagePct / 100
                    val preview = when (mode) {
                        Mode.MARKET_BUY -> if (amount > 0) "≈ ${Format.price(amount * (1 - fee) / (price * (1 + slip)))} ${t?.base} alırsın (komisyon ${Format.num(amount * fee)} TL)" else null
                        Mode.MARKET_SELL -> holding?.let { h ->
                            val net = h.qty * fraction * price * (1 - slip) * (1 - fee)
                            "≈ ${tl(net)} eline geçer • K/Z ${signedTl(net - h.costTry * fraction)}"
                        }
                        Mode.STOP, Mode.TP, Mode.LIMIT_SELL -> holding?.takeIf { trigger > 0 }?.let { h ->
                            val net = h.qty * fraction * trigger * (1 - slip) * (1 - fee)
                            "Tetiklenirse ≈ ${tl(net)} • K/Z ${signedTl(net - h.costTry * fraction)}"
                        }
                        Mode.LIMIT_BUY -> if (amount > 0 && trigger > 0) "Tetiklenirse ≈ ${Format.price(amount * (1 - fee) / (trigger * (1 + slip)))} ${t?.base}" else null
                    }
                    preview?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Accent) }
                }
                error?.let { Text(it, color = Down, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            if (sym != null) TextButton(onClick = {
                val err = when (mode) {
                    Mode.MARKET_BUY -> vm.paperBuy(sym, amount)
                    Mode.MARKET_SELL -> vm.paperSell(sym, fraction)
                    Mode.LIMIT_BUY -> vm.paperOrder(sym, OrderKind.LIMIT_BUY, trigger, amount, 1.0)
                    Mode.LIMIT_SELL -> vm.paperOrder(sym, OrderKind.LIMIT_SELL, trigger, 0.0, fraction)
                    Mode.STOP -> vm.paperOrder(sym, OrderKind.STOP_LOSS, trigger, 0.0, fraction)
                    Mode.TP -> vm.paperOrder(sym, OrderKind.TAKE_PROFIT, trigger, 0.0, fraction)
                }
                if (err != null) error = err
                else {
                    Toast.makeText(ctx, if (mode == Mode.MARKET_BUY || mode == Mode.MARKET_SELL) "İşlem tamam (sanal)" else "Emir kaydedildi", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text("Onayla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
