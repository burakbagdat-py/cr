package com.kriptoradar.data

import kotlinx.serialization.Serializable

/*
 * Sanal portföy: gerçek para olmadan alım-satım. Tüm tutarlar TL.
 */

/** Bir coindeki toplam sanal varlık (ortalama maliyetli). */
@Serializable
data class Holding(
    val symbol: String,
    val base: String,
    val qty: Double,
    /** Bu miktar için ödenen toplam TL (komisyon dahil). */
    val costTry: Double,
    val openedAt: Long,
) {
    val avgCostTry: Double get() = if (qty > 0) costTry / qty else 0.0
}

@Serializable
enum class LedgerType { DEPOSIT, WITHDRAW, BUY, SELL, ADJUST, RESET }

/** Tüm hareketlerin kaydı (hesap ekstresi). */
@Serializable
data class LedgerEntry(
    val id: Long,
    val at: Long,
    val type: LedgerType,
    val base: String? = null,
    val qty: Double = 0.0,
    val priceTry: Double = 0.0,
    val amountTry: Double = 0.0,
    val feeTry: Double = 0.0,
    val pnlTry: Double = 0.0,
    val note: String = "",
)

@Serializable
enum class OrderKind { LIMIT_BUY, LIMIT_SELL, STOP_LOSS, TAKE_PROFIT }

/** Bekleyen emir: fiyat tetik seviyesine gelince otomatik çalışır. */
@Serializable
data class PaperOrder(
    val id: Long,
    val symbol: String,
    val base: String,
    val kind: OrderKind,
    val triggerTry: Double,
    /** Alım emirleri için harcanacak TL. */
    val amountTry: Double = 0.0,
    /** Satış emirleri için satılacak oran (0..1]. */
    val fraction: Double = 1.0,
    val createdAt: Long,
)

@Serializable
data class EquityPoint(val at: Long, val value: Double, val deposited: Double = -1.0)

@Serializable
data class PaperPortfolio(
    val startCash: Double = 10_000.0,
    val cash: Double = 10_000.0,
    val feePct: Double = 0.2,
    /** Gerçekçilik için alış-satış fiyat farkı (spread/kayma) yüzdesi. */
    val slippagePct: Double = 0.05,
    /** Kâr/zarar hesabının temeli: toplam yüklenen - çekilen para. */
    val totalDeposited: Double = 10_000.0,
    val realizedPnl: Double = 0.0,
    val feesPaid: Double = 0.0,
    val holdings: List<Holding> = emptyList(),
    val orders: List<PaperOrder> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val equity: List<EquityPoint> = emptyList(),
    // Eski sürüm (1.2.x) verileri: açılışta yeni yapıya taşınır
    val positions: List<PaperPosition> = emptyList(),
    val closed: List<PaperClosed> = emptyList(),
)

@Serializable
data class PaperPosition(
    val id: Long, val symbol: String, val base: String, val qty: Double,
    val entryPriceUsdt: Double, val entryRate: Double, val investedTry: Double, val at: Long,
)

@Serializable
data class PaperClosed(val base: String, val investedTry: Double, val returnedTry: Double, val openedAt: Long, val closedAt: Long)
