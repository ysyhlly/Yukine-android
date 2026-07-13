package app.yukine

import android.content.Context
import android.os.Handler
import app.yukine.model.Track
import app.yukine.playback.PlaybackQueueSnapshot
import app.yukine.playback.PlaybackReadModel
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.LuoxueTrackMetadataResolver
import kotlinx.coroutines.flow.StateFlow
import java.util.function.Consumer

internal fun interface PlaybackSourceCandidates {
    fun candidates(track: Track): List<Track>
}

internal fun interface PlaybackProviderTrackIdSource {
    fun providerTrackId(track: Track?): String
}

/** Owns the Activity-scoped playback connection, commands, UI bindings and lifecycle. */
internal class PlaybackFeatureBinding(
    private val context: Context,
    viewModels: MainActivityViewModels,
    private val mainHandler: Handler,
    private val settingsStore: MainSettingsStore,
    private val serviceStarter: NowPlayingPlaybackServiceStarter,
    private val commandQueue: PlaybackServiceCommandQueue,
    private val sourceSwitchPlanner: StreamingSourceSwitchPlanner,
    private val luoxueTrackMetadataResolver: LuoxueTrackMetadataResolver,
    private val streamingQuality: StreamingPlaybackQuality,
    private val statusMessages: StatusMessageController
) {
    val playbackViewModel = viewModels.playbackViewModel
    val nowPlayingViewModel = viewModels.nowPlayingViewModel
    val queueViewModel = viewModels.queueViewModel

    lateinit var connection: PlaybackServiceConnectionController
        private set
    lateinit var playbackActionController: PlaybackActionController
        private set
    lateinit var playbackStartController: PlaybackStartController
        private set
    lateinit var queueActionController: QueueActionController
        private set
    lateinit var nowPlayingEffectOwner: NowPlayingEffectOwner
        private set

    private lateinit var sourceSwitchOwner: NowPlayingSourceSwitchOwner
    private var boundLyricsViewModel: LyricsViewModel? = null
    private var boundHomeDashboardViewModel: HomeDashboardViewModel? = null

    fun bindConnection(
        lyricsViewModel: LyricsViewModel,
        libraryCollectionsOwner: LibraryCollectionsOwner,
        streamingViewModel: StreamingViewModel,
        libraryViewModel: LibraryViewModel,
        sourceCandidates: PlaybackSourceCandidates,
        preResolveNext: PlaybackDomainReactionOwner.NextStreamingTrackPreResolver,
        recoverBuffering: PlaybackDomainReactionOwner.StreamingBufferingRecoveryHandler,
        resolveCurrentStreamingTrack: PlaybackDomainReactionOwner.CurrentStreamingTrackResolver
    ) {
        val domainReactions = PlaybackDomainReactionOwner(
            mainHandler,
            lyricsViewModel::trackId,
            { playbackSpeed, appVolume ->
                settingsStore.setPlaybackSpeed(playbackSpeed)
                settingsStore.setAppVolume(appVolume)
            },
            lyricsViewModel::loadPlaybackTrack,
            libraryCollectionsOwner::load,
            preResolveNext,
            recoverBuffering,
            resolveCurrentStreamingTrack,
            statusMessages::setStatus
        )
        val serviceHost = PlaybackServiceHostController(MainPlaybackServiceHost(
            settingsStore::playbackSpeed,
            settingsStore::appVolume,
            settingsStore::concurrentPlaybackEnabled,
            settingsStore::statusBarLyricsEnabled,
            settingsStore::systemMediaLyricsTitleEnabled,
            settingsStore::playbackRestoreEnabled,
            settingsStore::replayGainEnabled,
            playbackViewModel::resetPlayback,
            { playbackStartController.playPendingTracksIfNeeded() }
        ))
        connection = PlaybackServiceConnectionController(
            context,
            domainReactions,
            serviceHost,
            serviceStarter,
            commandQueue
        )
        playbackViewModel.bind(connection)
        sourceSwitchOwner = NowPlayingSourceSwitchOwner(
            sourceSwitchPlanner,
            streamingViewModel,
            nowPlayingViewModel,
            connection,
            streamingQuality,
            statusMessages
        )
        nowPlayingViewModel.bindGateway(MainNowPlayingGateway(
            { if (::playbackActionController.isInitialized) playbackActionController else null },
            { snapshot() },
            { track -> libraryViewModel.onEvent(LibraryEvent.ToggleFavorite(track)) },
            nowPlayingViewModel::seekTo,
            { key -> AppLanguage.text(settingsStore.languageMode(), key) }
        ))
        nowPlayingViewModel.bindPlaybackGateway(NowPlayingPlaybackGatewayAdapter(
            { connection },
            { connection },
            serviceStarter::startPlaybackService,
            commandQueue
        ))
        nowPlayingViewModel.bindLuoxueTrackMetadataResolver(luoxueTrackMetadataResolver)
        nowPlayingViewModel.bindSourceCandidatesProvider(sourceCandidates::candidates)
    }

    fun bindActions(
        streamingTrackListResolver: StreamingTrackListResolver,
        resolveCurrentStreamingTrack: StreamingQueueResolveHandler,
        fallbackTracks: PlaybackFallbackTracksSource,
        heartbeatStopper: PlaybackStartHeartbeatStopper,
        resolvingStatus: PlaybackStartResolvingStatusProvider,
        openQueue: PlaybackStartQueueOpener,
        clearQueueConfirmer: QueueClearQueueConfirmer,
        lyricsViewModel: LyricsViewModel,
        providerTrackIdSource: PlaybackProviderTrackIdSource
    ) {
        playbackActionController = PlaybackActionController(
            nowPlayingViewModel,
            MainPlaybackActionListener(
                resolveCurrentStreamingTrack,
                { snapshot() },
                fallbackTracks,
                ::applyActionResult
            )
        )
        val startListener = MainPlaybackStartListener(
            heartbeatStopper,
            { serviceStarter.startPlaybackService(null) },
            { connection.isConnected() },
            resolvingStatus,
            statusMessages::setStatus,
            openQueue
        )
        playbackStartController = PlaybackStartController(
            streamingTrackListResolver,
            nowPlayingViewModel::playTrackList,
            ::applyActionResult,
            startListener
        )
        nowPlayingViewModel.bindStateObserver(FloatingLyricsPublisher::update)
        queueActionController = QueueActionController(
            nowPlayingViewModel,
            MainQueueActionListener(
                ::applyActionResult,
                { connection.isConnected() },
                connection::moveQueueTrack,
                clearQueueConfirmer,
                { AppLanguage.text(settingsStore.languageMode(), "queue.empty") },
                statusMessages::setStatus
            )
        )
        lyricsViewModel.bindReloadGateway(
            { snapshot().currentTrack },
            providerTrackIdSource::providerTrackId,
            statusMessages::setStatus
        )
        boundLyricsViewModel = lyricsViewModel
    }

    fun bindStateSources(
        libraryState: StateFlow<LibraryStoreState>,
        lyricsViewModel: LyricsViewModel,
        settingsViewModel: SettingsViewModel,
        streamingViewModel: StreamingViewModel,
        homeDashboardViewModel: HomeDashboardViewModel,
        homeIntentHandler: MainHomeDashboardRenderListener
    ) {
        nowPlayingViewModel.bindStateSources(
            connection,
            libraryState,
            lyricsViewModel.state,
            settingsViewModel.state
        )
        homeDashboardViewModel.bindStateSources(
            connection,
            libraryState,
            streamingViewModel.streaming,
            settingsViewModel.state,
            homeIntentHandler
        )
        boundHomeDashboardViewModel = homeDashboardViewModel
        queueViewModel.bindStateSources(connection, libraryState, settingsViewModel.state)
    }

    fun bindEffects(
        openQueue: Runnable,
        addToPlaylist: Consumer<Track>,
        shareTrack: Consumer<Track>,
        downloadTrack: Consumer<Track>
    ) {
        nowPlayingEffectOwner = NowPlayingEffectOwner(
            nowPlayingViewModel,
            openQueue,
            addToPlaylist,
            shareTrack,
            downloadTrack,
            sourceSwitchOwner::handle,
            sourceSwitchOwner::handle,
            statusMessages::setStatus
        )
    }

    fun bindService() = connection.bind()
    fun setAppVisible(visible: Boolean) = connection.setAppVisible(visible)
    fun isConnected(): Boolean = connection.isConnected()
    fun readModel(): PlaybackReadModel = connection
    fun snapshot(): PlaybackStateSnapshot = connection.state.value
    fun queueState(): StateFlow<PlaybackQueueSnapshot> = connection.queue
    fun queueSnapshot(): List<Track> = connection.queue.value.tracks

    fun continueDashboardPlayback(track: Track?) {
        if (snapshot().hasTrack()) {
            playbackActionController.togglePlayback()
        } else if (track != null) {
            playbackStartController.playTrackList(listOf(track), 0)
        }
    }

    fun applyActionResult(result: PlaybackActionResultUi?) {
        result?.status?.trim()?.takeIf(String::isNotEmpty)?.let(statusMessages::setStatus)
    }

    fun release() {
        nowPlayingViewModel.bindStateObserver(null)
        nowPlayingViewModel.bindStateSources(null, null, null, null)
        nowPlayingViewModel.bindGateway(null)
        nowPlayingViewModel.bindPlaybackGateway(null)
        nowPlayingViewModel.bindSourceCandidatesProvider(null)
        nowPlayingViewModel.bindLuoxueTrackMetadataResolver(null)
        queueViewModel.bindStateSources(null, null, null)
        boundHomeDashboardViewModel?.bindStateSources(null, null, null, null, null)
        boundHomeDashboardViewModel = null
        boundLyricsViewModel?.bindReloadGateway(null, null, null)
        boundLyricsViewModel = null
        playbackViewModel.bind(null)
        connection.release()
    }
}
