package com.kriptoradar.data

import kotlin.math.abs
import kotlin.math.min

/**
 * Sanal portföyün tüm hesapları. Saf fonksiyonlar: portföyü alır, yenisini döndürür.
 * Fiyatlar TL cinsindendir (USDT fiyatı × USDT/TRY kuru).
 */
object PaperEngine {
    const val MAX_LEDGER = 1000
    const val MAX_EQUITY = 720
    private const val DUST = 1e-12

    class PaperException(msg: String) : Exception(msg)

    data class Executed(val order: PaperOrder, val entry: LedgerEntry)

    private var lastId = 0L
    @Synchronized
    private fun newId(): Long {
        val now = System.currentTimeMillis()
        lastId = if (now > lastId) now else lastId + 1
        return lastId
    }

    private fun PaperPortfolio.log(e: LedgerEntry) = copy(ledger = (listOf(e) + ledger).take(MAX_LEDGER))

    /** Eski sürüm (lot bazlı) verisini yeni yapıya taşır. */
    fun migrate(p: PaperPortfolio): PaperPortfolio {
        if (p.positions.isEmpty() && p.closed.isEmpty()) return p
        val holdings = p.positions.groupBy { it.symbol }.map { (sym, list) ->
            Holding(sym, list.first().base, list.sumOf { it.qty }, list.sumOf { it.investedTry }, list.minOf { it.at })
        }
        val realized = p.closed.sumOf { it.returnedTry - it.investedTry }
        val ledger = p.closed.map {
            LedgerEntry(it.closedAt, it.closedAt, LedgerType.SELL, it.base, amountTry = it.returnedTry, pnlTry = it.returnedTry - it.investedTry, note = "Eski sürümden")
        }
        return p.copy(
            holdings = p.holdings + holdings, realizedPnl = p.realizedPnl + realized,
            totalDeposited = if (p.ledger.isEmpty()) p.startCash else p.totalDeposited,
            ledger = (p.ledger + ledger).sortedByDescending { it.at }.take(MAX_LEDGER),
            positions = emptyList(), closed = emptyList(),
        )
    }

    fun deposit(p: PaperPortfolio, amount: Double, note: String = ""): PaperPortfolio {
        if (amount <= 0) throw PaperException("Tutar sıfırdan büyük olmalı")
        return p.copy(cash = p.cash + amount, totalDeposited = p.totalDeposited + amount)
            .log(LedgerEntry(newId(), System.currentTimeMillis(), LedgerType.DEPOSIT, amountTry = amount, note = note))
    }

    fun withdraw(p: PaperPortfolio, amount: Double, note: String = ""): PaperPortfolio {
        if (amount <= 0) throw PaperException("Tutar sıfırdan büyük olmalı")
        if (amount > p.cash + 1e-6) throw PaperException("Nakit yetersiz")
        return p.copy(cash = p.cash - amount, totalDeposited = p.totalDeposited - amount)
            .log(LedgerEntry(newId(), System.currentTimeMillis(), LedgerType.WITHDRAW, amountTry = amount, note = note))
    }

    /** Admin: nakiti doğrudan belirli bir değere ayarla (fark yükleme/çekme sayılır). */
    fun setCash(p: PaperPortfolio, value: Double): PaperPortfolio {
        if (value < 0) throw PaperException("Negatif olamaz")
        val diff = value - p.cash
        return p.copy(cash = value, totalDeposited = p.totalDeposited + diff)
            .log(LedgerEntry(newId(), System.currentTimeMillis(), LedgerType.ADJUST, amountTry = diff, note = "Nakit ${"%.2f".format(value)} TL'ye ayarlandı"))
    }

