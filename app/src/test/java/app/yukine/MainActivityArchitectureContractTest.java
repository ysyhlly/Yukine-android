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

/** Boundary alarms. Behavior belongs in focused feature tests. */
public final class MainActivityArchitectureContractTest {
    @Test
    public void legacyActivityHubIsDeletedAndKotlinEntryIsThin() throws Exception {
        Path legacy = root().resolve("app/src/main/java/app/yukine/MainActivityBase.java");
        String activity = read("app/src/main/java/app/yukine/MainActivity.kt");

        assertFalse(Files.exists(legacy));
        assertTrue(activity.contains("class MainActivity : ComponentActivity()"));
        assertTrue(activity.lines().count() < 100);
        assertTrue(activity.contains("features.navigation.bindRoot("));
        assertTrue(activity.contains("features.playback.bindService()"));
        assertFalse(activity.contains("by viewModels()"));
        assertFalse(activity.contains("ViewModelProvider"));
        assertFalse(activity.contains("FeatureBinding("));
        assertFalse(activity.contains("new Main"));
        assertFalse(activity.contains("Factory"));
        assertFalse(activity.contains("Listener"));
        assertFalse(activity.contains("private lateinit var permissionController"));
        assertFalse(activity.contains("private lateinit var documentPickerController"));
        assertFalse(activity.contains("private lateinit var queueActionController"));
        assertFalse(activity.contains("render"));
        assertFalse(activity.contains("bindStateSources"));
        assertFalse(activity.contains("bindEffectListener"));
    }

    @Test
    public void compositionRootOnlyAssemblesFocusedBindings() throws Exception {
        String composition = read("app/src/main/java/app/yukine/MainActivityComposition.kt");
        for (String binding : new String[]{
                "SettingsFeatureBinding(",
                "PlatformFeatureBinding(",
                "NavigationFeatureBinding(",
                "StreamingFeatureBinding(",
                "LibraryFeatureBinding(",
                "PlaybackFeatureBinding(",
                "NetworkFeatureBinding(",
                "OnboardingFeatureBinding("
        }) {
            assertTrue("Missing focused composition: " + binding, composition.contains(binding));
        }
        assertFalse(composition.contains("fun onCreate("));
        assertFalse(composition.contains("fun onResume("));
        assertFalse(composition.contains("fun onDestroy("));
        assertFalse(composition.contains("fun render"));
        assertFalse(composition.contains("class AppEventBus"));
        assertFalse(composition.contains("class Coordinator"));
        assertFalse(composition.contains("class Gateway"));
    }

    @Test
    public void focusedBindingsOwnAssemblyAndRelease() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivity.kt");
        String playback = read("app/src/main/java/app/yukine/PlaybackFeatureBinding.kt");
        String streaming = read("app/src/main/java/app/yukine/StreamingFeatureBinding.java");
        String library = read("app/src/main/java/app/yukine/LibraryFeatureBinding.java");
        String settings = read("app/src/main/java/app/yukine/SettingsFeatureBinding.java");
        String navigation = read("app/src/main/java/app/yukine/NavigationFeatureBinding.kt");
        String network = read("app/src/main/java/app/yukine/NetworkFeatureBinding.java");
        String platform = read("app/src/main/java/app/yukine/PlatformFeatureBinding.java");
        String onboarding = read("app/src/main/java/app/yukine/OnboardingFeatureBinding.java");

        assertFalse(activity.contains("PlaybackServiceConnectionController"));
        assertFalse(activity.contains("StreamingPlaybackController"));
        assertFalse(activity.contains("LibraryRenderOwner"));
        assertFalse(activity.contains("SettingsEffectOwner"));
        assertFalse(activity.contains("NetworkRenderCoordinator"));
        assertFalse(activity.contains("OnboardingOwner"));

