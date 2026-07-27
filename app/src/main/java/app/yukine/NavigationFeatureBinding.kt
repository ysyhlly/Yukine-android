package app.yukine

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import app.yukine.navigation.EchoNavHostState
import app.yukine.navigation.HomeTab
import app.yukine.navigation.LibraryNavBinding
import app.yukine.navigation.PlayerNavBinding
import app.yukine.navigation.SettingsNavBinding
import app.yukine.navigation.SettingsTab
import app.yukine.navigation.StreamingNavBinding
import app.yukine.navigation.TabRoute
import app.yukine.navigation.TogetherNavBinding
import app.yukine.navigation.TogetherTab
import app.yukine.playback.TogetherMedia3PlayerAdapter
import app.yukine.streaming.StreamingQualityPreference
import app.yukine.together.TogetherLabels
import app.yukine.together.TogetherPlaylistRef
import app.yukine.together.TogetherPreferences
import app.yukine.together.TogetherQueueItem
import app.yukine.together.TogetherQueueEditPort
import app.yukine.together.TogetherQueueItemMapper
import app.yukine.together.TogetherViewModel
import app.yukine.ui.OnboardingActions
import kotlinx.coroutines.flow.map

/**
 * Activity-scoped navigation binding.
 *
 * This is the single owner for route intent policy, back handling, queue navigation intents and
 * destination assembly. Feature behavior stays behind the focused owners passed to [bindRoot].
 */
