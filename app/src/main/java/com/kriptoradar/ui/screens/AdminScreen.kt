package com.kriptoradar.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.kriptoradar.ui.components.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.Prefs
import com.kriptoradar.service.MarketService
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.update.UpdateManager
import kotlinx.coroutines.delay

@Composable
fun AdminScreen(vm: MainViewModel) {
    val unlocked by vm.adminUnlocked.collectAsStateWithLifecycle()
    if (!unlocked) PinGate(vm) else AdminPanel(vm)
}

@Composable
private fun PinGate(vm: MainViewModel) {
    val hasPin = remember { vm.hasAdminPin() }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔐", fontSize = 48.sp)
        Text(if (hasPin) "Admin paneli kilitli" else "Admin PIN'i oluştur", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(if (hasPin) "PIN'ini gir." else "Paneli korumak için 4-8 haneli bir PIN belirle. Unutma; sıfırlamak için uygulama verisini silmen gerekir.",
            fontSize = 13.sp, color = Muted)
        val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        OutlinedTextField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
        if (!hasPin) OutlinedTextField(pin2, { if (it.length <= 8 && it.all(Char::isDigit)) pin2 = it }, label = { Text("PIN (tekrar)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
        error?.let { Text(it, color = Down) }
        Button(onClick = {
            error = when {
                pin.length < 4 -> "En az 4 hane"
                hasPin -> if (vm.unlockAdmin(pin)) null else "PIN yanlış"
                pin != pin2 -> "PIN'ler aynı değil"
                else -> { vm.setAdminPin(pin); null }
            }
        }) { Text(if (hasPin) "Aç" else "Oluştur ve aç") }
    }
}

private fun parse(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminPanel(vm: MainViewModel) {
    val ctx = LocalContext.current
    val p by vm.paper.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    fun toast(msg: String?) = Toast.makeText(ctx, msg ?: "Tamam", Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Buradaki tüm para ve coinler SANALDIR; gerçek para ile ilgisi yoktur.", fontSize = 12.sp, color = Muted)

        // ---------- Bakiye ----------
        AdminSection("💰 Sanal bakiye") {
            Text("Nakit: ${Format.num(p.cash)} TL • Net yatırılan: ${Format.num(p.totalDeposited)} TL", fontSize = 13.sp)
            var amount by remember { mutableStateOf("") }
            var note by remember { mutableStateOf("") }
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Tutar (TL)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1_000, 5_000, 10_000, 50_000, 100_000).forEach { a ->
                    FilterChip(selected = false, onClick = { amount = a.toString() }, label = { Text(Format.compact(a.toDouble())) })
                }
            }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Not (isteğe bağlı)") }, singleLine = true)
            val v = parse(amount)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { toast(vm.paperDeposit(v ?: 0.0, note) ?: "${Format.num(v ?: 0.0)} TL yüklendi"); amount = ""; note = "" }) { Text("Yükle") }
                OutlinedButton(onClick = { toast(vm.paperWithdraw(v ?: 0.0) ?: "Çekildi"); amount = "" }) { Text("Çek") }
                OutlinedButton(onClick = { v?.let { confirm = "Nakit ${Format.num(it)} TL olarak ayarlansın mı?" to { toast(vm.paperSetCash(it)) } } }) { Text("Nakiti ayarla") }
            }
        }

        // ---------- Coin ekle / sil ----------
        AdminSection("🪙 Coin ekle / sil") {
            var q by remember { mutableStateOf("") }
            var qty by remember { mutableStateOf("") }
            var sel by remember { mutableStateOf<String?>(null) }
            OutlinedTextField(q, { q = it; sel = null }, Modifier.fillMaxWidth(), label = { Text("Coin (BTC, ETH…)") }, singleLine = true)
            val qq = q.trim().uppercase()
            if (qq.isNotEmpty() && sel == null) {
                tickers.values.filter { it.quote == "USDT" && BinanceApi.isTradable(it.base) && it.base.startsWith(qq) }
                    .sortedByDescending { it.quoteVolume }.take(5).forEach { t ->
                        Text("${t.base} • ${vm.priceTry(t.symbol)?.let { Format.price(it) + " TL" } ?: ""}",
                            Modifier.fillMaxWidth().clickable { sel = t.symbol; q = t.base }.padding(6.dp), color = Muted)
                    }
            }
            OutlinedTextField(qty, { qty = it }, Modifier.fillMaxWidth(), label = { Text("Adet") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            val s = sel
            val qv = qty.replace(',', '.').toDoubleOrNull()
            if (s != null && qv != null) vm.priceTry(s)?.let { Text("Değeri ≈ ${Format.num(qv * it)} TL (yükleme olarak sayılır, kâr/zarar bozulmaz)", fontSize = 12.sp, color = Muted) }
            Button(enabled = s != null && qv != null && qv > 0, onClick = {
                toast(vm.paperGrant(s!!, qv!!) ?: "Eklendi"); qty = ""
            }) { Text("Hediye coin ekle") }
            if (p.holdings.isNotEmpty()) {
                Text("Portföydeki coinler (satmadan silmek için):", fontSize = 12.sp, color = Muted)
                p.holdings.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Format.price(h.qty)} ${h.base}", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { confirm = "${h.base} satılmadan silinsin mi?" to { toast(vm.paperRemove(h.symbol)) } }) { Text("Sil", color = Down) }
                    }
                }
            }
        }

        // ---------- İşlem ayarları ----------
        AdminSection("⚙️ İşlem ayarları") {
            var fee by remember(p.feePct) { mutableStateOf(p.feePct.toString()) }
            var slip by remember(p.slippagePct) { mutableStateOf(p.slippagePct.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fee, { fee = it }, Modifier.weight(1f), label = { Text("Komisyon %") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(slip, { slip = it }, Modifier.weight(1f), label = { Text("Fiyat kayması %") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Text("Gerçek borsalarda komisyon genelde %0,1–0,2'dir. Kayma: alırken biraz daha pahalıya, satarken biraz daha ucuza gerçekleşme farkı.",
                fontSize = 11.sp, color = Muted)
            Button(onClick = {
                toast(vm.paperFees(fee.replace(',', '.').toDoubleOrNull() ?: p.feePct, slip.replace(',', '.').toDoubleOrNull() ?: p.slippagePct) ?: "Kaydedildi")
            }) { Text("Kaydet") }
        }

        // ---------- Temizlik ----------
        AdminSection("🧹 Veri yönetimi") {
            var start by remember { mutableStateOf("10000") }
            Text("Bekleyen emir: ${p.orders.size} • Hareket kaydı: ${p.ledger.size}", fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { confirm = "Tüm bekleyen emirler iptal edilsin mi?" to { toast(vm.paperCancelAll()) } }) { Text("Emirleri iptal et") }
                OutlinedButton(onClick = { confirm = "Hesap hareketleri ve değer grafiği silinsin mi?" to { toast(vm.paperClearLedger()) } }) { Text("Hareketleri temizle") }
                OutlinedButton(onClick = { confirm = "Öneri takibi geçmişi silinsin mi?" to { vm.clearSuggestions(); toast(null) } }) { Text("Öneri geçmişini sil") }
                OutlinedButton(onClick = { confirm = "Tüm fiyat alarmların silinsin mi?" to { Prefs.updateAlerts { emptyList() }; toast(null) } }) { Text("Alarmları sil") }
                OutlinedButton(onClick = { Prefs.clearCooldowns(); toast("Bildirim bekleme süreleri sıfırlandı") }) { Text("Bildirim sürelerini sıfırla") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("Başlangıç bakiyesi") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(colors = ButtonDefaults.buttonColors(containerColor = Down), onClick = {
                    val v = parse(start) ?: 10_000.0
                    confirm = "Sanal portföy tamamen sıfırlanıp ${Format.num(v)} TL ile yeniden başlasın mı?" to { toast(vm.paperReset(v)) }
                }) { Text("Sıfırla") }
            }
        }

        // ---------- Yedek ----------
        AdminSection("💾 Yedekle / geri yükle") {
            Text("Ayarlar, alarmlar, favoriler, sanal portföy ve öneri geçmişi tek bir metin olarak dışa aktarılır. Telefon değiştirirken işe yarar.",
                fontSize = 12.sp, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Kripto Radar yedeği").putExtra(Intent.EXTRA_TEXT, Prefs.exportJson()), "Yedeği paylaş"))
                }) { Text("Yedeği paylaş") }
                OutlinedButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("yedek", Prefs.exportJson()))
                    toast("Panoya kopyalandı")
                }) { Text("Panoya kopyala") }
            }
            var importText by remember { mutableStateOf("") }
            OutlinedTextField(importText, { importText = it }, Modifier.fillMaxWidth().height(110.dp), label = { Text("Yedek metnini buraya yapıştır") })
            OutlinedButton(enabled = importText.isNotBlank(), onClick = {
                confirm = "Mevcut veriler yedektekilerle değiştirilsin mi?" to {
                    toast(runCatching { Prefs.importJson(importText) }.fold({ importText = ""; "Geri yüklendi" }, { "Geçersiz yedek: ${it.message?.take(80)}" }))
                }
            }) { Text("Geri yükle") }
        }

        // ---------- Sistem ----------
        AdminSection("🖥 Sistem durumu") {
            val conn by vm.conn.collectAsStateWithLifecycle()
            val last by vm.lastMsg.collectAsStateWithLifecycle()
            val scan by vm.scan.collectAsStateWithLifecycle()
            val tick by produceState(0L) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
            Stat2("Sürüm", "${UpdateManager.currentVersionName} (${UpdateManager.currentVersionCode})")
            Stat2("Arka plan servisi", if (MarketService.running) "Çalışıyor ✓" else "Durdu", if (MarketService.running) Up else Down)
            Stat2("Canlı bağlantı", conn.name + if (last > 0) " • son veri ${(tick - last) / 1000} sn önce" else "")
            Stat2("İzlenen çift", "${tickers.size}")
            Stat2("USD/TL", vm.usdTry()?.let { Format.price(it) } ?: "—")
            Stat2("Son tarama", "${Format.ago(scan?.at ?: 0)} • ${scan?.signals?.size ?: 0} coin")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.scanNow(); toast("Tarama başladı") }) { Text("Şimdi tara") }
                OutlinedButton(onClick = { MarketService.stop(ctx); MarketService.start(ctx); toast("Servis yeniden başlatıldı") }) { Text("Servisi yeniden başlat") }
                OutlinedButton(onClick = { vm.checkUpdate(); toast("Güncelleme kontrol ediliyor") }) { Text("Güncelleme denetle") }
            }
        }

        // ---------- Güvenlik ----------
        AdminSection("🔐 Güvenlik") {
            var newPin by remember { mutableStateOf("") }
            OutlinedTextField(newPin, { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it }, label = { Text("Yeni PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = newPin.length >= 4, onClick = { vm.setAdminPin(newPin); newPin = ""; toast("PIN değişti") }) { Text("PIN'i değiştir") }
                Button(onClick = vm::lockAdmin) { Text("Paneli kilitle") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Emin misin?") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Evet", color = Down) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun AdminSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Accent)
            content()
        }
    }
}

@Composable
private fun Stat2(k: String, v: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), fontSize = 13.sp, color = Muted)
        Text(v, fontSize = 13.sp, color = color)
    }
}
