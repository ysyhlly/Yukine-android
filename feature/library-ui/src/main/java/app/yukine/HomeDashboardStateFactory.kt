package app.yukine

import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.ui.HomeDashboardHeatmapDay
import app.yukine.ui.HomeDashboardHeatmapMonth
import app.yukine.ui.HomeDashboardListeningPoint
import app.yukine.ui.HomeDashboardRecentUiState
import app.yukine.ui.HomeDashboardStatUiState
import app.yukine.ui.HomeDashboardUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

object HomeDashboardStateFactory {
    private const val RECENT_LIMIT = 8
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val HEATMAP_WEEKS = 12

    /** 「今日回声」标语池 —— 每天轮换一条，与音乐相关。 */
    private val echoSlogans = listOf(
        "今天想听点什么？",
        "每一段旋律，都是时间的回声。",
        "按下播放，让世界安静三秒。",
        "音符落下的地方，就是归处。",
        "耳机里藏着另一个宇宙。",
        "把心跳调成 BPM 的形状。",
        "今天的你，适合哪一首歌？",
        "旋律是记忆最忠实的容器。",
        "让节拍替你说话。",
        "一首歌的时间，刚好够想通一件事。",
        "声波抵达的地方，情绪有了着落。",
        "用一首歌，给今天打个结。",
        "频率对了，世界就安静了。",
        "每个和弦都是一封未寄出的信。",
        "低音是心跳，高音是呼吸。",
        "把日子过成一张专辑。",
        "前奏响起，烦恼静音。",
        "音乐是耳朵的旅行。",
        "今天的空气里，有旋律的味道。",
        "一段副歌，足以撑起整个下午。",
        "让回声替你把今天记住。",
        "泛音散开的时候，时间变慢了。",
        "歌单是心情的化石。",
        "按下随机，把选择交给命运。",
        "每首歌都在等一个对的时刻。",
        "混响是空间的记忆。",
        "节奏是身体最诚实的语言。",
        "戴上耳机，世界是背景音。",
        "采样一段雨声，混进今晚的歌。",
        "渐强的人生，不需要跳过前奏。",
        "切歌是犹豫，单曲循环是笃定。",
        "声场打开的时候，房间变大了。",
        "用 EQ 调出属于自己的频率。",
        "间奏是留给呼吸的空白。",
        "音乐不停，今天就不算结束。"
    )

    /** 根据当天日期确定性地选取一条标语（同一天内不会变化）。 */
    @JvmStatic
    fun dailySlogan(nowMs: Long = System.currentTimeMillis()): String {
        val dayIndex = (nowMs / DAY_MS).toInt()
        return echoSlogans[dayIndex % echoSlogans.size]
    }

