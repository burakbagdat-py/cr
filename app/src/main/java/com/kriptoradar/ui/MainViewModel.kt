package com.kriptoradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoradar.alerts.PaperExecutor
import com.kriptoradar.alerts.ScanRunner
import com.kriptoradar.data.OrderKind
import com.kriptoradar.data.PaperEngine
import com.kriptoradar.data.PaperOrder
import com.kriptoradar.data.PaperPortfolio
import com.kriptoradar.data.AlertType
import com.kriptoradar.data.BinanceApi
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.PriceAlert
import com.kriptoradar.data.Prefs
import com.kriptoradar.data.Ticker
import com.kriptoradar.service.MarketService
import com.kriptoradar.update.UpdateInfo
import com.kriptoradar.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MarketFilter(val label: String) {
    FAV("Favoriler"), ALL("Tümü"), GAINERS("Yükselenler"), LOSERS("Düşenler"), VOLUME("Hacim"), MOVERS("Ani hareket")
}

data class MarketRow(val t: Ticker, val change5m: Double?, val fav: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val tickers = MarketRepository.tickers
    val conn = MarketRepository.state
    val lastMsg = MarketRepository.lastMessageAt
    val settings = Prefs.settings
    val alerts = Prefs.alerts
    val favorites = Prefs.favorites
    val scan = Prefs.scan
    val suggestions = Prefs.suggestions

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(MarketFilter.VOLUME)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    val rows: StateFlow<List<MarketRow>> = combine(tickers, query, filter, favorites, settings) { tk, q, f, fav, s ->
        val now = System.currentTimeMillis()
        val qq = q.trim().uppercase()
        var list = tk.values.asSequence()
            .filter { if (f == MarketFilter.FAV) it.symbol in fav else it.quote == s.quote }
            .filter { qq.isEmpty() || it.base.contains(qq) || it.symbol.contains(qq) }
            .filter { f == MarketFilter.FAV || qq.isNotEmpty() || it.quoteVolume > 0 }
            .map { MarketRow(it, MarketRepository.history(it.symbol)?.changePct(now, 5 * 60_000L, it.price), it.symbol in fav) }
            .toList()
        list = when (f) {
            MarketFilter.GAINERS -> list.filter { BinanceApi.isTradable(it.t.base) && it.t.quoteVolume > 500_000 }.sortedByDescending { it.t.change24hPct }
            MarketFilter.LOSERS -> list.filter { BinanceApi.isTradable(it.t.base) && it.t.quoteVolume > 500_000 }.sortedBy { it.t.change24hPct }
            MarketFilter.MOVERS -> list.filter { it.change5m != null && it.t.quoteVolume > 500_000 }.sortedByDescending { kotlin.math.abs(it.change5m!!) }
            else -> list.sortedByDescending { it.t.quoteVolume }
        }
        list.take(300)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        MarketRepository.acquire()
        // Servis kapalıyken de sanal emirler uygulama açıkken çalışsın
        viewModelScope.launch(Dispatchers.Default) {
            MarketRepository.updates.collect { if (!MarketService.running) PaperExecutor.onTicks(getApplication()) }
        }
        viewModelScope.launch { UpdateManager.check() }
        if (Prefs.settings.value.serviceEnabled) MarketService.start(app)
        // İlk açılışta veya veri eskiyse otomatik tara
        val last = Prefs.scan.value?.at ?: 0
        if (System.currentTimeMillis() - last > Prefs.settings.value.scanIntervalMin * 60_000L) {
            viewModelScope.launch { kotlinx.coroutines.delay(4_000); scanNow() }
        }
    }

    override fun onCleared() {
        MarketRepository.release()
        super.onCleared()
    }

    val update = UpdateManager.state
    val paper = Prefs.paper

    /** Simülatörde seçili coin (USDT çifti) ve sekme geçiş isteği. */
    val simSymbol = MutableStateFlow("BTCUSDT")
    val navRequest = MutableStateFlow<String?>(null)

    fun openSimulator(symbol: String) {
        simSymbol.value = symbol
        navRequest.value = "sim"
    }

    fun usdTry(): Double? = tickers.value["USDTTRY"]?.price

    // ---------------- Sanal portföy ----------------
    fun paperPrices(): Map<String, Double> = PaperExecutor.pricesTry(tickers.value)
    fun priceTry(symbol: String): Double? = tickers.value[symbol]?.let { t -> usdTry()?.let { t.price * it } }

    /** Portföy işlemi uygular; hata varsa mesajını döndürür. */
    private fun paperOp(f: (PaperPortfolio) -> PaperPortfolio): String? = try {
        Prefs.updatePaper(f); null
    } catch (e: PaperEngine.PaperException) { e.message } catch (e: Exception) { "Hata: ${e.message}" }

    fun paperBuy(symbol: String, amountTry: Double): String? {
        val t = tickers.value[symbol] ?: return "Fiyat bulunamadı"
        val price = priceTry(symbol) ?: return "Dolar/TL kuru alınamadı"
        return paperOp { PaperEngine.buy(it, symbol, t.base, price, amountTry) }
    }

    /** Kârlı satışta kutlama (zaman, kâr). */
    val celebrate = MutableStateFlow<Pair<Long, Double>?>(null)

    fun paperSell(symbol: String, fraction: Double): String? {
        val price = priceTry(symbol) ?: return "Fiyat bulunamadı"
        val err = paperOp { PaperEngine.sell(it, symbol, fraction, price) }
        if (err == null) paper.value.ledger.firstOrNull()?.takeIf { it.pnlTry > 0 }?.let { celebrate.value = System.currentTimeMillis() to it.pnlTry }
        return err
    }

    fun paperOrder(symbol: String, kind: OrderKind, triggerTry: Double, amountTry: Double, fraction: Double): String? {
        val t = tickers.value[symbol] ?: return "Coin bulunamadı"
        return paperOp { PaperEngine.placeOrder(it, PaperOrder(0, symbol, t.base, kind, triggerTry, amountTry, fraction, System.currentTimeMillis())) }
    }

    fun paperCancel(id: Long) = paperOp { PaperEngine.cancelOrder(it, id) }
    fun paperDeposit(amount: Double, note: String = "") = paperOp { PaperEngine.deposit(it, amount, note) }
    fun paperWithdraw(amount: Double) = paperOp { PaperEngine.withdraw(it, amount) }
    fun paperSetCash(v: Double) = paperOp { PaperEngine.setCash(it, v) }
    fun paperGrant(symbol: String, qty: Double): String? {
        val t = tickers.value[symbol] ?: return "Coin bulunamadı"
        val price = priceTry(symbol) ?: return "Fiyat yok"
        return paperOp { PaperEngine.grant(it, symbol, t.base, qty, price) }
    }
    fun paperRemove(symbol: String) = paperOp { PaperEngine.removeHolding(it, symbol, priceTry(symbol)) }
    fun paperFees(feePct: Double, slippagePct: Double) = paperOp { it.copy(feePct = feePct.coerceIn(0.0, 5.0), slippagePct = slippagePct.coerceIn(0.0, 5.0)) }
    fun paperClearLedger() = paperOp { it.copy(ledger = emptyList(), equity = emptyList()) }
    fun paperCancelAll() = paperOp { it.copy(orders = emptyList()) }
    fun paperReset(startCash: Double) = paperOp { PaperEngine.reset(it, startCash) }

    // ---------------- Admin ----------------
    private val _adminUnlocked = MutableStateFlow(false)
    val adminUnlocked: StateFlow<Boolean> = _adminUnlocked
    fun hasAdminPin() = Prefs.hasAdminPin()
    fun unlockAdmin(pin: String): Boolean = Prefs.checkAdminPin(pin).also { _adminUnlocked.value = it }
    fun setAdminPin(pin: String) { Prefs.setAdminPin(pin); _adminUnlocked.value = true }
    fun lockAdmin() { _adminUnlocked.value = false }
    fun openAdmin() { navRequest.value = "admin" }

    fun checkUpdate() { viewModelScope.launch { UpdateManager.check() } }
    fun downloadUpdate(info: UpdateInfo) { viewModelScope.launch { UpdateManager.download(getApplication(), info) } }
    fun installUpdate() = UpdateManager.install(getApplication())

    fun clearSuggestions() = Prefs.clearSuggestions()

    fun toggleFav(symbol: String) = Prefs.toggleFavorite(symbol)

    fun scanNow() {
        if (_scanning.value) return
        viewModelScope.launch(Dispatchers.Default) {
            _scanning.value = true
            try {
                ScanRunner.run(getApplication())
            } catch (_: Exception) {
            } finally {
                _scanning.value = false
            }
        }
    }

    fun addAlert(symbol: String, type: AlertType, value: Double, windowMin: Int, repeat: Boolean) {
        Prefs.updateAlerts { it + PriceAlert(System.currentTimeMillis(), symbol, type, value, windowMin, repeat) }
    }

    /** Hedef fiyat girildiğinde yönü otomatik belirle. */
    fun addPriceTarget(symbol: String, target: Double, repeat: Boolean) {
        val cur = tickers.value[symbol]?.price ?: return
        addAlert(symbol, if (target >= cur) AlertType.PRICE_ABOVE else AlertType.PRICE_BELOW, target, 5, repeat)
    }

    fun toggleAlert(id: Long) = Prefs.updateAlerts { l -> l.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
    fun deleteAlert(id: Long) = Prefs.updateAlerts { l -> l.filterNot { it.id == id } }

    fun setService(enabled: Boolean) {
        Prefs.updateSettings { it.copy(serviceEnabled = enabled) }
        if (enabled) MarketService.start(getApplication()) else MarketService.stop(getApplication())
    }
}
