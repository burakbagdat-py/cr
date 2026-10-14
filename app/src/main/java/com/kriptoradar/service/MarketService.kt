package com.kriptoradar.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kriptoradar.alerts.AlertEngine
import com.kriptoradar.alerts.Notifier
import com.kriptoradar.alerts.PaperExecutor
import com.kriptoradar.alerts.ScanRunner
import com.kriptoradar.data.ConnState
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.Prefs
import com.kriptoradar.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Uygulama kapalıyken bile tüm piyasayı saniyelik izleyen ön plan servisi.
 * - Canlı WebSocket akışını açık tutar
 * - Her pakette alarmları ve ani hareketleri kontrol eder
 * - Belirli aralıklarla istatistiksel tarama yapar
 */
class MarketService : LifecycleService() {
    private lateinit var engine: AlertEngine
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        engine = AlertEngine(this)
        running = true
        startAsForeground("Kripto Radar başlatılıyor…", "Piyasaya bağlanılıyor")
        // Kısmi wake lock: ekran kapalıyken CPU'nun uyuyup akışı kaçırmasını engeller
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KriptoRadar::stream")
            .apply { setReferenceCounted(false); acquire() }
        MarketRepository.acquire()

        lifecycleScope.launch(Dispatchers.Default) {
            MarketRepository.updates.collect {
                engine.onTicks(it)
                PaperExecutor.onTicks(this@MarketService)
            }
        }
        lifecycleScope.launch { liveNotificationLoop() }
        lifecycleScope.launch(Dispatchers.Default) { scanLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_SCAN_NOW) lifecycleScope.launch(Dispatchers.Default) { runScan() }
        return START_STICKY
    }

    private fun startAsForeground(title: String, text: String) {
        val n = Notifier.liveNotification(this, title, text)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.LIVE_ID, n, type)
    }

    private suspend fun liveNotificationLoop() {
        val nm = NotificationManagerCompat.from(this)
        while (lifecycleScope.isActive) {
            val tk = MarketRepository.tickers.value
            val st = MarketRepository.state.value
            val q = Prefs.settings.value.quote
            val parts = Prefs.favorites.value.mapNotNull { tk[it] }.take(4).joinToString("  •  ") {
                "${it.base} ${Format.price(it.price)} (${Format.signedPct(it.change24hPct)})"
            }
            val title = when (st) {
                ConnState.LIVE -> "● Canlı • ${tk.count { it.value.quote == q }} $q çifti izleniyor"
                ConnState.CONNECTING -> "Bağlanıyor…"
                ConnState.RECONNECTING -> "Yeniden bağlanıyor…"
                ConnState.DISCONNECTED -> "Bağlantı yok"
            }
            if (Notifier.canPost(this)) {
                try {
                    nm.notify(Notifier.LIVE_ID, Notifier.liveNotification(this, title, parts.ifEmpty { "Favori coin ekleyin" }))
                } catch (_: SecurityException) {}
            }
            runCatching { com.kriptoradar.alerts.DailySummary.maybeSend(this@MarketService) }
            delay(5_000)
        }
    }

    private suspend fun scanLoop() {
        delay(15_000) // önce akışın dolmasını bekle
        while (lifecycleScope.isActive) {
            val last = Prefs.scan.value?.at ?: 0
            val interval = Prefs.settings.value.scanIntervalMin * 60_000L
            val wait = last + interval - System.currentTimeMillis()
            if (wait > 0) { delay(wait.coerceAtMost(60_000)); continue }
            runScan()
            delay(30_000)
        }
    }

    private suspend fun runScan() {
        ScanRunner.run(this)
    }

    override fun onDestroy() {
        running = false
        MarketRepository.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_SCAN_NOW = "scan_now"
        @Volatile var running = false
            private set

        fun start(ctx: Context, scanNow: Boolean = false): Boolean = try {
            val i = Intent(ctx, MarketService::class.java)
            if (scanNow) i.action = ACTION_SCAN_NOW
            ContextCompat.startForegroundService(ctx, i)
            true
        } catch (e: Exception) {
            // Android 12+ arka plandan başlatma kısıtı vb.
            false
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MarketService::class.java))
        }
    }
}
