package app.echo.next

import android.net.Uri
import app.echo.next.model.Track
import app.echo.next.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateUpdateControllerTest {
    private val controller = PlaybackStateUpdateController()

    @Test
    fun resolveRefreshesLyricsAndCollectionsWhenPlayingTrackChanges() {
        val next = snapshot(track = track(42L), playing = true)

        val result = controller.resolve(
            MainRoutes.TAB_QUEUE,
            PlaybackStateSnapshot.empty(),
            next,
            currentLyricsTrackId = 7L,
            lastHistoryRefreshTrackId = 7L
        )

        assertTrue(result.loadLyrics)
        assertTrue(result.refreshCollections)
        assertTrue(result.renderSelectedTab)
        assertFalse(result.updateNowPlaying)
        assertFalse(result.showError)
        assertEquals(42L, result.lastHistoryRefreshTrackId)
    }

    @Test
    fun resolveDoesNotRefreshCollectionsForSameHistoryTrack() {
        val next = snapshot(track = track(42L), playing = true)

        val result = controller.resolve(
            MainRoutes.TAB_LIBRARY,
            PlaybackStateSnapshot.empty(),
            next,
            currentLyricsTrackId = 42L,
            lastHistoryRefreshTrackId = 42L
        )

        assertFalse(result.loadLyrics)
        assertFalse(result.refreshCollections)
        assertFalse(result.renderSelectedTab)
        assertFalse(result.updateNowPlaying)
        assertEquals(42L, result.lastHistoryRefreshTrackId)
    }

    @Test
    fun resolveUpdatesNowPlayingContentWhenNowTabStateDidNotNeedFullRender() {
        val previous = snapshot(track = track(42L), playing = true)
        val next = snapshot(track = track(42L), playing = true)

        val result = controller.resolve(
            MainRoutes.TAB_NOW,
            previous,
            next,
            currentLyricsTrackId = 42L,
            lastHistoryRefreshTrackId = 42L
        )

        assertFalse(result.renderSelectedTab)
        assertTrue(result.updateNowPlaying)
    }

    @Test
    fun resolveShowsErrorWhenSnapshotHasErrorMessage() {
        val result = controller.resolve(
            MainRoutes.TAB_LIBRARY,
            PlaybackStateSnapshot.empty(),
            snapshot(errorMessage = "Playback failed"),
            currentLyricsTrackId = -1L,
            lastHistoryRefreshTrackId = -1L
        )

        assertTrue(result.showError)
    }

    private fun track(id: Long): Track {
        return Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
    }

    private fun snapshot(
        track: Track? = null,
        playing: Boolean = false,
        errorMessage: String = ""
    ): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            track,
            if (track == null) -1 else 0,
            if (track == null) 0 else 1,
            0L,
            track?.durationMs ?: 0L,
            playing,
            false,
            errorMessage,
            false,
            0,
            1.0f,
            1.0f,
            0L
        )
    }
}
