package com.kriptoradar

import com.kriptoradar.data.Http
import com.kriptoradar.data.LedgerType
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.data.PaperOrder
import com.kriptoradar.data.PaperPortfolio
import com.kriptoradar.data.PaperPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperEngineTest {
    private val noFee = PaperPortfolio(feePct = 0.0, slippagePct = 0.0)

    @Test fun buyThenSellAtHigherPriceGivesProfit() {
        var p = PaperEngine.buy(noFee, "BTCUSDT", "BTC", 100.0, 1000.0)
        assertEquals(9000.0, p.cash, 1e-9)
        assertEquals(10.0, p.holdings.single().qty, 1e-9)
        p = PaperEngine.sell(p, "BTCUSDT", 1.0, 120.0)
        assertEquals(10_200.0, p.cash, 1e-9)
        assertEquals(200.0, p.realizedPnl, 1e-9)
        assertTrue(p.holdings.isEmpty())
    }

    @Test fun feesAndSlippageReduceResult() {
        val p0 = PaperPortfolio(feePct = 0.2, slippagePct = 0.1)
        var p = PaperEngine.buy(p0, "X", "X", 100.0, 1000.0)
        p = PaperEngine.sell(p, "X", 1.0, 100.0) // aynı fiyattan geri sat
        assertTrue("komisyon zarar ettirmeli: ${p.cash}", p.cash < 10_000.0)
        assertEquals(p.cash - 10_000.0, p.realizedPnl, 1e-6)
        assertTrue(p.feesPaid > 3.9)
    }

    @Test fun partialSellKeepsAverageCost() {
        var p = PaperEngine.buy(noFee, "X", "X", 100.0, 1000.0)
        p = PaperEngine.buy(p, "X", "X", 50.0, 1000.0) // 10 + 20 = 30 adet, maliyet 2000
        assertEquals(2000.0 / 30, p.holdings.single().avgCostTry, 1e-9)
        p = PaperEngine.sell(p, "X", 0.5, 100.0)
        val h = p.holdings.single()
        assertEquals(15.0, h.qty, 1e-9); assertEquals(1000.0, h.costTry, 1e-9)
        assertEquals(500.0, p.realizedPnl, 1e-9)
    }

    @Test fun depositDoesNotCountAsProfit() {
        val p = PaperEngine.deposit(noFee, 5000.0)
        assertEquals(15_000.0, p.cash, 1e-9)
        assertEquals(0.0, PaperEngine.totalPnl(p, emptyMap()), 1e-9)
        val g = PaperEngine.grant(p, "X", "X", 2.0, 100.0)
        assertEquals(0.0, PaperEngine.totalPnl(g, mapOf("X" to 100.0)), 1e-9)
        assertEquals(20.0, PaperEngine.totalPnl(g, mapOf("X" to 110.0)), 1e-9)
    }

    @Test fun cannotOverspend() {
        val r = runCatching { PaperEngine.buy(noFee, "X", "X", 1.0, 20_000.0) }
        assertTrue(r.exceptionOrNull() is PaperEngine.PaperException)
        assertTrue(runCatching { PaperEngine.withdraw(noFee, 20_000.0) }.isFailure)
    }

    @Test fun stopLossAndTakeProfitTrigger() {
        var p = PaperEngine.buy(noFee, "X", "X", 100.0, 1000.0)
        p = PaperEngine.placeOrder(p, PaperOrder(0, "X", "X", OrderKind.STOP_LOSS, 90.0, fraction = 1.0, createdAt = 1))
        p = PaperEngine.placeOrder(p, PaperOrder(0, "X", "X", OrderKind.TAKE_PROFIT, 130.0, fraction = 0.5, createdAt = 2))
        val (p1, ex1) = PaperEngine.checkOrders(p, mapOf("X" to 95.0))
        assertTrue(ex1.isEmpty()); assertEquals(2, p1.orders.size)
        val (p2, ex2) = PaperEngine.checkOrders(p1, mapOf("X" to 89.0))
        assertEquals(OrderKind.STOP_LOSS, ex2.single().order.kind)
        assertTrue(p2.holdings.isEmpty())
        assertTrue("tümü satılınca kâr-al da iptal olmalı", p2.orders.isEmpty())
        assertEquals(-110.0, p2.realizedPnl, 1e-9)
    }

    @Test fun limitBuyWaitsForPrice() {
        var p = PaperEngine.placeOrder(noFee, PaperOrder(0, "X", "X", OrderKind.LIMIT_BUY, 80.0, amountTry = 800.0, createdAt = 1))
        assertTrue(PaperEngine.checkOrders(p, mapOf("X" to 85.0)).second.isEmpty())
        val (p2, ex) = PaperEngine.checkOrders(p, mapOf("X" to 79.0))
        assertEquals(1, ex.size)
        assertEquals(800.0 / 79.0, p2.holdings.single().qty, 1e-9)
        assertTrue(runCatching { PaperEngine.placeOrder(noFee, PaperOrder(0, "X", "X", OrderKind.LIMIT_BUY, 1.0, amountTry = 99_999.0, createdAt = 1)) }.isFailure)
    }

    @Test fun migratesOldVersionData() {
        val old = PaperPortfolio(cash = 8000.0, positions = listOf(
            PaperPosition(1, "BTCUSDT", "BTC", 0.001, 80_000.0, 40.0, 1000.0, 1),
            PaperPosition(2, "BTCUSDT", "BTC", 0.001, 80_000.0, 40.0, 1000.0, 2)))
        val m = PaperEngine.migrate(old)
        assertTrue(m.positions.isEmpty())
        assertEquals(0.002, m.holdings.single().qty, 1e-12)
        assertEquals(2000.0, m.holdings.single().costTry, 1e-9)
        // Eski JSON (yeni alanlar olmadan) okunabilmeli
        val json = """{"startCash":10000.0,"cash":9000.0,"feePct":0.2,"positions":[{"id":1,"symbol":"ETHUSDT","base":"ETH","qty":0.01,"entryPriceUsdt":3000.0,"entryRate":40.0,"investedTry":1000.0,"at":5}],"closed":[]}"""
        val p = PaperEngine.migrate(Http.json.decodeFromString(PaperPortfolio.serializer(), json))
        assertEquals("ETH", p.holdings.single().base)
        assertEquals(LedgerType.BUY, PaperEngine.buy(p, "ETHUSDT", "ETH", 120_000.0, 100.0).ledger.first().type)
    }
}

