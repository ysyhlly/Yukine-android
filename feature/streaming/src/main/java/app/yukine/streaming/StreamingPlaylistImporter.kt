package app.yukine.streaming

import app.yukine.model.Track

/**
 * Imports a local Yukine playlist (or favorites collection) into a streaming provider by
 * matching each local track via search and assembling a streaming playlist.
 *
 * The result currently lives in-memory: it surfaces the matched [StreamingTrack]s plus a list of
 * unresolved local tracks, which the caller can render in the streaming search view, queue for
 * playback, or hand off to a future "create remote playlist" gateway endpoint.
 *
 * When the gateway exposes a playlist-creation endpoint, [createRemotePlaylist] can be called to
 * push the matched tracks onto the user's streaming account. Until then, the import returns a
 * locally-assembled [StreamingPlaylistImportSummary] that the UI can use to show the user what
 * was matched.
 */
class StreamingPlaylistImporter(
    private val repository: StreamingRepository
) {

    suspend fun importToStreaming(
        provider: StreamingProviderName,
        playlistName: String,
        localTracks: List<Track>
    ): StreamingPlaylistImportSummary {
        if (!ProviderRolePolicy.canSyncPlaylists(provider.wireName)) {
            return StreamingPlaylistImportSummary(
                provider = provider,
                playlistName = playlistName,
                matchedTracks = emptyList(),
                unresolvedTracks = localTracks,
                errors = listOf("Provider ${provider.wireName} is not an identity playlist source")
            )
        }
        val matched = ArrayList<StreamingTrack>()
        val unresolved = ArrayList<Track>()
        val errors = ArrayList<String>()
        for (track in localTracks) {
            val query = StreamingTrackMatchPolicy.searchQuery(track)
            if (query.isBlank()) {
                unresolved.add(track)
                continue
            }
            try {
                val result = repository.search(
                    provider = provider,
                    query = query,
                    mediaTypes = setOf(StreamingMediaType.TRACK),
                    page = 1,
                    pageSize = 5
                )
                val streamingTrack = StreamingTrackMatchPolicy.pickBestCandidate(track, result.tracks)
                if (streamingTrack != null) {
                    matched.add(streamingTrack)
                } else {
                    unresolved.add(track)
                }
            } catch (error: Exception) {
                unresolved.add(track)
                error.message
                    ?.takeIf { it.isNotBlank() }
                    ?.let { errors.add("${track.title}: $it") }
            }
        }
        return StreamingPlaylistImportSummary(
            provider = provider,
            playlistName = playlistName,
            matchedTracks = matched.toList(),
            unresolvedTracks = unresolved.toList(),
            errors = errors.toList()
        )
    }

    suspend fun createRemotePlaylist(summary: StreamingPlaylistImportSummary): StreamingPlaylistImportSummary {
        if (!ProviderRolePolicy.canSyncPlaylists(summary.provider.wireName)) return summary
        if (summary.matchedTracks.isEmpty()) return summary
        val capability = repository.providerCapabilities().firstOrNull { it.provider == summary.provider }
        if (capability?.supportsPlaylistCreate != true || capability.supportsPlaylistWrite.not()) return summary
        val playlist = repository.createUserPlaylist(summary.provider, summary.playlistName)
        repository.mutateUserPlaylistTracks(
            summary.provider,
            playlist.providerPlaylistId,
            summary.matchedTracks.map { it.providerTrackId }.distinct(),
            add = true
        )
        return summary.copy(remotePlaylist = playlist)
    }

    /** Incrementally reconciles a mirror playlist without clearing or rebuilding it. */
    suspend fun syncRemotePlaylist(
        provider: StreamingProviderName,
        providerPlaylistId: String,
        title: String,
        desiredTracks: List<StreamingTrack>
    ) {
        if (!ProviderRolePolicy.canSyncPlaylists(provider.wireName)) return
        val existing = repository.playlist(provider, providerPlaylistId, pageSize = 2_000, useCache = false)
        val existingIds = existing.tracks.map { it.providerTrackId }
        val desiredIds = desiredTracks.map { it.providerTrackId }.filter { it.isNotBlank() }.distinct()
        if (existing.playlist?.title != title) repository.renameUserPlaylist(provider, providerPlaylistId, title)
        val add = desiredIds.filterNot(existingIds::contains)
        val remove = existingIds.filterNot(desiredIds::contains)
        if (add.isNotEmpty()) repository.mutateUserPlaylistTracks(provider, providerPlaylistId, add, true)
        if (remove.isNotEmpty()) repository.mutateUserPlaylistTracks(provider, providerPlaylistId, remove, false)
        if (desiredIds.isNotEmpty() && desiredIds != existingIds) {
            repository.reorderUserPlaylistTracks(provider, providerPlaylistId, desiredIds)
        }
    }

    private fun buildSearchQuery(track: Track): String {
        val title = sanitize(track.title)
        val artist = sanitize(track.artist)
        return when {
            title.isNotBlank() && artist.isNotBlank() -> "$title $artist"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> ""
        }
    }

    private fun pickBestCandidate(local: Track, candidates: List<StreamingTrack>): StreamingTrack? {
        if (candidates.isEmpty()) return null
        val titleNeedle = sanitize(local.title).lowercase()
        val artistNeedle = sanitize(local.artist).lowercase()
        // Prefer candidates where both title and artist match (case-insensitive).
        val exact = candidates.firstOrNull { candidate ->
            val candidateTitle = sanitize(candidate.title).lowercase()
            val candidateArtist = sanitize(candidate.artist).lowercase()
            candidateTitle == titleNeedle && candidateArtist == artistNeedle
        }
        if (exact != null) return exact
        // Otherwise, prefer the first candidate whose title contains the local title.
        val titleMatch = candidates.firstOrNull { candidate ->
            val candidateTitle = sanitize(candidate.title).lowercase()
            titleNeedle.isNotBlank() && candidateTitle.contains(titleNeedle)
        }
        if (titleMatch != null) return titleMatch
        // Fallback: just use the top result.
        return candidates.first()
    }

    private fun sanitize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val trimmed = value.trim()
        if (trimmed == "未知歌曲" || trimmed == "未知艺人" || trimmed == "未知专辑") return ""
        return trimmed
    }
}
