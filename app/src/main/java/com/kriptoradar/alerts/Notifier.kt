package com.kriptoradar.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kriptoradar.R
import com.kriptoradar.ui.MainActivity

object Notifier {
    const val CH_LIVE = "live"
    const val CH_ALERT = "alerts"
    const val CH_MOVE = "moves"
    const val CH_SIGNAL = "signals"
    const val LIVE_ID = 1

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_LIVE, "Canlı izleme", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Arka planda sürekli çalışan piyasa takibi"; setShowBadge(false)
            },
            NotificationChannel(CH_ALERT, "Fiyat alarmları", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kurduğunuz fiyat alarmları"; enableVibration(true)
            },
            NotificationChannel(CH_MOVE, "Ani hareketler", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kısa sürede sert yükseliş/düşüş ve hacim patlamaları"
            },
            NotificationChannel(CH_SIGNAL, "İstatistiksel sinyaller", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Teknik skoru eşiği geçen coinler (tavsiye değildir)"
            },
        ))
    }

    fun openAppIntent(ctx: Context, symbol: String? = null): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (symbol != null) putExtra(MainActivity.EXTRA_SYMBOL, symbol)
        }
        return PendingIntent.getActivity(ctx, symbol?.hashCode() ?: 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, channel: String, id: Int, title: String, text: String, symbol: String? = null) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == CH_SIGNAL) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx, symbol))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    fun liveNotification(ctx: Context, title: String, text: String) =
        NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(ctx))
            .build()
}
