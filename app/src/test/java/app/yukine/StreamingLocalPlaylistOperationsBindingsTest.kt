package app.yukine

import app.yukine.model.PlaylistImportResult
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingLocalPlaylistOperationsBindingsTest {
    @Test
    fun importDelegatesToUseCaseAndLinksBlankProviderPlaylistWhenRequested() {
        val importOperations = FakeStreamingPlaylistImportOperations()
        val bindings = bindings(importOperations = importOperations)

        val result = bindings.importStreamingPlaylist(
            "List",
            StreamingProviderName.NETEASE,
            "",
            listOf(streamingTrack("11")),
            true
        )

        assertEquals(7L, result.playlistId)
        assertEquals("List", importOperations.playlistName)
        assertEquals(listOf("streaming:netease:11"), importOperations.importedDataPaths)
        assertEquals(listOf("link:7:netease:"), importOperations.events)
    }

    @Test
    fun syncAndLoginMapUseCaseResultsToViewModelContracts() {
        val syncOperations = FakeStreamingPlaylistSyncOperations()
        val loginOperations = FakeStreamingLoginPlaylistOperations()
        val bindings = bindings(syncOperations = syncOperations, loginOperations = loginOperations)
        val link = StreamingPlaylistSyncStore.LinkedPlaylist(5L, StreamingProviderName.NETEASE, "p-1", 0L)

        val syncResult = bindings.syncStreamingPlaylist(link, listOf(streamingTrack("22")))
        val loginResult = bindings.ensureStreamingLoginPlaylist("Login", StreamingProviderName.NETEASE)

        assertEquals(5L, syncResult.playlistId)
        assertEquals(1, syncResult.syncedCount)
        assertEquals(false, syncResult.empty)
        assertEquals(listOf("sync:5:1", "mark:5"), syncOperations.events)
        assertEquals(9L, loginResult.playlistId)
        assertEquals("Login", loginResult.playlistName)
        assertEquals(listOf("create:Login", "link:9:netease:"), loginOperations.events)
    }

    @Test
    fun linkedPlaylistDelegatesToLinkUseCase() {
        val linkOperations = FakeStreamingPlaylistLinkOperations()
        val bindings = bindings(linkOperations = linkOperations)

        val missing = bindings.linkedPlaylist(-1L)
        val linked = bindings.linkedPlaylist(8L)
        val remoteLinked = bindings.linkedPlaylist(StreamingProviderName.NETEASE, "p-9")

        assertNull(missing)
        assertEquals(8L, linked?.localPlaylistId)
        assertEquals(9L, remoteLinked?.localPlaylistId)
        assertEquals(listOf("link:8", "remote:netease:p-9"), linkOperations.events)
    }

    private fun bindings(
        importOperations: FakeStreamingPlaylistImportOperations = FakeStreamingPlaylistImportOperations(),
        syncOperations: FakeStreamingPlaylistSyncOperations = FakeStreamingPlaylistSyncOperations(),
        loginOperations: FakeStreamingLoginPlaylistOperations = FakeStreamingLoginPlaylistOperations(),
        linkOperations: FakeStreamingPlaylistLinkOperations = FakeStreamingPlaylistLinkOperations()
    ) = StreamingLocalPlaylistOperationsBindings(
        ImportStreamingPlaylistUseCase(importOperations),
        SyncStreamingPlaylistUseCase(syncOperations),
        EnsureStreamingLoginPlaylistUseCase(loginOperations),
        GetStreamingPlaylistLinkUseCase(linkOperations)
    )

    private class FakeStreamingPlaylistImportOperations : StreamingPlaylistImportOperations {
        val events = mutableListOf<String>()
        var playlistName: String? = null
        var importedDataPaths: List<String> = emptyList()

        override fun importStreamingPlaylist(playlistName: String?, tracks: List<Track>): PlaylistImportResult {
            this.playlistName = playlistName
            importedDataPaths = tracks.map { it.dataPath }
            return PlaylistImportResult(7L, playlistName.orEmpty(), tracks.size, tracks.size, tracks.size, 0)
        }

        override fun linkPlaylist(
            localPlaylistId: Long,
            provider: StreamingProviderName,
            providerPlaylistId: String
        ) {
            events += "link:$localPlaylistId:${provider.wireName}:$providerPlaylistId"
        }
    }

    private class FakeStreamingPlaylistSyncOperations : StreamingPlaylistSyncOperations {
        val events = mutableListOf<String>()

        override fun syncStreamingPlaylist(playlistId: Long, tracks: List<Track>): Int {
            events += "sync:$playlistId:${tracks.size}"
            return tracks.size
        }

        override fun markSynced(playlistId: Long) {
            events += "mark:$playlistId"
        }
    }

    private class FakeStreamingLoginPlaylistOperations : StreamingLoginPlaylistOperations {
        val events = mutableListOf<String>()

        override fun createPlaylist(name: String): Long {
            events += "create:$name"
            return 9L
        }

        override fun loadPlaylists() = emptyList<app.yukine.model.Playlist>()

        override fun linkPlaylist(
            localPlaylistId: Long,
            provider: StreamingProviderName,
            providerPlaylistId: String
        ) {
            events += "link:$localPlaylistId:${provider.wireName}:$providerPlaylistId"
        }
    }

    private class FakeStreamingPlaylistLinkOperations : StreamingPlaylistLinkOperations {
        val events = mutableListOf<String>()

        override fun getLink(localPlaylistId: Long): StreamingPlaylistSyncStore.LinkedPlaylist? {
            events += "link:$localPlaylistId"
            return StreamingPlaylistSyncStore.LinkedPlaylist(localPlaylistId, StreamingProviderName.NETEASE, "p-$localPlaylistId", 0L)
        }

        override fun getLink(
            provider: StreamingProviderName,
            providerPlaylistId: String
        ): StreamingPlaylistSyncStore.LinkedPlaylist? {
            events += "remote:${provider.wireName}:$providerPlaylistId"
            return StreamingPlaylistSyncStore.LinkedPlaylist(9L, provider, providerPlaylistId, 0L)
        }
    }

    private fun streamingTrack(id: String) = StreamingTrack(
        provider = StreamingProviderName.NETEASE,
        providerTrackId = id,
        title = "Song $id",
        artist = "Artist"
    )
}
