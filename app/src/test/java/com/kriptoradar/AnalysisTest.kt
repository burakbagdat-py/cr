package com.kriptoradar

import com.kriptoradar.analysis.Indicators
import com.kriptoradar.analysis.SignalEngine
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.Kline
import com.kriptoradar.data.PriceHistory
import com.kriptoradar.data.Ticker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AnalysisTest {
    @Test fun rsiOfMonotonicRiseIs100() {
        val r = Indicators.rsi((1..50).map { it.toDouble() })
        assertEquals(100.0, r.last(), 1e-9)
    }

    @Test fun rsiOfAlternatingIsNear50() {
        val r = Indicators.rsi((0 until 200).map { if (it % 2 == 0) 100.0 else 101.0 })
        assertEquals(50.0, r.last(), 2.0)
    }

    @Test fun emaOfConstantIsConstant() {
        assertEquals(5.0, Indicators.ema(List(60) { 5.0 }, 20).last(), 1e-9)
    }

    @Test fun priceHistoryWindowChange() {
        val h = PriceHistory()
        val t0 = 1_000_000L
        for (i in 0..120) h.add(t0 + i * 5_000L, 100.0 + i * 0.1) // 10 dk
        val now = t0 + 120 * 5_000L
        val c = h.changePct(now, 5 * 60_000L, 112.0)!!
        // 5 dk önce fiyat 100 + 60*0.1 = 106
        assertEquals((112.0 - 106.0) / 106.0 * 100, c, 1e-6)
        assertNull(h.changePct(now, 60 * 60_000L, 112.0)) // yeterli geçmiş yok
    }

    @Test fun splitSymbol() {
        assertEquals("BTC" to "USDT", BinanceApi.splitSymbol("BTCUSDT"))
        assertEquals("BTC" to "TRY", BinanceApi.splitSymbol("BTCTRY"))
        assertEquals("ETH" to "BTC", BinanceApi.splitSymbol("ETHBTC"))
    }

    @Test fun analyzeSyntheticUptrendScoresHigherThanDowntrend() {
        fun series(drift: Double) = (0 until 300).map { i ->
            val p = 100 * Math.pow(1 + drift, i.toDouble()) * (1 + 0.004 * Math.sin(i / 3.0))
            Kline(i * 3_600_000L, p, p * 1.003, p * 0.997, p, 1000.0, 1000.0 * p)
        }
        fun tick(k: List<Kline>, chg: Double) = Ticker("XUSDT", "X", "USDT", k.last().close, k.last().close / (1 + chg / 100),
            k.last().close, k.last().close, 1.0, 1e8, 0)
        val up = series(0.002); val down = series(-0.002)
        val su = SignalEngine.analyze(tick(up, 5.0), up, up.takeLast(60), 0.0)!!
        val sd = SignalEngine.analyze(tick(down, -5.0), down, down.takeLast(60), 0.0)!!
        assertTrue("up=${su.score} down=${sd.score}", su.score > sd.score + 20)
    }

    /** Gerçek Binance verisiyle uçtan uca tarama (ağ yoksa atlanır). */
    @Test fun liveScan() = runBlocking {
        val tickers = runCatching { BinanceApi.tickers24h() }.getOrNull()
        assumeTrue("ağ yok", tickers != null)
        val map = tickers!!.associateBy { it.symbol }
        assertNotNull(map["BTCUSDT"])
        val res = SignalEngine.scan(map, 15)
        println("Tarama: ${res.signals.size} coin, F&G=${res.fearGreed} ${res.fearGreedLabel}, BTC dom=${res.btcDominance}")
        res.signals.forEach {
            println("${it.base.padEnd(8)} skor=${it.score} ${it.label} RSI=${it.rsi.toInt()} hacim=${"%.2f".format(it.volumeRatio)}x isabet=${"%.2f".format(it.histWinRate)} (${it.histSamples})")
        }
        assertTrue(res.signals.size >= 10)
        assertTrue(res.signals.all { it.score in 0..100 })
    }
}
