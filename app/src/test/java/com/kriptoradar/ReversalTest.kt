package com.kriptoradar

import com.kriptoradar.analysis.ReversalDetector
import com.kriptoradar.analysis.SignalEngine
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.Kline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sin

class ReversalTest {
    private fun candles(prices: List<Double>, stepMs: Long): List<Kline> = prices.mapIndexed { i, p ->
        val o = if (i == 0) p else prices[i - 1]
        Kline(i * stepMs, o, maxOf(o, p) * 1.001, minOf(o, p) * 0.999, p, 1000.0, 1000.0 * p)
    }

    /** 90 gün yatay (100), sonra 58'e sert düşüş, son günler toparlanma. */
    private fun dailyDump(end: Double) = candles(
        List(90) { 100.0 + sin(it.toDouble()) } +
            List(26) { 100.0 - 42.0 * (it + 1) / 26 } +
            List(4) { 58.0 + (end - 58.0) * (it + 1) / 4 },
        86_400_000L,
    )

    private fun hourly(recover: Boolean): List<Kline> {
        val p = ArrayList<Double>()
        for (i in 0 until 440) p += 75.0 - 13.0 * i / 440 + 0.3 * sin(i / 2.0)      // 75 → 62 yavaş düşüş
        for (i in 0 until 30) p += 62.0 - 4.0 * (i + 1) / 30                      // 62 → 58 sert düşüş
        for (i in 0 until 30) p += if (recover) 58.0 + 5.0 * (i + 1) / 30 + 0.05 * sin(i.toDouble())
                                   else 58.0 - 5.0 * (i + 1) / 30                 // dönüş ya da düşüş devam
        return candles(p, 3_600_000L)
    }

    @Test fun detectsDumpThenRecovery() {
        val h = hourly(recover = true)
        val r = ReversalDetector.detect(h, dailyDump(63.0), h.last().close)!!
        println("Dönüş: skor=${r.score} derinlik=${r.depth} dönüş=${r.turn}\n" + r.reasons.joinToString("\n"))
        assertTrue("derinlik ${r.depth}", r.depth >= 0.45)
        assertTrue("dönüş ${r.turn}", r.turn >= 0.5)
        assertTrue(r.candidate)
    }

    @Test fun rejectsFallingKnife() {
        val h = hourly(recover = false)
        val r = ReversalDetector.detect(h, dailyDump(53.0), h.last().close)!!
        println("Düşen bıçak: skor=${r.score} dönüş=${r.turn}")
        assertFalse(r.candidate)
    }

    @Test fun rejectsHealthyUptrend() {
        val h = candles(List(500) { 50.0 * (1 + 0.001 * it) + 0.2 * sin(it / 3.0) }, 3_600_000L)
        val d = candles(List(120) { 30.0 + 0.2 * it }, 86_400_000L)
        val r = ReversalDetector.detect(h, d, h.last().close)!!
        assertTrue("derinlik ${r.depth}", r.depth < 0.2)
        assertFalse(r.candidate)
    }

    @Test fun regressionSlope() {
        val (slope, r2) = ReversalDetector.regression(List(10) { 2.0 * it + 1 })
        assertEquals(2.0, slope, 1e-9); assertEquals(1.0, r2, 1e-9)
    }

    @Test fun liveReversalScan() = runBlocking {
        val tickers = runCatching { BinanceApi.tickers24h() }.getOrNull()
        assumeTrue("ağ yok", tickers != null)
        val res = SignalEngine.scan(tickers!!.associateBy { it.symbol }, 40)
        println("Taranan: ${res.signals.size}, desen geçmişi: ${res.revBaseSamples} örnek, isabet=${res.revBaseWinRate}, ort=${res.revBaseAvgReturn}")
        res.signals.filter { it.reversal != null }.sortedByDescending { it.reversal!!.score }.take(8).forEach {
            val r = it.reversal!!
            println("${it.base.padEnd(8)} dip=${r.score} aday=${r.candidate} derinlik=${"%.2f".format(r.depth)} dönüş=${"%.2f".format(r.turn)} dd30=%${"%.1f".format(r.drawdown30)} | ${r.reasons.take(3).joinToString("; ")}")
        }
        assertTrue(res.signals.isNotEmpty())
    }
}