        assertTrue(playback.contains("connection = PlaybackServiceConnectionController("));
        assertTrue(playback.contains("playbackViewModel.bind(connection)"));
        assertTrue(playback.contains("fun release()"));
        assertTrue(streaming.contains("void bindPlayback("));
        assertTrue(streaming.contains("void handleNewIntent(Intent intent)"));
        assertTrue(streaming.contains("void release()"));
        assertTrue(streaming.contains("playbackTaskScheduler.shutdownNow()"));
        assertTrue(library.contains("new LibraryRenderOwner("));
        assertTrue(library.contains("void bindPlatform("));
        assertTrue(library.contains("void release()"));
        assertTrue(settings.contains("new SettingsEffectOwner("));
        assertTrue(settings.contains("new SettingsRuntimeApplier("));
        assertTrue(settings.contains("void release()"));
        assertTrue(navigation.contains("fun bindRoot("));
        assertTrue(navigation.contains("fun release()"));
        assertTrue(network.contains("new NetworkRenderCoordinator("));
        assertTrue(network.contains("actionsViewModel.bindListener(null)"));
        assertTrue(platform.contains("new MainPermissionController(activity, permissionResultOwner)"));
        assertTrue(platform.contains("new DocumentPickerController(activity"));
        assertTrue(onboarding.contains("new OnboardingOwner("));
        assertTrue(onboarding.contains("void release()"));
    }

    @Test
    public void activityReleaseDelegatesToEveryFocusedBinding() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivity.kt");
        for (String release : new String[]{
                "features.settings.release()",
                "features.navigation.release()",
                "features.onboarding.release()",
                "features.network.release()",
                "features.library.release()",
                "features.playback.release()",
                "features.streaming.release()",
                "features.platform.shutdown()"
        }) {
            assertTrue("Missing lifecycle delegate: " + release, activity.contains(release));
        }
        assertFalse(activity.contains("bindListener(null)"));
        assertFalse(activity.contains("bindEffectListener(null)"));
        assertFalse(activity.contains("shutdownNow()"));
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
    public void playbackFeatureDoesNotDependOnActivityShell() throws Exception {
        for (Path source : sourceFiles("feature/playback/src/main")) {
            String text = read(source);
            assertFalse(source + " must not reference MainActivity", text.contains("MainActivity"));
            assertFalse(source + " must not reference ComponentActivity", text.contains("ComponentActivity"));
            assertFalse(source + " must not reference Android Activity", text.contains("import android.app.Activity"));
        }
    }

    @Test
    public void navigationHasOneTypedPersistentRouteOwner() throws Exception {
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
        assertTrue(routeController.contains("viewModel.updateRoute"));
    }

    @Test
    public void screensObserveFlowsInsteadOfActivityRenderFanOut() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivity.kt");
        String home = read("app/src/main/java/app/yukine/HomeDashboardViewModel.kt");
        String search = read("app/src/main/java/app/yukine/SearchViewModel.kt");
        String library = read("app/src/main/java/app/yukine/LibraryRenderOwner.kt");
        String network = read("app/src/main/java/app/yukine/NetworkRenderCoordinator.kt");
        String collections = read("app/src/main/java/app/yukine/CollectionsRenderController.kt");

        assertFalse(activity.contains("renderHome"));
        assertFalse(activity.contains("renderQueue"));
        assertFalse(activity.contains("renderSettings"));
        assertFalse(activity.contains("renderSearch"));
        assertFalse(activity.contains("renderNetwork"));
        assertFalse(activity.contains("renderLibrary"));
        assertTrue(home.contains("fun bindStateSources("));
        assertTrue(search.contains("fun bindStateSources("));
        assertTrue(library.contains("fun bindStateSources("));
        assertTrue(network.contains("fun bindStateSources("));
        assertTrue(collections.contains("fun bindStateSources("));
        assertFalse(Files.exists(root().resolve("app/src/main/java/app/yukine/MainTabRenderDispatcher.kt")));
    }

    @Test
    public void playbackCommandsAndReactionsDoNotRouteThroughActivity() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivity.kt");
        String connection = read("app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt");
        String reactions = read("app/src/main/java/app/yukine/PlaybackDomainReactionOwner.kt");
        String binding = read("app/src/main/java/app/yukine/PlaybackFeatureBinding.kt");

        assertFalse(activity.contains("playbackService."));
        assertFalse(activity.contains("PlaybackStateEventController"));
        assertFalse(activity.contains("playTrackListFromHost"));
        assertTrue(connection.contains("PlaybackCommands, SettingsPlaybackServicePort"));
        assertTrue(connection.contains("nextService.snapshot()?.let(::publishReadModel)"));
        assertTrue(reactions.contains(") : PlaybackStateListener"));
        assertTrue(binding.contains("PlaybackDomainReactionOwner("));
        assertTrue(binding.contains("playbackStartController = PlaybackStartController("));
    }

    @Test
    public void streamingAuthHasFocusedOwnerAndOneMutableStateStore() throws Exception {
        String viewModel = read("app/src/main/java/app/yukine/StreamingViewModel.kt");
        String stateOwner = read("app/src/main/java/app/yukine/StreamingFeatureStateOwner.kt");
        String authOwner = read("app/src/main/java/app/yukine/StreamingAuthStateOwner.kt");
        String searchOwner = read("app/src/main/java/app/yukine/StreamingSearchStateOwner.kt");
        String resolutionOwner = read("app/src/main/java/app/yukine/StreamingPlaybackResolutionStateOwner.kt");
        String playlistOwner = read("app/src/main/java/app/yukine/StreamingPlaylistStateOwner.kt");
        String statusFactory = read("app/src/main/java/app/yukine/StreamingStatusTextFactory.kt");
        String actions = read("app/src/main/java/app/yukine/DefaultStreamingSearchActionHandler.kt");
        String playlistController = read("app/src/main/java/app/yukine/StreamingPlaylistController.kt");
        String playbackController = read("app/src/main/java/app/yukine/StreamingPlaybackController.kt");
        String featureBinding = read("app/src/main/java/app/yukine/StreamingFeatureBinding.java");

        assertTrue(viewModel.contains("private val streamingState = StreamingFeatureStateOwner()"));
        assertTrue(viewModel.lines().count() < 140);
        assertFalse(viewModel.contains("MutableStateFlow(StreamingSearchState())"));
        assertFalse(viewModel.contains("streamingState.value ="));
        assertTrue(stateOwner.contains("private val mutableState = MutableStateFlow(initial)"));
        assertTrue(authOwner.contains("class StreamingAuthStateOwner("));
        assertTrue(authOwner.contains("fun refreshProviders(): Job"));
        assertTrue(authOwner.contains("fun completeAuth("));
        assertTrue(authOwner.contains("fun maintainSessions(): Job"));
        assertTrue(searchOwner.contains("class StreamingSearchStateOwner("));
        assertTrue(searchOwner.contains("fun searchAllStreaming("));
        assertTrue(searchOwner.contains("private fun mergeStreamingSearchResults("));
        assertTrue(searchOwner.contains("private fun levenshteinDistance("));
        assertTrue(resolutionOwner.contains("class StreamingPlaybackResolutionStateOwner("));
        assertTrue(resolutionOwner.contains("private var playbackPlanner:"));
        assertTrue(resolutionOwner.contains("private val queueWindowPreResolveInFlight"));
        assertTrue(resolutionOwner.contains("fun recoverStreamingBuffering("));
        assertTrue(resolutionOwner.contains("fun resolveStreamingTrackListForPlayback("));
        assertTrue(playlistOwner.contains("class StreamingPlaylistStateOwner("));
        assertTrue(playlistOwner.contains("private val playlistDataCoordinator"));
        assertTrue(playlistOwner.contains("fun importAccountPlaylistsToLocal("));
        assertTrue(playlistOwner.contains("fun syncStreamingPlaylistToLocal("));
        assertTrue(actions.contains("streamingViewModel.auth.startAuth("));
        assertTrue(actions.contains("streamingViewModel.auth.signOut(provider)"));
        assertTrue(actions.contains("streamingViewModel.search.searchAllStreaming("));
        assertFalse(viewModel.contains("private fun mergeStreamingSearchResults("));
        assertFalse(viewModel.contains("private var streamingPlaybackPlanner"));
        assertFalse(viewModel.contains("private val queueWindowPreResolveInFlight"));
        assertFalse(viewModel.contains("streamingPlaylistDataCoordinator"));
        assertFalse(viewModel.contains("streamingLocalPlaylistOperations"));
        assertFalse(viewModel.contains("fun searchAllStreaming("));
        assertFalse(viewModel.contains("fun completeStreamingAuth("));
        assertFalse(viewModel.contains("fun resolveStreamingTrackListForPlayback("));
        assertFalse(viewModel.contains("fun importStreamingPlaylistToLocal("));
        assertFalse(viewModel.contains("fun fetchDailyRecommendations("));
        assertTrue(playlistController.contains("streamingViewModel.playlists.importStreamingPlaylistToLocal("));
        assertTrue(statusFactory.contains("internal object StreamingStatusTextFactory"));
        assertTrue(playbackController.contains("StreamingStatusTextFactory.playback("));
        assertTrue(playlistController.contains("StreamingStatusTextFactory.playback("));
        assertTrue(featureBinding.contains("StreamingStatusTextFactory.playback("));
    }

    @Test
    public void focusedOwnersRetainTypedPolicies() throws Exception {
        assertTrue(read("app/src/main/java/app/yukine/PermissionResultOwner.kt")
                .contains("permissionResultObserver.onPermissionsChanged()"));
        assertTrue(read("app/src/main/java/app/yukine/DocumentPickerController.kt")
                .contains("private val actions: DocumentPickerActions"));
        assertTrue(read("app/src/main/java/app/yukine/ConfirmationDialogController.kt")
                .contains("private val actions: ConfirmationActions"));
        assertTrue(read("app/src/main/java/app/yukine/BackgroundImageSelectionOwner.kt")
                .contains(": BackgroundImagePickerController.Listener"));
        assertTrue(read("app/src/main/java/app/yukine/OnboardingOwner.kt")
                .contains(": OnboardingController.Listener"));
        assertTrue(read("app/src/main/java/app/yukine/PlaylistMutationOwner.kt")
                .contains(": PlaylistDialogController.Listener"));
        assertTrue(read("app/src/main/java/app/yukine/NowPlayingEffectOwner.kt")
                .contains("is NowPlayingEffect.SwitchSource ->"));
        assertTrue(read("app/src/main/java/app/yukine/UnifiedSearchOwner.kt")
                .contains("if (requestId != streamingPlaybackGeneration)"));
    }

    @Test
    public void nowPlayingPresentationUsesFocusedImmutableSubstates() throws Exception {
        String nowBar = read("feature/ui-common/src/main/java/app/yukine/ui/NowBar.kt");
        String nowPlaying = read("feature/navigation/src/main/java/app/yukine/NowPlayingContracts.kt");
        String factory = read("app/src/main/java/app/yukine/NowBarStateFactory.kt");

        String nowBarState = nowBar.substring(
                nowBar.indexOf("data class NowBarState("),
                nowBar.indexOf("private data class NowBarProgressSlice")
        );
        assertTrue(nowBarState.contains("val track: NowBarTrackState"));
        assertTrue(nowBarState.contains("val progress: NowBarProgressState"));
        assertTrue(nowBarState.contains("val modes: NowBarModesState"));
        assertTrue(nowBarState.contains("val lyrics: NowBarLyricsState"));
        assertTrue(nowBarState.contains("val labels: NowBarLabels"));
        assertTrue(nowBarState.contains("val artwork: NowBarArtworkState"));
        assertFalse(nowBarState.contains("val waveformBars: FloatArray"));
        assertTrue(nowBar.contains("class WaveformSamples private constructor"));
        assertTrue(factory.contains("WaveformSamples.of(playbackState.waveform.bars)"));

        String nowPlayingState = nowPlaying.substring(
                nowPlaying.indexOf("data class NowPlayingUiState("),
                nowPlaying.indexOf("interface NowPlayingScreenStateProvider")
        );
        assertTrue(nowPlayingState.contains("val track: NowPlayingTrackState"));
        assertTrue(nowPlayingState.contains("val progress: NowPlayingProgressState"));
        assertTrue(nowPlayingState.contains("val modes: NowPlayingModesState"));
        assertTrue(nowPlayingState.contains("val lyrics: LyricsUiState"));
        assertTrue(nowPlayingState.contains("val labels: NowPlayingLabelsState"));
        assertTrue(nowPlayingState.contains("val artwork: NowPlayingArtworkState"));
        assertFalse(nowPlayingState.contains("val trackTitle: String"));
        assertFalse(nowPlayingState.contains("val positionMs: Long"));
    }

    private static List<Path> sourceFiles(String relativeDirectory) throws IOException {
        try (Stream<Path> stream = Files.walk(root().resolve(relativeDirectory))) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".kt") || path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
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
            if (Files.isRegularFile(current.resolve("settings.gradle"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate echo-android workspace root");
    }
}
