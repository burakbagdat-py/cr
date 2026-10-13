package com.kriptoradar.analysis

import com.kriptoradar.data.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object Indicators {
    fun ema(values: List<Double>, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out
        val k = 2.0 / (period + 1)
        var prev = values.take(period).average()
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    /** Wilder RSI. */
    fun rsi(closes: List<Double>, period: Int = 14): DoubleArray {
        val out = DoubleArray(closes.size) { Double.NaN }
        if (closes.size <= period) return out
        var gain = 0.0; var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        gain /= period; loss /= period
        out[period] = if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            gain = (gain * (period - 1) + max(d, 0.0)) / period
            loss = (loss * (period - 1) + max(-d, 0.0)) / period
            out[i] = if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
        }
        return out
    }

    data class Macd(val macd: DoubleArray, val signal: DoubleArray, val hist: DoubleArray)

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, sig: Int = 9): Macd {
        val f = ema(closes, fast); val s = ema(closes, slow)
        val m = DoubleArray(closes.size) { if (f[it].isNaN() || s[it].isNaN()) Double.NaN else f[it] - s[it] }
        val firstValid = m.indexOfFirst { !it.isNaN() }
        val signal = DoubleArray(closes.size) { Double.NaN }
        if (firstValid >= 0) {
            val sub = ema(m.drop(firstValid), sig)
            for (i in sub.indices) signal[firstValid + i] = sub[i]
        }
        val h = DoubleArray(closes.size) { if (m[it].isNaN() || signal[it].isNaN()) Double.NaN else m[it] - signal[it] }
        return Macd(m, signal, h)
    }

    /** Bollinger %B ve bant genişliği (son mum için). */
    fun bollinger(closes: List<Double>, period: Int = 20, mult: Double = 2.0): Pair<Double, Double>? {
        if (closes.size < period) return null
        val w = closes.takeLast(period)
        val mean = w.average()
        val sd = sqrt(w.sumOf { (it - mean) * (it - mean) } / period)
        val upper = mean + mult * sd; val lower = mean - mult * sd
        val pctB = if (upper > lower) (closes.last() - lower) / (upper - lower) else 0.5
        val width = if (mean > 0) (upper - lower) / mean * 100 else 0.0
        return pctB to width
    }

    /** Ortalama gerçek aralık, fiyatın yüzdesi olarak. */
    fun atrPct(k: List<Kline>, period: Int = 14): Double {
        if (k.size <= period) return 0.0
        val trs = (1 until k.size).map { i ->
            maxOf(k[i].high - k[i].low, abs(k[i].high - k[i - 1].close), abs(k[i].low - k[i - 1].close))
        }
        val atr = trs.takeLast(period).average()
        return atr / k.last().close * 100
    }

    fun pctChange(from: Double, to: Double) = if (from > 0) (to - from) / from * 100 else 0.0
}
