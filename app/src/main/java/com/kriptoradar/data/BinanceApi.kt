package com.kriptoradar.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Binance halka açık piyasa verisi. Anahtar gerekmez.
 * Bir host erişilemezse sıradakine geçilir (bölgesel engellere karşı).
 */
object BinanceApi {
    private val restHosts = listOf(
        "https://data-api.binance.vision",
        "https://api.binance.com",
        "https://api1.binance.com",
        "https://api2.binance.com",
        "https://api3.binance.com",
    )
    @Volatile private var preferred = 0

    val QUOTES = listOf("USDT", "TRY", "FDUSD", "USDC", "BTC")

    private val excludedBases = setOf(
        "USDC", "FDUSD", "TUSD", "BUSD", "DAI", "USDP", "EUR", "EURI", "AEUR", "PAX", "USTC",
        "UST", "GBP", "AUD", "TRY", "BRL", "PAXG", "XUSD", "USD1", "BFUSD", "RLUSD", "USDE",
    )

    /** Kaldıraçlı token ve stabil coin gibi analiz için anlamsız çiftleri eler. */
    fun isTradable(base: String): Boolean =
        base !in excludedBases && !base.endsWith("UP") && !base.endsWith("DOWN") &&
            !base.endsWith("BULL") && !base.endsWith("BEAR")

    fun splitSymbol(symbol: String): Pair<String, String>? {
        for (q in QUOTES) if (symbol.endsWith(q) && symbol.length > q.length) return symbol.dropLast(q.length) to q
        return null
    }

    private suspend fun getWithFallback(path: String): String {
        var last: Exception? = null
        for (i in restHosts.indices) {
            val idx = (preferred + i) % restHosts.size
            try {
                val body = Http.get(restHosts[idx] + path)
                preferred = idx
                return body
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Binance erişilemedi")
    }

    suspend fun tickers24h(): List<Ticker> {
        val arr = Http.json.parseToJsonElement(getWithFallback("/api/v3/ticker/24hr?type=FULL")).jsonArray
        val now = System.currentTimeMillis()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val symbol = o.str("symbol") ?: return@mapNotNull null
            val (base, quote) = splitSymbol(symbol) ?: return@mapNotNull null
            val price = o.dbl("lastPrice") ?: return@mapNotNull null
            if (price <= 0.0) return@mapNotNull null
            Ticker(
                symbol, base, quote, price,
                open24h = o.dbl("openPrice") ?: price,
                high24h = o.dbl("highPrice") ?: price,
                low24h = o.dbl("lowPrice") ?: price,
                baseVolume = o.dbl("volume") ?: 0.0,
                quoteVolume = o.dbl("quoteVolume") ?: 0.0,
                updatedAt = now,
            )
        }
    }

    suspend fun klines(symbol: String, interval: String, limit: Int): List<Kline> {
        val arr = Http.json.parseToJsonElement(
            getWithFallback("/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit")
        ).jsonArray
        return arr.map { k ->
            val a = k.jsonArray
            Kline(
                openTime = a[0].jsonPrimitive.content.toLong(),
                open = a[1].jsonPrimitive.content.toDouble(),
                high = a[2].jsonPrimitive.content.toDouble(),
                low = a[3].jsonPrimitive.content.toDouble(),
                close = a[4].jsonPrimitive.content.toDouble(),
                volume = a[5].jsonPrimitive.content.toDouble(),
                quoteVolume = a[7].jsonPrimitive.content.toDouble(),
            )
        }
    }

    /** Korku & Açgözlülük endeksi (alternative.me). */
    suspend fun fearGreed(): Pair<Int, String>? = runCatching {
        val o = Http.json.parseToJsonElement(Http.get("https://api.alternative.me/fng/?limit=1")).jsonObject
        val d = (o["data"] as JsonArray)[0].jsonObject
        val v = d["value"]!!.jsonPrimitive.content.toInt()
        v to trFearGreed(d["value_classification"]?.jsonPrimitive?.content ?: "")
    }.getOrNull()

    /** CoinGecko küresel piyasa: BTC dominansı ve toplam piyasa değeri değişimi. */
    suspend fun global(): Pair<Double, Double>? = runCatching {
        val o = Http.json.parseToJsonElement(Http.get("https://api.coingecko.com/api/v3/global")).jsonObject
        val d = o["data"]!!.jsonObject
        val dom = d["market_cap_percentage"]!!.jsonObject["btc"]!!.jsonPrimitive.doubleOrNull!!
        val chg = d["market_cap_change_percentage_24h_usd"]!!.jsonPrimitive.doubleOrNull!!
        dom to chg
    }.getOrNull()

    private fun trFearGreed(s: String) = when (s.lowercase()) {
        "extreme fear" -> "Aşırı Korku"
        "fear" -> "Korku"
        "neutral" -> "Nötr"
        "greed" -> "Açgözlülük"
        "extreme greed" -> "Aşırı Açgözlülük"
        else -> s
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content
    private fun JsonObject.dbl(k: String) = this[k]?.jsonPrimitive?.content?.toDoubleOrNull()
    @Suppress("unused") private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
}
