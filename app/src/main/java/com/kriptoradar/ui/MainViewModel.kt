package com.kriptoradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kriptoradar.alerts.ScanRunner
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

    /** Sanal portföyde TL ile al. Başarısızsa hata mesajı döner. */
    fun paperBuy(symbol: String, amountTry: Double): String? {
        val t = tickers.value[symbol] ?: return "Fiyat bulunamadı"
        val rate = usdTry() ?: return "Dolar/TL kuru alınamadı"
        val p = paper.value
        if (amountTry <= 0) return "Tutar girin"
        if (amountTry > p.cash + 1e-6) return "Sanal bakiyen yetersiz (${Format.num(p.cash)} TL)"
        val qty = com.kriptoradar.analysis.Simulator.qtyFor(amountTry, t.price * rate, p.feePct)
        Prefs.updatePaper {
            it.copy(cash = it.cash - amountTry, positions = it.positions + com.kriptoradar.data.PaperPosition(
                System.currentTimeMillis(), symbol, t.base, qty, t.price, rate, amountTry, System.currentTimeMillis()))
        }
        return null
    }

    fun paperValue(pos: com.kriptoradar.data.PaperPosition): Double? {
        val t = tickers.value[pos.symbol] ?: return null
        val rate = usdTry() ?: return null
        return pos.qty * t.price * rate * (1 - paper.value.feePct / 100)
    }

    fun paperSell(id: Long) {
        val pos = paper.value.positions.firstOrNull { it.id == id } ?: return
        val v = paperValue(pos) ?: return
        Prefs.updatePaper {
            it.copy(
                cash = it.cash + v,
                positions = it.positions.filterNot { p -> p.id == id },
                closed = (listOf(com.kriptoradar.data.PaperClosed(pos.base, pos.investedTry, v, pos.at, System.currentTimeMillis())) + it.closed).take(100),
            )
        }
    }

    fun paperReset(startCash: Double) = Prefs.updatePaper { com.kriptoradar.data.PaperPortfolio(startCash = startCash, cash = startCash, feePct = it.feePct) }

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
