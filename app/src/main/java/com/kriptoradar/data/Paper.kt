package com.kriptoradar.data

import kotlinx.serialization.Serializable

/** Sanal portföy: gerçek para olmadan alım-satım denemesi. Tüm tutarlar TL. */
@Serializable
data class PaperPosition(
    val id: Long,
    val symbol: String,
    val base: String,
    val qty: Double,
    val entryPriceUsdt: Double,
    val entryRate: Double,
    val investedTry: Double,
    val at: Long,
)

@Serializable
data class PaperClosed(
    val base: String,
    val investedTry: Double,
    val returnedTry: Double,
    val openedAt: Long,
    val closedAt: Long,
) {
    val pnl get() = returnedTry - investedTry
}

@Serializable
data class PaperPortfolio(
    val startCash: Double = 10_000.0,
    val cash: Double = 10_000.0,
    val feePct: Double = 0.2,
    val positions: List<PaperPosition> = emptyList(),
    val closed: List<PaperClosed> = emptyList(),
)
