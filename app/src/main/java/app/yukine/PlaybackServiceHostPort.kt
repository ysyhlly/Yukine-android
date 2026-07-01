package app.yukine

import app.yukine.playback.PlaybackStateListener

interface PlaybackServiceHostPort : NowPlayingPlaybackServicePort, SettingsPlaybackServicePort {
    fun registerListener(listener: PlaybackStateListener?)

    fun unregisterListener(listener: PlaybackStateListener?)

    fun setAppVisible(visible: Boolean)

    fun realtimeBeat(): Float

    fun realtimeBands(): FloatArray
}