    fun buy(p: PaperPortfolio, symbol: String, base: String, priceTry: Double, amountTry: Double, note: String = ""): PaperPortfolio {
        if (amountTry <= 0) throw PaperException("Tutar girin")
        if (priceTry <= 0) throw PaperException("Fiyat yok")
        if (amountTry > p.cash + 1e-6) throw PaperException("Nakit yetersiz (${"%.2f".format(p.cash)} TL)")
        val fee = amountTry * p.feePct / 100
        val execPrice = priceTry * (1 + p.slippagePct / 100)
        val qty = (amountTry - fee) / execPrice
        val now = System.currentTimeMillis()
        val existing = p.holdings.firstOrNull { it.symbol == symbol }
        val holdings = if (existing == null) p.holdings + Holding(symbol, base, qty, amountTry, now)
        else p.holdings.map { if (it.symbol == symbol) it.copy(qty = it.qty + qty, costTry = it.costTry + amountTry) else it }
        return p.copy(cash = p.cash - amountTry, holdings = holdings, feesPaid = p.feesPaid + fee)
            .log(LedgerEntry(newId(), now, LedgerType.BUY, base, qty, execPrice, amountTry, fee, note = note))
    }

    fun sell(p: PaperPortfolio, symbol: String, fraction: Double, priceTry: Double, note: String = ""): PaperPortfolio {
        val h = p.holdings.firstOrNull { it.symbol == symbol } ?: throw PaperException("Bu coinden yok")
        if (priceTry <= 0) throw PaperException("Fiyat yok")
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0) throw PaperException("Miktar seçin")
        val qty = h.qty * f
        val execPrice = priceTry * (1 - p.slippagePct / 100)
        val gross = qty * execPrice
        val fee = gross * p.feePct / 100
        val net = gross - fee
        val costPart = h.costTry * f
        val pnl = net - costPart
        val full = f >= 1.0 - 1e-9 || h.qty - qty < DUST
        val holdings = if (full) p.holdings.filterNot { it.symbol == symbol }
        else p.holdings.map { if (it.symbol == symbol) it.copy(qty = it.qty - qty, costTry = it.costTry - costPart) else it }
        // Tamamı satıldıysa o coinin satış emirleri anlamsız
        val orders = if (full) p.orders.filterNot { it.symbol == symbol && it.kind != OrderKind.LIMIT_BUY } else p.orders
        return p.copy(cash = p.cash + net, holdings = holdings, orders = orders, realizedPnl = p.realizedPnl + pnl, feesPaid = p.feesPaid + fee)
            .log(LedgerEntry(newId(), System.currentTimeMillis(), LedgerType.SELL, h.base, qty, execPrice, net, fee, pnl, note))
    }

    /** Admin: parasını ödemeden coin ekle (hediye). Değeri yükleme sayılır, kâr/zarar bozulmaz. */
    fun grant(p: PaperPortfolio, symbol: String, base: String, qty: Double, priceTry: Double): PaperPortfolio {
        if (qty <= 0) throw PaperException("Miktar girin")
        val value = qty * priceTry
        val now = System.currentTimeMillis()
        val existing = p.holdings.firstOrNull { it.symbol == symbol }
        val holdings = if (existing == null) p.holdings + Holding(symbol, base, qty, value, now)
        else p.holdings.map { if (it.symbol == symbol) it.copy(qty = it.qty + qty, costTry = it.costTry + value) else it }
        return p.copy(holdings = holdings, totalDeposited = p.totalDeposited + value)
            .log(LedgerEntry(newId(), now, LedgerType.ADJUST, base, qty, priceTry, value, note = "Admin: coin eklendi"))
    }

    /** Admin: bir coini satmadan portföyden sil (değeri çekim sayılır). */
    fun removeHolding(p: PaperPortfolio, symbol: String, priceTry: Double?): PaperPortfolio {
        val h = p.holdings.firstOrNull { it.symbol == symbol } ?: return p
        val value = priceTry?.let { h.qty * it } ?: h.costTry
        return p.copy(
            holdings = p.holdings.filterNot { it.symbol == symbol },
            orders = p.orders.filterNot { it.symbol == symbol && it.kind != OrderKind.LIMIT_BUY },
            totalDeposited = p.totalDeposited - value,
        ).log(LedgerEntry(newId(), System.currentTimeMillis(), LedgerType.ADJUST, h.base, h.qty, priceTry ?: 0.0, -value, note = "Admin: coin silindi"))
    }

    fun reset(p: PaperPortfolio, startCash: Double): PaperPortfolio =
        PaperPortfolio(startCash = startCash, cash = startCash, totalDeposited = startCash, feePct = p.feePct, slippagePct = p.slippagePct)
            .log(LedgerEntry(newId(), System.currentTimeMillis(), LedgerType.RESET, amountTry = startCash, note = "Portföy sıfırlandı"))

    fun placeOrder(p: PaperPortfolio, o: PaperOrder): PaperPortfolio {
        if (o.triggerTry <= 0) throw PaperException("Tetik fiyatı girin")
        when (o.kind) {
            OrderKind.LIMIT_BUY -> {
                if (o.amountTry <= 0) throw PaperException("Tutar girin")
                val reserved = p.orders.filter { it.kind == OrderKind.LIMIT_BUY }.sumOf { it.amountTry }
                if (o.amountTry + reserved > p.cash + 1e-6) throw PaperException("Nakit yetersiz (bekleyen alım emirleri dahil)")
            }
            else -> if (p.holdings.none { it.symbol == o.symbol }) throw PaperException("Önce bu coinden almalısın")
        }
        return p.copy(orders = p.orders + o.copy(id = newId()))
    }

    fun cancelOrder(p: PaperPortfolio, id: Long) = p.copy(orders = p.orders.filterNot { it.id == id })

    fun triggered(o: PaperOrder, price: Double) = when (o.kind) {
        OrderKind.LIMIT_BUY, OrderKind.STOP_LOSS -> price <= o.triggerTry
        OrderKind.LIMIT_SELL, OrderKind.TAKE_PROFIT -> price >= o.triggerTry
    }

    /** Fiyatlara göre tetiklenen emirleri çalıştırır. [prices]: sembol → TL fiyat. */
    fun checkOrders(p: PaperPortfolio, prices: Map<String, Double>): Pair<PaperPortfolio, List<Executed>> {
        if (p.orders.isEmpty()) return p to emptyList()
        var cur = p
        val done = mutableListOf<Executed>()
        for (o in p.orders.sortedBy { it.createdAt }) {
            if (cur.orders.none { it.id == o.id }) continue // önceki satışla iptal olmuş olabilir
            val price = prices[o.symbol] ?: continue
            if (!triggered(o, price)) continue
            val label = when (o.kind) {
                OrderKind.LIMIT_BUY -> "Limit alım"; OrderKind.LIMIT_SELL -> "Limit satış"
                OrderKind.STOP_LOSS -> "Zarar-kes"; OrderKind.TAKE_PROFIT -> "Kâr-al"
            }
            val next = try {
                val r = if (o.kind == OrderKind.LIMIT_BUY) buy(cancelOrder(cur, o.id), o.symbol, o.base, price, min(o.amountTry, cur.cash), label)
                else sell(cancelOrder(cur, o.id), o.symbol, o.fraction, price, label)
                done += Executed(o, r.ledger.first())
                r
            } catch (e: PaperException) {
                cancelOrder(cur, o.id) // çalıştırılamayan emir iptal edilir
            }
            cur = next
        }
        return cur to done
    }

    fun holdingsValue(p: PaperPortfolio, prices: Map<String, Double>) =
        p.holdings.sumOf { h -> prices[h.symbol]?.let { h.qty * it } ?: h.costTry }

    fun equity(p: PaperPortfolio, prices: Map<String, Double>) = p.cash + holdingsValue(p, prices)

    /** Toplam kâr/zarar = şimdiki değer − net yüklenen para. */
    fun totalPnl(p: PaperPortfolio, prices: Map<String, Double>) = equity(p, prices) - p.totalDeposited

    fun recordEquity(p: PaperPortfolio, value: Double, now: Long, everyMs: Long = 15 * 60_000L): PaperPortfolio {
        val last = p.equity.lastOrNull()
        if (last != null && now - last.at < everyMs && abs(last.value - value) < 0.01 * value) return p
        return p.copy(equity = (p.equity + EquityPoint(now, value)).takeLast(MAX_EQUITY))
    }

    data class Stats(val sells: Int, val wins: Int, val best: LedgerEntry?, val worst: LedgerEntry?, val buys: Int)

    fun stats(p: PaperPortfolio): Stats {
        val sells = p.ledger.filter { it.type == LedgerType.SELL }
        return Stats(sells.size, sells.count { it.pnlTry > 0 }, sells.maxByOrNull { it.pnlTry }, sells.minByOrNull { it.pnlTry },
            p.ledger.count { it.type == LedgerType.BUY })
    }
}
