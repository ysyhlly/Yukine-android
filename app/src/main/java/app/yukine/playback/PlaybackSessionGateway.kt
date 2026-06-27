package app.yukine.playback

import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import app.yukine.playback.manager.PlaybackSessionManager

internal class PlaybackSessionGateway(
    private val sessionManager: PlaybackSessionManager?
) {
    fun session(): MediaLibrarySession? = sessionManager?.session()

    fun platformToken() = session()?.platformToken

    fun bind() {
        sessionManager?.bind()
    }

    fun refresh() {
        sessionManager?.refreshPlayer()
    }

    fun release() {
        sessionManager?.release()
    }
}
