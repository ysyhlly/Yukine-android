package app.yukine;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Small boundary checks; feature behavior belongs in the feature tests. */
public final class MainActivityArchitectureContractTest {
    @Test
    public void playbackFeatureDoesNotDependOnActivityShell() throws Exception {
        List<Path> sources = sourceFiles("feature/playback/src/main");
        for (Path source : sources) {
            String text = read(source);
            assertFalse(source + " must not reference the Activity shell", text.contains("MainActivityBase"));
            assertFalse(source + " must not reference ComponentActivity", text.contains("ComponentActivity"));
            assertFalse(source + " must not reference Android Activity", text.contains("import android.app.Activity"));
        }
    }

    @Test
    public void featureModulesDoNotDependOnAppModule() throws Exception {
        for (String module : new String[]{
                "core/common/build.gradle",
                "core/model/build.gradle",
                "feature/data/build.gradle",
                "feature/navigation/build.gradle",
                "feature/playback/build.gradle",
                "feature/streaming/build.gradle",
                "feature/ui-common/build.gradle"
        }) {
            assertFalse(module + " must not depend on :app", read(module).contains("project(\":app\")"));
        }
    }

    @Test
    public void navigationHasOnePersistentRouteOwner() throws Exception {
        String hostState = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt");
        String graph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String routeState = read("feature/navigation/src/main/java/app/yukine/NavigationRouteState.kt");
        String routeController = read("app/src/main/java/app/yukine/MainRouteController.kt");
        assertFalse(hostState.contains("selectedTabRoute"));
        assertFalse(graph.contains("hostState.selectedTabRoute"));
        assertTrue(graph.contains("route.selectedTab"));
        assertTrue(routeState.contains("val selectedTab: TabRoute"));
        assertTrue(routeState.contains("val settingsPage: SettingsPage"));
        assertFalse(routeState.contains("val selectedTab: String"));
        assertFalse(routeState.contains("val settingsPage: String"));
        assertTrue(routeController.contains("private val state: NavigationRouteState"));
        assertFalse(routeController.contains("private var state:"));
        assertTrue(routeController.contains("viewModel.updateRoute"));
    }

