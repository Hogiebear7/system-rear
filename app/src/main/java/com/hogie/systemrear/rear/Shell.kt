package com.hogie.systemrear.rear

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/**
 * Shell (ADB-level) commands through Shizuku. Arguments go straight to the process, no shell
 * string, so nothing can be injected. Approach and timeout handling adapted from the MIT-licensed
 * mi-back-screen project (Copyright (c) 2026 Andrew Worgan).
 */
object Shell {
    fun available(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Runs [cmd]; returns "ok" or a short error. Blocking: call off the main thread. */
    fun run(vararg cmd: String): String {
        if (!ready()) return "Shizuku not ready"
        return try {
            val p = newProcessMethod.invoke(null, arrayOf(*cmd), null, null) as Process
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            val t1 = thread(isDaemon = true) { runCatching { p.inputStream.copyTo(out) } }
            val t2 = thread(isDaemon = true) { runCatching { p.errorStream.copyTo(err) } }
            var code = -1
            val waiter = thread(isDaemon = true) { code = runCatching { p.waitFor() }.getOrDefault(-1) }
            waiter.join(6000)
            if (waiter.isAlive) {
                runCatching { p.destroy() }
                return "timed out"
            }
            t1.join(500); t2.join(500)
            val text = (out.toString() + "\n" + err.toString()).trim()
            val error = text.lines().firstOrNull { it.startsWith("Error") || it.startsWith("Exception") }
            when {
                code != 0 -> "failed ($code): ${text.lines().lastOrNull().orEmpty()}"
                error != null -> "failed: $error"
                else -> "ok"
            }
        } catch (e: Throwable) {
            "error: ${e.cause?.message ?: e.message}"
        }
    }

    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }
}
