package app.yukine

import app.yukine.playback.PlaybackStateSnapshot

internal object PlaybackRenderPolicy {
    fun shouldRenderForPlaybackChange(
        selectedTab: String,
        previous: PlaybackStateSnapshot?,
        next: PlaybackStateSnapshot
    ): Boolean {
        if (
            MainRoutes.TAB_QUEUE != selectedTab &&
            MainRoutes.TAB_LIBRARY != selectedTab &&
            MainRoutes.TAB_NOW != selectedTab &&
            MainRoutes.TAB_COLLECTIONS != selectedTab
        ) {
            return false
        }
        if (previous == null) {
            return true
        }
        val previousTrackId = previous.currentTrack?.id ?: -1L
        val nextTrackId = next.currentTrack?.id ?: -1L
        if (previousTrackId != nextTrackId) {
            return true
        }
        if (previous.currentIndex != next.currentIndex || previous.queueSize != next.queueSize) {
            return true
        }
        if (previous.durationMs != next.durationMs || previous.playing != next.playing || previous.preparing != next.preparing) {
            return true
        }
        if (previous.shuffleEnabled != next.shuffleEnabled || previous.repeatMode != next.repeatMode) {
            return true
        }
        return previous.errorMessage != next.errorMessage
    }
}
