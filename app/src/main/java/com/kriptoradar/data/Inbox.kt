package com.kriptoradar.data

import kotlinx.serialization.Serializable

@Serializable
enum class InboxKind { SIGNAL, REVERSAL, MOVE, VOLUME, ALERT, PAPER, SUMMARY }

/** Uygulama içi bildirim kutusundaki bir kayıt. */
@Serializable
data class InboxItem(
    val id: Long,
    val at: Long,
    val kind: InboxKind,
    val title: String,
    val text: String,
    val symbol: String? = null,
    /** "Al" düğmesi gösterilsin mi (USDT çifti). */
    val buySymbol: String? = null,
    /** Bildirim anındaki fiyat (USDT) – "bildirimden beri" değişimi için. */
    val priceAt: Double = 0.0,
    val read: Boolean = false,
)
