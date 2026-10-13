package com.kriptoradar

import android.app.Application
import com.kriptoradar.alerts.Notifier
import com.kriptoradar.data.Prefs
import com.kriptoradar.service.WatchdogWorker

class KriptoRadarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifier.createChannels(this)
        WatchdogWorker.schedule(this)
    }
}
