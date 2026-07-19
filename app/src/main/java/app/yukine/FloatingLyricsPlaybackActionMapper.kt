package app.yukine

import app.yukine.playback.service.PlaybackServiceActions

internal object FloatingLyricsPlaybackActionMapper {
    fun serviceAction(
        action: FloatingLyricsOverlayAction,
        playing: Boolean
    ): String? = when (action) {
        FloatingLyricsOverlayAction.PlayPause ->
            if (playing) PlaybackServiceActions.PAUSE else PlaybackServiceActions.PLAY
        FloatingLyricsOverlayAction.Previous -> PlaybackServiceActions.PREVIOUS
        FloatingLyricsOverlayAction.Next -> PlaybackServiceActions.NEXT
        else -> null
    }
}
