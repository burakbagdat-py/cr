package com.kriptoradar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kriptoradar.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        if (Prefs.settings.value.serviceEnabled) MarketService.start(context)
        WatchdogWorker.schedule(context)
    }
}
