package app.yukine

import app.yukine.model.Track
import app.yukine.ui.PlaylistTrackUiState
import app.yukine.ui.QueueTrackUiState
import app.yukine.ui.TrackRowUiState

object TrackRowStateFactory {
    @JvmStatic
    fun trackRow(
        track: Track,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        detail: String,
        showPlaylistAction: Boolean,
        key: String = track.id.toString(),
        favoritePendingIds: Set<Long> = emptySet()
    ): TrackRowUiState = TrackRowUiState(
        id = track.id,
        title = track.title,
        subtitle = track.subtitle(),
        detail = detail.ifBlank { track.audioSpecSummary() },
        duration = Track.formatDuration(track.durationMs),
        albumArtUri = track.albumArtUri,
        current = isCurrent(track, currentTrack),
        favorite = favoriteIds.contains(track.id),
        showPlaylistAction = showPlaylistAction,
        key = key,
        favoritePending = track.id in favoritePendingIds
    )

    @JvmStatic
    fun queueRow(
        key: String,
        track: Track,
        currentTrack: Track?,
        favoriteIds: Set<Long>
    ): QueueTrackUiState = QueueTrackUiState(
        key,
        track.id,
        track.title,
        track.subtitle(),
        track.audioSpecSummary(),
        Track.formatDuration(track.durationMs),
        track.albumArtUri,
        isCurrent(track, currentTrack),
        favoriteIds.contains(track.id)
    )

    @JvmStatic
    fun playlistRow(
        key: String,
        track: Track,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        canMoveUp: Boolean,
        canMoveDown: Boolean
    ): PlaylistTrackUiState = PlaylistTrackUiState(
        key,
        track.id,
        track.title,
        track.subtitle(),
        track.audioSpecSummary(),
        Track.formatDuration(track.durationMs),
        track.albumArtUri,
        isCurrent(track, currentTrack),
        favoriteIds.contains(track.id),
        canMoveUp,
        canMoveDown
    )

    private fun isCurrent(track: Track, currentTrack: Track?): Boolean =
        currentTrack != null && currentTrack.id == track.id
}
