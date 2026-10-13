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
    val scanIntervalMin: Int = 10,
    val scanUniverse: Int = 100,
    val reversalNotify: Boolean = true,
    val reversalThreshold: Int = 65,
    val volumeSpikeNotify: Boolean = true,
    val volumeSpikeRatio: Double = 4.0,
    val alertCooldownMin: Int = 30,
    /** Bildirimleri arkadaş ağzıyla ("kanka ... amk") yaz. */
    val casualMode: Boolean = false,
    /** Otomatik bildirimleri sadece seçili coinler için gönder. */
    val onlySelectedCoins: Boolean = false,
    /** Seçili coinler (BTC, ETH gibi taban sembolleri). */
    val notifyCoins: Set<String> = emptySet(),
) {
    fun allows(base: String) = !onlySelectedCoins || base in notifyCoins
}

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
    /** Dip dönüş analizi (bkz. ReversalDetector). */
    val reversal: Reversal? = null,
    /** Önceki taramaya göre skor değişimi. */
    val scoreDelta: Int? = null,
)

@Serializable
data class Reversal(
    /** 0-100: "ne kadar düşük" ve "ne kadar dönüyor" birleşimi. */
    val score: Int,
    /** Hem aşırı düşüş hem dönüş koşulları sağlandı mı. */
    val candidate: Boolean,
    val depth: Double,
    val turn: Double,
    val drawdown30: Double,
    val bounceFromLow: Double,
    val reasons: List<String>,
    /** Bu coinin kendi geçmişinde aynı desen sonrası 24s getiri istatistiği. */
    val histWins: Int = 0,
    val histSamples: Int = 0,
    val histSumReturn: Double = 0.0,
) {
    val histWinRate: Double get() = if (histSamples >= 3) histWins.toDouble() / histSamples else -1.0
    val histAvgReturn: Double get() = if (histSamples > 0) histSumReturn / histSamples else 0.0
}

@Serializable
enum class SuggestionKind { REVERSAL, STRONG }

/** Uygulamanın öne çıkardığı coinlerin sonradan nasıl performans gösterdiğini takip etmek için. */
@Serializable
data class SuggestionRecord(
    val symbol: String,
    val base: String,
    val kind: SuggestionKind,
    val price: Double,
    val score: Int,
    val at: Long,
)

@Serializable
data class ScanResult(
    val at: Long,
    val signals: List<Signal>,
    val fearGreed: Int? = null,
    val fearGreedLabel: String? = null,
    val btcDominance: Double? = null,
    val totalMcapChange24h: Double? = null,
    /** Dip dönüş deseninin TÜM taranan coinlerdeki geçmiş başarısı (24s sonrası). */
    val revBaseWins: Int = 0,
    val revBaseSamples: Int = 0,
    val revBaseSumReturn: Double = 0.0,
) {
    val revBaseWinRate: Double get() = if (revBaseSamples >= 5) revBaseWins.toDouble() / revBaseSamples else -1.0
    val revBaseAvgReturn: Double get() = if (revBaseSamples > 0) revBaseSumReturn / revBaseSamples else 0.0
}
