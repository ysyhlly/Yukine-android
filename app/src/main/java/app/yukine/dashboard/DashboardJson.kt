package app.yukine.dashboard

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serialization/deserialization for Dashboard API.
 */
internal object DashboardJson {

    // ── Request Builders ────────────────────────────────────────────────────

    fun seekRequest(positionMs: Long): JSONObject {
        return JSONObject().put("positionMs", positionMs)
    }

    // ── Response Parsers ────────────────────────────────────────────────────

    fun parseHomeResponse(json: String): DashboardHomeResponse {
        val root = JSONObject(json)
        return DashboardHomeResponse(
            hero = parseHero(root.getJSONObject("hero")),
            libraryStats = parseLibraryStats(root.getJSONObject("libraryStats")),
            nowPlaying = root.optJSONObject("nowPlaying")?.let { parseNowPlaying(it) },
            recentActivity = parseRecentList(root.optJSONArray("recentActivity")),
            weeklyRecap = parseWeeklyRecap(root.getJSONObject("weeklyRecap"))
        )
    }

    fun parsePlaybackState(json: String): PlaybackStateResponse {
        val root = JSONObject(json)
        return parsePlaybackStateObject(root)
    }

    fun parsePlaybackAction(json: String): PlaybackActionResponse {
        val root = JSONObject(json)
        return PlaybackActionResponse(
            ok = root.optBoolean("ok", false),
            state = root.optJSONObject("state")?.let { parsePlaybackStateObject(it) }
        )
    }

    fun parseRecentActivity(json: String): RecentActivityResponse {
        val root = JSONObject(json)
        return RecentActivityResponse(
            items = parseRecentList(root.optJSONArray("items"))
        )
    }

    fun parseWeeklyRecapResponse(json: String): WeeklyRecapResponse {
        val root = JSONObject(json)
        return WeeklyRecapResponse(
            playCount = root.optInt("playCount", 0),
            durationMs = root.optLong("durationMs", 0L),
            durationText = root.optString("durationText", "0 分钟"),
            activeDays = root.optInt("activeDays", 0),
            heatmap = parseHeatmap(root.optJSONArray("heatmap"))
        )
    }

    // ── Internal Parsers ────────────────────────────────────────────────────

    private fun parseHero(obj: JSONObject): DashboardHero {
        return DashboardHero(
            title = obj.optString("title", ""),
            subtitle = obj.optString("subtitle", ""),
            primaryAction = obj.optString("primaryAction", "continue_playback")
        )
    }

    private fun parseLibraryStats(obj: JSONObject): DashboardLibraryStats {
        return DashboardLibraryStats(
            tracks = obj.optInt("tracks", 0),
            albums = obj.optInt("albums", 0),
            artists = obj.optInt("artists", 0),
            folders = obj.optInt("folders", 0),
            totalDurationText = obj.optString("totalDurationText", "0 小时")
        )
    }

    private fun parseNowPlaying(obj: JSONObject): DashboardNowPlaying {
        return DashboardNowPlaying(
            trackId = obj.optString("trackId", ""),
            title = obj.optString("title", ""),
            artist = obj.optString("artist", ""),
            album = obj.optString("album", ""),
            artworkUrl = obj.optString("artworkUrl", null),
            isPlaying = obj.optBoolean("isPlaying", false),
            positionMs = obj.optLong("positionMs", 0L),
            durationMs = obj.optLong("durationMs", 0L)
        )
    }

    private fun parsePlaybackStateObject(obj: JSONObject): PlaybackStateResponse {
        return PlaybackStateResponse(
            isPlaying = obj.optBoolean("isPlaying", false),
            positionMs = obj.optLong("positionMs", 0L),
            durationMs = obj.optLong("durationMs", 0L),
            trackId = obj.optString("trackId", null),
            title = obj.optString("title", null),
            artist = obj.optString("artist", null),
            album = obj.optString("album", null),
            artworkUrl = obj.optString("artworkUrl", null)
        )
    }

    private fun parseRecentList(array: JSONArray?): List<DashboardRecentItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            DashboardRecentItem(
                id = obj.optString("id", ""),
                type = obj.optString("type", "played"),
                title = obj.optString("title", ""),
                subtitle = obj.optString("subtitle", ""),
                artworkUrl = obj.optString("artworkUrl", null),
                timestamp = obj.optLong("timestamp", 0L)
            )
        }
    }

    private fun parseWeeklyRecap(obj: JSONObject): DashboardWeeklyRecap {
        return DashboardWeeklyRecap(
            playCount = obj.optInt("playCount", 0),
            durationText = obj.optString("durationText", "0 分钟"),
            heatmap = parseHeatmap(obj.optJSONArray("heatmap"))
        )
    }

    private fun parseHeatmap(array: JSONArray?): List<DashboardHeatmapDay> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            DashboardHeatmapDay(
                date = obj.optString("date", ""),
                count = obj.optInt("count", 0)
            )
        }
    }
}
