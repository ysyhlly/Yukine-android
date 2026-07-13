package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot

internal fun interface MainPlaybackStateEventListenerFactory {
    fun create(
        currentLyricsTrackIdSource: MainPlaybackStateEventListener.CurrentLyricsTrackIdSource,
        playbackSettingsSaver: MainPlaybackStateEventListener.PlaybackSettingsSaver,
        lyricsLoader: MainPlaybackStateEventListener.LyricsLoader,
        collectionsLoader: MainPlaybackStateEventListener.CollectionsLoader,
        nextStreamingTrackPreResolver: MainPlaybackStateEventListener.NextStreamingTrackPreResolver,
        streamingBufferingRecoveryHandler: MainPlaybackStateEventListener.StreamingBufferingRecoveryHandler,
        currentStreamingTrackResolver: MainPlaybackStateEventListener.CurrentStreamingTrackResolver,
        statusSink: MainPlaybackStateEventListener.StatusSink
    ): PlaybackStateEventController.Listener
}

internal class MainPlaybackStateEventListener(
    private val currentLyricsTrackIdSource: CurrentLyricsTrackIdSource,
    private val playbackSettingsSaver: PlaybackSettingsSaver,
    private val lyricsLoader: LyricsLoader,
    private val collectionsLoader: CollectionsLoader,
    private val nextStreamingTrackPreResolver: NextStreamingTrackPreResolver,
    private val streamingBufferingRecoveryHandler: StreamingBufferingRecoveryHandler,
    private val currentStreamingTrackResolver: CurrentStreamingTrackResolver,
    private val statusSink: StatusSink
) : PlaybackStateEventController.Listener {
    fun interface CurrentLyricsTrackIdSource {
        fun currentLyricsTrackId(): Long
    }

    fun interface PlaybackSettingsSaver {
        fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float)
    }

    fun interface LyricsLoader {
        fun loadLyrics(track: Track?)
    }

    fun interface CollectionsLoader {
        fun loadCollections()
    }

    fun interface NextStreamingTrackPreResolver {
        fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot)
    }

    fun interface StreamingBufferingRecoveryHandler {
        fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot)
    }

    fun interface CurrentStreamingTrackResolver {
        fun resolveCurrentStreamingTrackIfNeeded(): Boolean
    }

    fun interface StatusSink {
        fun setStatus(status: String)
    }

    override fun currentLyricsTrackId(): Long = currentLyricsTrackIdSource.currentLyricsTrackId()

    override fun savePlaybackSettings(playbackSpeed: Float, appVolume: Float) {
        playbackSettingsSaver.savePlaybackSettings(playbackSpeed, appVolume)
    }

    override fun loadLyrics(track: Track?) {
        lyricsLoader.loadLyrics(track)
    }

    override fun loadCollections() {
        collectionsLoader.loadCollections()
    }

    override fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot) {
        nextStreamingTrackPreResolver.preResolveNextStreamingTrack(snapshot)
    }

    override fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot) {
        streamingBufferingRecoveryHandler.recoverStreamingBuffering(snapshot)
    }

    override fun resolveCurrentStreamingTrackIfNeeded(): Boolean =
        currentStreamingTrackResolver.resolveCurrentStreamingTrackIfNeeded()

    override fun setStatus(status: String) {
        statusSink.setStatus(status)
    }
}
