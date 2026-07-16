package app.yukine

import app.yukine.common.StreamingDataPathMetadata
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName

/**
 * Resolves the owner of a local playlist without making the UI depend on provider state.
 *
 * A persisted playlist link is authoritative. The track metadata fallback keeps playlists
 * imported before links were introduced (or before a delayed link write becomes visible) in the
 * correct provider folder. Mixed local/provider playlists remain local because they do not have a
 * single unambiguous remote owner.
 */
internal object PlaylistSourceResolver {
    @JvmStatic
    fun resolve(
        linkedProvider: StreamingProviderName?,
        tracks: List<Track>?
    ): StreamingProviderName? {
        linkedProvider?.let { return it }
        val playlistTracks = tracks.orEmpty()
        if (playlistTracks.isEmpty()) return null

        var resolved: StreamingProviderName? = null
        for (track in playlistTracks) {
            val provider = StreamingDataPathMetadata.provider(track.dataPath) ?: return null
            if (resolved == null) {
                resolved = provider
            } else if (resolved != provider) {
                return null
            }
        }
        return resolved
    }
}
