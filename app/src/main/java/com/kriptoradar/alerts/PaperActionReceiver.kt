package com.kriptoradar.alerts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.data.PaperOrder
import com.kriptoradar.data.Prefs
import com.kriptoradar.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sanal portföy bildirimindeki düğmeler: uygulamayı açmadan sat ya da kârı koru.
 */
class PaperActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val symbol = intent.getStringExtra(EXTRA_SYMBOL) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
        Prefs.init(ctx)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (PaperExecutor.pricesTry()[symbol] == null) MarketRepository.refreshSnapshot()
                val price = PaperExecutor.pricesTry()[symbol]
                val casual = Prefs.settings.value.casualMode
                val (title, text) = if (price == null) "Fiyat alınamadı" to "İnternet bağlantını kontrol edip tekrar dene."
                else try {
                    when (action) {
                        ACTION_SELL_ALL, ACTION_SELL_HALF -> {
                            val f = if (action == ACTION_SELL_ALL) 1.0 else 0.5
                            Prefs.updatePaper { PaperEngine.sell(it, symbol, f, price, "Bildirimden") }
                            val e = Prefs.paper.value.ledger.first()
                            val pnl = e.pnlTry
                            if (casual) (if (pnl >= 0) "💰 Kanka sattık, kâr cepte!" else "💱 Kanka sattık") to
                                "${Format.price(e.qty)} ${e.base} ${Format.price(price)} TL'den gitti. Sonuç: ${sign(pnl)} TL (sanal)"
                            else "✅ Sanal satış: ${e.base}" to
                                "${Format.price(e.qty)} ${e.base} ${Format.price(price)} TL'den satıldı. Kâr/zarar: ${sign(pnl)} TL"
                        }
                        ACTION_PROTECT -> {
                            val h = Prefs.paper.value.holdings.firstOrNull { it.symbol == symbol } ?: error("Bu coin artık portföyde yok")
                            // Kârı koru: fiyat %3 geri gelirse ya da maliyetin altına inerse sat
                            val stop = maxOf(price * 0.97, h.avgCostTry * 1.003)
                            Prefs.updatePaper { p ->
                                val cleaned = p.copy(orders = p.orders.filterNot { it.symbol == symbol && it.kind == OrderKind.STOP_LOSS })
                                PaperEngine.placeOrder(cleaned, PaperOrder(0, symbol, h.base, OrderKind.STOP_LOSS, stop, fraction = 1.0, createdAt = System.currentTimeMillis()))
                            }
                            (if (casual) "🛡️ Kanka kârı kilitledik" else "🛡️ Kâr koruması kuruldu") to
                                "${h.base} ${Format.price(stop)} TL'nin altına inerse otomatik satılacak. Yükselmeye devam ederse elinde kalır."
                        }
                        else -> return@launch
                    }
                } catch (e: Exception) {
                    "İşlem yapılamadı" to (e.message ?: "Bilinmeyen hata")
                }
                NotificationManagerCompat.from(ctx).cancel(notifId)
                Notifier.post(ctx, Notifier.CH_PAPER, notifId + 1, title, text, symbol)
            } finally {
                pending.finish()
            }
        }
    }

    private fun sign(v: Double) = (if (v >= 0) "+" else "") + Format.num(v)

    companion object {
        const val EXTRA_SYMBOL = "symbol"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NOTIF = "notif"
        const val ACTION_SELL_ALL = "sell_all"
        const val ACTION_SELL_HALF = "sell_half"
        const val ACTION_PROTECT = "protect"

        fun intent(ctx: Context, symbol: String, action: String, notifId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, (symbol + action).hashCode(),
                Intent(ctx, PaperActionReceiver::class.java)
                    .putExtra(EXTRA_SYMBOL, symbol).putExtra(EXTRA_ACTION, action).putExtra(EXTRA_NOTIF, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
