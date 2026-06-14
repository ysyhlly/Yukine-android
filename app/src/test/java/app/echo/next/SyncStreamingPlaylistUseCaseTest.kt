package app.echo.next

import app.echo.next.model.Track
import app.echo.next.streaming.StreamingPlaylistSyncStore
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStreamingPlaylistUseCaseTest {
    @Test
    fun emptyRemoteTracksOnlyMarksPlaylistSynced() {
        val operations = FakeStreamingPlaylistSyncOperations()
        val link = link(7L)

        val result = SyncStreamingPlaylistUseCase(operations).execute(link, emptyList())

        assertTrue(result.empty)
        assertEquals(0, result.syncedCount)
        assertEquals(listOf("mark:7"), operations.events)
    }

    @Test
    fun syncsPlaceholdersAndMarksPlaylistSynced() {
        val operations = FakeStreamingPlaylistSyncOperations()
        operations.nextCount = 2
        val link = link(9L)

        val result = SyncStreamingPlaylistUseCase(operations).execute(
            link,
            listOf(streamingTrack("100"), null, streamingTrack("200"))
        )

        assertFalse(result.empty)
        assertEquals(2, result.syncedCount)
        assertEquals(
            listOf("streaming:netease:100", "streaming:netease:200"),
            operations.syncedTracks.map { it.dataPath }
        )
        assertEquals(listOf("sync:9", "mark:9"), operations.events)
    }

    private class FakeStreamingPlaylistSyncOperations : StreamingPlaylistSyncOperations {
        var nextCount = 0
        var syncedTracks: List<Track> = emptyList()
        val events = mutableListOf<String>()

        override fun syncStreamingPlaylist(playlistId: Long, tracks: List<Track>): Int {
            events.add("sync:$playlistId")
            syncedTracks = tracks
            return nextCount
        }

        override fun markSynced(playlistId: Long) {
            events.add("mark:$playlistId")
        }
    }

    private fun link(playlistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist =
        StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = playlistId,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "playlist-$playlistId",
            lastSyncMs = 0L
        )

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist"
        )
}
