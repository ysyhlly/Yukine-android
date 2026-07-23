package app.yukine

import app.yukine.model.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRowStateFactoryTest {
    @Test
    fun unsupportedLegacyTrackIsMarkedAcrossLibraryPlaylistAndQueueStates() {
        val track = track(1L, "/music/legacy.wma", "wma")

        val library = TrackRowStateFactory.trackRow(track, null, emptySet(), "", true)
        val playlist = TrackRowStateFactory.playlistRow("p", track, null, emptySet(), false, false)
        val queue = TrackRowStateFactory.queueRow("q", track, null, emptySet())

        assertFalse(library.playbackEnabled)
        assertFalse(playlist.playbackEnabled)
        assertFalse(queue.playbackEnabled)
        assertTrue(library.supportLabel?.isNotBlank() == true)
        assertTrue(playlist.supportLabel?.isNotBlank() == true)
        assertTrue(queue.supportLabel?.isNotBlank() == true)
    }

    @Test
    fun dsdConditionalTrackRemainsPlayableInGenericUiPolicy() {
        val track = track(2L, "/music/album.dsf", "dsd")

        val library = TrackRowStateFactory.trackRow(track, null, emptySet(), "", true)

        assertTrue(library.playbackEnabled)
        assertNull(library.supportLabel)
    }

    private fun track(id: Long, path: String, codec: String): Track = Track(
        id,
        "Title",
        "Artist",
        "Album",
        1_000L,
        null,
        path,
        0L,
        null,
        codec,
        0,
        0,
        0,
        0
    )
}
