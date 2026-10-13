package com.kriptoradar.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.kriptoradar.analysis.Indicators
import com.kriptoradar.data.Factor
import com.kriptoradar.data.SuggestionKind
import com.kriptoradar.ui.components.PctPill
import com.kriptoradar.data.ScanResult
import com.kriptoradar.data.Signal
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.components.DISCLAIMER
import com.kriptoradar.ui.components.ScoreBadge
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.changeColor
import com.kriptoradar.ui.theme.scoreColor

private enum class SigFilter(val label: String) {
    TOP("En yüksek skor"), RISING("Skoru artan"), OVERSOLD("Aşırı satım"), VOLUME("Hacim artışı"), HIST("Geçmiş isabet"), WEAK("En zayıf")
}

private enum class SigTab(val label: String) { REVERSAL("Dip Dönüş"), SCORE("Teknik Skor"), TRACK("Öneri Takibi") }

@Composable
fun SignalsScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(SigTab.REVERSAL) }
    var filter by rememberSaveable { mutableStateOf(SigFilter.TOP) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            SigTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
            }
        }
        when (tab) {
            SigTab.REVERSAL -> ReversalTab(scan, scanning, vm::scanNow, onOpen)
            SigTab.TRACK -> SuggestionTracker(vm, onOpen)
            SigTab.SCORE -> {
                val list = remember(scan, filter) {
                    val s = scan?.signals ?: emptyList()
                    when (filter) {
                        SigFilter.TOP -> s.sortedByDescending { it.score }
                        SigFilter.OVERSOLD -> s.filter { it.rsi < 35 }.sortedBy { it.rsi }
                        SigFilter.VOLUME -> s.filter { it.volumeRatio > 1.3 }.sortedByDescending { it.volumeRatio }
                        SigFilter.HIST -> s.filter { it.histWinRate >= 0 && it.histSamples >= 10 }.sortedByDescending { it.histWinRate * 100 + it.histAvgReturn }
                        SigFilter.RISING -> s.filter { (it.scoreDelta ?: 0) > 0 }.sortedByDescending { it.scoreDelta ?: 0 }
                        SigFilter.WEAK -> s.sortedBy { it.score }
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { MarketHeader(scan, scanning, vm::scanNow) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SigFilter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                        }
                    }
                    item { Text(DISCLAIMER, fontSize = 11.sp, color = Muted, lineHeight = 14.sp) }
                    if (scan == null) item {
                        Text(if (scanning) "İlk tarama yapılıyor… (coinlerin 500 saatlik verisi analiz ediliyor)" else "Henüz tarama yok. Yenile'ye dokunun.", color = Muted)
                    }
                    if (scan != null && list.isEmpty()) item { Text("Bu filtreye uyan coin yok.", color = Muted) }
                    items(list, key = { it.symbol }) { SignalCard(it, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun ReversalTab(scan: ScanResult?, scanning: Boolean, onScan: () -> Unit, onOpen: (String) -> Unit) {
    val all = scan?.signals?.filter { it.reversal != null } ?: emptyList()
    val candidates = all.filter { it.reversal!!.candidate }.sortedByDescending { it.reversal!!.score }
    // Adaya yakın olanlar: çok düşük ama dönüş henüz tam teyit edilmedi
    val watch = all.filter { val r = it.reversal!!; !r.candidate && r.depth >= 0.45 && r.drawdown30 >= 15 }
        .sortedByDescending { it.reversal!!.turn }.take(15)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dip Dönüş Dedektörü", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Çok düşmüş ama grafiği yukarı dönmeye başlamış coinleri otomatik bulur.", fontSize = 12.sp, color = Muted)
                            Text("Son tarama: ${Format.ago(scan?.at ?: 0)} • ${scan?.signals?.size ?: 0} coin tarandı", fontSize = 11.sp, color = Muted)
                        }
                        if (scanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = onScan) { Icon(Icons.Default.Refresh, "Tara") }
                    }
                    if (scan != null && scan.revBaseWinRate >= 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Geçmiş istatistik: bu desen taranan coinlerde son ~3 haftada ${scan.revBaseSamples} kez oluştu; " +
                                "24 saat sonra fiyat %${(scan.revBaseWinRate * 100).toInt()} oranında daha yüksekti " +
                                "(ortalama ${Format.signedPct(scan.revBaseAvgReturn)}).",
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Ölçütler: 30g zirveden düşüş, 30g aralıktaki konum, günlük RSI dibi + RSI toparlanması, EMA9/21 kesişimi, " +
                        "MACD dönüşü, daha yüksek dip, RSI uyumsuzluğu, 24s regresyon eğimi, alış hacmi.", fontSize = 11.sp, color = Muted)
                }
            }
        }
        item { Text(DISCLAIMER, fontSize = 11.sp, color = Muted, lineHeight = 14.sp) }
        if (scan == null) item { Text(if (scanning) "Taranıyor…" else "Henüz tarama yok. Yenile'ye dokunun.", color = Muted) }
        if (scan != null) item {
            Text("✅ Adaylar (${candidates.size})", fontWeight = FontWeight.Bold, color = Up)
        }
        if (scan != null && candidates.isEmpty()) item {
            Text("Şu an tüm koşulları sağlayan coin yok. Yeni aday çıktığında bildirim gelir.", color = Muted, fontSize = 13.sp)
        }
        items(candidates, key = { "c" + it.symbol }) { ReversalCard(it, onOpen) }
        if (watch.isNotEmpty()) {
            item { Text("👀 İzleme listesi – çok düşük, dönüş henüz teyit edilmedi", fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.padding(top = 8.dp)) }
            items(watch, key = { "w" + it.symbol }) { ReversalCard(it, onOpen) }
        }
    }
}

