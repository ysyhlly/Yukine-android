package app.yukine

import app.yukine.model.Track
import app.yukine.ui.PlaylistTrackUiState
import app.yukine.ui.QueueTrackUiState
import app.yukine.ui.TrackRowUiState

internal object TrackRowStateFactory {
    @JvmStatic
    fun trackRow(
        track: Track,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        detail: String,
        showPlaylistAction: Boolean,
        key: String = track.id.toString()
    ): TrackRowUiState = TrackRowUiState(
        track.id,
        track.title,
        track.subtitle(),
        detail.ifBlank { track.audioSpecSummary() },
        Track.formatDuration(track.durationMs),
        track.albumArtUri,
        isCurrent(track, currentTrack),
        favoriteIds.contains(track.id),
        showPlaylistAction,
        key
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
