package app.yukine

internal class MainPlaybackServiceHost(
    private val playbackSpeedSource: PlaybackSpeedSource,
    private val appVolumeSource: AppVolumeSource,
    private val concurrentPlaybackSource: ConcurrentPlaybackSource,
    private val statusBarLyricsSource: StatusBarLyricsSource,
    private val systemMediaLyricsTitleSource: SystemMediaLyricsTitleSource,
    private val playbackRestoreSource: PlaybackRestoreSource,
    private val replayGainSource: ReplayGainSource,
    private val playbackServiceAttacher: PlaybackServiceAttacher,
    private val playbackServiceClearer: PlaybackServiceClearer,
    private val playbackStoreResetter: PlaybackStoreResetter,
    private val pendingTracksPlayer: PendingTracksPlayer
) : PlaybackServiceHostController.Host {
    fun interface PlaybackSpeedSource {
        fun playbackSpeed(): Float
    }

    fun interface AppVolumeSource {
        fun appVolume(): Float
    }

    fun interface ConcurrentPlaybackSource {
        fun concurrentPlaybackEnabled(): Boolean
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

    fun interface PlaybackServiceAttacher {
        fun attachPlaybackService(service: PlaybackServiceHostPort)
    }

    fun interface PlaybackServiceClearer {
        fun clearPlaybackService()
    }

    fun interface PlaybackStoreResetter {
        fun resetPlaybackStore()
    }

    fun interface PendingTracksPlayer {
        fun playPendingTracksIfNeeded()
    }

    override fun playbackSpeed(): Float = playbackSpeedSource.playbackSpeed()

    override fun appVolume(): Float = appVolumeSource.appVolume()

    override fun concurrentPlaybackEnabled(): Boolean = concurrentPlaybackSource.concurrentPlaybackEnabled()

    override fun statusBarLyricsEnabled(): Boolean = statusBarLyricsSource.statusBarLyricsEnabled()

    override fun systemMediaLyricsTitleEnabled(): Boolean =
        systemMediaLyricsTitleSource.systemMediaLyricsTitleEnabled()

    override fun playbackRestoreEnabled(): Boolean = playbackRestoreSource.playbackRestoreEnabled()

    override fun replayGainEnabled(): Boolean = replayGainSource.replayGainEnabled()

    override fun attachPlaybackService(service: PlaybackServiceHostPort) {
        playbackServiceAttacher.attachPlaybackService(service)
        service.setAppVisible(true)
    }

    override fun clearPlaybackService() {
        playbackServiceClearer.clearPlaybackService()
    }

    override fun resetPlaybackStore() {
        playbackStoreResetter.resetPlaybackStore()
    }

    override fun playPendingTracksIfNeeded() {
        pendingTracksPlayer.playPendingTracksIfNeeded()
    }

}
