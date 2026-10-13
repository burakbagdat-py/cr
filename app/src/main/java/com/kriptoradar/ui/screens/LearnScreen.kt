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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import com.kriptoradar.ui.components.Card
import androidx.compose.foundation.layout.Box
import com.kriptoradar.ui.components.enterAnim
import com.kriptoradar.ui.components.AnimatedBar
import com.kriptoradar.ui.components.bouncyClick
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoradar.data.Prefs
import com.kriptoradar.learn.BeginnerBrief
import com.kriptoradar.learn.LearnContent
import com.kriptoradar.learn.Pick
import com.kriptoradar.learn.Risk
import com.kriptoradar.ui.Format
import com.kriptoradar.ui.MainViewModel
import com.kriptoradar.ui.theme.Accent
import com.kriptoradar.ui.theme.Down
import com.kriptoradar.ui.theme.Muted
import com.kriptoradar.ui.theme.Up
import com.kriptoradar.ui.theme.changeColor
import java.util.Calendar

private enum class LearnTab(val label: String) { TODAY("Bugün"), LESSONS("Dersler"), GLOSSARY("Sözlük") }

@Composable
fun LearnScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(LearnTab.TODAY) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            LearnTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }) }
        }
        when (tab) {
            LearnTab.TODAY -> TodayTab(vm, onOpen) { tab = LearnTab.LESSONS }
            LearnTab.LESSONS -> LessonsTab()
            LearnTab.GLOSSARY -> GlossaryTab()
        }
    }
}

/** "**kalın**" işaretlerini kalın yazıya çevirir. */
fun rich(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, p -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p) }
}

