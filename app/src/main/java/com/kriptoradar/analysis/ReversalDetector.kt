package com.kriptoradar.analysis

import com.kriptoradar.data.Kline
import com.kriptoradar.data.Reversal
import kotlin.math.roundToInt

/**
 * "Dip dönüş" dedektörü: çok düşmüş (aşırı satılmış) AMA grafiği yukarı dönmeye
 * başlamış coinleri tamamen istatistiksel ölçütlerle otomatik bulur.
 *
 * 1) Derinlik (ne kadar düşük?) 0..1
 *    - 30 günlük zirveden düşüş yüzdesi
 *    - 30 günlük fiyat aralığındaki konum (alt bölgede mi?)
 *    - Son 7 günde günlük RSI'ın gördüğü en düşük değer
 *
 * 2) Dönüş (yükselmeye meyilli mi?) 0..1
 *    - Saatlik RSI aşırı satımdan toparlanıyor
 *    - EMA9 > EMA21 (kısa vadeli trend yukarı döndü)
 *    - MACD histogramı pozitife döndü / güçleniyor
 *    - Dip yapıldı ve daha yüksek dip oluştu (dip tekrar test edilmedi)
 *    - Pozitif RSI uyumsuzluğu (fiyat daha düşük dip, RSI daha yüksek dip)
 *    - Son 24 saatin doğrusal regresyon eğimi pozitif ve tutarlı (R²)
 *    - Yükselen mumların hacmi düşen mumlardan fazla
 *
 * Aday = derinlik ≥ 0.45, 30g zirveden ≥ %15 aşağıda, dönüş ≥ 0.5 ve son 6 saatte çöküş yok.
 */
object ReversalDetector {
    const val HORIZON = 24 // geçmiş istatistik için ileri bakış (saat)

