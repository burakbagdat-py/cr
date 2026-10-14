package com.kriptoradar.alerts

import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.data.PaperOrder
import com.kriptoradar.data.Prefs
import com.kriptoradar.ui.Format

/**
 * Tek dokunuşla sanal alım (bildirim düğmesi ve uygulama içi kutu ortak kullanır).
 * Ayar açıksa aynı anda otomatik zarar-kes ve kâr-al emirleri de kurulur.
 */
object QuickTrade {
    /** Bir coinin sanal portföyde kullanılan USDT çiftini bulur (BTCTRY → BTCUSDT). */
    fun usdtSymbol(symbol: String): String? {
        val tk = MarketRepository.tickers.value
        if (symbol.endsWith("USDT") && tk.containsKey(symbol)) return symbol
        val base = tk[symbol]?.base ?: com.kriptoradar.data.BinanceApi.splitSymbol(symbol)?.first ?: return null
        return (base + "USDT").takeIf { tk.containsKey(it) }
    }

    /** Başarılıysa kullanıcıya gösterilecek mesajı döndürür; hata varsa PaperException fırlatır. */
    suspend fun buy(symbol: String, amount: Double = Prefs.settings.value.quickBuyAmount): String {
        val sym = usdtSymbol(symbol) ?: run {
            MarketRepository.refreshSnapshot()
            usdtSymbol(symbol) ?: throw PaperEngine.PaperException("Bu coin sanal portföyde alınamıyor")
        }
        if (PaperExecutor.pricesTry()[sym] == null) MarketRepository.refreshSnapshot()
        val price = PaperExecutor.pricesTry()[sym] ?: throw PaperEngine.PaperException("Fiyat alınamadı")
        val base = MarketRepository.tickers.value[sym]?.base ?: sym.removeSuffix("USDT")
        val s = Prefs.settings.value
        Prefs.updatePaper { p ->
            var np = PaperEngine.buy(p, sym, base, price, amount, "Hızlı alım")
            if (s.autoProtect) {
                val now = System.currentTimeMillis()
                // Eski otomatik emirleri yenisiyle değiştir
                np = np.copy(orders = np.orders.filterNot { it.symbol == sym && (it.kind == OrderKind.STOP_LOSS || it.kind == OrderKind.TAKE_PROFIT) })
                if (s.autoStopPct > 0) np = PaperEngine.placeOrder(np, PaperOrder(0, sym, base, OrderKind.STOP_LOSS, price * (1 - s.autoStopPct / 100), fraction = 1.0, createdAt = now))
                if (s.autoTakePct > 0) np = PaperEngine.placeOrder(np, PaperOrder(0, sym, base, OrderKind.TAKE_PROFIT, price * (1 + s.autoTakePct / 100), fraction = 1.0, createdAt = now + 1))
            }
            np
        }
        val e = Prefs.paper.value.ledger.first { it.type == com.kriptoradar.data.LedgerType.BUY }
        return "${Format.num(amount)} TL'ye ${Format.price(e.qty)} $base alındı (${Format.price(price)} TL)." +
            if (s.autoProtect) " Zarar-kes -%${Format.num(s.autoStopPct)}, kâr-al +%${Format.num(s.autoTakePct)} kuruldu." else ""
    }

    /** Kârı koru: fiyat %3 geri gelirse ya da maliyetin altına inerse satacak zarar-kes kurar. */
    fun protect(symbol: String): String {
        val price = PaperExecutor.pricesTry()[symbol] ?: throw PaperEngine.PaperException("Fiyat alınamadı")
        val h = Prefs.paper.value.holdings.firstOrNull { it.symbol == symbol } ?: throw PaperEngine.PaperException("Bu coin portföyde yok")
        val stop = maxOf(price * 0.97, h.avgCostTry * 1.003)
        if (stop >= price) throw PaperEngine.PaperException("Henüz koruyacak kâr yok (fiyat maliyetin altında)")
        Prefs.updatePaper { p ->
            val cleaned = p.copy(orders = p.orders.filterNot { it.symbol == symbol && it.kind == OrderKind.STOP_LOSS })
            PaperEngine.placeOrder(cleaned, PaperOrder(0, symbol, h.base, OrderKind.STOP_LOSS, stop, fraction = 1.0, createdAt = System.currentTimeMillis()))
        }
        return "${h.base} ${Format.price(stop)} TL'nin altına inerse otomatik satılacak."
    }
}
