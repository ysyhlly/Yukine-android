package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HomeDashboardStateFactoryTest {
    @Test
    fun createBuildsChineseDashboardSummaryAndStats() {
        val now = 10_000_000L
        val tracks = listOf(
            track(1L, "第一首", "艺人 A", "专辑 A", "E:/Music/A/one.mp3", 180_000L),
            track(2L, "第二首", "艺人 A", "专辑 A", "E:/Music/A/two.mp3", 240_000L),
            track(3L, "第三首", "艺人 B", "专辑 B", "E:/Music/B/three.mp3", 60_000L)
        )
        val records = listOf(
            TrackPlayRecord(tracks[2], now - 1_000L, 2),
            TrackPlayRecord(tracks[0], now - 2_000L, 1)
        )

        val state = HomeDashboardStateFactory.create(
            "zh",
            tracks,
            tracks,
            records,
            snapshot(tracks[1], 120_000L, playing = true),
            now
        )

        assertEquals("YUKINE", state.title)
        assertEquals("3 首歌曲 - 8 分钟", state.subtitle)
        assertEquals("第二首", state.continueTitle)
        assertEquals("正在播放", state.continueDetail)
        assertEquals(0.5f, state.continueProgress, 0.001f)
        assertEquals(listOf("歌曲", "专辑", "艺人", "文件夹"), state.stats.map { it.label })
        assertEquals(listOf("3", "2", "2", "2"), state.stats.map { it.value })
        assertEquals("本周回声", state.weeklyTitle)
        assertEquals(3, state.weeklyPlays)
        assertEquals("5 分钟", state.weeklyDuration)
        assertEquals(listOf("第三首", "第一首"), state.recent.map { it.title })
        assertEquals("播放 2 次", state.recent.first().detail)
        assertFalse(state.empty)
    }

    @Test
    fun createUsesChineseEmptyStateWhenLibraryHasNoTracks() {
        val state = HomeDashboardStateFactory.create(
            "zh",
            emptyList(),
            emptyList(),
            emptyList(),
            PlaybackStateSnapshot.empty(),
            10_000L
        )

        assertEquals("0 首歌曲", state.subtitle)
        assertEquals("准备播放", state.continueTitle)
        assertEquals("添加音乐后开始聆听", state.continueSubtitle)
        assertEquals("继续播放", state.continueDetail)
        assertTrue(state.empty)
    }

    @Test
    fun createBuildsTodayListeningDurationByHourFromPlayRecords() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 17, 18, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val morningTrack = track(1L, "晨间", "艺人 A", "专辑 A", "E:/Music/A/morning.mp3", 180_000L)
        val afternoonTrack = track(2L, "午后", "艺人 B", "专辑 B", "E:/Music/B/afternoon.mp3", 240_000L)
        val yesterdayTrack = track(3L, "昨日", "艺人 C", "专辑 C", "E:/Music/C/yesterday.mp3", 600_000L)
        val records = listOf(
            TrackPlayRecord(morningTrack, timeOnSameDay(now, 9), 2),
            TrackPlayRecord(afternoonTrack, timeOnSameDay(now, 14), 1),
            TrackPlayRecord(yesterdayTrack, now - 24L * 60L * 60L * 1_000L, 1)
        )

        val state = HomeDashboardStateFactory.create(
            "zh",
            listOf(morningTrack, afternoonTrack, yesterdayTrack),
            listOf(morningTrack, afternoonTrack, yesterdayTrack),
            records,
            PlaybackStateSnapshot.empty(),
            now
        )

        assertEquals("10 分钟", state.todayListeningDuration)
        assertEquals(24, state.todayListeningPoints.size)
        assertEquals(360_000L, state.todayListeningPoints[9].durationMs)
        assertEquals(240_000L, state.todayListeningPoints[14].durationMs)
        assertEquals(0L, state.todayListeningPoints[8].durationMs)
        assertFalse(state.todayListeningPoints[18].future)
        assertTrue(state.todayListeningPoints[19].future)
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        path: String,
        durationMs: Long
    ): Track {
        return Track(id, title, artist, album, durationMs, Uri.EMPTY, path)
    }

    private fun snapshot(track: Track, positionMs: Long, playing: Boolean): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            track,
            0,
            1,
            positionMs,
            track.durationMs,
            playing,
            false,
            "",
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
    }

    private fun timeOnSameDay(dayMs: Long, hour: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dayMs
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
