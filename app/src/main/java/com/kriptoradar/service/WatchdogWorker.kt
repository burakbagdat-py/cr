package com.kriptoradar.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kriptoradar.alerts.AlertEngine
import com.kriptoradar.data.MarketRepository
import com.kriptoradar.data.Prefs
import com.kriptoradar.update.UpdateManager
import java.util.concurrent.TimeUnit

/**
 * Yedek mekanizma: sistem servisi öldürürse 15 dakikada bir yeniden başlatmayı dener;
 * başlatamazsa en azından REST ile alarmları kontrol eder.
 */
class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Prefs.init(applicationContext)
        runCatching { UpdateManager.checkInBackground(applicationContext) }
        runCatching { com.kriptoradar.alerts.DailySummary.maybeSend(applicationContext) }
        val s = Prefs.settings.value
        if (!s.serviceEnabled) return Result.success()
        if (MarketService.running) return Result.success()
        if (MarketService.start(applicationContext)) return Result.success()
        // Servis başlatılamadı: tek seferlik kontrol
        if (MarketRepository.refreshSnapshot()) {
            AlertEngine(applicationContext).onTicks(MarketRepository.tickers.value.values.toList())
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("watchdog", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
