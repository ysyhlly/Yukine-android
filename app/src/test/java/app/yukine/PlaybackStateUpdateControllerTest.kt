package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateUpdateControllerTest {
    @Test
    fun resolveRefreshesLyricsAndCollectionsWhenPlayingTrackChanges() {
        val next = snapshot(track = track(42L), playing = true)

        val result = PlaybackStateUpdateController.resolve(
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
    fun resolveRendersLibraryWhenPlaybackSnapshotIntroducesTrackWithoutRefreshingCollections() {
        val next = snapshot(track = track(42L), playing = true)

        val result = PlaybackStateUpdateController.resolve(
            MainRoutes.TAB_LIBRARY,
            PlaybackStateSnapshot.empty(),
            next,
            currentLyricsTrackId = 42L,
            lastHistoryRefreshTrackId = 42L
        )

        assertFalse(result.loadLyrics)
        assertFalse(result.refreshCollections)
        assertTrue(result.renderSelectedTab)
        assertFalse(result.updateNowPlaying)
        assertEquals(42L, result.lastHistoryRefreshTrackId)
    }

    @Test
    fun resolveRendersLibraryWhenCurrentTrackChanges() {
        val previous = snapshot(track = track(41L), playing = true)
        val next = snapshot(track = track(42L), playing = true)

        val result = PlaybackStateUpdateController.resolve(
            MainRoutes.TAB_LIBRARY,
            previous,
            next,
            currentLyricsTrackId = 42L,
            lastHistoryRefreshTrackId = 42L
        )

        assertTrue(result.renderSelectedTab)
    }

    @Test
    fun resolveDoesNotRenderLibraryForProgressOnlyChange() {
        val previous = snapshot(track = track(42L), playing = true, positionMs = 100L)
        val next = snapshot(track = track(42L), playing = true, positionMs = 200L)

        val result = PlaybackStateUpdateController.resolve(
            MainRoutes.TAB_LIBRARY,
            previous,
            next,
            currentLyricsTrackId = 42L,
            lastHistoryRefreshTrackId = 42L
        )

        assertFalse(result.renderSelectedTab)
    }

    @Test
    fun resolveUpdatesNowPlayingContentWhenNowTabStateDidNotNeedFullRender() {
        val previous = snapshot(track = track(42L), playing = true)
        val next = snapshot(track = track(42L), playing = true)

        val result = PlaybackStateUpdateController.resolve(
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
        val result = PlaybackStateUpdateController.resolve(
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
        positionMs: Long = 0L,
        errorMessage: String = ""
    ): PlaybackStateSnapshot {
        return PlaybackStateSnapshot(
            track,
            if (track == null) -1 else 0,
            if (track == null) 0 else 1,
            positionMs,
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
