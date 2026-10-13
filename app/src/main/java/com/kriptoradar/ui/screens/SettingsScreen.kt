package com.kriptoradar.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val pm = ctx.getSystemService(PowerManager::class.java)
    val ignoringBattery = pm.isIgnoringBatteryOptimizations(ctx.packageName)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
