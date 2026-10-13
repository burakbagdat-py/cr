package com.kriptoradar.alerts

import android.content.Context
import com.kriptoradar.data.AlertType
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.Prefs
import com.kriptoradar.data.ScanResult
import com.kriptoradar.data.SuggestionKind
import com.kriptoradar.data.SuggestionRecord
import com.kriptoradar.data.Ticker
import com.kriptoradar.ui.Format
import kotlin.math.abs

/**
 * Her akış paketinde çalışır: kullanıcı alarmları + otomatik ani hareket tespiti.
 * Tarama sonrası: yeni güçlü sinyaller ve hacim patlamaları.
 */
class AlertEngine(private val ctx: Context) {

    fun onTicks(changed: List<Ticker>) {
        val now = System.currentTimeMillis()
        checkUserAlerts(changed, now)
        checkRapidMoves(changed, now)
    }

    private fun checkUserAlerts(changed: List<Ticker>, now: Long) {
        val alerts = Prefs.alerts.value.filter { it.enabled }
        if (alerts.isEmpty()) return
        val bySymbol = changed.associateBy { it.symbol }
        val cooldown = Prefs.settings.value.alertCooldownMin * 60_000L
        val fired = mutableListOf<Long>()
        for (a in alerts) {
            val t = bySymbol[a.symbol] ?: continue
            if (a.repeat && now - a.lastTriggeredAt < cooldown) continue
            val hit: String? = when (a.type) {
                AlertType.PRICE_ABOVE -> if (t.price >= a.value) "${t.base} ${Format.price(a.value)} seviyesini yukarı geçti" else null
                AlertType.PRICE_BELOW -> if (t.price <= a.value) "${t.base} ${Format.price(a.value)} seviyesinin altına indi" else null
                AlertType.CHANGE_24H_UP -> if (t.change24hPct >= a.value) "${t.base} 24 saatte %${Format.num(t.change24hPct)} yükseldi" else null
                AlertType.CHANGE_24H_DOWN -> if (t.change24hPct <= -a.value) "${t.base} 24 saatte %${Format.num(t.change24hPct)} düştü" else null
                AlertType.MOVE_IN_WINDOW -> {
                    val c = MarketRepository.history(t.symbol)?.changePct(now, a.windowMin * 60_000L, t.price)
                    if (c != null && abs(c) >= a.value) "${t.base} ${a.windowMin} dakikada %${Format.num(c)} hareket etti" else null
                }
            }
            if (hit != null) {
                Notifier.post(ctx, Notifier.CH_ALERT, (a.id % Int.MAX_VALUE).toInt(),
                    "🔔 ${t.base}/${t.quote}: ${Format.price(t.price)}", hit, t.symbol)
                fired += a.id
            }
        }
        if (fired.isNotEmpty()) {
            Prefs.updateAlerts { list ->
                list.map { if (it.id in fired) it.copy(lastTriggeredAt = now, enabled = it.repeat) else it }
            }
        }
    }

    private fun checkRapidMoves(changed: List<Ticker>, now: Long) {
        val s = Prefs.settings.value
        if (!s.rapidMoveEnabled) return
        val favs = Prefs.favorites.value
        val window = s.rapidMoveWindowMin * 60_000L
        val cooldown = s.alertCooldownMin * 60_000L
        for (t in changed) {
            val isFav = t.symbol in favs
            if (!isFav && (t.quote != s.quote || t.quoteVolume < s.minQuoteVolume || !com.kriptoradar.data.BinanceApi.isTradable(t.base))) continue
            val c = MarketRepository.history(t.symbol)?.changePct(now, window, t.price) ?: continue
            if (abs(c) < s.rapidMovePct) continue
            val key = "move_${t.symbol}_${if (c > 0) "u" else "d"}"
            if (now - Prefs.lastNotified(key) < cooldown) continue
            Prefs.markNotified(key, now)
            val up = c > 0
            Notifier.post(
                ctx, Notifier.CH_MOVE, key.hashCode(),
                "${if (up) "🚀" else "🔻"} ${t.base} ${s.rapidMoveWindowMin} dk'da %${Format.num(c)}",
                "${t.base}/${t.quote} ${Format.price(t.price)} • 24s %${Format.num(t.change24hPct)} • Hacim ${Format.compact(t.quoteVolume)} ${t.quote}",
                t.symbol,
            )
        }
    }

