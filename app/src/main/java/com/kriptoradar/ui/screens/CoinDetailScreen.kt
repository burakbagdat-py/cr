package com.kriptoradar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import com.kriptoradar.ui.components.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.analysis.Indicators
import com.kriptoradar.analysis.SignalEngine
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.Kline
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.Signal
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.components.CandleChart
import com.kriptoradar.ui.components.RollingText
import com.kriptoradar.ui.components.DISCLAIMER
import com.kriptoradar.ui.components.ScoreBadge
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.changeColor
import com.kriptoradar.ui.theme.scoreColor
import kotlinx.coroutines.delay

private val intervals = listOf("5m" to "5dk", "15m" to "15dk", "1h" to "1s", "4h" to "4s", "1d" to "1g")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(vm: MainViewModel, symbol: String, onBack: () -> Unit) {
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val t = tickers[symbol]
    var interval by rememberSaveable { mutableStateOf("1h") }
    var showAlert by remember { mutableStateOf(false) }

    // Grafik verisi: seçili aralıkta, 30 sn'de bir tazelenir
    val klines by produceState<List<Kline>>(emptyList(), symbol, interval) {
        while (true) {
            runCatching { value = BinanceApi.klines(symbol, interval, 120) }
            delay(30_000)
        }
    }
    val ema = remember(klines) { if (klines.size >= 20) Indicators.ema(klines.map { it.close }, 20) else null }

    // İstatistiksel analiz
    var signal by remember(symbol) { mutableStateOf<Signal?>(null) }
    var analysisError by remember(symbol) { mutableStateOf(false) }
    LaunchedEffect(symbol, t != null) {
        val tk = MarketRepository.tickers.value[symbol] ?: return@LaunchedEffect
        val res = runCatching {
            val h1 = BinanceApi.klines(symbol, "1h", 500)
            val d1 = BinanceApi.klines(symbol, "1d", 120)
            val btc = MarketRepository.tickers.value["BTCUSDT"]?.change24hPct ?: 0.0
            SignalEngine.analyze(tk, h1, d1, btc)
        }
        signal = res.getOrNull()
        analysisError = res.isFailure || signal == null
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            title = { Text(t?.let { "${it.base}/${it.quote}" } ?: symbol, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            actions = {
                IconButton(onClick = { showAlert = true }) { Icon(Icons.Default.Notifications, "Alarm") }
                IconButton(onClick = { vm.toggleFav(symbol) }) {
                    val f = symbol in favs
                    Icon(if (f) Icons.Filled.Star else Icons.Outlined.Star, "Favori", tint = if (f) Accent else Muted)
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (t != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // Kayan rakamlı canlı fiyat
                    val prevPrice = remember(symbol) { mutableStateOf(t.price) }
                    val up = t.price >= prevPrice.value
                    LaunchedEffect(t.price) { prevPrice.value = t.price }
                    RollingText(Format.price(t.price), style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                        color = changeColor(t.change24hPct), up = up)
                    Spacer(Modifier.width(8.dp))
                    Text(Format.signedPct(t.change24hPct), color = changeColor(t.change24hPct), modifier = Modifier.padding(bottom = 5.dp))
                }
                val now = System.currentTimeMillis()
                val h = MarketRepository.history(symbol)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(1 to "1dk", 5 to "5dk", 15 to "15dk", 60 to "1sa").forEach { (m, l) ->
                        val c = h?.changePct(now, m * 60_000L, t.price)
                        Column {
                            Text(l, fontSize = 10.sp, color = Muted)
                            Text(c?.let { Format.signedPct(it) } ?: "—", fontSize = 13.sp, color = changeColor(c ?: 0.0))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Kv("24s Yüksek", Format.price(t.high24h))
                    Kv("24s Düşük", Format.price(t.low24h))
                    Kv("24s Hacim", Format.compact(t.quoteVolume) + " " + t.quote)
                }
                Text("24s aralıktaki konum: %${(t.rangePosition * 100).toInt()}", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("Veri bekleniyor…", color = Muted)
            }

            if (t != null && t.quote == "USDT") {
                androidx.compose.material3.OutlinedButton(onClick = { vm.openSimulator(symbol) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("💰 Bu coine para koysam ne olur? (Simülatör)")
                }
            }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intervals.forEach { (k, l) -> FilterChip(selected = interval == k, onClick = { interval = k }, label = { Text(l) }) }
            }
            CandleChart(klines, ema, animKey = interval)
            Text("Sarı çizgi: EMA20", fontSize = 10.sp, color = Muted)

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("İstatistiksel Analiz", fontWeight = FontWeight.Bold)
                    val s = signal
                    when {
                        s != null -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                ScoreBadge(s.score, scoreColor(s.score))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(s.label, color = scoreColor(s.score), fontWeight = FontWeight.SemiBold)
                                    Text("Teknik skor (0-100)", fontSize = 11.sp, color = Muted)
                                }
                            }
                            s.factors.forEach { FactorRow(it) }
                            if (s.histWinRate >= 0) Text(
                                "Geçmiş benzer koşullar: ${s.histSamples} örnek, 12s sonra yükseliş oranı %${(s.histWinRate * 100).toInt()}, ort. ${Format.signedPct(s.histAvgReturn)}",
                                fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        analysisError -> Text("Analiz için yeterli veri yok.", color = Muted)
                        else -> Text("Hesaplanıyor…", color = Muted)
                    }
                }
            }
            signal?.reversal?.let { r ->
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Dip Dönüş Analizi", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            ScoreBadge(r.score, if (r.candidate) Up else if (r.depth >= 0.45) Accent else Muted)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    when {
                                        r.candidate -> "Dip dönüş adayı ✅"
                                        r.depth >= 0.45 -> "Çok düşük, dönüş henüz teyit edilmedi"
                                        else -> "Aşırı düşüş bölgesinde değil"
                                    },
                                    color = if (r.candidate) Up else if (r.depth >= 0.45) Accent else Muted, fontWeight = FontWeight.SemiBold,
                                )
                                Text("Derinlik ${(r.depth * 100).toInt()} • Dönüş ${(r.turn * 100).toInt()}", fontSize = 11.sp, color = Muted)
                            }
                        }
                        r.reasons.forEach { Text("• $it", fontSize = 12.sp) }
                        if (r.histWinRate >= 0) Text(
                            "Bu coinde aynı desen ${r.histSamples} kez: 24s sonra %${(r.histWinRate * 100).toInt()} yükseliş, ort. ${Format.signedPct(r.histAvgReturn)}",
                            fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Text(DISCLAIMER, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        }
    }

    if (showAlert && t != null) {
        AddAlertDialog(vm, initialSymbol = symbol, onDismiss = { showAlert = false })
    }
}

@Composable
private fun Kv(k: String, v: String) {
    Column {
        Text(k, fontSize = 10.sp, color = Muted)
        Text(v, fontSize = 13.sp)
    }
}
