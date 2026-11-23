package com.hogie.systemrear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.hogie.systemrear.coaching.Coaching
import com.hogie.systemrear.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Coaching.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_KEEPER, "System panel", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_RECORD, "Voice recording", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val CH_KEEPER = "keeper"
        const val CH_RECORD = "record"
    }
}
