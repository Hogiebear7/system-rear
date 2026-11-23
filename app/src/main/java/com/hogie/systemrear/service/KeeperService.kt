package com.hogie.systemrear.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import com.hogie.systemrear.App
import com.hogie.systemrear.R
import com.hogie.systemrear.data.Store
import com.hogie.systemrear.rear.RearActivity
import com.hogie.systemrear.rear.RearLauncher
import com.hogie.systemrear.ui.MainActivity

/**
 * Runs in the background: puts the System panel back on the rear screen each time the rear
 * screen wakes (Xiaomi's own clock takes over whenever it dozes), and counts steps for quests.
 */
class KeeperService : Service() {
    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_STOP = "stop"
        @Volatile var running = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeeperService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeeperService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastState = Display.STATE_UNKNOWN
    private lateinit var dm: DisplayManager

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val target = Store.settings.value.displayId
            if (displayId != target) return
            val state = dm.getDisplay(displayId)?.state ?: return
            val wokeUp = state == Display.STATE_ON && lastState != Display.STATE_ON
            lastState = state
            if (wokeUp && Store.settings.value.showOnWake) {
                handler.postDelayed({
                    if (!RearActivity.visible) RearLauncher.launch(this@KeeperService)
                }, 350)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        dm = getSystemService(DisplayManager::class.java)
        lastState = dm.getDisplay(Store.settings.value.displayId)?.state ?: Display.STATE_UNKNOWN
        dm.registerDisplayListener(listener, handler)
        Steps.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        when (intent?.action) {
            ACTION_SHOW -> RearLauncher.launch(this)
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        dm.unregisterDisplayListener(listener)
        Steps.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val show = PendingIntent.getService(this, 1, Intent(this, KeeperService::class.java).setAction(ACTION_SHOW), flags)
        val stop = PendingIntent.getService(this, 2, Intent(this, KeeperService::class.java).setAction(ACTION_STOP), flags)
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), flags)
        return Notification.Builder(this, App.CH_KEEPER)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("System is watching the back screen")
            .setContentText("Tap “Show on back” if Xiaomi's clock is in front")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Show on back", show).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