    @JvmStatic
    fun create(
        languageMode: String,
        allTracks: List<Track>,
        visibleTracks: List<Track>,
        recentRecords: List<TrackPlayRecord>,
        playbackState: PlaybackStateSnapshot?,
        nowMs: Long = System.currentTimeMillis()
    ): HomeDashboardUiState {
        val snapshot = playbackState ?: PlaybackStateSnapshot.empty()
        val current = snapshot.currentTrack
        val activeTracks = if (visibleTracks.isNotEmpty()) visibleTracks else allTracks
        val recentRows = recentRecords
            .asSequence()
            .filter { it.track != null }
            .sortedByDescending { it.playedAt }
            .take(RECENT_LIMIT)
            .map { record ->
                HomeDashboardRecentUiState(
                    id = record.track.id,
                    title = record.track.title,
                    subtitle = record.track.subtitle(),
                    detail = lastPlayedLabel(record),
                    albumArtUri = record.track.albumArtUri
                )
            }
            .toList()

        val weekStartMs = startOfWeek(nowMs)
        val weekRecords = recentRecords.filter { it.track != null && it.playedAt >= weekStartMs }
        val weekPlayCount = weekRecords.sumOf { max(1, it.playCount) }
        val weekDurationMs = weekRecords.sumOf { it.track.durationMs * max(1, it.playCount) }
        val continueTrack = current ?: recentRecords.firstOrNull { it.track != null }?.track ?: activeTracks.firstOrNull()
        val durationMs = when {
            snapshot.durationMs > 0L -> snapshot.durationMs
            continueTrack != null -> continueTrack.durationMs
            else -> 0L
        }
        val progress = if (durationMs <= 0L) 0f else (snapshot.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val totalDurationMs = activeTracks.sumOf { it.durationMs }
        val heatmapData = buildHeatmap(recentRecords, nowMs)
        val heatmapMonths = buildMonthLabels(heatmapData)
        val activeWeeks = heatmapData.chunked(7)
            .count { week -> week.any { !it.future && it.count > 0 } }
        val todayListening = buildTodayListening(recentRecords, nowMs)
        val todayListeningDurationMs = todayListening.sumOf { it.durationMs }

        return HomeDashboardUiState(
            title = "YUKINE",
            subtitle = librarySubtitle(activeTracks.size, totalDurationMs),
            heroTitle = dailySlogan(nowMs),
            heroSubtitle = buildHeroSubtitle(continueTrack),
            continueTitle = continueTrack?.title ?: "准备播放",
            continueSubtitle = continueTrack?.subtitle() ?: "添加音乐后开始聆听",
            continueDetail = if (snapshot.playing) "正在播放" else "继续播放",
            continueAlbumArtUri = continueTrack?.albumArtUri,
            continueProgress = progress,
            continuePlaying = snapshot.playing,
            stats = listOf(
                HomeDashboardStatUiState("歌曲", activeTracks.size.toString(), LibraryGrouping.SONGS),
                HomeDashboardStatUiState("专辑", LibraryGrouping.uniqueAlbumCount(activeTracks).toString(), LibraryGrouping.ALBUMS),
                HomeDashboardStatUiState("艺人", LibraryGrouping.uniqueArtistCount(activeTracks).toString(), LibraryGrouping.ARTISTS),
                HomeDashboardStatUiState("文件夹", LibraryGrouping.uniqueFolderCount(activeTracks).toString(), LibraryGrouping.FOLDERS)
            ),
            recent = recentRows,
            recentTabIndex = 0,
            todayListeningDuration = listeningDurationSummary(todayListeningDurationMs),
            todayListeningPoints = todayListening,
            weeklyTitle = "本周回声",
            weeklyPlays = weekPlayCount,
            weeklyDuration = durationSummary(weekDurationMs),
            weeklyBars = weekBars(weekRecords, nowMs),
            heatmap = heatmapData,
            heatmapMonths = heatmapMonths,
            activeWeeks = activeWeeks,
            activeDays = heatmapData.count { it.count > 0 },
            empty = activeTracks.isEmpty()
        )
    }

    private fun buildHeroSubtitle(continueTrack: Track?): String {
        return if (continueTrack != null) {
            "接上 ${continueTrack.artist} 的「${continueTrack.title}」，或者从最近入库里挑一张封面开始。"
        } else {
            "接上最近播放，或者从最近入库里挑一张封面开始。"
        }
    }

    private fun librarySubtitle(trackCount: Int, durationMs: Long): String {
        val trackLabel = "$trackCount 首歌曲"
        val duration = durationSummary(durationMs)
        return if (duration == "0 分钟") trackLabel else "$trackLabel - $duration"
    }

    private fun lastPlayedLabel(record: TrackPlayRecord): String {
        val count = max(1, record.playCount)
        return if (count == 1) "播放 1 次" else "播放 $count 次"
    }

    private fun durationSummary(durationMs: Long): String {
        val minutes = max(0L, durationMs / 60000L)
        if (minutes < 60L) {
            return "$minutes 分钟"
        }
        val hours = minutes / 60L
        val rest = minutes % 60L
        return if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分钟"
    }

    private fun listeningDurationSummary(durationMs: Long): String {
        if (durationMs <= 0L) return "0 分钟"
        if (durationMs < 60_000L) {
            return "${max(1L, durationMs / 1_000L)} 秒"
        }
        return durationSummary(durationMs)
    }

    private fun buildTodayListening(
        records: List<TrackPlayRecord>,
        nowMs: Long
    ): List<HomeDashboardListeningPoint> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMs
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val todayStartMs = startOfDay(nowMs)
        calendar.timeInMillis = todayStartMs
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStartMs = calendar.timeInMillis
        val buckets = LongArray(24)

        records.forEach { record ->
            if (record.track == null || record.playedAt < todayStartMs || record.playedAt >= tomorrowStartMs) {
                return@forEach
            }
            calendar.timeInMillis = record.playedAt
            val hour = calendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            val playCount = max(1, record.playCount)
            buckets[hour] += record.track.durationMs.coerceAtLeast(0L) * playCount.toLong()
        }

        return buckets.mapIndexed { hour, durationMs ->
            HomeDashboardListeningPoint(
                hour = hour,
                durationMs = durationMs,
                future = hour > currentHour
            )
        }
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
        // Calendar.MONDAY = 2; shift so Monday is the first column like desktop.
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val mondayOffset = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        calendar.add(Calendar.DAY_OF_YEAR, mondayOffset)
        return calendar.timeInMillis
    }

    private fun startOfDay(timeMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Build a HEATMAP_WEEKS x 7 grid of natural weeks (Mon..Sun per column),
     * ending with the current week. Days after today are flagged as future.
     */
    private fun buildHeatmap(records: List<TrackPlayRecord>, nowMs: Long): List<HomeDashboardHeatmapDay> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()

        val currentWeekStart = startOfWeek(nowMs)
        val firstWeekStart = currentWeekStart - (HEATMAP_WEEKS - 1) * 7L * DAY_MS
        val daysToShow = HEATMAP_WEEKS * 7

        // Today at midnight, for future-day detection.
        calendar.timeInMillis = nowMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStartMs = calendar.timeInMillis

        val dayCounts = mutableMapOf<String, Int>()
        for (record in records) {
            if (record.playedAt >= firstWeekStart) {
                calendar.timeInMillis = record.playedAt
                val dateKey = dateFormat.format(calendar.time)
                dayCounts[dateKey] = (dayCounts[dateKey] ?: 0) + max(1, record.playCount)
            }
        }

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

    /**
     * Derive month labels spanning week columns. The label for a column is the
     * month of that column's first day (Monday); consecutive columns in the same
     * month are merged into a single spanning label, matching desktop.
     */
    private fun buildMonthLabels(heatmap: List<HomeDashboardHeatmapDay>): List<HomeDashboardHeatmapMonth> {
        if (heatmap.isEmpty()) return emptyList()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        val weeks = heatmap.chunked(7)
        val labels = mutableListOf<HomeDashboardHeatmapMonth>()
        var lastMonth = -1
        var lastYear = -1
        weeks.forEachIndexed { weekIndex, week ->
            val firstDay = week.firstOrNull() ?: return@forEachIndexed
            val parsed = runCatching { dateFormat.parse(firstDay.date) }.getOrNull() ?: return@forEachIndexed
            calendar.time = parsed
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            if (labels.isEmpty() || month != lastMonth || year != lastYear) {
                labels.add(
                    HomeDashboardHeatmapMonth(
                        label = "${month + 1}月",
                        weekIndex = weekIndex,
                        span = 1
                    )
                )
                lastMonth = month
                lastYear = year
            } else {
                val last = labels.removeAt(labels.size - 1)
                labels.add(last.copy(span = last.span + 1))
            }
        }
        return labels
    }
}
