package com.kriptoradar.alerts

import android.content.Context
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.data.Prefs
import com.kriptoradar.ui.Format
import java.util.Calendar

/** Akşam 21:00'den sonra günde bir kez sanal portföy özeti bildirimi. */
object DailySummary {
    suspend fun maybeSend(ctx: Context) {
        val s = Prefs.settings.value
        if (!s.dailySummary) return
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < 21) return
        val day = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
        if (Prefs.lastNotified("daily_summary") == day) return
        val p = Prefs.paper.value
        if (p.holdings.isEmpty() && p.ledger.none { it.type == com.kriptoradar.data.LedgerType.SELL }) return
        if (PaperExecutor.pricesTry().isEmpty()) MarketRepository.refreshSnapshot()
        val prices = PaperExecutor.pricesTry()
        if (prices.isEmpty()) return
        Prefs.markNotified("daily_summary", day)
        val now = System.currentTimeMillis()
        val equity = PaperEngine.equity(p, prices)
        val total = equity - p.totalDeposited
        val today = PaperEngine.pnlChangeSince(p, equity, now, 24 * 3_600_000L)
        val best = p.holdings.maxByOrNull { h -> prices[h.symbol]?.let { (h.qty * it - h.costTry) / h.costTry } ?: 0.0 }
        fun sg(v: Double) = (if (v >= 0) "+" else "") + Format.num(v) + " TL"
        val casual = s.casualMode
        val title = if (casual) (if ((today ?: total) >= 0) "📒 Kanka günü kârla kapattık" else "📒 Kanka bugün biraz yedik") else "📒 Sanal portföy gün sonu"
        val text = buildString {
            today?.let { append("Bugün: ${sg(it)}\n") }
            append("Toplam: ${sg(total)} (${Format.num(p.totalDeposited)} TL → ${Format.num(equity)} TL)")
            best?.let { h -> prices[h.symbol]?.let { pr -> append("\nEn iyi coinin: ${h.base} ${Format.signedPct((h.qty * pr - h.costTry) / h.costTry * 100)}") } }
        }
        Notifier.post(ctx, Notifier.CH_SUMMARY, 3100, title, text)
    }
}
