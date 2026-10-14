package com.kriptoradar.alerts

import android.content.Context
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.data.Prefs
import com.kriptoradar.data.Ticker
import com.kriptoradar.ui.Format

/** Sanal portföyün bekleyen emirlerini canlı fiyatlarla çalıştırır ve değer geçmişini kaydeder. */
object PaperExecutor {
    @Volatile private var lastRun = 0L

    /** USDT çiftlerinin TL fiyatları (USDT fiyatı × USDT/TRY). */
    fun pricesTry(tickers: Map<String, Ticker> = MarketRepository.tickers.value): Map<String, Double> {
        val rate = tickers["USDTTRY"]?.price ?: return emptyMap()
        val out = HashMap<String, Double>()
        for ((sym, t) in tickers) if (t.quote == "USDT") out[sym] = t.price * rate
        return out
    }

    fun onTicks(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRun < 2_000) return
        lastRun = now
        val p0 = Prefs.paper.value
        if (p0.orders.isEmpty() && p0.holdings.isEmpty()) return
        val prices = pricesTry()
        if (prices.isEmpty()) return
        var executed: List<PaperEngine.Executed> = emptyList()
        Prefs.updatePaper { p ->
            val (np, ex) = PaperEngine.checkOrders(p, prices)
            executed = ex
            val withEq = PaperEngine.recordEquity(np, PaperEngine.equity(np, prices), now)
            if (ex.isEmpty() && withEq === np) p else withEq
        }
        if (Prefs.settings.value.paperNotify) watchHoldings(ctx, prices, now)
        val casual = Prefs.settings.value.casualMode
        for (e in executed) {
            val o = e.order; val l = e.entry
            val (title, text) = if (casual) Voice.paperExecuted(o.base, o.kind, l.pnlTry, Format.price(l.priceTry))
            else when (o.kind) {
                OrderKind.LIMIT_BUY -> "🛒 Sanal limit alım gerçekleşti: ${o.base}" to
                    "${Format.price(l.qty)} ${o.base} alındı, fiyat ${Format.price(l.priceTry)} TL (${Format.num(l.amountTry)} TL)"
                else -> "${if (o.kind == OrderKind.STOP_LOSS) "🛑 Zarar-kes" else if (o.kind == OrderKind.TAKE_PROFIT) "🎯 Kâr-al" else "💱 Limit satış"} çalıştı: ${o.base}" to
                    "${Format.price(l.qty)} ${o.base} ${Format.price(l.priceTry)} TL'den satıldı. Sonuç: ${if (l.pnlTry >= 0) "+" else ""}${Format.num(l.pnlTry)} TL (sanal)"
            }
            Notifier.post(ctx, Notifier.CH_ALERT, ("paper" + o.id).hashCode(), title, text, o.symbol)
        }
    }

    /** Kısa vadeli ivme: hâlâ yükseliyor mu, sönüyor mu? */
    enum class Momentum { RISING_STRONG, RISING_FADING, FALLING }

    private fun momentum(symbol: String, price: Double, now: Long): Pair<Momentum, Double> {
        val h = MarketRepository.history(symbol) ?: return Momentum.RISING_FADING to 0.0
        val c1 = h.changePct(now, 60_000L, price) ?: 0.0
        val recent = h.snapshot().filter { it.first >= now - 15 * 60_000L }.map { it.second }
        val high = (recent.maxOrNull() ?: price).coerceAtLeast(price)
        val offHigh = (high - price) / high * 100
        val c5 = h.changePct(now, 5 * 60_000L, price) ?: 0.0
        val m = when {
            c5 < 0 && c1 <= 0 -> Momentum.FALLING
            c1 > 0 && offHigh < 0.3 -> Momentum.RISING_STRONG
            else -> Momentum.RISING_FADING
        }
        return m to offHigh
    }

    /** Elindeki sanal coinler için kâr/zarar basamakları ve ani hareket bildirimleri. */
    private fun watchHoldings(ctx: Context, prices: Map<String, Double>, now: Long) {
        val s = Prefs.settings.value
        for (h in Prefs.paper.value.holdings) {
            val price = prices[h.symbol] ?: continue
            if (h.costTry <= 0) continue
            val value = h.qty * price
            val pnl = value - h.costTry
            val pct = pnl / h.costTry * 100
            val id = ("pp" + h.symbol).hashCode()
            val tag = "${h.symbol}_${h.openedAt}"
            val money = PaperMoney(h.base, h.costTry, value, pnl, pct, price)

            // 1) Kâr basamağı
            val up = if (s.paperProfitStepPct > 0) (pct / s.paperProfitStepPct).toInt() else 0
            if (up >= 1 && up > Prefs.lastNotified("ppu_$tag")) {
                Prefs.markNotified("ppu_$tag", up.toLong())
                val (m, off) = momentum(h.symbol, price, now)
                val (title, text) = Voice.paperProfit(s.casualMode, money, m, off)
                Notifier.post(ctx, Notifier.CH_PAPER, id, title, text, h.symbol, profitActions(ctx, h.symbol, id))
                continue
            }
            // 2) Zarar basamağı
            val down = if (s.paperLossStepPct > 0) (-pct / s.paperLossStepPct).toInt() else 0
            if (down >= 1 && down > Prefs.lastNotified("ppd_$tag")) {
                Prefs.markNotified("ppd_$tag", down.toLong())
                val (title, text) = Voice.paperLoss(s.casualMode, money)
                Notifier.post(ctx, Notifier.CH_PAPER, id, title, text, h.symbol, sellActions(ctx, h.symbol, id))
                continue
            }
            // 3) Elindeki coin 5 dakikada sert hareket etti
            if (s.paperMovePct <= 0) continue
            val c5 = MarketRepository.history(h.symbol)?.changePct(now, 5 * 60_000L, price) ?: continue
            if (kotlin.math.abs(c5) < s.paperMovePct) continue
            val key = "ppm_${h.symbol}_${if (c5 > 0) "u" else "d"}"
            if (now - Prefs.lastNotified(key) < 15 * 60_000L) continue
            Prefs.markNotified(key, now)
            val (m, off) = momentum(h.symbol, price, now)
            val (title, text) = Voice.paperMove(s.casualMode, money, c5, m, off)
            Notifier.post(ctx, Notifier.CH_PAPER, id, title, text, h.symbol,
                if (c5 > 0 && pnl > 0) profitActions(ctx, h.symbol, id) else sellActions(ctx, h.symbol, id))
        }
    }

    private fun sellActions(ctx: Context, symbol: String, id: Int) = listOf(
        "Hepsini sat" to PaperActionReceiver.intent(ctx, symbol, PaperActionReceiver.ACTION_SELL_ALL, id),
        "Yarısını sat" to PaperActionReceiver.intent(ctx, symbol, PaperActionReceiver.ACTION_SELL_HALF, id),
    )

    private fun profitActions(ctx: Context, symbol: String, id: Int) = sellActions(ctx, symbol, id) +
        ("Kârı koru" to PaperActionReceiver.intent(ctx, symbol, PaperActionReceiver.ACTION_PROTECT, id))
}

/** Bildirim metinleri için bir pozisyonun para durumu. */
data class PaperMoney(val base: String, val invested: Double, val value: Double, val pnl: Double, val pct: Double, val price: Double)
