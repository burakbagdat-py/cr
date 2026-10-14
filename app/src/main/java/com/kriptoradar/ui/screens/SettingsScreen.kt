package com.kriptoradar.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.kriptoradar.ui.components.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.Prefs
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.components.DISCLAIMER
import com.kriptoradar.ui.components.UpdateSection
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(vm: MainViewModel, openAdmin: () -> Unit = {}) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val pm = ctx.getSystemService(PowerManager::class.java)
    val ignoringBattery = pm.isIgnoringBatteryOptimizations(ctx.packageName)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("🛠 Admin paneli") {
            Text("Sanal para yükle/çek, coin ekle-sil, komisyon ayarı, yedekleme, sistem durumu. PIN ile korunur.", fontSize = 12.sp, color = Muted)
            Button(onClick = openAdmin) { Text("Admin panelini aç") }
        }

        val upd by vm.update.collectAsStateWithLifecycle()
        Section("Uygulama güncelleme") {
            UpdateSection(upd, vm::checkUpdate, vm::downloadUpdate, vm::installUpdate)
        }

        Section("Bildirim tarzı") {
            ToggleRow("😎 Kanka modu", "Bildirimler resmi değil, arkadaş ağzıyla gelir", s.casualMode) { v -> Prefs.updateSettings { it.copy(casualMode = v) } }
            Text(
                if (s.casualMode) "Örnek: \"🚀 Kanka BTC uçuyo amk! – 5 dakikada %3,20 artmış…\"\n\"🔻 Kanka SOL batıyo amk! – Elinde varsa stop'unu kontrol et, istersen çık.\""
                else "Örnek: \"🚀 BTC 5 dk'da %3,20 – BTC/USDT 84.120,00 • 24s %4,10\"",
                fontSize = 12.sp, color = Muted,
            )
            OutlinedButton(onClick = {
                val (title, text) = if (s.casualMode) com.kriptoradar.alerts.Voice.rapidMove("BTC", true, 3.2, 5, "84.120,00 USDT", 4.1)
                else "🚀 BTC 5 dk'da %3,20" to "BTC/USDT 84.120,00 • 24s %4,10 (test bildirimi)"
                com.kriptoradar.alerts.Notifier.post(ctx, com.kriptoradar.alerts.Notifier.CH_MOVE, 9999, title, text, "BTCUSDT")
            }) { Text("Test bildirimi gönder") }
        }

        Section("🎮 Sanal portföy bildirimleri") {
            ToggleRow("Aldığım coinler için bildir", "Kâra/zarara geçince ya da sert hareket edince; bildirimden direkt satabilirsin", s.paperNotify) { v -> Prefs.updateSettings { it.copy(paperNotify = v) } }
            SliderRow("Kâr bildirimi her", s.paperProfitStepPct.toFloat(), 1f..25f, { "+%" + it.toInt() }) { v -> Prefs.updateSettings { it.copy(paperProfitStepPct = v.toInt().toDouble()) } }
            SliderRow("Zarar uyarısı her", s.paperLossStepPct.toFloat(), 1f..25f, { "-%" + it.toInt() }) { v -> Prefs.updateSettings { it.copy(paperLossStepPct = v.toInt().toDouble()) } }
            SliderRow("5 dakikalık sert hareket", s.paperMovePct.toFloat(), 0.5f..10f, { "%" + Format.num(it.toDouble()) }) { v -> Prefs.updateSettings { it.copy(paperMovePct = v.toDouble()) } }
            OutlinedButton(onClick = {
                val m = com.kriptoradar.alerts.PaperMoney("BTC", 1000.0, 1080.0, 80.0, 8.0, 4_200_000.0)
                val (title, text) = com.kriptoradar.alerts.Voice.paperProfit(s.casualMode, m, com.kriptoradar.alerts.PaperExecutor.Momentum.RISING_STRONG, 0.1)
                com.kriptoradar.alerts.Notifier.post(ctx, com.kriptoradar.alerts.Notifier.CH_PAPER, 9998, title, text + " – test", "BTCUSDT")
            }) { Text("Örnek bildirim gönder") }
        }

        Section("Hangi coinler için bildirim?") {
            ToggleRow("Sadece seçtiğim coinler", "Ani hareket, sinyal, dip dönüş ve hacim bildirimleri sadece aşağıdaki coinler için gelir. Fiyat alarmların her zaman çalışır.", s.onlySelectedCoins) { v ->
                Prefs.updateSettings { it.copy(onlySelectedCoins = v) }
            }
            CoinPicker(vm, s.notifyCoins, enabled = s.onlySelectedCoins)
        }

        Section("Arka plan izleme") {
            ToggleRow("Sürekli canlı izleme", "Uygulama kapalıyken de tüm piyasa saniyelik izlenir", s.serviceEnabled) { vm.setService(it) }
            Text(
                if (ignoringBattery) "✓ Pil optimizasyonu kapalı – kesintisiz çalışır" else "⚠ Pil optimizasyonu açık: sistem servisi durdurabilir",
                color = if (ignoringBattery) Up else Accent, fontSize = 12.sp,
            )
            if (!ignoringBattery) Button(onClick = {
                ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }) { Text("Pil kısıtlamasını kaldır") }
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text("Bildirim ayarları") }
        }

        Section("Piyasa") {
            Text("Liste para birimi", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("USDT", "TRY", "FDUSD", "BTC").forEach { q ->
                    FilterChip(selected = s.quote == q, onClick = { Prefs.updateSettings { it.copy(quote = q) } }, label = { Text(q) })
                }
            }
        }

        Section("Ani hareket bildirimleri") {
            ToggleRow("Etkin", "Favoriler + hacimli coinlerde sert hareketler", s.rapidMoveEnabled) { v -> Prefs.updateSettings { it.copy(rapidMoveEnabled = v) } }
            SliderRow("Eşik", s.rapidMovePct.toFloat(), 0.5f..15f, { "%" + Format.num(it.toDouble()) }) { v -> Prefs.updateSettings { it.copy(rapidMovePct = v.toDouble()) } }
            SliderRow("Zaman penceresi", s.rapidMoveWindowMin.toFloat(), 1f..60f, { "${it.toInt()} dk" }) { v -> Prefs.updateSettings { it.copy(rapidMoveWindowMin = v.toInt()) } }
            SliderRow("En düşük 24s hacim", (s.minQuoteVolume / 1e6).toFloat(), 1f..200f, { "${it.toInt()} Mn" }) { v -> Prefs.updateSettings { it.copy(minQuoteVolume = v.toDouble() * 1e6) } }
            SliderRow("Aynı coin için bekleme", s.alertCooldownMin.toFloat(), 5f..240f, { "${it.toInt()} dk" }) { v -> Prefs.updateSettings { it.copy(alertCooldownMin = v.toInt()) } }
        }

        Section("Dip dönüş dedektörü") {
            ToggleRow("Dip dönüş bildirimleri", "Çok düşmüş ama yükselişe dönme eğilimi gösteren coinleri otomatik bildir", s.reversalNotify) { v -> Prefs.updateSettings { it.copy(reversalNotify = v) } }
            SliderRow("En düşük dip dönüş skoru", s.reversalThreshold.toFloat(), 40f..90f, { "${it.toInt()}" }) { v -> Prefs.updateSettings { it.copy(reversalThreshold = v.toInt()) } }
        }

        Section("İstatistiksel tarama") {
            ToggleRow("Sinyal bildirimleri", "Skoru eşiği yeni geçen coinler", s.signalNotify) { v -> Prefs.updateSettings { it.copy(signalNotify = v) } }
            SliderRow("Skor eşiği", s.signalThreshold.toFloat(), 50f..95f, { "${it.toInt()}" }) { v -> Prefs.updateSettings { it.copy(signalThreshold = v.toInt()) } }
            ToggleRow("Hacim patlaması bildirimi", null, s.volumeSpikeNotify) { v -> Prefs.updateSettings { it.copy(volumeSpikeNotify = v) } }
            SliderRow("Hacim patlaması oranı", s.volumeSpikeRatio.toFloat(), 2f..10f, { Format.num(it.toDouble()) + "x" }) { v -> Prefs.updateSettings { it.copy(volumeSpikeRatio = v.toDouble()) } }
            SliderRow("Tarama aralığı", s.scanIntervalMin.toFloat(), 5f..60f, { "${it.toInt()} dk" }) { v -> Prefs.updateSettings { it.copy(scanIntervalMin = v.toInt()) } }
            SliderRow("Taranan coin sayısı", s.scanUniverse.toFloat(), 20f..200f, { "${it.toInt()}" }) { v -> Prefs.updateSettings { it.copy(scanUniverse = v.toInt()) } }
        }

        Section("Hakkında") {
            Text("Veri kaynakları: Binance (canlı WebSocket + REST), alternative.me (Korku/Açgözlülük), CoinGecko (küresel piyasa).", fontSize = 12.sp, color = Muted)
            Text(DISCLAIMER, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Accent)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, fontSize = 11.sp, color = Muted)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: (Float) -> String, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Column {
        Row {
            Text(title, Modifier.weight(1f), fontSize = 13.sp)
            Text(label(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = v, onValueChange = { v = it }, valueRange = range, onValueChangeFinished = { onDone(v) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoinPicker(vm: MainViewModel, selected: Set<String>, enabled: Boolean) {
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    fun toggle(base: String) = Prefs.updateSettings { st ->
        st.copy(notifyCoins = if (base in st.notifyCoins) st.notifyCoins - base else st.notifyCoins + base)
    }
    val suggestions = remember(q, tickers.size) {
        val qq = q.trim().uppercase()
        if (qq.isEmpty()) emptyList()
        else tickers.values.filter { it.quote == "USDT" && it.base.startsWith(qq) && com.kriptoradar.data.BinanceApi.isTradable(it.base) }
            .sortedByDescending { it.quoteVolume }.map { it.base }.distinct().take(6)
    }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (enabled && selected.isEmpty()) Text("⚠ Hiç coin seçmedin; otomatik bildirim gelmez.", fontSize = 12.sp, color = Accent)
        Text("Seçili (${selected.size}) – kaldırmak için dokun:", fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            selected.sorted().forEach { b -> FilterChip(selected = true, onClick = { toggle(b) }, label = { Text("$b ✕") }) }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Coin ekle (BTC, ETH…)") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { b -> FilterChip(selected = b in selected, onClick = { toggle(b); q = "" }, label = { Text(b) }) }
        }
        val favBases = favs.mapNotNull { tickers[it]?.base ?: com.kriptoradar.data.BinanceApi.splitSymbol(it)?.first }.distinct()
        if (favBases.any { it !in selected }) {
            Text("Favorilerinden hızlı ekle:", fontSize = 12.sp, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                favBases.filter { it !in selected }.forEach { b -> FilterChip(selected = false, onClick = { toggle(b) }, label = { Text("+ $b") }) }
            }
        }
    }
}
