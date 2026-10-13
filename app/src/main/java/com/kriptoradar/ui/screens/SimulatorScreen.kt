package com.kriptoradar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.kriptoradar.ui.components.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.analysis.DayPrice
import com.kriptoradar.analysis.Simulator
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.analysis.MonteCarlo
import com.kriptoradar.ui.components.AnimatedBar
import com.kriptoradar.ui.components.AnimatedNumber
import com.kriptoradar.ui.components.MonteCarloChart
import com.kriptoradar.ui.components.ScoreRing
import com.kriptoradar.ui.components.Shimmer
import com.kriptoradar.ui.components.ShimmerList
import com.kriptoradar.ui.components.animatedDouble
import com.kriptoradar.ui.components.enterAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.changeColor

private enum class SimTab(val label: String) { CALC("Ne olurdu?"), PAPER("Sanal Portföy") }

@Composable
fun SimulatorScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SimTab.CALC) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            SimTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }) }
        }
        when (tab) {
            SimTab.CALC -> CalcTab(vm) { tab = SimTab.PAPER }
            SimTab.PAPER -> PortfolioTab(vm, onOpen)
        }
    }
}

private fun money(v: Double, tl: Boolean) = if (tl) Format.num(v) + " TL" else "$" + Format.num(v)

@Composable
private fun CalcTab(vm: MainViewModel, goPaper: () -> Unit) {
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val symbol by vm.simSymbol.collectAsStateWithLifecycle()
    var coinText by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("1000") }
    var tl by rememberSaveable { mutableStateOf(true) }
    var feeText by rememberSaveable { mutableStateOf("0.2") }
    var horizon by rememberSaveable { mutableStateOf(7) }
    var message by remember { mutableStateOf<String?>(null) }

    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val fee = feeText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 5.0) ?: 0.2
    val t = tickers[symbol]
    val rate = tickers["USDTTRY"]?.price
    val useTl = tl && rate != null
    val priceNow = t?.let { if (useTl) it.price * rate!! else it.price }

    val suggestions = remember(coinText, tickers.size) {
        val q = coinText.trim().uppercase()
        if (q.isEmpty()) emptyList()
        else tickers.values.filter { it.quote == "USDT" && it.base.startsWith(q) && BinanceApi.isTradable(it.base) }
            .sortedByDescending { it.quoteVolume }.take(5)
    }

    // Günlük geçmiş veri (coin + dolar/TL kuru)
    val series by produceState<List<DayPrice>?>(null, symbol, useTl) {
        value = null
        value = runCatching {
            val coin = BinanceApi.klines(symbol, "1d", 1000)
            val r = if (useTl) BinanceApi.klines("USDTTRY", "1d", 1000) else null
            Simulator.series(coin, r)
        }.getOrDefault(emptyList())
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth().enterAnim(0)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ne kadar koysam ne olurdu?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Seçili coin: ${t?.base ?: symbol.removeSuffix("USDT")}", color = Accent, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BTC", "ETH", "SOL", "XRP", "BNB").forEach { b ->
                            FilterChip(selected = symbol == b + "USDT", onClick = { vm.simSymbol.value = b + "USDT" }, label = { Text(b, fontSize = 12.sp) })
                        }
                    }
                    OutlinedTextField(coinText, { coinText = it }, Modifier.fillMaxWidth(), label = { Text("Başka coin ara") }, singleLine = true)
                    suggestions.forEach { s ->
                        Text("${s.base} • ${Format.price(s.price)} $", Modifier.fillMaxWidth().clickable { vm.simSymbol.value = s.symbol; coinText = "" }.padding(6.dp), color = Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(amountText, { amountText = it }, Modifier.weight(1f), label = { Text("Tutar") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        FilterChip(selected = tl, onClick = { tl = true }, label = { Text("TL") })
                        FilterChip(selected = !tl, onClick = { tl = false }, label = { Text("$") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("500", "1000", "5000", "10000").forEach { a -> FilterChip(selected = amountText == a, onClick = { amountText = a }, label = { Text(a, fontSize = 12.sp) }) }
                    }
                    OutlinedTextField(feeText, { feeText = it }, Modifier.width(160.dp), label = { Text("Komisyon %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (tl && rate == null) Text("Dolar/TL kuru henüz yüklenmedi; hesaplar dolar cinsinden.", fontSize = 11.sp, color = Accent)
                }
            }
        }

        // 1) Şimdi alsan
        if (t != null && priceNow != null && amount > 0) item {
            val qty = Simulator.qtyFor(amount, priceNow, fee)
            Card(Modifier.fillMaxWidth().enterAnim(1)) {
                Column(Modifier.padding(14.dp)) {
                    Text("🛒 Şimdi alsan", fontWeight = FontWeight.Bold)
                    Text("${money(amount, useTl)} ile yaklaşık", fontSize = 13.sp, color = Muted)
                    AnimatedNumber(qty, { "${Format.price(it)} ${t.base}" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                    Text("alırsın. (1 ${t.base} = ${money(priceNow, useTl)}, komisyon ≈ ${money(amount * fee / 100, useTl)})", fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    val step = priceNow * 0.10
                    val aq = animatedDouble(qty)
                    Text("Fiyat %10 yükselirse: ${money(aq * (priceNow + step), useTl)} (+${money(aq * step, useTl)})", fontSize = 13.sp, color = Up)
                    Text("Fiyat %10 düşerse: ${money(aq * (priceNow - step), useTl)} (−${money(aq * step, useTl)})", fontSize = 13.sp, color = Down)
                    Text("Fiyat %50 düşerse (kriptoda olağan): ${money(aq * priceNow * 0.5, useTl)}", fontSize = 13.sp, color = Down)
                    if (useTl) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = {
                            message = vm.paperBuy(symbol, amount) ?: "${Format.num(amount)} TL'lik sanal ${t.base} alındı. Sanal Portföy'den takip et."
                        }) { Text("Sanal portföyde dene (gerçek para değil)") }
                    }
                    message?.let { Text(it, fontSize = 12.sp, color = Accent, modifier = Modifier.clickable { goPaper() }) }
                }
            }
        }

        val s = series
        if (s == null) item { ShimmerList(3, 120.dp) }
        else if (s.isNotEmpty() && priceNow != null && amount > 0) {
            // 2) Geçmişte koysaydın
            item {
                Card(Modifier.fillMaxWidth().enterAnim(2)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⏪ Geçmişte koysaydın, bugün ne olurdu?", fontWeight = FontWeight.Bold)
                        Text("${money(amount, useTl)} yatırsaydın bugünkü değeri:", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(6.dp))
                        val rows = listOf(7 to "1 hafta önce", 30 to "1 ay önce", 90 to "3 ay önce", 180 to "6 ay önce", 365 to "1 yıl önce", 730 to "2 yıl önce")
                            .mapNotNull { (d, l) -> Simulator.whatIf(s, d, amount, fee, priceNow)?.let { l to it } }
                        val maxV = (rows.maxOfOrNull { it.second.valueNow } ?: amount).coerceAtLeast(amount)
                        rows.forEachIndexed { i, (l, w) ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(l, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("En kötü anda: ${money(w.worstValue, useTl)} • en iyi: ${money(w.bestValue, useTl)}", fontSize = 11.sp, color = Muted)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        AnimatedNumber(w.valueNow, { money(it, useTl) }, fontWeight = FontWeight.SemiBold, color = changeColor(w.pnl), durationMs = 900)
                                        Text((if (w.pnl >= 0) "+" else "") + money(w.pnl, useTl), fontSize = 11.sp, color = changeColor(w.pnl))
                                    }
                                }
                                AnimatedBar((w.valueNow / maxV).toFloat(), changeColor(w.pnl), Modifier.padding(top = 4.dp), height = 5.dp, delayMs = i * 90)
                            }
                        }
                        Text("\"En kötü anda\": o süre içinde paranın düştüğü en düşük değer. O an panikle satsaydın bu kadar zarar ederdin.",
                            fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            // 3) DCA
            item {
                val d = Simulator.dca(s, 365, 7, amount, fee, priceNow)
                if (d != null) Card(Modifier.fillMaxWidth().enterAnim(3)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("📅 Tek seferde mi, parça parça mı?", fontWeight = FontWeight.Bold)
                        Text("Son 1 yılda ${money(amount, useTl)}'yi ${d.buys} haftaya bölüp her hafta ${money(amount / d.buys, useTl)} alsaydın:", fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Parça parça (DCA)", fontSize = 11.sp, color = Muted)
                                AnimatedNumber(d.valueNow, { money(it, useTl) }, fontWeight = FontWeight.Bold, color = changeColor(d.pnl))
                                AnimatedBar((d.valueNow / maxOf(d.valueNow, d.lumpValueNow)).toFloat(), changeColor(d.pnl), Modifier.padding(vertical = 3.dp, horizontal = 0.dp).padding(end = 12.dp))
                                Text(Format.signedPct(d.pct), fontSize = 11.sp, color = changeColor(d.pnl))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Tek seferde (1 yıl önce)", fontSize = 11.sp, color = Muted)
                                val lp = (d.lumpValueNow - amount) / amount * 100
                                AnimatedNumber(d.lumpValueNow, { money(it, useTl) }, fontWeight = FontWeight.Bold, color = changeColor(lp))
                                AnimatedBar((d.lumpValueNow / maxOf(d.valueNow, d.lumpValueNow)).toFloat(), changeColor(lp), Modifier.padding(vertical = 3.dp).padding(end = 12.dp), delayMs = 150)
                                Text(Format.signedPct(lp), fontSize = 11.sp, color = changeColor(lp))
                            }
                        }
                        Text("Parça parça almak, \"yanlış zamanda hepsini aldım\" riskini azaltır. Yükselen piyasada tek seferde almak, düşen piyasada parça parça almak daha iyi sonuç verir.",
                            fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            // 4) Olası senaryolar + Monte Carlo
            item {
                var seed by rememberSaveable { mutableStateOf(1L) }
                val mc by produceState<MonteCarlo?>(null, s, horizon, amount, fee, seed) {
                    value = withContext(Dispatchers.Default) { Simulator.monteCarlo(s, horizon, amount, fee, seed) }
                }
                Card(Modifier.fillMaxWidth().enterAnim(4), tint = Accent.copy(alpha = 0.08f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🔮 Şimdi koyarsan ne olabilir?", fontWeight = FontWeight.Bold)
                        Text("Monte Carlo simülasyonu: bu coinin son 2 yıldaki gerçek günlük hareketlerinden rastgele seçilerek 2000 olası gelecek üretildi. Grafikte 60 tanesi akıyor.",
                            fontSize = 12.sp, color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            listOf(1 to "1 gün", 7 to "1 hafta", 30 to "1 ay", 90 to "3 ay").forEach { (h, l) ->
                                FilterChip(selected = horizon == h, onClick = { horizon = h }, label = { Text(l, fontSize = 12.sp) })
                            }
                        }
                        val m = mc
                        if (m == null) Shimmer(height = 220.dp)
                        else {
                            MonteCarloChart(m, amount, animKey = listOf(seed, horizon, amount, s.lastOrNull()?.price), format = { money(it, useTl) })
                            Text("Yeşil yollar kârla, kırmızılar zararla biten senaryolar. Sarı çizgi ortadaki sonuç, kesikli beyaz çizgi yatırdığın para.",
                                fontSize = 10.sp, color = Muted)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                ScoreRing((m.lossProbability * 100).toInt(), if (m.lossProbability > 0.5) Down else Accent, size = 56.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Zarar etme ihtimali", fontWeight = FontWeight.Bold)
                                    Text("${m.sims} simülasyonun %${(m.lossProbability * 100).toInt()}'i zararla bitti.", fontSize = 12.sp, color = Muted)
                                }
                                OutlinedButton(onClick = { seed++ }) { Text("🎲 Tekrar") }
                            }
                        }
                        val f = Simulator.forecast(s, horizon, amount, fee)
                        if (f == null) Text("Bu coin için yeterli geçmiş veri yok.", color = Muted)
                        else {
                            Spacer(Modifier.height(8.dp))
                            Text("Geçmişteki gerçek ${when (horizon) { 1 -> "1 günlük"; 7 -> "1 haftalık"; 30 -> "1 aylık"; else -> "3 aylık" }} dönemlere göre:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Scenario("😱 Çok kötü (20'de 1 ihtimal)", f.p5, amount, useTl)
                            Scenario("😟 Kötü (10'da 1 ihtimal)", f.p10, amount, useTl)
                            Scenario("😐 Ortadaki sonuç", f.p50, amount, useTl)
                            Scenario("🙂 İyi (10'da 1 ihtimal)", f.p90, amount, useTl)
                            Scenario("🤩 Çok iyi (20'de 1 ihtimal)", f.p95, amount, useTl)
                            RangeBar(f.p10, f.p50, f.p90, amount)
                            Text("Yani ${money(amount, useTl)}, ${horizonText(horizon)} sonra 10'da 8 ihtimalle ${money(f.p10, useTl)} ile ${money(f.p90, useTl)} arasında olurdu (${f.samples} dönem incelendi). " +
                                "Geçmiş, geleceğin garantisi değildir; daha kötü sonuçlar da mümkündür.",
                                fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun horizonText(d: Int) = when (d) { 1 -> "1 gün"; 7 -> "1 hafta"; 30 -> "1 ay"; else -> "3 ay" }

@Composable
private fun Scenario(label: String, value: Double, amount: Double, tl: Boolean) {
    val pnl = value - amount
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        AnimatedNumber(value, { money(it, tl) }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = changeColor(pnl))
        Text("  " + (if (pnl >= 0) "+" else "") + Format.num(pnl / amount * 100) + "%", fontSize = 11.sp, color = changeColor(pnl), modifier = Modifier.width(64.dp))
    }
}

/** Kötü–orta–iyi aralığını yatay çubukla gösterir; dikey çizgi yatırılan tutar. */
@Composable
private fun RangeBar(lo: Double, mid: Double, hi: Double, invested: Double) {
    val min = minOf(lo, invested) * 0.95; val max = maxOf(hi, invested) * 1.05
    val aLo = animatedDouble(lo, 900); val aHi = animatedDouble(hi, 900)
    fun frac(v: Double) = ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(18.dp).padding(top = 8.dp).background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))) {
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(frac(aLo).coerceAtLeast(0.001f)))
            Box(Modifier.weight((frac(aHi) - frac(aLo)).coerceAtLeast(0.01f)).fillMaxHeight()
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Down, Accent, Up)), RoundedCornerShape(4.dp)))
            Spacer(Modifier.weight((1f - frac(aHi)).coerceAtLeast(0.001f)))
        }
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(frac(invested).coerceAtLeast(0.001f)))
            Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White))
            Spacer(Modifier.weight((1f - frac(invested)).coerceAtLeast(0.001f)))
        }
    }
    Text("Beyaz çizgi: yatırdığın tutar", fontSize = 10.sp, color = Muted)
}