    fun detect(h1: List<Kline>, d1: List<Kline>, price: Double): Reversal? {
        if (h1.size < 150 || d1.size < 20) return null
        val reasons = mutableListOf<String>()
        val closes = h1.map { it.close }
        val last = if (price > 0) price else closes.last()

        // ---------- 1) DERİNLİK ----------
        val d30 = d1.takeLast(30)
        val high30 = maxOf(d30.maxOf { it.high }, last)
        val low30 = minOf(d30.minOf { it.low }, h1.takeLast(24).minOf { it.low })
        val dd30 = (high30 - last) / high30 * 100
        val pos30 = if (high30 > low30) (last - low30) / (high30 - low30) else 0.5
        val dRsi = Indicators.rsi(d1.map { it.close })
        val dRsiMin = dRsi.takeLast(7).filter { !it.isNaN() }.minOrNull() ?: 50.0

        val ddS = ((dd30 - 10) / 30).coerceIn(0.0, 1.0)
        val posS = ((0.4 - pos30) / 0.4).coerceIn(0.0, 1.0)
        val rsiDS = ((45 - dRsiMin) / 20).coerceIn(0.0, 1.0)
        val depth = 0.4 * ddS + 0.3 * posS + 0.3 * rsiDS
        if (dd30 >= 10) reasons += "30 günlük zirveden %${f(dd30)} aşağıda"
        if (pos30 < 0.4) reasons += "30 günlük fiyat aralığının alt bölgesinde (konum %${(pos30 * 100).roundToInt()})"
        if (dRsiMin < 40) reasons += "Günlük RSI son 7 günde ${dRsiMin.roundToInt()} seviyesine kadar indi"

        // ---------- 2) DÖNÜŞ ----------
        var turn = 0.0
        val rsi = Indicators.rsi(closes)
        val rsiNow = rsi.last()
        val rsiMin48 = rsi.takeLast(48).filter { !it.isNaN() }.minOrNull() ?: rsiNow
        if (rsiMin48 < 35 && rsiNow >= rsiMin48 + 12 && rsiNow > 40) {
            turn += 0.20; reasons += "Saatlik RSI ${rsiMin48.roundToInt()} → ${rsiNow.roundToInt()} toparlandı"
        } else if (rsiMin48 < 40 && rsiNow >= rsiMin48 + 8) {
            turn += 0.10; reasons += "Saatlik RSI dipten yükseliyor (${rsiMin48.roundToInt()} → ${rsiNow.roundToInt()})"
        }

        val e9 = Indicators.ema(closes, 9); val e21 = Indicators.ema(closes, 21)
        val n = closes.size
        if (e9[n - 1] > e21[n - 1] && last > e21[n - 1]) {
            turn += 0.15
            val crossAgo = (1 until 48).firstOrNull { e9[n - 1 - it] <= e21[n - 1 - it] }
            reasons += if (crossAgo != null) "EMA9, EMA21'i yukarı kesti ($crossAgo saat önce)" else "Fiyat ve EMA9, EMA21'in üstünde"
        }

        val m = Indicators.macd(closes)
        val hist = m.hist
        val crossUp = (0 until 8).any { i -> hist[n - 1 - i] > 0 && hist[n - 2 - i] <= 0 }
        if (hist[n - 1] > 0 && (crossUp || hist[n - 1] > hist[n - 2])) {
            turn += 0.15; reasons += if (crossUp) "MACD histogramı pozitife döndü" else "MACD pozitif ve güçleniyor"
        } else if (hist[n - 1] < 0 && hist[n - 1] > hist[n - 2] && hist[n - 2] > hist[n - 3]) {
            turn += 0.05; reasons += "MACD negatif ama 3 saattir toparlanıyor"
        }

        // Dip ve daha yüksek dip
        val look = 120
        val start = n - look
        var bottomIdx = start
        for (i in start until n) if (h1[i].low < h1[bottomIdx].low) bottomIdx = i
        val bottom = h1[bottomIdx].low
        val sinceBottom = n - 1 - bottomIdx
        val bounce = (last - bottom) / bottom * 100
        if (sinceBottom in 6..100 && bounce >= 3) {
            val after = h1.subList(bottomIdx + 3, n)
            val higherLow = after.isNotEmpty() && after.minOf { it.low } > bottom * 1.01
            if (higherLow) {
                turn += 0.15; reasons += "Dipten %${f(bounce)} tepki ($sinceBottom saat önce), dip tekrar görülmedi"
            } else {
                turn += 0.05; reasons += "Dipten %${f(bounce)} tepki"
            }
        }

        // Pozitif RSI uyumsuzluğu
        val lows = (n - 96 until n - 3).filter { i ->
            (1..3).all { k -> h1[i].low <= h1[i - k].low && h1[i].low <= h1[i + k].low }
        }
        if (lows.size >= 2) {
            val a = lows[lows.size - 2]; val b = lows.last()
            if (b - a >= 5 && h1[b].low < h1[a].low && rsi[b] > rsi[a] + 3) {
                turn += 0.10; reasons += "Pozitif RSI uyumsuzluğu (fiyat daha düşük dip, RSI daha yüksek dip)"
            }
        }

        // Regresyon eğimi
        val (slope, r2) = regression(closes.takeLast(24))
        val proj24 = slope * 24 / last * 100
        if (proj24 > 0.5 && r2 > 0.35) {
            turn += (0.15 * (r2.coerceAtMost(0.9) / 0.9)).coerceAtMost(0.15)
            reasons += "Son 24s eğilim yukarı: ~%${f(proj24)}/gün (tutarlılık R² ${f(r2)})"
        }

        // Hacim yönü
        var upV = 0.0; var dnV = 0.0
        for (k in h1.takeLast(24)) if (k.close >= k.open) upV += k.quoteVolume else dnV += k.quoteVolume
        val vRatio = if (dnV > 0) upV / dnV else 2.0
        if (vRatio > 1.2) { turn += 0.10; reasons += "Alış yönlü hacim baskın (${f(vRatio)}x)" }

        turn = turn.coerceAtMost(1.0)
        val ch6 = Indicators.pctChange(closes[n - 7], last)
        val notCollapsing = ch6 > -3.0
        if (!notCollapsing) reasons += "⚠ Son 6 saatte %${f(ch6)} — düşüş sürüyor"

        val candidate = depth >= 0.45 && dd30 >= 15 && turn >= 0.5 && notCollapsing
        val score = (100 * (0.45 * depth + 0.55 * turn)).roundToInt().coerceIn(0, 100)

        // Geçmiş: bu coinde aynı desen (aşırı satım sonrası toparlanma + MACD>0 + fiyat>EMA21) 24s sonra ne yaptı?
        var wins = 0; var samples = 0; var sum = 0.0
        var i = 60
        while (i < n - HORIZON) {
            if (patternAt(i, closes, rsi, hist, e21)) {
                val ret = Indicators.pctChange(closes[i], closes[i + HORIZON])
                samples++; sum += ret; if (ret > 0) wins++
                i += HORIZON // örtüşen örnekleri sayma
            } else i++
        }

        return Reversal(score, candidate, depth, turn, dd30, bounce, reasons, wins, samples, sum)
    }

    /** Geçmiş istatistik için sadeleştirilmiş desen (sadece saatlik veriyle hesaplanabilir). */
    fun patternAt(i: Int, closes: List<Double>, rsi: DoubleArray, hist: DoubleArray, e21: DoubleArray): Boolean {
        if (i < 49 || rsi[i].isNaN() || hist[i].isNaN() || e21[i].isNaN()) return false
        var mn = Double.MAX_VALUE
        for (k in i - 48..i) if (!rsi[k].isNaN() && rsi[k] < mn) mn = rsi[k]
        return mn < 32 && rsi[i] >= mn + 12 && rsi[i] > 40 && hist[i] > 0 && closes[i] > e21[i]
    }

    /** En küçük kareler: eğim (birim/mum) ve R². */
    fun regression(y: List<Double>): Pair<Double, Double> {
        val n = y.size
        if (n < 3) return 0.0 to 0.0
        val mx = (n - 1) / 2.0
        val my = y.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) {
            val dx = i - mx; val dy = y[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        val slope = if (sxx > 0) sxy / sxx else 0.0
        val r2 = if (sxx > 0 && syy > 0) (sxy * sxy) / (sxx * syy) else 0.0
        return slope to r2
    }

    private fun f(v: Double) = "%.1f".format(v)
}
