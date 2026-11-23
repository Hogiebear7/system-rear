package com.hogie.systemrear.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class Msg(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val reply: Notification.Action?,
    val open: PendingIntent?,
)

object Notifs {
    val items = MutableStateFlow<List<Msg>>(emptyList())
    val incoming = MutableSharedFlow<Msg>(extraBufferCapacity = 8)
    val connected = MutableStateFlow(false)

    /** Sends [text] through the notification's own reply action (as a smartwatch does). */
    fun reply(context: Context, msg: Msg, text: String): Boolean {
        val action = msg.reply ?: return false
        val inputs = action.remoteInputs ?: return false
        return try {
            val intent = Intent()
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(context, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun dismiss(msg: Msg) {
        NotifService.instance?.cancelNotification(msg.key)
    }

    fun dismissAll() {
        val svc = NotifService.instance ?: return
        items.value.forEach { svc.cancelNotification(it.key) }
    }
}

class NotifService : NotificationListenerService() {
    companion object {
        @Volatile var instance: NotifService? = null

        private val KNOWN = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.google.android.apps.messaging" to "Messages",
            "com.android.mms" to "Messages",
            "com.miui.mms" to "Messages",
            "org.telegram.messenger" to "Telegram",
            "com.instagram.android" to "Instagram",
            "com.facebook.orca" to "Messenger",
            "com.snapchat.android" to "Snapchat",
            "com.android.incallui" to "Phone",
            "com.google.android.dialer" to "Phone",
            "com.google.android.gm" to "Gmail",
        )
    }

    override fun onListenerConnected() {
        instance = this
        Notifs.connected.value = true
        refresh()
        Media.attach(this)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        Notifs.connected.value = false
        Media.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refresh()
        toMsg(sbn)?.let { Notifs.incoming.tryEmit(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val list = try {
            activeNotifications?.mapNotNull { toMsg(it) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        Notifs.items.value = list.sortedByDescending { it.time }
    }

    private fun toMsg(sbn: StatusBarNotification): Msg? {
        if (sbn.packageName == packageName || sbn.isOngoing) return null
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val e = n.extras
        // Music players have their own page.
        if (n.category == Notification.CATEGORY_TRANSPORT || e.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        val title = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        return Msg(sbn.key, sbn.packageName, label(sbn.packageName), title, text, sbn.postTime, reply, n.contentIntent)
    }

    private fun label(pkg: String): String = KNOWN[pkg] ?: try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
