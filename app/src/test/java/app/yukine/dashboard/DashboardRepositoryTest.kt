package app.yukine.dashboard

import android.net.Uri
import app.yukine.StreamingGatewayEndpointStore
import app.yukine.model.Track
import app.yukine.model.TrackPlayRecord
import app.yukine.playback.PlaybackStateSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRepositoryTest {
    @Test
    fun fetchHomeComputesLocalWeeklyRecapWhenGatewayIsNotConfigured() = runTest {
        val now = System.currentTimeMillis()
        val recent = track(1L, "Recent", 180_000L)
        val old = track(2L, "Old", 600_000L)
        val repo = DashboardRepository(FakeDashboardGateway(), FakeEndpointStore(configured = false))

        val state = repo.fetchHome(
            listOf(recent, old),
            listOf(
                TrackPlayRecord(recent, now, 2),
                TrackPlayRecord(old, now - 8L * 24L * 60L * 60L * 1000L, 10)
            ),
            PlaybackStateSnapshot.empty()
        )

        assertEquals(2, state.weeklyPlays)
        assertEquals("6 分钟", state.weeklyDuration)
        assertTrue(state.weeklyBars.any { it > 0.08f })
        assertTrue(state.heatmap.any { it.count == 2 })
        assertTrue(state.activeDays > 0)
        assertEquals(24, state.todayListeningPoints.size)
        assertEquals(360_000L, state.todayListeningPoints.sumOf { it.durationMs })
        assertEquals("6 分钟", state.todayListeningDuration)
    }

    @Test
    fun fetchHomeUsesLocalWeeklyRecapWhenGatewayHomeDoesNotProvideOne() = runTest {
        val now = System.currentTimeMillis()
        val recent = track(1L, "Recent", 240_000L)
        val repo = DashboardRepository(
            FakeDashboardGateway(homeResponse = emptyRemoteHome()),
            FakeEndpointStore(configured = true)
        )

        val state = repo.fetchHome(
            listOf(recent),
            listOf(TrackPlayRecord(recent, now, 3)),
            PlaybackStateSnapshot.empty()
        )

        assertEquals(3, state.weeklyPlays)
        assertEquals("12 分钟", state.weeklyDuration)
        assertTrue(state.weeklyBars.any { it > 0.08f })
        assertTrue(state.activeDays > 0)
        assertEquals(24, state.todayListeningPoints.size)
        assertEquals(720_000L, state.todayListeningPoints.sumOf { it.durationMs })
        assertEquals("12 分钟", state.todayListeningDuration)
    }

    private fun track(id: Long, title: String, durationMs: Long): Track {
        return Track(id, title, "Artist", "Album", durationMs, Uri.EMPTY, "$title.mp3")
    }

    private fun emptyRemoteHome(): DashboardHomeResponse {
        return DashboardHomeResponse(
            hero = DashboardHero("Hero", "Subtitle", "continue_playback"),
            libraryStats = DashboardLibraryStats(1, 1, 1, 1, "4 鍒嗛挓"),
            nowPlaying = null,
            recentActivity = emptyList(),
            weeklyRecap = DashboardWeeklyRecap(
                playCount = 0,
                durationText = "0 鍒嗛挓",
                heatmap = emptyList()
            )
        )
    }

    private class FakeEndpointStore(
        private val configured: Boolean
    ) : StreamingGatewayEndpointStore {
        override fun endpoint(): String = if (configured) "http://127.0.0.1:43990" else "gateway://unconfigured"

        override fun configured(): Boolean = configured

        override fun setEndpoint(nextEndpoint: String?) = Unit
    }

    private class FakeDashboardGateway(
        private val homeResponse: DashboardHomeResponse? = null
    ) : DashboardGateway {
        override suspend fun home(): DashboardHomeResponse = homeResponse ?: throw DashboardGatewayException("No fake home response")

        override suspend fun playbackState(): PlaybackStateResponse = PlaybackStateResponse(
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            trackId = null,
            title = null,
            artist = null,
            album = null,
            artworkUrl = null
        )

        override suspend fun play(): PlaybackActionResponse = PlaybackActionResponse(false, null)

        override suspend fun pause(): PlaybackActionResponse = PlaybackActionResponse(false, null)

        override suspend fun toggle(): PlaybackActionResponse = PlaybackActionResponse(false, null)

        override suspend fun next(): PlaybackActionResponse = PlaybackActionResponse(false, null)

        override suspend fun previous(): PlaybackActionResponse = PlaybackActionResponse(false, null)

        override suspend fun seek(positionMs: Long): PlaybackActionResponse = PlaybackActionResponse(false, null)

        override suspend fun recentActivity(limit: Int): RecentActivityResponse = RecentActivityResponse(emptyList())

        override suspend fun weeklyRecap(): WeeklyRecapResponse = WeeklyRecapResponse(
            playCount = 0,
            durationMs = 0L,
            durationText = "0 鍒嗛挓",
            activeDays = 0,
            heatmap = emptyList()
        )
    }
}
