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

    private val _suggestions = MutableStateFlow<List<SuggestionRecord>>(emptyList())
    val suggestions: StateFlow<List<SuggestionRecord>> = _suggestions

    private val _readLessons = MutableStateFlow<Set<String>>(emptySet())
    val readLessons: StateFlow<Set<String>> = _readLessons

    private val _paper = MutableStateFlow(PaperPortfolio())
    val paper: StateFlow<PaperPortfolio> = _paper

    private val _scan = MutableStateFlow<ScanResult?>(null)
    val scan: StateFlow<ScanResult?> = _scan

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("kripto_radar", Context.MODE_PRIVATE)
        _settings.value = read("settings", Settings.serializer()) ?: Settings()
        _alerts.value = read("alerts", ListSerializer(PriceAlert.serializer())) ?: emptyList()
        _favorites.value = read("favorites", SetSerializer(String.serializer())) ?: _favorites.value
        _scan.value = read("scan", ScanResult.serializer())
        _paper.value = PaperEngine.migrate(read("paper", PaperPortfolio.serializer()) ?: PaperPortfolio())
        _readLessons.value = read("read_lessons", SetSerializer(String.serializer())) ?: emptySet()
        _suggestions.value = read("suggestions", ListSerializer(SuggestionRecord.serializer())) ?: emptyList()
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

    @Synchronized
    fun addSuggestions(items: List<SuggestionRecord>) {
        if (items.isEmpty()) return
        val v = (items + _suggestions.value).take(200)
        _suggestions.value = v; write("suggestions", ListSerializer(SuggestionRecord.serializer()), v)
    }

    @Synchronized
    fun updatePaper(f: (PaperPortfolio) -> PaperPortfolio) {
        val v = f(_paper.value); _paper.value = v; write("paper", PaperPortfolio.serializer(), v)
    }

    fun markLessonRead(id: String) {
        if (id in _readLessons.value) return
        val v = _readLessons.value + id; _readLessons.value = v; write("read_lessons", SetSerializer(String.serializer()), v)
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList(); write("suggestions", ListSerializer(SuggestionRecord.serializer()), emptyList())
    }

    // ---------------- Admin PIN (SHA-256 ile saklanır) ----------------
    private fun hash(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(("kriptoradar:" + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun hasAdminPin() = sp.getString("admin_pin", null) != null
    fun setAdminPin(pin: String) = sp.edit().putString("admin_pin", hash(pin)).apply()
    fun checkAdminPin(pin: String) = sp.getString("admin_pin", null) == hash(pin)

    // ---------------- Yedekleme ----------------
    @kotlinx.serialization.Serializable
    data class Backup(
        val version: Int = 1,
        val settings: Settings,
        val alerts: List<PriceAlert>,
        val favorites: Set<String>,
        val paper: PaperPortfolio,
        val suggestions: List<SuggestionRecord>,
    )

    fun exportJson(): String = json.encodeToString(Backup.serializer(),
        Backup(settings = _settings.value, alerts = _alerts.value, favorites = _favorites.value, paper = _paper.value, suggestions = _suggestions.value))

    fun importJson(text: String) {
        val b = json.decodeFromString(Backup.serializer(), text.trim())
        updateSettings { b.settings }
        updateAlerts { b.alerts }
        _favorites.value = b.favorites; write("favorites", SetSerializer(String.serializer()), b.favorites)
        updatePaper { PaperEngine.migrate(b.paper) }
        _suggestions.value = b.suggestions; write("suggestions", ListSerializer(SuggestionRecord.serializer()), b.suggestions)
    }

    /** Bildirim bekleme sürelerini sıfırla (aynı olaylar tekrar bildirilebilir). */
    fun clearCooldowns() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("n_") && !it.startsWith("n_update") }.forEach { e.remove(it) }
        e.apply()
    }

    /** Bildirim bekleme süresi takibi (aynı olay için tekrar tekrar bildirim gitmesin). */
    fun lastNotified(key: String): Long = sp.getLong("n_$key", 0)
    fun markNotified(key: String, t: Long) = sp.edit().putLong("n_$key", t).apply()
}
