package app.yukine

import app.yukine.model.Playlist
import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class MainStreamingLocalPlaylistOperationsTest {
    @Test
    fun importsStreamingPlaylistThroughUseCaseAndLinksProviderPlaylist() {
        val importOperations = FakeStreamingPlaylistImportOperations()
        importOperations.nextResult = PlaylistImportResult(42L, "Cloud Mix", 2, 2, 2, 0)
        val operations = operations(importOperations = importOperations)

        val result = operations.importStreamingPlaylist(
            playlistName = "Cloud Mix",
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "  remote-42  ",
            streamingTracks = listOf(streamingTrack("100"), streamingTrack("200")),
            linkWhenProviderPlaylistIdBlank = false
        )

        assertEquals(42L, result.playlistId)
        assertEquals("Cloud Mix", importOperations.importedPlaylistName)
        assertEquals(
            listOf("streaming:netease:100", "streaming:netease:200"),
            importOperations.importedTracks.map { it.dataPath }
        )
        assertEquals(listOf("link:42|netease|remote-42"), importOperations.events)
    }

    @Test
    fun syncsLoginPlaylistAndLoadsLinkedPlaylistsThroughUseCases() {
        val syncOperations = FakeStreamingPlaylistSyncOperations().apply {
            nextCount = 2
        }
        val loginOperations = FakeStreamingLoginPlaylistOperations().apply {
            nextCreatedPlaylistId = 88L
        }
        val linkOperations = FakeStreamingPlaylistLinkOperations().apply {
            localLink = link(88L)
            remoteLink = link(99L)
        }
        val operations = operations(
            syncOperations = syncOperations,
            loginOperations = loginOperations,
            linkOperations = linkOperations
        )

        assertEquals(true, operations.playlistExists(88L))
        val syncResult = operations.syncStreamingPlaylist(link(88L), listOf(streamingTrack("300")))
        val loginResult = operations.ensureStreamingLoginPlaylist("Liked Songs", StreamingProviderName.NETEASE)
        val localLink = operations.linkedPlaylist(88L)
        val remoteLink = operations.linkedPlaylist(StreamingProviderName.NETEASE, " remote-99 ")

        assertEquals(88L, syncResult.playlistId)
        assertEquals(2, syncResult.syncedCount)
        assertEquals(false, syncResult.empty)
        assertEquals(listOf("streaming:netease:300"), syncOperations.syncedTracks.map { it.dataPath })
        assertEquals(listOf("exists:88", "exists:88", "sync:88", "mark:88"), syncOperations.events)
        assertEquals(88L, loginResult.playlistId)
        assertEquals("Liked Songs", loginResult.playlistName)
        assertEquals(listOf("create:Liked Songs", "link:88|netease|"), loginOperations.events)
        assertEquals(88L, localLink?.localPlaylistId)
        assertEquals(99L, remoteLink?.localPlaylistId)
        assertEquals(listOf("get:88", "remote:netease:remote-99"), linkOperations.events)
    }

    private fun operations(
        importOperations: FakeStreamingPlaylistImportOperations = FakeStreamingPlaylistImportOperations(),
        syncOperations: FakeStreamingPlaylistSyncOperations = FakeStreamingPlaylistSyncOperations(),
        loginOperations: FakeStreamingLoginPlaylistOperations = FakeStreamingLoginPlaylistOperations(),
        linkOperations: FakeStreamingPlaylistLinkOperations = FakeStreamingPlaylistLinkOperations()
    ): MainStreamingLocalPlaylistOperations =
        MainStreamingLocalPlaylistOperations(
            ImportStreamingPlaylistUseCase(importOperations),
            SyncStreamingPlaylistUseCase(syncOperations),
            EnsureStreamingLoginPlaylistUseCase(loginOperations),
            GetStreamingPlaylistLinkUseCase(linkOperations)
        )

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

    private class FakeStreamingPlaylistSyncOperations : StreamingPlaylistSyncOperations {
        var playlistExists = true
        var nextCount = 0
        var syncedTracks: List<Track> = emptyList()
        val events = mutableListOf<String>()

        override fun playlistExists(playlistId: Long): Boolean {
            events.add("exists:$playlistId")
            return playlistExists
        }

        override fun syncStreamingPlaylist(playlistId: Long, tracks: List<Track>): Int {
            events.add("sync:$playlistId")
            syncedTracks = tracks
            return nextCount
        }

        override fun markSynced(playlistId: Long) {
            events.add("mark:$playlistId")
        }
    }

    private class FakeStreamingLoginPlaylistOperations : StreamingLoginPlaylistOperations {
        var nextCreatedPlaylistId = 1L
        var playlists: List<Playlist> = emptyList()
        val events = mutableListOf<String>()

        override fun createPlaylist(name: String): Long {
            events.add("create:$name")
            return nextCreatedPlaylistId
        }

        override fun loadPlaylists(): List<Playlist> {
            events.add("load")
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

    private class FakeStreamingPlaylistLinkOperations : StreamingPlaylistLinkOperations {
        var localLink: StreamingPlaylistSyncStore.LinkedPlaylist? = null
        var remoteLink: StreamingPlaylistSyncStore.LinkedPlaylist? = null
        val events = mutableListOf<String>()

        override fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? {
            events.add("get:$localPlaylistId")
            return localLink
        }

        override fun getLink(
            provider: StreamingProviderName,
            providerPlaylistId: String
        ): StreamingPlaylistSyncStore.LinkedPlaylist? {
            events.add("remote:${provider.wireName}:$providerPlaylistId")
            return remoteLink
        }
    }

    private fun link(playlistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist =
        StreamingPlaylistSyncStore.LinkedPlaylist(
            localPlaylistId = playlistId,
            provider = StreamingProviderName.NETEASE,
            providerPlaylistId = "remote-$playlistId",
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
