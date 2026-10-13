package com.kriptoradar.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tüm piyasanın canlı durumu. Binance "!miniTicker@arr" akışı her saniye
 * değişen tüm çiftleri gönderir; böylece tek bağlantıyla tüm piyasa saniyelik izlenir.
 * Kopma durumunda üstel bekleme ile yeniden bağlanır ve REST ile tam görüntü yenilenir.
 */
object MarketRepository {
    private const val TAG = "MarketRepo"
    private val wsHosts = listOf(
        "wss://data-stream.binance.vision/ws/!miniTicker@arr",
        "wss://stream.binance.com:9443/ws/!miniTicker@arr",
        "wss://stream.binance.com:443/ws/!miniTicker@arr",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val map = ConcurrentHashMap<String, Ticker>()
    private val histories = ConcurrentHashMap<String, PriceHistory>()

    private val _tickers = MutableStateFlow<Map<String, Ticker>>(emptyMap())
    val tickers: StateFlow<Map<String, Ticker>> = _tickers

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state

    private val _lastMessageAt = MutableStateFlow(0L)
    val lastMessageAt: StateFlow<Long> = _lastMessageAt

    /** Her akış paketinde güncellenen sembollerin listesi (alarm motoru için). */
    private val _updates = MutableSharedFlow<List<Ticker>>(extraBufferCapacity = 16)
    val updates: SharedFlow<List<Ticker>> = _updates

    private val clients = AtomicInteger(0)
    private var runJob: Job? = null
    private var socket: WebSocket? = null

    fun history(symbol: String): PriceHistory? = histories[symbol]

    @Synchronized
    fun acquire() {
        if (clients.incrementAndGet() == 1 || runJob?.isActive != true) start()
    }

    @Synchronized
    fun release() {
        if (clients.decrementAndGet() <= 0) {
            clients.set(0)
            // Kısa süreli ekran geçişlerinde bağlantıyı koparmamak için gecikmeli kapat
            scope.launch {
                delay(20_000)
                synchronized(this@MarketRepository) { if (clients.get() == 0) stop() }
            }
        }
    }

    private fun start() {
        runJob?.cancel()
        runJob = scope.launch {
            launch { snapshotLoop() }
            connectLoop()
        }
    }

    private fun stop() {
        runJob?.cancel()
        runJob = null
        socket?.close(1000, "bye")
        socket = null
        _state.value = ConnState.DISCONNECTED
    }

    /** Akışın vermediği alanları (tam liste, yeni listelenen coinler) tazelemek için periyodik REST. */
    private suspend fun snapshotLoop() {
        while (scope.isActive) {
            refreshSnapshot()
            delay(if (_state.value == ConnState.LIVE) 180_000 else 20_000)
        }
    }

    suspend fun refreshSnapshot(): Boolean = try {
        val list = BinanceApi.tickers24h()
        val now = System.currentTimeMillis()
        for (t in list) {
            val existing = map[t.symbol]
            // Akış verisi daha tazeyse onu koru
            if (existing == null || now - existing.updatedAt > 10_000) {
                map[t.symbol] = t
                histories.getOrPut(t.symbol) { PriceHistory() }.add(now, t.price)
            }
        }
        _tickers.value = HashMap(map)
        true
    } catch (e: Exception) {
        Log.w(TAG, "snapshot hatası: ${e.message}")
        false
    }

    private suspend fun connectLoop() {
        var attempt = 0
        var hostIdx = 0
        while (scope.isActive) {
            _state.value = if (attempt == 0) ConnState.CONNECTING else ConnState.RECONNECTING
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val ws = Http.client.newWebSocket(
                Request.Builder().url(wsHosts[hostIdx]).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        attempt = 0
                        _state.value = ConnState.LIVE
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "ws hata: ${t.message}")
                        closed.complete(Unit)
                    }
                },
            )
            socket = ws
            // Bekçi: 30 sn veri gelmezse bağlantıyı yenile
            val watchdog = scope.launch {
                while (isActive) {
                    delay(10_000)
                    val last = _lastMessageAt.value
                    if (_state.value == ConnState.LIVE && last > 0 && System.currentTimeMillis() - last > 30_000) {
                        ws.cancel(); closed.complete(Unit)
                    }
                }
            }
            try {
                closed.await()
            } finally {
                watchdog.cancel()
                ws.cancel()
            }
            if (!scope.isActive) break
            attempt++
            if (attempt % 2 == 0) hostIdx = (hostIdx + 1) % wsHosts.size
            _state.value = ConnState.RECONNECTING
            delay((1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000))
        }
    }

    private fun handle(text: String) {
        val now = System.currentTimeMillis()
        try {
            val arr = Http.json.parseToJsonElement(text).jsonArray
            val changed = ArrayList<Ticker>(arr.size)
            for (el in arr) {
                val o = el.jsonObject
                val symbol = o["s"]?.jsonPrimitive?.content ?: continue
                val (base, quote) = BinanceApi.splitSymbol(symbol) ?: continue
                val c = o["c"]!!.jsonPrimitive.content.toDouble()
                val t = Ticker(
                    symbol, base, quote, c,
                    open24h = o["o"]!!.jsonPrimitive.content.toDouble(),
                    high24h = o["h"]!!.jsonPrimitive.content.toDouble(),
                    low24h = o["l"]!!.jsonPrimitive.content.toDouble(),
                    baseVolume = o["v"]!!.jsonPrimitive.content.toDouble(),
                    quoteVolume = o["q"]!!.jsonPrimitive.content.toDouble(),
                    updatedAt = now,
                )
                map[symbol] = t
                histories.getOrPut(symbol) { PriceHistory() }.add(now, c)
                changed += t
            }
            _lastMessageAt.value = now
            _tickers.value = HashMap(map)
            _updates.tryEmit(changed)
        } catch (e: Exception) {
            Log.w(TAG, "parse: ${e.message}")
        }
    }
}
