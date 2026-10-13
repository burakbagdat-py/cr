package com.kriptoradar.learn

import com.kriptoradar.data.ScanResult
import com.kriptoradar.data.Signal
import com.kriptoradar.data.Ticker
import com.kriptoradar.ui.Format
import kotlin.math.roundToInt

enum class Risk(val label: String, val emoji: String, val explain: String) {
    LOW("Görece düşük risk", "🟢", "Büyük ve köklü bir coin; yine de %20-30'luk düşüşler yaşanabilir."),
    MEDIUM("Orta risk", "🟡", "Bilinen bir coin ama Bitcoin'den daha oynak."),
    HIGH("Yüksek risk", "🟠", "Fiyatı sert hareket ediyor; kısa sürede büyük kayıp mümkün."),
    VERY_HIGH("Çok yüksek risk", "🔴", "Çok oynak veya küçük bir coin (memecoin olabilir). Sadece çok küçük tutarlar için."),
}

data class Pick(
    val signal: Signal,
    val risk: Risk,
    val headline: String,
    val reasons: List<String>,
    val caution: String?,
    val about: String?,
)

data class Brief(
    val moodEmoji: String,
    val moodTitle: String,
    val moodText: String,
    val btcText: String?,
    val rising: List<Pick>,
    val safer: List<Pick>,
    val reversals: List<Pick>,
    val avoid: List<Pick>,
    val tip: String,
    val scannedAt: Long,
)

/** Teknik analizi hiç bilgisi olmayan biri için sade Türkçeye çevirir. */
object BeginnerBrief {
    private val memes = setOf("DOGE", "SHIB", "PEPE", "BONK", "WIF", "FLOKI", "TRUMP", "MEME", "BOME", "NEIRO", "PNUT", "TURBO", "PEOPLE", "1000SATS", "MUBARAK")
    private val majors = setOf("BTC", "ETH")
    private val large = setOf("BNB", "SOL", "XRP", "ADA", "TRX", "LTC", "LINK", "AVAX", "DOT", "BCH", "TON", "XLM")

    fun risk(s: Signal, t: Ticker?): Risk {
        val vol = t?.quoteVolume ?: 0.0
        val known = s.base in LearnContent.coinInfo
        return when {
            s.base in majors -> Risk.LOW
            !known && (s.volatilityPct > 1.5 || vol < 2e7) -> Risk.VERY_HIGH
            !known -> Risk.HIGH
            s.base in memes || s.volatilityPct > 2.5 || vol < 5e6 -> Risk.VERY_HIGH
            s.base in large && s.volatilityPct < 1.5 -> Risk.MEDIUM
            s.volatilityPct > 1.5 || vol < 3e7 -> Risk.HIGH
            else -> Risk.MEDIUM
        }
    }

    fun build(scan: ScanResult?, tickers: Map<String, Ticker>, dayOfYear: Int): Brief? {
        if (scan == null || scan.signals.isEmpty()) return null
        val sigs = scan.signals
        val avg = sigs.map { it.score }.average()
        val fg = scan.fearGreed

        val (emoji, title) = when {
            avg >= 60 -> "😊" to "Piyasa iyimser"
            avg >= 50 -> "🙂" to "Piyasa hafif olumlu"
            avg >= 42 -> "😐" to "Piyasa kararsız"
            avg >= 35 -> "😟" to "Piyasa zayıf"
            else -> "😨" to "Piyasa çok zayıf"
        }
        val rising = sigs.count { it.score >= 60 }
        var mood = "Taranan ${sigs.size} coinin $rising tanesi yükseliş eğiliminde görünüyor (ortalama teknik puan ${avg.roundToInt()}/100)."
        if (fg != null) mood += " Korku/Açgözlülük endeksi $fg (${scan.fearGreedLabel}): " + when {
            fg < 25 -> "insanlar çok korkmuş durumda. Bu dönemlerde fiyatlar genelde düşmüş olur; bazı yatırımcılar bunu fırsat görür, ama düşüş sürebilir."
            fg < 45 -> "piyasada tedirginlik var."
            fg < 56 -> "piyasa dengede."
            fg < 75 -> "insanlar iyimser."
            else -> "insanlar aşırı coşkulu. Bu dönemlerde fiyatlar şişmiş olabilir; tepeden alma riskine dikkat."
        }

        val btc = sigs.firstOrNull { it.base == "BTC" }
        val btcText = btc?.let {
            "Bitcoin şu an ${Format.price(it.price)} $ (24 saatte ${Format.signedPct(it.change24h)}). " + when {
                it.score >= 60 -> "Teknik görünümü olumlu; bu genelde diğer coinleri de destekler."
                it.score >= 45 -> "Teknik görünümü nötr; belirgin bir yön yok."
                else -> "Teknik görünümü zayıf. Bitcoin zayıfken altcoinler genelde daha sert düşer, dikkatli ol."
            }
        }

        fun pick(s: Signal, reversal: Boolean = false): Pick {
            val t = tickers[s.symbol]
            return Pick(s, risk(s, t), headline(s, reversal), if (reversal) reversalReasons(s) else reasons(s), caution(s), LearnContent.coinInfo[s.base])
        }

        val risingPicks = sigs.filter { it.score >= 60 && it.reversal?.candidate != true }
            .sortedByDescending { it.score + (if (it.histWinRate > 0.55) 5 else 0) }.take(5).map { pick(it) }
        val saferPicks = sigs.filter { it.score >= 55 && (it.base in majors || it.base in large) }
            .sortedByDescending { it.score }.take(3).map { pick(it) }
            .filter { p -> p.risk == Risk.LOW || p.risk == Risk.MEDIUM }
        val revPicks = sigs.filter { it.reversal?.candidate == true }
            .sortedByDescending { it.reversal!!.score }.take(3).map { pick(it, reversal = true) }
        val avoidPicks = sigs.filter { it.score < 30 && it.reversal?.candidate != true }
            .sortedBy { it.score }.take(3).map { s ->
                val t = tickers[s.symbol]
                Pick(s, risk(s, t), "${s.base} düşüş eğiliminde",
                    listOf("Fiyatı son ortalamalarının altında ve satıcılar baskın.", "Göstergeler henüz dönüş işareti vermiyor."),
                    "\"Ucuzladı\" diye almak düşen bıçağı tutmaya benzeyebilir.", LearnContent.coinInfo[s.base])
            }

        return Brief(emoji, title, mood, btcText, risingPicks, saferPicks, revPicks, avoidPicks,
            LearnContent.tips[dayOfYear % LearnContent.tips.size], scan.at)
    }

