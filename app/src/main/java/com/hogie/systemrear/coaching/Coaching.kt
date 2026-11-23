package com.hogie.systemrear.coaching

import android.content.Context
import com.hogie.systemrear.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

data class ClassWorkout(
    val title: String,
    val time: String,
    val lines: List<String>,
    val notes: String,
    val fetchedAt: Long,
)

/** Today's class workout from S&C Performance Coaching (the app's own mobile API, Bearer sign-in). */
object Coaching {
    val today = MutableStateFlow<ClassWorkout?>(null)
    val status = MutableStateFlow("")
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val cached = appContext.getSharedPreferences("coaching", Context.MODE_PRIVATE).getString("today", null)
        cached?.let { runCatching { today.value = fromJson(JSONObject(it)) } }
    }

    val signedIn get() = Store.settings.value.coachToken.isNotEmpty()

    suspend fun login(email: String, password: String): String = withContext(Dispatchers.IO) {
        val (code, body) = http("POST", "/api/mobile/auth/login", JSONObject().put("email", email).put("password", password), null)
        val o = runCatching { JSONObject(body) }.getOrNull()
        if (code == 200 && o?.optBoolean("success") == true) {
            Store.updateSettings { it.copy(coachEmail = email, coachToken = o.getString("token")) }
            "Signed in"
        } else {
            o?.optString("message")?.ifEmpty { null } ?: "Sign-in failed ($code)"
        }
    }

    fun logout() {
        Store.updateSettings { it.copy(coachToken = "") }
        today.value = null
        appContext.getSharedPreferences("coaching", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val token = Store.settings.value.coachToken
        if (token.isEmpty()) {
            status.value = "Sign in to your coaching app in the front app"
            return@withContext
        }
        status.value = "Loading…"
        try {
            // Rolling sign-in: /me hands back a fresh 7-day token.
            val (meCode, meBody) = http("GET", "/api/mobile/auth/me", null, token)
            if (meCode == 401) {
                Store.updateSettings { it.copy(coachToken = "") }
                status.value = "Signed out — sign in again in the front app"
                return@withContext
            }
            val fresh = runCatching { JSONObject(meBody).optString("token") }.getOrNull().orEmpty()
            val t = if (fresh.isNotEmpty()) fresh.also { f -> Store.updateSettings { it.copy(coachToken = f) } } else token

            val date = LocalDate.now().toString()
            val (code, body) = http("GET", "/api/mobile/staff/classes", null, t)
            val result = if (code == 200) fromStaffClasses(JSONObject(body).optJSONArray("data") ?: JSONArray(), date, t)
            else fromSchedule(date, t)
            today.value = result
            status.value = if (result == null) "No class today" else ""
            appContext.getSharedPreferences("coaching", Context.MODE_PRIVATE).edit()
                .putString("today", result?.let { toJson(it).toString() }).apply()
        } catch (e: Exception) {
            status.value = "Couldn't reach the coaching app: ${e.message}"
        }
    }

    private fun fromStaffClasses(classes: JSONArray, date: String, token: String): ClassWorkout? {
        val todays = (0 until classes.length()).map { classes.getJSONObject(it) }
            .filter { it.optString("date") == date }
            .sortedBy { it.optString("startTime") }
        if (todays.isEmpty()) return null
        val now = LocalTime.now()
        val pick = todays.firstOrNull {
            val start = runCatching { LocalTime.parse(it.optString("startTime").take(5)) }.getOrNull() ?: return@firstOrNull true
            !start.plusMinutes(it.optLong("durationMins", 60)).isBefore(now)
        } ?: todays.last()
        val id = pick.getString("id")
        val (code, body) = http("GET", "/api/mobile/staff/classes/$id/workout", null, token)
        var lines = emptyList<String>()
        var notes = ""
        if (code == 200) {
            val w = JSONObject(body).optJSONObject("data")?.optJSONObject("existingWorkout")
            if (w != null) {
                lines = workoutLines(w)
                notes = w.optString("notes").takeIf { it != "null" }.orEmpty()
            }
        }
        if (lines.isEmpty()) lines = listOf("No workout saved for this class yet")
        return ClassWorkout(pick.optString("title"), pick.optString("startTime").take(5), lines, notes, System.currentTimeMillis())
    }

    /** Members (non-staff): show today's booked class; the workout itself is staff-only in the API. */
    private fun fromSchedule(date: String, token: String): ClassWorkout? {
        val (code, body) = http("GET", "/api/mobile/schedule", null, token)
        if (code != 200) error("schedule $code")
        val bookings = JSONObject(body).optJSONObject("data")?.optJSONArray("upcomingBookings") ?: return null
        val b = (0 until bookings.length()).map { bookings.getJSONObject(it) }
            .firstOrNull { it.optString("date") == date } ?: return null
        return ClassWorkout(b.optString("title"), b.optString("startTime").take(5), listOf("Booked — workout shows after class"), "", System.currentTimeMillis())
    }

    private fun workoutLines(w: JSONObject): List<String> {
        val items = mutableListOf<Pair<Int, String>>()
        val ex = w.optJSONArray("exercises") ?: JSONArray()
        for (i in 0 until ex.length()) {
            val e = ex.getJSONObject(i)
            val sets = e.optInt("sets", 0)
            val reps = e.optInt("reps", 0)
            val weight = e.optString("weight").takeIf { it.isNotBlank() && it != "null" }
            val group = e.optString("supersetGroup").takeIf { it.isNotBlank() && it != "null" }
            val sb = StringBuilder()
            if (group != null) sb.append("[$group] ")
            sb.append(e.optString("name"))
            if (sets > 0 || reps > 0) sb.append(" — ").append(if (sets > 0) "$sets×" else "").append(if (reps > 0) "$reps" else "")
            if (weight != null) sb.append(" @ ").append(weight).append(if (weight.any { it.isLetter() }) "" else "kg")
            items += e.optInt("order", i) to sb.toString()
        }
        val runs = w.optJSONArray("runs") ?: JSONArray()
        for (i in 0 until runs.length()) {
            val r = runs.getJSONObject(i)
            val type = r.optString("cardioType").takeIf { it.isNotBlank() && it != "null" } ?: "run"
            val sb = StringBuilder(type.replaceFirstChar { it.uppercase() })
            val dist = r.optDouble("distance", 0.0)
            if (dist > 0) sb.append(" — ").append(if (dist % 1.0 == 0.0) dist.toInt() else dist).append(" km")
            val secs = r.optLong("durationSecs", 0)
            if (secs > 0) sb.append(" — ").append(secs / 60).append(" min")
            items += r.optInt("order", 1000 + i) to sb.toString()
        }
        return items.sortedBy { it.first }.map { it.second }
    }

    private fun http(method: String, path: String, json: JSONObject?, token: String?): Pair<Int, String> {
        val c = URL(Store.settings.value.coachBase.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (json != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(json.toString().toByteArray()) }
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        return code to body
    }

    private fun toJson(w: ClassWorkout) = JSONObject().put("title", w.title).put("time", w.time)
        .put("lines", JSONArray(w.lines)).put("notes", w.notes).put("fetchedAt", w.fetchedAt)

    private fun fromJson(o: JSONObject): ClassWorkout {
        val a = o.optJSONArray("lines") ?: JSONArray()
        return ClassWorkout(o.optString("title"), o.optString("time"), (0 until a.length()).map { a.getString(it) }, o.optString("notes"), o.optLong("fetchedAt"))
    }
}
