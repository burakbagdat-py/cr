package com.kriptoradar.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object Format {
    private val tr = DecimalFormatSymbols(Locale("tr", "TR"))
    private fun df(p: String) = DecimalFormat(p, tr)
    private val f2 = df("#,##0.00")
    private val f4 = df("#,##0.0000")
    private val f6 = df("0.000000")
    private val f8 = df("0.00000000")

    fun price(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1000 -> f2.format(v)
            a >= 1 -> f4.format(v)
            a >= 0.01 -> f6.format(v)
            else -> f8.format(v)
        }
    }

    fun num(v: Double): String = f2.format(v)

    fun signedPct(v: Double): String = (if (v > 0) "+" else "") + f2.format(v) + "%"

    fun compact(v: Double): String = when {
        v >= 1e9 -> f2.format(v / 1e9) + " Mr"
        v >= 1e6 -> f2.format(v / 1e6) + " Mn"
        v >= 1e3 -> f2.format(v / 1e3) + " B"
        else -> f2.format(v)
    }

    fun ago(ms: Long): String {
        if (ms <= 0) return "—"
        val s = (System.currentTimeMillis() - ms) / 1000
        return when {
            s < 5 -> "şimdi"
            s < 60 -> "$s sn önce"
            s < 3600 -> "${s / 60} dk önce"
            s < 86400 -> "${s / 3600} sa önce"
            else -> "${s / 86400} gün önce"
        }
    }
}
