package app.echo.next;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MainActivityArchitectureContractTest {
    @Test
    public void mainActivityDoesNotDirectlyOwnStreamingRepositoryProvider() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String gatewayEvents = read("app/src/main/java/app/echo/next/StreamingGatewayEventController.kt");

        assertFalse(mainActivity.contains("StreamingRepositoryProvider"));
        assertFalse(mainActivity.contains("streamingRepositoryProvider"));
        assertFalse(mainActivity.contains(".setStreamingRepository("));
        assertFalse(mainActivity.contains("viewModel.configureStreamingRepository()"));
        assertFalse(mainActivity.contains("viewModel.refreshStreamingProviders()"));
        assertTrue(mainActivity.contains("streamingGatewayController.configureRepository()"));
        assertTrue(mainActivity.contains("streamingGatewayEventController.refreshStreamingProviders()"));
        assertTrue(gatewayEvents.contains("viewModel.configureStreamingRepository()"));
        assertTrue(gatewayEvents.contains("viewModel.refreshStreamingProviders()"));
    }

    @Test
    public void viewModelUsesRepositorySourceInsteadOfPublicRepositorySetter() throws Exception {
        String viewModel = read("app/src/main/java/app/echo/next/MainActivityViewModel.kt");

        assertTrue(viewModel.contains("StreamingRepositorySource"));
        assertTrue(viewModel.contains("fun configureStreamingRepository(): Job"));
        assertTrue(viewModel.contains("fun clearExpiredStreamingCache()"));
        assertTrue(viewModel.contains("fun searchStreaming("));
        assertTrue(viewModel.contains("): Job {\r\n        val normalizedMediaTypes")
                || viewModel.contains("): Job {\n        val normalizedMediaTypes"));
        assertTrue(viewModel.contains("streamingRepository.clearExpiredCache()"));
        assertFalse(viewModel.contains("fun setStreamingRepository("));
    }

    @Test
    public void mainActivityDelegatesNetworkOperationsThroughRequestController() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String requestController = read("app/src/main/java/app/echo/next/NetworkRequestController.kt");
        String dialogEventController = read("app/src/main/java/app/echo/next/NetworkDialogEventController.kt");
        String actionsController = read("app/src/main/java/app/echo/next/NetworkActionsController.kt");
        String operationSink = read("app/src/main/java/app/echo/next/NetworkOperationSink.kt");

        assertTrue(mainActivity.contains("NetworkRequestController"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkRequestController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkDialogEventController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkOperationSink.java"));
        assertTrue(requestController.contains("internal class NetworkRequestController"));
        assertTrue(requestController.contains("private val operations: NetworkOperationSink"));
        assertTrue(requestController.contains("interface Labels"));
        assertTrue(requestController.contains("interface Listener"));
        assertTrue(requestController.contains("listener.setStatus(labels.text(\"adding.stream\"))"));
        assertTrue(requestController.contains("operations.syncAllWebDavSources(sourceIds)"));
        assertTrue(dialogEventController.contains("internal class NetworkDialogEventController"));
        assertTrue(dialogEventController.contains(": NetworkDialogController.Listener"));
        assertTrue(dialogEventController.contains("private val requestController: NetworkRequestController"));
        assertTrue(dialogEventController.contains("requestController.addStreamUrl(title, url)"));
        assertTrue(dialogEventController.contains("requestController.importM3uPlaylist(url)"));
        assertTrue(dialogEventController.contains("requestController.updateStreamUrl(track, title, url)"));
        assertTrue(dialogEventController.contains("requestController.saveWebDavSource(sourceId, name, baseUrl, username, password, rootPath)"));
        assertTrue(mainActivity.contains("new NetworkDialogEventController(networkRequestController)"));
        assertFalse(mainActivity.contains("new NetworkDialogController.Listener()"));
        assertFalse(mainActivity.contains("private void addStreamUrl("));
        assertFalse(mainActivity.contains("private void updateStreamUrl("));
        assertFalse(mainActivity.contains("private void importM3uPlaylist("));
        assertFalse(mainActivity.contains("private void saveWebDavSource("));
        assertTrue(actionsController.contains("internal class NetworkActionsController"));
        assertTrue(actionsController.contains(": NetworkOperationSink"));
        assertTrue(actionsController.contains("private val repository: MusicLibraryRepository"));
        assertTrue(actionsController.contains("listener.onStreamAdded(cached, favorites, status)"));
        assertTrue(actionsController.contains("repository.syncRemoteSource(sourceId)"));
        assertTrue(actionsController.contains("listener.onAllWebDavSourcesSynced(cached, favorites, status)"));
        assertTrue(operationSink.contains("internal interface NetworkOperationSink"));
        assertTrue(operationSink.contains("fun updateStreamUrl(oldTrack: Track?, title: String, url: String)"));
        assertTrue(operationSink.contains("fun syncAllWebDavSources(sourceIds: List<Long>)"));
        assertFalse(mainActivity.contains("networkActionsController.addStreamUrl("));
        assertFalse(mainActivity.contains("networkActionsController.updateStreamUrl("));
        assertFalse(mainActivity.contains("networkActionsController.importM3uPlaylist("));
        assertFalse(mainActivity.contains("networkActionsController.deleteAllStreams("));
        assertFalse(mainActivity.contains("networkActionsController.deleteTrack("));
        assertFalse(mainActivity.contains("networkActionsController.deleteRemoteSource("));
        assertFalse(mainActivity.contains("networkActionsController.saveWebDavSource("));
        assertFalse(mainActivity.contains("networkActionsController.testRemoteSource("));
        assertFalse(mainActivity.contains("networkActionsController.syncRemoteSource("));
        assertFalse(mainActivity.contains("networkActionsController.syncAllWebDavSources("));
    }

    @Test
    public void networkOperationStatusLabelsStayOutOfMainActivityRequestMethods() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");

        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"adding.stream\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"updating.stream\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleting.streams\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleting.stream\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleting.source\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"saving.webdav.source\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"syncing.webdav.sources\")"));
    }

    @Test
    public void playbackStateListenerStaysOutOfMainActivity() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String playbackStateEventController = read("app/src/main/java/app/echo/next/PlaybackStateEventController.kt");
        String playbackStateUpdateController = read("app/src/main/java/app/echo/next/PlaybackStateUpdateController.kt");
        String playbackRenderPolicy = read("app/src/main/java/app/echo/next/PlaybackRenderPolicy.kt");
        String playbackServiceConnectionController = read("app/src/main/java/app/echo/next/PlaybackServiceConnectionController.kt");
        String playbackActionsController = read("app/src/main/java/app/echo/next/PlaybackActionsController.kt");
        String nowBarStateFactory = read("app/src/main/java/app/echo/next/NowBarStateFactory.kt");
        String nowPlayingStateFactory = read("app/src/main/java/app/echo/next/NowPlayingStateFactory.kt");
        String nowPlayingRenderController = read("app/src/main/java/app/echo/next/NowPlayingRenderController.kt");
        String queueRenderController = read("app/src/main/java/app/echo/next/QueueRenderController.kt");

        assertFalse(mainActivity.contains("implements PlaybackStateListener"));
        assertFalse(mainActivity.contains("onPlaybackStateChanged("));
        assertFalse(mainActivity.contains("PlaybackStateListener"));
        assertTrue(mainActivity.contains("PlaybackStateEventController"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaybackStateEventController.java"));
        assertTrue(playbackStateEventController.contains(": PlaybackStateListener"));
        assertTrue(playbackStateEventController.contains("playbackStore.replaceSnapshot(snapshot)"));
        assertTrue(playbackStateEventController.contains("playbackStore.publish(serviceQueueSource.service())"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaybackStateUpdateController.java"));
        assertTrue(playbackStateUpdateController.contains("internal class PlaybackStateUpdateController"));
        assertTrue(playbackStateUpdateController.contains("data class Result("));
        assertTrue(playbackStateUpdateController.contains("PlaybackRenderPolicy.shouldRenderForPlaybackChange"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaybackRenderPolicy.java"));
        assertTrue(playbackRenderPolicy.contains("internal object PlaybackRenderPolicy"));
        assertTrue(playbackRenderPolicy.contains("fun shouldRenderForPlaybackChange("));
        assertTrue(playbackRenderPolicy.contains("MainRoutes.TAB_COLLECTIONS"));
        assertTrue(playbackRenderPolicy.contains("previous.errorMessage != next.errorMessage"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaybackServiceConnectionController.java"));
        assertTrue(playbackServiceConnectionController.contains("internal class PlaybackServiceConnectionController"));
        assertTrue(playbackServiceConnectionController.contains("private val playbackStateListener: PlaybackStateListener"));
        assertTrue(playbackServiceConnectionController.contains("object : ServiceConnection"));
        assertTrue(playbackServiceConnectionController.contains("nextService.registerListener(playbackStateListener)"));
        assertTrue(playbackServiceConnectionController.contains("disconnectedService?.unregisterListener(playbackStateListener)"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaybackActionsController.java"));
        assertTrue(playbackActionsController.contains("internal class PlaybackActionsController"));
        assertTrue(playbackActionsController.contains("@JvmField val snapshot: PlaybackStateSnapshot?"));
        assertTrue(playbackActionsController.contains("@JvmField val publishPlaybackState: Boolean"));
        assertTrue(playbackActionsController.contains("service.playQueue(ArrayList(tracks), index)"));
        assertTrue(playbackActionsController.contains("context.startService(intent)"));
        assertFalse(exists("app/src/main/java/app/echo/next/NowBarStateFactory.java"));
        assertTrue(nowBarStateFactory.contains("internal object NowBarStateFactory"));
        assertTrue(nowBarStateFactory.contains("@JvmStatic"));
        assertTrue(nowBarStateFactory.contains("fun create("));
        assertTrue(nowBarStateFactory.contains("favoriteIds.contains(track.id)"));
        assertTrue(nowBarStateFactory.contains("EchoPlaybackService.REPEAT_ONE"));
        assertFalse(exists("app/src/main/java/app/echo/next/NowPlayingStateFactory.java"));
        assertTrue(nowPlayingStateFactory.contains("internal object NowPlayingStateFactory"));
        assertTrue(nowPlayingStateFactory.contains("@JvmStatic"));
        assertTrue(nowPlayingStateFactory.contains("): NowPlayingUiState?"));
        assertTrue(nowPlayingStateFactory.contains("val track = playbackState.currentTrack ?: return null"));
        assertTrue(nowPlayingStateFactory.contains("LyricUiLine(line.text, index == activeIndex)"));
        assertFalse(exists("app/src/main/java/app/echo/next/NowPlayingRenderController.java"));
        assertTrue(nowPlayingRenderController.contains("internal class NowPlayingRenderController"));
        assertTrue(nowPlayingRenderController.contains("interface Listener"));
        assertTrue(nowPlayingRenderController.contains("private var controller: NowPlayingController?"));
        assertTrue(nowPlayingRenderController.contains("fun render(playbackStore: MainPlaybackStore, lyricsController: LyricsController?): Boolean"));
        assertTrue(nowPlayingRenderController.contains("NowPlayingStateFactory.create("));
        assertFalse(exists("app/src/main/java/app/echo/next/QueueRenderController.java"));
        assertTrue(queueRenderController.contains("internal class QueueRenderController"));
        assertTrue(queueRenderController.contains("interface Listener"));
        assertTrue(queueRenderController.contains("fun render(queue: List<Track>?"));
        assertTrue(queueRenderController.contains("TrackRowStateFactory.queueRow("));
        assertTrue(queueRenderController.contains("listener.publishQueue(rows)"));
        assertTrue(queueRenderController.contains("QueueScreenFactory.create("));
    }

    @Test
    public void mainPlaybackStoreIsKotlinStateHolder() throws Exception {
        String playbackStore = read("app/src/main/java/app/echo/next/MainPlaybackStore.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainPlaybackStore.java"));
        assertTrue(playbackStore.contains("internal class MainPlaybackStore"));
        assertTrue(playbackStore.contains("private val viewModel: MainActivityViewModel"));
        assertTrue(playbackStore.contains("fun snapshot(): PlaybackStateSnapshot"));
        assertTrue(playbackStore.contains("viewModel.playback.value.snapshot"));
        assertTrue(playbackStore.contains("viewModel.replacePlaybackSnapshot(snapshot)"));
        assertTrue(playbackStore.contains("viewModel.updatePlayback(snapshot(), queue)"));
    }

    @Test
    public void mainLibraryStoreIsKotlinStateHolder() throws Exception {
        String libraryStore = read("app/src/main/java/app/echo/next/MainLibraryStore.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainLibraryStore.java"));
        assertTrue(libraryStore.contains("internal class MainLibraryStore"));
        assertTrue(libraryStore.contains("private val repository: MusicLibraryRepository"));
        assertTrue(libraryStore.contains("private val viewModel: MainActivityViewModel"));
        assertTrue(libraryStore.contains("return ArrayList(state().allTracks)"));
        assertTrue(libraryStore.contains("viewModel.replaceLibrary(cachedTracks, repository.search(cachedTracks, searchQuery), HashSet(favorites))"));
        assertTrue(libraryStore.contains("viewModel.applyCollections("));
        assertTrue(libraryStore.contains("NetworkLibrary.streamTracks(allTracks())"));
        assertTrue(libraryStore.contains("return viewModel.library.value"));
    }

    @Test
    public void mainRouteControllerIsKotlinStateHolder() throws Exception {
        String routeController = read("app/src/main/java/app/echo/next/MainRouteController.kt");
        String backPolicy = read("app/src/main/java/app/echo/next/MainBackNavigationPolicy.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainRouteController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/MainBackNavigationPolicy.java"));
        assertTrue(routeController.contains("internal class MainRouteController"));
        assertTrue(routeController.contains("private val viewModel: MainActivityViewModel"));
        assertTrue(routeController.contains("private var state: MainActivityRouteState"));
        assertTrue(routeController.contains("fun restoreFromViewModel()"));
        assertTrue(routeController.contains("state = viewModel.state.value"));
        assertTrue(routeController.contains("fun navigateToTab(tabKey: String, userInitiated: Boolean): Boolean"));
        assertTrue(routeController.contains("MainBackNavigationPolicy.resolve("));
        assertTrue(routeController.contains("viewModel.updateRoute(state)"));
        assertTrue(backPolicy.contains("internal object MainBackNavigationPolicy"));
        assertTrue(backPolicy.contains("@JvmField val handled: Boolean"));
        assertTrue(backPolicy.contains("fun resolve("));
        assertTrue(backPolicy.contains("Result.navigate(MainRoutes.TAB_HOME"));
    }

    @Test
    public void mainTabRenderDispatcherIsKotlinRouteBoundary() throws Exception {
        String dispatcher = read("app/src/main/java/app/echo/next/MainTabRenderDispatcher.kt");
        String tabBarController = read("app/src/main/java/app/echo/next/ui/TabBarController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainTabRenderDispatcher.java"));
        assertTrue(dispatcher.contains("internal class MainTabRenderDispatcher"));
        assertTrue(dispatcher.contains("interface Renderer"));
        assertTrue(dispatcher.contains("fun render(selectedTab: String)"));
        assertTrue(dispatcher.contains("MainRoutes.TAB_HOME -> renderer.renderHome()"));
        assertTrue(dispatcher.contains("MainRoutes.TAB_LIBRARY -> renderer.renderLibrary()"));
        assertTrue(dispatcher.contains("MainRoutes.TAB_NETWORK -> renderer.renderNetwork()"));
        assertTrue(dispatcher.contains("else -> renderer.renderSettings()"));
        assertTrue(tabBarController.contains("fun updateSelected(key: String) {\n        selectedKey.value = key\n        requestedKey.value = \"\"\n    }")
                || tabBarController.contains("fun updateSelected(key: String) {\r\n        selectedKey.value = key\r\n        requestedKey.value = \"\"\r\n    }"));
        assertTrue(tabBarController.contains("initialSelectedKey: String"));
        assertTrue(tabBarController.contains("mutableStateOf(initialTabKey(tabs, initialSelectedKey))"));
        assertTrue(tabBarController.contains("internal fun initialTabKey(tabs: List<AppTabUiState>, initialSelectedKey: String): String"));
        assertTrue(tabBarController.contains("private val requestedKey: MutableState<String> =\n        mutableStateOf(\"\")")
                || tabBarController.contains("private val requestedKey: MutableState<String> =\r\n        mutableStateOf(\"\")"));
        assertTrue(tabBarController.contains("val requested = requestedKey.value"));
        assertTrue(tabBarController.contains("if (!shouldAcceptRequestedRoute(route, requested))"));
        assertTrue(tabBarController.contains("userRequestedRoute = tabKey"));
        assertTrue(tabBarController.contains("requestedKey.value = tabKey"));
    }

    @Test
    public void contentHostControllerIsKotlinUiBoundary() throws Exception {
        String controller = read("app/src/main/java/app/echo/next/ContentHostController.kt");
        String shellController = read("app/src/main/java/app/echo/next/MainUiShellController.java");

        assertFalse(exists("app/src/main/java/app/echo/next/ContentHostController.java"));
        assertTrue(controller.contains("internal class ContentHostController"));
        assertTrue(controller.contains("fun useScrollingContainer(): LinearLayout"));
        assertTrue(controller.contains("fun useFixedContainer(existingChildren: List<View>): LinearLayout"));
        assertTrue(controller.contains("fun getScrollView(): ScrollView? = scrollView"));
        assertTrue(controller.contains("fun prepareHorizontalTransition(next: Boolean)"));
        assertTrue(controller.contains("fun animateContentIfPending(view: View)"));
        assertTrue(controller.contains("fun applyBackground()"));
        assertTrue(controller.contains("routeHost?.let"));
        assertTrue(controller.contains("FrameLayout.LayoutParams("));
        String routeHostController = read("app/src/main/java/app/echo/next/ui/ContentRouteHostController.kt");
        assertTrue(routeHostController.contains("fun selectedRoute(): String = selectedRoute.value"));
        assertTrue(routeHostController.contains("fun updateSelected(route: String) {\r\n        selectedRoute.value = route\r\n        requestedRoute.value = \"\"\r\n    }")
                || routeHostController.contains("fun updateSelected(route: String) {\n        selectedRoute.value = route\n        requestedRoute.value = \"\"\n    }"));
        int routeFlowIndex = routeHostController.indexOf("navController.currentBackStackEntryFlow.collect");
        assertTrue(routeFlowIndex >= 0);
        assertTrue(routeHostController.indexOf("if (!shouldAcceptRequestedRoute(route, requested))", routeFlowIndex) <
                routeHostController.indexOf("selectedRoute.value = route", routeFlowIndex));
        assertTrue(shellController.contains("new ContentHostController(activity, contentHost, contentRouteHostController.getView())"));
        assertTrue(shellController.contains("new TabBarController(activity, localizedTabs(languageMode), initialRoute"));
        assertTrue(shellController.contains("contentHostController.useScrollingContainer()"));
        assertTrue(shellController.contains("contentHostController.useFixedContainer(existingChildren)"));
        assertTrue(shellController.contains("contentHost.setOnHorizontalSwipeListener"));
        assertTrue(shellController.contains("private void selectAdjacentTab(boolean next)"));
        assertTrue(shellController.contains("String selected = listener.selectedTab();"));
        assertTrue(shellController.contains("String nextTab = MainTabSwipePolicy.adjacentTab(tabRoutes, selected, next);"));
        assertFalse(shellController.contains("String selected = contentRouteHostController.selectedRoute();"));
        assertTrue(shellController.contains("prepareHorizontalContentTransition(next)"));
        assertTrue(shellController.contains("listener.onTabSelected(nextTab, false)"));
    }

    @Test
    public void trackListRenderControllerIsKotlinRenderBoundary() throws Exception {
        String controller = read("app/src/main/java/app/echo/next/TrackListRenderController.kt");
        String rowStateFactory = read("app/src/main/java/app/echo/next/TrackRowStateFactory.kt");
        String rowKeyPolicy = read("app/src/main/java/app/echo/next/TrackRowKeyPolicy.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/TrackListRenderController.java"));
        assertTrue(controller.contains("internal class TrackListRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("TrackRowStateFactory.trackRow("));
        assertTrue(controller.contains("listener.publishTrackList(title, rows)"));
        assertTrue(controller.contains("TrackListScreenFactory.create("));
        assertTrue(controller.contains("viewModel.trackList"));
        assertFalse(exists("app/src/main/java/app/echo/next/TrackRowStateFactory.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/TrackRowKeyPolicy.java"));
        assertTrue(rowStateFactory.contains("internal object TrackRowStateFactory"));
        assertTrue(rowStateFactory.contains("@JvmStatic"));
        assertTrue(rowStateFactory.contains("fun trackRow("));
        assertTrue(rowStateFactory.contains("fun queueRow("));
        assertTrue(rowStateFactory.contains("fun playlistRow("));
        assertTrue(rowKeyPolicy.contains("internal object TrackRowKeyPolicy"));
        assertTrue(rowKeyPolicy.contains("@JvmStatic"));
        assertTrue(rowKeyPolicy.contains("fun occurrenceKey(tracks: List<Track>?"));
    }

    @Test
    public void libraryGroupsRenderControllerIsKotlinStateFlowBoundary() throws Exception {
        String controller = read("app/src/main/java/app/echo/next/LibraryGroupsRenderController.kt");
        String collectionRowStateFactory = read("app/src/main/java/app/echo/next/CollectionRowStateFactory.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/LibraryGroupsRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/CollectionRowStateFactory.java"));
        assertTrue(controller.contains("internal class LibraryGroupsRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("LibraryGrouping.groupTracks("));
        assertTrue(controller.contains("listener.publishLibraryGroups(title, groupRows)"));
        assertTrue(controller.contains("LibraryGroupsScreenFactory.create("));
        assertTrue(controller.contains("viewModel.libraryGroups"));
        assertTrue(controller.contains("private fun renderGroupDetail("));
        assertTrue(collectionRowStateFactory.contains("internal object CollectionRowStateFactory"));
        assertTrue(collectionRowStateFactory.contains("@JvmStatic"));
        assertTrue(collectionRowStateFactory.contains("fun playlistRow("));
        assertTrue(collectionRowStateFactory.contains("fun networkSourceRow("));
        assertTrue(collectionRowStateFactory.contains("NetworkLibrary.remoteSourceSubtitle("));
    }

    @Test
    public void collectionsRenderControllerIsKotlinUiStateBoundary() throws Exception {
        String controller = read("app/src/main/java/app/echo/next/CollectionsRenderController.kt");
        String documentPickerController = read("app/src/main/java/app/echo/next/DocumentPickerController.kt");
        String confirmationDialogController = read("app/src/main/java/app/echo/next/ConfirmationDialogController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/CollectionsRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/DocumentPickerController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/ConfirmationDialogController.java"));
        assertTrue(controller.contains("internal class CollectionsRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("CollectionsUiState("));
        assertTrue(controller.contains("CollectionsActions("));
        assertTrue(controller.contains("CollectionsScreenFactory.create(context, state, actions)"));
        assertTrue(controller.contains("TrackRowStateFactory.trackRow("));
        assertTrue(controller.contains("CollectionRowStateFactory.playlistRow("));
        assertTrue(controller.contains("TrackRowKeyPolicy.occurrenceKey("));
        assertTrue(controller.contains("private fun buildSelectedPlaylistRows("));
        assertTrue(controller.contains("private fun recordDetails("));
        assertTrue(documentPickerController.contains("internal class DocumentPickerController"));
        assertTrue(documentPickerController.contains("interface Listener"));
        assertTrue(documentPickerController.contains("fun openAudioFilePicker()"));
        assertTrue(documentPickerController.contains("fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean"));
        assertTrue(documentPickerController.contains("private fun selectedAudioUris(data: Intent?): ArrayList<Uri>"));
        assertTrue(documentPickerController.contains("@JvmField val REQUEST_IMPORT_AUDIO_FILES: Int = 4002"));
        assertTrue(confirmationDialogController.contains("internal class ConfirmationDialogController"));
        assertTrue(confirmationDialogController.contains("interface LanguageProvider"));
        assertTrue(confirmationDialogController.contains("interface Listener"));
        assertTrue(confirmationDialogController.contains("fun confirmClearPlayHistory()"));
        assertTrue(confirmationDialogController.contains("fun confirmDeleteRemoteSource(source: RemoteSource?)"));
        assertTrue(confirmationDialogController.contains("EchoDialog.builder(context)"));
    }

    @Test
    public void networkRenderCoordinatorIsKotlinAndOwnsNetworkPageDispatch() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String coordinator = read("app/src/main/java/app/echo/next/NetworkRenderCoordinator.kt");
        String menuRenderer = read("app/src/main/java/app/echo/next/NetworkMenuRenderController.kt");
        String menuEvents = read("app/src/main/java/app/echo/next/NetworkMenuEventController.kt");
        String trackListRenderer = read("app/src/main/java/app/echo/next/NetworkTrackListRenderController.kt");
        String sourcesRenderer = read("app/src/main/java/app/echo/next/NetworkSourcesRenderController.kt");
        String sourcesEvents = read("app/src/main/java/app/echo/next/NetworkSourcesEventController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/NetworkRenderCoordinator.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkMenuRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkMenuEventController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkTrackListRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkSourcesRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkSourcesEventController.java"));
        assertTrue(coordinator.contains("internal class NetworkRenderCoordinator"));
        assertTrue(coordinator.contains("private val libraryStore: MainLibraryStore"));
        assertTrue(coordinator.contains("MainRoutes.NETWORK_STREAMING -> renderStreamingNetwork()"));
        assertTrue(coordinator.contains("MainRoutes.NETWORK_STREAM_LIST -> renderStreamList(languageMode, searchQuery)"));
        assertTrue(coordinator.contains("MainRoutes.NETWORK_WEBDAV_SOURCE_TRACKS ->"));
        assertTrue(coordinator.contains("sourcesRenderer.render(languageMode, libraryStore.remoteSources(), libraryStore.allTracks())"));
        assertTrue(coordinator.contains("streamingRenderer.render()"));
        assertTrue(coordinator.contains("trackListRenderer.renderWebDavSourceTrackList("));
        assertTrue(menuRenderer.contains("internal class NetworkMenuRenderController"));
        assertTrue(menuRenderer.contains("interface Listener"));
        assertTrue(menuRenderer.contains("fun renderHome("));
        assertTrue(menuRenderer.contains("fun renderStreaming("));
        assertTrue(menuRenderer.contains("fun renderWebDav("));
        assertTrue(menuRenderer.contains("SettingsScreenFactory.create(context, metrics, actions)"));
        assertTrue(menuRenderer.contains("MainRoutes.NETWORK_STREAMING"));
        assertTrue(menuEvents.contains("internal class NetworkMenuEventController"));
        assertTrue(menuEvents.contains(": NetworkMenuRenderController.Listener"));
        assertTrue(menuEvents.contains("interface LibrarySource"));
        assertTrue(menuEvents.contains("fun interface Requests"));
        assertTrue(menuEvents.contains("statusSink.setStatus(labels.text(\"no.streams.to.play\"))"));
        assertTrue(menuEvents.contains("deleteConfirmation.confirmDeleteAllStreams()"));
        assertTrue(menuEvents.contains("requests.syncAllWebDavSources(sourceIds)"));
        assertTrue(mainActivity.contains("NetworkMenuEventController networkMenuEventController"));
        assertTrue(mainActivity.contains("new NetworkMenuRenderController(this, networkMenuEventController)"));
        assertFalse(mainActivity.contains("new NetworkMenuRenderController(this, new NetworkMenuRenderController.Listener()"));
        assertFalse(mainActivity.contains("private void playAllStreams("));
        assertFalse(mainActivity.contains("private void playAllWebDavTracks("));
        assertFalse(mainActivity.contains("private void syncAllWebDavSources("));
        assertFalse(mainActivity.contains("private void confirmDeleteAllStreams("));
        assertTrue(trackListRenderer.contains("internal class NetworkTrackListRenderController"));
        assertTrue(trackListRenderer.contains("interface Listener"));
        assertTrue(trackListRenderer.contains("fun renderStreamList("));
        assertTrue(trackListRenderer.contains("fun renderWebDavTrackList("));
        assertTrue(trackListRenderer.contains("fun renderWebDavSourceTrackList("));
        assertTrue(trackListRenderer.contains("listener.renderTrackList("));
        assertTrue(trackListRenderer.contains("MainRoutes.NETWORK_STREAMING"));
        assertTrue(trackListRenderer.contains("private fun trackListLabels("));
        assertTrue(sourcesRenderer.contains("internal class NetworkSourcesRenderController"));
        assertTrue(sourcesRenderer.contains("interface Listener"));
        assertTrue(sourcesRenderer.contains("fun render(languageMode: String, remoteSources: List<RemoteSource>, allTracks: List<Track>)"));
        assertTrue(sourcesRenderer.contains("CollectionRowStateFactory.networkSourceRow("));
        assertTrue(sourcesRenderer.contains("listener.publishNetworkSources(title, rows)"));
        assertTrue(sourcesRenderer.contains("NetworkSourcesScreenFactory.create("));
        assertTrue(sourcesRenderer.contains("viewModel.networkSources"));
        assertTrue(sourcesEvents.contains("internal class NetworkSourcesEventController"));
        assertTrue(sourcesEvents.contains(": NetworkSourcesRenderController.Listener"));
        assertTrue(sourcesEvents.contains("private val routeController: MainRouteController"));
        assertTrue(sourcesEvents.contains("private val statePublisher: MainStatePublisher"));
        assertTrue(sourcesEvents.contains("requestController.syncRemoteSource(sourceId, librarySource.remoteSourceName(sourceId))"));
        assertTrue(sourcesEvents.contains("statePublisher.publishNetworkSources(title, rows)"));
        assertTrue(mainActivity.contains("NetworkSourcesEventController networkSourcesEventController"));
        assertTrue(mainActivity.contains("new NetworkSourcesRenderController(this, viewModel, networkSourcesEventController)"));
        assertFalse(mainActivity.contains("new NetworkSourcesRenderController(this, viewModel, new NetworkSourcesRenderController.Listener()"));
        assertFalse(mainActivity.contains("private void publishNetworkSourcesUiState("));
        assertFalse(mainActivity.contains("private void showEditWebDavDialog("));
        assertFalse(mainActivity.contains("private void confirmDeleteRemoteSource("));
        assertFalse(mainActivity.contains("private void selectRemoteSourceAndNavigateNetworkPage("));
    }

    @Test
    public void mainStatePublisherIsKotlinStateFlowBridge() throws Exception {
        String publisher = read("app/src/main/java/app/echo/next/MainStatePublisher.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainStatePublisher.java"));
        assertTrue(publisher.contains("internal class MainStatePublisher"));
        assertTrue(publisher.contains("private val viewModel: MainActivityViewModel"));
        assertTrue(publisher.contains("fun publishTrackList(title: String, rows: List<TrackRowUiState>)"));
        assertTrue(publisher.contains("viewModel.updateTrackList(title, rows)"));
        assertTrue(publisher.contains("viewModel.updateQueue(rows)"));
        assertTrue(publisher.contains("viewModel.updateNetworkSources(title, rows)"));
    }

    @Test
    public void settingsRenderCoordinatorIsKotlinAndOwnsSettingsPageDispatch() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String coordinator = read("app/src/main/java/app/echo/next/SettingsRenderCoordinator.kt");
        String pageRenderer = read("app/src/main/java/app/echo/next/SettingsPageRenderController.kt");
        String pageEvents = read("app/src/main/java/app/echo/next/SettingsPageEventController.kt");
        String libraryGrouping = read("app/src/main/java/app/echo/next/LibraryGrouping.kt");
        String permissionController = read("app/src/main/java/app/echo/next/MainPermissionController.kt");
        String appPermissions = read("app/src/main/java/app/echo/next/AppPermissions.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/SettingsRenderCoordinator.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/SettingsPageRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/SettingsPageEventController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/LibraryGrouping.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/MainPermissionController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/AppPermissions.java"));
        assertTrue(coordinator.contains("internal class SettingsRenderCoordinator"));
        assertTrue(coordinator.contains("private val settingsStore: MainSettingsStore"));
        assertTrue(coordinator.contains("private val libraryStore: MainLibraryStore"));
        assertTrue(coordinator.contains("private val playbackStore: MainPlaybackStore"));
        assertTrue(coordinator.contains("MainRoutes.SETTINGS_STREAMING_GATEWAY ->"));
        assertTrue(coordinator.contains("renderer.renderStreamingGateway("));
        assertTrue(coordinator.contains("LibraryGrouping.uniqueAlbumCount(allTracks)"));
        assertTrue(coordinator.contains("renderer.renderHome("));
        assertTrue(pageRenderer.contains("internal class SettingsPageRenderController"));
        assertTrue(pageRenderer.contains("interface Listener"));
        assertTrue(pageRenderer.contains("fun renderStreamingGateway(languageMode: String, endpoint: String, configured: Boolean)"));
        assertTrue(pageRenderer.contains("StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT"));
        assertTrue(pageRenderer.contains("StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT"));
        assertTrue(pageRenderer.contains("SettingsScreenFactory.create(context, metrics, actions, scrollState)"));
        assertTrue(pageRenderer.contains("@JvmStatic"));
        assertTrue(pageRenderer.contains("fun playbackSpeedLabel(speed: Float): String"));
        assertTrue(pageRenderer.contains("fun appVolumeLabel(volume: Float): String"));
        assertTrue(pageRenderer.contains("fun lyricsOffsetLabel(offsetMs: Long): String"));
        assertTrue(pageEvents.contains("internal class SettingsPageEventController"));
        assertTrue(pageEvents.contains(": SettingsPageRenderController.Listener"));
        assertTrue(pageEvents.contains("fun interface Navigator"));
        assertTrue(pageEvents.contains("interface LyricsActions"));
        assertTrue(pageEvents.contains("fun interface StreamingGatewayActions"));
        assertTrue(pageEvents.contains("override fun applyStreamingGatewayEndpoint(endpoint: String)"));
        assertTrue(pageEvents.contains("streamingGatewayActions.applyEndpoint(endpoint)"));
        assertTrue(mainActivity.contains("SettingsPageEventController settingsPageEventController"));
        assertFalse(mainActivity.contains("new SettingsPageRenderController.Listener()"));
        assertTrue(libraryGrouping.contains("internal object LibraryGrouping"));
        assertTrue(libraryGrouping.contains("@JvmField val SONGS: String"));
        assertTrue(libraryGrouping.contains("@JvmStatic"));
        assertTrue(libraryGrouping.contains("fun groupTracks("));
        assertTrue(libraryGrouping.contains("fun uniqueArtistCount("));
        assertTrue(permissionController.contains("internal class MainPermissionController"));
        assertTrue(permissionController.contains("interface Listener"));
        assertTrue(permissionController.contains("fun hasAudioPermission(): Boolean"));
        assertTrue(permissionController.contains("fun hasNotificationPermission(): Boolean"));
        assertTrue(permissionController.contains("fun requestNeededPermissions()"));
        assertTrue(permissionController.contains("fun handlePermissionsResult(requestCode: Int): Boolean"));
        assertTrue(appPermissions.contains("internal object AppPermissions"));
        assertTrue(appPermissions.contains("@JvmStatic"));
        assertTrue(appPermissions.contains("fun requestNeededPermissions("));
        assertTrue(appPermissions.contains("fun hasAudioPermission("));
        assertTrue(appPermissions.contains("fun hasNotificationPermission("));
        assertTrue(appPermissions.contains("Manifest.permission.READ_MEDIA_AUDIO"));
    }

    @Test
    public void mainSettingsStoreIsKotlinStateHolder() throws Exception {
        String settingsStore = read("app/src/main/java/app/echo/next/MainSettingsStore.kt");
        String executors = read("app/src/main/java/app/echo/next/MainExecutors.kt");
        String actionsController = read("app/src/main/java/app/echo/next/SettingsActionsController.kt");
        String playlistActionsController = read("app/src/main/java/app/echo/next/PlaylistActionsController.kt");
        String playlistExportController = read("app/src/main/java/app/echo/next/PlaylistExportController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainSettingsStore.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/MainExecutors.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/SettingsActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaylistActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaylistExportController.java"));
        assertTrue(settingsStore.contains("internal class MainSettingsStore"));
        assertTrue(settingsStore.contains("private var themeMode: String = EchoTheme.MODE_SYSTEM"));
        assertTrue(settingsStore.contains("private var languageMode: String = AppLanguage.MODE_SYSTEM"));
        assertTrue(settingsStore.contains("fun load(repository: MusicLibraryRepository)"));
        assertTrue(settingsStore.contains("EchoTheme.normalizeMode(repository.loadThemeMode())"));
        assertTrue(settingsStore.contains("AppLanguage.normalizeMode(repository.loadLanguageMode())"));
        assertTrue(settingsStore.contains("EchoTheme.setMode(themeMode)"));
        assertTrue(settingsStore.contains("fun setPlaybackSpeed(playbackSpeed: Float)"));
        assertTrue(executors.contains("internal class MainExecutors"));
        assertTrue(executors.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(executors.contains("Executors.newFixedThreadPool(2)"));
        assertTrue(executors.contains("Executors.newFixedThreadPool(3)"));
        assertTrue(executors.contains("fun io(task: Runnable)"));
        assertTrue(executors.contains("fun shutdownNow()"));
        assertTrue(actionsController.contains("internal class SettingsActionsController"));
        assertTrue(actionsController.contains("interface Listener"));
        assertTrue(actionsController.contains("fun applyThemeMode(nextMode: String)"));
        assertTrue(actionsController.contains("EchoTheme.normalizeMode(nextMode)"));
        assertTrue(actionsController.contains("executors.io { repository.saveThemeMode(mode) }"));
        assertTrue(actionsController.contains("fun applyPlaybackSpeed(speed: Float)"));
        assertTrue(actionsController.contains("private fun normalizePlaybackSpeed(speed: Float): Float"));
        assertTrue(actionsController.contains("fun applyLyricsOffset(offsetMs: Long)"));
        assertTrue(playlistActionsController.contains("internal class PlaylistActionsController"));
        assertTrue(playlistActionsController.contains("interface Listener"));
        assertTrue(playlistActionsController.contains("fun addToDefaultPlaylist(track: Track?)"));
        assertTrue(playlistActionsController.contains("repository.ensureDefaultPlaylist()"));
        assertTrue(playlistActionsController.contains("mainHandler.post {"));
        assertTrue(playlistActionsController.contains("fun moveSelectedPlaylistTrack(playlistId: Long, track: Track?, trackIndex: Int, direction: Int)"));
        assertTrue(playlistActionsController.contains("repository.movePlaylistTrackAt(playlistId, trackIndex, direction)"));
        assertTrue(playlistExportController.contains("internal class PlaylistExportController"));
        assertTrue(playlistExportController.contains("interface Listener"));
        assertTrue(playlistExportController.contains("private var pendingPlaylistId: Long = -1L"));
        assertTrue(playlistExportController.contains("fun openSelectedPlaylistExportDocument("));
        assertTrue(playlistExportController.contains("fun exportSelectedPlaylistToUri(exportUri: Uri)"));
    }

    @Test
    public void streamingRepositoryProviderUsesInjectableGatewayAndPlaybackAdapter() throws Exception {
        String provider = read("app/src/main/java/app/echo/next/StreamingRepositoryProvider.kt");
        String repository = read("app/src/main/java/app/echo/next/streaming/StreamingRepository.kt");
        String module = read("app/src/main/java/app/echo/next/di/StreamingDataModule.kt");

        assertTrue(provider.contains("StreamingGatewayFactory"));
        assertTrue(provider.contains("StreamingPlaybackTrackAdapter"));
        assertTrue(provider.contains("gatewayFactory.remote(settingsStore.endpoint())"));
        assertFalse(provider.contains("RemoteStreamingGateway("));
        assertTrue(repository.contains("private val playbackTrackAdapter: StreamingPlaybackTrackAdapter"));
        assertTrue(repository.contains("playbackTrackAdapter.toTrack(source, metadata)"));
        assertFalse(repository.contains("StreamingPlaybackAdapter.toTrack(source, metadata)"));
        assertTrue(module.contains("provideStreamingGatewayFactory"));
        assertTrue(module.contains("provideStreamingPlaybackTrackAdapter"));
    }

    @Test
    public void playbackServiceUsesInjectableStreamingHeaderStore() throws Exception {
        String service = read("app/src/main/java/app/echo/next/playback/EchoPlaybackService.java");
        String module = read("app/src/main/java/app/echo/next/di/StreamingDataModule.kt");

        assertTrue(service.contains("StreamingPlaybackHeaderStore"));
        assertTrue(service.contains("streamingPlaybackHeaderStore.forDataPath(track.dataPath)"));
        assertFalse(service.contains("StreamingPlaybackHeaders.INSTANCE"));
        assertTrue(module.contains("provideStreamingPlaybackHeaderStore"));
    }

    @Test
    public void streamingActionsControllerIsKotlinAndCapabilityDriven() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String controller = read("app/src/main/java/app/echo/next/StreamingActionsController.kt");
        String playbackController = read("app/src/main/java/app/echo/next/StreamingResolvedPlaybackController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingResolvedPlaybackController.java"));
        assertTrue(controller.contains("internal class StreamingActionsController"));
        assertTrue(controller.contains(": StreamingSearchActionHandler, StreamingAuthCallbackHandler"));
        assertTrue(controller.contains("StreamingCapabilityResolver.canSearch"));
        assertTrue(controller.contains("StreamingCapabilityResolver.canAuth"));
        assertTrue(controller.contains("StreamingCapabilityResolver.canPlayback"));
        assertTrue(controller.contains("supportedSearchMediaTypes"));
        assertTrue(playbackController.contains("internal class StreamingResolvedPlaybackController"));
        assertTrue(playbackController.contains(": StreamingActionsController.Listener"));
        assertTrue(playbackController.contains("interface Player"));
        assertTrue(playbackController.contains("override fun playResolvedTrack(track: Track)"));
        assertTrue(playbackController.contains("player.playTrackList(ArrayList(listOf(track)), 0)"));
        assertTrue(mainActivity.contains("StreamingResolvedPlaybackController"));
        assertFalse(mainActivity.contains("new StreamingActionsController.Listener()"));
    }

    @Test
    public void streamingSearchRenderControllerIsKotlin() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String controller = read("app/src/main/java/app/echo/next/StreamingSearchRenderController.kt");
        String eventController = read("app/src/main/java/app/echo/next/StreamingSearchEventController.kt");
        String authCallbackController = read("app/src/main/java/app/echo/next/StreamingAuthCallbackController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingSearchRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingSearchEventController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingAuthCallbackController.java"));
        assertTrue(controller.contains("internal class StreamingSearchRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("StreamingSearchScreenFactory.create"));
        assertTrue(controller.contains("viewModel.streaming"));
        assertTrue(controller.contains("StreamingSearchActions"));
        assertTrue(eventController.contains("internal class StreamingSearchEventController"));
        assertTrue(eventController.contains(": StreamingSearchRenderController.Listener"));
        assertTrue(eventController.contains("private val actionsController: StreamingSearchActionHandler"));
        assertTrue(eventController.contains("override fun selectProvider("));
        assertTrue(eventController.contains("actionsController.search(query)"));
        assertTrue(eventController.contains("actionsController.login(provider)"));
        assertTrue(eventController.contains("actionsController.playStreamingTrack(track)"));
        assertTrue(eventController.contains("actionsController.loadNextPage()"));
        assertTrue(authCallbackController.contains("internal class StreamingAuthCallbackController"));
        assertTrue(authCallbackController.contains("private val actionsController: StreamingAuthCallbackHandler"));
        assertTrue(authCallbackController.contains("fun handleInitialIntent(intent: Intent?): Boolean"));
        assertTrue(authCallbackController.contains("fun handleNewIntent(intent: Intent?): Boolean"));
        assertTrue(authCallbackController.contains("actionsController.handleAuthCallback(intent)"));
        assertTrue(mainActivity.contains("StreamingSearchEventController"));
        assertTrue(mainActivity.contains("StreamingAuthCallbackController"));
        assertFalse(mainActivity.contains("new StreamingSearchRenderController.Listener()"));
        assertFalse(mainActivity.contains("streamingActionsController.handleAuthCallback("));
        assertFalse(mainActivity.contains("public void selectProvider(StreamingProviderName provider)"));
        assertFalse(mainActivity.contains("public void playStreamingTrack(StreamingTrack track)"));
    }

    @Test
    public void streamingAuthLauncherIsKotlinAndKeepsNativeLoginBranches() throws Exception {
        String launcher = read("app/src/main/java/app/echo/next/StreamingAuthLauncher.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingAuthLauncher.java"));
        assertTrue(launcher.contains("internal object StreamingAuthLauncher"));
        assertTrue(launcher.contains("StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE"));
        assertTrue(launcher.contains("StreamingWebAuthActivity::class.java"));
        assertTrue(launcher.contains("CustomTabsIntent.Builder()"));
        assertTrue(launcher.contains("Intent.ACTION_VIEW"));
        assertTrue(launcher.contains("Intent.FLAG_ACTIVITY_NEW_TASK"));
    }

    @Test
    public void streamingWebAuthActivityIsKotlinAndKeepsCookieIsolationFlow() throws Exception {
        String activity = read("app/src/main/java/app/echo/next/StreamingWebAuthActivity.kt");
        String manifest = read("app/src/main/AndroidManifest.xml");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingWebAuthActivity.java"));
        assertTrue(activity.contains("class StreamingWebAuthActivity : Activity()"));
        assertTrue(activity.contains("WebView.setDataDirectorySuffix(\"streaming_auth\")"));
        assertTrue(activity.contains("cookieManager.setAcceptThirdPartyCookies(webView, true)"));
        assertTrue(activity.contains("CookieManager.getInstance().flush()"));
        assertTrue(activity.contains("EXTRA_COOKIE_HEADER"));
        assertTrue(activity.contains("echo_auth_callback"));
        assertTrue(activity.contains("withProviderFallback(uri)"));
        assertTrue(activity.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP"));
        assertTrue(manifest.contains("android:name=\".StreamingWebAuthActivity\""));
        assertTrue(manifest.contains("android:exported=\"false\""));
    }

    @Test
    public void streamingGatewaySettingsBoundaryIsKotlin() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String settingsStore = read("app/src/main/java/app/echo/next/StreamingGatewaySettingsStore.kt");
        String controller = read("app/src/main/java/app/echo/next/StreamingGatewayController.kt");
        String eventController = read("app/src/main/java/app/echo/next/StreamingGatewayEventController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingGatewaySettingsStore.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingGatewayController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingGatewayEventController.java"));
        assertTrue(settingsStore.contains("class StreamingGatewaySettingsStore"));
        assertTrue(settingsStore.contains("interface StreamingGatewayEndpointStore"));
        assertTrue(settingsStore.contains("@JvmStatic"));
        assertTrue(settingsStore.contains("fun normalize(value: String?)"));
        assertTrue(controller.contains("internal class StreamingGatewayController"));
        assertTrue(controller.contains("settingsStore.setEndpoint(endpoint)"));
        assertTrue(controller.contains("viewModelBridge.configureStreamingRepository()"));
        assertTrue(controller.contains("viewModelBridge.refreshStreamingProviders()"));
        assertTrue(eventController.contains("internal class StreamingGatewayEventController"));
        assertTrue(eventController.contains(": StreamingGatewayController.ViewModelBridge, StreamingGatewayController.Listener"));
        assertTrue(eventController.contains("override fun configureStreamingRepository()"));
        assertTrue(eventController.contains("override fun refreshStreamingProviders()"));
        assertTrue(eventController.contains("override fun onStreamingGatewayApplied(endpoint: String)"));
        assertTrue(eventController.contains("AppLanguage.text(host.languageMode(), \"streaming.gateway.applied\")"));
        assertTrue(mainActivity.contains("StreamingGatewayEventController"));
        assertFalse(mainActivity.contains("new StreamingGatewayController.ViewModelBridge()"));
        assertFalse(mainActivity.contains("new StreamingGatewayController.Listener()"));
    }

    @Test
    public void passivePlaybackAndAnimationUpdatesKeepTheCurrentViewport() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String playbackActions = read("app/src/main/java/app/echo/next/PlaybackActionsController.kt");
        String nowPlaying = read("app/src/main/java/app/echo/next/ui/NowPlayingScreen.kt");
        String scrollDirection = read("app/src/main/java/app/echo/next/ScrollDirectionFrameLayout.java");
        String contentHost = read("app/src/main/java/app/echo/next/ContentHostController.kt");
        String shellController = read("app/src/main/java/app/echo/next/MainUiShellController.java");
        String searchBar = read("app/src/main/java/app/echo/next/ui/SearchBarController.kt");
        String settingsPage = read("app/src/main/java/app/echo/next/SettingsPageRenderController.kt");
        String settingsScreen = read("app/src/main/java/app/echo/next/ui/SettingsScreen.kt");
        String trackListScreen = read("app/src/main/java/app/echo/next/ui/TrackListScreen.kt");
        String collectionsScreen = read("app/src/main/java/app/echo/next/ui/CollectionsScreen.kt");
        String queueScreen = read("app/src/main/java/app/echo/next/ui/QueueScreen.kt");
        String playlistListScreen = read("app/src/main/java/app/echo/next/ui/PlaylistListScreen.kt");
        String playlistTrackScreen = read("app/src/main/java/app/echo/next/ui/PlaylistTrackScreen.kt");

        assertTrue(mainActivity.contains("private boolean scrollContentToTopOnNextRender"));
        assertFalse(mainActivity.contains("renderAndPersistSelectedTab(true)"));
        assertFalse(mainActivity.contains("if (uiShellController.navigateContentRoute(selectedTab()))"));
        assertTrue(mainActivity.contains("uiShellController.navigateContentRoute(selectedTab());\r\n        renderSelectedTabContent();")
                || mainActivity.contains("uiShellController.navigateContentRoute(selectedTab());\n        renderSelectedTabContent();"));
        assertTrue(mainActivity.contains("if (!route.equals(selectedTab())) {\r\n                    return;\r\n                }")
                || mainActivity.contains("if (!route.equals(selectedTab())) {\n                    return;\n                }"));
        assertTrue(mainActivity.contains("userInitiated && sameTab && previousDirectory.equals(currentDirectoryKey())"));
        assertTrue(mainActivity.contains("private void requestCurrentDirectoryScrollToTop()"));
        assertEquals(2, countOccurrences(mainActivity, "requestCurrentDirectoryScrollToTop()"));
        assertEquals(1, countOccurrences(mainActivity, "scrollContentToTopOnNextRender = true;"));
        assertEquals(1, countOccurrences(mainActivity, "scrollView.scrollTo(0, 0);"));
        assertTrue(mainActivity.contains("settingsRenderCoordinator.scrollToTopOnNextRender();"));
        assertFalse(mainActivity.contains("TAB_NETWORK.equals(selectedTab()) || TAB_SETTINGS.equals(selectedTab())"));
        assertTrue(mainActivity.contains("private void navigateSettingsPage(String page)"));
        assertTrue(mainActivity.contains("routeController.setSettingsPage(page);\r\n        renderAndPersistSelectedTab();")
                || mainActivity.contains("routeController.setSettingsPage(page);\n        renderAndPersistSelectedTab();"));
        assertTrue(settingsPage.contains("private val scrollState = SettingsListScrollState()"));
        assertTrue(settingsPage.contains("fun scrollToTopOnNextRender()"));
        assertTrue(settingsPage.contains("SettingsScreenFactory.create(context, metrics, actions, scrollState)"));
        assertTrue(settingsScreen.contains("rememberLazyListState("));
        assertTrue(settingsScreen.contains("scrollState.save(listState)"));
        assertTrue(settingsScreen.contains("fun scrollToTop()"));
        assertFalse(playbackActions.contains("Result(service.snapshot(), null, false, false, true, true)"));
        assertFalse(nowPlaying.contains("animateScrollToItem"));
        assertTrue(scrollDirection.contains("ev.getRawY()"));
        assertFalse(scrollDirection.contains("ev.getY()"));
        assertTrue(searchBar.contains("if (collapsed.value == value)"));
        assertTrue(searchBar.contains("if (!expanded)"));
        assertFalse(searchBar.contains("AnimatedVisibility"));
        assertFalse(searchBar.contains("slideInVertically"));
        assertFalse(searchBar.contains("slideOutVertically"));
        assertFalse(searchBar.contains("fadeIn"));
        assertFalse(searchBar.contains("fadeOut"));
        assertFalse(searchBar.contains("expandVertically"));
        assertFalse(searchBar.contains("shrinkVertically"));
        assertFalse(trackListScreen.contains("Modifier.animateItem()"));
        assertFalse(collectionsScreen.contains("Modifier.animateItem()"));
        assertFalse(queueScreen.contains("Modifier.animateItem()"));
        assertFalse(playlistListScreen.contains("Modifier.animateItem()"));
        assertFalse(playlistTrackScreen.contains("Modifier.animateItem()"));
        assertEquals(1, countOccurrences(shellController, "animateContentIfPending(view)"));
        assertTrue(shellController.indexOf("void addVirtualContent(View view)") <
                shellController.indexOf("animateContentIfPending(view)"));
        assertTrue(contentHost.contains("view.animate().cancel()"));
        assertTrue(contentHost.contains("resetAnimationState(view)\r\n        view.alpha = 0f")
                || contentHost.contains("resetAnimationState(view)\n        view.alpha = 0f"));
        assertTrue(contentHost.contains("view.setLayerType(View.LAYER_TYPE_HARDWARE, null)"));
        assertTrue(contentHost.contains("withEndAction"));
        assertTrue(contentHost.contains("resetAnimationState(view)"));
        assertTrue(contentHost.contains("const val CONTENT_TRANSITION_MS = 170L"));
    }

    @Test
    public void swipeAndWaveformRegressionsStayFixed() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String shellController = read("app/src/main/java/app/echo/next/MainUiShellController.java");
        String scrollDirection = read("app/src/main/java/app/echo/next/ScrollDirectionFrameLayout.java");
        String nowBar = read("app/src/main/java/app/echo/next/ui/NowBarController.kt");
        String localWaveform = read("app/src/main/java/app/echo/next/ui/PlaybackWaveform.kt");
        String streamingWaveform = read("app/src/main/java/app/echo/next/playback/StreamingWaveformGenerator.java");
        String playbackService = read("app/src/main/java/app/echo/next/playback/EchoPlaybackService.java");
        String waveformMergePolicy = read("app/src/main/java/app/echo/next/playback/PlaybackWaveformMergePolicy.java");

        assertTrue(shellController.contains("String selectedTab();"));
        assertTrue(mainActivity.contains("public String selectedTab()"));
        assertTrue(mainActivity.contains("if (!TAB_LIBRARY.equals(selectedTab())) {\r\n            return false;\r\n        }")
                || mainActivity.contains("if (!TAB_LIBRARY.equals(selectedTab())) {\n            return false;\n        }"));
        int horizontalSwipeIndex = mainActivity.indexOf("private boolean handleContentHorizontalSwipe(boolean next)");
        int nonLibraryReturnIndex = mainActivity.indexOf("if (!TAB_LIBRARY.equals(selectedTab()))", horizontalSwipeIndex);
        int librarySwipeModesIndex = mainActivity.indexOf("ArrayList<String> modes = librarySwipeModes();", horizontalSwipeIndex);
        int setLibraryModeIndex = mainActivity.indexOf("routeController.setLibraryMode(mode);", horizontalSwipeIndex);
        assertTrue(horizontalSwipeIndex >= 0);
        assertTrue(nonLibraryReturnIndex > horizontalSwipeIndex);
        assertTrue(librarySwipeModesIndex > nonLibraryReturnIndex);
        assertTrue(setLibraryModeIndex > librarySwipeModesIndex);
        assertTrue(shellController.contains("String selected = listener.selectedTab();"));
        assertTrue(shellController.contains("listener.onTabSelected(nextTab, false);"));
        assertFalse(shellController.contains("contentRouteHostController.selectedRoute()"));
        assertTrue(scrollDirection.contains("case MotionEvent.ACTION_UP:"));
        assertTrue(scrollDirection.contains("lockHorizontalSwipeIfNeeded(ev.getRawX() - downX, ev.getRawY() - downY);"));
        assertTrue(scrollDirection.contains("horizontalSwipeListener.onHorizontalSwipe(horizontalSwipeLeft);"));
        assertTrue(scrollDirection.contains("longHorizontalSwipeDistance"));
        assertTrue(scrollDirection.contains("private void lockHorizontalSwipeIfNeeded(float dxFromDown, float dyFromDown)"));
        assertTrue(scrollDirection.contains("horizontalSwipeAccepted(dxFromDown)"));
        assertTrue(scrollDirection.contains("SwipeGesturePolicy.horizontalDistanceDominates"));
        assertTrue(scrollDirection.contains("SwipeGesturePolicy.verticalDistanceDominates"));
        assertTrue(scrollDirection.contains("tracking && !horizontalSwipeLocked && !verticalScrollLocked && listener != null"));
        assertTrue(scrollDirection.contains("verticalScrollLocked = true;"));
        assertFalse(scrollDirection.contains("horizontalSwipeListener.onHorizontalSwipe(dxFromDown < 0f);"));

        assertTrue(localWaveform.contains("val energy = FloatArray(barCount)"));
        assertTrue(localWaveform.contains("val rms = sqrt(energy[index] / counts[index])"));
        assertTrue(localWaveform.contains("energy[bucket] += peak * peak"));
        assertTrue(localWaveform.contains("internal object PlaybackWaveformCache"));
        assertTrue(localWaveform.contains("isRemoteUri(contentUriString)"));
        assertTrue(localWaveform.contains("value.startsWith(\"http://\", ignoreCase = true)"));
        assertTrue(localWaveform.contains("value.startsWith(\"https://\", ignoreCase = true)"));
        assertTrue(localWaveform.contains("return dataPath.isNotBlank()"));
        assertFalse(localWaveform.contains("if (peak > peaks[bucket])"));
        assertTrue(streamingWaveform.contains("float[] energy = new float[BAR_COUNT];"));
        assertTrue(streamingWaveform.contains("float rms = (float) Math.sqrt(energy[i] / counts[i]);"));
        assertTrue(streamingWaveform.contains("energy[bucket] += peak * peak;"));
        assertFalse(streamingWaveform.contains("if (peak > peaks[bucket])"));
        assertTrue(streamingWaveform.contains("private static final int MAX_IDLE_CODEC_POLLS"));
        assertTrue(streamingWaveform.contains("int idleCodecPolls = 0;"));
        assertTrue(streamingWaveform.contains("if (madeProgress)"));
        assertTrue(streamingWaveform.contains("++idleCodecPolls >= MAX_IDLE_CODEC_POLLS"));
        assertTrue(streamingWaveform.contains("int actualGeneratedBars = continuousGeneratedBars(counts, generatedBars);"));
        assertTrue(streamingWaveform.contains("static int continuousGeneratedBars(int[] counts, int generatedBars)"));
        assertFalse(streamingWaveform.contains("highestDecodedBucket"));
        assertTrue(streamingWaveform.contains("return new PlaybackWaveformSnapshot(peaks, actualGeneratedBars, cachedProgress);"));
        assertTrue(streamingWaveform.contains("extends MediaDataSource"));
        assertTrue(streamingWaveform.contains("private long nextReadPosition = -1L;"));
        assertTrue(streamingWaveform.contains("private void openAt(long position)"));
        assertTrue(streamingWaveform.contains("if (position == nextReadPosition)"));
        assertTrue(streamingWaveform.contains(".setLength(length - position)"));
        assertTrue(streamingWaveform.contains("if (read <= 0)"));
        assertFalse(streamingWaveform.contains(".setLength(readSize)"));
        assertTrue(playbackService.contains("continuousCachedBytes(cacheKey)"));
        assertTrue(playbackService.contains("cacheDataSourceForTrack(waveformTrack)"));
        assertTrue(playbackService.contains("PlaybackWaveformMergePolicy.merge("));
        assertTrue(waveformMergePolicy.contains("generated.generatedBars >= safeCurrent.generatedBars"));
        assertTrue(waveformMergePolicy.contains("Math.max(safeCurrent.cachedProgress, cachedProgress)"));
        assertFalse(playbackService.contains("Uri.fromFile(span.file)"));
        assertFalse(playbackService.contains("firstCachedSpan("));
        assertTrue(nowBar.contains("serviceHasVisibleWaveform"));
        assertTrue(nowBar.contains("hasVisibleWaveformBars(serviceWaveformBars, serviceGeneratedBars)"));
        assertTrue(nowBar.contains("internal fun hasVisibleWaveformBars"));
        assertTrue(nowBar.contains("waveformCachedProgressForDraw(serviceWaveformCachedProgress, serviceGeneratedBars)"));
        assertTrue(nowBar.contains("internal fun waveformCachedProgressForDraw"));
        assertFalse(nowBar.contains(".take(serviceGeneratedBars).any"));
        assertFalse(nowBar.contains("val cachedProgress = if (serviceGeneratedBars > 0)"));
        assertTrue(nowBar.contains("internal fun visibleWaveformPeakRange"));
        assertTrue(nowBar.contains("remember(waveform, generatedBars)"));
        assertTrue(nowBar.contains(".drawWithCache {"));
        assertTrue(nowBar.contains("onDrawBehind {"));
        assertTrue(nowBar.contains("* 0.12f"));
        assertTrue(nowBar.contains("* 0.94f"));
        assertTrue(nowBar.contains("peaks.sort()"));
        assertTrue(nowBar.contains("visibleMinPeak"));
        assertTrue(nowBar.contains("visibleSpan"));
        assertTrue(nowBar.contains("val generatedWidth = width * (generatedBars.toFloat() / barCount.toFloat())"));
        assertTrue(nowBar.contains("val pendingPlayedWidth = playedWidth - pendingPlayedStart"));
        assertTrue(nowBar.contains("val railHeight = max(2.dp.toPx(), trackHeight * 0.16f)"));
        assertTrue(shellController.contains("setOnTapListener"));
        assertTrue(shellController.contains("collapseNowBarWaveform();"));
        assertTrue(shellController.contains("public boolean dispatchTouchEvent(MotionEvent event)"));
        assertTrue(shellController.contains("downInsideNowBar = isPointInsideNowBar(downX, downY);"));
        assertTrue(shellController.contains("if (!downInsideNowBar)"));
        assertTrue(shellController.contains("&& !isPointInsideNowBar(event.getRawX(), event.getRawY())"));
        assertTrue(shellController.contains("private boolean isPointInsideNowBar(float rawX, float rawY)"));
        assertTrue(nowBar.contains("nowBarBlankCollapseInteraction"));
        assertTrue(nowBar.contains(".clickable(\n                        enabled = waveformExpanded"));
        assertTrue(nowBar.contains("onClick = onCollapseWaveform"));

        String playbackProgress = read("app/src/main/java/app/echo/next/ui/PlaybackProgressState.kt");
        assertTrue(playbackProgress.contains("while (position.value < duration)"));
        assertTrue(playbackProgress.contains("if (position.value != nextPosition)"));
        assertTrue(playbackProgress.contains("if (nextPosition >= duration)"));
    }

    private static String read(String path) throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            Path appCandidate = current.resolve("echo-android").resolve(path);
            if (Files.isRegularFile(appCandidate)) {
                return new String(Files.readAllBytes(appCandidate), StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new java.io.FileNotFoundException(path);
    }

    private static int countOccurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private static boolean exists(String path) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(path))) {
                return true;
            }
            if (Files.exists(current.resolve("echo-android").resolve(path))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
