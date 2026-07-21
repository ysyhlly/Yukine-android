package app.yukine.dashboard

import android.net.Uri
import android.util.Log
import app.yukine.HomeDashboardStateFactory
import app.yukine.LibraryGrouping
import app.yukine.HomeDashboardRepository
import app.yukine.StreamingGatewayEndpointStore
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.HomeDashboardHeatmapDay
import app.yukine.ui.HomeDashboardHeatmapMonth
import app.yukine.ui.HomeDashboardRecentUiState
import app.yukine.ui.HomeDashboardStatUiState
import app.yukine.ui.HomeDashboardUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Repository that provides Dashboard data with backend-first, local-fallback strategy.
 */
class DashboardRepository(
    private val gateway: DashboardGateway,
    private val endpointStore: StreamingGatewayEndpointStore
) : HomeDashboardRepository {
    companion object {
        private const val TAG = "DashboardRepository"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val HEATMAP_WEEKS = 12
    }

    /**
     * Fetch homepage data.
     * @return HomeDashboardUiState ready for UI consumption
     */
    override suspend fun fetchHome(
        localTracks: List<Track>,
        localRecords: List<TrackPlayRecord>,
        localPlayback: PlaybackStateSnapshot?
    ): HomeDashboardUiState = withContext(Dispatchers.IO) {
        if (!endpointStore.configured()) {
            safeLogDebug("Gateway not configured, using local data")
            return@withContext buildLocalDashboard(localTracks, localRecords, localPlayback)
        }

        try {
            val response = gateway.home()
            withLocalListeningTrail(
                state = withLocalWeeklyRecapIfMissing(mapToUiState(response), localRecords),
                tracks = localTracks,
                records = localRecords,
                playback = localPlayback
            )
        } catch (e: Exception) {
            safeLogWarn("Failed to fetch from backend, falling back to local", e)
            buildLocalDashboard(localTracks, localRecords, localPlayback)
        }
    }

    /**
     * Fetch recent activity from backend, or return empty on failure.
     */
    suspend fun fetchRecentActivity(limit: Int = 20): List<HomeDashboardRecentUiState> = withContext(Dispatchers.IO) {
        if (!endpointStore.configured()) return@withContext emptyList()

        try {
            val response = gateway.recentActivity(limit)
            response.items.map { item ->
                HomeDashboardRecentUiState(
                    id = item.id.hashCode().toLong(),
                    title = item.title,
                    subtitle = item.subtitle,
                    detail = formatTimestamp(item.timestamp),
                    albumArtUri = item.artworkUri()
                )
            }
        } catch (e: Exception) {
            safeLogWarn("Failed to fetch recent activity", e)
            emptyList()
        }
    }

    /**
     * Fetch weekly recap from backend.
     */
    suspend fun fetchWeeklyRecap(): WeeklyRecapResponse? = withContext(Dispatchers.IO) {
        if (!endpointStore.configured()) return@withContext null

        try {
            gateway.weeklyRecap()
        } catch (e: Exception) {
            safeLogWarn("Failed to fetch weekly recap", e)
            null
        }
    }

    // ── Playback Control ────────────────────────────────────────────────────

    suspend fun toggle(): PlaybackActionResponse? = safePlaybackAction { gateway.toggle() }
    suspend fun play(): PlaybackActionResponse? = safePlaybackAction { gateway.play() }
    suspend fun pause(): PlaybackActionResponse? = safePlaybackAction { gateway.pause() }
    suspend fun next(): PlaybackActionResponse? = safePlaybackAction { gateway.next() }
    suspend fun previous(): PlaybackActionResponse? = safePlaybackAction { gateway.previous() }
    suspend fun seek(positionMs: Long): PlaybackActionResponse? = safePlaybackAction { gateway.seek(positionMs) }

    suspend fun getPlaybackState(): PlaybackStateResponse? = withContext(Dispatchers.IO) {
        if (!endpointStore.configured()) return@withContext null
        try {
            gateway.playbackState()
        } catch (e: Exception) {
            safeLogWarn("Failed to get playback state", e)
            null
        }
    }

    private suspend fun safePlaybackAction(action: suspend () -> PlaybackActionResponse): PlaybackActionResponse? {
        return withContext(Dispatchers.IO) {
            if (!endpointStore.configured()) return@withContext null
            try {
                action()
            } catch (e: Exception) {
                safeLogWarn("Playback action failed", e)
                null
            }
        }
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private fun safeLogDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // Android Log is not available in plain JVM unit tests.
        }
    }

    private fun safeLogWarn(message: String, throwable: Throwable) {
        try {
            Log.w(TAG, message, throwable)
        } catch (_: RuntimeException) {
            // Android Log is not available in plain JVM unit tests.
        }
    }

    private fun mapToUiState(response: DashboardHomeResponse): HomeDashboardUiState {
        val stats = response.libraryStats
        val nowPlaying = response.nowPlaying
        val progress = if (nowPlaying != null && nowPlaying.durationMs > 0) {
            (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
        // Normalize the backend heatmap (a flat date/count list) into the same
        // 12-week natural-week grid the desktop renders.
        val remoteHeatmap = assembleHeatmap(
            dayCounts = response.weeklyRecap.heatmap.associate { it.date to it.count },
            nowMs = System.currentTimeMillis()
        )

        return HomeDashboardUiState(
            title = "Yukine",
            subtitle = "${stats.tracks} 首歌曲 - ${stats.totalDurationText}",
            heroTitle = response.hero.title,
            heroSubtitle = response.hero.subtitle,
            continueTitle = nowPlaying?.title ?: "准备播放",
            continueSubtitle = nowPlaying?.let { "${it.artist} · ${it.album}" } ?: "添加音乐后开始聆听",
            continueDetail = if (nowPlaying?.isPlaying == true) "正在播放" else "继续播放",
            continueAlbumArtUri = nowPlaying?.artworkUri(),
            continueProgress = progress,
            continuePlaying = nowPlaying?.isPlaying ?: false,
            stats = listOf(
                HomeDashboardStatUiState("歌曲", stats.tracks.toString(), LibraryGrouping.SONGS),
                HomeDashboardStatUiState("专辑", stats.albums.toString(), LibraryGrouping.ALBUMS),
                HomeDashboardStatUiState("艺人", stats.artists.toString(), LibraryGrouping.ARTISTS),
                HomeDashboardStatUiState("文件夹", stats.folders.toString(), LibraryGrouping.FOLDERS)
            ),
            recent = response.recentActivity.map { item ->
                HomeDashboardRecentUiState(
                    id = item.id.hashCode().toLong(),
                    title = item.title,
                    subtitle = item.subtitle,
                    detail = formatTimestamp(item.timestamp),
                    albumArtUri = item.artworkUri()
                )
            },
            recentTabIndex = 0,
            weeklyTitle = "本周回声",
            weeklyPlays = response.weeklyRecap.playCount,
            weeklyDuration = response.weeklyRecap.durationText,
            weeklyBars = response.weeklyRecap.heatmap.take(7).map { day ->
                if (day.count == 0) 0.08f else (day.count.toFloat() / 10f).coerceIn(0.18f, 1f)
            }.let { bars -> if (bars.size < 7) bars + List(7 - bars.size) { 0.08f } else bars },
            heatmap = remoteHeatmap,
            heatmapMonths = buildMonthLabels(remoteHeatmap),
            activeWeeks = remoteHeatmap.chunked(7).count { week -> week.any { !it.future && it.count > 0 } },
            activeDays = remoteHeatmap.count { it.count > 0 },
            empty = stats.tracks == 0
        )
    }

    // ── Local Fallback ──────────────────────────────────────────────────────

    private fun buildLocalDashboard(
        tracks: List<Track>,
        records: List<TrackPlayRecord>,
        playback: PlaybackStateSnapshot?
    ): HomeDashboardUiState {
        val snapshot = playback ?: PlaybackStateSnapshot.empty()
        val current = snapshot.currentTrack
        val continueTrack = current ?: records.firstOrNull()?.track ?: tracks.firstOrNull()

        val durationMs = when {
            snapshot.durationMs > 0L -> snapshot.durationMs
            continueTrack != null -> continueTrack.durationMs
            else -> 0L
        }
        val progress = if (durationMs <= 0L) 0f else (snapshot.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

        val recentRows = records
            .filter { it.track != null }
            .sortedByDescending { it.playedAt }
            .take(8)
            .map { record ->
                HomeDashboardRecentUiState(
                    id = record.track.id,
                    title = record.track.title,
                    subtitle = record.track.subtitle(),
                    detail = "播放 ${max(1, record.playCount)} 次",
                    albumArtUri = record.track.albumArtUri
                )
            }

        val totalDurationMs = tracks.sumOf { it.durationMs }
        val durationText = formatDuration(totalDurationMs)
        val nowMs = System.currentTimeMillis()
        val weekRecords = records.filter { it.track != null && it.playedAt >= startOfWeek(nowMs) }
        val weekPlayCount = weekRecords.sumOf { max(1, it.playCount) }
        val weekDurationMs = weekRecords.sumOf { record ->
            record.track.durationMs * max(1, record.playCount)
        }
        val heatmapData = buildHeatmap(records, nowMs)

        return withLocalListeningTrail(
            state = HomeDashboardUiState(
                title = "Yukine",
                subtitle = "${tracks.size} 首歌曲 - $durationText",
                heroTitle = "No thoughts, just PCM.",
                heroSubtitle = continueTrack?.let { "接上 ${it.artist} 的「${it.title}」，或者从最近入库里挑一张封面开始。" }
                    ?: "接上最近播放，或者从最近入库里挑一张封面开始。",
                continueTitle = continueTrack?.title ?: "准备播放",
                continueSubtitle = continueTrack?.subtitle() ?: "添加音乐后开始聆听",
                continueDetail = if (snapshot.playing) "正在播放" else "继续播放",
                continueAlbumArtUri = continueTrack?.albumArtUri,
                continueProgress = progress,
                continuePlaying = snapshot.playing,
                stats = listOf(
                    HomeDashboardStatUiState("歌曲", tracks.size.toString(), LibraryGrouping.SONGS),
                    HomeDashboardStatUiState("专辑", LibraryGrouping.uniqueAlbumCount(tracks).toString(), LibraryGrouping.ALBUMS),
                    HomeDashboardStatUiState("艺人", LibraryGrouping.uniqueArtistCount(tracks).toString(), LibraryGrouping.ARTISTS),
                    HomeDashboardStatUiState("文件夹", LibraryGrouping.uniqueFolderCount(tracks).toString(), LibraryGrouping.FOLDERS)
                ),
                recent = recentRows,
                recentTabIndex = 0,
                weeklyTitle = "本周回声",
                weeklyPlays = weekPlayCount,
                weeklyDuration = formatDuration(weekDurationMs),
                weeklyBars = weekBars(weekRecords, nowMs),
                heatmap = heatmapData,
                heatmapMonths = buildMonthLabels(heatmapData),
                activeWeeks = heatmapData.chunked(7).count { week -> week.any { !it.future && it.count > 0 } },
                activeDays = heatmapData.count { it.count > 0 },
                empty = tracks.isEmpty()
            ),
            tracks = tracks,
            records = records,
            playback = playback
        )
    }

    private fun withLocalListeningTrail(
        state: HomeDashboardUiState,
        tracks: List<Track>,
        records: List<TrackPlayRecord>,
        playback: PlaybackStateSnapshot?
    ): HomeDashboardUiState {
        val localState = HomeDashboardStateFactory.create(
            languageMode = "zh",
            allTracks = tracks,
            visibleTracks = tracks,
            recentRecords = records,
            playbackState = playback
        )
        return state.copy(
            todayListeningDuration = localState.todayListeningDuration,
            todayListeningPoints = localState.todayListeningPoints
        )
    }

    private fun withLocalWeeklyRecapIfMissing(
        state: HomeDashboardUiState,
        records: List<TrackPlayRecord>
    ): HomeDashboardUiState {
        val nowMs = System.currentTimeMillis()
        val weekRecords = records.filter { it.track != null && it.playedAt >= startOfWeek(nowMs) }
        if (weekRecords.isEmpty()) {
            return state
        }
        val weekPlayCount = weekRecords.sumOf { max(1, it.playCount) }
        val weekDurationMs = weekRecords.sumOf { record ->
            record.track.durationMs * max(1, record.playCount)
        }
        if (weekPlayCount <= 0 || weekDurationMs <= 0L || hasRemoteWeeklyRecap(state)) {
            return state
        }

        val heatmapData = buildHeatmap(records, nowMs)
        return state.copy(
            weeklyPlays = weekPlayCount,
            weeklyDuration = formatDuration(weekDurationMs),
            weeklyBars = weekBars(weekRecords, nowMs),
            heatmap = heatmapData,
            heatmapMonths = buildMonthLabels(heatmapData),
            activeWeeks = heatmapData.chunked(7).count { week -> week.any { !it.future && it.count > 0 } },
            activeDays = heatmapData.count { it.count > 0 }
        )
    }

    private fun hasRemoteWeeklyRecap(state: HomeDashboardUiState): Boolean {
        return state.weeklyPlays > 0 &&
            state.weeklyDuration.isNotBlank() &&
            !state.weeklyDuration.startsWith("0 ")
    }

    private fun weekBars(records: List<TrackPlayRecord>, nowMs: Long): List<Float> {
        if (records.isEmpty()) {
            return List(7) { 0.08f }
        }
        val buckets = LongArray(7)
        val start = startOfWeek(nowMs)
        for (record in records) {
            if (record.playedAt < start) continue
            val index = (((record.playedAt - start) / DAY_MS).toInt()).coerceIn(0, 6)
            buckets[index] += max(1, record.playCount).toLong()
        }
        val peak = buckets.maxOrNull()?.takeIf { it > 0L } ?: 1L
        return buckets.map { count ->
            if (count == 0L) 0.08f else (count.toFloat() / peak.toFloat()).coerceIn(0.18f, 1f)
        }
    }

    /** Midnight of the most recent Monday at or before [timeMs]. */
    private fun startOfWeek(timeMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val mondayOffset = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        calendar.add(Calendar.DAY_OF_YEAR, mondayOffset)
        return calendar.timeInMillis
    }

    private fun buildHeatmap(records: List<TrackPlayRecord>, nowMs: Long): List<HomeDashboardHeatmapDay> {
        val firstWeekStart = startOfWeek(nowMs) - (HEATMAP_WEEKS - 1) * 7L * DAY_MS
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        val dayCounts = mutableMapOf<String, Int>()
        for (record in records) {
            if (record.playedAt >= firstWeekStart) {
                calendar.timeInMillis = record.playedAt
                val dateKey = dateFormat.format(calendar.time)
                dayCounts[dateKey] = (dayCounts[dateKey] ?: 0) + max(1, record.playCount)
            }
        }
        return assembleHeatmap(dayCounts, nowMs)
    }

    /**
     * Build a HEATMAP_WEEKS x 7 natural-week grid (Mon..Sun per column) ending
     * with the current week, filling counts from [dayCounts] keyed yyyy-MM-dd.
     * Days after today are flagged as future. Matches desktop layout.
     */
    private fun assembleHeatmap(dayCounts: Map<String, Int>, nowMs: Long): List<HomeDashboardHeatmapDay> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()

        val currentWeekStart = startOfWeek(nowMs)
        val firstWeekStart = currentWeekStart - (HEATMAP_WEEKS - 1) * 7L * DAY_MS
        val daysToShow = HEATMAP_WEEKS * 7

        calendar.timeInMillis = nowMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStartMs = calendar.timeInMillis

        val result = mutableListOf<HomeDashboardHeatmapDay>()
        calendar.timeInMillis = firstWeekStart
        for (i in 0 until daysToShow) {
            val dateKey = dateFormat.format(calendar.time)
            result.add(
                HomeDashboardHeatmapDay(
                    date = dateKey,
                    count = dayCounts[dateKey] ?: 0,
                    dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                    future = calendar.timeInMillis > todayStartMs
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /** Month labels spanning week columns (column = its Monday's month). */
    private fun buildMonthLabels(heatmap: List<HomeDashboardHeatmapDay>): List<HomeDashboardHeatmapMonth> {
        if (heatmap.isEmpty()) return emptyList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        val labels = mutableListOf<HomeDashboardHeatmapMonth>()
        var lastMonth = -1
        var lastYear = -1
        heatmap.chunked(7).forEachIndexed { weekIndex, week ->
            val firstDay = week.firstOrNull() ?: return@forEachIndexed
            val parsed = runCatching { dateFormat.parse(firstDay.date) }.getOrNull() ?: return@forEachIndexed
            calendar.time = parsed
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            if (labels.isEmpty() || month != lastMonth || year != lastYear) {
                labels.add(HomeDashboardHeatmapMonth("${month + 1}月", weekIndex, 1))
                lastMonth = month
                lastYear = year
            } else {
                val last = labels.removeAt(labels.size - 1)
                labels.add(last.copy(span = last.span + 1))
            }
        }
        return labels
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = max(0L, durationMs / 60000L)
        if (minutes < 60L) return "$minutes 分钟"
        val hours = minutes / 60L
        val rest = minutes % 60L
        return if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分钟"
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86400_000 -> "${diff / 3600_000} 小时前"
            diff < 604800_000 -> "${diff / 86400_000} 天前"
            else -> "${diff / 604800_000} 周前"
        }
    }
}
