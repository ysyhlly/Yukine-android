package app.yukine

import app.yukine.model.TrackPlayRecord

internal const val SMART_COLLECTION_WEEK_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

internal const val SMART_RECENT_ADDED_KEY: String = "virtual:recent-added"
internal const val SMART_WEEK_FAVORITES_KEY: String = "virtual:week-favorites"
internal const val SMART_LONG_UNPLAYED_KEY: String = "virtual:long-unplayed"

/**
 * Read-only projection of the in-memory play records into a "this week's favorites" ordering.
 * Membership uses the 7-day window; ranking reuses the records' play counts so no extra query is
 * needed. Shared by the library overview shelf (count) and the virtual-collection reducer (list).
 */
internal fun weekFavoriteRecords(
    records: List<TrackPlayRecord>,
    nowMs: Long
): List<TrackPlayRecord> = records
    .filter { it.playedAt >= nowMs - SMART_COLLECTION_WEEK_WINDOW_MS }
    .sortedByDescending { it.playCount }