    fun onScan(prev: ScanResult?, now: ScanResult) {
        val s = Prefs.settings.value
        val ts = System.currentTimeMillis()
        val records = mutableListOf<SuggestionRecord>()

        // Dip dönüş adayları: çok düşmüş ama yukarı dönmeye başlamış coinler
        val prevRev = prev?.signals?.filter { it.reversal?.candidate == true }?.map { it.symbol }?.toSet() ?: emptySet()
        val newRev = now.signals
            .filter { val r = it.reversal; r != null && r.candidate && r.score >= s.reversalThreshold && it.symbol !in prevRev }
            .filter { ts - Prefs.lastNotified("rev_${it.symbol}") > 12 * 3_600_000L }
            .sortedByDescending { it.reversal!!.score }
        newRev.forEach {
            Prefs.markNotified("rev_${it.symbol}", ts)
            records += SuggestionRecord(it.symbol, it.base, SuggestionKind.REVERSAL, it.price, it.reversal!!.score, ts)
        }
        if (s.reversalNotify) {
            newRev.forEach { sig ->
                val r = sig.reversal!!
                val hist = if (r.histWinRate >= 0) "\nBu coinde benzer desen ${r.histSamples} kez: 24s sonra %${(r.histWinRate * 100).toInt()} yükseldi (ort. ${Format.signedPct(r.histAvgReturn)})" else ""
                Notifier.post(
                    ctx, Notifier.CH_REVERSAL, "rev_${sig.symbol}".hashCode(),
                    "🔄 ${sig.base}: dip dönüş adayı (${r.score}/100)",
                    "${sig.base} ${Format.price(sig.price)} • 30g zirveden -%${Format.num(r.drawdown30)} • dipten +%${Format.num(r.bounceFromLow)}\n" +
                        r.reasons.take(4).joinToString("\n") { "• $it" } + hist +
                        "\n(İstatistiksel tespit, yatırım tavsiyesi değildir)",
                    sig.symbol,
                )
            }
        }

        if (s.signalNotify) {
            val prevStrong = prev?.signals?.filter { it.score >= s.signalThreshold }?.map { it.symbol }?.toSet() ?: emptySet()
            val fresh = now.signals.filter { it.score >= s.signalThreshold && it.symbol !in prevStrong }
                .filter { ts - Prefs.lastNotified("sig_${it.symbol}") > 6 * 3_600_000L }
            if (fresh.isNotEmpty()) {
                fresh.forEach {
                    Prefs.markNotified("sig_${it.symbol}", ts)
                    records += SuggestionRecord(it.symbol, it.base, SuggestionKind.STRONG, it.price, it.score, ts)
                }
                val text = fresh.take(6).joinToString("\n") {
                    "${it.base}: skor ${it.score} • RSI ${it.rsi.toInt()} • 24s %${Format.num(it.change24h)}"
                } + "\n(İstatistiksel gösterge, yatırım tavsiyesi değildir)"
                Notifier.post(ctx, Notifier.CH_SIGNAL, 2001,
                    "📊 ${fresh.size} coin teknik eşiği geçti", text, fresh.first().symbol)
            }
        }
        if (s.volumeSpikeNotify) {
            for (sig in now.signals) {
                if (sig.volumeRatio < s.volumeSpikeRatio) continue
                val key = "vol_${sig.symbol}"
                if (ts - Prefs.lastNotified(key) < 4 * 3_600_000L) continue
                Prefs.markNotified(key, ts)
                Notifier.post(ctx, Notifier.CH_MOVE, key.hashCode(),
                    "📈 ${sig.base} hacim patlaması (${Format.num(sig.volumeRatio)}x)",
                    "Son 6 saatlik hacim, önceki 48 saatin ortalamasının ${Format.num(sig.volumeRatio)} katı. 24s %${Format.num(sig.change24h)}",
                    sig.symbol)
            }
        }
        Prefs.addSuggestions(records)
    }
}
