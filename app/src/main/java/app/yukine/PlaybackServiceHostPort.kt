package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackCommands
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.playback.state.PlaybackStateListener
import app.yukine.together.TogetherSessionHostPort

interface PlaybackServiceHostPort : PlaybackCommands, SettingsPlaybackServicePort {
    fun snapshot(): PlaybackStateSnapshot?

    fun queueSnapshot(): List<Track>

    /** False when queue mutations are locked (e.g. Together guest). Default true for fakes. */
    fun canMutateQueue(): Boolean = true

    fun registerListener(listener: PlaybackStateListener?)

    fun unregisterListener(listener: PlaybackStateListener?)

    fun setAppVisible(visible: Boolean)

    fun realtimeBeat(): Float

    fun realtimeBands(): FloatArray

    fun realtimeTransientBeat(): Float

    fun queueSize(): Int

    fun queueTrackAt(index: Int): Track?

    fun togetherSessionHost(): TogetherSessionHostPort
}