internal class NavigationFeatureBinding(
    private val activity: ComponentActivity,
    navigationViewModel: NavigationViewModel,
    settingsViewModel: SettingsViewModel,
    private val settingsStore: MainSettingsStore
) {
    val routeController = MainRouteController(navigationViewModel)
    val intentOwner = MainNavigationIntentOwner(
        routeController,
        settingsViewModel::scrollToTopOnNextRender
    )

    private var navHostState: EchoNavHostState? = null
    private var boundQueueViewModel: app.yukine.queue.QueueViewModel? = null
    private var rootInstalled = false
    private var togetherViewModel: TogetherViewModel? = null

    fun bindRoot(
        viewModels: MainActivityViewModels,
        onboardingOwner: OnboardingOwner,
        permissionController: MainPermissionController,
        nowPlayingEffectOwner: NowPlayingEffectOwner,
        playlistDialogController: PlaylistDialogController,
        queueActionController: QueueActionController,
        documentPickerController: DocumentPickerController,
        trackDownloadManager: TrackDownloadManager,
        playbackConnection: PlaybackServiceConnectionController
    ) {
        if (rootInstalled) return
        val queueViewModel = viewModels.queueViewModel
        queueViewModel.bindIntentListener(QueueIntentOwner(
            viewModels.libraryViewModel::onEvent,
            playlistDialogController::showAddToPlaylist,
            queueActionController::removeQueueTrack,
            queueActionController::moveQueueTrack,
            queueActionController::confirmClearQueue,
            { intentOwner.handleBack() }
        ))
        boundQueueViewModel = queueViewModel
        navHostState = createNavHostState(
            viewModels,
            documentPickerController,
            trackDownloadManager,
            playbackConnection
        )
        rootInstalled = true
        EchoAppHost.installNavHost(activity, MainNavHostMount(
            { requireNotNull(navHostState) },
            onboardingOwner.state,
            settingsStore::languageMode,
            OnboardingActions(
                requestPermissions = Runnable(permissionController::requestAudioPermission),
                addMusicFolder = Runnable(onboardingOwner::addMusicFolder),
                scanLibrary = Runnable(onboardingOwner::scanLibrary),
                importPlaylist = Runnable(onboardingOwner::importPlaylist),
                openStreaming = Runnable(onboardingOwner::openStreaming),
                finish = Runnable(onboardingOwner::finish)
            ),
            Runnable { intentOwner.navigateToTab(HomeTab, true) },
            nowPlayingEffectOwner::handle,
            { tab -> intentOwner.navigateToTab(tab, true) }
        ))
        installBackNavigation()
    }

    fun release() {
        boundQueueViewModel?.bindIntentListener(null)
        boundQueueViewModel = null
        navHostState = null
        togetherViewModel = null
        rootInstalled = false
    }

    fun selectedTab(): String = routeController.selectedTab()
    fun isQueueVisible(): Boolean =
        selectedTab() == MainRoutes.TAB_QUEUE || navHostState?.queueSheetVisible == true
    fun libraryMode(): String = routeController.libraryMode()
    fun selectedLibraryGroupKey(): String = routeController.selectedLibraryGroupKey()
    fun selectedLibraryGroupTitle(): String = routeController.selectedLibraryGroupTitle()
    fun selectedPlaylistId(): Long = routeController.selectedPlaylistId()
    fun networkPage(): NetworkPage = routeController.networkPage()
    fun settingsPage(): String = routeController.settingsPage()
    fun selectedRemoteSourceId(): Long = routeController.selectedRemoteSourceId()
    fun navigateNetworkPage(page: NetworkPage) = routeController.setNetworkPage(page)
    fun navigateToNetworkTabPage(page: NetworkPage) = routeController.navigateToNetworkPageFromCurrent(page)
    fun navigateToTab(tab: TabRoute, userInitiated: Boolean = true) =
        intentOwner.navigateToTab(tab, userInitiated)
    fun openFloatingLyricsSettings() {
        intentOwner.navigateToTab(SettingsTab, false)
        routeController.setSettingsPage(SettingsPage.FloatingLyrics)
    }
    fun openQueueSheet() {
        navHostState?.setQueueSheetVisibility(true)
    }
    fun openPlayHistory() {
        openPlayHistoryRoute(routeController, settingsStore.languageMode())
    }

    fun openSmartCollection(key: String, title: String) {
        routeController.openLibraryGroup(LibraryGrouping.PLAYLISTS, key, title)
    }
    fun openTogetherFromPlaylist(ref: TogetherPlaylistRef) {
        togetherViewModel?.openCreateFromPlaylist(ref)
        intentOwner.navigateToTab(TogetherTab, false)
    }

    fun handleBack(): Boolean = intentOwner.handleBack()

    private fun createNavHostState(
        viewModels: MainActivityViewModels,
        documentPickerController: DocumentPickerController,
        trackDownloadManager: TrackDownloadManager,
        playbackConnection: PlaybackServiceConnectionController
    ): EchoNavHostState = EchoNavHostState(
        routeState = viewModels.navigationViewModel.state,
        player = PlayerNavBinding(
            nowPlayingStateProvider = viewModels.nowPlayingViewModel,
            queueStateProvider = viewModels.queueViewModel,
            playbackSnapshotProvider = viewModels.playbackViewModel,
            trackDownloadController = trackDownloadManager,
            realtimeBeatProvider = playbackConnection::realtimeBeat,
            realtimeBandsProvider = playbackConnection::realtimeBands,
            realtimeTransientProvider = playbackConnection::realtimeTransientBeat,
            visualMotionEnabled = true
        ),
        library = LibraryNavBinding(
            homeDashboardState = viewModels.homeDashboardViewModel.uiState,
            libraryGroupsState = viewModels.libraryViewModel.libraryGroups,
            libraryTrackListState = viewModels.libraryViewModel.trackList,
            libraryStoreState = viewModels.libraryViewModel.library,
            collectionsStateProvider = viewModels.collectionsViewModel,
            downloadsState = viewModels.downloadsViewModel.uiState,
            downloadsOpenDirectoryRequests = viewModels.downloadsViewModel.openDirectoryRequests(),
            downloadsActions = DownloadsDestinationOwner(
                viewModels.downloadsViewModel,
                trackDownloadManager,
                openDirectoryPicker = {
                    documentPickerController.openDownloadFolderPicker()
                    Unit
                }
            ).actions(),
            searchState = viewModels.searchViewModel.uiState,
            libraryActionHandler = viewModels.libraryViewModel.presentation::onAction,
            openPlayHistoryAction = Runnable(::openPlayHistory),
            openNetworkSourcesAction = Runnable { navigateToNetworkTabPage(NetworkPage.Sources) },
            openSmartCollectionAction = ::openSmartCollection,
            navigateUpAction = Runnable { intentOwner.handleBack() },
            recordingMatchStateProvider = viewModels.recordingMatchViewModel
        ),
        settings = SettingsNavBinding(
            settingsState = viewModels.settingsViewModel.state,
            settingsChromeState = viewModels.settingsViewModel.chromeState,
            settingsScrollState = viewModels.settingsViewModel.scrollState,
            networkMenuState = viewModels.networkMenuViewModel.uiState,
            networkSourcesState = viewModels.networkSourcesViewModel.uiState
        ),
        streaming = StreamingNavBinding(viewModels.streamingViewModel.streaming),
        together = TogetherNavBinding(
            viewModel = TogetherViewModel(
                playbackConnection.togetherSession,
                currentQueue = {
                    TogetherQueueItemMapper.fromTracks(playbackConnection.queueSnapshot())
                },
                preferences = TogetherPreferences(activity),
                playlistCatalog = AppTogetherPlaylistCatalog(
                    libraryViewModel = viewModels.libraryViewModel,
                    streamingViewModel = viewModels.streamingViewModel,
                    quality = {
                        StreamingQualityPreference.playbackQuality(
                            activity, settingsStore.streamingAudioQuality()
                        )
                    },
                    languageMode = { settingsStore.languageMode() }
                ),
                currentQueueUpdates = playbackConnection.queue.map { snapshot ->
                    TogetherQueueItemMapper.fromTracks(snapshot.tracks)
                },
                queueEditPort = object : TogetherQueueEditPort {
                    override fun remove(stableId: String) {
                        // Resolve non-numeric catalog/streaming ids the same way Media3 tracks are built.
                        val trackId = TogetherMedia3PlayerAdapter.media3TrackId(stableId)
                        playbackConnection.removeTracksById(setOf(trackId))
                    }

                    override fun move(fromStableId: String, toStableId: String) {
                        val tracks = playbackConnection.queueSnapshot()
                        if (tracks.isEmpty()) return
                        val fromId = TogetherMedia3PlayerAdapter.media3TrackId(fromStableId)
                        val toId = TogetherMedia3PlayerAdapter.media3TrackId(toStableId)
                        val fromIndex = tracks.indexOfFirst { it.id == fromId }
                        val toIndex = tracks.indexOfFirst { it.id == toId }
                        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
                        playbackConnection.moveQueueTrack(fromIndex, toIndex)
                    }
                }
            ).also { togetherViewModel = it },
            labels = { togetherLabels(settingsStore.languageMode()) },
            copyRoomCode = ::copyTogetherRoomCode,
            shareRoomCode = ::shareTogetherRoomCode
        ),
        queueSheetVisibilityListener = { },
    )

    private fun copyTogetherRoomCode(code: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("junto room", code))
    }

    private fun shareTogetherRoomCode(code: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://junto.watch/join#$code")
        activity.startActivity(Intent.createChooser(intent, togetherLabels(settingsStore.languageMode()).shareCode))
    }

    private fun installBackNavigation() {
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intentOwner.handleBack()) return
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
            }
        })
    }
}

