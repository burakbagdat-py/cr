package com.kriptoradar.data

import kotlinx.serialization.Serializable

/** Anlık piyasa verisi (tek bir işlem çifti). */
data class Ticker(
    val symbol: String,
    val base: String,
    val quote: String,
    val price: Double,
    val open24h: Double,
    val high24h: Double,
    val low24h: Double,
    val baseVolume: Double,
    val quoteVolume: Double,
    val updatedAt: Long,
) {
    val change24hPct: Double get() = if (open24h > 0) (price - open24h) / open24h * 100.0 else 0.0
    /** 24s aralığında fiyatın konumu: 0 = dip, 1 = zirve. */
    val rangePosition: Double get() = if (high24h > low24h) (price - low24h) / (high24h - low24h) else 0.5
}

data class Kline(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val quoteVolume: Double,
)

enum class ConnState { DISCONNECTED, CONNECTING, LIVE, RECONNECTING }

@Serializable
enum class AlertType { PRICE_ABOVE, PRICE_BELOW, CHANGE_24H_UP, CHANGE_24H_DOWN, MOVE_IN_WINDOW }

@Serializable
data class PriceAlert(
    val id: Long,
    val symbol: String,
    val type: AlertType,
    val value: Double,
    /** Sadece MOVE_IN_WINDOW için: dakika cinsinden pencere. */
    val windowMin: Int = 5,
    val repeat: Boolean = false,
    val enabled: Boolean = true,
    val lastTriggeredAt: Long = 0,
)

@Serializable
data class Settings(
    val quote: String = "USDT",
    val serviceEnabled: Boolean = true,
    val rapidMoveEnabled: Boolean = true,
    val rapidMovePct: Double = 3.0,
    val rapidMoveWindowMin: Int = 5,
    /** Otomatik hızlı hareket bildirimleri için en düşük 24s hacim (quote cinsinden). */
    val minQuoteVolume: Double = 10_000_000.0,
    val signalNotify: Boolean = true,
    val signalThreshold: Int = 75,
    val scanIntervalMin: Int = 15,
    val scanUniverse: Int = 60,
    val volumeSpikeNotify: Boolean = true,
    val volumeSpikeRatio: Double = 4.0,
    val alertCooldownMin: Int = 30,
)

@Serializable
data class Factor(val name: String, val score: Double, val detail: String)

@Serializable
data class Signal(
    val symbol: String,
    val base: String,
    val price: Double,
    val score: Int,
    val label: String,
    val change24h: Double,
    val rsi: Double,
    val volumeRatio: Double,
    val factors: List<Factor>,
    /** Geçmişte benzer koşullarda 12 saat sonraki pozitif getiri oranı (0..1), örnek yoksa -1. */
    val histWinRate: Double,
    val histAvgReturn: Double,
    val histSamples: Int,
    val volatilityPct: Double,
)

@Serializable
data class ScanResult(
    val at: Long,
    val signals: List<Signal>,
    val fearGreed: Int? = null,
    val fearGreedLabel: String? = null,
    val btcDominance: Double? = null,
    val totalMcapChange24h: Double? = null,
)
