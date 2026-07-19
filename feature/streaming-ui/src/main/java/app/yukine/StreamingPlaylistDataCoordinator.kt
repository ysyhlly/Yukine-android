package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaylistImporter
import app.yukine.streaming.KugouIdentity
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingPlaylistSyncConflictResolver
import app.yukine.streaming.StreamingPlaylistSyncStore
import app.yukine.streaming.StreamingPlaylistSyncDirection
import app.yukine.streaming.StreamingPlaylistSyncSnapshot
import app.yukine.streaming.StreamingPlaylistSyncWinner
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingRepository
import app.yukine.streaming.StreamingTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Owns the data-side work for streaming playlist transfers. UI state and presentation remain in
 * StreamingViewModel, while repository paging and local-playlist mutations have one feature owner.
 */
internal class StreamingPlaylistDataCoordinator(
    private val repositoryProvider: () -> StreamingRepository,
    private val localOperationsProvider: () -> StreamingLocalPlaylistOperations?,
    private val ioDispatcherProvider: () -> CoroutineDispatcher
) {
    suspend fun loadPlaylistTracks(
        provider: StreamingProviderName,
        providerPlaylistId: String
    ): Pair<String, List<StreamingTrack>> {
        val repository = repositoryProvider()
        return withContext(ioDispatcherProvider()) {
            val tracks = ArrayList<StreamingTrack>()
            var playlistName: String? = null
            var page = 1
            var total: Int? = null
            while (true) {
                val detail = repository.playlist(
                    provider = provider,
                    providerPlaylistId = providerPlaylistId,
                    page = page,
                    pageSize = STREAMING_PLAYLIST_PAGE_SIZE,
                    useCache = false
                )
                if (playlistName.isNullOrBlank()) {
                    playlistName = detail.playlist?.title?.takeIf { it.isNotBlank() }
                }
                total = detail.total ?: total
                val remainingCapacity = STREAMING_PLAYLIST_MAX_TRACKS - tracks.size
                val acceptedTracks = if (remainingCapacity > 0) {
                    detail.tracks.take(remainingCapacity)
                } else {
                    emptyList()
                }
                tracks.addAll(acceptedTracks)

                val reachedTotal = total?.let { expected -> tracks.size >= expected } == true
                val reachedLocalPageCap = page >= STREAMING_PLAYLIST_MAX_PAGES
                val reachedLocalTrackCap = acceptedTracks.size < detail.tracks.size ||
                    tracks.size >= STREAMING_PLAYLIST_MAX_TRACKS
                if (!detail.hasMore || detail.tracks.isEmpty() || reachedTotal ||
                    reachedLocalPageCap || reachedLocalTrackCap
                ) {
                    break
                }
                page += 1
            }
            (playlistName?.takeIf { it.isNotBlank() }
                ?: "Streaming playlist $providerPlaylistId") to tracks
        }
    }

    suspend fun importTracksToLocal(
        playlistName: String,
        provider: StreamingProviderName,
        providerPlaylistId: String,
        tracks: List<StreamingTrack>,
        linkWhenProviderPlaylistIdBlank: Boolean
    ): StreamingLocalPlaylistImportResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        val result = withContext(ioDispatcherProvider()) {
            operations.importStreamingPlaylist(
                playlistName,
                provider,
                providerPlaylistId,
                tracks,
                linkWhenProviderPlaylistIdBlank
            )
        }
        return StreamingLocalPlaylistImportResult(
            playlistName = result.playlistName,
            playlistAddedCount = result.playlistAddedCount,
            empty = result.isEmpty
        )
    }

    suspend fun syncPlaylistToLocal(
        link: StreamingPlaylistSyncStore.LinkedPlaylist
    ): StreamingLocalPlaylistSyncResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        val repository = repositoryProvider()
        val (playlistTitle, tracks) = if (link.providerPlaylistId.isBlank()) {
            "Favorites" to repository.userLikedTracks(link.provider)
        } else {
            loadPlaylistTracks(link.provider, link.providerPlaylistId)
        }
        val result = withContext(ioDispatcherProvider()) {
            operations.syncStreamingPlaylist(link, tracks)
        }
        val observedAt = System.currentTimeMillis()
        val baseline = StreamingPlaylistSyncSnapshot(
            title = playlistTitle,
            orderedTrackIds = tracks.map { it.providerTrackId },
            updatedAtMs = observedAt
        )
        withContext(ioDispatcherProvider()) {
            operations.updatePlaylistSyncBaseline(
                link.localPlaylistId,
                baseline,
                observedAt,
                null,
                observedAt
            )
        }
        return result
    }

    suspend fun syncLinkedPlaylist(
        link: StreamingPlaylistSyncStore.LinkedPlaylist
    ): StreamingLocalPlaylistSyncResult {
        return when (link.direction) {
            StreamingPlaylistSyncDirection.REMOTE_TO_LOCAL -> syncPlaylistToLocal(link)
            StreamingPlaylistSyncDirection.LOCAL_TO_REMOTE -> syncPlaylistToRemote(link)
            StreamingPlaylistSyncDirection.BIDIRECTIONAL -> syncPlaylistBidirectionally(link)
        }
    }

    private suspend fun syncPlaylistToRemote(
        link: StreamingPlaylistSyncStore.LinkedPlaylist,
        snapshotOverride: StreamingLocalPlaylistSnapshot? = null
    ): StreamingLocalPlaylistSyncResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        val snapshot = snapshotOverride ?: withContext(ioDispatcherProvider()) {
            operations.localPlaylistSnapshot(link.localPlaylistId)
        } ?: return StreamingLocalPlaylistSyncResult(
            playlistId = link.localPlaylistId,
            empty = true,
            errorMessage = "Local playlist is unavailable"
        )
        val importer = StreamingPlaylistImporter(repositoryProvider())
        val summary = importer.importToStreaming(
            provider = link.provider,
            playlistName = snapshot.playlistName,
            localTracks = snapshot.tracks
        )
        if (summary.errors.isNotEmpty() || summary.unresolvedTracks.isNotEmpty()) {
            val problemCount = summary.errors.size + summary.unresolvedTracks.size
            return StreamingLocalPlaylistSyncResult(
                playlistId = link.localPlaylistId,
                empty = true,
                errorMessage = "$problemCount track(s) could not be matched; the remote playlist was not changed"
            )
        }
        importer.syncRemotePlaylist(
            provider = link.provider,
            providerPlaylistId = link.providerPlaylistId,
            title = snapshot.playlistName,
            desiredTracks = summary.matchedTracks
        )
        withContext(ioDispatcherProvider()) {
            val baseline = StreamingPlaylistSyncSnapshot(
                title = snapshot.playlistName,
                orderedTrackIds = summary.matchedTracks.map { it.providerTrackId },
                updatedAtMs = System.currentTimeMillis()
            )
            operations.updatePlaylistSyncBaseline(
                link.localPlaylistId,
                baseline,
                baseline.updatedAtMs,
                baseline.updatedAtMs,
                baseline.updatedAtMs
            )
        }
        return StreamingLocalPlaylistSyncResult(
            playlistId = link.localPlaylistId,
            syncedCount = snapshot.tracks.size,
            empty = false
        )
    }

    private suspend fun syncPlaylistBidirectionally(
        link: StreamingPlaylistSyncStore.LinkedPlaylist
    ): StreamingLocalPlaylistSyncResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        val repository = repositoryProvider()
        val canWrite = repository.providerCapabilities()
            .firstOrNull { it.provider == link.provider }
            ?.supportsPlaylistWrite == true
        if (!canWrite) {
            // Private account contracts are release-gated. A bidirectional link remains useful and
            // safe in read-only mode until its provider advertises verified write support.
            return syncPlaylistToLocal(link)
        }

        val local = withContext(ioDispatcherProvider()) {
            operations.localPlaylistSnapshot(link.localPlaylistId)
        } ?: return StreamingLocalPlaylistSyncResult(
            playlistId = link.localPlaylistId,
            empty = true,
            errorMessage = "Local playlist is unavailable"
        )
        val (remoteTitle, remoteTracks) = loadPlaylistTracks(
            link.provider,
            link.providerPlaylistId
        )
        val now = System.currentTimeMillis()
        val localTrackIds = canonicalLocalTrackIds(link.provider, local)
        val baseline = link.baseline
        val localSnapshot = StreamingPlaylistSyncSnapshot(
            title = local.playlistName,
            orderedTrackIds = localTrackIds,
            updatedAtMs = if (baseline?.let {
                    it.title != local.playlistName || it.orderedTrackIds != localTrackIds
                } == true
            ) now else link.localUpdatedAtMs
        )
        val remoteSnapshotWithoutObservation = StreamingPlaylistSyncSnapshot(
            title = remoteTitle,
            orderedTrackIds = remoteTracks.map { it.providerTrackId },
            updatedAtMs = link.remoteUpdatedAtMs
        )
        val remoteObservedAt = if (
            baseline?.fingerprint != null &&
            baseline.fingerprint != remoteSnapshotWithoutObservation.fingerprint
        ) {
            now
        } else {
            link.remoteObservedChangeAtMs
        }
        val winner = StreamingPlaylistSyncConflictResolver.resolve(
            baseline = baseline,
            local = localSnapshot,
            remote = remoteSnapshotWithoutObservation,
            remoteObservedChangeAtMs = remoteObservedAt
        )
        return when (winner) {
            StreamingPlaylistSyncWinner.LOCAL -> syncPlaylistToRemote(link, local)
            StreamingPlaylistSyncWinner.REMOTE -> {
                val result = withContext(ioDispatcherProvider()) {
                    operations.syncStreamingPlaylist(link, remoteTracks)
                }
                withContext(ioDispatcherProvider()) {
                    operations.updatePlaylistSyncBaseline(
                        link.localPlaylistId,
                        remoteSnapshotWithoutObservation,
                        remoteSnapshotWithoutObservation.updatedAtMs ?: remoteObservedAt,
                        remoteSnapshotWithoutObservation.updatedAtMs,
                        remoteObservedAt
                    )
                }
                result
            }
            StreamingPlaylistSyncWinner.NONE -> {
                withContext(ioDispatcherProvider()) {
                    operations.markPlaylistSynced(link.localPlaylistId)
                }
                StreamingLocalPlaylistSyncResult(
                    playlistId = link.localPlaylistId,
                    syncedCount = remoteTracks.size,
                    empty = remoteTracks.isEmpty()
                )
            }
        }
    }

    private fun canonicalLocalTrackIds(
        targetProvider: StreamingProviderName,
        snapshot: StreamingLocalPlaylistSnapshot
    ): List<String> = snapshot.tracks.map { track ->
        val provider = StreamingPlaybackAdapter.streamingProviderName(track.dataPath)
        val providerTrackId = StreamingPlaybackAdapter.providerTrackId(track.dataPath)
        when {
            targetProvider == StreamingProviderName.KUGOU &&
                (provider == StreamingProviderName.KUGOU ||
                    provider == StreamingProviderName.LUOXUE) ->
                KugouIdentity.canonicalTrackId(providerTrackId) ?: "local:${track.id}"
            provider == targetProvider && providerTrackId.isNotBlank() -> providerTrackId
            provider != null && providerTrackId.isNotBlank() ->
                "${provider.wireName}:$providerTrackId"
            else -> "local:${track.id}"
        }
    }

    suspend fun ensureLoginPlaylist(
        playlistName: String,
        provider: StreamingProviderName
    ): StreamingLoginPlaylistResult {
        val operations = localOperationsProvider()
            ?: error("Streaming local playlist operations are not bound")
        return withContext(ioDispatcherProvider()) {
            operations.ensureStreamingLoginPlaylist(playlistName, provider)
        }
    }

    suspend fun importPlaylistToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>
    ): app.yukine.streaming.StreamingPlaylistImportSummary {
        val importer = StreamingPlaylistImporter(repositoryProvider())
        val summary = importer.importToStreaming(
            provider,
            playlistName,
            localTracks
        )
        return importer.createRemotePlaylist(summary)
    }

    companion object {
        const val STREAMING_PLAYLIST_PAGE_SIZE = 2_000
        const val STREAMING_PLAYLIST_MAX_PAGES = 50
        const val STREAMING_PLAYLIST_MAX_TRACKS =
            STREAMING_PLAYLIST_PAGE_SIZE * STREAMING_PLAYLIST_MAX_PAGES
    }
}
