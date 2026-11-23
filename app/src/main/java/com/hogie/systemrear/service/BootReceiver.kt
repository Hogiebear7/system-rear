package com.hogie.systemrear.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hogie.systemrear.data.Store

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Store.settings.value.showOnWake) {
            runCatching { KeeperService.start(context) }
        }
    }
}
