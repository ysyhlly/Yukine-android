package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal class PlaybackStateUpdateController {
    data class Result(
        val loadLyrics: Boolean,
        val refreshCollections: Boolean,
        val renderSelectedTab: Boolean,
        val updateNowPlaying: Boolean,
        val showError: Boolean,
        val lastHistoryRefreshTrackId: Long
    )

    fun resolve(
        selectedTab: String,
        previous: PlaybackStateSnapshot?,
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
        val renderSelectedTab = PlaybackRenderPolicy.shouldRenderForPlaybackChange(selectedTab, previous, next)
        val updateNowPlaying = !renderSelectedTab && MainRoutes.TAB_NOW == selectedTab
        val showError = next.errorMessage.isNotEmpty()
        return Result(
            loadLyrics,
            refreshCollections,
            renderSelectedTab,
            updateNowPlaying,
            showError,
            nextHistoryRefreshTrackId
        )
    }
}
