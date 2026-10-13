package com.kriptoradar

import com.kriptoradar.analysis.DayPrice
import com.kriptoradar.analysis.SignalEngine
import com.kriptoradar.analysis.Simulator
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.Kline
import com.kriptoradar.learn.BeginnerBrief
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SimulatorTest {
    private val day = 86_400_000L

    @Test fun qtyWithFee() {
        // 1000 TL, fiyat 100 TL, %0,2 komisyon → 9,98 adet
        assertEquals(9.98, Simulator.qtyFor(1000.0, 100.0, 0.2), 1e-9)
    }

    @Test fun whatIfDoubling() {
        val s = List(31) { DayPrice(it * day, 100.0 + it * 100.0 / 30) } // 100 → 200
        val w = Simulator.whatIf(s, 30, 1000.0, 0.0, 200.0)!!
        assertEquals(2000.0, w.valueNow, 1e-6)
        assertEquals(1000.0, w.worstValue, 1e-6)
        assertEquals(100.0, w.pct, 1e-6)
    }

    @Test fun tlSeriesUsesDailyRate() {
        fun k(t: Long, c: Double) = Kline(t, c, c, c, c, 1.0, 1.0)
        val coin = listOf(k(0, 10.0), k(day, 20.0))
        val rate = listOf(k(0, 30.0), k(day, 40.0))
        val s = Simulator.series(coin, rate)
        assertEquals(300.0, s[0].price, 1e-9); assertEquals(800.0, s[1].price, 1e-9)
    }

    @Test fun forecastOfFlatSeriesHasNoGain() {
        val s = List(400) { DayPrice(it * day, 100.0) }
        val f = Simulator.forecast(s, 7, 1000.0, 0.0)!!
        assertEquals(1000.0, f.p50, 1e-9); assertEquals(0.0, f.lossProbability, 1e-9)
    }

    @Test fun liveBitcoinTl() = runBlocking {
        val coin = runCatching { BinanceApi.klines("BTCUSDT", "1d", 1000) }.getOrNull()
        assumeTrue(coin != null)
        val rate = BinanceApi.klines("USDTTRY", "1d", 1000)
        val s = Simulator.series(coin!!, rate)
        val now = s.last().price
        println("BTC ≈ ${"%.0f".format(now)} TL, ${s.size} gün veri")
        println("1000 TL ile şimdi: ${"%.6f".format(Simulator.qtyFor(1000.0, now, 0.2))} BTC")
        listOf(7, 30, 365).forEach { d ->
            val w = Simulator.whatIf(s, d, 1000.0, 0.2, now)!!
            println("$d gün önce 1000 TL → ${"%.0f".format(w.valueNow)} TL (en kötü ${"%.0f".format(w.worstValue)})")
        }
        val dca = Simulator.dca(s, 365, 7, 1000.0, 0.2, now)!!
        println("DCA ${dca.buys} alım → ${"%.0f".format(dca.valueNow)} TL, tek seferde → ${"%.0f".format(dca.lumpValueNow)} TL")
        listOf(7, 30).forEach { h ->
            val f = Simulator.forecast(s, h, 1000.0, 0.2)!!
            println("$h gün: zarar ihtimali %${(f.lossProbability * 100).toInt()}, kötü ${"%.0f".format(f.p10)}, orta ${"%.0f".format(f.p50)}, iyi ${"%.0f".format(f.p90)} (${f.samples} dönem)")
        }
        assertTrue(now > 100_000)
    }

    @Test fun liveBeginnerBrief() = runBlocking {
        val tickers = runCatching { BinanceApi.tickers24h() }.getOrNull()
        assumeTrue(tickers != null)
        val map = tickers!!.associateBy { it.symbol }
        val b = BeginnerBrief.build(SignalEngine.scan(map, 30), map, 100)!!
        println("${b.moodEmoji} ${b.moodTitle}: ${b.moodText}\n${b.btcText}")
        (b.safer + b.rising + b.reversals + b.avoid).forEach { p ->
            println("\n${p.risk.emoji} ${p.headline} [${p.risk.label}]" + (p.about?.let { " – $it" } ?: ""))
            p.reasons.forEach { println("   • $it") }
            p.caution?.let { println("   ⚠ $it") }
        }
    }
}

class MonteCarloTest {
    private val day = 86_400_000L

    @Test fun flatSeriesWithFeesAlwaysLoses() {
        val s = List(400) { DayPrice(it * day, 100.0) }
        val mc = Simulator.monteCarlo(s, 30, 1000.0, 0.2, seed = 1)!!
        assertEquals(1.0, mc.lossProbability, 1e-9)
        assertEquals(60, mc.paths.size)
        assertEquals(31, mc.p50.size)
    }

    @Test fun steadyGrowthNeverLoses() {
        val s = List(400) { DayPrice(it * day, 100.0 * Math.pow(1.002, it.toDouble())) }
        val mc = Simulator.monteCarlo(s, 30, 1000.0, 0.0, seed = 7)!!
        assertEquals(0.0, mc.lossProbability, 1e-9)
        assertTrue(mc.p10.last() <= mc.p50.last() && mc.p50.last() <= mc.p90.last())
    }

    @Test fun sameSeedSameResult() {
        val s = List(400) { DayPrice(it * day, 100.0 + 10 * Math.sin(it / 5.0)) }
        val a = Simulator.monteCarlo(s, 7, 1000.0, 0.1, seed = 42)!!
        val b = Simulator.monteCarlo(s, 7, 1000.0, 0.1, seed = 42)!!
        assertEquals(a.lossProbability, b.lossProbability, 0.0)
    }

    @Test fun liveBitcoinMonteCarlo() = kotlinx.coroutines.runBlocking {
        val coin = runCatching { BinanceApi.klines("BTCUSDT", "1d", 1000) }.getOrNull()
        org.junit.Assume.assumeTrue(coin != null)
        val s = Simulator.series(coin!!, null)
        val t0 = System.nanoTime()
        val mc = Simulator.monteCarlo(s, 90, 1000.0, 0.2, seed = 1)!!
        val ms = (System.nanoTime() - t0) / 1e6
        println("BTC 3 ay MC: zarar %${(mc.lossProbability * 100).toInt()}, p10=${"%.0f".format(mc.p10.last())} p50=${"%.0f".format(mc.p50.last())} p90=${"%.0f".format(mc.p90.last())} ($ms ms)")
        assertTrue("çok yavaş: $ms ms", ms < 1500)
    }
}
