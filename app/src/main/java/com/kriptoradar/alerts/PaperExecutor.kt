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
}
