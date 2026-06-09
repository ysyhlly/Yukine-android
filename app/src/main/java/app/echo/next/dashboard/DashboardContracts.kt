package app.echo.next.dashboard

import android.net.Uri

/**
 * Dashboard API contracts for ECHO Next.
 * Base URL: http://<gateway-host>:<port>/api/v1
 */

// ── GET /dashboard/home Response ────────────────────────────────────────────

data class DashboardHomeResponse(
    val hero: DashboardHero,
    val libraryStats: DashboardLibraryStats,
    val nowPlaying: DashboardNowPlaying?,
    val recentActivity: List<DashboardRecentItem>,
    val weeklyRecap: DashboardWeeklyRecap
)

data class DashboardHero(
    val title: String,
    val subtitle: String,
    val primaryAction: String
)

data class DashboardLibraryStats(
    val tracks: Int,
    val albums: Int,
    val artists: Int,
    val folders: Int,
    val totalDurationText: String
)

data class DashboardNowPlaying(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long
) {
    fun artworkUri(): Uri? = artworkUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
}

data class DashboardRecentItem(
    val id: String,
    val type: String, // "played" | "added"
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val timestamp: Long
) {
    fun artworkUri(): Uri? = artworkUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
}

data class DashboardWeeklyRecap(
    val playCount: Int,
    val durationText: String,
    val heatmap: List<DashboardHeatmapDay>
)

data class DashboardHeatmapDay(
    val date: String, // "2026-06-01"
    val count: Int
)

// ── Playback State & Control ────────────────────────────────────────────────

data class PlaybackStateResponse(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val trackId: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?
)

data class PlaybackSeekRequest(
    val positionMs: Long
)

data class PlaybackActionResponse(
    val ok: Boolean,
    val state: PlaybackStateResponse?
)

// ── GET /activity/recent Response ───────────────────────────────────────────

data class RecentActivityResponse(
    val items: List<DashboardRecentItem>
)

// ── GET /recap/weekly Response ──────────────────────────────────────────────

data class WeeklyRecapResponse(
    val playCount: Int,
    val durationMs: Long,
    val durationText: String,
    val activeDays: Int,
    val heatmap: List<DashboardHeatmapDay>
)
