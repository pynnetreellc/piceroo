package com.piceroo.app.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Stats(
    val points: Long = 0,
    val bytesFreed: Long = 0,
    val itemsTrashed: Int = 0,
    val decksCleared: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
) {
    val level: Int get() = (points / POINTS_PER_LEVEL).toInt() + 1
    val levelProgress: Float get() = (points % POINTS_PER_LEVEL) / POINTS_PER_LEVEL.toFloat()
    val title: String get() = TITLES[(level - 1).coerceAtMost(TITLES.lastIndex)]

    companion object {
        const val POINTS_PER_LEVEL = 500L
        private val TITLES = listOf(
            // A young kangaroo is a joey, a big male is a boomer, and a group is a mob.
            "Joey", "Pouch Peeker", "Hopper", "Bounder", "Boomer",
            "Mob Leader", "Outback Ranger", "Big Red",
        )
    }
}

/** What one finished round earned; shown on the celebration screen. */
data class RoundResult(
    val trashedCount: Int,
    val keptCount: Int,
    val bytesFreed: Long,
    val pointsEarned: Long,
    val stats: Stats,
    val leveledUp: Boolean,
)

/**
 * Saves game progress on the phone. SharedPreferences is Android's simple key-value store
 * (think: a small dictionary that survives app restarts).
 */
class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("game", Context.MODE_PRIVATE)
    private val logFile = File(context.filesDir, "trash_log.csv")

    fun stats() = Stats(
        points = prefs.getLong("points", 0),
        bytesFreed = prefs.getLong("bytesFreed", 0),
        itemsTrashed = prefs.getInt("itemsTrashed", 0),
        decksCleared = prefs.getInt("decksCleared", 0),
        streak = currentStreak(),
        bestStreak = prefs.getInt("bestStreak", 0),
    )

    /** IDs of photos you chose to keep, so they don't come back in future decks. */
    fun reviewedIds(): Set<Long> =
        prefs.getStringSet("reviewed", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun completeRound(trashed: List<MediaItem>, kept: List<MediaItem>): RoundResult {
        val before = stats()
        val bytes = trashed.sumOf { it.size }
        val earned = trashed.size * 10L + kept.size + 25 + bytes / (1024 * 1024)

        val today = LocalDate.now()
        val last = prefs.getString("lastDay", null)?.let(LocalDate::parse)
        val streak = when (last) {
            today -> prefs.getInt("streak", 1)
            today.minusDays(1) -> prefs.getInt("streak", 0) + 1
            else -> 1
        }

        // getStringSet's result must not be modified in place, so copy it.
        val reviewed = prefs.getStringSet("reviewed", emptySet())!!.toMutableSet()
        kept.forEach { reviewed += it.id.toString() }

        prefs.edit()
            .putLong("points", before.points + earned)
            .putLong("bytesFreed", before.bytesFreed + bytes)
            .putInt("itemsTrashed", before.itemsTrashed + trashed.size)
            .putInt("decksCleared", before.decksCleared + 1)
            .putInt("streak", streak)
            .putInt("bestStreak", maxOf(before.bestStreak, streak))
            .putString("lastDay", today.toString())
            .putStringSet("reviewed", reviewed)
            .apply()

        if (trashed.isNotEmpty()) appendLog(trashed)

        val after = stats()
        return RoundResult(trashed.size, kept.size, bytes, earned, after, after.level > before.level)
    }

    /** A streak only counts if you played today or yesterday. */
    private fun currentStreak(): Int {
        val last = prefs.getString("lastDay", null)?.let(LocalDate::parse) ?: return 0
        val today = LocalDate.now()
        return if (last == today || last == today.minusDays(1)) prefs.getInt("streak", 0) else 0
    }

    /** Remembers what was trashed, so you can find the backed-up copies in Google Photos later. */
    private fun appendLog(items: List<MediaItem>) {
        val zone = ZoneId.systemDefault()
        logFile.appendText(items.joinToString("") {
            val day = Instant.ofEpochMilli(it.dateTaken).atZone(zone).toLocalDate()
            "$day,${it.name.replace(',', ' ')},${it.size}\n"
        })
    }

    /** The trash log grouped by date, as plain text you can share to yourself. */
    fun cloudCleanupList(): String {
        if (!logFile.exists()) return ""
        return logFile.readLines()
            .mapNotNull { line -> line.split(',').takeIf { it.size >= 2 } }
            .groupBy({ it[0] }, { it[1] })
            .toSortedMap(reverseOrder())
            .entries.joinToString("\n") { (day, names) -> "$day (${names.size}): ${names.joinToString(", ")}" }
    }
}
