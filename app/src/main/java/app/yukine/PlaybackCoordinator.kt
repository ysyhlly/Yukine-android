package app.yukine

import app.yukine.model.Track
import app.yukine.playback.EchoPlaybackService

internal class PlaybackCoordinator(
    private val playbackViewModel: PlaybackViewModel,
    private val nowPlayingViewModel: NowPlayingViewModel,
    private val playbackStore: MainPlaybackStore,
    private val settingsStore: MainSettingsStore,
    private val statusMessageController: StatusMessageController,
    val playbackActionController: PlaybackActionController,
    val playbackStartController: PlaybackStartController,
    val playbackServiceHostController: PlaybackServiceHostController,
    val playbackServiceConnectionController: PlaybackServiceConnectionController,
    val playbackStateUpdateController: PlaybackStateUpdateController,
    val playbackStateEventController: PlaybackStateEventController,
    private val listener: Listener
) {

    interface Listener {
        fun currentPlaybackService(): EchoPlaybackService?
        fun renderSelectedTab()
        fun renderNowBar()
    }

    private var pendingPlaybackTracks: List<Track> = emptyList()
    private var pendingPlaybackIndex: Int = -1

    fun playTrackListFromHost(tracks: List<Track>?, index: Int) {
        if (tracks == null || tracks.isEmpty()) return
        playbackStartController.playTrackList(tracks, index)
    }

    fun playTrackListFromHostOrDefer(tracks: List<Track>?, index: Int) {
        val svc = listener.currentPlaybackService()
        if (svc == null) {
            pendingPlaybackTracks = if (tracks.isNullOrEmpty()) emptyList() else ArrayList(tracks)
            pendingPlaybackIndex = index
            statusMessageController.setStatus(
                AppLanguage.text(
                    settingsStore.languageMode() ?: AppLanguage.MODE_SYSTEM,
                    "streaming.resolving"
                )
            )
            return
        }
        playbackStartController.playTrackList(tracks, index)
    }

    fun applyPlaybackActionResult(result: PlaybackActionResultUi?) {
        if (result == null) return
        result.snapshot?.let { playbackStore.replaceSnapshot(it) }
        val status = result.status
        if (!status.isNullOrBlank()) statusMessageController.setStatus(status)
        if (result.publishPlaybackState) playbackStore.publish(listener.currentPlaybackService())
        if (result.renderNowBar) listener.renderNowBar()
        if (result.renderSelectedTab) listener.renderSelectedTab()
        if (result.navigateNow) listener.renderSelectedTab()
    }

    fun flushPendingPlayback() {
        if (pendingPlaybackTracks.isNotEmpty() && pendingPlaybackIndex >= 0) {
            playbackStartController.playTrackList(pendingPlaybackTracks, pendingPlaybackIndex)
            pendingPlaybackTracks = emptyList()
            pendingPlaybackIndex = -1
        }
    }

    fun bind() {
        playbackServiceConnectionController.bind()
    }

    fun release() {
        playbackServiceConnectionController.release()
    }

    fun onResume() {
        listener.currentPlaybackService()?.setAppVisible(true)
    }

    fun onPause() {
        listener.currentPlaybackService()?.setAppVisible(false)
    }
}