@Composable
private fun ReversalCard(s: Signal, onOpen: (String) -> Unit) {
    val r = s.reversal ?: return
    var expanded by remember { mutableStateOf(r.candidate) }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreBadge(r.score, if (r.candidate) Up else Accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.base, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("30g zirveden -%${Format.num(r.drawdown30)} • dipten +%${Format.num(r.bounceFromLow)}", fontSize = 12.sp, color = Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.price(s.price), fontWeight = FontWeight.SemiBold)
                    Text(Format.signedPct(s.change24h), color = changeColor(s.change24h), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Meter("Düşüş derinliği", r.depth, Down)
            Meter("Dönüş gücü", r.turn, Up)
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                r.reasons.forEach { Text("• $it", fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp)) }
                if (r.histWinRate >= 0) Text(
                    "Bu coinde aynı desen ${r.histSamples} kez oluştu: 24s sonra %${(r.histWinRate * 100).toInt()} yükseliş, ort. ${Format.signedPct(r.histAvgReturn)}",
                    fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = { onOpen(s.symbol) }) { Text("Grafiği ve detayı aç →") }
            }
        }
    }
}

@Composable
private fun Meter(label: String, v: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = Muted, modifier = Modifier.width(100.dp))
        LinearProgressIndicator(progress = { v.toFloat().coerceIn(0f, 1f) }, Modifier.weight(1f).height(6.dp), color = color, trackColor = Color(0x22FFFFFF))
        Text(" ${(v * 100).toInt()}", fontSize = 11.sp, modifier = Modifier.width(32.dp))
    }
}

@Composable
private fun SuggestionTracker(vm: MainViewModel, onOpen: (String) -> Unit) {
    val recs by vm.suggestions.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val rows = recs.mapNotNull { r -> tickers[r.symbol]?.let { r to Indicators.pctChange(r.price, it.price) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Öneri Takibi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Uygulamanın bildirdiği her coin, bildirildiği andaki fiyatla kaydedilir ve canlı olarak takip edilir. " +
                        "Böylece yöntemin gerçekte ne kadar tuttuğunu kendiniz görürsünüz.", fontSize = 12.sp, color = Muted)
                    SuggestionKind.entries.forEach { k ->
                        val g = rows.filter { it.first.kind == k }
                        if (g.isNotEmpty()) {
                            val win = g.count { it.second > 0 } * 100 / g.size
                            Text(
                                "${if (k == SuggestionKind.REVERSAL) "Dip dönüş" else "Güçlü skor"}: ${g.size} öneri • kârda %$win • ort. ${Format.signedPct(g.map { it.second }.average())}",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    if (recs.isNotEmpty()) TextButton(onClick = vm::clearSuggestions) { Text("Geçmişi temizle") }
                }
            }
        }
        if (rows.isEmpty()) item { Text("Henüz kayıt yok. Yeni dip dönüş adayı veya güçlü sinyal bildirildiğinde burada görünür.", color = Muted) }
        items(rows, key = { it.first.symbol + it.first.at }) { (r, ret) ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(r.symbol) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.base, fontWeight = FontWeight.Bold)
                        Text("${if (r.kind == SuggestionKind.REVERSAL) "🔄 Dip dönüş" else "📊 Güçlü skor"} (${r.score}) • ${Format.ago(r.at)}", fontSize = 11.sp, color = Muted)
                        Text("Bildirim fiyatı: ${Format.price(r.price)}", fontSize = 11.sp, color = Muted)
                    }
                    PctPill(ret)
                }
            }
        }
    }
}

