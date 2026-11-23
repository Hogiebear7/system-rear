package com.hogie.systemrear.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Game state + settings, shared by the front app and the back panel (same process). */
object Store {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(GameState())
    private val _settings = MutableStateFlow(Settings())
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)

    val state: StateFlow<GameState> = _state
    val settings: StateFlow<Settings> = _settings
    val events: SharedFlow<GameEvent> = _events

    fun init(context: Context) {
        prefs = context.getSharedPreferences("system", Context.MODE_PRIVATE)
        prefs.getString("settings", null)?.let { runCatching { _settings.value = settingsFrom(JSONObject(it)) } }
        if (prefs.getString("settings", null) == null) saveSettings()
        prefs.getString("state", null)?.let { runCatching { _state.value = stateFrom(JSONObject(it)) } }
        tick()
    }

    // ── Settings ────────────────────────────────────────────────────────────
    fun updateSettings(f: (Settings) -> Settings) {
        synchronized(this) {
            _settings.value = f(_settings.value)
            saveSettings()
        }
    }

    private fun saveSettings() = prefs.edit().putString("settings", settingsJson(_settings.value).toString()).apply()

    // ── Game ────────────────────────────────────────────────────────────────
    /** Day rollover check; call now and then. */
    fun tick() = update { it }

    fun update(f: (GameState) -> GameState) {
        synchronized(this) {
            val before = rollover(_state.value)
            val after = applyRules(before, f(before))
            _state.value = after
            prefs.edit().putString("state", stateJson(after).toString()).apply()
        }
    }

    fun addProgress(questId: String, delta: Int) = update { s ->
        s.copy(quests = s.quests.map {
            if (it.id == questId) it.copy(progress = (it.progress + delta).coerceIn(0, it.target * 5)) else it
        })
    }

    fun spendPoint(stat: String) = update { s ->
        if (s.statPoints <= 0) s
        else s.copy(statPoints = s.statPoints - 1, stats = s.stats + (stat to (s.stats[stat] ?: 10) + 1))
    }

    fun classDone() = update { s ->
        val today = today()
        if (s.classDoneDay == today) s
        else {
            val xp = _settings.value.classXp
            _events.tryEmit(GameEvent.QuestDone("Class workout", xp))
            s.copy(
                classDoneDay = today, xp = s.xp + xp,
                stats = s.stats + ("STR" to (s.stats["STR"] ?: 10) + 1) + ("END" to (s.stats["END"] ?: 10) + 1),
                log = addLog(s.log, "Class workout complete. +$xp XP, STR +1, END +1"),
            )
        }
    }

    /** Raw value from TYPE_STEP_COUNTER (counts since boot). */
    fun onStepCounter(raw: Long) = update { s0 ->
        var s = s0
        val today = today()
        if (s.stepDay != today) s = s.copy(stepDay = today, stepAccum = 0, stepSegStart = raw, stepLastRaw = raw)
        if (raw < s.stepLastRaw) s = s.copy(stepAccum = s.stepAccum + (s.stepLastRaw - s.stepSegStart), stepSegStart = 0)
        s = s.copy(stepLastRaw = raw)
        val steps = (s.stepAccum + raw - s.stepSegStart).coerceAtLeast(0).toInt()
        s.copy(quests = s.quests.map { if (it.auto == "steps") it.copy(progress = steps) else it })
    }

    fun setQuests(quests: List<Quest>) = update { s -> s.copy(quests = quests) }

    fun setAllies(allies: List<Ally>, active: String? = null) = update { s ->
        s.copy(allies = allies, activeAllyId = active ?: s.activeAllyId)
    }

    fun setActiveAlly(id: String) = update { it.copy(activeAllyId = id) }

    fun resetProgress() = update { s ->
        s.copy(xp = 0, statPoints = 0, stats = DEFAULT_STATS, streak = 0, bestStreak = 0, log = emptyList())
    }

    fun today(): String = LocalDate.now().toString()

    private fun rollover(s: GameState): GameState {
        val today = today()
        if (s.day == today) return s
        if (s.day.isEmpty()) return s.copy(day = today)
        val yesterday = LocalDate.now().minusDays(1).toString()
        val keptStreak = s.day == yesterday && s.dailyCleared
        var log = s.log
        if (!s.dailyCleared) {
            val text = "Daily quest failed. Penalty: streak reset to 0."
            log = addLog(log, text)
            _events.tryEmit(GameEvent.Penalty(text))
        }
        return s.copy(
            day = today,
            dailyCleared = false,
            awarded = emptySet(),
            streak = if (keptStreak) s.streak else 0,
            quests = s.quests.map { it.copy(progress = 0) },
            log = log,
        )
    }

    private fun applyRules(before: GameState, after0: GameState): GameState {
        var after = after0
        val settings = _settings.value
        // Quests completed for the first time today.
        for (q in after.quests) {
            if (q.done && q.id !in after.awarded) {
                after = after.copy(
                    xp = after.xp + q.xp,
                    awarded = after.awarded + q.id,
                    log = addLog(after.log, "Quest complete: ${q.name}. +${q.xp} XP"),
                )
                _events.tryEmit(GameEvent.QuestDone(q.name, q.xp))
            }
        }
        if (!after.dailyCleared && after.quests.isNotEmpty() && after.quests.all { it.done }) {
            val streak = after.streak + 1
            after = after.copy(
                dailyCleared = true,
                xp = after.xp + settings.dailyBonus,
                streak = streak,
                bestStreak = maxOf(after.bestStreak, streak),
                statPoints = after.statPoints + 1,
                log = addLog(after.log, "Daily quest cleared! +${settings.dailyBonus} XP, +1 stat point. Streak $streak"),
            )
            _events.tryEmit(GameEvent.DailyClear(streak))
        }
        val oldLevel = before.level
        val newLevel = after.level
        if (newLevel > oldLevel) {
            after = after.copy(
                statPoints = after.statPoints + 3 * (newLevel - oldLevel),
                log = addLog(after.log, "LEVEL UP! You are now level $newLevel. +${3 * (newLevel - oldLevel)} stat points"),
            )
            _events.tryEmit(GameEvent.LevelUp(newLevel))
            val oldRank = Leveling.rank(oldLevel)
            val newRank = Leveling.rank(newLevel)
            if (oldRank != newRank) {
                after = after.copy(log = addLog(after.log, "Rank up: $oldRank → $newRank"))
                _events.tryEmit(GameEvent.RankUp(newRank))
            }
            after.allies.filter { it.unlockLevel in (oldLevel + 1)..newLevel }.forEach {
                after = after.copy(log = addLog(after.log, "New ally unlocked: ${it.name}"))
                _events.tryEmit(GameEvent.AllyUnlocked(it.name))
            }
        }
        return after
    }

    private fun addLog(log: List<String>, text: String): List<String> {
        val t = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return (listOf("$t  $text") + log).take(40)
    }

    // ── JSON ────────────────────────────────────────────────────────────────
    private fun stateJson(s: GameState) = JSONObject().apply {
        put("xp", s.xp); put("statPoints", s.statPoints)
        put("stats", JSONObject(s.stats as Map<*, *>))
        put("streak", s.streak); put("bestStreak", s.bestStreak)
        put("day", s.day); put("dailyCleared", s.dailyCleared)
        put("awarded", JSONArray(s.awarded.toList()))
        put("classDoneDay", s.classDoneDay)
        put("quests", JSONArray(s.quests.map {
            JSONObject().put("id", it.id).put("name", it.name).put("target", it.target).put("step", it.step)
                .put("unit", it.unit).put("xp", it.xp).put("auto", it.auto).put("progress", it.progress)
        }))
        put("log", JSONArray(s.log))
        put("allies", JSONArray(s.allies.map {
            JSONObject().put("id", it.id).put("name", it.name).put("file", it.file).put("unlockLevel", it.unlockLevel)
        }))
        put("activeAllyId", s.activeAllyId)
        put("stepDay", s.stepDay); put("stepAccum", s.stepAccum)
        put("stepSegStart", s.stepSegStart); put("stepLastRaw", s.stepLastRaw)
    }

    private fun stateFrom(o: JSONObject): GameState {
        val statsObj = o.optJSONObject("stats")
        return GameState(
            xp = o.optLong("xp"),
            statPoints = o.optInt("statPoints"),
            stats = STAT_NAMES.associateWith { statsObj?.optInt(it, 10) ?: 10 },
            streak = o.optInt("streak"),
            bestStreak = o.optInt("bestStreak"),
            day = o.optString("day"),
            dailyCleared = o.optBoolean("dailyCleared"),
            awarded = o.optJSONArray("awarded").strings().toSet(),
            classDoneDay = o.optString("classDoneDay"),
            quests = o.optJSONArray("quests")?.let { a ->
                (0 until a.length()).map { i ->
                    val q = a.getJSONObject(i)
                    Quest(
                        q.getString("id"), q.getString("name"), q.optInt("target", 1), q.optInt("step", 1),
                        q.optString("unit"), q.optInt("xp", 50), q.optString("auto"), q.optInt("progress"),
                    )
                }
            } ?: DEFAULT_QUESTS,
            log = o.optJSONArray("log").strings(),
            allies = o.optJSONArray("allies")?.let { a ->
                (0 until a.length()).map { i ->
                    val c = a.getJSONObject(i)
                    Ally(c.getString("id"), c.optString("name"), c.getString("file"), c.optInt("unlockLevel", 1))
                }
            } ?: emptyList(),
            activeAllyId = o.optString("activeAllyId"),
            stepDay = o.optString("stepDay"),
            stepAccum = o.optLong("stepAccum"),
            stepSegStart = o.optLong("stepSegStart"),
            stepLastRaw = o.optLong("stepLastRaw"),
        )
    }

    private fun settingsJson(s: Settings) = JSONObject().apply {
        put("hunterName", s.hunterName); put("title", s.title); put("accent", s.accent)
        put("cameraInset", s.cameraInset.toDouble()); put("showOnWake", s.showOnWake)
        put("keepAwake", s.keepAwake); put("lockPrivacy", s.lockPrivacy); put("hideTextLocked", s.hideTextLocked)
        put("quickReplies", JSONArray(s.quickReplies)); put("displayId", s.displayId)
        put("token", s.token); put("coachBase", s.coachBase); put("coachEmail", s.coachEmail)
        put("coachToken", s.coachToken); put("classXp", s.classXp); put("dailyBonus", s.dailyBonus)
    }

    private fun settingsFrom(o: JSONObject): Settings {
        val d = Settings()
        return Settings(
            hunterName = o.optString("hunterName", d.hunterName),
            title = o.optString("title", d.title),
            accent = o.optInt("accent", d.accent),
            cameraInset = o.optDouble("cameraInset", d.cameraInset.toDouble()).toFloat(),
            showOnWake = o.optBoolean("showOnWake", d.showOnWake),
            keepAwake = o.optBoolean("keepAwake", d.keepAwake),
            lockPrivacy = o.optBoolean("lockPrivacy", d.lockPrivacy),
            hideTextLocked = o.optBoolean("hideTextLocked", d.hideTextLocked),
            quickReplies = o.optJSONArray("quickReplies")?.strings() ?: d.quickReplies,
            displayId = o.optInt("displayId", d.displayId),
            token = o.optString("token", d.token).ifEmpty { d.token },
            coachBase = o.optString("coachBase", d.coachBase),
            coachEmail = o.optString("coachEmail"),
            coachToken = o.optString("coachToken"),
            classXp = o.optInt("classXp", d.classXp),
            dailyBonus = o.optInt("dailyBonus", d.dailyBonus),
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
