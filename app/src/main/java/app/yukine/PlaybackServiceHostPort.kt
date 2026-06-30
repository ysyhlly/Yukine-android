package app.yukine

interface PlaybackServiceHostPort : NowPlayingPlaybackServicePort, SettingsPlaybackServicePort {
    fun setAppVisible(visible: Boolean)

    fun realtimeBeat(): Float

    fun realtimeBands(): FloatArray
}