private fun togetherLabels(language: String) = TogetherLabels(
    title = AppLanguage.text(language, "together.title"),
    createRoom = AppLanguage.text(language, "together.create"),
    joinRoom = AppLanguage.text(language, "together.join"),
    roomCode = AppLanguage.text(language, "together.room.code"),
    pasteRoomCode = AppLanguage.text(language, "together.room.paste"),
    settings = AppLanguage.text(language, "together.settings"),
    back = AppLanguage.text(language, "action.back"),
    confirmCreate = AppLanguage.text(language, "together.create.confirm"),
    emptyQueue = AppLanguage.text(language, "together.queue.empty"),
    addLocalAudio = AppLanguage.text(language, "together.queue.add"),
    previewFiles = AppLanguage.text(language, "together.join.preview"),
    confirmJoin = AppLanguage.text(language, "together.join.confirm"),
    matchLocal = AppLanguage.text(language, "together.join.match"),
    matchedLocal = AppLanguage.text(language, "together.join.matched"),
    downloadRequired = AppLanguage.text(language, "together.join.download"),
    storageSpace = AppLanguage.text(language, "together.join.storage"),
    notEnoughSpace = AppLanguage.text(language, "together.join.space.error"),
    remove = AppLanguage.text(language, "action.remove"),
    moveUp = AppLanguage.text(language, "action.move.up"),
    moveDown = AppLanguage.text(language, "action.move.down"),
    connecting = AppLanguage.text(language, "together.connecting"),
    waitingReady = AppLanguage.text(language, "together.waiting"),
    leave = AppLanguage.text(language, "together.leave"),
    members = AppLanguage.text(language, "together.members"),
    buffering = AppLanguage.text(language, "together.buffering"),
    ready = AppLanguage.text(language, "together.ready"),
    drift = AppLanguage.text(language, "together.drift"),
    transfer = AppLanguage.text(language, "together.transfer"),
    saveFile = AppLanguage.text(language, "together.save.file"),
    direct = AppLanguage.text(language, "together.connection.direct"),
    turn = AppLanguage.text(language, "together.connection.turn"),
    relay = AppLanguage.text(language, "together.connection.relay"),
    nickname = AppLanguage.text(language, "together.nickname"),
    relays = AppLanguage.text(language, "together.relays"),
    turnUrl = AppLanguage.text(language, "together.turn.url"),
    turnUsername = AppLanguage.text(language, "together.turn.username"),
    turnPassword = AppLanguage.text(language, "together.turn.password"),
    rememberPassword = AppLanguage.text(language, "together.turn.remember"),
    saveSettings = AppLanguage.text(language, "together.settings.save"),
    connectionTest = AppLanguage.text(language, "together.connection.test"),
    relayTestOk = AppLanguage.text(language, "together.connection.test.ok"),
    relayTurnConfigured = AppLanguage.text(language, "together.connection.test.turn"),
    copyCode = AppLanguage.text(language, "together.code.copy"),
    shareCode = AppLanguage.text(language, "together.code.share"),
    invalidRoomCode = AppLanguage.text(language, "together.code.invalid"),
    fileSaved = AppLanguage.text(language, "together.file.saved"),
    homeSubtitle = AppLanguage.text(language, "together.home.subtitle"),
    createRoomHint = AppLanguage.text(language, "together.create.hint"),
    joinRoomHint = AppLanguage.text(language, "together.join.hint"),
    createTitle = AppLanguage.text(language, "together.create.title"),
    joinTitle = AppLanguage.text(language, "together.join.title"),
    roomTitle = AppLanguage.text(language, "together.room.title"),
    choosePlaylist = AppLanguage.text(language, "together.queue.choose.playlist"),
    closePlaylistPicker = AppLanguage.text(language, "together.queue.close.picker"),
    playlistLoading = AppLanguage.text(language, "together.queue.playlist.loading"),
    replaceQueue = AppLanguage.text(language, "together.queue.replace"),
    skippedTracks = AppLanguage.text(language, "together.queue.skipped"),
    playlistHasNoShareableTracks = AppLanguage.text(language, "together.queue.no.shareable"),
    replaceQueueConfirmation = AppLanguage.text(language, "together.queue.replace.confirm"),
    currentQueueHint = AppLanguage.text(language, "together.queue.hint"),
    liveQueue = AppLanguage.text(language, "together.queue.live"),
    emptyQueueHint = AppLanguage.text(language, "together.queue.empty.hint"),
    trackCountLabel = AppLanguage.text(language, "together.queue.track.count"),
    settingsSaved = AppLanguage.text(language, "together.settings.saved"),
    noPlaylists = AppLanguage.text(language, "together.queue.no.playlists"),
    nowPlayingBadge = AppLanguage.text(language, "together.now.playing"),
    pasteFromClipboard = AppLanguage.text(language, "together.room.paste.action"),
    joinStepInput = AppLanguage.text(language, "together.join.step.input"),
    joinStepPreview = AppLanguage.text(language, "together.join.step.preview"),
    matchedSummary = AppLanguage.text(language, "together.join.matched.summary"),
    editRoomCode = AppLanguage.text(language, "together.room.edit.code"),
    dragReorder = AppLanguage.text(language, "together.queue.drag")
)

internal fun openPlayHistoryRoute(routeController: MainRouteController, languageMode: String) {
    routeController.openLibraryGroup(
        LibraryGrouping.PLAYLISTS,
        LibraryPlaylistsStateReducer.HISTORY_GROUP_KEY,
        AppLanguage.text(languageMode, "play.history.playlist")
    )
}
