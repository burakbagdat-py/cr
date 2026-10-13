package com.kriptoradar.analysis

import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.Factor
import com.kriptoradar.data.Kline
import com.kriptoradar.data.ScanResult
import com.kriptoradar.data.Signal
import com.kriptoradar.data.Ticker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Yatırım tavsiyesi DEĞİLDİR. Tamamen geçmiş fiyat/hacim istatistiklerinden
 * türetilen 0-100 arası bir "teknik güç" skoru üretir.
 *
 * Bileşenler (ağırlıklı, her biri -1..+1):
 *  - Trend (1s): fiyat > EMA20 > EMA50
 *  - Günlük trend: kapanış vs günlük EMA20
 *  - Momentum: MACD histogramı yönü ve eğimi
 *  - RSI: 50-68 güçlü, >75 aşırı alım cezası, <30 tepki potansiyeli
 *  - Hacim: son 6 saat / önceki 48 saat ortalaması
 *  - Göreceli güç: BTC'ye göre 24s performans
 *  - Bollinger konumu / sıkışma
 *  - Oynaklık cezası (ATR)
 * Ek olarak: son ~500 saatte benzer koşulların 12 saat sonraki getirileri (isabet oranı).
 */
object SignalEngine {
    private data class W(val trend: Double = 0.20, val daily: Double = 0.15, val macd: Double = 0.15,
                         val rsi: Double = 0.15, val volume: Double = 0.12, val rel: Double = 0.13,
                         val boll: Double = 0.05, val vol: Double = 0.05)
    private val w = W()

