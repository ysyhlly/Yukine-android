package app.echo.next

import app.echo.next.model.PlaylistImportResult
import app.echo.next.model.Track
import app.echo.next.streaming.StreamingProviderName
import app.echo.next.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportStreamingPlaylistUseCaseTest {
    @Test
    fun importsPlaceholderTracksAndLinksPlaylist() {
        val operations = FakeStreamingPlaylistImportOperations()
        operations.nextResult = PlaylistImportResult(42L, "Cloud Mix", 2, 2, 2, 0)
        val tracks = listOf(
            streamingTrack("100"),
            null,
            streamingTrack("200")
        )

        val result = ImportStreamingPlaylistUseCase(operations).execute(
            playlistName = "Cloud Mix",
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "  98765  ",
            streamingTracks = tracks
        )

        assertEquals(42L, result.playlistId)
        assertEquals("Cloud Mix", operations.importedPlaylistName)
        assertEquals(
            listOf("streaming:netease:100", "streaming:netease:200"),
            operations.importedTracks.map { it.dataPath }
        )
        assertEquals(listOf("link:42|netease|98765"), operations.events)
    }

    @Test
    fun doesNotLinkWhenImportFailsOrProviderPlaylistIdIsBlank() {
        val operations = FakeStreamingPlaylistImportOperations()
        operations.nextResult = PlaylistImportResult(-1L, "Broken", 1, 0, 0, 1)

        ImportStreamingPlaylistUseCase(operations).execute(
            playlistName = "Broken",
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "",
            streamingTracks = listOf(streamingTrack("100"))
        )

        assertEquals(emptyList<String>(), operations.events)
    }

    @Test
    fun canLinkProviderFavoritesWithBlankProviderPlaylistId() {
        val operations = FakeStreamingPlaylistImportOperations()
        operations.nextResult = PlaylistImportResult(9L, "Liked", 1, 1, 1, 0)

        ImportStreamingPlaylistUseCase(operations).execute(
            playlistName = "Liked",
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "",
            streamingTracks = listOf(streamingTrack("100")),
            linkWhenProviderPlaylistIdBlank = true
        )

        assertEquals(listOf("link:9|netease|"), operations.events)
    }

    private class FakeStreamingPlaylistImportOperations : StreamingPlaylistImportOperations {
        var nextResult = PlaylistImportResult(1L, "Playlist", 0, 0, 0, 0)
        var importedPlaylistName: String? = null
        var importedTracks: List<Track> = emptyList()
        val events = mutableListOf<String>()

        override fun importStreamingPlaylist(playlistName: String?, tracks: List<Track>): PlaylistImportResult {
            importedPlaylistName = playlistName
            importedTracks = tracks
            return nextResult
        }

        override fun linkPlaylist(
            localPlaylistId: Long,
            provider: StreamingProviderName,
            providerPlaylistId: String
        ) {
            events.add("link:$localPlaylistId|${provider.wireName}|$providerPlaylistId")
        }
    }

    private fun streamingTrack(id: String): StreamingTrack =
        StreamingTrack(
            provider = StreamingProviderName.NETEASE,
            providerTrackId = id,
            title = "Song $id",
            artist = "Artist"
        )
}
