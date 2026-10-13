package com.kriptoradar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import com.kriptoradar.ui.components.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.AlertType
import com.kriptoradar.data.PriceAlert
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.theme.Muted

@Composable
fun AlertsScreen(vm: MainViewModel) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Alarmlar arka planda saniyelik kontrol edilir. Otomatik ani hareket ve sinyal bildirimleri Ayarlar'dan yönetilir.",
                    fontSize = 12.sp, color = Muted)
            }
            if (alerts.isEmpty()) item { Text("Henüz alarm yok. + ile ekleyin.", color = Muted, modifier = Modifier.padding(top = 16.dp)) }
            items(alerts.sortedByDescending { it.id }, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.symbol, fontWeight = FontWeight.Bold)
                            Text(describe(a), fontSize = 13.sp)
                            val cur = tickers[a.symbol]?.price
                            Text(
                                (cur?.let { "Şu an: ${Format.price(it)}" } ?: "") +
                                    (if (a.repeat) " • tekrarlı" else " • tek sefer") +
                                    (if (a.lastTriggeredAt > 0) " • son: ${Format.ago(a.lastTriggeredAt)}" else ""),
                                fontSize = 11.sp, color = Muted,
                            )
                        }
                        Switch(checked = a.enabled, onCheckedChange = { vm.toggleAlert(a.id) })
                        IconButton(onClick = { vm.deleteAlert(a.id) }) { Icon(Icons.Default.Delete, "Sil", tint = Muted) }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Alarm ekle") },
        )
    }
    if (adding) AddAlertDialog(vm, null) { adding = false }
}

private fun describe(a: PriceAlert) = when (a.type) {
    AlertType.PRICE_ABOVE -> "Fiyat ≥ ${Format.price(a.value)}"
    AlertType.PRICE_BELOW -> "Fiyat ≤ ${Format.price(a.value)}"
    AlertType.CHANGE_24H_UP -> "24s değişim ≥ +%${Format.num(a.value)}"
    AlertType.CHANGE_24H_DOWN -> "24s değişim ≤ -%${Format.num(a.value)}"
    AlertType.MOVE_IN_WINDOW -> "${a.windowMin} dk içinde ±%${Format.num(a.value)} hareket"
}

private enum class Kind(val label: String) { TARGET("Hedef fiyat"), PCT24("24s %"), MOVE("Ani hareket") }

@Composable
fun AddAlertDialog(vm: MainViewModel, initialSymbol: String?, onDismiss: () -> Unit) {
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val quote = vm.settings.collectAsStateWithLifecycle().value.quote
    var sym by remember { mutableStateOf(initialSymbol?.let { s -> tickers[s]?.base } ?: initialSymbol ?: "") }
    var kind by remember { mutableStateOf(Kind.TARGET) }
    var value by remember { mutableStateOf("") }
    var window by remember { mutableStateOf("5") }
    var up by remember { mutableStateOf(true) }
    var repeat by remember { mutableStateOf(false) }

    val symbol = initialSymbol ?: (sym.trim().uppercase().let { if (tickers.containsKey(it)) it else it + quote })
    val t = tickers[symbol]
    val suggestions = remember(sym, tickers.size) {
        if (initialSymbol != null || sym.length < 1) emptyList()
        else tickers.values.filter { it.quote == quote && it.base.startsWith(sym.trim().uppercase()) }
            .sortedByDescending { it.quoteVolume }.take(5)
    }
    val v = value.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialSymbol == null) {
                    OutlinedTextField(sym, { sym = it }, label = { Text("Coin (örn. BTC)") }, singleLine = true)
                    suggestions.forEach { s ->
                        if (s.base != sym.trim().uppercase()) Text("${s.base} • ${Format.price(s.price)}", Modifier.clickable { sym = s.base }.padding(4.dp), color = Muted)
                    }
                }
                Text(t?.let { "${it.base}/${it.quote} şu an ${Format.price(it.price)}" } ?: "Çift bulunamadı", fontSize = 12.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label, fontSize = 12.sp) }) }
                }
                when (kind) {
                    Kind.TARGET -> OutlinedTextField(value, { value = it }, label = { Text("Hedef fiyat") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Kind.PCT24 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = up, onClick = { up = true }, label = { Text("Yükseliş") })
                            FilterChip(selected = !up, onClick = { up = false }, label = { Text("Düşüş") })
                        }
                        OutlinedTextField(value, { value = it }, label = { Text("Yüzde (örn. 5)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Kind.MOVE -> {
                        OutlinedTextField(value, { value = it }, label = { Text("Yüzde (örn. 2)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(window, { window = it }, label = { Text("Dakika (1-120)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(repeat, { repeat = it })
                    Text("Tekrarlı (bekleme süresiyle)", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = t != null && v != null && v > 0, onClick = {
                val vv = v ?: return@TextButton
                when (kind) {
                    Kind.TARGET -> vm.addPriceTarget(symbol, vv, repeat)
                    Kind.PCT24 -> vm.addAlert(symbol, if (up) AlertType.CHANGE_24H_UP else AlertType.CHANGE_24H_DOWN, vv, 5, repeat)
                    Kind.MOVE -> vm.addAlert(symbol, AlertType.MOVE_IN_WINDOW, vv, (window.toIntOrNull() ?: 5).coerceIn(1, 120), repeat)
                }
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
