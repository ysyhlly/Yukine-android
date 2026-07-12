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
        String routeController = read("app/src/main/java/app/yukine/MainRouteController.kt");
        assertFalse(hostState.contains("selectedTabRoute"));
        assertFalse(graph.contains("hostState.selectedTabRoute"));
        assertTrue(graph.contains("route.selectedTab"));
        assertTrue(routeController.contains("private val state: NavigationRouteState"));
        assertFalse(routeController.contains("private var state:"));
        assertTrue(routeController.contains("viewModel.updateRoute"));
    }

    @Test
    public void activityDoesNotPersistRoutesDuringEveryRender() throws Exception {
        String activity = read("app/src/main/java/app/yukine/MainActivityBase.java");
        assertFalse(activity.contains("routeController.persist()"));
        assertTrue(activity.contains("renderSelectedTabAfterStateChange"));
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
        assertTrue(activity.contains("lyricsViewModel.bindListener(null)"));
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