class PaperNotifyTest {
    @Test fun todayPnlIgnoresDeposits() {
        val now = 100L * 3_600_000L
        var p = PaperPortfolio(feePct = 0.0, slippagePct = 0.0)
        p = PaperEngine.recordEquity(p, 10_000.0, now - 25 * 3_600_000L)          // dün: 10.000, yatırılan 10.000
        p = PaperEngine.deposit(p, 5_000.0)                                        // bugün 5.000 yüklendi
        // şimdi değer 15.600 → gerçek kazanç 600 (5.000 yükleme kâr sayılmamalı)
        assertEquals(600.0, PaperEngine.pnlChangeSince(p, 15_600.0, now, 24 * 3_600_000L)!!, 1e-9)
    }

    @Test fun printPaperMessages() {
        val m = com.kriptoradar.alerts.PaperMoney("SOL", 1000.0, 1100.0, 100.0, 10.0, 6000.0)
        println(com.kriptoradar.alerts.Voice.paperProfit(true, m, com.kriptoradar.alerts.PaperExecutor.Momentum.RISING_STRONG, 0.1))
        println(com.kriptoradar.alerts.Voice.paperProfit(true, m, com.kriptoradar.alerts.PaperExecutor.Momentum.RISING_FADING, 1.4))
        println(com.kriptoradar.alerts.Voice.paperProfit(false, m, com.kriptoradar.alerts.PaperExecutor.Momentum.RISING_FADING, 1.4))
        println(com.kriptoradar.alerts.Voice.paperLoss(true, m.copy(value = 920.0, pnl = -80.0, pct = -8.0)))
    }
}
