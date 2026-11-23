package com.hogie.systemrear.data

import java.util.UUID

data class Quest(
    val id: String,
    val name: String,
    val target: Int,
    val step: Int,
    val unit: String,
    val xp: Int,
    /** "steps" = filled in automatically from the phone's step counter. */
    val auto: String = "",
    val progress: Int = 0,
) {
    val done get() = progress >= target
}

/** A character image the user imported. Unlocks once the hunter reaches [unlockLevel]. */
data class Ally(val id: String, val name: String, val file: String, val unlockLevel: Int)

data class GameState(
    val xp: Long = 0,
    val statPoints: Int = 0,
    val stats: Map<String, Int> = DEFAULT_STATS,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val day: String = "",
    val dailyCleared: Boolean = false,
    val awarded: Set<String> = emptySet(),
    val classDoneDay: String = "",
    val quests: List<Quest> = DEFAULT_QUESTS,
    val log: List<String> = emptyList(),
    val allies: List<Ally> = emptyList(),
    val activeAllyId: String = "",
    val stepDay: String = "",
    val stepAccum: Long = 0,
    val stepSegStart: Long = 0,
    val stepLastRaw: Long = 0,
) {
    val level get() = Leveling.levelOf(xp).level
}

data class Settings(
    val hunterName: String = "Hunter",
    val title: String = "The Awakened",
    val accent: Int = 0,
    val cameraInset: Float = 0.30f,
    val showOnWake: Boolean = true,
    val keepAwake: Boolean = false,
    val lockPrivacy: Boolean = true,
    val quickReplies: List<String> = DEFAULT_REPLIES,
    val displayId: Int = 1,
    val token: String = UUID.randomUUID().toString(),
    val coachBase: String = "https://sandccoaching.com",
    val coachEmail: String = "",
    val coachToken: String = "",
    val classXp: Int = 150,
    val dailyBonus: Int = 100,
)

val STAT_NAMES = listOf("STR", "AGI", "VIT", "END", "SEN")
val DEFAULT_STATS = STAT_NAMES.associateWith { 10 }

val DEFAULT_QUESTS = listOf(
    Quest("pushups", "Push-ups", 100, 10, "reps", 50),
    Quest("situps", "Sit-ups", 100, 10, "reps", 50),
    Quest("squats", "Squats", 100, 10, "reps", 50),
    Quest("steps", "Steps", 10000, 500, "steps", 50, auto = "steps"),
)

val DEFAULT_REPLIES = listOf(
    "On my way",
    "At training, call you after",
    "Can't talk right now",
    "Sounds good 👍",
)

val ACCENTS = listOf(0xFF3DA9FF, 0xFF9B5CFF, 0xFFFF4D5E, 0xFFFFC547, 0xFF2EF2C4)
val ACCENT_NAMES = listOf("System blue", "Monarch purple", "Red gate", "Gold", "Mana teal")

data class LevelInfo(val level: Int, val into: Long, val need: Long)

object Leveling {
    /** XP to go from [level] to the next one. */
    fun need(level: Int): Long = 100L + level * 40L

    fun levelOf(xp: Long): LevelInfo {
        var level = 1
        var rest = xp
        while (rest >= need(level)) {
            rest -= need(level)
            level++
        }
        return LevelInfo(level, rest, need(level))
    }

    fun rank(level: Int) = when {
        level < 10 -> "E"
        level < 20 -> "D"
        level < 30 -> "C"
        level < 45 -> "B"
        level < 60 -> "A"
        else -> "S"
    }
}

sealed class GameEvent {
    data class LevelUp(val level: Int) : GameEvent()
    data class RankUp(val rank: String) : GameEvent()
    data class QuestDone(val name: String, val xp: Int) : GameEvent()
    data class DailyClear(val streak: Int) : GameEvent()
    data class AllyUnlocked(val name: String) : GameEvent()
    data class Penalty(val text: String) : GameEvent()
}
