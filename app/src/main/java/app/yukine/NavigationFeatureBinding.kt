package app.yukine

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import app.yukine.navigation.EchoNavHostState
import app.yukine.navigation.HomeTab
import app.yukine.navigation.TabRoute
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
                requestPermissions = Runnable(permissionController::requestNeededPermissions),
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
        rootInstalled = false
    }

    fun selectedTab(): String = routeController.selectedTab()
    fun isQueueVisible(): Boolean =
        selectedTab() == MainRoutes.TAB_QUEUE || navHostState?.queueSheetVisible == true
    fun libraryMode(): String = routeController.libraryMode()
    fun selectedLibraryGroupKey(): String = routeController.selectedLibraryGroupKey()
    fun selectedLibraryGroupTitle(): String = routeController.selectedLibraryGroupTitle()
    fun selectedPlaylistId(): Long = routeController.selectedPlaylistId()
    fun networkPage(): String = routeController.networkPage()
    fun settingsPage(): String = routeController.settingsPage()
    fun selectedRemoteSourceId(): Long = routeController.selectedRemoteSourceId()
    fun navigateNetworkPage(page: String) = routeController.setNetworkPage(page)
    fun navigateToNetworkTabPage(page: String) = routeController.navigateToNetworkPageFromCurrent(page)
    fun navigateToTab(tab: TabRoute, userInitiated: Boolean = true) =
        intentOwner.navigateToTab(tab, userInitiated)
    fun handleBack(): Boolean = intentOwner.handleBack()

    private fun createNavHostState(
        viewModels: MainActivityViewModels,
        documentPickerController: DocumentPickerController,
        trackDownloadManager: TrackDownloadManager,
        playbackConnection: PlaybackServiceConnectionController
    ): EchoNavHostState = EchoNavHostState(
        routeState = viewModels.navigationViewModel.state,
        homeDashboardState = viewModels.homeDashboardViewModel.uiState,
        nowPlayingStateProvider = viewModels.nowPlayingViewModel,
        queueStateProvider = viewModels.queueViewModel,
        libraryGroupsState = viewModels.libraryViewModel.libraryGroups,
        libraryTrackListState = viewModels.libraryViewModel.trackList,
        collectionsStateProvider = viewModels.collectionsViewModel,
        settingsState = viewModels.settingsViewModel.state,
        settingsChromeState = viewModels.settingsViewModel.chromeState,
        settingsScrollState = viewModels.settingsViewModel.scrollState,
        networkMenuState = viewModels.networkMenuViewModel.uiState,
        networkSourcesState = viewModels.networkSourcesViewModel.uiState,
        streamingState = viewModels.streamingViewModel.streaming,
        playbackSnapshotProvider = viewModels.playbackViewModel,
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
        trackDownloadController = trackDownloadManager,
        realtimeBeatProvider = playbackConnection::realtimeBeat,
        realtimeBandsProvider = playbackConnection::realtimeBands,
        visualMotionEnabled = true,
        queueSheetVisibilityListener = { },
        libraryActionHandler = viewModels.libraryViewModel::onLibraryAction
    )

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
