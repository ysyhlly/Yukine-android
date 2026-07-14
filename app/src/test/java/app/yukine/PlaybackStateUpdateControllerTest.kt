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
            next,
            currentLyricsTrackId = 7L,
            lastHistoryRefreshTrackId = 7L
        )

        assertTrue(result.loadLyrics)
        assertTrue(result.refreshCollections)
        assertFalse(result.showError)
        assertEquals(42L, result.lastHistoryRefreshTrackId)
    }

    @Test
    fun resolveDoesNotRefreshLyricsOrCollectionsForAlreadyHandledTrack() {
        val next = snapshot(track = track(42L), playing = true)

        val result = PlaybackStateUpdateController.resolve(
            next,
            currentLyricsTrackId = 42L,
            lastHistoryRefreshTrackId = 42L
        )

        assertFalse(result.loadLyrics)
        assertFalse(result.refreshCollections)
        assertEquals(42L, result.lastHistoryRefreshTrackId)
    }

    @Test
    fun resolveShowsErrorWhenSnapshotHasErrorMessage() {
        val result = PlaybackStateUpdateController.resolve(
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
