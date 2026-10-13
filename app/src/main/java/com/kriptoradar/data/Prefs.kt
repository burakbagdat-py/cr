package com.kriptoradar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Ayarlar, alarmlar, favoriler ve son tarama sonucu için basit kalıcı depo. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private val json get() = Http.json

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _alerts = MutableStateFlow<List<PriceAlert>>(emptyList())
    val alerts: StateFlow<List<PriceAlert>> = _alerts

    private val _favorites = MutableStateFlow<Set<String>>(setOf("BTCUSDT", "ETHUSDT"))
    val favorites: StateFlow<Set<String>> = _favorites

    private val _scan = MutableStateFlow<ScanResult?>(null)
    val scan: StateFlow<ScanResult?> = _scan

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("kripto_radar", Context.MODE_PRIVATE)
        _settings.value = read("settings", Settings.serializer()) ?: Settings()
        _alerts.value = read("alerts", ListSerializer(PriceAlert.serializer())) ?: emptyList()
        _favorites.value = read("favorites", SetSerializer(String.serializer())) ?: _favorites.value
        _scan.value = read("scan", ScanResult.serializer())
    }

    private fun <T> read(key: String, s: kotlinx.serialization.KSerializer<T>): T? =
        sp.getString(key, null)?.let { runCatching { json.decodeFromString(s, it) }.getOrNull() }

    private fun <T> write(key: String, s: kotlinx.serialization.KSerializer<T>, v: T) {
        sp.edit().putString(key, json.encodeToString(s, v)).apply()
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val v = f(_settings.value); _settings.value = v; write("settings", Settings.serializer(), v)
    }

    @Synchronized
    fun updateAlerts(f: (List<PriceAlert>) -> List<PriceAlert>) {
        val v = f(_alerts.value); _alerts.value = v; write("alerts", ListSerializer(PriceAlert.serializer()), v)
    }

    fun toggleFavorite(symbol: String) {
        val v = _favorites.value.let { if (symbol in it) it - symbol else it + symbol }
        _favorites.value = v; write("favorites", SetSerializer(String.serializer()), v)
    }

    fun saveScan(r: ScanResult) {
        _scan.value = r; write("scan", ScanResult.serializer(), r)
    }

    /** Bildirim bekleme süresi takibi (aynı olay için tekrar tekrar bildirim gitmesin). */
    fun lastNotified(key: String): Long = sp.getLong("n_$key", 0)
    fun markNotified(key: String, t: Long) = sp.edit().putLong("n_$key", t).apply()
}
