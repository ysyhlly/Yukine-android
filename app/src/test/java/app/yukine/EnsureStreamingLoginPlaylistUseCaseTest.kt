package app.yukine

import app.yukine.model.Playlist
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Test

class EnsureStreamingLoginPlaylistUseCaseTest {
    @Test
    fun createsPlaylistAndLinksFavorites() {
        val operations = FakeStreamingLoginPlaylistOperations()
        operations.createdPlaylistId = 42L

        val result = EnsureStreamingLoginPlaylistUseCase(operations).execute(
            "My NetEase Playlist",
            StreamingProviderName.NETEASE
        )

        assertEquals(42L, result.playlistId)
        assertEquals(listOf("create:My NetEase Playlist", "link:42|netease|"), operations.events)
    }

    @Test
    fun linksExistingPlaylistWhenCreateReportsDuplicate() {
        val operations = FakeStreamingLoginPlaylistOperations()
        operations.createdPlaylistId = -1L
        operations.playlists = listOf(playlist(9L, "My NetEase Playlist"))

        val result = EnsureStreamingLoginPlaylistUseCase(operations).execute(
            "My NetEase Playlist",
            StreamingProviderName.NETEASE
        )

        assertEquals(9L, result.playlistId)
        assertEquals(
            listOf("create:My NetEase Playlist", "loadPlaylists", "link:9|netease|"),
            operations.events
        )
    }

    @Test
    fun doesNotLinkWhenPlaylistCannotBeCreatedOrFound() {
        val operations = FakeStreamingLoginPlaylistOperations()
        operations.createdPlaylistId = -1L

        val result = EnsureStreamingLoginPlaylistUseCase(operations).execute(
            "Missing",
            StreamingProviderName.NETEASE
        )

        assertEquals(-1L, result.playlistId)
        assertEquals(listOf("create:Missing", "loadPlaylists"), operations.events)
    }

    private class FakeStreamingLoginPlaylistOperations : StreamingLoginPlaylistOperations {
        var createdPlaylistId = -1L
        var playlists: List<Playlist> = emptyList()
        val events = mutableListOf<String>()

        override fun createPlaylist(name: String): Long {
            events.add("create:$name")
            return createdPlaylistId
        }

        override fun loadPlaylists(): List<Playlist> {
            events.add("loadPlaylists")
            return playlists
        }

        override fun linkPlaylist(
            localPlaylistId: Long,
            provider: StreamingProviderName,
            providerPlaylistId: String
        ) {
            events.add("link:$localPlaylistId|${provider.wireName}|$providerPlaylistId")
        }
    }

    private fun playlist(id: Long, name: String): Playlist =
        Playlist(id, name, 0, 0L, 0L)
}
