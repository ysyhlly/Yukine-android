package app.yukine

internal class MainPlaybackServiceHost(
    private val playbackSpeedSource: PlaybackSpeedSource,
    private val appVolumeSource: AppVolumeSource,
    private val statusBarLyricsSource: StatusBarLyricsSource,
    private val systemMediaLyricsTitleSource: SystemMediaLyricsTitleSource,
    private val playbackRestoreSource: PlaybackRestoreSource,
    private val replayGainSource: ReplayGainSource,
    private val playbackStoreResetter: PlaybackStoreResetter,
    private val pendingTracksPlayer: PendingTracksPlayer
) : PlaybackServiceHostController.Host {
    fun interface PlaybackSpeedSource {
        fun playbackSpeed(): Float
    }

    fun interface AppVolumeSource {
        fun appVolume(): Float
    }

    fun interface StatusBarLyricsSource {
        fun statusBarLyricsEnabled(): Boolean
    }

    fun interface SystemMediaLyricsTitleSource {
        fun systemMediaLyricsTitleEnabled(): Boolean
    }

    fun interface PlaybackRestoreSource {
        fun playbackRestoreEnabled(): Boolean
    }

    fun interface ReplayGainSource {
        fun replayGainEnabled(): Boolean
    }

    fun interface PlaybackStoreResetter {
        fun resetPlaybackStore()
    }

    fun interface PendingTracksPlayer {
        fun playPendingTracksIfNeeded()
    }

    override fun playbackSpeed(): Float = playbackSpeedSource.playbackSpeed()

    override fun appVolume(): Float = appVolumeSource.appVolume()

    override fun statusBarLyricsEnabled(): Boolean = statusBarLyricsSource.statusBarLyricsEnabled()

    override fun systemMediaLyricsTitleEnabled(): Boolean =
        systemMediaLyricsTitleSource.systemMediaLyricsTitleEnabled()

    override fun playbackRestoreEnabled(): Boolean = playbackRestoreSource.playbackRestoreEnabled()

    override fun replayGainEnabled(): Boolean = replayGainSource.replayGainEnabled()

    override fun resetPlaybackStore() {
        playbackStoreResetter.resetPlaybackStore()
    }

    override fun playPendingTracksIfNeeded() {
        pendingTracksPlayer.playPendingTracksIfNeeded()
    }

}
