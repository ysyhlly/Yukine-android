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
import app.yukine.together.TogetherLabels
import app.yukine.together.TogetherPreferences
import app.yukine.together.TogetherQueueItem
import app.yukine.together.TogetherViewModel
import app.yukine.ui.OnboardingActions

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
                    playbackConnection.queueSnapshot().map { track ->
                        val source = track.dataPath.takeIf(String::isNotBlank)
                            ?: track.contentUri?.toString().orEmpty()
                        val localFile = java.io.File(source)
                        TogetherQueueItem(
                            stableId = track.id.toString(),
                            title = track.title,
                            artist = track.artist,
                            sourceUri = source,
                            sizeBytes = if (localFile.isFile) localFile.length() else 0L,
                            shareable = source.startsWith("content://") ||
                                source.startsWith("file://") ||
                                localFile.isFile
                        )
                    }
                },
                preferences = TogetherPreferences(activity)
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
    fileSaved = AppLanguage.text(language, "together.file.saved")
)

internal fun openPlayHistoryRoute(routeController: MainRouteController, languageMode: String) {
    routeController.openLibraryGroup(
        LibraryGrouping.PLAYLISTS,
        LibraryPlaylistsStateReducer.HISTORY_GROUP_KEY,
        AppLanguage.text(languageMode, "play.history.playlist")
    )
}
