package app.echo.next

import app.echo.next.model.Track
import app.echo.next.ui.PlaylistTrackUiState
import app.echo.next.ui.QueueTrackUiState
import app.echo.next.ui.TrackRowUiState

internal object TrackRowStateFactory {
    @JvmStatic
    fun trackRow(
        track: Track,
        currentTrack: Track?,
        favoriteIds: Set<Long>,
        detail: String,
        showPlaylistAction: Boolean
    ): TrackRowUiState = TrackRowUiState(
        track.id,
        track.title,
        track.subtitle(),
        detail.ifBlank { track.audioSpecSummary() },
        Track.formatDuration(track.durationMs),
        track.albumArtUri,
        isCurrent(track, currentTrack),
        favoriteIds.contains(track.id),
        showPlaylistAction
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
