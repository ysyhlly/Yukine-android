package app.yukine

import app.yukine.model.Track

fun interface PlaybackController {
    fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi?
}

internal class PlaybackStartControllerAdapter(
    private val streamingTrackListResolver: StreamingTrackListResolver,
    private val playbackTrackListPlayer: PlaybackTrackListPlayer,
    private val playbackActionResultApplier: QueuePlaybackActionResultApplier
) : PlaybackController {
    override fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi? {
        if (streamingTrackListResolver.resolve(tracks, index)) {
            return null
        }
        val result = playbackTrackListPlayer.play(tracks, index)
        playbackActionResultApplier.apply(result)
        return result
    }
}
