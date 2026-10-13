package com.kriptoradar.alerts

import android.content.Context
import com.kriptoradar.analysis.SignalEngine
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.Prefs
import com.kriptoradar.data.ScanResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Servis ve arayüz aynı taramayı kullanır; böylece hiçbir yeni aday bildirimsiz kalmaz. */
object ScanRunner {
    private val mutex = Mutex()

    suspend fun run(ctx: Context): ScanResult? = mutex.withLock {
        if (MarketRepository.tickers.value.isEmpty() && !MarketRepository.refreshSnapshot()) return@withLock null
        val prev = Prefs.scan.value
        val res = runCatching {
            SignalEngine.scan(MarketRepository.tickers.value, Prefs.settings.value.scanUniverse, prev = prev)
        }.getOrNull() ?: return@withLock null
        if (res.signals.isEmpty()) return@withLock null
        Prefs.saveScan(res)
        AlertEngine(ctx.applicationContext).onScan(prev, res)
        res
    }
}
