package app.yukine.streaming

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPlaylistWriteTest {
    @Test
    fun qqPlaylistCanBeCreatedAndWrittenWhilePlaybackRemainsDisabled() = runTest {
        val gateway = RecordingPlaylistGateway()
        val repository = StreamingRepository(gateway)
        val importer = StreamingPlaylistImporter(repository)
        val summary = StreamingPlaylistImportSummary(
            provider = StreamingProviderName.QQ_MUSIC,
            playlistName = "镜像收藏",
            matchedTracks = listOf(track("qq-1"), track("qq-2")),
            unresolvedTracks = emptyList(),
            errors = emptyList()
        )

        val created = importer.createRemotePlaylist(summary)
        val capability = repository.providerCapabilities().single()

        assertEquals("qq-playlist", created.remotePlaylist?.providerPlaylistId)
        assertEquals(listOf("qq-1", "qq-2"), gateway.added.single())
        assertTrue(capability.supportsPlaylistCreate)
        assertTrue(capability.supportsPlaylistWrite)
        assertTrue(capability.supportsFavoritesWrite)
        assertFalse(capability.supportsAudioResolve)
        assertFalse(capability.supportsPlayback)
    }

    @Test
    fun qqMirrorPlaylistUsesIncrementalDiffRenameAndReorder() = runTest {
        val gateway = RecordingPlaylistGateway(
            existingTitle = "旧名称",
            existingTracks = listOf(track("old"), track("keep"))
        )
        val repository = StreamingRepository(gateway)
        val importer = StreamingPlaylistImporter(repository)

        importer.syncRemotePlaylist(
            provider = StreamingProviderName.QQ_MUSIC,
            providerPlaylistId = "qq-playlist",
            title = "新名称",
            desiredTracks = listOf(track("keep"), track("new"))
        )
        repository.deleteUserPlaylist(StreamingProviderName.QQ_MUSIC, "unused-mirror")

        assertEquals(listOf("新名称"), gateway.renamed)
        assertEquals(listOf("new"), gateway.added.single())
        assertEquals(listOf("old"), gateway.removed.single())
        assertEquals(listOf("keep", "new"), gateway.reordered.single())
        assertEquals(listOf("unused-mirror"), gateway.deleted)
        assertEquals(0, gateway.clearOrRebuildCalls)
    }

    private class RecordingPlaylistGateway(
        private val existingTitle: String = "镜像收藏",
        existingTracks: List<StreamingTrack> = emptyList()
    ) : StreamingGateway by RegistryStreamingGateway(StreamingProviderRegistry()) {
        private val currentTracks = existingTracks.toMutableList()
        val added = mutableListOf<List<String>>()
        val removed = mutableListOf<List<String>>()
        val renamed = mutableListOf<String>()
        val reordered = mutableListOf<List<String>>()
        val deleted = mutableListOf<String>()
        var clearOrRebuildCalls = 0

        override suspend fun providerCapabilities(): List<StreamingProviderCapability> = listOf(
            StreamingProviderCapability(
                provider = StreamingProviderName.QQ_MUSIC,
                displayName = "QQ 音乐",
                enabled = true,
                supportsSearch = true,
                supportsPlayback = false,
                supportsFavorites = true,
                supportsPlaylists = true,
                supportsPlaylistImport = true,
                supportsPlaylistReadSync = true,
                supportsPlaylistCreate = true,
                supportsPlaylistWrite = true,
                supportsPlaylistDelete = true,
                supportsPlaylistRename = true,
                supportsPlaylistReorder = true,
                supportsFavoritesRead = true,
                supportsFavoritesWrite = true,
                supportsAudioResolve = false,
                supportsAudioFallback = false,
                supportsAudioDownload = false,
                supportsAudioCache = false
            )
        )

        override suspend fun createUserPlaylist(provider: StreamingProviderName, title: String) =
            StreamingPlaylist(provider, "qq-playlist", title)

        override suspend fun playlist(request: StreamingPlaylistRequest) = StreamingPlaylistDetail(
            provider = request.provider,
            providerPlaylistId = request.providerPlaylistId,
            playlist = StreamingPlaylist(request.provider, request.providerPlaylistId, existingTitle),
            tracks = currentTracks.toList()
        )

        override suspend fun renameUserPlaylist(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            title: String
        ) {
            renamed += title
        }

        override suspend fun deleteUserPlaylist(provider: StreamingProviderName, providerPlaylistId: String) {
            deleted += providerPlaylistId
        }

        override suspend fun mutateUserPlaylistTracks(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            providerTrackIds: List<String>,
            add: Boolean
        ) {
            if (add) {
                added += providerTrackIds
                providerTrackIds.forEach { id ->
                    if (currentTracks.none { it.providerTrackId == id }) {
                        currentTracks += track(id)
                    }
                }
            } else {
                removed += providerTrackIds
                currentTracks.removeAll { it.providerTrackId in providerTrackIds }
            }
        }

        override suspend fun reorderUserPlaylistTracks(
            provider: StreamingProviderName,
            providerPlaylistId: String,
            orderedProviderTrackIds: List<String>
        ) {
            reordered += orderedProviderTrackIds
        }
    }

    private companion object {
        fun track(id: String) = StreamingTrack(
            provider = StreamingProviderName.QQ_MUSIC,
            providerTrackId = id,
            title = id,
            artist = "歌手"
        )
    }
}
