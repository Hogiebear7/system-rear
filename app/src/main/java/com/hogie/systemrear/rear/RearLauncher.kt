package com.hogie.systemrear.rear

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.hogie.systemrear.data.Store
import kotlin.concurrent.thread

object RearLauncher {
    const val EXTRA_TOKEN = "t"

    /**
     * Puts the System panel on the back screen. Shizuku's shell can start it at any time; without
     * Shizuku a direct start only works while our app is in front (or with "display over other apps").
     */
    fun launch(context: Context, onResult: ((String) -> Unit)? = null) {
        val s = Store.settings.value
        val app = context.applicationContext
        thread(isDaemon = true) {
            var result = "Shizuku not ready"
            if (Shell.ready()) {
                Shell.run("/system/bin/input", "-d", s.displayId.toString(), "keyevent", "KEYCODE_WAKEUP")
                result = Shell.run(
                    "/system/bin/am", "start", "--display", s.displayId.toString(),
                    "-n", "${app.packageName}/.rear.RearActivity", "--es", EXTRA_TOKEN, s.token,
                )
            }
            if (result != "ok") {
                result = try {
                    val opts = ActivityOptions.makeBasic().setLaunchDisplayId(s.displayId)
                    app.startActivity(
                        Intent(app, RearActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(EXTRA_TOKEN, s.token),
                        opts.toBundle(),
                    )
                    "ok (direct)"
                } catch (e: Exception) {
                    "Couldn't open on the back: ${e.message} (Shizuku: $result)"
                }
            }
            onResult?.invoke(result)
        }
    }
}
