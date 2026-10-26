package com.piceroo.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DeckKind(val sectionTitle: String, val blurb: String) {
    BURST("Bursts", "Pick the best shot, trash the rest"),
    SCREENSHOTS("Screenshots", "Most of these did their job already"),
    DOWNLOADS("Chats & downloads", "WhatsApp, Telegram, Downloads…"),
    BIG_VIDEOS("Big videos", "Biggest space hogs first"),
    DAY("By day", "Swipe through one day at a time"),
}

/**
 * A deck is one "round" of the game. Swipe decks use [items]; burst decks use [bursts],
 * where each inner list is a set of near-identical shots taken seconds apart.
 */
data class Deck(
    val key: String,
    val kind: DeckKind,
    val title: String,
    val items: List<MediaItem>,
    val bursts: List<List<MediaItem>> = emptyList(),
) {
    val allItems: List<MediaItem> get() = if (kind == DeckKind.BURST) bursts.flatten() else items
    val count: Int get() = allItems.size
    val totalBytes: Long get() = allItems.sumOf { it.size }
}

object Clusterer {
    private const val BURST_GAP_MS = 10_000L // shots within 10 s of each other count as one burst
    private const val BIG_VIDEO_BYTES = 100L * 1024 * 1024
    private const val SWIPE_DECK_MAX = 40
    private const val BURSTS_PER_DECK = 8

    private val messagingFolders =
        listOf("whatsapp", "telegram", "messenger", "signal", "download", "snapchat")
    private val zone = ZoneId.systemDefault()
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")

    /** Sorts every item into exactly one kind of deck. [reviewed] items (already kept) are skipped. */
    fun buildDecks(all: List<MediaItem>, reviewed: Set<Long>): List<Deck> {
        val pool = all.filter { it.id !in reviewed }.sortedByDescending { it.dateTaken }

        val screenshots = pool.filter { it.isScreenshot() }
        val downloads = pool.filter { !it.isScreenshot() && it.isFromMessaging() }
        val rest = pool - screenshots.toSet() - downloads.toSet()
        val bigVideos = rest.filter { it.isVideo && it.size >= BIG_VIDEO_BYTES }
        val camera = rest - bigVideos.toSet()

        val bursts = findBursts(camera)
        val inBursts = bursts.flatten().map { it.id }.toSet()
        val singles = camera.filter { it.id !in inBursts }

        val decks = mutableListOf<Deck>()

        // Bursts: group a handful of bursts per deck, newest first.
        bursts.chunked(BURSTS_PER_DECK).forEachIndexed { i, chunk ->
            val newest = chunk.first().first().localDate()
            decks += Deck("burst-$i-${chunk.first().first().id}", DeckKind.BURST,
                "${chunk.size} bursts · ${newest.format(monthFmt)}", emptyList(), chunk)
        }

        decks += monthlyDecks(screenshots, DeckKind.SCREENSHOTS, "shots")
        decks += monthlyDecks(downloads, DeckKind.DOWNLOADS, "dl")

        bigVideos.sortedByDescending { it.size }.chunked(SWIPE_DECK_MAX).forEachIndexed { i, chunk ->
            decks += Deck("vid-$i-${chunk.first().id}", DeckKind.BIG_VIDEOS,
                "${chunk.size} big videos", chunk)
        }

        singles.groupBy { it.localDate() }.forEach { (day, items) ->
            items.chunked(SWIPE_DECK_MAX).forEachIndexed { i, chunk ->
                val part = if (items.size > SWIPE_DECK_MAX) " (part ${i + 1})" else ""
                decks += Deck("day-$day-$i", DeckKind.DAY, day.format(dayFmt) + part, chunk)
            }
        }
        return decks
    }

    /** Walks photos in time order; consecutive shots closer than [BURST_GAP_MS] form one burst. */
    private fun findBursts(items: List<MediaItem>): List<List<MediaItem>> {
        val photos = items.filter { !it.isVideo }.sortedByDescending { it.dateTaken }
        val bursts = mutableListOf<List<MediaItem>>()
        var current = mutableListOf<MediaItem>()
        for (p in photos) {
            val prev = current.lastOrNull()
            if (prev != null && prev.dateTaken - p.dateTaken <= BURST_GAP_MS) {
                current += p
            } else {
                if (current.size >= 2) bursts += current
                current = mutableListOf(p)
            }
        }
        if (current.size >= 2) bursts += current
        return bursts
    }

    private fun monthlyDecks(items: List<MediaItem>, kind: DeckKind, prefix: String): List<Deck> =
        items.groupBy { it.localDate().withDayOfMonth(1) }.flatMap { (month, monthItems) ->
            monthItems.chunked(SWIPE_DECK_MAX).mapIndexed { i, chunk ->
                val part = if (monthItems.size > SWIPE_DECK_MAX) " (part ${i + 1})" else ""
                Deck("$prefix-$month-$i", kind, month.format(monthFmt) + part, chunk)
            }
        }

    private fun MediaItem.localDate(): LocalDate =
        Instant.ofEpochMilli(dateTaken).atZone(zone).toLocalDate()

    private fun MediaItem.isScreenshot(): Boolean =
        relativePath.contains("screenshot", ignoreCase = true) ||
            bucket.contains("screenshot", ignoreCase = true)

    private fun MediaItem.isFromMessaging(): Boolean {
        val path = relativePath.lowercase()
        return messagingFolders.any { path.contains(it) }
    }
}