@Composable
private fun TodayTab(vm: MainViewModel, onOpen: (String) -> Unit, goLessons: () -> Unit) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val tickers by vm.tickers.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val read by Prefs.readLessons.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val day = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
    // Fiyatlar canlı akarken özeti her saniye yeniden hesaplamamak için sadece tarama değişince kur
    val brief = remember(scan, tickers.isNotEmpty()) { BeginnerBrief.build(scan, tickers, day) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (read.size < LearnContent.lessons.size) item {
            Card(Modifier.fillMaxWidth().bouncyClick(goLessons), colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f))) {
                Column(Modifier.padding(14.dp)) {
                    Text("👋 Kriptoya yeni mi başlıyorsun?", fontWeight = FontWeight.Bold)
                    Text("${LearnContent.lessons.size} kısa dersle sıfırdan öğren. İlerleme: ${read.size}/${LearnContent.lessons.size}", fontSize = 13.sp)
                    AnimatedBar(read.size.toFloat() / LearnContent.lessons.size, Accent, Modifier.padding(top = 6.dp))
                    Text("Derslere git →", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (brief == null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(if (scanning) "  Piyasa analiz ediliyor, birazdan özet hazır…" else "Henüz analiz yok.", color = Muted)
                    if (!scanning) TextButton(onClick = vm::scanNow) { Text("Şimdi analiz et") }
                }
            }
            return@LazyColumn
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bob = androidx.compose.animation.core.rememberInfiniteTransition(label = "mood")
                        val dy by bob.animateFloat(-4f, 4f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600), androidx.compose.animation.core.RepeatMode.Reverse), label = "dy")
                        Text(brief.moodEmoji, fontSize = 34.sp, modifier = Modifier.graphicsLayer { translationY = dy })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bugün piyasa: ${brief.moodTitle}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Analiz: ${Format.ago(brief.scannedAt)}", fontSize = 11.sp, color = Muted)
                        }
                        if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = vm::scanNow) { Icon(Icons.Default.Refresh, "Yenile") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(brief.moodText, fontSize = 13.sp)
                    brief.btcText?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Down.copy(alpha = 0.10f))) {
                Text(
                    rich("**Önce bunu oku:** Aşağıdaki seçimler geçmiş fiyat ve hacim verisinden otomatik hesaplanan istatistiklerdir; " +
                        "**kesin bilgi veya yatırım tavsiyesi değildir.** Hiç kimse yarının fiyatını bilemez. \"Yükseliş eğiliminde\" denen bir coin " +
                        "ertesi gün düşebilir. Sadece kaybetmeyi göze alabileceğin küçük tutarlarla, risk etiketine bakarak karar ver."),
                    fontSize = 12.sp, modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (brief.safer.isNotEmpty()) {
            item { SectionTitle("🟢 Yeni başlayan için daha sakin seçenekler", "Büyük ve köklü coinler arasından teknik görünümü olumlu olanlar.") }
            items(brief.safer, key = { "s" + it.signal.symbol }) { Box(Modifier.animateItem().enterAnim(1)) { PickCard(it, it.signal.symbol in favs, onOpen, vm::toggleFav, vm::openSimulator) } }
        }

        item { SectionTitle("📈 İstatistiğe göre yükseliş eğiliminde olanlar", "Teknik puanı en yüksek coinler. Risk etiketine mutlaka bak.") }
        if (brief.rising.isEmpty()) item { Text("Şu an belirgin yükseliş eğiliminde coin yok. Piyasa zayıfken beklemek de bir stratejidir.", color = Muted, fontSize = 13.sp) }
        items(brief.rising, key = { "r" + it.signal.symbol }) { Box(Modifier.animateItem().enterAnim(2)) { PickCard(it, it.signal.symbol in favs, onOpen, vm::toggleFav, vm::openSimulator) } }

        if (brief.reversals.isNotEmpty()) {
            item { SectionTitle("🔄 Çok düşmüş ama toparlanıyor olabilir", "Ucuzlamış ve grafiği yukarı dönmeye başlamış coinler. Düşüş yeniden başlayabilir.") }
            items(brief.reversals, key = { "v" + it.signal.symbol }) { Box(Modifier.animateItem().enterAnim(3)) { PickCard(it, it.signal.symbol in favs, onOpen, vm::toggleFav, vm::openSimulator) } }
        }

        if (brief.avoid.isNotEmpty()) {
            item { SectionTitle("⚠️ Şu an dikkatli olunması gerekenler", "Düşüş trendindeki coinler.") }
            items(brief.avoid, key = { "a" + it.signal.symbol }) { Box(Modifier.animateItem().enterAnim(4)) { PickCard(it, it.signal.symbol in favs, onOpen, vm::toggleFav, vm::openSimulator) } }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("💡 Günün ipucu", fontWeight = FontWeight.Bold, color = Accent)
                    Text(brief.tip, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, sub: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, fontSize = 12.sp, color = Muted)
    }
}

private fun riskColor(r: Risk) = when (r) {
    Risk.LOW -> Up; Risk.MEDIUM -> Accent; Risk.HIGH -> Color(0xFFFF9800); Risk.VERY_HIGH -> Down
}

@Composable
private fun PickCard(p: Pick, fav: Boolean, onOpen: (String) -> Unit, onFav: (String) -> Unit, onSim: ((String) -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val s = p.signal
    Card(Modifier.fillMaxWidth().animateContentSize().bouncyClick { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.headline, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    p.about?.let { Text(it, fontSize = 12.sp, color = Muted) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.price(s.price) + " $", fontWeight = FontWeight.SemiBold)
                    Text("24s " + Format.signedPct(s.change24h), color = changeColor(s.change24h), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${p.risk.emoji} ${p.risk.label}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = riskColor(p.risk),
                modifier = Modifier.background(riskColor(p.risk).copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("Neden?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            (if (expanded) p.reasons else p.reasons.take(2)).forEach { Text("• $it", fontSize = 13.sp) }
            p.caution?.let { Text("⚠️ $it", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(top = 4.dp)) }
            if (expanded) {
                Text(p.risk.explain, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                Row {
                    TextButton(onClick = { onOpen(s.symbol) }) { Text("Grafiği gör") }
                    TextButton(onClick = { onFav(s.symbol) }) { Text(if (fav) "★ Takipte" else "☆ Takibe al") }
                    if (onSim != null) TextButton(onClick = { onSim(s.symbol) }) { Text("Koysam ne olur?") }
                }
            } else if (p.reasons.size > 2) {
                Text("Devamı için dokun", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun LessonsTab() {
    val read by Prefs.readLessons.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Sırayla oku; her ders 1-2 dakika. Okuduğun dersler ✓ ile işaretlenir.", fontSize = 12.sp, color = Muted)
        }
        items(LearnContent.lessons, key = { it.id }) { l ->
            val isOpen = open == l.id
            val idx = LearnContent.lessons.indexOf(l) + 1
            Card(Modifier.fillMaxWidth().animateContentSize().bouncyClick {
                open = if (isOpen) null else l.id
                Prefs.markLessonRead(l.id)
            }) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("$idx. ${l.title}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (l.id in read) Text("✓", color = Up, fontWeight = FontWeight.Bold)
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(8.dp))
                        l.body.forEach { Text(rich(it), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlossaryTab() {
    var q by rememberSaveable { mutableStateOf("") }
    val list = remember(q) {
        val qq = q.trim().lowercase()
        if (qq.isEmpty()) LearnContent.terms
        else LearnContent.terms.filter { it.term.lowercase().contains(qq) || it.meaning.lowercase().contains(qq) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Terim ara (RSI, stablecoin, kaldıraç…)") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.term }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.term, fontWeight = FontWeight.Bold, color = Accent)
                        Text(t.meaning, fontSize = 13.sp)
                    }
                }
            }
            if (list.isEmpty()) item { Text("Bulunamadı.", color = Muted) }
        }
    }
}