@Composable
private fun MarketHeader(scan: ScanResult?, scanning: Boolean, onScan: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Piyasa Nabzı", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Son tarama: ${Format.ago(scan?.at ?: 0)} • ${scan?.signals?.size ?: 0} coin", fontSize = 11.sp, color = Muted)
                }
                if (scanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = onScan) { Icon(Icons.Default.Refresh, "Tara") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val fg = scan?.fearGreed
                Stat("Korku/Açgözlülük", fg?.toString() ?: "—", scan?.fearGreedLabel ?: "",
                    when { fg == null -> Muted; fg < 25 -> Down; fg < 45 -> Color(0xFFFF9800); fg < 55 -> Accent; else -> Up }, Modifier.weight(1f))
                Stat("BTC dominansı", scan?.btcDominance?.let { "%" + Format.num(it) } ?: "—", "", Color.White, Modifier.weight(1f))
                val mc = scan?.totalMcapChange24h
                Stat("Toplam PD 24s", mc?.let { Format.signedPct(it) } ?: "—", "", changeColor(mc ?: 0.0), Modifier.weight(1f))
            }
            scan?.signals?.takeIf { it.isNotEmpty() }?.let { s ->
                Spacer(Modifier.height(10.dp))
                val avg = s.map { it.score }.average()
                val bull = s.count { it.score >= 60 }
                Text("Ortalama teknik skor: ${avg.toInt()} • Olumlu görünüm: $bull/${s.size}", fontSize = 12.sp, color = Muted)
                LinearProgressIndicator(progress = { (avg / 100).toFloat() }, Modifier.fillMaxWidth().padding(top = 4.dp), color = scoreColor(avg.toInt()))
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, sub: String, color: Color, modifier: Modifier) {
    Column(modifier.background(Color(0x10FFFFFF), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(title, fontSize = 10.sp, color = Muted)
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
        if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, color = color)
    }
}

@Composable
private fun SignalCard(s: Signal, onOpen: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreBadge(s.score, scoreColor(s.score))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.base, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(s.label, fontSize = 12.sp, color = scoreColor(s.score))
                    s.scoreDelta?.takeIf { it != 0 }?.let { d ->
                        Text("${if (d > 0) "▲" else "▼"} ${kotlin.math.abs(d)} (önceki taramaya göre)", fontSize = 11.sp, color = if (d > 0) Up else Down)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.price(s.price), fontWeight = FontWeight.SemiBold)
                    Text(Format.signedPct(s.change24h), color = changeColor(s.change24h), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mini("RSI", s.rsi.toInt().toString())
                Mini("Hacim", Format.num(s.volumeRatio) + "x")
                Mini("ATR", "%" + Format.num(s.volatilityPct))
                if (s.histWinRate >= 0) Mini("Geçmiş isabet", "%${(s.histWinRate * 100).toInt()} (${s.histSamples})")
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                s.factors.forEach { FactorRow(it) }
                if (s.histWinRate >= 0) {
                    Text(
                        "Son ~500 saatte benzer teknik koşul ${s.histSamples} kez oluştu; 12 saat sonra fiyat " +
                            "%${(s.histWinRate * 100).toInt()} oranında daha yüksekti (ort. getiri ${Format.signedPct(s.histAvgReturn)}). " +
                            "Geçmiş, geleceğin garantisi değildir.",
                        fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(onClick = { onOpen(s.symbol) }) { Text("Grafiği ve detayı aç →") }
            }
        }
    }
}

@Composable
private fun Mini(k: String, v: String) {
    Column {
        Text(k, fontSize = 10.sp, color = Muted)
        Text(v, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FactorRow(f: Factor) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(f.detail, fontSize = 11.sp, color = Muted)
        }
        val c = if (f.score > 0.1) Up else if (f.score < -0.1) Down else Muted
        Text((if (f.score > 0) "+" else "") + Format.num(f.score), color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
