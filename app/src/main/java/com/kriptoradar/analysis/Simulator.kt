package com.kriptoradar.analysis

import com.kriptoradar.data.Kline
import kotlin.math.max
import kotlin.math.min

/** Günlük kapanış (zaman, fiyat) — seçilen para biriminde (TL veya $). */
data class DayPrice(val time: Long, val price: Double)

data class WhatIf(
    val days: Int, val startPrice: Double, val invested: Double, val valueNow: Double,
    val worstValue: Double, val bestValue: Double,
) {
    val pnl get() = valueNow - invested
    val pct get() = if (invested > 0) pnl / invested * 100 else 0.0
}

data class Dca(val buys: Int, val invested: Double, val qty: Double, val valueNow: Double, val lumpValueNow: Double) {
    val pnl get() = valueNow - invested
    val pct get() = if (invested > 0) pnl / invested * 100 else 0.0
}

data class Forecast(
    val horizonDays: Int, val samples: Int, val lossProbability: Double,
    val p5: Double, val p10: Double, val p50: Double, val p90: Double, val p95: Double,
)

/**
 * "Şu kadar koysam ne olur?" hesapları. Hepsi geçmiş veriye dayanır; gelecek garanti değildir.
 */
object Simulator {

    /** [amount] para ile [price] fiyattan alınabilecek adet (komisyon düşülmüş). */
    fun qtyFor(amount: Double, price: Double, feePct: Double): Double =
        if (price > 0) amount * (1 - feePct / 100) / price else 0.0

    /** Coin fiyatını (USDT) günlük kurla (USDTTRY) çarparak TL serisi üretir; kur yoksa USD kalır. */
    fun series(coin: List<Kline>, rate: List<Kline>?): List<DayPrice> {
        if (rate.isNullOrEmpty()) return coin.map { DayPrice(it.openTime, it.close) }
        val byDay = rate.associate { it.openTime / 86_400_000L to it.close }
        var last = rate.first().close
        return coin.mapNotNull { k ->
            val r = byDay[k.openTime / 86_400_000L]
            if (r != null) last = r
            if (k.openTime < rate.first().openTime) null else DayPrice(k.openTime, k.close * last)
        }
    }

    /** [days] gün önce [amount] koysaydın, bugünkü [currentPrice] ile değeri ne olurdu. */
    fun whatIf(s: List<DayPrice>, days: Int, amount: Double, feePct: Double, currentPrice: Double): WhatIf? {
        if (s.size <= days) return null
        val start = s[s.size - 1 - days].price
        val qty = qtyFor(amount, start, feePct)
        val path = s.subList(s.size - 1 - days, s.size).map { it.price }
        val sellFee = 1 - feePct / 100
        return WhatIf(days, start, amount, qty * currentPrice * sellFee, qty * path.min() * sellFee, qty * path.max() * sellFee)
    }

    /** Son [days] gün boyunca, toplam [total] parayı her [everyDays] günde bir eşit parçalarla alsaydın. */
    fun dca(s: List<DayPrice>, days: Int, everyDays: Int, total: Double, feePct: Double, currentPrice: Double): Dca? {
        if (s.size <= days) return null
        val idx = (s.size - 1 - days until s.size step everyDays).toList()
        val each = total / idx.size
        val qty = idx.sumOf { qtyFor(each, s[it].price, feePct) }
        val lumpQty = qtyFor(total, s[s.size - 1 - days].price, feePct)
        val sellFee = 1 - feePct / 100
        return Dca(idx.size, total, qty, qty * currentPrice * sellFee, lumpQty * currentPrice * sellFee)
    }

    /**
     * Geçmişteki tüm [horizon] günlük dönemlerin getirilerinden olası sonuç dağılımı.
     * Değerler: [amount] paranın dönem sonundaki olası karşılığı.
     */
    fun forecast(s: List<DayPrice>, horizon: Int, amount: Double, feePct: Double, lookback: Int = 730): Forecast? {
        val from = max(0, s.size - lookback)
        val rets = ArrayList<Double>()
        for (i in from until s.size - horizon) rets += s[i + horizon].price / s[i].price
        if (rets.size < 30) return null
        rets.sort()
        val fees = (1 - feePct / 100) * (1 - feePct / 100)
        fun pct(p: Double) = rets[min(rets.size - 1, (p * (rets.size - 1)).toInt())] * amount * fees
        return Forecast(
            horizon, rets.size, rets.count { it * fees < 1.0 }.toDouble() / rets.size,
            pct(0.05), pct(0.10), pct(0.50), pct(0.90), pct(0.95),
        )
    }
}
