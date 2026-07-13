package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal object PlaybackStateUpdateController {
    data class Result(
        val loadLyrics: Boolean,
        val refreshCollections: Boolean,
        val showError: Boolean,
        val lastHistoryRefreshTrackId: Long
    )

    fun resolve(
        next: PlaybackStateSnapshot,
        currentLyricsTrackId: Long,
        lastHistoryRefreshTrackId: Long
    ): Result {
        val currentTrackId = next.currentTrack?.id ?: -1L
        val loadLyrics = currentTrackId != currentLyricsTrackId
        val refreshCollections = next.playing &&
            next.currentTrack != null &&
            next.currentTrack.id != lastHistoryRefreshTrackId
        val nextHistoryRefreshTrackId = if (refreshCollections) {
            next.currentTrack.id
        } else {
            lastHistoryRefreshTrackId
        }
        val showError = next.errorMessage.isNotEmpty()
        return Result(
            loadLyrics,
            refreshCollections,
            showError,
            nextHistoryRefreshTrackId
        )
    }
}