    @Test
    public void activityDoesNotPersistRoutesDuringEveryRender() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        assertFalse(activity.contains("routeController.persist()"));
        assertFalse(activity.contains("renderSelectedTab"));
        assertFalse(activity.contains("syncNavHostState"));
        assertTrue(activity.contains("private void createNavHostState()"));
    }

    @Test
    public void composeRootIsMountedOnceAndOnboardingIsReactive() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String onboarding = read("app/src/main/java/app/yukine/OnboardingController.kt");
        String app = read("app/src/main/java/app/yukine/EchoApp.kt");
        assertTrue(activity.contains("navHostInstalled"));
        assertTrue(activity.contains("private void installNavHostShell()"));
        assertTrue(activity.contains("queueViewModel == null || navHostInstalled"));
        assertFalse(activity.contains("mountNavHostShell"));
        assertFalse(onboarding.contains("mountNavHostShell"));
        assertTrue(onboarding.contains("StateFlow<OnboardingUiState>"));
        assertTrue(app.contains("onboardingState().collectAsState()"));
    }

    @Test
    public void homeDashboardIsFlowDrivenInsteadOfTabRendered() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String viewModel = read("app/src/main/java/app/yukine/HomeDashboardViewModel.kt");
        String shellModule = read("app/src/main/java/app/yukine/di/ShellModule.kt");
        assertFalse(activity.contains("private void renderHome()"));
        assertFalse(activity.contains("HomeDashboardRenderController"));
        assertFalse(activity.contains("homeDashboardRenderListenerFactory"));
        assertTrue(viewModel.contains("fun bindStateSources("));
        assertTrue(viewModel.contains("combine(libraryInputs, streamingConnected, currentTrackId)"));
        assertFalse(shellModule.contains("MainHomeDashboardRenderListenerFactory"));
    }

    @Test
    public void queueConnectionStateIsFlowDrivenInsteadOfTabRendered() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String queue = read("app/src/main/java/app/yukine/queue/QueueViewModel.kt");
        assertFalse(activity.contains("private void renderQueue()"));
        assertTrue(queue.contains("playbackReadModel.connection"));
        assertTrue(queue.contains("PlaybackConnectionState.Connected"));
    }

    @Test
    public void settingsPageIsRouteFlowDrivenInsteadOfActivityRendered() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String settings = read("app/src/main/java/app/yukine/SettingsViewModel.kt");
        assertFalse(activity.contains("private void renderSettings()"));
        assertFalse(activity.contains("renderPageFromHost"));
        assertTrue(settings.contains("fun bindRouteState("));
        assertTrue(settings.contains(".map { it.selectedTab to it.settingsPage }"));
    }

    @Test
    public void unifiedSearchResultsAreFlowDrivenInsteadOfTabRendered() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String search = read("app/src/main/java/app/yukine/SearchViewModel.kt");
        assertFalse(activity.contains("private void renderSearch()"));
        assertFalse(activity.contains("refreshUnifiedSearch("));
        assertTrue(search.contains("fun bindStateSources("));
        assertTrue(search.contains("routeState.map { it.searchQuery }"));
        assertTrue(search.contains("libraryState.map { it.allTracks }"));
    }

    @Test
    public void networkPagesAreFlowDrivenInsteadOfActivityRendered() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String network = read("app/src/main/java/app/yukine/NetworkRenderCoordinator.kt");
        assertFalse(activity.contains("private void renderNetwork()"));
        assertTrue(network.contains("fun bindStateSources("));
        assertTrue(network.contains("routeState.map(::networkRenderRoute)"));
        assertTrue(network.contains("settingsState.map { it.preferences.languageMode }"));
    }

    @Test
    public void libraryPagesAreFlowDrivenInsteadOfActivityRendered() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String library = read("app/src/main/java/app/yukine/LibraryRenderOwner.kt");
        assertFalse(activity.contains("private void renderLibrary()"));
        assertFalse(activity.contains("private void renderLibraryGroups()"));
        assertFalse(activity.contains("private void renderLibraryPlaylists()"));
        assertTrue(library.contains("fun bindStateSources("));
        assertTrue(library.contains("playback.state.map { it.currentTrack }"));
        assertFalse(library.contains("positionMs"));
    }

    @Test
    public void collectionsAreFlowDrivenAndManualTabDispatcherIsDeleted() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String collections = read("app/src/main/java/app/yukine/CollectionsRenderController.kt");
        assertFalse(activity.contains("private void renderCollections()"));
        assertFalse(activity.contains("renderSelectedTabForNavHostState"));
        assertFalse(activity.contains("MainTabRenderDispatcher"));
        assertTrue(collections.contains("fun bindStateSources("));
        assertTrue(collections.contains("withContext(ioDispatcher) { insightsLoader.load() }"));
        assertTrue(collections.contains("playback.state.map { it.currentTrack }"));
        assertFalse(collections.contains("playback.state.map { it.positionMs }"));
        assertFalse(Files.exists(Paths.get("app/src/main/java/app/yukine/MainTabRenderDispatcher.kt")));
    }

    @Test
    public void trackListPresentationContextIsOutsideActivity() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String publisher = read("app/src/main/java/app/yukine/TrackListStatePublisher.kt");
        assertFalse(activity.contains("renderComposeTrackList"));
        assertFalse(activity.contains("trackListLabels()"));
        assertTrue(publisher.contains("private val libraryState: StateFlow<LibraryStoreState>"));
        assertTrue(publisher.contains("private val settingsState: StateFlow<SettingsState>"));
        assertTrue(publisher.contains("private val playbackReadModel: PlaybackReadModel"));
    }

    @Test
    public void libraryImportPipelineIsOwnedOutsideActivity() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String owner = read("app/src/main/java/app/yukine/LibraryImportOwner.kt");
        assertFalse(activity.contains("private void loadLibrary("));
        assertFalse(activity.contains("private void importSelectedAudio"));
        assertFalse(activity.contains("private void replaceLibrary("));
        assertTrue(owner.contains("class LibraryImportOwner"));
        assertTrue(owner.contains("libraryStore.replaceLibraryAsync"));
        assertTrue(owner.contains("viewModel.parseMissingAudioSpecsJava"));
    }

    @Test
    public void playlistMutationPolicyIsOwnedOutsideActivity() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String owner = read("app/src/main/java/app/yukine/PlaylistMutationOwner.kt");
        assertFalse(activity.contains("createPlaylistDialogController"));
        assertFalse(activity.contains("private void onPlaylist"));
        assertFalse(activity.contains("private void onSelectedPlaylistTrack"));
        assertTrue(owner.contains(": PlaylistDialogController.Listener"));
        assertTrue(owner.contains("routeController.setSelectedPlaylistId"));
        assertTrue(owner.contains("collectionsLoader.loadCollections()"));
    }

    @Test
    public void nowPlayingSourceSwitchPolicyIsOwnedOutsideActivity() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String owner = read("app/src/main/java/app/yukine/NowPlayingSourceSwitchOwner.kt");
        assertFalse(activity.contains("switchNowPlayingSource"));
        assertFalse(activity.contains("switchNowPlayingLibrarySource"));
        assertFalse(activity.contains("completeNowPlayingSourceSwitch"));
        assertFalse(activity.contains("selectedStreamingQuality"));
        assertTrue(owner.contains("private val playbackReadModel: PlaybackReadModel"));
        assertTrue(owner.contains("if (!isLatestRequest(requestId))"));
        assertTrue(owner.contains("replaceCurrentSourceAndResume(current.id, replacement, positionMs)"));
    }

    @Test
    public void activityHasNoFeatureRenderListenerImplementations() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String playlists = read("app/src/main/java/app/yukine/LibraryPlaylistsIntentOwner.kt");
        String network = read("app/src/main/java/app/yukine/NetworkTrackListOwner.kt");
        assertFalse(activity.contains("renderPlaylistTracks"));
        assertFalse(activity.contains("renderTrackList"));
        assertTrue(playlists.contains(": LibraryPlaylistsRenderController.Listener"));
        assertTrue(playlists.contains("LibraryGroupsDestinationState("));
        assertTrue(network.contains(": NetworkTrackListRenderController.Listener"));
        assertTrue(network.contains("private val publishRequest: (NetworkTrackListRequest) -> Unit"));
    }

    @Test
    public void composeMountAndDestinationIntentsAreOutsideActivity() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String mount = read("app/src/main/java/app/yukine/MainNavHostMount.kt");
        String queue = read("app/src/main/java/app/yukine/QueueIntentOwner.kt");
        String downloads = read("app/src/main/java/app/yukine/DownloadsDestinationOwner.kt");
        assertFalse(activity.contains("class ActivityNavHostMount"));
        assertFalse(activity.contains("instanceof QueueIntent"));
        assertFalse(activity.contains("downloadsDestinationActions()"));
        assertTrue(mount.contains(": EchoNavHostMount"));
        assertTrue(queue.contains(": QueueViewModel.IntentListener"));
        assertTrue(downloads.contains("fun actions(): DownloadsDestinationActions"));
    }

    @Test
    public void settingsAndStreamingHaveFocusedDataOwners() throws Exception {
        String settings = read("app/src/main/java/app/yukine/SettingsViewModel.kt");
        String settingsGateway = read("feature/data/src/main/java/app/yukine/data/EchoSettingsStore.java");
        String repository = read("feature/data/src/main/java/app/yukine/data/MusicLibraryRepository.java");
        String streaming = read("app/src/main/java/app/yukine/StreamingViewModel.kt");
        String playlistCoordinator = read("app/src/main/java/app/yukine/StreamingPlaylistDataCoordinator.kt");
        String settingsScreen = read("feature/ui-common/src/main/java/app/yukine/ui/SettingsScreen.kt");
        String settingsDestination = read("feature/navigation/src/main/java/app/yukine/settings/SettingsDestination.kt");
        String routeController = read("app/src/main/java/app/yukine/MainRouteController.kt");

        assertTrue(settings.contains("private val preferenceWriteMutex = Mutex()"));
        assertTrue(settings.contains("preferenceWriteMutex.withLock"));
        assertTrue(settings.contains("fun save(update: SettingsPreferenceUpdate): Boolean"));
        assertTrue(repository.contains("private final EchoSettingsStore settingsStore;"));
        assertTrue(settingsGateway.contains("Owns the settings-table boundary"));
        assertTrue(streaming.contains("streamingPlaylistDataCoordinator"));
        assertTrue(playlistCoordinator.contains("class StreamingPlaylistDataCoordinator"));
        assertTrue(streaming.contains("val state: StreamingSearchState"));
        assertFalse(streaming.contains("var state: StreamingSearchState"));
        assertTrue(settingsScreen.contains("action.icon ?:"));
        assertFalse(settingsScreen.contains("label.contains"));
        assertTrue(settingsDestination.contains("action.isBack"));
        assertFalse(settingsDestination.contains("isSettingsBackAction"));
        assertTrue(routeController.contains("fun settingsPageModel(): SettingsPage"));
    }

    @Test
    public void actionPresentationUsesSemanticMetadata() throws Exception {
        String collections = read("feature/ui-common/src/main/java/app/yukine/ui/CollectionsScreen.kt");
        String trackList = read("feature/ui-common/src/main/java/app/yukine/ui/TrackListScreen.kt");
        String trackListController = read("app/src/main/java/app/yukine/TrackListRenderController.kt");

        assertFalse(collections.contains("iconForCollectionAction"));
        assertTrue(collections.contains("action.icon"));
        assertFalse(trackList.contains("iconForTrackHeaderAction"));
        assertFalse(trackListController.contains("it.label == labels."));
        assertTrue(trackListController.contains("TrackListHeaderActionKind"));
    }

    @Test
    public void rootPackageDoesNotAccumulateBindingFiles() throws Exception {
        assertTrue(countMatching("app/src/main/java/app/yukine", "Bindings") == 0);
        assertTrue(countMatching("app/src/test/java/app/yukine", "Bindings") == 0);
    }

    @Test
    public void activityClearsRetainedViewModelHostCallbacksBeforeRelease() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        int onDestroy = activity.indexOf("protected void onDestroy()");
        int releaseBindings = activity.indexOf("releaseViewModelHostBindings();", onDestroy);
        int releaseConnection = activity.indexOf("playbackServiceConnectionController.release();", onDestroy);

        assertTrue(onDestroy >= 0);
        assertTrue(releaseBindings > onDestroy);
        assertTrue(releaseConnection > releaseBindings);
        assertFalse(activity.contains("lyricsViewModel.bindListener("));
        assertTrue(activity.contains("settingsViewModel.bindEffectListener(null)"));
        assertTrue(activity.contains("networkActionsViewModel.bindListener(null)"));
        assertTrue(activity.contains("queueViewModel.bindIntentListener(null)"));
        assertTrue(activity.contains("streamingViewModel.bindStreamingPlaybackCoordinator(null, null)"));
    }

    private static List<Path> sourceFiles(String relativeDirectory) throws IOException {
        try (Stream<Path> stream = Files.walk(root().resolve(relativeDirectory))) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".kt") || path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static int countMatching(String relativeDirectory, String token) throws IOException {
        try (Stream<Path> stream = Files.walk(root().resolve(relativeDirectory))) {
            return (int) stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(token))
                    .count();
        }
    }

    private static String read(String relativePath) throws IOException {
        return read(root().resolve(relativePath));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path root() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate echo-android workspace root");
    }
}
