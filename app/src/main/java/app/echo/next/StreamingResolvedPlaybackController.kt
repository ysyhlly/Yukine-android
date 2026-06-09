package app.echo.next

import app.echo.next.model.Track
import app.echo.next.streaming.StreamingProviderName
import java.util.ArrayList

internal class StreamingResolvedPlaybackController(
    private val player: Player,
    private val loginListener: LoginListener? = null
) : StreamingActionsController.Listener {
    interface Player {
        fun playTrackList(tracks: List<Track>, index: Int)
    }

    interface LoginListener {
        fun onStreamingLoginSuccess(provider: StreamingProviderName)
    }

    override fun playResolvedTrack(track: Track) {
        player.playTrackList(ArrayList(listOf(track)), 0)
    }

    override fun onStreamingLoginSuccess(provider: StreamingProviderName) {
        loginListener?.onStreamingLoginSuccess(provider)
    }
}