    private fun headline(s: Signal, reversal: Boolean) = when {
        reversal -> "${s.base} çok düşmüştü, toparlanmaya başlamış olabilir"
        s.score >= 75 -> "${s.base} güçlü yükseliş eğiliminde görünüyor"
        else -> "${s.base} yükseliş eğiliminde olabilir"
    }

    private fun reasons(s: Signal): List<String> {
        val out = mutableListOf<String>()
        fun f(name: String) = s.factors.firstOrNull { it.name.startsWith(name) }
        f("Trend")?.takeIf { it.score > 0.3 }?.let { out += "Fiyatı son günlerin ortalamasının üzerinde; yani genel yön yukarı." }
        f("Günlük")?.takeIf { it.score > 0.3 }?.let { out += "Günlük grafikte de yükseliş trendi var (daha uzun vadede de olumlu)." }
        f("MACD")?.takeIf { it.score > 0.5 }?.let { out += "Yükseliş gücü artıyor (MACD güçleniyor)." }
        when {
            s.rsi in 55.0..70.0 -> out += "Alıcılar satıcılardan güçlü ama fiyat henüz \"aşırı pahalı\" bölgede değil (RSI ${s.rsi.roundToInt()})."
            s.rsi > 72 -> out += "Dikkat: çok hızlı yükselmiş (RSI ${s.rsi.roundToInt()}), kısa vadede geri çekilebilir."
        }
        if (s.volumeRatio > 1.3) out += "İlgi artıyor: son saatlerde işlem hacmi normalin ${Format.num(s.volumeRatio)} katı."
        f("BTC")?.takeIf { it.score > 0.3 }?.let { out += "Bitcoin'den daha iyi performans gösteriyor." }
        if (s.histWinRate >= 0.55 && s.histSamples >= 10)
            out += "Geçmişte bu coinde aynı durum ${s.histSamples} kez oluşmuş; 12 saat sonra fiyat %${(s.histWinRate * 100).roundToInt()} oranında daha yüksekmiş."
        if (out.isEmpty()) out += "Göstergelerin çoğu olumlu yönde."
        return out
    }

    private fun reversalReasons(s: Signal): List<String> {
        val r = s.reversal ?: return reasons(s)
        val out = mutableListOf<String>()
        out += "Son 30 günün zirvesinden %${r.drawdown30.roundToInt()} aşağıda – yani epey ucuzlamış."
        out += "En dipten bu yana %${Format.num(r.bounceFromLow)} toparlanmış ve dibe tekrar inmemiş."
        if (r.reasons.any { it.contains("RSI") && it.contains("toparlan") }) out += "Satış baskısı azalıyor, alıcılar geri geliyor (RSI yükseliyor)."
        if (r.reasons.any { it.contains("EMA9") }) out += "Kısa vadeli yön yukarı döndü (kısa ortalama uzun ortalamayı yukarı kesti)."
        if (r.reasons.any { it.contains("MACD") }) out += "Yükseliş gücü artmaya başladı (MACD pozitife döndü)."
        if (r.reasons.any { it.contains("hacim") }) out += "Alım yönlü işlemler satışlardan fazla."
        if (r.histWinRate >= 0) out += "Bu coinde aynı desen ${r.histSamples} kez oluşmuş; 24 saat sonra %${(r.histWinRate * 100).roundToInt()} oranında yükselmiş."
        return out
    }

    private fun caution(s: Signal): String? = when {
        s.change24h > 20 -> "Son 24 saatte %${s.change24h.roundToInt()} yükselmiş. Bu kadar hızlı yükselişlerden sonra sert düşüşler sık görülür; geç kalmış olabilirsin."
        s.rsi > 72 -> "Çok hızlı yükselmiş; kısa vadede geri çekilme ihtimali yüksek."
        s.volatilityPct > 2.5 -> "Çok oynak: saatte ortalama %${Format.num(s.volatilityPct)} hareket ediyor."
        s.histWinRate in 0.0..0.45 && s.histSamples >= 10 -> "Geçmiş isabeti zayıf (%${(s.histWinRate * 100).roundToInt()}); göstergeler bu coinde pek tutmamış."
        else -> null
    }
}
