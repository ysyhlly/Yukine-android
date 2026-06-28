package app.yukine

import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.queue.QueueIntent
import app.yukine.queue.QueueViewModel

internal class NowPlayingQueueCoordinator(
    private val nowPlayingViewModel: NowPlayingViewModel,
    private val queueViewModel: QueueViewModel,
    val nowPlayingStateController: NowPlayingStateController,
    val queueRenderController: QueueRenderController,
    val queueActionController: QueueActionController,
    private val playbackStore: MainPlaybackStore,
    private val libraryStore: MainLibraryStore,
    private val settingsStore: MainSettingsStore,
    private val statusMessageController: StatusMessageController,
    private val listener: Listener
) {

    interface Listener {
        fun playTrackListFromHost(tracks: List<Track>, index: Int)
        fun showAddToPlaylist(track: Track)
        fun handleAppBack(): Boolean
        fun navigateToTab(tabKey: String, userInitiated: Boolean, renderImmediately: Boolean)
        fun downloadTrack(track: Track)
        fun shareTrack(track: Track)
        fun switchNowPlayingSource(effect: NowPlayingEffect.SwitchSource)
    }

    fun handleNowPlayingEvent(event: NowPlayingEvent?) {
        if (event == null) return
        nowPlayingStateController.publish(playbackStore.snapshot())
        nowPlayingViewModel.onEvent(event)
        handleNowPlayingEffects()
    }

    fun handleNowPlayingEffects() {
        val effects = nowPlayingViewModel.drainEffects() ?: return
        if (effects.isEmpty()) return
        for (effect in effects) {
            when (effect) {
                is NowPlayingEffect.OpenQueue ->
                    listener.navigateToTab(MainRoutes.TAB_QUEUE, true, true)
                is NowPlayingEffect.OpenAddToPlaylist ->
                    listener.showAddToPlaylist(effect.track)
                is NowPlayingEffect.ShareTrack ->
                    listener.shareTrack(effect.track)
                is NowPlayingEffect.DownloadTrack ->
                    listener.downloadTrack(effect.track)
                is NowPlayingEffect.SwitchSource ->
                    listener.switchNowPlayingSource(effect)
                is NowPlayingEffect.ShowMessage ->
                    statusMessageController.setStatus(effect.message)
                else -> {}
            }
        }
    }

    fun bindQueueIntentListener() {
        queueViewModel.bindIntentListener { intent ->
            when (intent) {
                is QueueIntent.PlayAt ->
                    listener.playTrackListFromHost(intent.tracks, intent.index)
                is QueueIntent.ToggleFavorite ->
                    nowPlayingViewModel.onEvent(NowPlayingEvent.ToggleFavorite)
                is QueueIntent.AddToPlaylist ->
                    listener.showAddToPlaylist(intent.track)
                is QueueIntent.Remove ->
                    queueActionController.removeQueueTrack(intent.track)
                is QueueIntent.Move ->
                    queueActionController.moveQueueTrack(intent.fromIndex, intent.toIndex)
                is QueueIntent.ClearQueue ->
                    queueActionController.confirmClearQueue()
                is QueueIntent.Back ->
                    listener.handleAppBack()
                else -> {}
            }
        }
    }

    fun bindQueueViewModelInputs(
        queueSnapshot: List<Track>,
        snapshot: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>
    ) {
        queueViewModel.bind(
            queueSnapshot,
            snapshot,
            favoriteIds,
            settingsStore.languageMode() ?: AppLanguage.MODE_SYSTEM
        )
    }

    fun renderQueue(
        queueSnapshot: List<Track>,
        snapshot: PlaybackStateSnapshot?,
        favoriteIds: Set<Long>,
        languageMode: String
    ) {
        queueRenderController.render(queueSnapshot, snapshot, favoriteIds, languageMode)
    }
}