    suspend fun scan(tickers: Map<String, Ticker>, universe: Int, quote: String = "USDT"): ScanResult = coroutineScope {
        val candidates = tickers.values
            .filter { it.quote == quote && BinanceApi.isTradable(it.base) && it.quoteVolume > 0 }
            .sortedByDescending { it.quoteVolume }
            .take(universe)
        val btc = tickers["BTC$quote"] ?: tickers["BTCUSDT"]
        val btcChange = btc?.change24hPct ?: 0.0

        val sem = Semaphore(6)
        val fg = async { BinanceApi.fearGreed() }
        val gl = async { BinanceApi.global() }
        val signals = candidates.map { t ->
            async {
                sem.withPermit {
                    runCatching {
                        val h1 = BinanceApi.klines(t.symbol, "1h", 500)
                        val d1 = BinanceApi.klines(t.symbol, "1d", 60)
                        analyze(t, h1, d1, btcChange)
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull().sortedByDescending { it.score }

        val f = fg.await(); val g = gl.await()
        ScanResult(System.currentTimeMillis(), signals, f?.first, f?.second, g?.first, g?.second)
    }

    fun analyze(t: Ticker, h1: List<Kline>, d1: List<Kline>, btcChange24h: Double): Signal? {
        if (h1.size < 120) return null
        val closes = h1.map { it.close }
        val last = t.price.takeIf { it > 0 } ?: closes.last()
        val factors = mutableListOf<Factor>()

        // 1) Saatlik trend
        val e20 = Indicators.ema(closes, 20).last()
        val e50 = Indicators.ema(closes, 50).last()
        val e200 = Indicators.ema(closes, 200).last()
        var trend = 0.0
        if (last > e20) trend += 0.35 else trend -= 0.35
        if (e20 > e50) trend += 0.35 else trend -= 0.35
        if (!e200.isNaN()) { if (last > e200) trend += 0.3 else trend -= 0.3 }
        factors += Factor("Trend (1s)", trend,
            "Fiyat EMA20 ${cmp(last, e20)}, EMA20 EMA50 ${cmp(e20, e50)}" + if (!e200.isNaN()) ", fiyat EMA200 ${cmp(last, e200)}" else "")

        // 2) Günlük trend
        var daily = 0.0
        if (d1.size >= 21) {
            val dc = d1.map { it.close }
            val de20 = Indicators.ema(dc, 20).last()
            val ch7 = Indicators.pctChange(dc[dc.size - 8], last)
            daily = (if (last > de20) 0.6 else -0.6) + (ch7 / 20).coerceIn(-0.4, 0.4)
            factors += Factor("Günlük trend", daily, "Günlük EMA20 ${if (last > de20) "üstünde" else "altında"}, 7g değişim %${fmt(ch7)}")
        }

        // 3) MACD
        val m = Indicators.macd(closes)
        val hNow = m.hist.last(); val hPrev = m.hist[m.hist.size - 2]
        val macdS = when {
            hNow > 0 && hNow > hPrev -> 1.0
            hNow > 0 -> 0.4
            hNow < 0 && hNow > hPrev -> -0.1
            else -> -0.8
        }
        factors += Factor("MACD", macdS, when {
            hNow > 0 && hNow > hPrev -> "Pozitif ve güçleniyor"
            hNow > 0 -> "Pozitif, zayıflıyor"
            hNow > hPrev -> "Negatif, toparlanıyor"
            else -> "Negatif ve zayıflıyor"
        })

        // 4) RSI
        val rsiArr = Indicators.rsi(closes)
        val rsi = rsiArr.last()
        val rsiS = when {
            rsi >= 80 -> -0.8
            rsi >= 72 -> -0.3
            rsi >= 55 -> 0.9
            rsi >= 48 -> 0.4
            rsi >= 35 -> -0.4
            rsi >= 28 -> 0.1   // aşırı satıma yakın: tepki ihtimali
            else -> 0.3
        }
        factors += Factor("RSI(14)", rsiS, "RSI ${rsi.roundToInt()} " + when {
            rsi >= 72 -> "(aşırı alım bölgesi)"
            rsi >= 55 -> "(güçlü momentum)"
            rsi < 30 -> "(aşırı satım – tepki potansiyeli)"
            else -> "(nötr)"
        })

        // 5) Hacim
        val v6 = h1.takeLast(6).map { it.quoteVolume }.average()
        val v48 = h1.dropLast(6).takeLast(48).map { it.quoteVolume }.average().takeIf { it > 0 } ?: v6
        val volRatio = v6 / v48
        val pr6 = Indicators.pctChange(closes[closes.size - 7], last)
        val volS = when {
            volRatio > 1.5 && pr6 > 0 -> 1.0
            volRatio > 1.5 && pr6 < 0 -> -0.8
            volRatio > 1.1 -> if (pr6 >= 0) 0.4 else -0.3
            volRatio < 0.6 -> -0.3
            else -> 0.0
        }
        factors += Factor("Hacim", volS, "Son 6s hacim ortalamanın ${fmt(volRatio)} katı, fiyat %${fmt(pr6)}")

        // 6) BTC'ye göre göreceli güç
        val rel = t.change24hPct - btcChange24h
        val relS = (rel / 6.0).coerceIn(-1.0, 1.0)
        factors += Factor("BTC'ye göre güç", relS, "24s: %${fmt(t.change24hPct)} (BTC %${fmt(btcChange24h)})")

        // 7) Bollinger
        val bb = Indicators.bollinger(closes)
        var bollS = 0.0
        if (bb != null) {
            val (pctB, width) = bb
            bollS = when {
                pctB > 1.05 -> -0.4
                pctB in 0.55..1.0 -> 0.6
                pctB < 0.0 -> 0.2
                else -> 0.0
            }
            if (width < 3.0) bollS += 0.3 // sıkışma: kırılım öncesi olabilir
            factors += Factor("Bollinger", bollS.coerceIn(-1.0, 1.0), "%B ${fmt(pctB)}, bant genişliği %${fmt(width)}" + if (width < 3.0) " (sıkışma)" else "")
        }

        // 8) Oynaklık
        val atr = Indicators.atrPct(h1)
        val volaS = when { atr > 4 -> -1.0; atr > 2.5 -> -0.5; atr < 0.4 -> -0.2; else -> 0.2 }
        factors += Factor("Oynaklık", volaS, "Saatlik ATR %${fmt(atr)}" + if (atr > 2.5) " (yüksek risk)" else "")

        val raw = trend * w.trend + daily * w.daily + macdS * w.macd + rsiS * w.rsi +
            volS * w.volume + relS * w.rel + bollS.coerceIn(-1.0, 1.0) * w.boll + volaS * w.vol
        val score = ((raw + 1) / 2 * 100).roundToInt().coerceIn(0, 100)

        // Geçmiş istatistik: benzer koşul (fiyat>EMA20, MACD hist işareti aynı, RSI aynı 10'luk dilim)
        val e20Arr = Indicators.ema(closes, 20)
        val horizon = 12
        val bucket = (rsi / 10).toInt()
        var wins = 0; var n = 0; var sum = 0.0
        for (i in 60 until closes.size - horizon) {
            val r = rsiArr[i]; val hh = m.hist[i]
            if (r.isNaN() || hh.isNaN() || e20Arr[i].isNaN()) continue
            if ((r / 10).toInt() != bucket) continue
            if ((closes[i] > e20Arr[i]) != (last > e20)) continue
            if ((hh > 0) != (hNow > 0)) continue
            val ret = Indicators.pctChange(closes[i], closes[i + horizon])
            sum += ret; n++; if (ret > 0) wins++
        }

        val label = when {
            score >= 75 -> "Güçlü teknik görünüm"
            score >= 60 -> "Olumlu"
            rsi < 30 && score >= 40 -> "Aşırı satım – tepki adayı"
            score >= 45 -> "Nötr"
            score >= 30 -> "Zayıf"
            else -> "Çok zayıf"
        }

        return Signal(
            symbol = t.symbol, base = t.base, price = last, score = score, label = label,
            change24h = t.change24hPct, rsi = rsi, volumeRatio = volRatio, factors = factors,
            histWinRate = if (n >= 5) wins.toDouble() / n else -1.0,
            histAvgReturn = if (n > 0) sum / n else 0.0, histSamples = n, volatilityPct = atr,
        )
    }

    private fun cmp(a: Double, b: Double) = if (a > b) "üstünde" else "altında"
    fun fmt(v: Double): String = if (abs(v) >= 100) "%.0f".format(v) else "%.2f".format(v)
}
