package app.yukine;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MainActivityArchitectureContractTest {
    private static final String[] PLAYBACK_UI_BOUNDARY_FORBIDDEN_REFERENCES = {
            "import app.yukine.MainActivity",
            "MainActivity.class",
            "MainActivityBase",
            "import android.app.Activity",
            "import androidx.activity",
            "import androidx.fragment",
            "FragmentActivity",
            "ComponentActivity"
    };

    @Test
    public void playbackServiceDoesNotDependOnMainActivityClass() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");

        assertFalse(service.contains("import app.yukine.MainActivity"));
        assertFalse(service.contains("MainActivity.class"));
        assertFalse(service.contains("MainActivityBase"));
        assertTrue(service.contains("getPackageManager().getLaunchIntentForPackage(getPackageName())"));
        assertTrue(service.contains("intent = new Intent(Intent.ACTION_MAIN)"));
    }

    @Test
    public void rootPackageHasNoMigrationBindingsFiles() throws Exception {
        assertEquals(0, countFiles("app/src/main/java/app/yukine", "*Bindings*"));
        assertEquals(0, countFiles("app/src/test/java/app/yukine", "*Bindings*"));
    }

    @Test
    public void rootPackageDoesNotAddPlaybackFacadeOrBroadCoordinatorFiles() throws Exception {
        assertEquals(0, countFiles("app/src/main/java/app/yukine", "*Facade*"));
        assertEquals(0, countFiles("app/src/test/java/app/yukine", "*Facade*"));
        assertEquals(1, countFiles("app/src/main/java/app/yukine", "*Coordinator*"));
        assertTrue(exists("app/src/main/java/app/yukine/NetworkRenderCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceFacade.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceFacade.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackFacade.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackFacade.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackCoordinator.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingCoordinator.kt"));
    }

    @Test
    public void mainActivityCreatesRouteStoresAndStatusBeforeGatewayBinding() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivityBase.java")
                .replace("\r\n", "\n");
        String onCreate = mainActivity.substring(
                mainActivity.indexOf("    protected void onCreate(Bundle savedInstanceState)"),
                mainActivity.indexOf("    protected abstract MainActivityViewModels createActivityViewModels()")
        );
        String streamingActionGateway = methodBody(mainActivity, "    private MainActivityStreamingActionGateway createStreamingActionGateway()");
        String nowPlayingGateway = methodBody(mainActivity, "    private void initializeNowPlayingGateways()");
        String downloadRequests = methodBody(mainActivity, "    private void initializeDownloadRequests()");
        String libraryGateway = methodBody(mainActivity, "    private void initializeLibraryGateway()");

        int streamingGatewayStep = onCreate.indexOf("        MainActivityStreamingActionGateway streamingActionGateway = createStreamingActionGateway();");
        int streamingOwnersStep = onCreate.indexOf("        initializeStreamingOwners(streamingActionGateway);");
        int routeStoresStep = onCreate.indexOf("        initializeRouteStoresAndStatus();");
        int nowPlayingGatewayStep = onCreate.indexOf("        initializeNowPlayingGateways();");
        int downloadRequestsStep = onCreate.indexOf("        initializeDownloadRequests();");
        int libraryGatewayStep = onCreate.indexOf("        initializeLibraryGateway();");
        int playbackLifecycleStep = onCreate.indexOf("        initializePlaybackLifecycleControllers();");

        assertTrue(streamingGatewayStep >= 0);
        assertTrue(streamingOwnersStep >= 0);
        assertTrue(streamingGatewayStep < streamingOwnersStep);
        assertTrue(routeStoresStep >= 0);
        assertTrue(routeStoresStep < nowPlayingGatewayStep);
        assertTrue(routeStoresStep < downloadRequestsStep);
        assertTrue(routeStoresStep < libraryGatewayStep);
        assertTrue(routeStoresStep < playbackLifecycleStep);
        assertTrue(mainActivity.contains("routeController = new MainRouteController(navigationViewModel)"));
        assertTrue(streamingActionGateway.contains(
                "                () -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),\n"));
        assertTrue(streamingActionGateway.contains(
                "                provider -> streamingPlaylistController.onStreamingLoginSuccess(provider),\n"));
        assertTrue(streamingActionGateway.contains("                    if (streamingManualCookieController != null) {"));
        assertFalse(streamingActionGateway.contains("                settingsStore.languageMode(),\n"));
        assertFalse(streamingActionGateway.contains("                streamingPlaylistController,\n"));
        assertFalse(streamingActionGateway.contains("                streamingManualCookieController,\n"));
        assertTrue(libraryGateway.contains("libraryViewModel.bindGateway(libraryGatewayFactory.create("));
        assertTrue(libraryGateway.contains("                routeController,\n"));
        assertTrue(nowPlayingGateway.contains("                () -> playbackActionController,\n"));
        assertTrue(nowPlayingGateway.contains("                () -> playbackStore,\n"));
        assertTrue(nowPlayingGateway.contains("                () -> playbackService\n"));
        assertFalse(nowPlayingGateway.contains("                playbackActionController,\n"));
        assertFalse(nowPlayingGateway.contains("                playbackStore,\n"));
        assertFalse(nowPlayingGateway.contains("                playbackService\n"));
        assertTrue(downloadRequests.contains("                () -> trackDownloadManager,\n"));
        assertFalse(downloadRequests.contains("                trackDownloadManager,\n"));
        assertTrue(libraryGateway.contains("                () -> documentPickerController.openAudioFilePicker(),\n"));
        assertFalse(libraryGateway.contains("                documentPickerController.openAudioFilePicker(),\n"));
        assertFalse(onCreate.contains("initializeLibraryGateway();\n        initializeRouteStoresAndStatus();"));
    }

    @Test
    public void uiCommonChineseCopyRemainsUtf8Readable() throws Exception {
        String homeDashboard = read("app/src/main/java/app/yukine/ui/HomeDashboardScreen.kt");
        String libraryGroups = read("app/src/main/java/app/yukine/ui/LibraryGroupsScreen.kt");
        String trackList = read("app/src/main/java/app/yukine/ui/TrackListScreen.kt");

        assertContainsUtf8Chinese(homeDashboard, "今天想听点什么？");
        assertContainsUtf8Chinese(homeDashboard, "继续播放");
        assertContainsUtf8Chinese(homeDashboard, "队列");
        assertContainsUtf8Chinese(homeDashboard, "收藏");
        assertContainsUtf8Chinese(homeDashboard, "正在播放");
        assertContainsUtf8Chinese(homeDashboard, "查看全部");
        assertContainsUtf8Chinese(libraryGroups, "播放");
        assertContainsUtf8Chinese(trackList, "歌手介绍");
        assertContainsUtf8Chinese(trackList, "全部专辑");
    }

    @Test
    public void playbackOwnerInventoryAndServiceWiringDoNotGrowWithoutAudit() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java")
                .replace("\r\n", "\n");
        String precacheManager = read("app/src/main/java/app/yukine/playback/PlaybackPrecacheManager.java");
        String visualizationCacheManager = read(
                "feature/playback/src/main/java/app/yukine/playback/PlaybackVisualizationCacheManager.java"
        );
        String mediaCacheOperations = read(
                "feature/playback/src/main/java/app/yukine/playback/manager/PlaybackMediaCacheOperations.java"
        );
        String mediaSourceProvider = read(
                "feature/playback/src/main/java/app/yukine/playback/manager/PlaybackMediaSourceProvider.kt"
        );

        assertEquals(42, countFiles("app/src/main/java/app/yukine/playback", "Playback*Owner.java"));
        assertTrue(
                "EchoPlaybackService should not add more Playback* fields without a narrower owner/interface slice",
                countPrivatePlaybackFields(service) <= 43
        );
        assertTrue(
                "EchoPlaybackService should not grow Playback* wiring fields, including final initialized fields",
                countPrivatePlaybackFieldDeclarations(service, false) <= 53
        );
        assertTrue(
                "EchoPlaybackService should not grow Playback*Owner wiring fields, including final initialized fields",
                countPrivatePlaybackFieldDeclarations(service, true) <= 22
        );
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackMediaSourceResolutionOwner.java"));
        assertFalse(exists("app/src/test/java/app/yukine/playback/PlaybackMediaSourceResolutionOwnerTest.java"));
        assertFalse(exists(
                "feature/playback/src/main/java/app/yukine/playback/PlaybackMediaSourceResolutionOwner.java"));
        assertFalse(exists(
                "feature/playback/src/main/java/app/yukine/playback/manager/PlaybackMediaSourceResolutionOwner.kt"));
        assertFalse(exists(
                "feature/playback/src/test/java/app/yukine/playback/PlaybackMediaSourceResolutionOwnerTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackItemResolver.java"));
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackItemResolver.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/playback/PlaybackItemResolverTest.java"));
        assertFalse(exists("feature/playback/src/main/java/app/yukine/playback/PlaybackItemResolver.java"));
        assertFalse(exists("feature/playback/src/main/java/app/yukine/playback/PlaybackItemResolver.kt"));
        assertFalse(exists("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackItemResolver.java"));
        assertFalse(exists("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackItemResolver.kt"));
        assertFalse(exists("feature/playback/src/test/java/app/yukine/playback/PlaybackItemResolverTest.kt"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*ResolutionOwner.*"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*ResolverOwner.*"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*ResolverFacade.*"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*MediaSource*Owner.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*ResolutionOwner.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*ResolverOwner.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*ResolverFacade.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*MediaSource*Owner.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*ResolutionOwner.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*ResolverOwner.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*ResolverFacade.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*MediaSource*Owner.*"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*CachePolicy.*"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*CacheFacade.*"));
        assertEquals(0, countFiles("app/src/main/java/app/yukine/playback", "Playback*PrecachePolicy.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*CachePolicy.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*CacheFacade.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback", "Playback*PrecachePolicy.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*CachePolicy.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*CacheFacade.*"));
        assertEquals(0, countFiles("feature/playback/src/main/java/app/yukine/playback/manager", "Playback*PrecachePolicy.*"));
        assertFalse(service.contains("PlaybackMediaSourceResolutionOwner"));
        assertFalse(precacheManager.contains("PlaybackMediaSourceResolutionOwner"));
        assertFalse(service.contains("PlaybackItemResolver"));
        assertFalse(service.contains("PlaybackCachePolicy"));
        assertFalse(service.contains("PlaybackCacheFacade"));
        assertFalse(service.contains("PlaybackPrecachePolicy"));
        assertFalse(service.contains("prepareTrackForPlayback("));
        assertFalse(service.contains("mediaSourceForTrack("));
        assertFalse(service.contains("mediaSourcesForTracks("));
        assertFalse(service.contains("mediaItemForTrack("));
        assertFalse(service.contains("playbackMediaItemForTrack("));
        assertFalse(service.contains("cacheKeyForTrack("));
        assertFalse(service.contains("cacheKeyForPrecache("));
        assertFalse(service.contains("cacheDataSourceForTrack("));
        assertFalse(service.contains("contentLengthForCacheKey("));
        assertFalse(service.contains("headersForTrack("));
        assertFalse(service.contains("audioCache("));
        assertTrue(service.contains("PlaybackPrecacheManager.fromMediaSourceProvider("));
        assertFalse(service.contains("PlaybackPrecacheManager.mediaCacheOperationsFromMediaSourceProvider("));
        assertFalse(service.contains("PlaybackPrecacheManager.audioCacheReleaseActionFromMediaSourceProvider("));
        assertFalse(service.contains("PlaybackMediaCacheOperations.fromMediaSourceProvider("));
        assertTrue(precacheManager.contains("private final PlaybackMediaCacheOperations mediaCacheOperations;"));
        assertTrue(precacheManager.contains("private final BiPredicate<MediaItem, Track> mediaItemTrackMatcher;"));
        assertFalse(precacheManager.contains("private final PlaybackMediaSourceProvider"));
        assertTrue(precacheManager.contains("PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider)"));
        assertTrue(precacheManager.contains("mediaSourceProvider.mediaItemMatchesTrackForReuse(mediaItem, track)"));
        assertFalse(precacheManager.contains("prepareTrackForPlayback("));
        assertFalse(precacheManager.contains("mediaSourceForTrack("));
        assertFalse(precacheManager.contains("mediaSourcesForTracks("));
        assertFalse(precacheManager.contains("mediaItemForTrack("));
        assertFalse(precacheManager.contains("playbackMediaItemForTrack("));
        assertFalse(precacheManager.contains("mediaCacheOperationsFromMediaSourceProvider("));
        assertFalse(precacheManager.contains("audioCacheReleaseActionFromMediaSourceProvider("));
        assertFalse(visualizationCacheManager.contains("interface MediaCacheOperations"));
        assertTrue(visualizationCacheManager.contains("private final PlaybackMediaCacheOperations mediaCacheOperations;"));
        assertFalse(visualizationCacheManager.contains("private final PlaybackMediaSourceProvider"));
        assertFalse(visualizationCacheManager.contains("mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider)"));
        assertTrue(visualizationCacheManager.contains("PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider)"));
        assertTrue(mediaCacheOperations.contains("public interface PlaybackMediaCacheOperations"));
        assertTrue(mediaCacheOperations.contains(
                "final class PlaybackMediaSourceProviderCacheOperations implements PlaybackMediaCacheOperations"));
        assertFalse(mediaCacheOperations.contains("androidx.media3.common.MediaItem"));
        assertFalse(mediaCacheOperations.contains("mediaItemMatchesTrackForReuse("));
        assertFalse(mediaCacheOperations.contains("prepareTrackForPlayback("));
        assertFalse(mediaCacheOperations.contains("mediaSourceForTrack("));
        assertFalse(mediaCacheOperations.contains("mediaSourcesForTracks("));
        assertFalse(mediaCacheOperations.contains("mediaItemForTrack("));
        assertFalse(mediaCacheOperations.contains("playbackMediaItemForTrack("));
        assertTrue(mediaSourceProvider.contains("fun prepareTrackForPlayback("));
        assertTrue(mediaSourceProvider.contains("fun mediaSourceForTrack("));
        assertTrue(mediaSourceProvider.contains("fun mediaSourcesForTracks("));
        assertTrue(mediaSourceProvider.contains("fun mediaItemForTrack("));
        assertTrue(mediaSourceProvider.contains("fun playbackMediaItemForTrack("));
    }

    @Test
    public void playbackOwnersDoNotExposePublicMethodsUnlessImplementingInterfaces() throws Exception {
        for (Path source : sourceFiles("app/src/main/java/app/yukine/playback")) {
            String fileName = source.getFileName().toString();
            if (!fileName.startsWith("Playback") || !fileName.endsWith("Owner.java")) {
                continue;
            }
            java.util.List<String> violations = nonOverridePublicMethodLines(source);
            assertTrue(
                    source + " must keep public methods limited to interface overrides: " + violations,
                    violations.isEmpty()
            );
        }
    }

    @Test
    public void playbackServiceAndOwnersDoNotDependOnActivityClasses() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        assertPlaybackSourceDoesNotDependOnActivityUi("EchoPlaybackService", service);

        for (Path source : sourceFiles("feature/playback/src/main/java")) {
            assertPlaybackSourceDoesNotDependOnActivityUi(
                    source.toString(),
                    new String(Files.readAllBytes(source), StandardCharsets.UTF_8)
            );
        }
    }

    @Test
    public void uiAndViewModelsDoNotDependOnConcretePlaybackService() throws Exception {
        for (Path source : sourceFiles("app/src/main/java/app/yukine")) {
            String normalized = source.toString().replace('\\', '/');
            String fileName = source.getFileName().toString();
            if (fileName.contains("ViewModel")
                    || normalized.contains("/app/src/main/java/app/yukine/ui/")
                    || normalized.contains("/app/src/main/java/app/yukine/navigation/")) {
                assertSourceDoesNotContain(source, "EchoPlaybackService");
            }
        }
        for (Path source : sourceFiles("feature/ui-common/src/main/java/app/yukine/ui")) {
            assertSourceDoesNotContain(source, "EchoPlaybackService");
        }
        for (Path source : sourceFiles("feature/navigation/src/main/java")) {
            assertSourceDoesNotContain(source, "EchoPlaybackService");
        }

        String nowPlayingPlaybackGatewayAdapter = read("app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt");
        String nowPlayingPlaybackServiceStarter = read("app/src/main/java/app/yukine/NowPlayingPlaybackServiceStarter.kt");
        String settingsPlaybackServiceControlsAdapter = read("app/src/main/java/app/yukine/SettingsPlaybackServiceControlsAdapter.kt");
        String playbackServiceHostPort = read("app/src/main/java/app/yukine/PlaybackServiceHostPort.kt");
        String mainPlaybackServiceHost = read("app/src/main/java/app/yukine/MainPlaybackServiceHost.kt");
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("EchoPlaybackService"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("NowPlayingPlaybackServicePort"));
        assertTrue(nowPlayingPlaybackServiceStarter.contains("EchoPlaybackService::class.java"));
        assertFalse(read("app/src/main/java/app/yukine/MainActivityBase.java")
                .contains("import app.yukine.playback.EchoPlaybackService;"));
        assertFalse(settingsPlaybackServiceControlsAdapter.contains("EchoPlaybackService"));
        assertTrue(settingsPlaybackServiceControlsAdapter.contains("SettingsPlaybackServicePort"));
        assertFalse(mainPlaybackServiceHost.contains("EchoPlaybackService"));
        assertTrue(playbackServiceHostPort.contains("interface PlaybackServiceHostPort : NowPlayingPlaybackServicePort, SettingsPlaybackServicePort"));
    }

    @Test
    public void viewModelsDoNotDependOnPlaybackServiceHostPorts() throws Exception {
        for (Path source : sourceFiles("app/src/main/java/app/yukine")) {
            if (source.getFileName().toString().contains("ViewModel")) {
                assertViewModelDoesNotDependOnPlaybackServiceHost(source);
            }
        }
        for (Path source : sourceFiles("feature")) {
            if (source.getFileName().toString().contains("ViewModel")) {
                assertViewModelDoesNotDependOnPlaybackServiceHost(source);
            }
        }

        String nowPlayingViewModel = read("app/src/main/java/app/yukine/NowPlayingViewModel.kt");
        String gatewayAdapter = read("app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt");
        String settingsRuntimeApplier = read("app/src/main/java/app/yukine/SettingsRuntimeApplier.kt");
        assertTrue(nowPlayingViewModel.contains("interface NowPlayingPlaybackGateway"));
        assertTrue(nowPlayingViewModel.contains("private var playbackGateway: NowPlayingPlaybackGateway?"));
        assertFalse(nowPlayingViewModel.contains("NowPlayingPlaybackServicePort"));
        assertTrue(gatewayAdapter.contains("interface NowPlayingPlaybackServicePort"));
        assertTrue(settingsRuntimeApplier.contains("SettingsPlaybackServiceControlsProvider"));
        assertFalse(settingsRuntimeApplier.contains("SettingsPlaybackServicePort"));
    }

    @Test
    public void concretePlaybackServiceReferencesStayAtAndroidBoundaries() throws Exception {
        for (Path source : sourceFiles("app/src/main/java/app/yukine")) {
            String normalized = source.toString().replace('\\', '/');
            if (normalized.contains("/app/src/main/java/app/yukine/playback/")) {
                continue;
            }
            String content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            if (content.contains("EchoPlaybackService")) {
                assertTrue(
                        source + " must keep concrete EchoPlaybackService usage at Android service boundaries",
                        isAllowedConcretePlaybackServiceBoundary(normalized)
                );
            }
        }

        for (Path source : sourceFiles("feature/playback/src/main/java")) {
            assertSourceDoesNotContain(source, "EchoPlaybackService");
        }
        for (Path source : sourceFiles("feature/ui-common/src/main/java")) {
            assertSourceDoesNotContain(source, "EchoPlaybackService");
        }
        for (Path source : sourceFiles("feature/navigation/src/main/java")) {
            assertSourceDoesNotContain(source, "EchoPlaybackService");
        }

        String nowPlayingPlaybackGatewayAdapter = read("app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt");
        String settingsPlaybackServiceControlsAdapter = read("app/src/main/java/app/yukine/SettingsPlaybackServiceControlsAdapter.kt");
        String playbackServiceConnectionController = read("app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt");
        String playbackServiceHostController = read("app/src/main/java/app/yukine/PlaybackServiceHostController.kt");
        String mainPlaybackServiceHost = read("app/src/main/java/app/yukine/MainPlaybackServiceHost.kt");
        String nowPlayingPlaybackServiceStarter = read("app/src/main/java/app/yukine/NowPlayingPlaybackServiceStarter.kt");
        assertTrue(playbackServiceConnectionController.contains("Intent(context, EchoPlaybackService::class.java)"));
        assertTrue(nowPlayingPlaybackServiceStarter.contains("Intent(context, EchoPlaybackService::class.java)"));
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("EchoPlaybackService"));
        assertFalse(settingsPlaybackServiceControlsAdapter.contains("EchoPlaybackService"));
        assertFalse(playbackServiceHostController.contains("EchoPlaybackService"));
        assertFalse(mainPlaybackServiceHost.contains("EchoPlaybackService"));
    }

    @Test
    public void mainActivityShellIsKotlinManifestEntryPoint() throws Exception {
        String shell = read("app/src/main/java/app/yukine/MainActivity.kt");
        String legacyBase = read("app/src/main/java/app/yukine/MainActivityBase.java");
        String holder = read("app/src/main/java/app/yukine/MainActivityViewModels.kt");
        String shellModule = read("app/src/main/java/app/yukine/di/ShellModule.kt");
        String platformModule = read("app/src/main/java/app/yukine/di/PlatformModule.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");
        String toggleFavoriteModule = read("app/src/main/java/app/yukine/ToggleFavoriteModule.kt");
        String playbackUiModule = read("app/src/main/java/app/yukine/PlaybackUiModule.kt");
        String streamingModule = read("app/src/main/java/app/yukine/StreamingModule.kt");
        String settingsModule = read("app/src/main/java/app/yukine/SettingsModule.kt");

        assertFalse(exists("app/src/main/java/app/yukine/MainActivity.java"));
        assertFalse(exists("app/src/main/java/app/yukine/ShellViewModel.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/ShellViewModelTest.kt"));
        assertTrue(shell.contains("@AndroidEntryPoint"));
        assertTrue(shell.contains("class MainActivity : MainActivityBase()"));
        assertTrue(shell.contains("private val mainActivityViewModel: MainActivityViewModel by viewModels()"));
        assertTrue(shell.contains("private val networkActionsViewModel: NetworkActionsViewModel by viewModels()"));
        assertFalse(shell.contains("ShellViewModel"));
        assertFalse(shell.contains("shellViewModel"));
        assertTrue(shell.contains("override fun createActivityViewModels(): MainActivityViewModels"));
        assertTrue(legacyBase.contains("public abstract class MainActivityBase extends ComponentActivity"));
        assertTrue(legacyBase.contains("protected abstract MainActivityViewModels createActivityViewModels();"));
        assertTrue(legacyBase.contains("initializeViewModels(createActivityViewModels());"));
        assertTrue(legacyBase.contains("networkActionsViewModel = (NetworkActionsViewModel) viewModels.getNetworkActionsViewModel();"));
        assertFalse(legacyBase.contains("ViewModelProvider"));
        assertTrue(legacyBase.contains("@Inject Handler mainHandler;"));
        assertTrue(legacyBase.contains("@Inject MainExecutors executors;"));
        assertTrue(legacyBase.contains("@Inject StreamingPlaybackTaskScheduler streamingPlaybackTaskScheduler;"));
        assertTrue(legacyBase.contains("@Inject ResolveStreamingPlaybackUseCase resolveStreamingPlaybackUseCase;"));
        assertTrue(legacyBase.contains("@Inject StreamingTrackMatchUseCase streamingTrackMatchUseCase;"));
        assertTrue(legacyBase.contains("@Inject ToggleFavoriteUseCase toggleFavoriteUseCase;"));
        assertTrue(legacyBase.contains("@Inject LoadPlaylistTracksUseCase loadPlaylistTracksUseCase;"));
        assertTrue(legacyBase.contains("@Inject LibraryCollectionGateway libraryCollectionGateway;"));
        assertTrue(legacyBase.contains("@Inject LibraryImportGateway libraryImportGateway;"));
        assertTrue(legacyBase.contains("@Inject LibraryDocumentGateway libraryDocumentGateway;"));
        assertTrue(legacyBase.contains("@Inject LoadSettingsPreferencesUseCase loadSettingsPreferencesUseCase;"));
        assertTrue(legacyBase.contains("@Inject ApplySettingsPreferenceUseCase applySettingsPreferenceUseCase;"));
        assertTrue(legacyBase.contains("@Inject LoadLyricsSettingsUseCase loadLyricsSettingsUseCase;"));
        assertTrue(legacyBase.contains("@Inject LyricsLoader lyricsLoader;"));
        assertTrue(legacyBase.contains("@Inject NetworkActionUseCases networkActionUseCases;"));
        assertTrue(legacyBase.contains("@Inject MainNetworkActionsListenerFactory networkActionsListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainHomeDashboardRenderListenerFactory homeDashboardRenderListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainHeartbeatRecommendationListenerFactory heartbeatRecommendationListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainRecommendationActionCallbacksFactory recommendationActionCallbacksFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingPlaylistDialogListenerFactory streamingPlaylistDialogListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingPlaylistListenerFactory streamingPlaylistListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingPlaylistImportDialogListenerFactory streamingPlaylistImportDialogListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingManualCookieListenerFactory streamingManualCookieListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingActionGatewayFactory streamingActionGatewayFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingSearchActionHandlerFactory streamingSearchActionHandlerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingSearchRenderListenerFactory streamingSearchRenderListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainDocumentPickerListenerFactory documentPickerListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainBackgroundImagePickerListenerFactory backgroundImagePickerListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPermissionListenerFactory permissionListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainTrackListRenderListenerFactory trackListRenderListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject ArtistInfoRepository artistInfoRepository;"));
        assertTrue(legacyBase.contains("@Inject MainLibraryGroupsRenderListenerFactory libraryGroupsRenderListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainCollectionsRenderListenerFactory collectionsRenderListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainQueueRenderListenerFactory queueRenderListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPlaybackStoreFactory playbackStoreFactory;"));
        assertTrue(legacyBase.contains("@Inject MainNowPlayingGatewayFactory nowPlayingGatewayFactory;"));
        assertTrue(legacyBase.contains("@Inject MainNowPlayingPlaybackGatewayFactory nowPlayingPlaybackGatewayFactory;"));
        assertTrue(legacyBase.contains("@Inject MainNowPlayingStateListenerFactory nowPlayingStateListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPlaybackActionListenerFactory playbackActionListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainQueueActionListenerFactory queueActionListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainStreamingPlaybackListenerFactory streamingPlaybackListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPlaybackStartListenerFactory playbackStartListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPlaybackStateEventListenerFactory playbackStateEventListenerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPlaybackServiceHostFactory playbackServiceHostFactory;"));
        assertTrue(legacyBase.contains("@Inject MainLibraryStoreFactory libraryStoreFactory;"));
        assertTrue(legacyBase.contains("@Inject MainPlayHistoryActionControllerFactory playHistoryActionControllerFactory;"));
        assertTrue(legacyBase.contains("@Inject MainLibraryGatewayFactory libraryGatewayFactory;"));
        assertTrue(legacyBase.contains("@Inject MainSettingsStore settingsStore;"));
        assertTrue(legacyBase.contains("@Inject"));
        assertTrue(legacyBase.contains("TrackShareOperations trackShareOperations;"));
        assertFalse(legacyBase.contains("new Handler(Looper.getMainLooper())"));
        assertFalse(legacyBase.contains("new MainExecutors()"));
        assertFalse(legacyBase.contains("new StreamingPlaybackTaskScheduler()"));
        assertFalse(legacyBase.contains("new ResolveStreamingPlaybackUseCase()"));
        assertFalse(legacyBase.contains("new StreamingTrackMatchUseCase("));
        assertFalse(legacyBase.contains("new ToggleFavoriteUseCase("));
        assertFalse(legacyBase.contains("new LoadPlaylistTracksUseCase("));
        assertFalse(legacyBase.contains("new MainPlaybackStore("));
        assertFalse(legacyBase.contains("new MainSettingsStore("));
        assertFalse(legacyBase.contains("new LoadLyricsSettingsUseCase("));
        assertFalse(legacyBase.contains("new MusicLibraryLyricsSettingsOperations(repository)"));
        assertFalse(legacyBase.contains("new LoadTrackLyricsUseCaseLyricsLoader("));
        assertFalse(legacyBase.contains("new LoadTrackLyricsUseCase(new LyricsRepositoryLoadOperations())"));
        assertFalse(legacyBase.contains("new NetworkActionUseCases("));
        assertFalse(legacyBase.contains("new MusicLibraryWebDavSourceOperations(repository)"));
        assertFalse(legacyBase.contains("new MusicLibraryNetworkLibraryOperations(repository)"));
        assertFalse(legacyBase.contains("new DefaultStreamingSearchActionHandler("));
        assertFalse(legacyBase.contains("new TrackShareManagerOperations("));
        assertTrue(holder.contains("data class MainActivityViewModels("));
        assertTrue(holder.contains("val networkActionsViewModel: ViewModel"));
        assertTrue(holder.contains("val statusMessageViewModel: ViewModel"));
        assertFalse(holder.contains("ShellViewModel"));
        assertFalse(holder.contains("shellViewModel"));
        assertTrue(shellModule.contains("@InstallIn(ActivityComponent::class)"));
        assertTrue(shellModule.contains("internal object ShellModule"));
        assertTrue(shellModule.contains("@ActivityScoped"));
        assertTrue(shellModule.contains("fun provideMainExecutors(): MainExecutors"));
        assertTrue(shellModule.contains("fun provideTrackShareOperations("));
        assertTrue(shellModule.contains("TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)"));
        assertTrue(shellModule.contains("fun provideMainHomeDashboardRenderListenerFactory(): MainHomeDashboardRenderListenerFactory"));
        assertTrue(platformModule.contains("@InstallIn(ActivityComponent::class)"));
        assertTrue(platformModule.contains("internal object PlatformModule"));
        assertTrue(platformModule.contains("fun provideMainHandler(): Handler"));
        assertTrue(platformModule.contains("fun provideMainDocumentPickerListenerFactory(): MainDocumentPickerListenerFactory"));
        assertTrue(platformModule.contains("fun provideMainBackgroundImagePickerListenerFactory(): MainBackgroundImagePickerListenerFactory"));
        assertTrue(platformModule.contains("fun provideMainPermissionListenerFactory(): MainPermissionListenerFactory"));
        assertTrue(libraryModule.contains("@InstallIn(ActivityComponent::class)"));
        assertTrue(libraryModule.contains("internal object LibraryModule"));
        assertFalse(libraryModule.contains("fun provideToggleFavoriteUseCase(repository: MusicLibraryRepository): ToggleFavoriteUseCase"));
        assertTrue(toggleFavoriteModule.contains("@InstallIn(ActivityComponent::class, ServiceComponent::class)"));
        assertTrue(toggleFavoriteModule.contains("internal object ToggleFavoriteModule"));
        assertTrue(toggleFavoriteModule.contains("fun provideToggleFavoriteUseCase(repository: MusicLibraryRepository): ToggleFavoriteUseCase"));
        assertTrue(libraryModule.contains("fun provideLoadPlaylistTracksUseCase(repository: MusicLibraryRepository): LoadPlaylistTracksUseCase"));
        assertTrue(libraryModule.contains("fun provideLibrarySearchUseCase(repository: MusicLibraryRepository): LibrarySearchUseCase"));
        assertTrue(libraryModule.contains("LibrarySearchUseCase(MusicLibrarySearchOperations(repository))"));
        assertTrue(libraryModule.contains("fun provideMainLibraryStoreFactory(searchUseCase: LibrarySearchUseCase): MainLibraryStoreFactory"));
        assertTrue(libraryModule.contains("fun provideMainPlayHistoryActionControllerFactory(): MainPlayHistoryActionControllerFactory"));
        assertTrue(libraryModule.contains("fun provideLibraryCollectionGateway(repository: MusicLibraryRepository): LibraryCollectionGateway"));
        assertTrue(libraryModule.contains("fun provideLibraryImportGateway(repository: MusicLibraryRepository): LibraryImportGateway"));
        assertTrue(libraryModule.contains("fun provideLibraryDocumentGateway("));
        assertTrue(libraryModule.contains("fun provideNetworkActionUseCases(repository: MusicLibraryRepository): NetworkActionUseCases"));
        assertTrue(libraryModule.contains("NetworkActionUseCases("));
        assertTrue(libraryModule.contains("TestWebDavSourceUseCase(webDavSourceOperations)"));
        assertTrue(libraryModule.contains("SaveWebDavSourceUseCase(networkLibraryOperations)"));
        assertTrue(libraryModule.contains("fun provideArtistInfoRepository(): ArtistInfoRepository = ArtistInfoRepository()"));
        assertTrue(libraryModule.contains("fun provideMainNetworkActionsListenerFactory(): MainNetworkActionsListenerFactory"));
        assertTrue(libraryModule.contains("fun provideMainTrackListRenderListenerFactory(): MainTrackListRenderListenerFactory"));
        assertTrue(libraryModule.contains("fun provideMainLibraryGroupsRenderListenerFactory(): MainLibraryGroupsRenderListenerFactory"));
        assertTrue(libraryModule.contains("fun provideMainCollectionsRenderListenerFactory(): MainCollectionsRenderListenerFactory"));
        assertTrue(settingsModule.contains("@InstallIn(ActivityComponent::class)"));
        assertTrue(settingsModule.contains("internal object SettingsModule"));
        assertTrue(settingsModule.contains("fun provideMainSettingsStore(): MainSettingsStore"));
        assertTrue(settingsModule.contains("fun provideMainSettingsRuntimeApplierFactory("));
        assertTrue(settingsModule.contains("@ActivityContext context: Context"));
        assertTrue(settingsModule.contains("fun provideLoadLyricsSettingsUseCase("));
        assertTrue(settingsModule.contains("LoadLyricsSettingsUseCase(MusicLibraryLyricsSettingsOperations(repository))"));
        assertTrue(settingsModule.contains("fun provideLyricsLoader(): LyricsLoader"));
        assertTrue(settingsModule.contains("LoadTrackLyricsUseCaseLyricsLoader("));
        assertTrue(settingsModule.contains("LoadTrackLyricsUseCase(LyricsRepositoryLoadOperations())"));
        assertTrue(settingsModule.contains("fun provideLoadSettingsPreferencesUseCase("));
        assertTrue(settingsModule.contains("LoadSettingsPreferencesUseCase(MusicLibrarySettingsPreferenceLoadOperations(repository))"));
        assertTrue(settingsModule.contains("fun provideApplySettingsPreferenceUseCase("));
        assertTrue(settingsModule.contains("ApplySettingsPreferenceUseCase(MusicLibrarySettingsPreferenceOperations(repository))"));
        assertTrue(playbackUiModule.contains("@InstallIn(ActivityComponent::class)"));
        assertTrue(playbackUiModule.contains("internal object PlaybackUiModule"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackStoreFactory(): MainPlaybackStoreFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainNowPlayingGatewayFactory(): MainNowPlayingGatewayFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainNowPlayingPlaybackGatewayFactory("));
        assertTrue(playbackUiModule.contains("fun provideMainNowPlayingStateListenerFactory(): MainNowPlayingStateListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackActionListenerFactory(): MainPlaybackActionListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainQueueActionListenerFactory(): MainQueueActionListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainQueueRenderListenerFactory(): MainQueueRenderListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainStreamingPlaybackListenerFactory(): MainStreamingPlaybackListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackStartListenerFactory(): MainPlaybackStartListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackStateEventListenerFactory(): MainPlaybackStateEventListenerFactory"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackServiceHostFactory(): MainPlaybackServiceHostFactory"));
        assertTrue(streamingModule.contains("@InstallIn(ActivityComponent::class)"));
        assertTrue(streamingModule.contains("internal object StreamingModule"));
        assertTrue(streamingModule.contains("fun provideStreamingPlaybackTaskScheduler(): StreamingPlaybackTaskScheduler"));
        assertTrue(streamingModule.contains("fun provideResolveStreamingPlaybackUseCase(): ResolveStreamingPlaybackUseCase"));
        assertTrue(streamingModule.contains("fun provideStreamingTrackMatchUseCase(repository: MusicLibraryRepository): StreamingTrackMatchUseCase"));
        assertTrue(streamingModule.contains("fun provideMainHeartbeatRecommendationListenerFactory(): MainHeartbeatRecommendationListenerFactory"));
        assertTrue(streamingModule.contains("fun provideMainRecommendationActionCallbacksFactory(): MainRecommendationActionCallbacksFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingPlaylistDialogListenerFactory(): MainStreamingPlaylistDialogListenerFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingPlaylistListenerFactory(): MainStreamingPlaylistListenerFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingPlaylistImportDialogListenerFactory(): MainStreamingPlaylistImportDialogListenerFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingManualCookieListenerFactory(): MainStreamingManualCookieListenerFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingActionGatewayFactory(): MainStreamingActionGatewayFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingSearchActionHandlerFactory(): MainStreamingSearchActionHandlerFactory"));
        assertTrue(streamingModule.contains("fun provideMainStreamingSearchRenderListenerFactory(): MainStreamingSearchRenderListenerFactory"));
    }

    @Test
    public void mainActivityDoesNotDirectlyOwnStreamingRepositoryProvider() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");

        assertFalse(mainActivity.contains("StreamingRepositoryProvider"));
        assertFalse(mainActivity.contains("streamingRepositoryProvider"));
        assertFalse(mainActivity.contains(".setStreamingRepository("));
        assertFalse(mainActivity.contains("viewModel.configureStreamingRepository()"));
        assertTrue(mainActivity.contains("streamingViewModel.configureStreamingRepository()"));
        assertTrue(mainActivity.contains("streamingViewModel.refreshStreamingProviders()"));
        assertFalse(mainActivity.contains("streamingGatewayController.configureRepository()"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewayController.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/StreamingGatewayControllerTest.kt"));
        assertFalse(mainActivity.contains("StreamingGatewayController streamingGatewayController"));
        assertFalse(mainActivity.contains("new StreamingGatewayController("));
    }

    @Test
    public void streamingViewModelUsesRepositorySourceInsteadOfPublicRepositorySetter() throws Exception {
        String mainActivityViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String streamingViewModel = read("app/src/main/java/app/yukine/StreamingViewModel.kt");

        assertTrue(streamingViewModel.contains("@HiltViewModel"));
        assertTrue(streamingViewModel.contains("private val streamingRepositorySource: StreamingRepositorySource"));
        assertTrue(streamingViewModel.contains("fun configureStreamingRepository(): Job"));
        assertTrue(streamingViewModel.contains("fun clearExpiredStreamingCache()"));
        assertTrue(streamingViewModel.contains("streamingRepository = streamingRepositorySource.current()"));
        assertTrue(streamingViewModel.contains("streamingRepository.clearExpiredCache()"));
        assertFalse(mainActivityViewModel.contains("private var streamingRepository: StreamingRepository"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.configureStreamingRepository()"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.clearExpiredStreamingCache()"));
        assertFalse(streamingViewModel.contains("fun setStreamingRepository("));
    }

    @Test
    public void mainActivityDelegatesNetworkOperationsThroughRequestController() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String requestController = read("app/src/main/java/app/yukine/NetworkRequestController.kt");
        String actionsViewModel = read("app/src/main/java/app/yukine/NetworkActionsViewModel.kt");
        String operationSink = read("app/src/main/java/app/yukine/NetworkOperationSink.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");
        String webDavUseCases = read("app/src/main/java/app/yukine/WebDavSourceUseCases.kt");
        String networkUseCases = read("app/src/main/java/app/yukine/NetworkLibraryUseCases.kt");

        assertTrue(mainActivity.contains("NetworkRequestController"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkRequestController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkRequestBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkRequestBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkActionsResultBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkActionsResultBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkDialogEventController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkDialogEventController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkActionsController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkOperationSink.java"));
        assertTrue(requestController.contains("internal class NetworkRequestController("));
        assertTrue(requestController.contains(": NetworkDialogController.Listener"));
        assertTrue(requestController.contains("private val operations: NetworkOperationSink"));
        assertTrue(requestController.contains("interface Labels"));
        assertTrue(requestController.contains("interface Listener"));
        assertTrue(requestController.contains("override fun addStream(title: String, url: String)"));
        assertTrue(requestController.contains("override fun importM3u(url: String)"));
        assertTrue(requestController.contains("override fun updateStream(track: Track, title: String, url: String)"));
        assertTrue(requestController.contains("listener.setStatus(labels.text(\"adding.stream\"))"));
        assertTrue(requestController.contains("operations.syncAllWebDavSources(sourceIds)"));
        assertTrue(mainActivity.contains("new NetworkDialogController(this, dialogLanguageProvider, networkRequestController)"));
        assertTrue(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), key)"));
        assertTrue(mainActivity.contains("status -> statusMessageController.setStatus(status)"));
        assertFalse(mainActivity.contains("new NetworkRequestController.Labels()"));
        assertFalse(mainActivity.contains("new NetworkRequestController.Listener()"));
        assertFalse(mainActivity.contains("new NetworkRequestLabels("));
        assertFalse(mainActivity.contains("new NetworkRequestStatusListener("));
        assertFalse(mainActivity.contains("new NetworkDialogEventController("));
        assertFalse(mainActivity.contains("new NetworkDialogController.Listener()"));
        assertFalse(mainActivity.contains("private void addStreamUrl("));
        assertFalse(mainActivity.contains("private void updateStreamUrl("));
        assertFalse(mainActivity.contains("private void importM3uPlaylist("));
        assertFalse(mainActivity.contains("private void saveWebDavSource("));
        assertTrue(actionsViewModel.contains("internal class NetworkActionsViewModel"));
        assertTrue(actionsViewModel.contains(": ViewModel(), NetworkOperationSink"));
        assertTrue(actionsViewModel.contains("internal data class NetworkActionUseCases"));
        assertTrue(actionsViewModel.contains("private var useCases: NetworkActionUseCases?"));
        assertTrue(actionsViewModel.contains("fun bindUseCases(nextUseCases: NetworkActionUseCases?)"));
        assertTrue(actionsViewModel.contains("fun bindListener(nextListener: Listener?)"));
        assertTrue(actionsViewModel.contains("internal class MainNetworkActionsListener("));
        assertTrue(actionsViewModel.contains(") : NetworkActionsViewModel.Listener"));
        assertTrue(actionsViewModel.contains("nowPlayingViewModel.replaceQueuedTrack(oldTrackId, updated)"));
        assertTrue(actionsViewModel.contains("nowPlayingViewModel.retainTracks(cached)"));
        assertTrue(actionsViewModel.contains("libraryReplacementSink.replaceLibrary(cached, favorites, status)"));
        assertTrue(actionsViewModel.contains("networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_STREAM_LIST)"));
        assertTrue(actionsViewModel.contains("collectionsReloader.loadCollections()"));
        assertTrue(actionsViewModel.contains("statusSink.setStatus(status)"));
        assertFalse(actionsViewModel.contains("private val repository: MusicLibraryRepository"));
        assertFalse(actionsViewModel.contains("TestWebDavSourceUseCase(webDavOperations)"));
        assertFalse(actionsViewModel.contains("AddStreamUrlUseCase(networkLibraryOperations)"));
        assertTrue(actionsViewModel.contains("actions.addStreamUrlUseCase.execute(title, url)"));
        assertTrue(actionsViewModel.contains("actions.saveWebDavSourceUseCase.execute(sourceId, name, baseUrl, username, password, rootPath)"));
        assertTrue(actionsViewModel.contains("listener?.onStreamAdded(result.snapshot.cached, result.snapshot.favorites, status)"));
        assertTrue(actionsViewModel.contains("actions.syncWebDavSourceUseCase.execute(sourceId, sourceName)"));
        assertTrue(actionsViewModel.contains("listener?.onAllWebDavSourcesSynced(result.cached, result.favorites, result.status)"));
        assertTrue(mainActivity.contains("networkActionsViewModel.bindUseCases(networkActionUseCases);"));
        assertFalse(mainActivity.contains("new NetworkActionUseCases("));
        assertTrue(mainActivity.contains("networkActionsViewModel.bindUseCases("));
        assertTrue(mainActivity.contains("networkActionsViewModel.bindListener("));
        assertTrue(mainActivity.contains("networkActionsListenerFactory.create("));
        assertTrue(mainActivity.contains("@Inject MainNetworkActionsListenerFactory networkActionsListenerFactory;"));
        assertFalse(mainActivity.contains("new NetworkActionsViewModel.Listener()"));
        assertFalse(mainActivity.contains("nowPlayingViewModel.replaceQueuedTrack(oldTrackId, updated);"));
        assertFalse(mainActivity.contains("nowPlayingViewModel.retainTracks(cached);"));
        assertTrue(libraryModule.contains("fun provideMainNetworkActionsListenerFactory(): MainNetworkActionsListenerFactory"));
        assertFalse(mainActivity.contains("private void syncUpdatedStreamQueue("));
        assertFalse(mainActivity.contains("private void retainPlaybackTracks("));
        assertTrue(actionsViewModel.contains("libraryReplacementSink.replaceLibrary(cached, favorites, status)"));
        assertTrue(actionsViewModel.contains("networkNavigator.navigateToNetworkPage(MainRoutes.NETWORK_STREAM_LIST)"));
        assertTrue(actionsViewModel.contains("collectionsReloader.loadCollections()"));
        assertFalse(mainActivity.contains("new MusicLibraryWebDavSourceOperations(repository)"));
        assertFalse(mainActivity.contains("new MusicLibraryNetworkLibraryOperations(repository)"));
        assertTrue(networkUseCases.contains("internal interface NetworkLibraryOperations"));
        assertTrue(networkUseCases.contains("internal class AddStreamUrlUseCase"));
        assertTrue(networkUseCases.contains("internal class SaveWebDavSourceUseCase"));
        assertTrue(webDavUseCases.contains("internal interface WebDavSourceOperations"));
        assertTrue(webDavUseCases.contains("internal class SyncWebDavSourceUseCase"));
        assertTrue(webDavUseCases.contains("internal class SyncAllWebDavSourcesUseCase"));
        assertFalse(actionsViewModel.contains("repository.syncRemoteSource(sourceId)"));
        assertFalse(actionsViewModel.contains("repository.addStreamUrl("));
        assertFalse(actionsViewModel.contains("repository.saveWebDavSource("));
        assertTrue(operationSink.contains("internal interface NetworkOperationSink"));
        assertTrue(operationSink.contains("fun updateStreamUrl(oldTrack: Track?, title: String, url: String)"));
        assertTrue(operationSink.contains("fun syncAllWebDavSources(sourceIds: List<Long>)"));
        assertFalse(mainActivity.contains("networkActionsController."));
    }

    @Test
    public void networkOperationStatusLabelsStayOutOfMainActivityRequestMethods() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");

        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"adding.stream\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"updating.stream\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleting.streams\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleting.stream\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleting.source\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"saving.webdav.source\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"syncing.webdav.sources\")"));
    }

    @Test
    public void releaseDocsLinkPlaybackServiceStabilityMatrix() throws Exception {
        String readme = read("README.md");
        String releaseChecklist = read("docs/RELEASE_EXPERIENCE_CHECKLIST.md");
        String matrix = read("docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md");

        assertTrue(readme.contains("docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md"));
        assertTrue(releaseChecklist.contains("PLAYBACK_SERVICE_STABILITY_MATRIX.md"));
        assertTrue(matrix.contains("ACTION_AUDIO_BECOMING_NOISY"));
        assertTrue(matrix.contains("P0"));
        assertTrue(matrix.contains("P1"));
        assertTrue(matrix.contains("P2"));
        assertTrue(matrix.contains("adb logcat"));
        assertTrue(matrix.contains("EchoPlaybackService"));
        assertTrue(matrix.contains("NowBar"));
        assertTrue(matrix.contains("MediaSession"));
        assertTrue(matrix.contains("ExoPlayer"));
        assertTrue(matrix.contains("Playback"));
    }

    @Test
    public void mainActivityDelegatesStatusLocalizationToStatusMessageController() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String viewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String streamingViewModel = read("app/src/main/java/app/yukine/StreamingViewModel.kt");
        String streamingSearchRenderController = read("app/src/main/java/app/yukine/StreamingSearchRenderController.kt");
        String streamingSearchScreen = read("feature/ui-common/src/main/java/app/yukine/ui/StreamingSearchScreen.kt");
        String statusController = read("app/src/main/java/app/yukine/StatusMessageController.java");
        String statusViewModel = read("app/src/main/java/app/yukine/StatusMessageViewModel.kt");
        String statusContracts = read("app/src/main/java/app/yukine/StatusMessageContracts.kt");
        String messageTextResolver = read("app/src/main/java/app/yukine/MessageTextResolver.kt");
        String playlistRenderController = read("app/src/main/java/app/yukine/LibraryPlaylistsRenderController.kt");
        String appLanguage = read("app/src/main/java/app/yukine/AppLanguage.java");

        assertTrue(mainActivity.contains("StatusMessageController statusMessageController"));
        assertTrue(mainActivity.contains("StatusMessageViewModel statusMessageViewModel"));
        assertTrue(mainActivity.contains("statusMessageViewModel = (StatusMessageViewModel) viewModels.getStatusMessageViewModel();"));
        assertFalse(exists("app/src/main/java/app/yukine/StatusMessageHostBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StatusMessageHostBindings.java"));
        assertFalse(mainActivity.contains("private void openLibraryModeFromHome("));
        assertFalse(mainActivity.contains("private void openFavoritesCollectionFromLibrary("));
        assertFalse(mainActivity.contains("private void openCollectionsFromHome("));
        assertFalse(mainActivity.contains("private void openSearchFromHome("));
        assertFalse(mainActivity.contains("private void syncRouteFieldsFromViewModel("));
        assertFalse(mainActivity.contains("private void persistRouteFields("));
        assertFalse(mainActivity.contains("private void selectPlaylistFromCollections("));
        assertFalse(mainActivity.contains("private void openSelectedPlaylistExportDocument("));
        assertTrue(mainActivity.contains("private OnboardingController onboardingController"));
        assertTrue(mainActivity.contains("private boolean showOnboarding()"));
        assertTrue(mainActivity.contains("onboardingController.initialize(repository == null || !repository.loadOnboardingCompleted())"));
        assertTrue(mainActivity.contains("onboardingController.scanLibraryFromOnboarding();"));
        assertTrue(mainActivity.contains("onboardingController.importPlaylistFromOnboarding();"));
        assertTrue(mainActivity.contains("onboardingController.openStreamingFromOnboarding();"));
        assertTrue(mainActivity.contains("onboardingController.finishOnboarding();"));
        assertFalse(mainActivity.contains("private void finishOnboarding()"));
        assertFalse(mainActivity.contains("private void openStreamingFromOnboarding()"));
        assertFalse(mainActivity.contains("private void scanLibraryFromOnboarding()"));
        assertFalse(mainActivity.contains("private void importPlaylistFromOnboarding()"));
        assertFalse(mainActivity.contains("private void completeOnboarding(Runnable afterComplete)"));
        assertFalse(mainActivity.contains("private boolean canFinishOnboarding()"));
        assertFalse(mainActivity.contains("private String onboardingMissingSetupMessage()"));
        assertFalse(mainActivity.contains("private void handleLibraryEvent("));
        assertFalse(mainActivity.contains("private void publishLibraryState("));
        assertFalse(mainActivity.contains("private void dispatchLibraryEvent(LibraryEvent event)"));
        assertFalse(mainActivity.contains("private void syncLibraryViewModelState()"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(status)"));
        assertTrue(mainActivity.contains("statusMessageController.showFeedback("));
        assertTrue(mainActivity.contains("statusMessageController.showFeedback(message);"));
        assertFalse(mainActivity.contains("private void addStateContent("));
        assertFalse(mainActivity.contains("this::addStateContent"));
        assertFalse(mainActivity.contains("addStateContent("));
        assertTrue(statusController.contains("StatusMessageViewModel viewModel"));
        assertTrue(statusController.contains("viewModel.applyStatus(status, languageModeProvider.get())"));
        assertTrue(statusController.contains("void showFeedback(String message)"));
        assertTrue(statusController.contains("if (message == null || message.trim().isEmpty())"));
        assertTrue(statusController.contains("MessageTextResolver textResolver"));
        assertTrue(statusController.contains("void setStatusKey(String key)"));
        assertTrue(statusController.contains("setStatus(textResolver.text(key))"));
        assertFalse(statusContracts.contains("internal fun interface StatusLanguageModeProvider"));
        assertTrue(statusContracts.contains("internal fun interface RawStatusUpdater"));
        assertFalse(statusController.contains("interface Host"));
        assertFalse(statusController.contains("StatusMessageController.Host"));
        assertTrue(messageTextResolver.contains("internal class MessageTextResolver("));
        assertFalse(messageTextResolver.contains("internal fun interface MessageLanguageModeProvider"));
        assertTrue(messageTextResolver.contains("private val languageModeProvider: Supplier<String>"));
        assertTrue(messageTextResolver.contains("AppLanguage.text(languageModeProvider.get(), cleanKey)"));
        assertFalse(mainActivity.contains("new StatusMessageController.Host()"));
        assertFalse(mainActivity.contains("new StatusMessageHostBindings("));
        assertFalse(mainActivity.contains("localizeStatus("));
        assertFalse(mainActivity.contains("private void showActionFeedback("));
        assertFalse(mainActivity.contains("this::showActionFeedback"));
        assertTrue(statusController.contains("final class StatusMessageController"));
        assertTrue(statusController.contains("static String localize("));
        assertTrue(statusController.contains("StatusMessageViewModel.localize(status, languageMode)"));
        assertTrue(statusViewModel.contains("internal class StatusMessageViewModel : ViewModel()"));
        assertTrue(statusViewModel.contains("val state: StateFlow<StatusMessageState>"));
        assertTrue(statusViewModel.contains("fun applyStatus(status: String?, languageMode: String): String"));
        assertTrue(statusViewModel.contains("loading.library"));
        assertTrue(statusViewModel.contains("streaming.cookie.empty"));
        assertTrue(statusViewModel.contains("streaming.choose.login.provider"));
        assertTrue(appLanguage.contains("importing.audio.files"));
        assertTrue(appLanguage.contains("importing.audio.folder"));
        assertTrue(appLanguage.contains("no.audio.files.selected"));
        assertFalse(mainActivity.contains("setStatus(\"Status\")"));
        assertFalse(mainActivity.contains("setStatus(canScan ? \"Loading library\" : \"Audio permission required\")"));
        assertTrue(appLanguage.contains("streaming.manual.cookie"));
        assertTrue(appLanguage.contains("streaming.cookie.saved"));
        assertTrue(appLanguage.contains("streaming.request.failed"));
        assertTrue(appLanguage.contains("streaming.account.playlists.failed"));
        assertTrue(streamingSearchRenderController.contains("streamingRequestFailed = text(languageMode, \"streaming.request.failed\")"));
        assertTrue(streamingSearchScreen.contains("streamingErrorMessage(message, labels)"));
        assertTrue(streamingSearchScreen.contains("\"Could not load account playlists\" -> labels.accountPlaylistsFailed"));
        assertTrue(streamingViewModel.contains("\"Streaming playlist $providerPlaylistId\""));
        assertFalse(viewModel.contains("\\u5a34\\u4f5a"));
        assertFalse(appLanguage.contains("\"Cookie is empty\""));
        assertFalse(appLanguage.contains("\"Cookie saved\""));
        assertFalse(viewModel.contains(utf8ReadAsGbk("姝屽崟瀵煎叆澶辫触")));
        assertFalse(viewModel.contains(utf8ReadAsGbk("鏃犳硶鍔犺浇璐︽埛姝屽崟")));
        assertFalse(viewModel.contains("\u6d41\u5a92\u4f53\u8bf7\u6c42\u5931\u8d25"));
        assertFalse(mainActivity.contains("\"????\""));
        assertTrue(playlistRenderController.contains("AppLanguage.text(languageMode, \"no.tracks.in.playlist\")"));
        assertFalse(mainActivity.contains("addLibraryModeAction(modes, \"??\""));
        assertFalse(mainActivity.contains("playlist.trackCount + \" ???\""));
        assertFalse(mainActivity.contains("\"鏆傛棤姝屽崟\""));
        assertTrue(mainActivity.contains("AppLanguage.text(languageMode, \"folders\")"));
        assertFalse(mainActivity.contains("private String trackCountLabel("));
        assertTrue(playlistRenderController.contains("CollectionRowStateFactory.trackCountLabel(playlist.trackCount, languageMode)"));
    }

    @Test
    public void localArtworkUsesEmbeddedPicturesOnly() throws Exception {
        String scanner = read("app/src/main/java/app/yukine/data/MediaStoreMusicScanner.java");
        String importer = read("app/src/main/java/app/yukine/data/DocumentMusicImporter.java");
        String embeddedArtwork = read("app/src/main/java/app/yukine/data/EmbeddedArtwork.java");
        String artworkLoader = read("app/src/main/java/app/yukine/ui/ArtworkLoader.kt");
        String track = read("app/src/main/java/app/yukine/model/Track.java");

        assertFalse(scanner.contains("content://media/external/audio/albumart"));
        assertFalse(scanner.contains("albumArtUri(albumId)"));
        assertTrue(scanner.contains("EmbeddedArtwork.uriIfEmbeddedPicture(context, uri)"));
        assertTrue(importer.contains("EmbeddedArtwork.uriIfEmbeddedPicture(context, uri)"));
        assertTrue(embeddedArtwork.contains("retriever.getEmbeddedPicture()"));
        assertTrue(embeddedArtwork.contains("LruCache<String, byte[]>"));
        assertTrue(embeddedArtwork.contains("cachedEmbeddedPicture(audioUri)"));
        assertTrue(embeddedArtwork.contains("Arrays.copyOf(cached, cached.length)"));
        assertTrue(embeddedArtwork.contains("echo-embedded-artwork"));
        assertTrue(artworkLoader.contains("EmbeddedArtwork.isEmbeddedArtworkUri(uri)"));
        assertTrue(track.contains("sanitizeAlbumArtUri(albumArtUri)"));
        assertTrue(track.contains("text.startsWith(\"content://media/\") && text.contains(\"/audio/albumart\")"));
    }

    @Test
    public void playbackStateListenerStaysOutOfMainActivity() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String playbackStateEventController = read("app/src/main/java/app/yukine/PlaybackStateEventController.kt");
        String playbackStateUpdateController = read("app/src/main/java/app/yukine/PlaybackStateUpdateController.kt");
        String settingsControls = read("app/src/main/java/app/yukine/SettingsControls.kt");
        String playbackRenderPolicy = read("app/src/main/java/app/yukine/PlaybackRenderPolicy.kt");
        String playbackServiceConnectionController = read("app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt");
        String playbackServiceHostController = read("app/src/main/java/app/yukine/PlaybackServiceHostController.kt");
        String playbackServiceHostPort = read("app/src/main/java/app/yukine/PlaybackServiceHostPort.kt");
        String shellController = read("app/src/main/java/app/yukine/MainUiShellController.java");
        String nowBarStateFactory = read("app/src/main/java/app/yukine/NowBarStateFactory.kt");
        String nowPlayingStateFactory = read("app/src/main/java/app/yukine/NowPlayingStateFactory.kt");
        String nowPlayingScreen = read("app/src/main/java/app/yukine/ui/NowPlayingScreen.kt");
        String echoNowBar = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNowBar.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String navHostState = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt");
        String downloadsContracts = read("feature/navigation/src/main/java/app/yukine/DownloadsDestinationContracts.kt");
        String trackDownloadManager = read("app/src/main/java/app/yukine/TrackDownloadManager.kt");
        String nowPlayingContracts = read("feature/navigation/src/main/java/app/yukine/NowPlayingContracts.kt");
        String nowPlayingDestination = read("feature/navigation/src/main/java/app/yukine/now/NowPlayingDestination.kt");
        String nowPlayingViewModel = read("app/src/main/java/app/yukine/NowPlayingViewModel.kt");
        String mainNowPlayingGateway = read("app/src/main/java/app/yukine/MainNowPlayingGateway.kt");
        String mainPlaybackActionListener = read("app/src/main/java/app/yukine/MainPlaybackActionListener.kt");
        String playbackUiModule = read("app/src/main/java/app/yukine/PlaybackUiModule.kt");
        String nowPlayingPlaybackGatewayAdapter = read("app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.kt");
        String nowPlayingPlaybackServiceStarter = read("app/src/main/java/app/yukine/NowPlayingPlaybackServiceStarter.kt");
        String trackShareLauncher = read("app/src/main/java/app/yukine/TrackShareLauncher.kt");
        String trackShareManager = read("app/src/main/java/app/yukine/TrackShareManager.kt");
        String nowPlayingStateController = read("app/src/main/java/app/yukine/NowPlayingStateController.kt");
        String mainNowPlayingStateListener = read("app/src/main/java/app/yukine/MainNowPlayingStateListener.kt");
        String lyricsViewModel = read("app/src/main/java/app/yukine/LyricsViewModel.kt");
        String queueRenderController = read("app/src/main/java/app/yukine/QueueRenderController.kt");
        String queueActionContracts = read("app/src/main/java/app/yukine/QueueActionContracts.kt");
        String playbackActionController = read("app/src/main/java/app/yukine/PlaybackActionController.kt");
        String playbackStartController = read("app/src/main/java/app/yukine/PlaybackStartController.kt");
        String mainPlaybackStartListener = read("app/src/main/java/app/yukine/MainPlaybackStartListener.kt");
        String mainPlaybackStateEventListener = read("app/src/main/java/app/yukine/MainPlaybackStateEventListener.kt");
        String mainPlaybackServiceHost = read("app/src/main/java/app/yukine/MainPlaybackServiceHost.kt");
        String mainQueueRenderListener = read("app/src/main/java/app/yukine/MainQueueRenderListener.kt");
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackServiceController.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/PlaybackControllerTest.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/playback/FakePlaybackController.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/playback/FakePlaybackControllerTest.kt"));
        String recommendationActionContracts = read("app/src/main/java/app/yukine/RecommendationAction.kt");
        String mainHomeDashboardRenderListener = read("app/src/main/java/app/yukine/MainHomeDashboardRenderListener.kt");
        String mainRecommendationActionCallbacks = read("app/src/main/java/app/yukine/MainRecommendationActionCallbacks.kt");
        String streamingRecommendationViewModel = read("app/src/main/java/app/yukine/StreamingRecommendationViewModel.kt");
        String streamingDailyRecommendationUseCase = read("app/src/main/java/app/yukine/StreamingDailyRecommendationUseCase.kt");
        String heartbeatRecommendationController = read("app/src/main/java/app/yukine/HeartbeatRecommendationController.kt");
        String mainHeartbeatRecommendationListener = read("app/src/main/java/app/yukine/MainHeartbeatRecommendationListener.kt");
        String heartbeatSeedResolver = read("app/src/main/java/app/yukine/HeartbeatRecommendationSeedResolver.kt");
        String heartbeatSeedBinder = read("app/src/main/java/app/yukine/HeartbeatRecommendationSeedBinder.kt");
        String streamingModule = read("app/src/main/java/app/yukine/StreamingModule.kt");

        assertFalse(exists("app/src/main/java/app/yukine/navigation/EchoNavGraph.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/navigation/EchoNavHostState.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/navigation/EchoNavHostBridge.kt"));
        assertTrue(exists("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt"));
        assertTrue(exists("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt"));
        assertTrue(exists("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostBridge.kt"));
        assertFalse(mainActivity.contains("implements PlaybackStateListener"));
        assertFalse(mainActivity.contains("onPlaybackStateChanged("));
        assertFalse(mainActivity.contains("PlaybackStateListener"));
        assertTrue(mainActivity.contains("PlaybackStateEventController"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStateEventController.java"));
        assertFalse(exists("feature/playback/src/main/java/app/yukine/playback/PlaybackStateListener.java"));
        assertTrue(exists("feature/playback/src/main/java/app/yukine/playback/state/PlaybackStateListener.java"));
        assertTrue(playbackStateEventController.contains(": PlaybackStateListener"));
        assertTrue(playbackStateEventController.contains("playbackStore.replaceSnapshot(snapshot)"));
        assertTrue(playbackStateEventController.contains("interface QueueSnapshotSource"));
        assertTrue(playbackStateEventController.contains("playbackStore.publish(queueSnapshotSource.queueSnapshot())"));
        assertFalse(playbackStateEventController.contains("EchoPlaybackService"));
        assertFalse(playbackStateEventController.contains("serviceQueueSource.service()"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStateEventBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStateEventBindings.java"));
        assertTrue(settingsControls.contains("internal fun interface SettingsSelectedTabProvider"));
        assertFalse(mainActivity.contains("new PlaybackStateEventController.ServiceQueueSource()"));
        assertFalse(mainActivity.contains("public EchoPlaybackService service()"));
        assertFalse(mainActivity.contains("new PlaybackStateEventBindings("));
        assertFalse(mainActivity.contains("new PlaybackStateEventController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainPlaybackStateEventListenerFactory playbackStateEventListenerFactory;"));
        assertTrue(mainActivity.contains("this::playbackQueueSnapshot"));
        assertEquals(1, countOccurrences(mainActivity, "playbackService.queueSnapshot()"));
        assertTrue(mainActivity.contains("playbackStateEventListenerFactory.create("));
        assertTrue(mainActivity.contains("this::selectedTab"));
        assertTrue(mainActivity.contains("this::resolveCurrentStreamingQueueTrackIfNeeded"));
        assertTrue(mainPlaybackStateEventListener.contains("internal class MainPlaybackStateEventListener("));
        assertTrue(mainPlaybackStateEventListener.contains(": PlaybackStateEventController.Listener"));
        assertTrue(mainPlaybackStateEventListener.contains("playbackSettingsSaver.savePlaybackSettings(playbackSpeed, appVolume)"));
        assertTrue(mainPlaybackStateEventListener.contains("currentStreamingTrackResolver.resolveCurrentStreamingTrackIfNeeded()"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackStateEventListenerFactory(): MainPlaybackStateEventListenerFactory"));
        assertFalse(mainActivity.contains("new PlaybackServiceQueueSourceBindings("));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStateUpdateController.java"));
        assertTrue(playbackStateUpdateController.contains("internal object PlaybackStateUpdateController"));
        assertTrue(playbackStateUpdateController.contains("data class Result("));
        assertTrue(playbackStateUpdateController.contains("PlaybackRenderPolicy.shouldRenderForPlaybackChange"));
        assertFalse(mainActivity.contains("private PlaybackStateUpdateController playbackStateUpdateController;"));
        assertFalse(mainActivity.contains("new PlaybackStateUpdateController()"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackRenderPolicy.java"));
        assertTrue(playbackRenderPolicy.contains("internal object PlaybackRenderPolicy"));
        assertTrue(playbackRenderPolicy.contains("fun shouldRenderForPlaybackChange("));
        assertTrue(playbackRenderPolicy.contains("MainRoutes.TAB_COLLECTIONS"));
        assertTrue(playbackRenderPolicy.contains("previous.errorMessage != next.errorMessage"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceConnectionController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceHostController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceHostBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingGatewayBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingPlaybackGatewayAdapter.java"));
        assertTrue(queueActionContracts.contains("internal fun interface QueuePlaybackActionResultApplier"));
        assertFalse(queueActionContracts.contains("QueuePlaybackServiceAvailability"));
        assertFalse(queueActionContracts.contains("QueueStatusProvider"));
        assertFalse(queueActionContracts.contains("QueueStatusSink"));
        assertFalse(queueActionContracts.contains("QueueNoArgAction"));
        assertTrue(playbackServiceConnectionController.contains("internal class PlaybackServiceConnectionController"));
        assertTrue(playbackServiceConnectionController.contains("private val playbackStateListener: PlaybackStateListener"));
        assertTrue(playbackServiceConnectionController.contains("private var service: PlaybackServiceHostPort? = null"));
        assertFalse(playbackServiceConnectionController.contains("private var service: EchoPlaybackService? = null"));
        assertTrue(playbackServiceHostPort.contains("fun registerListener(listener: PlaybackStateListener?)"));
        assertTrue(playbackServiceHostPort.contains("fun unregisterListener(listener: PlaybackStateListener?)"));
        assertTrue(playbackServiceConnectionController.contains("object : ServiceConnection"));
        assertTrue(playbackServiceConnectionController.contains("nextService.registerListener(playbackStateListener)"));
        assertTrue(playbackServiceConnectionController.contains("disconnectedService?.unregisterListener(playbackStateListener)"));
        assertTrue(playbackServiceHostController.contains("internal class PlaybackServiceHostController"));
        assertTrue(playbackServiceHostController.contains(": PlaybackServiceConnectionController.Listener"));
        assertTrue(playbackServiceHostController.contains("interface Host"));
        assertTrue(playbackServiceHostController.contains("fun attachPlaybackService(service: PlaybackServiceHostPort)"));
        assertTrue(playbackServiceHostController.contains("host.attachPlaybackService(service)"));
        assertTrue(playbackServiceHostController.contains("service.setPlaybackSpeed(host.playbackSpeed())"));
        assertTrue(playbackServiceHostController.contains("host.playPendingTracksIfNeeded()"));
        assertTrue(playbackServiceHostController.contains("host.resetPlaybackStore()"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceHostBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackServiceHostBindings.java"));
        assertFalse(mainActivity.contains("private PlaybackServiceHostController playbackServiceHostController;"));
        assertTrue(mainActivity.contains("PlaybackServiceHostController playbackServiceHostController = new PlaybackServiceHostController("));
        assertFalse(mainActivity.contains("new PlaybackServiceHostBindings("));
        assertFalse(mainActivity.contains("new PlaybackServiceHostController.Host() {"));
        assertTrue(mainActivity.contains("private PlaybackServiceHostPort playbackService;"));
        assertFalse(mainActivity.contains("private EchoPlaybackService playbackService;"));
        assertFalse(mainActivity.contains("public void attachPlaybackService(EchoPlaybackService service)"));
        assertTrue(mainActivity.contains("@Inject MainPlaybackServiceHostFactory playbackServiceHostFactory;"));
        assertTrue(mainActivity.contains("playbackServiceHostFactory.create("));
        assertTrue(mainActivity.contains("service -> playbackService = service"));
        assertTrue(mainActivity.contains("() -> playbackService = null"));
        assertTrue(mainActivity.contains("() -> playbackStore.reset()"));
        assertTrue(mainActivity.contains("new PlaybackServiceHostController("));
        assertFalse(mainActivity.contains("private final class ActivityPlaybackServiceHost implements PlaybackServiceHostController.Host"));
        assertTrue(mainPlaybackServiceHost.contains("internal class MainPlaybackServiceHost("));
        assertTrue(mainPlaybackServiceHost.contains(": PlaybackServiceHostController.Host"));
        assertFalse(mainPlaybackServiceHost.contains("EchoPlaybackService"));
        assertTrue(mainPlaybackServiceHost.contains("fun attachPlaybackService(service: PlaybackServiceHostPort)"));
        assertTrue(mainPlaybackServiceHost.contains("playbackServiceAttacher.attachPlaybackService(service)"));
        assertTrue(mainPlaybackServiceHost.contains("service.setAppVisible(true)"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackServiceHostFactory(): MainPlaybackServiceHostFactory"));
        assertFalse(mainActivity.contains("new PlaybackServiceConnectionController.Listener()"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionsController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStartController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStartBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackStartBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/DailyRecommendationController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/DailyRecommendationBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/DailyRecommendationController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/DailyRecommendationBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/HeartbeatRecommendationController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/HeartbeatRecommendationBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NowBarStateFactory.java"));
        assertTrue(nowBarStateFactory.contains("internal object NowBarStateFactory"));
        assertTrue(nowBarStateFactory.contains("@JvmStatic"));
        assertTrue(nowBarStateFactory.contains("fun create("));
        assertFalse(nowBarStateFactory.contains("import app.yukine.playback.EchoPlaybackService"));
        assertTrue(nowBarStateFactory.contains("favoriteIds.contains(track.id)"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"now.playing\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"close\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"show.lyrics\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"show.artwork\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"no.lyrics.found\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"previous\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"play\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"pause\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"next\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"tab.queue\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"more\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"add.to.playlist\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"playback.progress\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"expand.playback.waveform\")"));
        assertTrue(nowBarStateFactory.contains("PlaybackErrorMessageLocalizer.localize(playbackState.errorMessage, languageMode)"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"playback.error.title\")"));
        assertTrue(nowBarStateFactory.contains("AppLanguage.text(languageMode, \"retry.playback\")"));
        assertFalse(exists("app/src/main/java/app/yukine/ui/NowPlayingOverlayController.kt"));
        assertTrue(nowPlayingScreen.contains("fun NowPlayingScreen("));
        assertTrue(nowPlayingScreen.contains("defaultImmersive: Boolean = false"));
        assertTrue(nowPlayingScreen.contains("LyricsPanel("));
        assertTrue(echoNowBar.contains("fun EchoNowBar("));
        assertTrue(echoNowBar.contains("state: NowBarState"));
        assertTrue(echoNowBar.contains("onSeek: SeekAction"));
        assertTrue(echoNowBar.contains("NowBar("));
        assertFalse(echoNowBar.contains("NowPlayingViewModel"));
        assertFalse(echoNowBar.contains("viewModel.onEvent("));
        assertTrue(navGraph.contains("nowBarStateProvider.nowBarState.collectAsState()"));
        assertTrue(navGraph.contains("playbackSnapshotProvider.playbackSnapshot.collectAsState()"));
        assertTrue(navGraph.contains("Runnable { nowPlayingEventHandler(NowPlayingEvent.Previous) }"));
        assertTrue(navGraph.contains("Runnable { nowPlayingEventHandler(NowPlayingEvent.PlayPause) }"));
        assertTrue(navGraph.contains("Runnable { nowPlayingEventHandler(NowPlayingEvent.Next) }"));
        assertTrue(navGraph.contains("Runnable { nowPlayingEventHandler(NowPlayingEvent.ToggleFavorite) }"));
        assertTrue(navGraph.contains("Runnable { nowPlayingEventHandler(NowPlayingEvent.ToggleShuffle) }"));
        assertTrue(navGraph.contains("Runnable { nowPlayingEventHandler(NowPlayingEvent.CycleRepeatMode) }"));
        assertTrue(navGraph.contains("nowPlayingEventHandler(NowPlayingEvent.SeekTo(positionMs))"));
        assertTrue(navGraph.contains("hostState.nowPlayingStateProvider.switchSource(track, provider, providerTrackId, quality)"));
        assertFalse(navGraph.contains("hostState.nowPlayingViewModel.switchSource("));
        assertTrue(navHostState.contains("val nowPlayingStateProvider: NowPlayingScreenStateProvider"));
        assertTrue(navHostState.contains("nowPlayingStateProvider.uiState"));
        assertTrue(navHostState.contains("val queueStateProvider: QueueDestinationStateProvider"));
        assertTrue(navGraph.contains("QueueDestination("));
        assertTrue(navGraph.contains("hostState.queueStateProvider"));
        assertFalse(navGraph.contains("queueViewModel"));
        assertFalse(navHostState.contains("import app.yukine.MainActivityViewModel"));
        assertFalse(navHostState.contains("val mainViewModel: MainActivityViewModel"));
        assertTrue(navHostState.contains("val homeDashboardState: StateFlow<HomeDashboardDestinationState>"));
        assertFalse(navHostState.contains("import app.yukine.HomeDashboardViewModel"));
        assertFalse(navHostState.contains("val homeDashboardViewModel: HomeDashboardViewModel"));
        assertTrue(navGraph.contains("HomeDestination(hostState.homeDashboardState, activeDownload, playbackQuality, audioMotion)"));
        assertFalse(navGraph.contains("hostState.homeDashboardViewModel"));
        assertTrue(navHostState.contains("val routeState: StateFlow<NavigationRouteState>"));
        assertFalse(navHostState.contains("import app.yukine.NavigationViewModel"));
        assertFalse(navHostState.contains("val navigationViewModel: NavigationViewModel"));
        assertTrue(navGraph.contains("hostState.routeState.collectAsState()"));
        assertFalse(navGraph.contains("hostState.navigationViewModel.state.collectAsState()"));
        assertTrue(navHostState.contains("val collectionsStateProvider: CollectionsDestinationStateProvider"));
        assertFalse(navHostState.contains("import app.yukine.CollectionsViewModel"));
        assertFalse(navHostState.contains("val collectionsViewModel: CollectionsViewModel"));
        assertTrue(navGraph.contains("CollectionsDestination(hostState.collectionsStateProvider)"));
        assertFalse(navGraph.contains("CollectionsDestination(hostState.collectionsViewModel)"));
        assertTrue(navHostState.contains("val libraryGroupsState: StateFlow<LibraryGroupsDestinationState>"));
        assertTrue(navHostState.contains("val libraryTrackListState: StateFlow<LibraryTrackListDestinationState>"));
        assertFalse(navHostState.contains("import app.yukine.LibraryViewModel"));
        assertFalse(navHostState.contains("val libraryViewModel: LibraryViewModel"));
        assertTrue(navGraph.contains("groupsState = hostState.libraryGroupsState"));
        assertTrue(navGraph.contains("trackListState = hostState.libraryTrackListState"));
        assertFalse(navGraph.contains("hostState.libraryViewModel."));
        assertTrue(navHostState.contains("val networkMenuState: StateFlow<NetworkMenuUiState>"));
        assertTrue(navHostState.contains("val networkSourcesState: StateFlow<NetworkSourcesUiState>"));
        assertFalse(navHostState.contains("import app.yukine.NetworkMenuViewModel"));
        assertFalse(navHostState.contains("import app.yukine.NetworkSourcesViewModel"));
        assertFalse(navHostState.contains("val networkMenuViewModel: NetworkMenuViewModel"));
        assertFalse(navHostState.contains("val networkSourcesViewModel: NetworkSourcesViewModel"));
        assertTrue(navGraph.contains("hostState.networkMenuState.collectAsState()"));
        assertTrue(navGraph.contains("sourcesState = hostState.networkSourcesState"));
        assertFalse(navGraph.contains("hostState.networkMenuViewModel"));
        assertFalse(navGraph.contains("hostState.networkSourcesViewModel"));
        assertTrue(navHostState.contains("val streamingState: StateFlow<StreamingSearchState>"));
        assertFalse(navHostState.contains("import app.yukine.StreamingViewModel"));
        assertFalse(navHostState.contains("val streamingViewModel: StreamingViewModel"));
        assertTrue(navGraph.contains("hostState.streamingState.collectAsState()"));
        assertFalse(navGraph.contains("hostState.streamingViewModel"));
        assertTrue(navHostState.contains("val settingsState: StateFlow<SettingsDestinationState>"));
        assertTrue(navHostState.contains("val settingsChromeState: StateFlow<SettingsChromeState>"));
        assertFalse(navHostState.contains("import app.yukine.SettingsState"));
        assertTrue(navHostState.contains("val settingsScrollState: SettingsListScrollState"));
        assertFalse(navHostState.contains("import app.yukine.SettingsViewModel"));
        assertFalse(navHostState.contains("val settingsViewModel: SettingsViewModel"));
        assertTrue(navGraph.contains("hostState.settingsChromeState.collectAsState()"));
        assertTrue(navGraph.contains("state = hostState.settingsState"));
        assertTrue(navGraph.contains("scrollState = hostState.settingsScrollState"));
        assertFalse(navGraph.contains("settingsState.preferences"));
        assertFalse(navGraph.contains("hostState.settingsViewModel"));
        assertTrue(navHostState.contains("val downloadsState: StateFlow<DownloadsUiState>"));
        assertTrue(navHostState.contains("val downloadsOpenDirectoryRequests: Flow<Unit>"));
        assertTrue(navHostState.contains("val downloadsActions: DownloadsDestinationActions"));
        assertFalse(navHostState.contains("import app.yukine.DownloadsViewModel"));
        assertFalse(navHostState.contains("val downloadsViewModel: DownloadsViewModel"));
        assertTrue(navGraph.contains("state = hostState.downloadsState"));
        assertTrue(navGraph.contains("openDirectoryRequests = hostState.downloadsOpenDirectoryRequests"));
        assertTrue(navGraph.contains("actions = hostState.downloadsActions"));
        assertFalse(navGraph.contains("hostState.downloadsViewModel"));
        assertTrue(navHostState.contains("val searchState: StateFlow<UnifiedSearchUiState>"));
        assertFalse(navHostState.contains("import app.yukine.SearchViewModel"));
        assertFalse(navHostState.contains("val searchViewModel: SearchViewModel"));
        assertTrue(navGraph.contains("searchState = hostState.searchState"));
        assertFalse(navGraph.contains("searchViewModel.uiState"));
        assertTrue(navHostState.contains("val trackDownloadController: TrackDownloadController?"));
        assertFalse(navHostState.contains("import app.yukine.TrackDownloadManager"));
        assertFalse(navHostState.contains("val trackDownloadManager: TrackDownloadManager"));
        assertTrue(downloadsContracts.contains("interface TrackDownloadController"));
        assertTrue(downloadsContracts.contains("data class TrackDownloadActionResult"));
        assertFalse(exists("app/src/main/java/app/yukine/TrackDownloadController.kt"));
        assertFalse(trackDownloadManager.contains("interface TrackDownloadController"));
        assertFalse(trackDownloadManager.contains("data class TrackDownloadActionResult"));
        assertTrue(navGraph.contains("hostState.trackDownloadController"));
        assertFalse(navGraph.contains("hostState.trackDownloadManager"));
        assertFalse(navHostState.contains("val nowPlayingViewModel: NowPlayingScreenStateProvider"));
        assertTrue(navHostState.contains("val playbackSnapshotProvider: PlaybackSnapshotProvider"));
        assertFalse(navHostState.contains("import app.yukine.PlaybackViewModel"));
        assertFalse(navHostState.contains("val playbackViewModel: PlaybackViewModel"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingScreenStateProvider.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/now/NowPlayingDestination.kt"));
        assertTrue(nowPlayingContracts.contains("sealed interface NowPlayingEvent"));
        assertTrue(nowPlayingContracts.contains("data class NowPlayingUiState"));
        assertTrue(nowPlayingContracts.contains("interface NowPlayingScreenStateProvider : NowBarStateProvider"));
        assertTrue(nowPlayingDestination.contains("fun NowPlayingDestination("));
        assertTrue(nowPlayingDestination.contains("StreamingDataPathMetadata.provider(track.dataPath)"));
        assertFalse(nowPlayingViewModel.contains("sealed interface NowPlayingEvent"));
        assertFalse(nowPlayingViewModel.contains("data class NowPlayingUiState"));
        assertTrue(nowPlayingViewModel.contains("data class PlaybackActionResultUi"));
        assertTrue(nowPlayingViewModel.contains("interface NowPlayingPlaybackGateway"));
        assertTrue(nowPlayingViewModel.contains("class NowPlayingViewModel : ViewModel()"));
        assertFalse(nowPlayingViewModel.contains("NowPlayingEventHandler"));
        assertTrue(nowPlayingViewModel.contains("fun bindPlaybackGateway(nextGateway: NowPlayingPlaybackGateway?)"));
        assertTrue(nowPlayingViewModel.contains("fun onEvent(event: NowPlayingEvent)"));
        assertTrue(nowPlayingViewModel.contains("fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi"));
        assertTrue(nowPlayingViewModel.contains("player.playQueue(tracks, index)"));
        assertTrue(nowPlayingViewModel.contains("fun warmPlaybackTrack(track: Track?)"));
        assertTrue(nowPlayingViewModel.contains("player.warmPlaybackTrack(track)"));
        assertFalse(nowPlayingViewModel.contains("fun precacheTrack(track: Track?)"));
        assertFalse(nowPlayingViewModel.contains("player.precacheTrack(track)"));
        assertTrue(nowPlayingViewModel.contains("fun appendToQueue(tracks: List<Track>?)"));
        assertTrue(nowPlayingViewModel.contains("player.appendToQueue(tracks)"));
        assertTrue(nowPlayingViewModel.contains("fun replaceCurrentTrackAndResume(track: Track?, positionMs: Long)"));
        assertTrue(nowPlayingViewModel.contains("player.replaceCurrentTrackAndResume(track, positionMs)"));
        assertFalse(nowPlayingViewModel.contains("!player.serviceConnected() || updated == null"));
        assertFalse(nowPlayingViewModel.contains("!player.serviceConnected() || track == null"));
        assertFalse(nowPlayingViewModel.contains("!player.serviceConnected() || tracks.isNullOrEmpty()"));
        assertFalse(nowPlayingViewModel.contains("!player.serviceConnected() || tracksToKeep == null"));
        assertFalse(nowPlayingViewModel.contains("import app.yukine.playback.EchoPlaybackService"));
        assertFalse(nowPlayingViewModel.contains("EchoPlaybackService.ACTION_PREVIOUS"));
        assertFalse(nowPlayingViewModel.contains("EchoPlaybackService.REPEAT_ALL"));
        assertFalse(nowPlayingViewModel.contains("import app.yukine.playback.PlaybackServiceActions"));
        assertFalse(nowPlayingViewModel.contains("import app.yukine.playback.service.PlaybackServiceActions"));
        assertTrue(nowPlayingViewModel.contains("import app.yukine.playback.PlaybackRepeatMode"));
        assertFalse(nowPlayingViewModel.contains("startPlaybackService(PlaybackServiceActions"));
        assertTrue(nowPlayingViewModel.contains("player.setRepeatMode(PlaybackRepeatMode.REPEAT_ALL)"));
        assertTrue(nowPlayingViewModel.contains("data class OpenAddToPlaylist(val track: Track)"));
        assertTrue(nowPlayingViewModel.contains("emitEffect(NowPlayingEffect.OpenAddToPlaylist(track))"));
        assertFalse(shellController.contains("void onNowPlayingEvent(NowPlayingEvent event);"));
        assertFalse(shellController.contains("listener.onNowPlayingEvent(event);"));
        assertFalse(shellController.contains("void onAddCurrentToPlaylist();"));
        assertFalse(shellController.contains("listener.onAddCurrentToPlaylist();"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingEffectController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingEffectController.java"));
        assertFalse(exists("app/src/test/java/app/yukine/NowPlayingEffectControllerTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingEffectBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingEffectBindings.java"));
        assertFalse(mainActivity.contains("NowPlayingEffectController nowPlayingEffectController"));
        assertFalse(mainActivity.contains("new NowPlayingEffectController("));
        assertFalse(mainActivity.contains("nowPlayingEffectController.handle("));
        assertTrue(mainActivity.contains("List<NowPlayingEffect> effects = nowPlayingViewModel.drainEffects();"));
        assertTrue(mainActivity.contains("effect == NowPlayingEffect.OpenQueue.INSTANCE"));
        assertTrue(mainActivity.contains("playlistDialogController.showAddToPlaylist(openAddToPlaylist.getTrack())"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(showMessage.getMessage())"));
        assertFalse(mainActivity.contains("new NowPlayingEffectBindings("));
        assertFalse(exists("app/src/main/java/app/yukine/TrackShareLauncher.java"));
        assertTrue(trackShareLauncher.contains("internal class TrackShareLauncher"));
        assertTrue(trackShareLauncher.contains("internal class TrackShareManagerOperations("));
        assertFalse(trackShareLauncher.contains("internal fun interface TrackShareLanguageProvider"));
        assertFalse(trackShareLauncher.contains("internal fun interface TrackShareStyleProvider"));
        assertTrue(trackShareLauncher.contains("private val languageProvider: () -> String"));
        assertTrue(trackShareLauncher.contains("private val shareStyleProvider: () -> String"));
        assertTrue(trackShareLauncher.contains("fun share(track: Track?)"));
        assertTrue(trackShareLauncher.contains("AppLanguage.text(languageProvider(), \"no.track.selected\")"));
        assertTrue(trackShareLauncher.contains("TrackShareStyle.normalize(shareStyleProvider())"));
        assertTrue(trackShareLauncher.contains("nativeMusicShareManager?.share(activity, track, payload) == true"));
        assertTrue(trackShareLauncher.contains("activityStarter.startActivity(Intent.createChooser(send, \"分享到\"))"));
        assertTrue(trackShareLauncher.contains("Log.w(TAG, \"Unable to share track\", error)"));
        assertTrue(mainActivity.contains("private TrackShareLauncher trackShareLauncher;"));
        assertTrue(mainActivity.contains("new TrackShareLauncher("));
        assertTrue(mainActivity.contains("trackShareOperations,"));
        assertFalse(mainActivity.contains("new TrackShareManagerOperations(trackShareManager, nativeMusicShareManager)"));
        assertFalse(mainActivity.contains("TrackShareManager trackShareManager;"));
        assertFalse(mainActivity.contains("NativeMusicShareManager nativeMusicShareManager;"));
        assertTrue(trackShareManager.contains("StreamingDataPathMetadata.provider(track.dataPath)"));
        assertTrue(trackShareManager.contains("StreamingDataPathMetadata.providerTrackId(track.dataPath)"));
        assertFalse(trackShareManager.contains("StreamingPlaybackAdapter"));
        assertTrue(mainActivity.contains("trackShareLauncher.share(shareTrack.getTrack())"));
        assertFalse(mainActivity.contains("private void shareTrack(final Track track)"));
        assertFalse(mainActivity.contains("Intent.createChooser(send,"));
        assertFalse(mainActivity.contains("Unable to share track"));
        assertTrue(mainActivity.contains("downloadRequestController.downloadTrack(downloadTrack.getTrack())"));
        assertTrue(mainActivity.contains("switchNowPlayingSource(switchSource)"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingStateController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingStateBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingStateBindings.java"));
        assertTrue(nowPlayingStateController.contains("internal class NowPlayingStateController("));
        assertTrue(nowPlayingStateController.contains("fun renderNowBar()"));
        assertTrue(nowPlayingStateController.contains("fun publish(snapshot: PlaybackStateSnapshot): NowPlayingUiState"));
        assertTrue(nowPlayingStateController.contains("viewModel.updateState("));
        assertTrue(nowPlayingStateController.contains("listener.publishFloatingLyrics(state)"));
        assertTrue(nowPlayingStateController.contains("listener.syncQueueInputs()"));
        assertFalse(mainActivity.contains("new NowPlayingStateController(nowPlayingViewModel, new NowPlayingStateController.Listener()"));
        assertFalse(mainActivity.contains("new NowPlayingStateController.Listener()"));
        assertFalse(mainActivity.contains("public boolean storesReady()"));
        assertFalse(mainActivity.contains("private static String activeLyricLine("));
        assertTrue(mainActivity.contains("@Inject MainNowPlayingStateListenerFactory nowPlayingStateListenerFactory;"));
        assertTrue(mainActivity.contains("new NowPlayingStateController(nowPlayingViewModel, nowPlayingStateListenerFactory.create("));
        assertTrue(mainActivity.contains("this::bindQueueViewModelInputs"));
        assertTrue(mainActivity.contains("FloatingLyricsPublisher.update("));
        assertTrue(mainNowPlayingStateListener.contains("internal class MainNowPlayingStateListener("));
        assertTrue(mainNowPlayingStateListener.contains(": NowPlayingStateController.Listener"));
        assertTrue(mainNowPlayingStateListener.contains("private fun activeLyricLine(state: NowPlayingUiState?): String"));
        assertTrue(mainNowPlayingStateListener.contains("lines.firstOrNull { it.active }?.text"));
        assertTrue(mainNowPlayingStateListener.contains("queueInputsSyncer.syncQueueInputs()"));
        assertTrue(mainActivity.contains("nowPlayingStateController.renderNowBar()"));
        assertTrue(mainActivity.contains("nowPlayingStateController.publish(playbackStore.snapshot())"));
        assertFalse(mainActivity.contains("private void renderNowBar()"));
        assertFalse(mainActivity.contains("nowPlayingViewModel.updateState("));
        assertTrue(mainActivity.contains("private NowPlayingViewModel nowPlayingViewModel;"));
        assertFalse(mainActivity.contains("new NowPlayingGatewayBindings("));
        assertFalse(mainActivity.contains("nowPlayingViewModel.bindGateway(new NowPlayingGateway()"));
        assertTrue(mainActivity.contains("@Inject MainNowPlayingGatewayFactory nowPlayingGatewayFactory;"));
        assertTrue(mainActivity.contains("@Inject MainNowPlayingPlaybackGatewayFactory nowPlayingPlaybackGatewayFactory;"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.bindGateway(nowPlayingGatewayFactory.create("));
        assertTrue(mainActivity.contains("() -> playbackActionController"));
        assertTrue(mainActivity.contains("() -> playbackStore"));
        assertTrue(mainActivity.contains("track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track))"));
        assertTrue(mainActivity.contains("positionMs -> nowPlayingViewModel.seekTo(positionMs)"));
        assertTrue(mainNowPlayingGateway.contains("internal class MainNowPlayingGateway("));
        assertTrue(mainNowPlayingGateway.contains(": NowPlayingGateway"));
        assertTrue(mainNowPlayingGateway.contains("playbackActionControllerProvider.controller()?.togglePlayback()"));
        assertTrue(mainNowPlayingGateway.contains("playbackActionControllerProvider.controller()?.skipToNext()"));
        assertTrue(mainNowPlayingGateway.contains("playbackActionControllerProvider.controller()?.skipToPrevious()"));
        assertTrue(mainNowPlayingGateway.contains("seekHandler.seekTo(positionMs)"));
        assertTrue(mainNowPlayingGateway.contains("favoriteToggler.toggleFavorite(track)"));
        assertTrue(mainNowPlayingGateway.contains("playbackActionControllerProvider.controller()?.toggleShuffle()"));
        assertTrue(mainNowPlayingGateway.contains("playbackActionControllerProvider.controller()?.cycleRepeat()"));
        assertTrue(mainNowPlayingGateway.contains("statusTextProvider.text(key)"));
        assertFalse(mainActivity.contains("playbackActionController.toggleShuffle();"));
        assertFalse(mainActivity.contains("private void togglePlayback()"));
        assertFalse(mainActivity.contains("private void skipToNext()"));
        assertFalse(mainActivity.contains("private void skipToPrevious()"));
        assertFalse(mainActivity.contains("private void toggleShuffle()"));
        assertFalse(mainActivity.contains("private void cycleRepeat()"));
        assertTrue(playbackActionController.contains("internal class PlaybackActionController("));
        assertTrue(playbackActionController.contains("fun togglePlayback()"));
        assertTrue(playbackActionController.contains("listener.resolveCurrentStreamingQueueTrackIfNeeded()"));
        assertTrue(playbackActionController.contains("viewModel.togglePlayback(listener.playbackSnapshot(), listener.fallbackTracks())"));
        assertTrue(playbackActionController.contains("viewModel.toggleShuffle(listener.playbackSnapshot())"));
        assertTrue(playbackActionController.contains("viewModel.cycleRepeat()"));
        assertFalse(mainActivity.contains("new PlaybackActionController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainPlaybackActionListenerFactory playbackActionListenerFactory;"));
        assertTrue(mainActivity.contains("playbackActionListenerFactory.create("));
        assertTrue(mainActivity.contains("this::resolveCurrentStreamingQueueTrackIfNeeded"));
        assertTrue(mainActivity.contains("this::applyPlaybackActionResult"));
        assertTrue(mainActivity.contains("() -> playbackService == null ? playbackStore.snapshot() : playbackService.snapshot()"));
        assertTrue(mainActivity.contains("() -> libraryStore == null ? Collections.emptyList() : libraryStore.visibleTracks()"));
        assertTrue(mainPlaybackActionListener.contains("internal class MainPlaybackActionListener("));
        assertTrue(mainPlaybackActionListener.contains(": PlaybackActionController.Listener"));
        assertTrue(mainPlaybackActionListener.contains("streamingResolver.resolveCurrent()"));
        assertTrue(mainPlaybackActionListener.contains("snapshotSource.snapshot()"));
        assertTrue(mainPlaybackActionListener.contains("fallbackTracksSource.tracks()"));
        assertTrue(mainPlaybackActionListener.contains("resultSink.apply(result)"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackActionListenerFactory(): MainPlaybackActionListenerFactory"));
        assertTrue(mainActivity.contains("playbackStartController = new PlaybackStartController("));
        assertFalse(mainActivity.contains("new PlaybackStartBindings("));
        assertFalse(mainActivity.contains("new PlaybackStartControllerAdapter("));
        assertTrue(mainActivity.contains("streamingPlaybackController::resolveAndPlayStreamingTrack"));
        assertTrue(mainActivity.contains("nowPlayingViewModel::playTrackList"));
        assertTrue(mainActivity.contains("this::applyPlaybackActionResult"));
        assertFalse(mainActivity.contains("streamingViewModel::stopHeartbeatRecommendationMode"));
        assertTrue(mainActivity.contains("() -> streamingRecommendationViewModel.stopHeartbeatRecommendationMode()"));
        assertFalse(mainActivity.contains("viewModel::stopHeartbeatRecommendationMode"));
        assertFalse(mainActivity.contains("PlaybackController playbackStartPlaybackController"));
        assertFalse(mainActivity.contains("return playbackStartPlaybackController;"));
        assertTrue(mainActivity.contains("private void playTrackListFromHost(List<Track> tracks, int index)"));
        assertFalse(mainActivity.contains("private List<Track> pendingPlaybackTracks"));
        assertFalse(mainActivity.contains("private int pendingPlaybackIndex"));
        assertFalse(mainActivity.contains("pendingPlaybackTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);"));
        assertFalse(mainActivity.contains("pendingPlaybackIndex = index;"));
        assertTrue(mainActivity.contains("@Inject MainPlaybackStartListenerFactory playbackStartListenerFactory;"));
        assertTrue(mainActivity.contains("private MainPlaybackStartListener playbackStartListener;"));
        assertTrue(mainActivity.contains("playbackStartListener = playbackStartListenerFactory.create("));
        assertFalse(mainActivity.contains("() -> startService(new Intent(MainActivityBase.this, EchoPlaybackService.class))"));
        assertTrue(mainActivity.contains("@Inject NowPlayingPlaybackServiceStarter nowPlayingPlaybackServiceStarter;"));
        assertTrue(mainActivity.contains("() -> nowPlayingPlaybackServiceStarter.startPlaybackService(null)"));
        assertTrue(mainActivity.contains("() -> playbackService != null"));
        assertTrue(mainActivity.contains("() -> streamingViewModel.prepareStreamingPlaybackStatusText("));
        assertTrue(mainActivity.contains("status -> statusMessageController.setStatus(status)"));
        assertTrue(mainActivity.contains("() -> navigateToTab(TAB_QUEUE, true, true)"));
        assertTrue(mainActivity.contains("playbackStartListener.savePendingPlayback("));
        assertTrue(mainActivity.contains("playbackStartListener.setStatus(playbackStartListener.resolvingStatus())"));
        assertTrue(mainActivity.contains("playbackStartController.playTrackList(tracks, index)"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackCoordinator.kt"));
        assertFalse(mainActivity.contains("PlaybackCoordinator playbackCoordinator"));
        assertFalse(mainActivity.contains("new PlaybackCoordinator("));
        assertFalse(mainActivity.contains("playbackCoordinator."));
        assertTrue(mainActivity.contains("playbackServiceConnectionController.bind();"));
        assertTrue(mainActivity.contains("playbackServiceConnectionController.release();"));
        assertTrue(mainActivity.contains("playbackService.setAppVisible(true);"));
        assertTrue(mainActivity.contains("playbackService.setAppVisible(false);"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingQueueCoordinator.kt"));
        assertFalse(mainActivity.contains("NowPlayingQueueCoordinator nowPlayingQueueCoordinator"));
        assertFalse(mainActivity.contains("new NowPlayingQueueCoordinator("));
        assertFalse(mainActivity.contains("nowPlayingQueueCoordinator."));
        assertFalse(mainActivity.contains("private void installCoordinators()"));
        assertTrue(mainActivity.contains("queueViewModel.bindIntentListener(intent -> {"));
        assertTrue(mainActivity.contains("libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(playAt.getTracks(), playAt.getIndex()))"));
        assertTrue(mainActivity.contains("queueActionController.moveQueueTrack(move.getFromIndex(), move.getToIndex())"));
        assertTrue(mainActivity.contains("playbackStartController.playPendingTracksIfNeeded()"));
        assertTrue(mainActivity.contains("presentation -> playbackStartController.playHeartbeatRecommendation(presentation)"));
        assertFalse(mainActivity.contains("playHeartbeatRecommendationTracks("));
        assertFalse(mainActivity.contains("playHeartbeatRecommendationTrackList("));
        assertFalse(mainActivity.contains("private void playTrackListInternal("));
        assertFalse(mainActivity.contains("private void playPendingTracksIfNeeded()"));
        assertFalse(mainActivity.contains("private void ensurePlaybackServiceStarted()"));
        assertTrue(playbackStartController.contains("internal class PlaybackStartController("));
        assertTrue(playbackStartController.contains("fun playTrackList(tracks: List<Track>?, index: Int)"));
        assertTrue(playbackStartController.contains("fun playRecommendation(presentation: StreamingRecommendationPresentation)"));
        assertTrue(playbackStartController.contains("fun playHeartbeatRecommendation(presentation: StreamingRecommendationPresentation)"));
        assertFalse(playbackStartController.contains("fun playHeartbeatRecommendationTrackList("));
        assertTrue(playbackStartController.contains("listener.stopHeartbeatRecommendationMode()"));
        assertTrue(playbackStartController.contains("listener.openQueue()"));
        assertTrue(playbackStartController.contains("fun playPendingTracksIfNeeded()"));
        assertTrue(playbackStartController.contains("listener.savePendingPlayback(tracks ?: emptyList(), index)"));
        assertTrue(playbackStartController.contains("private val streamingTrackListResolver: StreamingTrackListResolver"));
        assertTrue(playbackStartController.contains("private val playbackTrackListPlayer: PlaybackTrackListPlayer"));
        assertTrue(playbackStartController.contains("private val playbackActionResultApplier: QueuePlaybackActionResultApplier"));
        assertFalse(playbackStartController.contains("fun playbackController(): PlaybackController"));
        assertFalse(playbackStartController.contains("playbackController.playTrackList(tracks, index)"));
        assertTrue(playbackStartController.contains("streamingTrackListResolver.resolve(tracks, index)"));
        assertTrue(playbackStartController.contains("val result = playbackTrackListPlayer.play(tracks, index)"));
        assertTrue(playbackStartController.contains("playbackActionResultApplier.apply(result)"));
        assertFalse(playbackStartController.contains("listener.playbackController().playTrackList(tracks, index)"));
        assertFalse(playbackStartController.contains("listener.resolveAndPlayStreamingTrack(tracks, index)"));
        assertFalse(playbackStartController.contains("listener.applyPlaybackActionResult(listener.playTrackList(tracks, index))"));
        assertTrue(mainPlaybackStartListener.contains("internal class MainPlaybackStartListener("));
        assertTrue(mainPlaybackStartListener.contains(": PlaybackStartController.Listener"));
        assertTrue(mainPlaybackStartListener.contains("private var pendingTracks: List<Track> = emptyList()"));
        assertTrue(mainPlaybackStartListener.contains("private var pendingIndex: Int = -1"));
        assertTrue(mainPlaybackStartListener.contains("pendingTracks = tracks.toList()"));
        assertTrue(mainPlaybackStartListener.contains("pendingIndex = index"));
        assertTrue(mainPlaybackStartListener.contains("heartbeatStopper.stopHeartbeatRecommendationMode()"));
        assertTrue(mainPlaybackStartListener.contains("serviceStarter.startPlaybackService()"));
        assertTrue(mainPlaybackStartListener.contains("serviceAvailability.hasPlaybackService()"));
        assertTrue(mainPlaybackStartListener.contains("resolvingStatusProvider.resolvingStatus()"));
        assertTrue(mainPlaybackStartListener.contains("statusSink.setStatus(status)"));
        assertTrue(mainPlaybackStartListener.contains("queueOpener.openQueue()"));
        assertTrue(playbackUiModule.contains("fun provideMainPlaybackStartListenerFactory(): MainPlaybackStartListenerFactory"));
        assertTrue(mainActivity.contains("private StreamingRecommendationViewModel streamingRecommendationViewModel;"));
        assertTrue(mainActivity.contains("private RecommendationActionCallbacks recommendationActionCallbacks;"));
        assertTrue(mainActivity.contains("streamingRecommendationViewModel = viewModels.getStreamingRecommendationViewModel();"));
        assertTrue(mainActivity.contains("streamingRecommendationViewModel.updateProviders(streamingViewModel.getState().getProviders())"));
        assertFalse(mainActivity.contains("private RecommendationActionController recommendationActionController;"));
        assertFalse(mainActivity.contains("recommendationActionController = new RecommendationActionController("));
        assertFalse(mainActivity.contains("recommendationActionCallbacks = new RecommendationActionCallbacks() {"));
        assertFalse(mainActivity.contains("new RecommendationActionCallbacks() {"));
        assertTrue(mainActivity.contains("@Inject MainRecommendationActionCallbacksFactory recommendationActionCallbacksFactory;"));
        assertTrue(mainActivity.contains("recommendationActionCallbacks = recommendationActionCallbacksFactory.create("));
        assertTrue(mainActivity.contains("presentation -> playbackStartController.playRecommendation(presentation)"));
        assertTrue(mainActivity.contains("presentation -> playbackStartController.playHeartbeatRecommendation(presentation)"));
        assertTrue(mainActivity.contains("this::logHeartbeatSeedMiss"));
        assertTrue(mainRecommendationActionCallbacks.contains("internal class MainRecommendationActionCallbacks("));
        assertTrue(mainRecommendationActionCallbacks.contains(": RecommendationActionCallbacks"));
        assertTrue(mainRecommendationActionCallbacks.contains("override fun playDailyRecommendation("));
        assertTrue(mainRecommendationActionCallbacks.contains("override fun seedRequest("));
        assertTrue(mainRecommendationActionCallbacks.contains("override fun playHeartbeatRecommendation("));
        assertTrue(mainRecommendationActionCallbacks.contains("override fun logSeedMiss("));
        assertFalse(mainActivity.contains("action -> recommendationActionController.run(action)"));
        assertTrue(mainActivity.contains("private void runRecommendationAction(RecommendationAction action)"));
        assertTrue(mainActivity.contains("streamingRecommendationViewModel.onAction("));
        assertFalse(mainActivity.contains("new HomeDashboardRenderController(homeDashboardViewModel, new HomeDashboardRenderController.Listener()"));
        assertFalse(mainActivity.contains("new HomeDashboardRenderController.Listener()"));
        assertTrue(mainActivity.contains("new HomeDashboardRenderController(homeDashboardViewModel, homeDashboardRenderListenerFactory.create("));
        assertTrue(mainHomeDashboardRenderListener.contains("internal class MainHomeDashboardRenderListener("));
        assertTrue(mainHomeDashboardRenderListener.contains(": HomeDashboardRenderController.Listener"));
        assertFalse(mainActivity.contains("private void playStreamingDailyRecommendations("));
        assertFalse(mainActivity.contains("private void playStreamingHeartbeatRecommendations("));
        assertFalse(mainActivity.contains("this::playStreamingDailyRecommendations"));
        assertFalse(mainActivity.contains("this::playStreamingHeartbeatRecommendations"));
        assertFalse(exists("app/src/main/java/app/yukine/RecommendationActionController.kt"));
        assertTrue(recommendationActionContracts.contains("sealed interface RecommendationAction"));
        assertTrue(recommendationActionContracts.contains("data class PlayDaily("));
        assertTrue(recommendationActionContracts.contains("data class PlayHeartbeat("));
        assertTrue(recommendationActionContracts.contains("interface RecommendationActionCallbacks"));
        assertFalse(recommendationActionContracts.contains("RecommendationActionHandler"));
        assertFalse(recommendationActionContracts.contains("internal class RecommendationActionController("));
        assertFalse(recommendationActionContracts.contains("fun interface RecommendationActionRunner"));
        assertFalse(recommendationActionContracts.contains("fun interface RecommendationLanguageProvider"));
        assertFalse(recommendationActionContracts.contains("recommendationActionHandler.onAction(action, languageProvider.languageMode(), callbacks)"));
        assertFalse(exists("app/src/main/java/app/yukine/RecommendationActionBindings.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/RecommendationActionControllerTest.kt"));
        assertFalse(recommendationActionContracts.contains("dailyController()?.playStreamingDailyRecommendations(action.provider)"));
        assertFalse(recommendationActionContracts.contains("heartbeatController()?.playStreamingHeartbeatRecommendations(action.provider)"));
        assertFalse(mainActivity.contains("private DailyRecommendationController dailyRecommendationController;"));
        assertFalse(mainActivity.contains("new DailyRecommendationController(streamingRecommendationViewModel"));
        assertFalse(mainActivity.contains("dailyRecommendationController.playStreamingDailyRecommendations(provider)"));
        assertFalse(streamingRecommendationViewModel.contains("DailyRecommendationPlayer"));
        assertFalse(recommendationActionContracts.contains("internal fun interface DailyRecommendationTrackListPlayer"));
        assertTrue(streamingRecommendationViewModel.contains("class StreamingRecommendationViewModel @Inject constructor("));
        assertTrue(streamingRecommendationViewModel.contains(": ViewModel(), HeartbeatRecommendationPlayer"));
        assertTrue(streamingRecommendationViewModel.contains("fun onAction("));
        assertTrue(streamingRecommendationViewModel.contains("RecommendationAction.PlayDaily"));
        assertTrue(streamingRecommendationViewModel.contains("RecommendationAction.PlayHeartbeat"));
        assertTrue(streamingRecommendationViewModel.contains("private val dailyRecommendationUseCase = StreamingDailyRecommendationUseCase(streamingRepositorySource)"));
        assertTrue(streamingRecommendationViewModel.contains("private val heartbeatRecommendationUseCase = StreamingHeartbeatRecommendationUseCase()"));
        assertTrue(streamingRecommendationViewModel.contains("internal interface HeartbeatRecommendationPlayer"));
        assertTrue(streamingRecommendationViewModel.contains("fun playDailyRecommendations("));
        assertTrue(streamingRecommendationViewModel.contains("override fun prepareStreamingHeartbeatRecommendationRequest("));
        assertTrue(streamingRecommendationViewModel.contains("override fun fetchHeartbeatRecommendations("));
        assertTrue(streamingRecommendationViewModel.contains("override fun resolveHeartbeatRecommendationSeed("));
        assertTrue(streamingRecommendationViewModel.contains("fun updateProviders(nextProviders: List<StreamingProviderDescriptor>)"));
        assertTrue(streamingRecommendationViewModel.contains("fun bindStreamingTrackMatchStore(store: StreamingTrackMatchStore?)"));
        assertTrue(streamingRecommendationViewModel.contains("fun prepareDailyRecommendationPresentation("));
        assertTrue(streamingRecommendationViewModel.contains("override fun prepareHeartbeatRecommendationPresentation("));
        assertTrue(streamingRecommendationViewModel.contains("override fun prepareHeartbeatRecommendationAppendPresentation("));
        assertTrue(streamingRecommendationViewModel.contains("StreamingPlaybackAdapter.placeholderTrack(it)"));
        assertTrue(streamingDailyRecommendationUseCase.contains("internal class StreamingDailyRecommendationUseCase("));
        assertTrue(streamingDailyRecommendationUseCase.contains("val repository = repositorySource.current()"));
        assertTrue(streamingDailyRecommendationUseCase.contains("tracks = repository.dailyRecommendations(provider)"));
        assertTrue(streamingDailyRecommendationUseCase.contains("diagnostics = repository.diagnostics()"));
        assertTrue(mainActivity.contains("heartbeatRecommendationController = new HeartbeatRecommendationController("));
        assertTrue(mainActivity.contains("streamingRecommendationViewModel,\n                () -> settingsStore.languageMode(),")
                || mainActivity.contains("streamingRecommendationViewModel,\r\n                () -> settingsStore.languageMode(),"));
        assertTrue(mainActivity.contains("private HeartbeatRecommendationSeedBinder heartbeatSeedBinder"));
        assertTrue(mainActivity.contains("heartbeatSeedBinder = new HeartbeatRecommendationSeedBinder("));
        assertTrue(mainActivity.contains("provider -> heartbeatSeedBinder == null"));
        assertTrue(mainActivity.contains(": heartbeatSeedBinder.request(provider)"));
        assertTrue(mainActivity.contains("heartbeatSeedBinder.bind(streamingTrackMatchUseCase);"));
        assertFalse(mainActivity.contains("private final LateBoundHeartbeatSeedRequestProvider heartbeatSeedRequestProvider"));
        assertFalse(mainActivity.contains("heartbeatSeedRequestProvider.bind(new HeartbeatRecommendationSeedResolver("));
        assertFalse(mainActivity.contains("new HeartbeatRecommendationSeedResolver(\n                streamingTrackMatchUseCase,")
                || mainActivity.contains("new HeartbeatRecommendationSeedResolver(\r\n                streamingTrackMatchUseCase,"));
        assertTrue(mainActivity.contains("heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot)"));
        assertFalse(mainActivity.contains("heartbeatRecommendationController.playStreamingHeartbeatRecommendations(provider)"));
        assertFalse(mainActivity.contains("private void stopHeartbeatRecommendationMode()"));
        assertFalse(mainActivity.contains("private HeartbeatRecommendationSeedRequest heartbeatRecommendationSeedRequest("));
        assertFalse(mainActivity.contains("streamingViewModel.prepareHeartbeatRecommendationSeedRequest("));
        assertTrue(heartbeatRecommendationController.contains("internal class HeartbeatRecommendationController("));
        assertTrue(heartbeatRecommendationController.contains("private val recommendationPlayer: HeartbeatRecommendationPlayer"));
        assertFalse(heartbeatRecommendationController.contains("private val streamingViewModel: StreamingViewModel"));
        assertTrue(heartbeatRecommendationController.contains("fun interface LanguageProvider"));
        assertFalse(heartbeatRecommendationController.contains("MainActivityViewModel"));
        assertTrue(heartbeatRecommendationController.contains("fun playStreamingHeartbeatRecommendations(provider: StreamingProviderName?)"));
        assertTrue(heartbeatRecommendationController.contains("fun maybeAppendHeartbeatRecommendations(snapshot: PlaybackStateSnapshot?)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.prepareStreamingHeartbeatRecommendationRequest(provider, languageMode)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.prepareHeartbeatRecommendationRefill(snapshot)"));
        assertTrue(heartbeatRecommendationController.contains("listener.seedRequest(provider)"));
        assertTrue(heartbeatRecommendationController.contains("listener.stopHeartbeatRecommendationMode()"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.fetchHeartbeatRecommendations(provider, request.seedTrackId, request.playlistId"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.prepareHeartbeatRecommendationAppendPresentation("));
        assertTrue(heartbeatRecommendationController.contains("listener.appendToQueue(presentation)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.prepareHeartbeatRecommendationPresentation("));
        assertTrue(heartbeatRecommendationController.contains("listener.playHeartbeatRecommendation(presentation)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.resolveHeartbeatRecommendationSeed("));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.markHeartbeatRecommendationLoadingFinished()"));
        assertFalse(exists("app/src/main/java/app/yukine/HeartbeatRecommendationBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/HeartbeatRecommendationBindings.java"));
        assertFalse(mainActivity.contains("new HeartbeatRecommendationBindings("));
        assertFalse(mainActivity.contains("new HeartbeatRecommendationController(streamingRecommendationViewModel, () -> settingsStore.languageMode(), new HeartbeatRecommendationController.Listener()"));
        assertFalse(mainActivity.contains("new HeartbeatRecommendationController(streamingRecommendationViewModel, () -> settingsStore.languageMode(), new HeartbeatRecommendationController.Listener"));
        assertTrue(mainActivity.contains("@Inject MainHeartbeatRecommendationListenerFactory heartbeatRecommendationListenerFactory;"));
        assertTrue(mainActivity.contains("heartbeatRecommendationListenerFactory.create("));
        assertTrue(mainActivity.contains("() -> playbackService != null"));
        assertTrue(mainActivity.contains("provider -> heartbeatSeedBinder == null"));
        assertTrue(mainActivity.contains(": heartbeatSeedBinder.request(provider)"));
        assertTrue(mainActivity.contains("() -> streamingRecommendationViewModel.stopHeartbeatRecommendationMode()"));
        assertTrue(mainActivity.contains("presentation -> nowPlayingViewModel.appendToQueue(presentation.getTracks())"));
        assertTrue(mainActivity.contains("presentation -> playbackStartController.playHeartbeatRecommendation(presentation)"));
        assertTrue(mainActivity.contains("this::logHeartbeatSeedMiss"));
        assertTrue(mainActivity.contains("status -> statusMessageController.setStatus(status)"));
        assertTrue(mainHeartbeatRecommendationListener.contains("internal class MainHeartbeatRecommendationListener("));
        assertTrue(mainHeartbeatRecommendationListener.contains(": HeartbeatRecommendationController.Listener"));
        assertTrue(mainHeartbeatRecommendationListener.contains("serviceAvailability.hasPlaybackService()"));
        assertTrue(mainHeartbeatRecommendationListener.contains("seedRequestProvider.request(provider)"));
        assertTrue(mainHeartbeatRecommendationListener.contains("modeStopper.stopHeartbeatRecommendationMode()"));
        assertTrue(mainHeartbeatRecommendationListener.contains("queueAppender.appendToQueue(presentation)"));
        assertTrue(mainHeartbeatRecommendationListener.contains("playerSink.playHeartbeatRecommendation(presentation)"));
        assertTrue(mainHeartbeatRecommendationListener.contains("seedMissLogger.logSeedMiss(request)"));
        assertTrue(mainHeartbeatRecommendationListener.contains("statusSink.setStatus(status)"));
        assertTrue(streamingModule.contains("fun provideMainHeartbeatRecommendationListenerFactory(): MainHeartbeatRecommendationListenerFactory"));
        assertTrue(heartbeatSeedResolver.contains("internal class HeartbeatRecommendationSeedResolver("));
        assertTrue(heartbeatSeedResolver.contains("internal fun interface HeartbeatSeedRequestProvider"));
        assertTrue(heartbeatSeedResolver.contains(") : HeartbeatSeedRequestProvider"));
        assertTrue(heartbeatSeedResolver.contains("trackMatchStore.heartbeatSeedCandidates("));
        assertTrue(heartbeatSeedResolver.contains("trackMatchStore.providerTrackIdFromCandidates(candidates, provider).trim()"));
        assertTrue(heartbeatSeedResolver.contains("trackMatchStore.snapshotQueueForHeartbeat("));
        assertTrue(heartbeatSeedResolver.contains("trackMatchStore.heartbeatSeedMissMessage("));
        assertFalse(heartbeatSeedResolver.contains("internal class HeartbeatRecommendationSeedResolverBindings("));
        assertFalse(heartbeatSeedResolver.contains("HeartbeatPlaybackSnapshotProvider"));
        assertFalse(heartbeatSeedResolver.contains("HeartbeatQueueSnapshotProvider"));
        assertTrue(heartbeatSeedResolver.contains("internal class LateBoundHeartbeatSeedRequestProvider : HeartbeatSeedRequestProvider"));
        assertTrue(heartbeatSeedBinder.contains("internal class HeartbeatRecommendationSeedBinder("));
        assertTrue(heartbeatSeedBinder.contains(") : HeartbeatSeedRequestProvider"));
        assertTrue(heartbeatSeedBinder.contains("private val lateBoundProvider = LateBoundHeartbeatSeedRequestProvider()"));
        assertTrue(heartbeatSeedBinder.contains("fun bind(trackMatchStore: StreamingTrackMatchStore?)"));
        assertTrue(heartbeatSeedBinder.contains("HeartbeatRecommendationSeedResolver("));
        assertTrue(heartbeatSeedBinder.contains("override fun request(provider: StreamingProviderName): HeartbeatRecommendationSeedRequest"));
        assertFalse(mainActivity.contains("private void maybeAppendHeartbeatRecommendations("));
        assertFalse(mainActivity.contains("private void fetchHeartbeatRecommendations("));
        assertFalse(mainActivity.contains("private void resolveHeartbeatSeedFromQueue("));
        assertFalse(mainActivity.contains("nowPlayingViewModel.bindGateway(new ActivityNowPlayingGateway())"));
        assertFalse(mainActivity.contains("private final class ActivityNowPlayingGateway implements NowPlayingGateway"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("internal class MainNowPlayingPlaybackGatewayFactory("));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("fun create(serviceProvider: () -> NowPlayingPlaybackServicePort?): NowPlayingPlaybackGateway"));
        assertTrue(nowPlayingPlaybackServiceStarter.contains("internal class NowPlayingPlaybackServiceStarter("));
        assertTrue(nowPlayingPlaybackServiceStarter.contains("Intent(context, EchoPlaybackService::class.java)"));
        assertTrue(nowPlayingPlaybackServiceStarter.contains("context.startService(intent)"));
        assertTrue(playbackUiModule.contains("fun provideNowPlayingPlaybackServiceStarter("));
        assertTrue(playbackUiModule.contains("): NowPlayingPlaybackServiceStarter ="));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("internal class NowPlayingPlaybackGatewayAdapter("));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains(") : NowPlayingPlaybackGateway"));
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("fun interface ServiceProvider"));
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("fun interface ServiceStarter"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("private val serviceProvider: () -> NowPlayingPlaybackServicePort?"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("private val serviceStarter: (String?) -> Unit"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("private fun service(): NowPlayingPlaybackServicePort? = serviceProvider()"));
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("override fun startPlaybackService(action: String?)"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("serviceStarter(PlaybackServiceActions.PREVIOUS)"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("serviceStarter(PlaybackServiceActions.NEXT)"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("serviceStarter(null)"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("service()?.playQueue(tracks, index)"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("fun warmPlaybackTrack(track: Track)"));
        assertTrue(nowPlayingPlaybackGatewayAdapter.contains("service()?.warmPlaybackTrack(track)"));
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("fun precacheTrack(track: Track)"));
        assertFalse(nowPlayingPlaybackGatewayAdapter.contains("service()?.precacheTrack(track)"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.bindPlaybackGateway(nowPlayingPlaybackGatewayFactory.create("));
        assertFalse(mainActivity.contains("nowPlayingViewModel.bindPlaybackGateway(new NowPlayingPlaybackGatewayAdapter("));
        assertFalse(mainActivity.contains("Intent intent = new Intent(MainActivityBase.this, EchoPlaybackService.class);"));
        assertFalse(mainActivity.contains("new Intent(MainActivityBase.this, EchoPlaybackService.class)"));
        assertFalse(mainActivity.contains("nowPlayingViewModel.bindPlaybackGateway(new ActivityNowPlayingPlaybackGateway())"));
        assertFalse(mainActivity.contains("private final class ActivityNowPlayingPlaybackGateway implements NowPlayingPlaybackGateway"));
        assertFalse(mainActivity.contains("nowPlayingViewModel.precacheTrack(resolved)"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.appendToQueue(presentation.getTracks())"));
        assertFalse(mainActivity.contains("nowPlayingViewModel.replaceCurrentTrackAndResume(resolved.getTrack(), resolved.getPositionMs())"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionResultController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionResultController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionResultBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackActionResultBindings.java"));
        assertFalse(mainActivity.contains("new PlaybackActionResultBindings("));
        assertFalse(mainActivity.contains("new PlaybackActionResultController(new PlaybackActionResultController.Listener()"));
        assertTrue(mainActivity.contains("private void applyPlaybackActionResult(PlaybackActionResultUi result)"));
        assertTrue(mainActivity.contains("PlaybackStateSnapshot snapshot = result.snapshot;"));
        assertTrue(mainActivity.contains("playbackStore.replaceSnapshot(snapshot);"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(status);"));
        assertTrue(mainActivity.contains("publishPlaybackStore();"));
        assertTrue(mainActivity.contains("nowPlayingStateController.renderNowBar();"));
        assertTrue(mainActivity.contains("routeController.setSelectedTab(TAB_NOW);"));
        assertFalse(mainActivity.contains("playbackActionResultController.apply(result)"));
        assertFalse(mainActivity.contains("private void publishPlaybackState()"));
        assertFalse(mainActivity.contains("if (result.snapshot != null)"));
        assertFalse(mainActivity.contains("playbackService.precacheTrack(resolved)"));
        assertFalse(mainActivity.contains("playbackService.appendToQueue(placeholders)"));
        assertFalse(mainActivity.contains("playbackService.replaceCurrentTrackAndResume(resolved, snapshot.positionMs)"));
        assertFalse(mainActivity.contains("private PlaybackActionsController playbackActionsController"));
        assertFalse(mainActivity.contains("playbackActionsController."));
        assertTrue(mainActivity.contains("private void handleNowPlayingEvent(NowPlayingEvent event)"));
        assertTrue(mainActivity.contains("effect == NowPlayingEffect.OpenQueue.INSTANCE"));
        assertTrue(mainActivity.contains("effect instanceof NowPlayingEffect.OpenAddToPlaylist openAddToPlaylist"));
        assertTrue(mainActivity.contains("effect instanceof NowPlayingEffect.ShowMessage showMessage"));
        assertFalse(mainActivity.contains("showAddToPlaylistDialog(((NowPlayingEffect.OpenAddToPlaylist) effect).getTrack());"));
        assertFalse(mainActivity.contains("private Track currentTrackForEffect("));
        assertFalse(exists("app/src/main/java/app/yukine/ui/NowPlayingOverlayController.kt"));
        assertFalse(nowPlayingScreen.contains("\"Close\""));
        assertFalse(nowPlayingScreen.contains("\"Show lyrics\""));
        assertFalse(nowPlayingScreen.contains("\"Show artwork\""));
        assertFalse(nowPlayingScreen.contains("\"No lyrics found\""));
        assertFalse(nowPlayingScreen.contains("\"Playback progress\""));
        assertFalse(echoNowBar.contains("\"Previous\""));
        assertFalse(echoNowBar.contains("\"Pause\""));
        assertFalse(echoNowBar.contains("\"Play\""));
        assertFalse(echoNowBar.contains("\"Next\""));
        assertFalse(echoNowBar.contains("\"Queue\""));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsController.java"));
        assertTrue(lyricsViewModel.contains("class LyricsViewModel @JvmOverloads constructor("));
        assertTrue(lyricsViewModel.contains("private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO"));
        assertTrue(lyricsViewModel.contains("data class LyricsState"));
        assertTrue(lyricsViewModel.contains("enum class LyricsStatusKind"));
        assertTrue(lyricsViewModel.contains("fun interface LyricsLoader"));
        assertTrue(lyricsViewModel.contains("internal class LoadTrackLyricsUseCaseLyricsLoader"));
        assertTrue(lyricsViewModel.contains("withContext(ioDispatcher)"));
        assertTrue(lyricsViewModel.contains("LyricsStatusText.status"));
        assertFalse(lyricsViewModel.contains("new LyricsRepository()"));
        assertFalse(lyricsViewModel.contains("\"Loading lyrics\""));
        assertFalse(lyricsViewModel.contains("\"No local lyrics found\""));
        assertTrue(nowBarStateFactory.contains("repeatMode = playbackState.repeatMode"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingStateFactory.java"));
        assertTrue(nowPlayingStateFactory.contains("internal object NowPlayingStateFactory"));
        assertTrue(nowPlayingStateFactory.contains("@JvmStatic"));
        assertTrue(nowPlayingStateFactory.contains("): NowPlayingUiState?"));
        assertTrue(nowPlayingStateFactory.contains("val track = playbackState.currentTrack ?: return null"));
        assertTrue(nowPlayingStateFactory.contains("LyricUiLine(line.text, index == activeIndex, line.timeMs)"));
        assertTrue(nowPlayingStateFactory.contains("StreamingDataPathMetadata.provider(track.dataPath)"));
        assertFalse(nowPlayingStateFactory.contains("StreamingPlaybackAdapter"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingRenderController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NowPlayingRenderController.java"));
        assertTrue(mainActivity.contains("private boolean updateNowPlayingContent()"));
        assertTrue(mainActivity.contains("return playbackStore.snapshot().currentTrack != null;"));
        assertFalse(mainActivity.contains("private void renderNowPlaying()"));
        assertFalse(mainActivity.contains("NowPlayingRenderController"));
        assertFalse(mainActivity.contains("nowPlayingRenderController"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueRenderBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueRenderBindings.kt"));
        assertTrue(queueRenderController.contains("internal class QueueRenderController"));
        assertFalse(queueRenderController.contains("MainActivityViewModel"));
        assertTrue(queueRenderController.contains("interface Listener"));
        assertTrue(queueRenderController.contains("internal fun interface TrackListPlaybackAction"));
        assertFalse(queueRenderController.contains("internal fun interface QueueTrackAction"));
        assertTrue(queueRenderController.contains("fun render(queue: List<Track>?"));
        assertFalse(queueRenderController.contains("TrackRowStateFactory.queueRow("));
        assertFalse(queueRenderController.contains("listener.publishQueue(rows)"));
        assertTrue(queueRenderController.contains("listener.publishQueueChrome("));
        assertFalse(queueRenderController.contains("internal data class QueueChromeState("));
        assertFalse(queueRenderController.contains("QueueUiStatePublisher"));
        assertFalse(mainActivity.contains("new QueueRenderController(new QueueRenderBindings("));
        assertFalse(mainActivity.contains("new QueueRenderController(viewModel,"));
        assertFalse(mainActivity.contains("new QueueRenderController(new QueueRenderController.Listener()"));
        assertFalse(mainActivity.contains("new QueueRenderController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainQueueRenderListenerFactory queueRenderListenerFactory;"));
        assertTrue(mainActivity.contains("new QueueRenderController(queueRenderListenerFactory.create("));
        assertTrue(mainActivity.contains("track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track))"));
        assertTrue(mainActivity.contains("track -> playlistDialogController.showAddToPlaylist(track)"));
        assertTrue(mainActivity.contains("() -> queueActionController.confirmClearQueue()"));
        assertTrue(mainQueueRenderListener.contains("internal class MainQueueRenderListener("));
        assertTrue(mainQueueRenderListener.contains(": QueueRenderController.Listener"));
        assertTrue(mainQueueRenderListener.contains("override fun publishQueueChrome("));
        assertFalse(mainActivity.contains("publishQueueUiState"));
        assertFalse(queueRenderController.contains("QueueScreenFactory.create("));
        assertFalse(exists("app/src/main/java/app/yukine/QueueIntentController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueIntentController.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/QueueIntentControllerTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueIntentBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueIntentBindings.java"));
        assertFalse(mainActivity.contains("private QueueIntentController queueIntentController;"));
        assertFalse(mainActivity.contains("new QueueIntentController(new QueueIntentController.Listener()"));
        assertFalse(mainActivity.contains("queueIntentController.handle(intent)"));
        assertTrue(mainActivity.contains("queueViewModel.bindIntentListener(intent -> {"));
        assertTrue(mainActivity.contains("intent instanceof QueueIntent.PlayAt playAt"));
        assertTrue(mainActivity.contains("libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(playAt.getTracks(), playAt.getIndex()))"));
        assertTrue(mainActivity.contains("intent instanceof QueueIntent.ToggleFavorite toggleFavorite"));
        assertTrue(mainActivity.contains("libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(toggleFavorite.getTrack()))"));
        assertTrue(mainActivity.contains("intent instanceof QueueIntent.ClearQueue"));
        assertFalse(mainActivity.contains("private void handleQueueIntent("));
        String queueActionController = read("app/src/main/java/app/yukine/QueueActionController.kt");
        String mainQueueActionListener = read("app/src/main/java/app/yukine/MainQueueActionListener.kt");
        assertFalse(exists("app/src/main/java/app/yukine/QueueActionController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueActionBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/QueueActionBindings.java"));
        assertTrue(queueActionController.contains("internal class QueueActionController("));
        assertTrue(queueActionController.contains("fun removeQueueTrack(track: Track?)"));
        assertTrue(queueActionController.contains("fun confirmClearQueue()"));
        assertTrue(queueActionController.contains("fun clearQueue()"));
        assertTrue(queueActionController.contains("fun moveQueueTrack(fromIndex: Int, toIndex: Int)"));
        assertTrue(queueActionController.contains("listener.applyPlaybackActionResult(viewModel.removeQueueTrack(track))"));
        assertTrue(queueActionController.contains("listener.applyPlaybackActionResult(viewModel.clearQueue())"));
        assertTrue(queueActionController.contains("listener.setStatus(listener.queueEmptyStatus())"));
        assertFalse(mainActivity.contains("new QueueActionBindings("));
        assertFalse(mainActivity.contains("new QueueActionController(nowPlayingViewModel, new QueueActionController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainQueueActionListenerFactory queueActionListenerFactory;"));
        assertTrue(mainActivity.contains("queueActionListenerFactory.create("));
        assertTrue(mainActivity.contains("this::applyPlaybackActionResult"));
        assertTrue(mainActivity.contains("() -> playbackService != null"));
        assertTrue(mainActivity.contains("(fromIndex, toIndex) -> playbackService.moveQueueTrack(fromIndex, toIndex)"));
        assertTrue(mainActivity.contains("() -> nowPlayingStateController.renderNowBar()"));
        assertTrue(mainActivity.contains("this::renderSelectedTab"));
        assertTrue(mainActivity.contains("() -> confirmationDialogController.confirmClearQueue()"));
        assertTrue(mainActivity.contains("() -> AppLanguage.text(settingsStore.languageMode(), \"queue.empty\")"));
        assertTrue(mainActivity.contains("status -> statusMessageController.setStatus(status)"));
        assertTrue(mainQueueActionListener.contains("internal class MainQueueActionListener("));
        assertTrue(mainQueueActionListener.contains(": QueueActionController.Listener"));
        assertTrue(mainQueueActionListener.contains("resultApplier.apply(result)"));
        assertTrue(mainQueueActionListener.contains("serviceAvailability.hasService()"));
        assertTrue(mainQueueActionListener.contains("trackMoveSink.move(fromIndex, toIndex)"));
        assertTrue(mainQueueActionListener.contains("nowBarRenderer.renderNowBar()"));
        assertTrue(mainQueueActionListener.contains("selectedTabRenderer.renderSelectedTab()"));
        assertTrue(mainQueueActionListener.contains("clearQueueConfirmer.confirmClearQueue()"));
        assertTrue(mainQueueActionListener.contains("emptyStatusProvider.queueEmptyStatus()"));
        assertTrue(mainQueueActionListener.contains("statusSink.setStatus(status)"));
        assertTrue(playbackUiModule.contains("fun provideMainQueueActionListenerFactory(): MainQueueActionListenerFactory"));
        assertTrue(mainActivity.contains("queueActionController.removeQueueTrack(track)"));
        assertTrue(mainActivity.contains("queueActionController.confirmClearQueue()"));
        assertTrue(mainActivity.contains("queueActionController.clearQueue();"));
        assertTrue(mainActivity.contains("queueActionController.moveQueueTrack(move.getFromIndex(), move.getToIndex())"));
        assertFalse(mainActivity.contains("private void removeQueueTrack(Track track)"));
        assertFalse(mainActivity.contains("private void moveQueueTrack(int fromIndex, int toIndex)"));
        assertFalse(mainActivity.contains("private void confirmClearQueue()"));
        assertFalse(mainActivity.contains("private void clearQueue()"));
        assertFalse(mainActivity.contains("applyPlaybackActionResult(nowPlayingViewModel.removeQueueTrack(track))"));
        assertFalse(mainActivity.contains("applyPlaybackActionResult(nowPlayingViewModel.clearQueue())"));
        assertFalse(mainActivity.contains("if (!nowPlayingViewModel.hasQueue())"));
    }

    @Test
    public void mainPlaybackStoreIsKotlinStateHolder() throws Exception {
        String playbackStore = read("app/src/main/java/app/yukine/MainPlaybackStore.kt");
        String playbackViewModel = read("app/src/main/java/app/yukine/PlaybackViewModel.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String mainActivityViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");

        assertFalse(exists("app/src/main/java/app/yukine/MainPlaybackStore.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaybackViewModel.java"));
        assertTrue(playbackViewModel.contains("class PlaybackViewModel : ViewModel()"));
        assertTrue(playbackViewModel.contains("private val playbackState = MutableStateFlow(PlaybackViewState())"));
        assertTrue(playbackViewModel.contains("val playback: StateFlow<PlaybackViewState> = playbackState.asStateFlow()"));
        assertFalse(playbackViewModel.contains("MainActivityPlaybackState"));
        assertTrue(playbackViewModel.contains("fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot?)"));
        assertTrue(playbackViewModel.contains("fun updatePlayback(snapshot: PlaybackStateSnapshot?, queue: List<Track>)"));
        assertTrue(playbackViewModel.contains("fun lastHistoryRefreshTrackId(): Long"));
        assertTrue(playbackStore.contains("internal class MainPlaybackStore"));
        assertTrue(playbackStore.contains("internal fun interface MainPlaybackStoreFactory"));
        assertTrue(playbackStore.contains("fun create(viewModel: PlaybackViewModel): MainPlaybackStore"));
        assertFalse(playbackStore.contains("EchoPlaybackService"));
        assertTrue(playbackStore.contains("private val viewModel: PlaybackViewModel"));
        assertTrue(playbackStore.contains("fun snapshot(): PlaybackStateSnapshot"));
        assertTrue(playbackStore.contains("viewModel.playback.value.snapshot"));
        assertTrue(playbackStore.contains("viewModel.replacePlaybackSnapshot(snapshot)"));
        assertTrue(playbackStore.contains("fun publish(queue: List<Track>)"));
        assertTrue(playbackStore.contains("viewModel.updatePlayback(snapshot(), queue)"));
        assertTrue(mainActivity.contains("private PlaybackViewModel playbackViewModel;"));
        assertTrue(mainActivity.contains("playbackViewModel = viewModels.getPlaybackViewModel();"));
        assertTrue(mainActivity.contains("@Inject MainPlaybackStoreFactory playbackStoreFactory;"));
        assertTrue(mainActivity.contains("playbackStore = playbackStoreFactory.create(playbackViewModel);"));
        assertFalse(mainActivity.contains("playbackStore = new MainPlaybackStore(playbackViewModel);"));
        assertFalse(mainActivityViewModel.contains("private val playbackState = MutableStateFlow(PlaybackViewState())"));
        assertFalse(mainActivityViewModel.contains("val playback: StateFlow<PlaybackViewState>"));
        assertFalse(mainActivityViewModel.contains("MainActivityPlaybackState"));
        assertFalse(mainActivityViewModel.contains("fun replacePlaybackSnapshot(snapshot: PlaybackStateSnapshot?)"));
        assertFalse(mainActivityViewModel.contains("fun updatePlayback(snapshot: PlaybackStateSnapshot?, queue: List<Track>)"));
    }

    @Test
    public void realtimeVisualizerDoesNotChurnDuringStartup() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String hostState = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt");

        assertTrue(mainActivity.contains("private static final float[] EMPTY_REALTIME_BANDS = new float[0];"));
        assertTrue(mainActivity.contains("playbackService == null ? EMPTY_REALTIME_BANDS : playbackService.realtimeBands()"));
        assertTrue(hostState.contains("private val EmptyRealtimeBands = FloatArray(0)"));
        assertTrue(hostState.contains("val realtimeBandsProvider: () -> FloatArray = { EmptyRealtimeBands }"));
        assertFalse(hostState.contains("val realtimeBandsProvider: () -> FloatArray = { FloatArray(0) }"));
        assertTrue(navGraph.contains("val realtimeVisualsActive = hostState.visualMotionEnabled && playbackState.playing"));
        assertTrue(navGraph.contains("if (!realtimeVisualsActive)"));
        assertTrue(navGraph.contains("return@LaunchedEffect"));
        assertTrue(navGraph.contains("if (!realtimeBands.contentEquals(nextBands))"));
        assertTrue(navGraph.contains("realtimeBands = if (nextBands.isEmpty()) EmptyRealtimeBands else nextBands"));
        assertTrue(navGraph.contains("private const val RealtimeVisualPollMs = 33L"));
        assertTrue(navGraph.contains("delay(RealtimeVisualPollMs)"));
    }

    @Test
    public void playbackStartDefersHeavyVisualizationWork() throws Exception {
        String playbackService = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedPlaybackService = playbackService.replace("\r\n", "\n");
        String playerStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackPlayerStateOwner.java");
        String visualizationAnalyzer = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationAnalyzer.kt");
        String visualizationStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationStateOwner.java");
        String bufferedProgressOwner = read("app/src/main/java/app/yukine/playback/PlaybackBufferedProgressOwner.java");
        String visualizationCacheManager = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationCacheManager.java");
        String visualizationCacheStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationCacheStateOwner.java");
        String realtimeVisualizationOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackRealtimeVisualizationOwner.java"
        );
        String stateSnapshotOwner = read("app/src/main/java/app/yukine/playback/PlaybackStateSnapshotOwner.java");
        String notificationArtworkManager = read("app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkManager.java");
        String notificationArtworkBridgeOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkBridgeOwner.java"
        );
        String normalizedNotificationArtworkManager = notificationArtworkManager.replace("\r\n", "\n");

        assertFalse(playbackService.contains("PLAYBACK_VISUALIZATION_CACHE_DELAY_MS"));
        assertTrue(visualizationAnalyzer.contains("private const val PLAYBACK_VISUALIZATION_WARMUP_MS"));
        assertTrue(visualizationAnalyzer.contains("visualizationWarmupUntilMs = System.currentTimeMillis() + PLAYBACK_VISUALIZATION_WARMUP_MS"));
        assertTrue(playbackService.contains("private final PlaybackPlayerStateOwner playbackPlayerStateOwner"));
        assertTrue(playbackService.contains("new PlaybackPlayerStateOwner(() -> player)"));
        assertFalse(playerStateOwner.contains("fromPlayerProvider("));
        assertFalse(playbackService.contains("player.isPlaying()"));
        assertFalse(playbackService.contains("player.getCurrentPosition()"));
        assertFalse(playbackService.contains("player.getDuration()"));
        assertFalse(playbackService.contains("private boolean isPlaying()"));
        assertFalse(playbackService.contains("private long positionMs()"));
        assertFalse(playbackService.contains("private long durationMs()"));
        assertTrue(playbackService.contains("playbackPlayerStateOwner.isPlaying()"));
        assertTrue(playbackService.contains("playbackPlayerStateOwner.positionMs() > 3000L"));
        assertTrue(playerStateOwner.contains("final class PlaybackPlayerStateOwner implements"));
        assertTrue(playerStateOwner.contains("PlaybackStateSnapshotOwner.PlaybackPositionProvider"));
        assertFalse(playerStateOwner.contains("PlaybackBufferedProgressOwner.PlaybackPositionProvider"));
        assertFalse(playerStateOwner.contains("interface PlayerProvider"));
        assertTrue(playerStateOwner.contains("private final Supplier<Player> playerProvider;"));
        assertTrue(playerStateOwner.contains("long bufferedPositionMs()"));
        assertTrue(playerStateOwner.contains("player.getBufferedPosition()"));
        assertFalse(playerStateOwner.contains("PlaybackActiveStateOwner.PlayingStateProvider"));
        assertFalse(playerStateOwner.contains("PlaybackProgressUpdateStateOwner.PlaybackStateProvider"));
        assertFalse(playerStateOwner.contains("PlaybackCrossfadeStateOwner.PlaybackStateProvider"));
        assertFalse(playerStateOwner.contains("PlaybackRealtimeVisualizationOwner.PlaybackStateProvider"));
        assertFalse(playerStateOwner.contains("PlaybackPositionStateOwner.PlaybackPositionProvider"));
        assertFalse(playerStateOwner.contains("PlaybackNoisyReceiverActionsOwner.PlaybackStateProvider"));
        assertFalse(playerStateOwner.contains("PlaybackShutdownPlaybackStateOwner.PlaybackStateProvider"));
        assertFalse(playerStateOwner.contains("interface PlayerStateOperations"));
        assertFalse(playerStateOwner.contains("PlayerStateOperationsProvider"));
        assertFalse(playerStateOwner.contains("Media3PlayerStateOperations"));
        assertTrue(playerStateOwner.contains("return player.isPlaying();"));
        assertTrue(playerStateOwner.contains("return Math.max(0L, player.getCurrentPosition());"));
        assertTrue(playerStateOwner.contains("durationMs == C.TIME_UNSET ? 0L : Math.max(0L, durationMs)"));
        assertFalse(playbackService.contains("private static final float[] EMPTY_REALTIME_BANDS = new float[0];"));
        assertFalse(playbackService.contains("return isPlaying() ? realtimeBassDetector.bands() : EMPTY_REALTIME_BANDS;"));
        assertFalse(playbackService.contains("return isPlaying() ? realtimeBassDetector.beat() : 0f;"));
        assertFalse(playbackService.contains("() -> realtimeBassDetector.beat()"));
        assertTrue(playbackService.contains("private PlaybackRealtimeVisualizationOwner playbackRealtimeVisualizationOwner;"));
        assertTrue(playbackService.contains("PlaybackRealtimeVisualizationOwner.fromRealtimeBassDetector("));
        assertTrue(playbackService.contains("playbackPlayerStateOwner::isPlaying"));
        assertTrue(playbackService.contains("playbackRealtimeVisualizationOwner == null ? 0f : playbackRealtimeVisualizationOwner.beat()"));
        assertTrue(playbackService.contains("playbackRealtimeVisualizationOwner == null ? new float[0] : playbackRealtimeVisualizationOwner.bands()"));
        assertTrue(playbackService.contains("                playbackRealtimeVisualizationOwner::beat,"));
        assertTrue(realtimeVisualizationOwner.contains(
                "final class PlaybackRealtimeVisualizationOwner"));
        assertFalse(realtimeVisualizationOwner.contains(
                "implements PlaybackStateSnapshotOwner.RealtimeBeatProvider"));
        assertFalse(realtimeVisualizationOwner.contains("interface PlaybackStateProvider"));
        assertTrue(realtimeVisualizationOwner.contains("private final BooleanSupplier playbackStateProvider;"));
        assertTrue(realtimeVisualizationOwner.contains("interface RealtimeDataProvider"));
        assertTrue(realtimeVisualizationOwner.contains("private static final float[] EMPTY_BANDS = new float[0];"));
        assertTrue(realtimeVisualizationOwner.contains("realtimeBassDetector.beat();"));
        assertTrue(realtimeVisualizationOwner.contains("realtimeBassDetector.bands();"));
        assertTrue(realtimeVisualizationOwner.contains("playbackStateProvider.getAsBoolean()"));
        assertFalse(playbackService.contains("private PlaybackVisualizationStateOwner playbackVisualizationStateOwner;"));
        assertFalse(playbackService.contains("private PlaybackBufferedProgressOwner playbackBufferedProgressOwner;"));
        assertTrue(playbackService.contains("final PlaybackBufferedProgressOwner playbackBufferedProgressOwner ="));
        assertTrue(playbackService.contains("new PlaybackBufferedProgressOwner("));
        assertFalse(bufferedProgressOwner.contains("fromPlayerProvider("));
        assertTrue(playbackService.contains("                        playbackPlayerStateOwner::positionMs,"));
        assertTrue(playbackService.contains("                        playbackPlayerStateOwner::bufferedPositionMs"));
        assertTrue(playbackService.contains("                        playbackBufferedProgressOwner,"));
        assertFalse(playbackService.contains("private float bufferedProgress(long durationMs)"));
        assertFalse(playbackService.contains("player.getBufferedPosition()"));
        assertTrue(playbackService.contains("final PlaybackVisualizationStateOwner playbackVisualizationStateOwner ="));
        assertTrue(playbackService.contains("new PlaybackVisualizationStateOwner("));
        assertTrue(playbackService.contains("playbackVisualizationAnalyzer = new PlaybackVisualizationAnalyzer("));
        assertTrue(playbackService.contains("                playbackVisualizationStateOwner,"));
        assertFalse(playbackService.contains("new PlaybackVisualizationAnalyzer.StateProvider()"));
        assertFalse(playbackService.contains("private PlaybackVisualizationCacheStateOwner playbackVisualizationCacheStateOwner;"));
        assertTrue(playbackService.contains("PlaybackVisualizationCacheStateOwner playbackVisualizationCacheStateOwner ="));
        assertTrue(playbackService.contains("new PlaybackVisualizationCacheStateOwner("));
        String normalizedVisualizationCacheStateWiring = normalizedPlaybackService.substring(
                normalizedPlaybackService.indexOf(
                        "PlaybackVisualizationCacheStateOwner playbackVisualizationCacheStateOwner ="
                ),
                normalizedPlaybackService.indexOf(
                        "        playbackVisualizationCacheManager = PlaybackVisualizationCacheManager.fromMediaSourceProvider("
                )
        );
        assertTrue(normalizedVisualizationCacheStateWiring.contains("                        playbackQueueStateOwner,\n"));
        assertFalse(normalizedVisualizationCacheStateWiring.contains("currentTrackSupplier"));
        assertTrue(playbackService.contains(
                "playbackVisualizationCacheManager = PlaybackVisualizationCacheManager.fromMediaSourceProvider("));
        assertTrue(playbackService.contains("                playbackVisualizationCacheStateOwner,"));
        assertFalse(playbackService.contains("new PlaybackVisualizationCacheManager.StateProvider()"));
        assertTrue(playbackService.contains("playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager("));
        assertTrue(normalizedPlaybackService.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                currentTrackSupplier,"
        ));
        assertEquals(1, countOccurrences(playbackService,
                "final Supplier<Track> currentTrackSupplier = playbackNotificationStateOwner::currentTrack;"));
        assertFalse(normalizedPlaybackService.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                playbackQueueStateOwner::currentTrack,"
        ));
        assertFalse(normalizedPlaybackService.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                playbackQueueManager,"
        ));
        assertFalse(normalizedPlaybackService.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                playbackQueueStateOwner::queueStateSnapshot,"
        ));
        assertFalse(playbackService.contains("new PlaybackNotificationArtworkManager.NotificationBridge()"));
        assertTrue(playbackService.contains("new PlaybackNotificationArtworkBridgeOwner("));
        assertTrue(playbackService.contains("                        playbackSessionRefresher,"));
        assertTrue(playbackService.contains("                        playbackNotificationCommandOwner::publishPlaybackNotification"));
        assertFalse(playbackService.contains("                        EchoPlaybackService.this::publishPlaybackNotification"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackSessionRefreshOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackSessionRefreshOwnerTest.java")));
        assertFalse(playbackService.contains("PlaybackSessionRefreshOwner"));
        assertFalse(playbackService.contains("playbackSessionRefreshOwner"));
        assertFalse(playbackService.contains("private PlaybackNotificationArtworkBridgeOwner.SessionRefresher playbackSessionRefresher;"));
        assertTrue(playbackService.contains(
                "final PlaybackNotificationArtworkBridgeOwner.SessionRefresher playbackSessionRefresher =\n"
                        + "                PlaybackNotificationArtworkBridgeOwner.sessionRefresherFromPlaybackSessionManager("));
        assertFalse(playbackService.contains("private void refreshPlaybackSession()"));
        assertTrue(playbackService.contains("postponePlaybackVisualizationWarmup();"));
        assertTrue(playbackService.contains("private PlaybackStateSnapshotOwner playbackStateSnapshotOwner;"));
        assertTrue(playbackService.contains("playbackStateSnapshotOwner = new PlaybackStateSnapshotOwner("));
        assertTrue(normalizedPlaybackService.contains(
                "playbackStateSnapshotOwner = new PlaybackStateSnapshotOwner(\n"
                        + "                playbackQueueStateOwner,"));
        assertFalse(normalizedPlaybackService.contains(
                "playbackStateSnapshotOwner = new PlaybackStateSnapshotOwner(\n"
                        + "                queueStateSupplier,"));
        assertTrue(playbackService.contains("PlaybackStateSnapshotOwner.fromRuntimeStateManager(playbackRuntimeStateManager)"));
        assertFalse(playbackService.contains("PlaybackStateSnapshotOwner.fromRuntimeStateManagerProvider("));
        assertTrue(playbackService.contains("PlaybackStateSnapshotOwner.fromVisualizationAnalyzer(playbackVisualizationAnalyzer)"));
        assertFalse(playbackService.contains("PlaybackStateSnapshotOwner.fromVisualizationAnalyzerProvider("));
        assertTrue(playbackService.contains("return playbackStateSnapshotOwner == null"));
        assertFalse(playbackService.contains("boolean deferVisualGeneration = shouldDeferPlaybackVisualization();"));
        assertFalse(playbackService.contains("waveformSnapshotFor(track, duration, deferVisualGeneration)"));
        assertFalse(playbackService.contains("spectrumSnapshotFor(track, duration, deferVisualGeneration)"));
        assertFalse(playbackService.contains("private PlaybackWaveformSnapshot waveformSnapshotFor("));
        assertFalse(playbackService.contains("private PlaybackSpectrumSnapshot spectrumSnapshotFor("));
        assertFalse(playbackService.contains("private boolean shouldDeferPlaybackVisualization()"));
        assertTrue(stateSnapshotOwner.contains("final class PlaybackStateSnapshotOwner"));
        assertFalse(stateSnapshotOwner.contains("interface QueueStateProvider"));
        assertFalse(stateSnapshotOwner.contains("interface SleepTimerProvider"));
        assertFalse(stateSnapshotOwner.contains("interface RuntimeStateManagerProvider"));
        assertFalse(stateSnapshotOwner.contains("interface VisualizationAnalyzerProvider"));
        assertFalse(stateSnapshotOwner.contains("interface RealtimeBeatProvider"));
        assertFalse(stateSnapshotOwner.contains("private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateProvider;"));
        assertTrue(stateSnapshotOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(stateSnapshotOwner.contains("PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateOwner == null"));
        assertFalse(stateSnapshotOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertTrue(stateSnapshotOwner.contains("Track track = queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertTrue(stateSnapshotOwner.contains("int currentIndex = queueStateOwner == null ? -1 : queueStateOwner.currentIndex();"));
        assertTrue(stateSnapshotOwner.contains("int queueSize = queueStateOwner == null ? 0 : queueStateOwner.queueSize();"));
        assertTrue(stateSnapshotOwner.contains("private final LongSupplier sleepTimerProvider;"));
        assertTrue(stateSnapshotOwner.contains("private final DoubleSupplier realtimeBeatProvider;"));
        assertFalse(stateSnapshotOwner.contains("Supplier<PlaybackRuntimeStateManager> runtimeStateManagerProvider"));
        assertTrue(stateSnapshotOwner.contains("static RuntimeStateProvider fromRuntimeStateManager("));
        assertTrue(stateSnapshotOwner.contains("static VisualizationProvider fromVisualizationAnalyzer("));
        assertFalse(stateSnapshotOwner.contains("Supplier<PlaybackVisualizationAnalyzer>"));
        assertFalse(stateSnapshotOwner.contains("visualizationAnalyzerProvider"));
        assertTrue(stateSnapshotOwner.contains("interface RuntimeStateProvider"));
        assertTrue(stateSnapshotOwner.contains("interface VisualizationProvider"));
        assertTrue(stateSnapshotOwner.contains("boolean deferVisualGeneration = visualizationProvider != null"));
        assertTrue(stateSnapshotOwner.contains("visualizationProvider.waveformSnapshot(track, durationMs, deferVisualGeneration)"));
        assertTrue(stateSnapshotOwner.contains("visualizationProvider.spectrumSnapshot(track, durationMs, deferVisualGeneration)"));
        assertTrue(stateSnapshotOwner.contains("return new PlaybackStateSnapshot("));
        assertTrue(visualizationAnalyzer.contains("internal class PlaybackVisualizationAnalyzer"));
        assertTrue(visualizationAnalyzer.contains("fun waveformSnapshot(track: Track?, durationMs: Long, deferGeneration: Boolean)"));
        assertTrue(visualizationAnalyzer.contains("fun spectrumSnapshot(track: Track?, durationMs: Long, deferGeneration: Boolean)"));
        assertTrue(visualizationAnalyzer.contains("fun postponePlaybackVisualizationWarmup()"));
        assertTrue(visualizationAnalyzer.contains("fun shouldDeferPlaybackVisualization(): Boolean"));
        assertTrue(visualizationAnalyzer.contains("fun release()"));
        assertTrue(visualizationAnalyzer.contains("private var released = false"));
        assertTrue(visualizationAnalyzer.contains("fun interface VisualizationTaskScheduler"));
        assertTrue(visualizationAnalyzer.contains("taskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM, Runnable {"));
        assertTrue(visualizationAnalyzer.contains("return@Runnable"));
        assertTrue(visualizationStateOwner.contains("final class PlaybackVisualizationStateOwner implements PlaybackVisualizationAnalyzer.StateProvider"));
        assertFalse(visualizationStateOwner.contains("interface AppVisibilityProvider"));
        assertFalse(visualizationStateOwner.contains("interface BufferedProgressProvider"));
        assertFalse(visualizationStateOwner.contains("interface StatePublisher"));
        assertTrue(visualizationStateOwner.contains("import java.util.function.LongToDoubleFunction;"));
        assertTrue(visualizationStateOwner.contains("private final BooleanSupplier appVisibilityProvider;"));
        assertTrue(visualizationStateOwner.contains("private final LongToDoubleFunction bufferedProgressProvider;"));
        assertTrue(visualizationStateOwner.contains("private final Runnable statePublisher;"));
        assertTrue(visualizationStateOwner.contains("return appVisibilityProvider.getAsBoolean();"));
        assertTrue(bufferedProgressOwner.contains(
                "final class PlaybackBufferedProgressOwner"));
        assertTrue(bufferedProgressOwner.contains("implements LongToDoubleFunction"));
        assertTrue(bufferedProgressOwner.contains("import java.util.function.LongToDoubleFunction;"));
        assertFalse(bufferedProgressOwner.contains("interface PlaybackPositionProvider"));
        assertFalse(bufferedProgressOwner.contains("interface PlayerProvider"));
        assertFalse(bufferedProgressOwner.contains("interface PlayerBufferProvider"));
        assertFalse(bufferedProgressOwner.contains("PlayerBufferProviderSource"));
        assertFalse(bufferedProgressOwner.contains("import androidx.media3.common.Player;"));
        assertTrue(bufferedProgressOwner.contains("private final LongSupplier playbackPositionProvider;"));
        assertFalse(bufferedProgressOwner.contains("private final Supplier<Player> playerProvider;"));
        assertTrue(bufferedProgressOwner.contains("private final LongSupplier bufferedPositionProvider;"));
        assertTrue(bufferedProgressOwner.contains("playbackPositionProvider.getAsLong();"));
        assertFalse(bufferedProgressOwner.contains("Player player = player();"));
        assertFalse(bufferedProgressOwner.contains("player.getBufferedPosition()"));
        assertTrue(bufferedProgressOwner.contains("bufferedPositionProvider.getAsLong();"));
        assertTrue(bufferedProgressOwner.contains("Math.max(positionMs, bufferedPositionMs)"));
        assertTrue(bufferedProgressOwner.contains("Math.max(0.0, Math.min(1.0, bufferedMs / (double) durationMs))"));
        assertTrue(visualizationStateOwner.contains(
                "return bufferedProgressProvider == null ? 0f : (float) bufferedProgressProvider.applyAsDouble(durationMs);"));
        assertTrue(visualizationStateOwner.contains("statePublisher.run();"));
        assertTrue(visualizationAnalyzer.contains("PlaybackWaveformMergePolicy.merge("));
        assertTrue(visualizationAnalyzer.contains("private fun maybeGenerateSpectrum("));
        assertTrue(visualizationAnalyzer.contains("private fun maybeGenerateStreamingWaveform("));
        assertTrue(visualizationAnalyzer.contains("private fun visualizationCachedProgress("));
        assertTrue(visualizationAnalyzer.contains("contentLengthForCacheKey(cacheKey)"));
        assertTrue(visualizationAnalyzer.contains("private val mediaSourceProvider: PlaybackMediaSourceProvider"));
        assertTrue(visualizationAnalyzer.contains("mediaSourceProvider.isHttpTrack(track)"));
        assertTrue(visualizationAnalyzer.contains("mediaSourceProvider.cacheDataSourceForTrack("));
        assertTrue(visualizationAnalyzer.contains("mediaSourceProvider.mediaCacheKeyForTrack(track)"));
        assertTrue(visualizationAnalyzer.contains("mediaSourceProvider.continuousCachedBytes(cacheKey)"));
        assertTrue(visualizationAnalyzer.contains("mediaSourceProvider.contentLengthForCacheKey(cacheKey)"));
        assertFalse(visualizationAnalyzer.contains("fun isHttpUri(uri: Uri?): Boolean"));
        assertFalse(visualizationAnalyzer.contains("fun cacheDataSourceForTrack(track: Track): CacheDataSource"));
        assertFalse(visualizationAnalyzer.contains("fun mediaCacheKeyForTrack(track: Track): String"));
        assertFalse(visualizationAnalyzer.contains("fun continuousCachedBytes(cacheKey: String): Long"));
        assertFalse(visualizationAnalyzer.contains("        fun contentLengthForCacheKey(cacheKey: String): Long"));
        assertTrue(visualizationCacheManager.contains("final class PlaybackVisualizationCacheManager"));
        assertTrue(visualizationCacheManager.contains("void scheduleVisualizationCache(Track track)"));
        assertTrue(visualizationCacheManager.contains("void scheduleVisualizationCacheTask(Runnable task);"));
        assertTrue(visualizationCacheManager.contains("stateProvider.scheduleVisualizationCacheTask(() -> cacheVisualizationWindow(visualTrack, generation))"));
        assertTrue(visualizationCacheManager.contains("void release()"));
        assertTrue(visualizationCacheManager.contains("cacheGeneration.incrementAndGet();"));
        assertTrue(visualizationCacheManager.contains("activeCacheWriters"));
        assertTrue(visualizationCacheManager.contains("writer.cancel();"));
        assertTrue(visualizationCacheManager.contains("private boolean isCurrentCacheGeneration(int generation)"));
        assertTrue(visualizationCacheManager.contains("VISUALIZATION_CACHE_BYTES"));
        assertFalse(visualizationCacheManager.contains("interface MediaCacheOperations"));
        assertTrue(visualizationCacheManager.contains("private final PlaybackMediaCacheOperations mediaCacheOperations;"));
        assertFalse(visualizationCacheManager.contains("String cacheKeyForPrecache(Track track);"));
        assertFalse(visualizationCacheManager.contains(
                "boolean tracksShareResolvedUriForReuse(Track current, Track candidate);"));
        assertFalse(visualizationCacheManager.contains(
                "long cachedBytesInRange(String cacheKey, long position, long length);"));
        assertFalse(visualizationCacheManager.contains("CacheDataSource cacheDataSourceForTrack(Track track);"));
        assertFalse(visualizationCacheManager.contains("long contentLengthForCacheKey(String cacheKey);"));
        assertFalse(visualizationCacheManager.contains("Map<String, String> headersForTrack(Track track);"));
        assertFalse(visualizationCacheManager.contains("boolean mediaItemMatchesTrackForReuse("));
        assertTrue(visualizationCacheManager.contains("static PlaybackVisualizationCacheManager fromMediaSourceProvider("));
        assertFalse(visualizationCacheManager.contains(
                "mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider)"));
        assertTrue(visualizationCacheManager.contains(
                "PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider)"));
        assertTrue(visualizationCacheManager.contains("mediaCacheOperations.cacheKeyForPrecache(track)"));
        assertTrue(visualizationCacheManager.contains(
                "mediaCacheOperations.cachedBytesInRange(cacheKey, 0L, Long.MAX_VALUE)"));
        assertTrue(visualizationCacheManager.contains("mediaCacheOperations.cacheDataSourceForTrack(track)"));
        assertTrue(visualizationCacheManager.contains("current.id == candidate.id"));
        assertTrue(visualizationCacheManager.contains(
                "mediaCacheOperations.tracksShareResolvedUriForReuse(current, candidate)"));
        assertFalse(visualizationCacheManager.contains("private final PlaybackMediaSourceProvider mediaSourceProvider;"));
        assertFalse(visualizationCacheManager.contains("mediaSourceProvider.isHttpTrack(track)"));
        assertFalse(visualizationCacheManager.contains("mediaSourceProvider.cacheKeyForTrack(track)"));
        assertFalse(visualizationCacheManager.contains("mediaSourceProvider.continuousCachedBytes(cacheKey)"));
        assertFalse(visualizationCacheManager.contains("mediaSourceProvider.cacheDataSourceForTrack(track)"));
        assertTrue(visualizationCacheStateOwner.contains("final class PlaybackVisualizationCacheStateOwner implements PlaybackVisualizationCacheManager.StateProvider"));
        assertFalse(visualizationCacheStateOwner.contains("interface MainHandlerProvider"));
        assertFalse(visualizationCacheStateOwner.contains("interface CurrentTrackProvider"));
        assertFalse(visualizationCacheStateOwner.contains("interface CacheTaskScheduler"));
        assertTrue(visualizationCacheStateOwner.contains("private final Handler mainHandler;"));
        assertFalse(visualizationCacheStateOwner.contains("private final Supplier<Handler> mainHandlerProvider;"));
        assertTrue(visualizationCacheStateOwner.contains("private final Consumer<Runnable> cacheTaskScheduler;"));
        assertFalse(visualizationCacheStateOwner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertFalse(visualizationCacheStateOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertTrue(visualizationCacheStateOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(visualizationCacheStateOwner.contains("private final Supplier<Track> currentTrackSupplier;"));
        assertTrue(visualizationCacheStateOwner.contains("return mainHandler;"));
        assertFalse(visualizationCacheStateOwner.contains("return mainHandlerProvider == null ? null : mainHandlerProvider.get();"));
        assertFalse(visualizationCacheStateOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(visualizationCacheStateOwner.contains("PlaybackQueueManager.QueueStateSnapshot.empty()"));
        assertFalse(visualizationCacheStateOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(visualizationCacheStateOwner.contains("return snapshot.getCurrentTrack();"));
        assertTrue(visualizationCacheStateOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertFalse(visualizationCacheStateOwner.contains("queueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertFalse(visualizationCacheStateOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertFalse(visualizationCacheStateOwner.contains("return currentTrackProvider.currentTrack();"));
        assertTrue(visualizationCacheStateOwner.contains("if (cacheTaskScheduler != null)"));
        assertTrue(visualizationCacheStateOwner.contains("cacheTaskScheduler.accept(task);"));
        assertFalse(visualizationCacheManager.contains("boolean isHttpUri(Uri uri);"));
        assertFalse(visualizationCacheManager.contains("String cacheKeyForTrack(Track track);"));
        assertFalse(visualizationCacheManager.contains("long continuousCachedBytes(String cacheKey);"));
        assertFalse(visualizationCacheManager.contains("PlaybackTaskScheduler visualizationTaskScheduler();"));
        assertFalse(visualizationCacheManager.contains("PlaybackCacheDependencies cacheDependencies();"));
        assertFalse(visualizationCacheManager.contains("interface PlaybackCacheDependencies"));
        assertFalse(playbackService.contains("public long contentLengthForCacheKey(String cacheKey)"));
        assertFalse(playbackService.contains("public PlaybackVisualizationCacheManager.PlaybackCacheDependencies cacheDependencies()"));
        assertFalse(playbackService.contains("private void maybeGenerateSpectrum("));
        assertFalse(playbackService.contains("private void maybeGenerateStreamingWaveform("));
        assertFalse(playbackService.contains("private float visualizationCachedProgress("));
        assertFalse(playbackService.contains("private CacheDataSource cacheDataSourceForTrack("));
        assertFalse(playbackService.contains("private long continuousCachedBytes("));
        assertFalse(playbackService.contains("private long contentLengthForCacheKey("));
        assertFalse(playbackService.contains("private String mediaCacheKeyForTrack("));
        assertFalse(playbackService.contains("private String waveformKey("));
        assertFalse(playbackService.contains("private SimpleCache audioCache("));
        assertFalse(playbackService.contains("cacheVisualizationWindow(Track track)"));
        assertTrue(notificationArtworkManager.contains("final class PlaybackNotificationArtworkManager"));
        assertTrue(notificationArtworkManager.contains("interface NotificationBridge"));
        assertTrue(notificationArtworkManager.contains("interface ArtworkLoader"));
        assertTrue(notificationArtworkManager.contains("interface ArtworkEncoder"));
        assertTrue(notificationArtworkManager.contains("private final NotificationBridge notificationBridge;"));
        assertFalse(notificationArtworkManager.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertTrue(notificationArtworkManager.contains("private final Supplier<Track> currentTrackProvider;"));
        assertFalse(notificationArtworkManager.contains("Supplier<PlaybackQueueManager.QueueStateSnapshot>"));
        assertFalse(notificationArtworkManager.contains("PlaybackQueueManager.QueueStateSnapshot snapshot"));
        assertFalse(notificationArtworkManager.contains("playbackQueueManager.queueStateSnapshot()"));
        assertTrue(notificationArtworkManager.contains("return currentTrackProvider == null ? null : currentTrackProvider.get();"));
        assertTrue(notificationArtworkManager.contains("private final AtomicInteger artworkGeneration"));
        assertTrue(notificationArtworkManager.contains("artworkGeneration.incrementAndGet();"));
        assertTrue(notificationArtworkManager.contains("notificationBridge.refreshPlaybackSession();"));
        assertTrue(notificationArtworkManager.contains("notificationBridge.updateMediaNotification();"));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "final class PlaybackNotificationArtworkBridgeOwner"));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "implements PlaybackNotificationArtworkManager.NotificationBridge"));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "interface SessionRefresher extends PlaybackNotificationManager.SessionRefresher"));
        assertTrue(notificationArtworkBridgeOwner.contains("interface SessionOperations"));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "static SessionRefresher sessionRefresherFromPlaybackSessionManager("));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "static SessionRefresher sessionRefresherFromSessionOperations("));
        assertTrue(notificationArtworkBridgeOwner.contains("interface NotificationUpdater"));
        assertTrue(notificationArtworkBridgeOwner.contains("return manager == null ? null : manager::refreshPlayer;"));
        assertTrue(notificationArtworkBridgeOwner.contains("sessionOperations.refreshPlayer();"));
        assertTrue(notificationArtworkBridgeOwner.contains("sessionRefresher.refreshPlaybackSession();"));
        assertTrue(notificationArtworkBridgeOwner.contains("notificationUpdater.updateMediaNotification(true);"));
        assertFalse(normalizedNotificationArtworkManager.contains("interface StateProvider {\n        Track currentTrack();\n        void refreshPlaybackSession();"));
        assertFalse(normalizedNotificationArtworkManager.contains("interface StateProvider {\n        Track currentTrack();\n        void updateMediaNotification();"));
        assertTrue(notificationArtworkManager.contains("Bitmap notificationArtworkFor(Track track)"));
        assertTrue(notificationArtworkManager.contains("byte[] notificationArtworkDataFor(Track track)"));
        assertTrue(notificationArtworkManager.contains("void loadNotificationArtworkAsync(Track track, String key)"));
        assertTrue(notificationArtworkManager.contains("decodeNotificationArtwork(Uri uri)"));
        assertTrue(notificationArtworkManager.contains("openNotificationArtworkStream(Uri uri)"));
        assertTrue(notificationArtworkManager.contains("artworkSampleSize(int width, int height, int targetPx)"));
        assertTrue(notificationArtworkManager.contains("int generation = artworkGeneration.get();"));
        assertTrue(notificationArtworkManager.contains("if (!isCurrentArtworkGeneration(generation))"));
        assertTrue(notificationArtworkManager.contains("private boolean isCurrentArtworkGeneration(int generation)"));
        assertTrue(notificationArtworkManager.contains("artworkCache.put(key, bitmap)"));
        assertTrue(notificationArtworkManager.contains("void release()"));
        assertFalse(playbackService.contains("notificationArtworkCache"));
    }

    @Test
    public void streamingPlaybackCacheUsesSegmentedConcurrentPrecache() throws Exception {
        String playbackService = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedPlaybackService = playbackService.replace("\r\n", "\n");
        String playbackPrecacheManager = read("app/src/main/java/app/yukine/playback/PlaybackPrecacheManager.java");
        String playerStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackPlayerStateOwner.java");
        String playbackVisualizationCacheManager = read(
                "feature/playback/src/main/java/app/yukine/playback/PlaybackVisualizationCacheManager.java"
        );
        String diagnostics = read("feature/playback/src/main/java/app/yukine/playback/diagnostics/PlaybackStreamingDiagnostics.java");
        String precacheWiring = normalizedPlaybackService.substring(
                normalizedPlaybackService.indexOf(
                        "        playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider("),
                normalizedPlaybackService.indexOf(
                        "        );",
                        normalizedPlaybackService.indexOf(
                                "        playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider("))
        );

        assertTrue(playbackService.contains("playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider("));
        assertFalse(playbackService.contains("private PlaybackPrecacheStateOwner playbackPrecacheStateOwner;"));
        assertFalse(playbackService.contains("new PlaybackPrecacheStateOwner("));
        assertFalse(playbackService.contains("PlaybackPrecacheStateOwner playbackPrecacheStateOwner"));
        assertFalse(playbackService.contains("PlaybackPrecacheStateOwner."));
        assertFalse(playbackService.contains("playbackQueueStateOwner::upcomingTracksForPrecache"));
        assertFalse(playbackService.contains("() -> streamingDiagnostics"));
        assertTrue(normalizedPlaybackService.contains(
                "        playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider(\n"
                        + "                PlaybackPlayerStateOwner.mediaItemSupplierFromPlayerSupplier(() -> player),"));
        assertFalse(playbackService.contains("                playbackPrecacheStateOwner,"));
        assertTrue(precacheWiring.contains("                streamingDiagnostics,"));
        assertFalse(precacheWiring.contains("                playbackQueueManager,"));
        assertTrue(precacheWiring.contains("                playbackQueueStateOwner,"));
        assertFalse(normalizedPlaybackService.contains(
                "        playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider(\n"
                        + "                playbackPrecacheStateOwner,\n"
                        + "                playbackQueueStateOwner::upcomingTracksForPrecache,"
        ));
        assertFalse(playbackService.contains("playbackQueueManager::upcomingTracksForPrecache"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackPrecachePlayerMediaItemOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackPrecacheStateOwner.java")));
        assertFalse(playbackService.contains("PlaybackPrecachePlayerMediaItemOwner"));
        assertTrue(playbackService.contains("PlaybackPlayerStateOwner.mediaItemSupplierFromPlayerSupplier(() -> player)"));
        assertFalse(playbackService.contains("|| player.getPlaybackState() == Player.STATE_IDLE"));
        assertFalse(playbackService.contains("return player.getCurrentMediaItem();"));
        assertFalse(playbackService.contains("new PlaybackPrecacheManager.StateProvider()"));
        assertTrue(playbackService.contains("public void warmPlaybackTrack(Track track)"));
        assertTrue(normalizedPlaybackService.contains("public void warmPlaybackTrack(Track track) {\n        if (playbackWarmupCoordinator != null) {\n            playbackWarmupCoordinator.warmup(track);\n        }\n    }"));
        assertFalse(normalizedPlaybackService.contains("public void warmPlaybackTrack(Track track) {\n        if (playbackPrecacheManager != null) {\n            playbackPrecacheManager.precacheTrack(track);\n        }\n    }"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackWarmupActionsOwner.java")));
        assertFalse(playbackService.contains("PlaybackWarmupActionsOwner"));
        assertFalse(playbackService.contains("playbackWarmupActionsOwner"));
        assertTrue(playbackService.contains("private PlaybackWarmupCoordinator playbackWarmupCoordinator;"));
        assertTrue(playbackService.contains("playbackWarmupCoordinator = new PlaybackWarmupCoordinator("));
        assertFalse(playbackService.contains("PlaybackPrecacheManager.precacheTrackActionFromSupplier("));
        assertFalse(playbackService.contains("PlaybackVisualizationCacheManager.scheduleVisualizationCacheActionFromSupplier("));
        assertFalse(playbackPrecacheManager.contains("static Consumer<Track> precacheTrackActionFromSupplier("));
        assertFalse(playbackVisualizationCacheManager.contains(
                "static Consumer<Track> scheduleVisualizationCacheActionFromSupplier("));
        assertTrue(playbackService.contains("playbackPrecacheManager.precacheTrack(track);"));
        assertTrue(playbackService.contains("playbackVisualizationCacheManager.scheduleVisualizationCache(track);"));
        assertTrue(playbackService.contains("PlaybackPrecacheManager::release"));
        assertFalse(playbackService.contains("playbackPrecacheManager.release();"));
        assertFalse(playbackService.contains("private void precacheUpcomingTracks("));
        assertFalse(playbackService.contains("private void precacheWithMediaCache("));
        assertFalse(playbackService.contains("private void scheduleCurrentSegmentedPrecache("));
        assertFalse(playbackService.contains("private void submitPlaybackCacheTask("));
        assertFalse(playbackService.contains("private static final class PrecacheTask"));
        assertFalse(playbackService.contains("private enum PrecachePriority"));
        assertFalse(playbackService.contains("private enum PrecacheMode"));
        assertFalse(playbackService.contains("private final ThreadPoolExecutor playbackCacheExecutor"));
        assertFalse(playbackService.contains("private final Set<String> activePrecacheRanges"));
        assertFalse(playbackService.contains("private final Set<CacheWriter> activePrecacheWriters"));
        assertFalse(playbackService.contains("private final AtomicInteger precacheGeneration"));
        assertFalse(playbackService.contains("private volatile String lastPrecacheKey"));

        assertTrue(playbackPrecacheManager.contains("final class PlaybackPrecacheManager"));
        String playbackMediaCacheOperations = read(
                "feature/playback/src/main/java/app/yukine/playback/manager/PlaybackMediaCacheOperations.java"
        );
        assertFalse(playbackPrecacheManager.contains("interface MediaCacheOperations"));
        assertTrue(playbackPrecacheManager.contains("private final PlaybackMediaCacheOperations mediaCacheOperations;"));
        assertTrue(playbackPrecacheManager.contains(
                "import app.yukine.playback.manager.PlaybackMediaCacheOperations;"));
        assertTrue(playbackMediaCacheOperations.contains("public interface PlaybackMediaCacheOperations"));
        assertTrue(playbackMediaCacheOperations.contains("final class PlaybackMediaSourceProviderCacheOperations"));
        assertTrue(playbackMediaCacheOperations.contains("String cacheKeyForPrecache(Track track);"));
        assertTrue(playbackMediaCacheOperations.contains("CacheDataSource cacheDataSourceForTrack(Track track);"));
        assertTrue(playbackMediaCacheOperations.contains("long contentLengthForCacheKey(String cacheKey);"));
        assertTrue(playbackMediaCacheOperations.contains("mediaSourceProvider.cacheKeyForTrack(track)"));
        assertTrue(playbackMediaCacheOperations.contains("mediaSourceProvider.cacheDataSourceForTrack(track)"));
        assertFalse(playbackService.contains("PlaybackPrecacheManager.mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider)"));
        assertFalse(playbackService.contains("PlaybackPrecacheManager.audioCacheReleaseActionFromMediaSourceProvider(mediaSourceProvider)"));
        assertTrue(playbackPrecacheManager.contains("static PlaybackPrecacheManager fromMediaSourceProvider("));
        assertTrue(playbackPrecacheManager.contains(
                "PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider)"));
        assertFalse(playbackPrecacheManager.contains("mediaCacheOperationsFromMediaSourceProvider("));
        assertFalse(playbackPrecacheManager.contains("audioCacheReleaseActionFromMediaSourceProvider("));
        assertFalse(playbackPrecacheManager.contains(
                "private void precacheWithMediaCache(Track track, int generation, PrecacheMode mode)"));
        assertFalse(normalizedPlaybackService.contains(
                "new PlaybackPrecacheManager(\n"
                        + "                playbackPrecacheStateOwner,\n"
                        + "                playbackQueueStateOwner::upcomingTracksForPrecache,\n"
                        + "                mediaSourceProvider,\n"
                        + "                playbackMainHandlerSchedulerOwner"
        ));
        String playbackPrecacheManagerBody =
                playbackPrecacheManager.substring(
                        0,
                        playbackPrecacheManager.indexOf(
                                "private static ThreadPoolExecutor newPlaybackCacheExecutor"
                        )
                );
        assertFalse(playbackPrecacheManagerBody.contains("private final PlaybackMediaSourceProvider mediaSourceProvider;"));
        String normalizedPlaybackPrecacheManager = playbackPrecacheManager.replace("\r\n", "\n");
        assertFalse(normalizedPlaybackPrecacheManager.contains(
                "PlaybackPrecacheManager(\n"
                        + "            StateProvider stateProvider,\n"
                        + "            PlaybackMediaSourceProvider mediaSourceProvider"
        ));
        assertFalse(playbackPrecacheManager.contains("interface StateProvider"));
        assertFalse(playbackPrecacheManager.contains("MediaItem currentPlayerMediaItem();"));
        assertFalse(playbackPrecacheManager.contains("private final StateProvider stateProvider;"));
        assertTrue(playbackPrecacheManager.contains(
                "private final Supplier<MediaItem> currentPlayerMediaItemSupplier;"));
        assertTrue(normalizedPlaybackPrecacheManager.contains(
                "PlaybackPrecacheManager(\n"
                        + "            Supplier<MediaItem> currentPlayerMediaItemSupplier,"));
        assertFalse(normalizedPlaybackPrecacheManager.contains(
                "            PlaybackMediaCacheOperations mediaCacheOperations,\n"
                        + "            CallbackScheduler callbackScheduler,"));
        assertFalse(normalizedPlaybackPrecacheManager.contains(
                "PlaybackPrecacheManager(\n"
                        + "            StateProvider stateProvider,\n"
                        + "            IntFunction<List<Track>> upcomingTracksProvider,\n"
                        + "            PlaybackMediaSourceProvider mediaSourceProvider"
        ));
        assertTrue(playbackPrecacheManager.contains("interface CallbackScheduler"));
        assertTrue(playbackPrecacheManager.contains("void postDelayed(Runnable runnable, long delayMs);"));
        assertTrue(playbackPrecacheManager.contains("void removeCallbacks(Runnable runnable);"));
        assertFalse(playbackPrecacheManager.contains("interface UpcomingTracksProvider"));
        assertFalse(playbackPrecacheManager.contains("import java.util.function.IntFunction;"));
        assertFalse(playbackPrecacheManager.contains("private final IntFunction<List<Track>> upcomingTracksProvider;"));
        assertFalse(playbackPrecacheManager.contains("upcomingTracksProvider.apply(SEGMENTED_PRECACHE_CONCURRENCY)"));
        assertFalse(playbackPrecacheManager.contains("List<Track> upcomingTracksForPrecache(int maxCount);"));
        assertFalse(playbackPrecacheManager.contains("Track currentTrack();"));
        assertFalse(playbackPrecacheManager.contains("PlaybackStreamingDiagnostics streamingDiagnostics();"));
        assertTrue(playbackPrecacheManager.contains("private final PlaybackStreamingDiagnostics streamingDiagnostics;"));
        assertTrue(playbackPrecacheManager.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertTrue(playbackPrecacheManager.contains("private Track currentTrack()"));
        assertFalse(playbackPrecacheManager.contains("playbackQueueManager.queueStateSnapshot().getCurrentTrack()"));
        assertTrue(playbackPrecacheManager.contains("return queueStateOwner.currentTrack();"));
        assertFalse(playbackPrecacheManager.contains("stateProvider.currentTrack()"));
        assertFalse(playbackPrecacheManager.contains(
                "playbackQueueManager.upcomingTracksForPrecache(SEGMENTED_PRECACHE_CONCURRENCY)"));
        assertTrue(playbackPrecacheManager.contains(
                "queueStateOwner.upcomingTracksForPrecache(SEGMENTED_PRECACHE_CONCURRENCY)"));
        assertFalse(playbackPrecacheManager.contains(
                "stateProvider.upcomingTracksForPrecache(SEGMENTED_PRECACHE_CONCURRENCY)"));
        assertFalse(playbackPrecacheManager.contains("interface AudioCacheReleaser"));
        assertFalse(playbackPrecacheManager.contains("interface PrecacheManagerProvider"));
        assertTrue(playbackPrecacheManager.contains("import java.util.function.Supplier;"));
        assertFalse(playbackPrecacheManager.contains("import java.util.function.Consumer;"));
        assertFalse(playbackPrecacheManager.contains("static Runnable audioCacheReleaseActionFromPrecacheManagerSupplier("));
        assertTrue(playbackPrecacheManager.contains("private final Runnable audioCacheReleaseAction;"));
        assertFalse(playbackPrecacheManager.contains("implements MediaCacheOperations, AudioCacheReleaser"));
        assertFalse(playbackPrecacheManager.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertFalse(playbackPrecacheManager.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertTrue(playbackPrecacheManager.contains("private final CallbackScheduler callbackScheduler;"));
        assertTrue(playbackPrecacheManager.contains("PlaybackPrecacheManager("));
        assertTrue(playbackPrecacheManager.contains("CallbackScheduler callbackScheduler"));
        assertFalse(playbackPrecacheManager.contains("Handler mainHandler();"));
        assertFalse(playbackPrecacheManager.contains("Map<String, String> headersForTrack(Track track);"));
        assertFalse(playbackMediaCacheOperations.contains("Map<String, String> headersForTrack(Track track);"));
        assertTrue(playbackMediaCacheOperations.contains(
                "long probeSegmentedPrecacheContentLength(Track track, String cacheKey, long start, long length);"));
        assertFalse(playbackPrecacheManager.contains("SimpleCache audioCache();"));
        assertFalse(playbackPrecacheManager.contains("private static final class PlaybackMediaSourceProviderCacheOperations"));
        assertFalse(playbackPrecacheManager.contains("PlaybackMediaSourceResolutionOwner"));
        assertFalse(playerStateOwner.contains("PlaybackPrecacheManager.StateProvider"));
        assertFalse(playerStateOwner.contains("interface CurrentTrackProvider"));
        assertFalse(playerStateOwner.contains("interface PlayerMediaItemProvider"));
        assertFalse(playerStateOwner.contains("interface StreamingDiagnosticsProvider"));
        assertFalse(playerStateOwner.contains("interface PlayerProvider"));
        assertFalse(playerStateOwner.contains("interface PlayerOperationsProvider"));
        assertTrue(playerStateOwner.contains("import java.util.function.Supplier;"));
        assertFalse(playerStateOwner.contains("import java.util.function.IntFunction;"));
        assertFalse(playerStateOwner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertFalse(playerStateOwner.contains("private final Supplier<MediaItem> playerMediaItemSupplier;"));
        assertFalse(playerStateOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(playerStateOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(playerStateOwner.contains("private final Supplier<Track> currentTrackSupplier;"));
        assertFalse(playerStateOwner.contains("private final IntFunction<List<Track>> upcomingTracksProvider;"));
        assertFalse(playerStateOwner.contains("private final PlaybackStreamingDiagnostics streamingDiagnostics;"));
        assertFalse(playerStateOwner.contains("private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsSupplier;"));
        assertFalse(playerStateOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(playerStateOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(playerStateOwner.contains("return snapshot.getCurrentTrack();"));
        assertFalse(playerStateOwner.contains(": queueStateOwner.currentTrack();"));
        assertFalse(playerStateOwner.contains("queueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertFalse(playerStateOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertFalse(playerStateOwner.contains("queueStateOwner.upcomingTracksForPrecache(maxCount)"));
        assertFalse(playerStateOwner.contains("PlaybackQueueManager.QueueStateSnapshot.empty()"));
        assertFalse(playerStateOwner.contains(
                "return playerMediaItemSupplier == null ? null : playerMediaItemSupplier.get();"));
        assertFalse(playerStateOwner.contains("return streamingDiagnostics;"));
        assertFalse(playerStateOwner.contains("return streamingDiagnosticsSupplier.get();"));
        assertFalse(playerStateOwner.contains("interface PlayerOperations"));
        assertFalse(playerStateOwner.contains("Media3PlayerOperations"));
        assertFalse(playerStateOwner.contains("playerMediaItemSupplierFromOperationsSupplier"));
        assertTrue(playerStateOwner.contains("import java.util.function.IntSupplier;"));
        assertTrue(playerStateOwner.contains("static Supplier<MediaItem> mediaItemSupplierFromPlayerSupplier("));
        assertTrue(playerStateOwner.contains("static Supplier<MediaItem> mediaItemSupplierFromStateSuppliers("));
        assertTrue(playerStateOwner.contains("private static MediaItem mediaItemFromStateSuppliers("));
        assertTrue(playerStateOwner.contains("playbackStateSupplier.getAsInt() == Player.STATE_IDLE"));
        assertTrue(playerStateOwner.contains("mediaItemCountSupplier.getAsInt() <= 0"));
        assertTrue(playerStateOwner.contains("return currentMediaItemSupplier.get();"));
        assertTrue(playbackPrecacheManager.contains("void precacheTrack(Track track)"));
        assertTrue(playbackPrecacheManager.contains("void release()"));
        assertFalse(playbackService.contains("PlaybackPrecacheManager.audioCacheReleaseActionFromPrecacheManagerSupplier("));
        assertTrue(playbackService.contains("PlaybackShutdownPlaybackResourcesOwner.releaseFrom("));
        assertTrue(playbackService.contains("PlaybackPrecacheManager::releaseAudioCache"));
        assertFalse(playbackService.contains("PlaybackPrecacheManager.audioCacheReleaserFromPrecacheManagerProvider("));
        assertFalse(playbackService.contains("playbackPrecacheManager.releaseAudioCache();"));
        assertTrue(playbackPrecacheManager.contains("private final ThreadPoolExecutor playbackCacheExecutor"));
        assertTrue(playbackPrecacheManager.contains("private final Set<String> activePrecacheRanges"));
        assertTrue(playbackPrecacheManager.contains("private final Set<CacheWriter> activePrecacheWriters"));
        assertTrue(playbackPrecacheManager.contains("private final List<Runnable> pendingPrecacheCallbacks"));
        assertTrue(playbackPrecacheManager.contains("private final AtomicInteger precacheGeneration = new AtomicInteger();"));
        assertTrue(playbackPrecacheManager.contains("private final AtomicBoolean audioCacheReleased = new AtomicBoolean();"));
        assertTrue(playbackPrecacheManager.contains("private volatile String lastPrecacheKey = \"\";"));
        assertFalse(playbackPrecacheManager.contains("String mediaCacheKeyForTrack(Track track);"));
        assertFalse(playbackPrecacheManager.contains("mediaCacheOperations.mediaCacheKeyForTrack(track)"));
        assertTrue(playbackPrecacheManager.contains("private void precacheUpcomingTracks(int generation)"));
        assertTrue(playbackPrecacheManager.contains("private void precacheWithMediaCache("));
        assertTrue(playbackPrecacheManager.contains("private void scheduleCurrentSegmentedPrecache(Track track, String cacheKey, int generation)"));
        assertTrue(playbackPrecacheManager.contains("private SegmentedPrecacheProbe probeSegmentedPrecache(Track track, String cacheKey, int generation)"));
        assertFalse(playbackPrecacheManager.contains("static long totalBytesFromContentRange(String contentRange)"));
        assertTrue(playbackPrecacheManager.contains("mediaCacheOperations.probeSegmentedPrecacheContentLength("));
        assertFalse(playbackPrecacheManager.contains("mediaCacheOperations.headersForTrack("));
        assertFalse(playbackPrecacheManager.contains("import java.net.HttpURLConnection;"));
        assertFalse(playbackPrecacheManager.contains("import java.net.URL;"));
        assertTrue(playbackPrecacheManager.contains("private void precacheMediaSegments("));
        assertTrue(playbackPrecacheManager.contains("private long currentSegmentedPrecacheStart(String cacheKey)"));
        assertTrue(playbackPrecacheManager.contains("static long segmentedPrecacheStart(long leadingBytes, long continuousCachedBytes)"));
        assertTrue(playbackPrecacheManager.contains("static List<PrecacheSegment> planPrecacheSegments("));
        assertTrue(playbackPrecacheManager.contains("static DataSpec cacheRangeDataSpec("));
        assertTrue(playbackPrecacheManager.contains("private void submitPlaybackCacheTask(PrecachePriority priority, Runnable task)"));
        assertTrue(playbackPrecacheManager.contains("private void trimPlaybackCacheQueueIfNeeded(PrecachePriority priority)"));
        assertTrue(playbackPrecacheManager.contains("private void postDelayedPrecacheCallback(Runnable task, long delayMs)"));
        assertTrue(playbackPrecacheManager.contains("private void cancelPendingPrecacheCallbacks()"));
        assertTrue(playbackPrecacheManager.contains("callbackScheduler.removeCallbacks(callback);"));
        assertTrue(playbackPrecacheManager.contains("private boolean isCurrentPrecacheGeneration(int generation, String cacheKey)"));
        assertTrue(playbackPrecacheManager.contains("private long cachedBytesInRange(String cacheKey, long position, long length)"));
        assertTrue(playbackPrecacheManager.contains("return cachedBytesInRange(cacheKey, 0L, Long.MAX_VALUE);"));
        assertTrue(playbackPrecacheManager.contains("private long cacheMediaRange(Track track, String cacheKey, long position, long length, int generation)"));
        assertTrue(playbackPrecacheManager.contains("private void cancelActivePrecacheWriters()"));
        assertTrue(playbackPrecacheManager.contains("private static final class PlaybackCacheThreadFactory implements ThreadFactory"));
        assertTrue(playbackPrecacheManager.contains("private static final class PrecacheTask implements Runnable, Comparable<PrecacheTask>"));
        assertTrue(playbackPrecacheManager.contains("private static final class PrecacheSupersededException extends RuntimeException"));
        assertTrue(playbackPrecacheManager.contains("private enum PrecachePriority"));
        assertTrue(playbackPrecacheManager.contains("private enum PrecacheMode"));
        assertFalse(playbackPrecacheManager.contains("private final PlaybackMediaSourceProvider mediaSourceProvider;"));
        assertTrue(playbackPrecacheManager.contains("private String cacheKeyForPrecache(Track track)"));
        assertTrue(playbackPrecacheManager.contains("cacheKeyForPrecache(upcomingTrack)"));
        assertTrue(playbackPrecacheManager.contains("String cacheKey = cacheKeyForPrecache(track);"));
        assertFalse(playbackPrecacheManager.contains("mediaCacheOperations.isHttpTrack(track)"));
        assertFalse(playbackPrecacheManager.contains("mediaCacheOperations.cacheKeyForTrack(track)"));
        assertFalse(playbackPrecacheManager.contains("boolean isHttpUri(Uri uri);"));
        assertFalse(playbackPrecacheManager.contains("boolean isHttpTrack(Track track);"));
        assertFalse(playbackPrecacheManager.contains("String cacheKeyForTrack(Track track);"));
        assertTrue(playbackMediaCacheOperations.contains("String cacheKeyForPrecache(Track track);"));
        assertTrue(playbackPrecacheManager.contains("mediaCacheOperations.cacheKeyForPrecache(track)"));
        assertFalse(playbackService.contains("private boolean isHttpUri(Uri uri)"));
        assertFalse(playbackService.contains("private String cacheKeyForTrack(Track track)"));
        assertFalse(playbackService.contains("private Map<String, String> headersForTrack(Track track)"));
        assertFalse(playbackService.contains("EchoPlaybackService.this.isHttpUri(uri)"));
        assertFalse(playbackService.contains("mediaSourceProvider.cacheKeyForTrack(track)"));
        assertFalse(playbackService.contains("mediaSourceProvider.mediaCacheKeyForTrack("));
        assertFalse(playbackService.contains("mediaSourceProvider.cacheDataSourceForTrack("));
        assertFalse(playbackService.contains("mediaSourceProvider.continuousCachedBytes("));
        assertFalse(playbackService.contains("mediaSourceProvider.contentLengthForCacheKey("));
        assertFalse(playbackService.contains("mediaSourceProvider.headersForTrack("));
        assertFalse(playbackService.contains("mediaSourceProvider.audioCache()"));
        assertFalse(playbackService.contains("mediaSourceProvider.releaseAudioCache()"));
        assertFalse(playbackService.contains("import java.net.HttpURLConnection;"));
        assertFalse(playbackService.contains("import java.net.URL;"));
        assertFalse(playbackService.contains("import java.io.InputStream;"));
        assertFalse(playbackService.contains("import java.nio.charset.StandardCharsets;"));
        assertFalse(playbackService.contains("import android.util.LruCache;"));
        assertFalse(playbackService.contains("import android.graphics.Bitmap;"));
        assertFalse(playbackService.contains("import android.util.Base64;"));
        assertTrue(playbackPrecacheManager.contains("SEGMENTED_PRECACHE_BYTES"));
        assertTrue(playbackPrecacheManager.contains("SEGMENTED_PRECACHE_CHUNK_BYTES"));
        assertTrue(playbackPrecacheManager.contains("SEGMENTED_PRECACHE_CONCURRENCY"));
        assertTrue(playbackPrecacheManager.contains("UPCOMING_TRACK_PRECACHE_BYTES"));
        assertTrue(playbackPrecacheManager.contains("UPCOMING_TRACK_PRECACHE_DELAY_MS"));
        assertTrue(playbackPrecacheManager.contains("PLAYBACK_CACHE_QUEUE_CAPACITY"));
        assertTrue(playbackPrecacheManager.contains("CURRENT_TRACK_LEADING_PRECACHE_DELAY_MS"));
        assertTrue(playbackPrecacheManager.contains("CURRENT_TRACK_SEGMENTED_PRECACHE_DELAY_MS"));
        assertTrue(playbackPrecacheManager.contains("PRECACHE_RANGE_PROBE_BYTES"));
        assertTrue(playbackPrecacheManager.contains("currentPlayerLoadsTrack(precacheTrack)"));
        assertTrue(playbackPrecacheManager.contains("cacheMediaRange(track, cacheKey, segment.start, segment.length, generation)"));
        assertTrue(playbackPrecacheManager.contains("recordPrecacheSegmentComplete(track, segment.start, cached)"));
        assertTrue(playbackPrecacheManager.contains("recordPrecacheSegmentFailed(track, segment.start, error)"));
        assertTrue(playbackPrecacheManager.contains("Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)"));
        assertTrue(diagnostics.contains("precacheSegmentSuccesses"));
        assertTrue(diagnostics.contains("precacheSegmentFailures"));
        assertTrue(diagnostics.contains("precache_segment_complete"));
        assertTrue(diagnostics.contains("precache_segment_failed"));
    }

    @Test
    public void downloadRequestsAreOwnedOutsideMainActivity() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String requestController = read("app/src/main/java/app/yukine/DownloadRequestController.kt");
        String qualityDialogController = read("app/src/main/java/app/yukine/DownloadQualityDialogController.kt");
        String downloadManager = read("app/src/main/java/app/yukine/TrackDownloadManager.kt");
        String downloadsViewModel = read("app/src/main/java/app/yukine/DownloadsViewModel.kt");
        String downloadsContracts = read("feature/navigation/src/main/java/app/yukine/DownloadsDestinationContracts.kt");
        String downloadsDestination = read("feature/navigation/src/main/java/app/yukine/downloads/DownloadsDestination.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String documentPickerController = read("app/src/main/java/app/yukine/DocumentPickerController.kt");
        String appLanguage = read("app/src/main/java/app/yukine/AppLanguage.java");

        assertFalse(exists("app/src/main/java/app/yukine/DownloadsCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/FileIOCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NavigationCoordinator.kt"));
        assertFalse(mainActivity.contains("DownloadsCoordinator downloadsCoordinator"));
        assertFalse(mainActivity.contains("FileIOCoordinator fileIOCoordinator"));
        assertFalse(mainActivity.contains("LibraryCoordinator libraryCoordinator"));
        assertFalse(mainActivity.contains("NavigationCoordinator navigationCoordinator"));
        assertFalse(mainActivity.contains("new DownloadsCoordinator("));
        assertFalse(mainActivity.contains("new FileIOCoordinator("));
        assertFalse(mainActivity.contains("new LibraryCoordinator("));
        assertFalse(mainActivity.contains("new NavigationCoordinator("));
        assertFalse(mainActivity.contains("new LibraryCoordinator.Listener()"));
        assertFalse(mainActivity.contains("new NavigationCoordinator.Listener()"));
        assertFalse(mainActivity.contains("downloadsCoordinator.downloadTrack(track)"));
        assertTrue(requestController.contains("internal class DownloadRequestController("));
        assertFalse(requestController.contains("internal fun interface DownloadManagerProvider"));
        assertFalse(requestController.contains("internal fun interface DownloadStatusSink"));
        assertTrue(requestController.contains("private val downloadManagerProvider: () -> TrackDownloadRequestQueue?"));
        assertTrue(requestController.contains("private val resolveStreamingPlaybackUseCase: StreamingPlaybackResolvePlanner"));
        assertTrue(requestController.contains("private val qualityChooser: DownloadQualityChooser"));
        assertTrue(requestController.contains("private val streamingResolver: StreamingDownloadResolver"));
        assertTrue(requestController.contains("private val statusSink: (String) -> Unit"));
        assertTrue(requestController.contains("fun downloadTrack(track: Track?)"));
        assertTrue(requestController.contains("fun downloadTracks(tracks: List<Track>?)"));
        assertTrue(requestController.contains("fun downloadTrackWithQuality(track: Track, quality: StreamingAudioQuality, silent: Boolean)"));
        assertTrue(requestController.contains("resolveStreamingPlaybackUseCase.prepareDownload(track)"));
        assertTrue(requestController.contains("downloadManager.enqueue(track, quality)"));
        assertTrue(requestController.contains("downloadsViewModel.refresh(downloadManager)"));
        assertTrue(requestController.contains("statusSink(\"未选择歌曲\")"));
        assertTrue(qualityDialogController.contains("internal class DownloadQualityDialogController("));
        assertTrue(qualityDialogController.contains(": DownloadQualityChooser"));
        assertFalse(qualityDialogController.contains("DownloadQualitySelectedCallback"));
        assertTrue(qualityDialogController.contains("onQualitySelected: (StreamingAudioQuality) -> Unit"));
        assertTrue(qualityDialogController.contains("StreamingQualityPlatformMapping.optionLabel"));
        assertTrue(qualityDialogController.contains("StreamingQualityPlatformMapping.downloadDialogMessage"));
        assertFalse(exists("app/src/main/java/app/yukine/DownloadDirectoryPickerController.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/DownloadDirectoryPickerControllerTest.kt"));
        assertTrue(mainActivity.contains("new DownloadRequestController("));
        assertFalse(mainActivity.contains("private void downloadTrack("));
        assertFalse(mainActivity.contains("private void chooseDirectory("));
        assertFalse(mainActivity.contains("new DownloadDirectoryPickerController("));
        assertTrue(documentPickerController.contains("fun openDownloadFolderPicker()"));
        assertTrue(downloadsContracts.contains("interface TrackDownloadController"));
        assertTrue(downloadsContracts.contains("data class TrackDownloadActionResult"));
        assertFalse(downloadManager.contains("interface TrackDownloadController"));
        assertFalse(downloadManager.contains("data class TrackDownloadActionResult"));
        assertTrue(downloadManager.contains("interface TrackDownloadRequestQueue : TrackDownloadDirectoryController"));
        assertTrue(downloadManager.contains(") : TrackDownloadRequestQueue"));
        assertTrue(downloadManager.contains("interface TrackDownloadDirectoryController : TrackDownloadController"));
        assertTrue(downloadManager.contains("fun downloadDirectoryLabel(): String"));
        assertTrue(downloadManager.contains("fun setDownloadDirectory(directory: String)"));
        assertTrue(downloadsViewModel.contains("sealed interface DownloadsEffect"));
        assertTrue(downloadsViewModel.contains("data object OpenDirectoryPicker"));
        assertTrue(downloadsViewModel.contains("val effects: SharedFlow<DownloadsEffect>"));
        assertTrue(downloadsViewModel.contains("fun useMusicDirectory(downloadManager: TrackDownloadDirectoryController?)"));
        assertTrue(downloadsViewModel.contains("fun useDownloadsDirectory(downloadManager: TrackDownloadDirectoryController?)"));
        assertTrue(downloadsViewModel.contains("fun chooseDirectory(downloadManager: TrackDownloadDirectoryController?)"));
        assertTrue(downloadsViewModel.contains("downloadManager.setDownloadDirectory(directory)"));
        assertTrue(downloadsViewModel.contains("mutableEffects.tryEmit(DownloadsEffect.OpenDirectoryPicker)"));
        assertTrue(downloadsViewModel.contains("fun openDirectoryRequests(): Flow<Unit>"));
        assertTrue(downloadsViewModel.contains("DownloadsEffect.OpenDirectoryPicker -> emit(Unit)"));
        assertFalse(exists("app/src/main/java/app/yukine/downloads/DownloadsDestination.kt"));
        assertTrue(downloadsContracts.contains("data class DownloadsDestinationActions"));
        assertTrue(downloadsContracts.contains("val openDirectoryPicker: () -> Unit"));
        assertTrue(downloadsDestination.contains("fun DownloadsDestination("));
        assertTrue(downloadsDestination.contains("state: StateFlow<DownloadsUiState>"));
        assertTrue(downloadsDestination.contains("openDirectoryRequests: Flow<Unit>"));
        assertTrue(downloadsDestination.contains("actions: DownloadsDestinationActions"));
        assertFalse(downloadsDestination.contains("DownloadsViewModel"));
        assertFalse(downloadsDestination.contains("TrackDownloadManager"));
        assertFalse(downloadsDestination.contains("DownloadsEffect"));
        assertFalse(navGraph.contains("DownloadsDestinationActions("));
        assertFalse(navGraph.contains("downloadsViewModel.openDirectoryRequests()"));
        assertFalse(navGraph.contains("DownloadsEffect.OpenDirectoryPicker -> emit(Unit)"));
        assertTrue(navGraph.contains("state = hostState.downloadsState"));
        assertTrue(navGraph.contains("openDirectoryRequests = hostState.downloadsOpenDirectoryRequests"));
        assertTrue(navGraph.contains("actions = hostState.downloadsActions"));
        assertTrue(downloadsDestination.contains("directoryLabel = uiState.directoryLabel"));
        assertTrue(downloadsDestination.contains("onUseMusicDirectory = { latestActions.useMusicDirectory() }"));
        assertTrue(downloadsDestination.contains("onChooseDirectory = { latestActions.chooseDirectory() }"));
        assertFalse(navGraph.contains("useMusicDirectory = { downloadsViewModel.useMusicDirectory(downloadManager) }"));
        assertFalse(navGraph.contains("chooseDirectory = { downloadsViewModel.chooseDirectory(downloadManager) }"));
        assertTrue(mainActivity.contains("downloadsViewModel.openDirectoryRequests()"));
        assertTrue(mainActivity.contains("private app.yukine.DownloadsDestinationActions downloadsDestinationActions()"));
        assertTrue(mainActivity.contains("return new app.yukine.DownloadsDestinationActions("));
        assertFalse(downloadsDestination.contains("downloadManager?.setDownloadDirectory"));
        assertFalse(downloadsDestination.contains("mutableStateOf(downloadManager?.downloadDirectoryLabel()"));
        assertFalse(downloadsDestination.contains("directoryLabel = downloadManager?.downloadDirectoryLabel()"));
        assertTrue(mainActivity.contains("DownloadRequestController downloadRequestController"));
        assertTrue(mainActivity.contains("downloadRequestController = new DownloadRequestController("));
        assertFalse(mainActivity.contains("DownloadDirectoryPickerController downloadDirectoryPickerController"));
        assertFalse(mainActivity.contains("downloadDirectoryPickerController = new DownloadDirectoryPickerController("));
        assertTrue(mainActivity.contains("documentPickerController.openDownloadFolderPicker();"));
        assertTrue(mainActivity.contains("if (documentPickerController == null)"));
        assertTrue(appLanguage.contains("put(\"download.directory.picker.unavailable\""));
        assertTrue(mainActivity.contains("AppLanguage.text("));
        assertTrue(mainActivity.contains("\"download.directory.picker.unavailable\""));
        assertFalse(mainActivity.contains("\"目录选择暂不可用\""));
        assertTrue(mainActivity.contains("documentPickerController.openDownloadFolderPicker();"));
        assertFalse(mainActivity.contains("navHostState.setOpenDownloadDirectoryPickerAction("));
        assertFalse(mainActivity.contains("private void openDownloadFolderPicker()"));
        assertTrue(mainActivity.contains("new DownloadQualityDialogController("));
        assertTrue(mainActivity.contains("tracks -> downloadRequestController.downloadTracks(tracks)"));
        assertTrue(mainActivity.contains("effect instanceof NowPlayingEffect.DownloadTrack downloadTrack"));
        assertTrue(mainActivity.contains("downloadRequestController.downloadTrack(downloadTrack.getTrack())"));
        assertFalse(mainActivity.contains("private void downloadTrack(final Track track)"));
        assertFalse(mainActivity.contains("private void downloadTrackWithQuality"));
        assertFalse(mainActivity.contains("private void downloadTracks(final List<Track> tracks)"));
        assertFalse(mainActivity.contains("private void enqueueTrackDownload"));
        assertFalse(mainActivity.contains("private void showDownloadQualityDialog"));
        assertFalse(mainActivity.contains("private String downloadQualityLabel"));
        assertFalse(mainActivity.contains("private interface DownloadQualityCallback"));
        assertFalse(mainActivity.contains("StreamingQualityPlatformMapping.optionLabel(StreamingAudioQuality.STANDARD"));
        assertFalse(mainActivity.contains("StreamingQualityPlatformMapping.downloadDialogMessage(settingsStore.languageMode())"));
    }

    @Test
    public void streamingUiStateBelongsToStreamingViewModel() throws Exception {
        String streamingViewModel = read("app/src/main/java/app/yukine/StreamingViewModel.kt");
        String mainActivityViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String hostState = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String networkDestination = read("feature/navigation/src/main/java/app/yukine/network/NetworkDestination.kt");
        String streamingContracts = read("feature/ui-common/src/main/java/app/yukine/StreamingSearchContracts.kt");
        String streamingScreen = read("feature/ui-common/src/main/java/app/yukine/ui/StreamingSearchScreen.kt");

        assertFalse(exists("app/src/main/java/app/yukine/StreamingViewModel.java"));
        assertTrue(streamingViewModel.contains("@HiltViewModel"));
        assertTrue(streamingViewModel.contains("class StreamingViewModel @Inject constructor"));
        assertTrue(streamingContracts.contains("data class StreamingSearchState"));
        assertTrue(streamingContracts.contains("data class StreamingSearchAuthLaunch"));
        assertFalse(streamingContracts.contains("data class MainActivityStreamingState"));
        assertFalse(streamingContracts.contains("data class MainActivityStreamingAuthLaunch"));
        assertFalse(streamingViewModel.contains("data class MainActivityStreamingState"));
        assertTrue(streamingViewModel.contains("private val streamingState = MutableStateFlow(StreamingSearchState())"));
        assertTrue(streamingViewModel.contains("val streaming: StateFlow<StreamingSearchState> = streamingState.asStateFlow()"));
        assertTrue(streamingViewModel.contains("fun updateStreamingProviders("));
        assertTrue(streamingViewModel.contains("fun appendStreamingSearchResult("));
        assertFalse(mainActivityViewModel.contains("streamingViewModel.updateStreamingProviders(providers, capabilities, health)"));
        assertFalse(mainActivityViewModel.contains("streamingViewModel.appendStreamingSearchResult(result)"));
        assertFalse(mainActivityViewModel.contains("streamingViewModel.updateStreamingAuthLaunch(provider, authState, launchUrl)"));
        assertFalse(mainActivityViewModel.contains("private val streamingState = MutableStateFlow(MainActivityStreamingState())"));
        assertFalse(mainActivityViewModel.contains("private var streamingViewModel"));
        assertFalse(mainActivityViewModel.contains("val streaming: StateFlow<MainActivityStreamingState>"));
        assertFalse(mainActivityViewModel.contains("val preferredProvider = providers.firstOrNull"));
        assertFalse(mainActivityViewModel.contains("items = previous.unifiedItems + result.unifiedItems"));
        assertFalse(mainActivityViewModel.contains("MainActivityStreamingAuthLaunch(provider, it, authState.kind)"));
        assertTrue(mainActivity.contains("private StreamingViewModel streamingViewModel;"));
        assertTrue(mainActivity.contains("streamingViewModel = viewModels.getStreamingViewModel();"));
        assertFalse(mainActivity.contains("viewModel.bindStreamingViewModel(streamingViewModel);"));
        assertTrue(hostState.contains("val streamingState: StateFlow<StreamingSearchState>"));
        assertFalse(hostState.contains("import app.yukine.StreamingViewModel"));
        assertFalse(hostState.contains("val streamingViewModel: StreamingViewModel"));
        assertFalse(exists("app/src/main/java/app/yukine/network/NetworkDestination.kt"));
        assertFalse(networkDestination.contains("EchoNavHostState"));
        assertFalse(networkDestination.contains("StreamingSearchScreen"));
        assertTrue(streamingScreen.contains("fun StreamingSearchScreen("));
        assertTrue(streamingScreen.contains("state: StreamingSearchState"));
        assertTrue(networkDestination.contains("streamingContent: @Composable () -> Unit"));
        assertTrue(networkDestination.contains("MainRoutes.NETWORK_STREAMING_HUB -> streamingContent()"));
        assertTrue(navGraph.contains("hostState.streamingState.collectAsState()"));
        assertFalse(navGraph.contains("hostState.streamingViewModel"));
        assertTrue(navGraph.contains("StreamingSearchScreen("));
        assertFalse(networkDestination.contains("hostState.mainViewModel.streaming.collectAsState()"));
    }

    @Test
    public void mainLibraryStoreIsKotlinStateHolder() throws Exception {
        String mainViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String libraryStore = read("app/src/main/java/app/yukine/MainLibraryStore.kt");
        String librarySearchUseCase = read("app/src/main/java/app/yukine/LibrarySearchUseCase.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");

        assertFalse(exists("app/src/main/java/app/yukine/MainLibraryStore.java"));
        assertTrue(mainViewModel.contains("data class LibraryStoreState"));
        assertTrue(mainViewModel.contains("private val libraryState = MutableStateFlow(LibraryStoreState())"));
        assertTrue(mainViewModel.contains("val library: StateFlow<LibraryStoreState> = libraryState.asStateFlow()"));
        assertFalse(mainViewModel.contains("MainActivityLibraryState"));
        assertTrue(libraryStore.contains("internal fun interface MainLibraryStoreFactory"));
        assertTrue(libraryStore.contains("fun create(viewModel: MainActivityViewModel): MainLibraryStore"));
        assertTrue(libraryStore.contains("internal class MainLibraryStore"));
        assertTrue(libraryStore.contains("private val searchUseCase: LibrarySearchUseCase"));
        assertTrue(libraryStore.contains("private val combinedSearchUseCase = LibraryCombinedSearchUseCase(searchUseCase)"));
        assertFalse(libraryStore.contains("MusicLibraryRepository"));
        assertTrue(libraryStore.contains("private val viewModel: MainActivityViewModel"));
        assertTrue(libraryStore.contains("return ArrayList(state().allTracks)"));
        assertTrue(libraryStore.contains("searchUseCase.execute(cachedTracks, searchQuery)"));
        assertTrue(libraryStore.contains("combinedSearchUseCase.execute(allTracks(), selectedPlaylistTracks(), query)"));
        assertTrue(libraryStore.contains("fun filteredSelectedPlaylistTracks(searchQuery: String?)"));
        assertTrue(librarySearchUseCase.contains("internal class LibrarySearchUseCase"));
        assertTrue(librarySearchUseCase.contains("operations.search(source, query)"));
        assertTrue(librarySearchUseCase.contains("internal class LibraryCombinedSearchUseCase"));
        assertTrue(librarySearchUseCase.contains("searchUseCase.execute(selectedPlaylistTracks, query)"));
        assertTrue(libraryModule.contains("fun provideLibrarySearchUseCase(repository: MusicLibraryRepository): LibrarySearchUseCase"));
        assertTrue(libraryModule.contains("LibrarySearchUseCase(MusicLibrarySearchOperations(repository))"));
        assertTrue(libraryModule.contains("fun provideMainLibraryStoreFactory(searchUseCase: LibrarySearchUseCase): MainLibraryStoreFactory"));
        assertTrue(libraryModule.contains("MainLibraryStoreFactory { viewModel -> MainLibraryStore(searchUseCase, viewModel) }"));
        assertTrue(mainActivity.contains("@Inject MainLibraryStoreFactory libraryStoreFactory;"));
        assertTrue(mainActivity.contains("libraryStore = libraryStoreFactory.create(viewModel);"));
        assertFalse(mainActivity.contains("new MainLibraryStore("));
        assertFalse(mainActivity.contains("new LibrarySearchUseCase(new MusicLibrarySearchOperations(repository))"));
        assertTrue(libraryStore.contains("viewModel.applyCollections("));
        assertTrue(libraryStore.contains("NetworkLibrary.streamTracks(allTracks())"));
        assertTrue(libraryStore.contains("return viewModel.library.value"));
        assertTrue(libraryStore.contains("private fun state(): LibraryStoreState"));
        assertFalse(libraryStore.contains("MainActivityLibraryState"));
    }

    @Test
    public void mainRouteControllerIsKotlinStateHolder() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String mainViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String routeState = read("feature/navigation/src/main/java/app/yukine/NavigationRouteState.kt");
        String navigationViewModel = read("app/src/main/java/app/yukine/NavigationViewModel.kt");
        String routeController = read("app/src/main/java/app/yukine/MainRouteController.kt");
        String backPolicy = read("app/src/main/java/app/yukine/MainBackNavigationPolicy.kt");

        assertFalse(exists("app/src/main/java/app/yukine/NavigationViewModel.java"));
        assertFalse(exists("app/src/main/java/app/yukine/MainRouteController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/MainBackNavigationPolicy.java"));
        assertTrue(routeState.contains("data class NavigationRouteState"));
        assertTrue(routeState.contains("val selectedTab: String = MainRoutes.TAB_HOME"));
        assertTrue(routeState.contains("val libraryMode: String = DefaultLibraryMode"));
        assertFalse(routeState.contains("LibraryGrouping"));
        assertTrue(navigationViewModel.contains("class NavigationViewModel"));
        assertTrue(navigationViewModel.contains("val state: StateFlow<NavigationRouteState>"));
        assertTrue(navigationViewModel.contains("NavigationRouteStateStore.restore(savedStateHandle)"));
        assertTrue(navigationViewModel.contains("NavigationRouteStateStore.save(savedStateHandle, snapshot)"));
        assertTrue(routeController.contains("internal class MainRouteController"));
        assertTrue(routeController.contains("private val viewModel: NavigationViewModel"));
        assertTrue(routeController.contains("private var state: NavigationRouteState"));
        assertTrue(routeController.contains("fun restoreFromViewModel()"));
        assertTrue(routeController.contains("state = viewModel.state.value"));
        assertTrue(routeController.contains("fun navigateToTab(tabKey: String, userInitiated: Boolean): Boolean"));
        assertTrue(routeController.contains("MainBackNavigationPolicy.resolve("));
        assertTrue(routeController.contains("viewModel.updateRoute(state)"));
        assertTrue(mainActivity.contains("navigationViewModel = viewModels.getNavigationViewModel();"));
        assertTrue(mainActivity.contains("routeController = new MainRouteController(navigationViewModel)"));
        assertTrue(mainActivity.contains("navigationViewModel.getState()"));
        assertFalse(mainViewModel.contains("routeState"));
        assertFalse(mainViewModel.contains("data class NavigationRouteState"));
        assertFalse(mainViewModel.contains("val state: StateFlow<MainActivityRouteState>"));
        assertFalse(mainViewModel.contains("data class MainActivityRouteState"));
        assertFalse(mainViewModel.contains("fun updateRoute("));
        assertTrue(backPolicy.contains("internal object MainBackNavigationPolicy"));
        assertTrue(backPolicy.contains("@JvmField val handled: Boolean"));
        assertTrue(backPolicy.contains("fun resolve("));
        assertTrue(backPolicy.contains("Result.navigate(MainRoutes.TAB_HOME"));
    }

    @Test
    public void mainTabRenderDispatcherIsKotlinRouteBoundary() throws Exception {
        String dispatcher = read("app/src/main/java/app/yukine/MainTabRenderDispatcher.kt");

        assertFalse(exists("app/src/main/java/app/yukine/MainTabRenderDispatcher.java"));
        assertFalse(exists("app/src/main/java/app/yukine/MainTabRendererBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/ui/TabBarController.kt"));
        assertTrue(dispatcher.contains("internal class MainTabRenderDispatcher"));
        assertTrue(dispatcher.contains("fun render(selectedTab: String)"));
        assertTrue(dispatcher.contains("MainRoutes.TAB_HOME -> renderHomeAction.run()"));
        assertTrue(dispatcher.contains("MainRoutes.TAB_LIBRARY -> renderLibraryAction.run()"));
        assertTrue(dispatcher.contains("MainRoutes.TAB_NETWORK -> renderNetworkAction.run()"));
        assertTrue(dispatcher.contains("else -> renderSettingsAction.run()"));
    }

    @Test
    public void legacyContentHostControllersAreRemovedFromTheShell() throws Exception {
        String shellController = read("app/src/main/java/app/yukine/MainUiShellController.java");

        assertFalse(exists("app/src/main/java/app/yukine/ContentHostController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/ContentHostController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/ScrollDirectionFrameLayout.java"));
        assertFalse(exists("app/src/main/java/app/yukine/ui/ContentRouteHostController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/ui/SearchBarController.kt"));
        assertFalse(shellController.contains("new ContentHostController("));
        assertFalse(shellController.contains("new TabBarController("));
        assertFalse(shellController.contains("contentHostController."));
        assertFalse(shellController.contains("contentHost.setOnHorizontalSwipeListener"));
        assertFalse(shellController.contains("contentRouteHostController"));
        assertFalse(shellController.contains("addVirtualContent"));
        assertFalse(shellController.contains("animateContentIfPending"));
    }

    @Test
    public void trackListRenderControllerIsKotlinRenderBoundary() throws Exception {
        String controller = read("app/src/main/java/app/yukine/TrackListRenderController.kt");
        String screen = read("app/src/main/java/app/yukine/ui/TrackListScreen.kt");
        String queueScreen = read("app/src/main/java/app/yukine/ui/QueueScreen.kt");
        String playlistScreen = read("app/src/main/java/app/yukine/ui/PlaylistTrackScreen.kt");
        String collectionsScreen = read("app/src/main/java/app/yukine/ui/CollectionsScreen.kt");
        String currentIndicator = read("app/src/main/java/app/yukine/ui/TrackCurrentIndicator.kt");
        String rowStateFactory = read("app/src/main/java/app/yukine/TrackRowStateFactory.kt");
        String rowKeyPolicy = read("app/src/main/java/app/yukine/TrackRowKeyPolicy.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String mainActivityViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String searchViewModel = read("app/src/main/java/app/yukine/SearchViewModel.kt");
        String unifiedSearchContracts = read("feature/ui-common/src/main/java/app/yukine/UnifiedSearchContracts.kt");
        String unifiedSearchScreen = read("feature/ui-common/src/main/java/app/yukine/ui/SearchScreen.kt");
        String searchDestination = read("feature/navigation/src/main/java/app/yukine/search/SearchDestination.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String navHostState = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt");
        String echoApp = read("app/src/main/java/app/yukine/EchoApp.kt");
        String navHostBridge = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostBridge.kt");
        String contracts = read("feature/navigation/src/main/java/app/yukine/LibraryDestinationContracts.kt");
        String destination = read("feature/navigation/src/main/java/app/yukine/library/LibraryTrackListDestination.kt");
        String collectionsRenderController = read("app/src/main/java/app/yukine/CollectionsRenderController.kt");
        String mainTrackListRenderListener = read("app/src/main/java/app/yukine/MainTrackListRenderListener.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");

        assertFalse(exists("app/src/main/java/app/yukine/TrackListRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/TrackListRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/TrackListRenderBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/library/LibraryTrackListDestination.kt"));
        assertTrue(controller.contains("internal class TrackListRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("private val viewModel: LibraryViewModel"));
        assertTrue(controller.contains("TrackRowStateFactory.trackRow("));
        assertTrue(controller.contains("viewModel.updateTrackList(title, rows)"));
        assertTrue(controller.contains("listener.publishTrackListChrome("));
        assertFalse(controller.contains("publishTrackList(title, rows)"));
        assertTrue(controller.contains("internal data class TrackListChromeState("));
        assertFalse(controller.contains("TrackListUiStatePublisher"));
        assertFalse(mainActivity.contains("new TrackListRenderController(libraryViewModel, new TrackListRenderController.Listener()"));
        assertFalse(mainActivity.contains("new TrackListRenderBindings("));
        assertFalse(mainActivity.contains("new TrackListRenderController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainTrackListRenderListenerFactory trackListRenderListenerFactory;"));
        assertTrue(mainActivity.contains("new TrackListRenderController(libraryViewModel, trackListRenderListenerFactory.create("));
        assertTrue(mainActivity.contains("libraryViewModel.onEvent(new LibraryEvent.PlayTrackList(tracks, index));"));
        assertFalse(mainActivity.contains("public void publishTrackListChrome("));
        assertFalse(mainActivity.contains("publishTrackListChromeState(new TrackListChromeState("));
        assertTrue(mainTrackListRenderListener.contains("internal class MainTrackListRenderListener("));
        assertTrue(mainTrackListRenderListener.contains(": TrackListRenderController.Listener"));
        assertTrue(mainTrackListRenderListener.contains("TrackListChromeState("));
        assertTrue(mainTrackListRenderListener.contains("actions = ArrayList(actions)"));
        assertTrue(libraryModule.contains("fun provideMainTrackListRenderListenerFactory(): MainTrackListRenderListenerFactory"));
        assertTrue(mainActivity.contains("private void publishTrackListChromeState(TrackListChromeState state)"));
        assertTrue(mainActivity.contains("libraryViewModel.updateTrackListChrome("));
        assertFalse(mainActivity.contains("navHostState.setTrackListActions(new ArrayList<>(state.getActions()));"));
        assertFalse(mainActivity.contains("navHostState.setLibraryGroupModeActions(new ArrayList<>(state.getModeActions()));"));
        assertTrue(mainActivity.contains("actions -> homeDashboardViewModel.updateHomeDashboardActions(actions)"));
        assertTrue(collectionsRenderController.contains("viewModel.updateActions(actions)"));
        assertTrue(mainActivity.contains("searchViewModel.updateActions(searchActions);"));
        assertTrue(mainActivity.contains("streamingSearchActionHandler.loadNextPage();"));
        assertFalse(mainActivity.contains("private void loadMoreUnifiedStreamingResults()"));
        assertFalse(mainActivity.contains("navTrackListActions = state.getActions();"));
        assertFalse(mainActivity.contains("navLibraryGroupModeActions = Collections.emptyList();"));
        assertFalse(mainActivity.contains("navHomeActions = actions,"));
        assertFalse(mainActivity.contains("navCollectionsActions = actions"));
        assertFalse(mainActivity.contains("navStreamingSearchLabels = state.getLabels();"));
        assertFalse(mainActivity.contains("navStreamingSearchActions = state.getActions();"));
        assertFalse(mainActivity.contains("navSearchActions = new app.yukine.ui.UnifiedSearchActions("));
        assertFalse(exists("app/src/main/java/app/yukine/search/SearchDestination.kt"));
        assertTrue(unifiedSearchContracts.contains("data class UnifiedSearchUiState"));
        assertFalse(searchViewModel.contains("data class UnifiedSearchUiState"));
        assertTrue(unifiedSearchScreen.contains("fun UnifiedSearchScreen("));
        assertTrue(unifiedSearchScreen.contains("searchState: UnifiedSearchUiState"));
        assertTrue(unifiedSearchScreen.contains("streamingState: UnifiedSearchStreamingState"));
        assertFalse(unifiedSearchScreen.contains("SearchViewModel"));
        assertFalse(unifiedSearchScreen.contains("MainActivityStreamingState"));
        assertTrue(searchDestination.contains("fun SearchDestination("));
        assertTrue(searchDestination.contains("searchState: StateFlow<UnifiedSearchUiState>"));
        assertFalse(searchDestination.contains("SearchViewModel"));
        assertFalse(searchDestination.contains("StreamingViewModel"));
        assertTrue(navGraph.contains("streamingState.toUnifiedSearchStreamingState()"));
        assertTrue(navHostState.contains("val searchState: StateFlow<UnifiedSearchUiState>"));
        assertTrue(mainActivity.contains("searchViewModel.getUiState()"));
        assertTrue(navGraph.contains("searchState = hostState.searchState"));
        assertFalse(navGraph.contains("searchViewModel.uiState"));
        assertFalse(navGraph.contains("SearchViewModel"));
        assertFalse(echoApp.contains("fun searchViewModel(): SearchViewModel"));
        assertFalse(echoApp.contains("searchViewModel = mount.searchViewModel()"));
        assertFalse(navHostBridge.contains("searchViewModel: SearchViewModel"));
        assertFalse(navHostBridge.contains("searchViewModel = searchViewModel"));
        assertFalse(mainActivity.contains("publishTrackListUiState"));
        assertFalse(controller.contains("TrackListScreenFactory.create("));
        assertFalse(destination.contains("@Suppress(\"UNUSED_PARAMETER\")"));
        assertTrue(destination.contains("single owner of track-list content plus chrome"));
        assertFalse(destination.contains("injected by the host"));
        assertTrue(contracts.contains("data class LibraryTrackListDestinationState"));
        assertFalse(contracts.contains("data class MainActivityTrackListUiState"));
        assertFalse(mainActivityViewModel.contains("data class MainActivityTrackListUiState"));
        assertTrue(screen.contains("TrackCurrentIndicator(track.current)"));
        assertTrue(screen.contains("TrackMoreMenu(track, actions, labels)"));
        assertTrue(screen.contains("DropdownMenu("));
        assertTrue(screen.contains("EchoIconKind.More"));
        assertTrue(queueScreen.contains("TrackCurrentIndicator(track.current"));
        assertTrue(playlistScreen.contains("TrackCurrentIndicator(track.current"));
        assertTrue(collectionsScreen.contains("TrackCurrentIndicator(track.current"));
        assertTrue(currentIndicator.contains("fun TrackCurrentIndicator(active: Boolean"));
        assertFalse(screen.contains("private fun CurrentTrackIndicator"));
        assertFalse(exists("app/src/main/java/app/yukine/TrackRowStateFactory.java"));
        assertFalse(exists("app/src/main/java/app/yukine/TrackRowKeyPolicy.java"));
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
    public void homeDashboardRenderControllerUsesBindingsBoundary() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String controller = read("app/src/main/java/app/yukine/HomeDashboardRenderController.kt");
        String mainHomeDashboardRenderListener = read("app/src/main/java/app/yukine/MainHomeDashboardRenderListener.kt");
        String homeViewModel = read("app/src/main/java/app/yukine/HomeDashboardViewModel.kt");
        String homeContracts = read("feature/navigation/src/main/java/app/yukine/HomeDashboardContracts.kt");
        String homeDestination = read("feature/navigation/src/main/java/app/yukine/home/HomeDestination.kt");
        String queueViewModel = read("app/src/main/java/app/yukine/queue/QueueViewModel.kt");
        String queueContracts = read("feature/navigation/src/main/java/app/yukine/QueueDestinationContracts.kt");
        String queueDestination = read("feature/navigation/src/main/java/app/yukine/queue/QueueDestination.kt");
        String mainViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String navHostState = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostState.kt");
        String echoApp = read("app/src/main/java/app/yukine/EchoApp.kt");
        String navHostBridge = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavHostBridge.kt");

        assertFalse(exists("app/src/main/java/app/yukine/HomeDashboardRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/HomeDashboardRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/HomeDashboardRenderBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/home/HomeDestination.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/queue/QueueDestination.kt"));
        assertTrue(homeViewModel.contains("class HomeDashboardViewModel"));
        assertTrue(homeViewModel.contains("val uiState: StateFlow<HomeDashboardDestinationState>"));
        assertFalse(homeViewModel.contains("data class MainActivityHomeDashboardUiState"));
        assertTrue(homeContracts.contains("data class HomeDashboardDestinationState"));
        assertFalse(homeContracts.contains("data class MainActivityHomeDashboardUiState"));
        assertTrue(homeContracts.contains("val actions: HomeDashboardActions = emptyHomeDashboardActions()"));
        assertTrue(homeDestination.contains("fun HomeDestination("));
        assertTrue(homeDestination.contains("HomeDashboardScreen(uiState.content, uiState.actions, activeDownload, playbackQuality, audioMotion)"));
        assertTrue(queueContracts.contains("data class QueueDestinationState"));
        assertTrue(queueContracts.contains("interface QueueDestinationStateProvider"));
        assertTrue(queueContracts.contains("val uiState: StateFlow<QueueDestinationState>"));
        assertTrue(queueContracts.contains("val labels: StateFlow<QueueScreenLabels>"));
        assertTrue(queueViewModel.contains("class QueueViewModel : ViewModel(), QueueDestinationStateProvider"));
        assertTrue(queueViewModel.contains("override val uiState: StateFlow<QueueDestinationState>"));
        assertTrue(queueViewModel.contains("_uiState.value = QueueDestinationState(rows)"));
        assertFalse(queueViewModel.contains("MainActivityQueueUiState"));
        assertFalse(mainViewModel.contains("data class MainActivityQueueUiState"));
        assertTrue(queueDestination.contains("fun QueueDestination(provider: QueueDestinationStateProvider"));
        assertFalse(queueDestination.contains("QueueViewModel"));
        assertTrue(navGraph.contains("QueueDestination("));
        assertTrue(navGraph.contains("hostState.queueStateProvider"));
        assertFalse(navGraph.contains("queueViewModel"));
        assertTrue(navHostState.contains("val queueStateProvider: QueueDestinationStateProvider"));
        assertTrue(mainActivity.contains("queueViewModel,"));
        assertFalse(echoApp.contains("fun queueViewModel(): QueueViewModel"));
        assertFalse(echoApp.contains("queueViewModel = queueViewModel"));
        assertFalse(navHostBridge.contains("queueViewModel: QueueViewModel"));
        assertFalse(navHostBridge.contains("queueViewModel = queueViewModel"));
        assertTrue(homeViewModel.contains("fun updateHomeDashboardActions(actions: HomeDashboardActions)"));
        assertTrue(homeViewModel.contains("fun updatePlayback(snapshot: PlaybackStateSnapshot?)"));
        assertTrue(homeViewModel.contains("fun fetchHomeDashboard("));
        assertFalse(mainViewModel.contains("homeDashboardState"));
        assertFalse(mainViewModel.contains("dashboardLoading"));
        assertFalse(mainViewModel.contains("fun updateHomeDashboard("));
        assertFalse(mainViewModel.contains("fun updateHomeDashboardPlayback("));
        assertFalse(mainViewModel.contains("fun fetchHomeDashboard("));
        assertFalse(mainViewModel.contains("dashboardRepository"));
        assertFalse(mainViewModel.contains("togglePlaybackRemote"));
        assertFalse(mainViewModel.contains("seekRemote"));
        assertFalse(mainViewModel.contains("nextRemote"));
        assertFalse(mainViewModel.contains("previousRemote"));
        assertTrue(controller.contains("internal class HomeDashboardRenderController"));
        assertTrue(controller.contains("private val viewModel: HomeDashboardViewModel"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("viewModel.fetchHomeDashboard("));
        assertTrue(controller.contains("listener.publishHomeDashboardActions(actions)"));
        assertTrue(mainActivity.contains("homeDashboardViewModel = viewModels.getHomeDashboardViewModel();"));
        assertFalse(mainActivity.contains("new HomeDashboardRenderController(homeDashboardViewModel, new HomeDashboardRenderController.Listener()"));
        assertFalse(mainActivity.contains("new HomeDashboardRenderController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainHomeDashboardRenderListenerFactory homeDashboardRenderListenerFactory;"));
        assertTrue(mainActivity.contains("new HomeDashboardRenderController(homeDashboardViewModel, homeDashboardRenderListenerFactory.create("));
        assertTrue(mainActivity.contains("actions -> homeDashboardViewModel.updateHomeDashboardActions(actions)"));
        assertTrue(mainHomeDashboardRenderListener.contains("override fun publishHomeDashboardActions(actions: HomeDashboardActions)"));
        assertTrue(mainActivity.contains("runRecommendationAction(new RecommendationAction.PlayDaily(StreamingProviderName.NETEASE))"));
        assertTrue(mainActivity.contains("runRecommendationAction(new RecommendationAction.PlayHeartbeat(StreamingProviderName.NETEASE))"));
        assertFalse(mainActivity.contains("public void playTrack(Track track)"));
        assertFalse(mainActivity.contains("Collections.shuffle(shuffled);"));
        assertTrue(mainHomeDashboardRenderListener.contains("override fun playTrack(track: Track)"));
        assertTrue(mainHomeDashboardRenderListener.contains("trackListPlayer.playTrackList(listOf(track), 0)"));
        assertTrue(mainHomeDashboardRenderListener.contains("override fun shuffleAll()"));
        assertTrue(mainHomeDashboardRenderListener.contains("Collections::shuffle"));
        assertFalse(mainActivity.contains("navHostState.setHomeActions(actions);"));
        assertFalse(navHostState.contains("var homeActions"));
        assertTrue(navHostState.contains("val homeDashboardState: StateFlow<HomeDashboardDestinationState>"));
        assertFalse(navHostState.contains("import app.yukine.HomeDashboardViewModel"));
        assertFalse(navHostState.contains("val homeDashboardViewModel: HomeDashboardViewModel"));
        assertTrue(navGraph.contains("HomeDestination(hostState.homeDashboardState, activeDownload, playbackQuality, audioMotion)"));
        assertFalse(navGraph.contains("hostState.homeDashboardViewModel"));
        assertFalse(navGraph.contains("hostState.homeActions"));
        assertFalse(navGraph.contains("hostState.mainViewModel.homeDashboard"));
        assertFalse(mainActivity.contains("private void openLibraryModeFromHome(String mode)"));
        assertTrue(mainActivity.contains("private void continueDashboardPlayback(Track track)"));
        assertFalse(mainActivity.contains("new HomeDashboardRenderBindings("));
    }

    @Test
    public void libraryGroupsRenderControllerIsKotlinStateFlowBoundary() throws Exception {
        String controller = read("app/src/main/java/app/yukine/LibraryGroupsRenderController.kt");
        String collectionRowStateFactory = read("app/src/main/java/app/yukine/CollectionRowStateFactory.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String mainActivityViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String contracts = read("feature/navigation/src/main/java/app/yukine/LibraryDestinationContracts.kt");
        String destination = read("feature/navigation/src/main/java/app/yukine/library/LibraryGroupsDestination.kt");
        String mainLibraryGroupsRenderListener = read("app/src/main/java/app/yukine/MainLibraryGroupsRenderListener.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");

        assertFalse(exists("app/src/main/java/app/yukine/LibraryGroupsRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryGroupsRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryGroupsRenderBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/library/LibraryGroupsDestination.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/CollectionRowStateFactory.java"));
        assertTrue(controller.contains("internal class LibraryGroupsRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("private val viewModel: LibraryViewModel"));
        assertTrue(controller.contains("LibraryGrouping.groupTracks("));
        assertTrue(controller.contains("viewModel.updateLibraryGroups(title, groupRows)"));
        assertTrue(controller.contains("listener.publishLibraryGroupsChrome("));
        assertFalse(controller.contains("publishLibraryGroups(title, rows)"));
        assertTrue(controller.contains("internal data class LibraryGroupsChromeState("));
        assertTrue(controller.contains("internal data class LibraryGroupTrackListRequest("));
        assertFalse(controller.contains("LibraryGroupsUiStatePublisher"));
        assertFalse(mainActivity.contains("new LibraryGroupsRenderController(libraryViewModel, new LibraryGroupsRenderController.Listener()"));
        assertTrue(mainActivity.contains("@Inject ArtistInfoRepository artistInfoRepository;"));
        assertTrue(mainActivity.contains("@Inject MainLibraryGroupsRenderListenerFactory libraryGroupsRenderListenerFactory;"));
        assertTrue(mainActivity.contains("libraryGroupsRenderListenerFactory.create("));
        assertTrue(mainActivity.contains("artistInfoRepository,"));
        assertFalse(mainActivity.contains("new ArtistInfoRepository()"));
        assertTrue(libraryModule.contains("fun provideArtistInfoRepository(): ArtistInfoRepository = ArtistInfoRepository()"));
        assertTrue(libraryModule.contains("fun provideMainLibraryGroupsRenderListenerFactory(): MainLibraryGroupsRenderListenerFactory"));
        assertTrue(mainLibraryGroupsRenderListener.contains("internal class MainLibraryGroupsRenderListener("));
        assertTrue(mainLibraryGroupsRenderListener.contains(": LibraryGroupsRenderController.Listener"));
        assertTrue(mainLibraryGroupsRenderListener.contains("AppLanguage.text(languageModeProvider.languageMode(), \"favorite.playlist\")"));
        assertTrue(mainLibraryGroupsRenderListener.contains("chromePublisher.publishLibraryGroupsChrome("));
        assertTrue(mainLibraryGroupsRenderListener.contains("trackListRenderer.renderLibraryGroupTrackList("));
        assertFalse(mainActivity.contains("new LibraryGroupsRenderBindings("));
        assertFalse(mainActivity.contains("public void selectLibraryGroup(String key, String title)"));
        assertTrue(mainActivity.contains("(key, title) -> libraryViewModel.onEvent(new LibraryEvent.OpenGroup(key, title))"));
        assertFalse(mainActivity.contains("publishLibraryGroupsChromeState(new LibraryGroupsChromeState("));
        assertTrue(mainActivity.contains("private void publishLibraryGroupsChromeState(LibraryGroupsChromeState state)"));
        assertTrue(mainActivity.contains("libraryViewModel.updateLibraryGroupsChrome("));
        assertFalse(mainActivity.contains("publishLibraryGroupsUiState"));
        assertTrue(mainActivity.contains("private void renderLibraryGroupTrackList(LibraryGroupTrackListRequest request)"));
        assertFalse(controller.contains("LibraryGroupsScreenFactory.create("));
        assertFalse(destination.contains("@Suppress(\"UNUSED_PARAMETER\")"));
        assertTrue(destination.contains("LibraryViewModel state"));
        assertFalse(destination.contains("injected by the host"));
        assertTrue(contracts.contains("data class LibraryGroupsDestinationState"));
        assertFalse(contracts.contains("data class MainActivityLibraryGroupsUiState"));
        assertFalse(mainActivityViewModel.contains("data class MainActivityLibraryGroupsUiState"));
        assertTrue(controller.contains("private fun renderGroupDetail("));
        assertTrue(collectionRowStateFactory.contains("internal object CollectionRowStateFactory"));
        assertTrue(collectionRowStateFactory.contains("@JvmStatic"));
        assertTrue(collectionRowStateFactory.contains("fun playlistRow("));
        assertTrue(collectionRowStateFactory.contains("fun networkSourceRow("));
        assertTrue(collectionRowStateFactory.contains("NetworkLibrary.remoteSourceSubtitle("));
    }

    @Test
    public void collectionsRenderControllerIsKotlinUiStateBoundary() throws Exception {
        String controller = read("app/src/main/java/app/yukine/CollectionsRenderController.kt");
        String collectionsViewModel = read("app/src/main/java/app/yukine/CollectionsViewModel.kt");
        String documentPickerController = read("app/src/main/java/app/yukine/DocumentPickerController.kt");
        String confirmationDialogController = read("app/src/main/java/app/yukine/ConfirmationDialogController.kt");
        String playHistoryActionController = read("app/src/main/java/app/yukine/PlayHistoryActionController.kt");
        String playlistDialogController = read("app/src/main/java/app/yukine/PlaylistDialogController.java");
        String collectionsContracts = read("feature/navigation/src/main/java/app/yukine/CollectionsDestinationContracts.kt");
        String collectionsDestination = read("feature/navigation/src/main/java/app/yukine/collections/CollectionsDestination.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String mainCollectionsRenderListener = read("app/src/main/java/app/yukine/MainCollectionsRenderListener.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String mainDocumentPickerListener = read("app/src/main/java/app/yukine/MainDocumentPickerListener.kt");
        String platformModule = read("app/src/main/java/app/yukine/di/PlatformModule.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");

        assertFalse(exists("app/src/main/java/app/yukine/CollectionsRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/CollectionsRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/CollectionsRenderBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/collections/CollectionsDestination.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/DocumentPickerController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/DocumentPickerBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/DocumentPickerBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/ConfirmationDialogController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/ConfirmationDialogBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/ConfirmationDialogBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistDialogBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistDialogBindings.kt"));
        assertTrue(controller.contains("internal class CollectionsRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("private val viewModel: CollectionsViewModel"));
        assertTrue(controller.contains("CollectionsUiState("));
        assertTrue(controller.contains("CollectionsActions("));
        assertTrue(controller.contains("viewModel.updateCollections("));
        assertTrue(controller.contains("viewModel.updateScreen(state)"));
        assertTrue(controller.contains("viewModel.updateActions(actions)"));
        assertFalse(controller.contains("CollectionsActionsSink"));
        assertFalse(controller.contains("PlaylistIdAction"));
        assertFalse(controller.contains("SelectedPlaylistExportOpener"));
        assertFalse(controller.contains("SelectedPlaylistTrackMover"));
        assertFalse(controller.contains("SelectedPlaylistTrackRemover"));
        assertFalse(controller.contains("TrackListDownloadAction"));
        assertFalse(controller.contains("listener.publishCollectionsActions(actions)"));
        assertFalse(mainActivity.contains("new CollectionsRenderController(collectionsViewModel, new CollectionsRenderController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainCollectionsRenderListenerFactory collectionsRenderListenerFactory;"));
        assertTrue(mainActivity.contains("new CollectionsRenderController(collectionsViewModel, collectionsRenderListenerFactory.create("));
        assertTrue(libraryModule.contains("fun provideMainCollectionsRenderListenerFactory(): MainCollectionsRenderListenerFactory"));
        assertTrue(mainCollectionsRenderListener.contains("internal class MainCollectionsRenderListener("));
        assertTrue(mainCollectionsRenderListener.contains(": CollectionsRenderController.Listener"));
        assertTrue(mainCollectionsRenderListener.contains("statusKeySink.setStatusKey(\"no.tracks.in.playlist\")"));
        assertTrue(mainCollectionsRenderListener.contains("playlistExportDocumentOpener.openPlaylistExportDocument("));
        assertFalse(mainActivity.contains("new CollectionsRenderBindings("));
        assertFalse(mainActivity.contains("public void showCreatePlaylist()"));
        assertTrue(mainActivity.contains("() -> playlistDialogController.showCreatePlaylist()"));
        assertTrue(mainActivity.contains("() -> documentPickerController.openPlaylistM3uFilePicker()"));
        assertFalse(mainActivity.contains("public void publishCollectionsActions(CollectionsActions actions)"));
        assertFalse(mainActivity.contains("collectionsViewModel.updateActions(actions);"));
        assertFalse(mainActivity.contains("private void selectPlaylistFromCollections(long playlistId)"));
        assertFalse(mainActivity.contains("private void openSelectedPlaylistExportDocument()"));
        assertFalse(controller.contains("CollectionsScreenFactory.create("));
        assertTrue(collectionsViewModel.contains("data class PlaylistDetailUiState"));
        assertTrue(collectionsViewModel.contains("val selectedPlaylist: PlaylistDetailUiState? = null"));
        assertTrue(collectionsViewModel.contains("class CollectionsViewModel : ViewModel(), CollectionsDestinationStateProvider"));
        assertTrue(collectionsViewModel.contains("override val screen: StateFlow<CollectionsUiState>"));
        assertTrue(collectionsContracts.contains("interface CollectionsDestinationStateProvider"));
        assertTrue(collectionsContracts.contains("val screen: StateFlow<CollectionsUiState>"));
        assertTrue(collectionsDestination.contains("fun CollectionsDestination("));
        assertTrue(collectionsDestination.contains("provider: CollectionsDestinationStateProvider"));
        assertTrue(navGraph.contains("CollectionsDestination(hostState.collectionsStateProvider)"));
        assertFalse(collectionsDestination.contains("CollectionsViewModel"));
        assertTrue(controller.contains("TrackRowStateFactory.trackRow("));
        assertTrue(controller.contains("CollectionRowStateFactory.playlistRow("));
        assertTrue(controller.contains("TrackRowKeyPolicy.occurrenceKey("));
        assertTrue(controller.contains("private fun buildSelectedPlaylistRows("));
        assertTrue(controller.contains("private fun recordDetails("));
        assertTrue(documentPickerController.contains("internal class DocumentPickerController"));
        assertFalse(documentPickerController.contains(": DownloadDirectoryPickerOpener"));
        assertTrue(documentPickerController.contains("interface Listener"));
        assertTrue(documentPickerController.contains("internal fun interface DocumentActivityResultLauncher"));
        assertTrue(documentPickerController.contains("fun openAudioFilePicker()"));
        assertTrue(documentPickerController.contains("fun openM3uFilePicker()"));
        assertTrue(documentPickerController.contains("fun openDownloadFolderPicker()"));
        assertTrue(documentPickerController.contains("registerForActivityResult(ActivityResultContracts.StartActivityForResult())"));
        assertTrue(documentPickerController.contains("private fun handleResult(action: DocumentAction, result: ActivityResult)"));
        assertTrue(documentPickerController.contains("activityResultLauncher.launch(intent)"));
        assertTrue(documentPickerController.contains("private fun selectedAudioUris(data: Intent?): ArrayList<Uri>"));
        assertFalse(documentPickerController.contains("startActivityForResult"));
        assertFalse(documentPickerController.contains("fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean"));
        assertFalse(documentPickerController.contains("REQUEST_IMPORT_AUDIO_FILES"));
        assertFalse(mainActivity.contains("new DocumentPickerController(this, new DocumentPickerController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainDocumentPickerListenerFactory documentPickerListenerFactory;"));
        assertTrue(mainActivity.contains("new DocumentPickerController(this, documentPickerListenerFactory.create("));
        assertTrue(mainActivity.contains("this::importSelectedAudioUris"));
        assertTrue(mainActivity.contains("this::importSelectedAudioFolder"));
        assertTrue(mainActivity.contains("this::setCustomDownloadFolder"));
        assertTrue(mainActivity.contains("this::importSelectedM3uFile"));
        assertTrue(mainActivity.contains("(exportUri, playlistId, playlistName) -> libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName)"));
        assertTrue(mainActivity.contains("this::importSelectedPlaylistM3uFile"));
        assertTrue(mainActivity.contains("uris -> luoxueSourceImportController.importSelectedUris(uris)"));
        assertTrue(mainDocumentPickerListener.contains("internal class MainDocumentPickerListener("));
        assertTrue(mainDocumentPickerListener.contains(": DocumentPickerController.Listener"));
        assertTrue(mainDocumentPickerListener.contains("audioUrisImporter.importAudioUris(uris)"));
        assertTrue(mainDocumentPickerListener.contains("audioFolderImporter.importAudioFolder(treeUri)"));
        assertTrue(mainDocumentPickerListener.contains("downloadFolderChooser.chooseDownloadFolder(treeUri)"));
        assertTrue(mainDocumentPickerListener.contains("streamM3uImporter.importStreamM3u(playlistUri)"));
        assertTrue(mainDocumentPickerListener.contains("playlistExporter.exportPlaylist(exportUri, playlistId, playlistName)"));
        assertTrue(mainDocumentPickerListener.contains("playlistM3uImporter.importPlaylistM3u(playlistUri)"));
        assertTrue(mainDocumentPickerListener.contains("luoxueSourceUrisImporter.importLuoxueSourceUris(uris)"));
        assertTrue(platformModule.contains("fun provideMainDocumentPickerListenerFactory(): MainDocumentPickerListenerFactory"));
        assertTrue(mainActivity.contains("documentPickerController.openAudioFilePicker();"));
        assertTrue(mainActivity.contains("documentPickerController.openAudioFolderPicker();"));
        assertTrue(mainActivity.contains("() -> documentPickerController.openM3uFilePicker()"));
        assertTrue(mainActivity.contains("documentPickerController.openPlaylistM3uFilePicker();"));
        assertFalse(mainActivity.contains("private void openAudioFilePicker()"));
        assertFalse(mainActivity.contains("private void openAudioFolderPicker()"));
        assertFalse(mainActivity.contains("private void openPlaylistM3uFilePicker()"));
        assertFalse(mainActivity.contains("private void openM3uFilePicker()"));
        assertFalse(mainActivity.contains("protected void onActivityResult(int requestCode, int resultCode, Intent data)"));
        assertFalse(mainActivity.contains("documentPickerController.handleActivityResult(requestCode, resultCode, data)"));
        assertFalse(mainActivity.contains("new DocumentPickerController(this, new DocumentPickerController.Listener()"));
        assertFalse(mainActivity.contains("new DocumentPickerController(this, new DocumentPickerBindings("));
        assertTrue(confirmationDialogController.contains("internal class ConfirmationDialogController"));
        assertTrue(read("app/src/main/java/app/yukine/DialogLanguageProvider.kt").contains("internal fun interface DialogLanguageProvider"));
        assertFalse(confirmationDialogController.contains("interface LanguageProvider"));
        assertTrue(confirmationDialogController.contains("interface Listener"));
        assertTrue(confirmationDialogController.contains("fun confirmClearPlayHistory()"));
        assertTrue(confirmationDialogController.contains("fun confirmClearQueue()"));
        assertTrue(confirmationDialogController.contains("fun confirmDeleteRemoteSource(source: RemoteSource?)"));
        assertTrue(confirmationDialogController.contains("EchoDialog.builder(context)"));
        assertTrue(mainActivity.contains("private PlayHistoryActionController playHistoryActionController;"));
        assertTrue(mainActivity.contains("@Inject MainPlayHistoryActionControllerFactory playHistoryActionControllerFactory;"));
        assertTrue(mainActivity.contains("playHistoryActionController = playHistoryActionControllerFactory.create("));
        assertFalse(mainActivity.contains("playHistoryActionController = new PlayHistoryActionController("));
        assertTrue(mainActivity.contains("() -> confirmationDialogController.confirmClearPlayHistory()"));
        assertTrue(mainActivity.contains("playHistoryActionController.clearPlayHistory();"));
        assertFalse(mainActivity.contains("new TrackListRenderController.Listener()"));
        assertTrue(mainActivity.contains("track -> networkDialogController.showEditStream(track)"));
        assertTrue(mainActivity.contains("track -> confirmationDialogController.confirmDeleteTrack(track)"));
        assertTrue(mainActivity.contains("(title, tracks) -> confirmationDialogController.confirmDeleteTracks(title, tracks)"));
        assertTrue(mainActivity.contains("networkRequestController.deleteAllStreams();"));
        assertTrue(mainActivity.contains("networkRequestController.deleteTrack(trackId, status);"));
        assertTrue(mainActivity.contains("networkRequestController.deleteTracks(trackIds, status);"));
        assertTrue(mainActivity.contains("networkRequestController.deleteRemoteSource(sourceId);"));
        assertFalse(mainActivity.contains("private void confirmClearPlayHistory()"));
        assertFalse(mainActivity.contains("private void clearPlayHistory()"));
        assertFalse(mainActivity.contains("private void showEditStreamDialog("));
        assertFalse(mainActivity.contains("private void confirmDeleteTrack("));
        assertFalse(mainActivity.contains("private void confirmDeleteTracks("));
        assertFalse(mainActivity.contains("private void deleteAllStreams()"));
        assertFalse(mainActivity.contains("private void deleteTrack("));
        assertFalse(mainActivity.contains("private void deleteTracks("));
        assertFalse(mainActivity.contains("private void deleteRemoteSource("));
        assertTrue(playHistoryActionController.contains("internal class PlayHistoryActionController("));
        assertTrue(playHistoryActionController.contains("internal fun interface MainPlayHistoryActionControllerFactory"));
        assertTrue(playHistoryActionController.contains("fun create("));
        assertFalse(playHistoryActionController.contains("internal fun interface PlayHistoryLanguageModeProvider"));
        assertFalse(playHistoryActionController.contains("internal fun interface PlayHistoryStatusSink"));
        assertTrue(playHistoryActionController.contains("private val languageModeProvider: () -> String"));
        assertTrue(playHistoryActionController.contains("private val statusSink: (String) -> Unit"));
        assertTrue(playHistoryActionController.contains("fun clearPlayHistory()"));
        assertTrue(playHistoryActionController.contains("viewModel.clearPlayHistory"));
        assertTrue(playHistoryActionController.contains("libraryStateStore.clearPlayHistory()"));
        assertTrue(playHistoryActionController.contains("statusSink(text(\"clearing.play.history\"))"));
        assertTrue(playHistoryActionController.contains("collectionsReloadAction.run()"));
        assertFalse(exists("app/src/main/java/app/yukine/CollectionsReloader.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/CollectionsReloader.java"));
        assertFalse(exists("app/src/main/java/app/yukine/DialogLanguageProviderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/DialogLanguageProviderBindings.java"));
        assertFalse(read("app/src/main/java/app/yukine/NetworkDialogController.java").contains("interface LanguageProvider"));
        assertFalse(read("app/src/main/java/app/yukine/PlaylistDialogController.java").contains("interface LanguageProvider"));
        assertFalse(mainActivity.contains("new DialogLanguageProviderBindings("));
        assertTrue(mainActivity.contains("DialogLanguageProvider dialogLanguageProvider ="));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsControlsBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsControlsBindings.java"));
        assertFalse(mainActivity.contains("new SettingsPlaybackServiceControls()"));
        assertFalse(mainActivity.contains("new SettingsLyricsControls()"));
        assertFalse(mainActivity.contains("new SettingsFloatingLyricsControls()"));
        assertTrue(mainActivity.contains("@Inject MainSettingsRuntimeApplierFactory settingsRuntimeApplierFactory;"));
        assertTrue(mainActivity.contains("settingsRuntimeApplierFactory.create("));
        assertFalse(exists("app/src/main/java/app/yukine/BackNavigationBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/BackNavigationBindings.java"));
        assertTrue(mainActivity.contains("new OnBackPressedCallback(true)"));
        assertTrue(mainActivity.contains("getOnBackPressedDispatcher().addCallback(this, backCallback)"));
        assertFalse(mainActivity.contains("new BackNavigationBindings(this::handleAppBack).install(this, getOnBackPressedDispatcher())"));
        assertFalse(mainActivity.contains("new NetworkDialogController.LanguageProvider()"));
        assertFalse(mainActivity.contains("new PlaylistDialogController.LanguageProvider()"));
        assertFalse(mainActivity.contains("new ConfirmationDialogController.LanguageProvider()"));
        assertFalse(mainActivity.contains("new SettingsPlaybackServiceControls()"));
        assertFalse(mainActivity.contains("new SettingsLyricsControls()"));
        assertFalse(mainActivity.contains("new SettingsFloatingLyricsControls()"));
        assertTrue(mainActivity.contains("new ConfirmationDialogController.Listener()"));
        assertTrue(mainActivity.contains("playHistoryActionController.clearPlayHistory();"));
        assertTrue(mainActivity.contains("queueActionController.clearQueue();"));
        assertTrue(mainActivity.contains("networkRequestController.deleteAllStreams();"));
        assertTrue(mainActivity.contains("networkRequestController.deleteTrack(trackId, status);"));
        assertTrue(mainActivity.contains("networkRequestController.deleteTracks(trackIds, status);"));
        assertTrue(mainActivity.contains("networkRequestController.deleteRemoteSource(sourceId);"));
        assertFalse(mainActivity.contains("new ConfirmationDialogBindings("));
        assertTrue(playlistDialogController.contains("final class PlaylistDialogController"));
        assertTrue(playlistDialogController.contains("interface Listener"));
        assertTrue(playlistDialogController.contains("interface PlaylistProvider"));
        assertTrue(playlistDialogController.contains("void addToDefaultPlaylist(Track track);"));
        assertTrue(playlistDialogController.contains("void addTrackToPlaylist(long playlistId, long trackId);"));
        assertTrue(playlistDialogController.contains("void showAddToPlaylist(final Track track)"));
        assertTrue(playlistDialogController.contains("playlistProvider.playlists()"));
        assertTrue(playlistDialogController.contains("listener.addToDefaultPlaylist(track);"));
        assertTrue(mainActivity.contains("new PlaylistDialogController.Listener()"));
        assertTrue(mainActivity.contains("playlistDialogController = createPlaylistDialogController();"));
        assertTrue(mainActivity.contains("private PlaylistDialogController createPlaylistDialogController()"));
        assertTrue(mainActivity.contains("() -> libraryStore.playlists()"));
        assertFalse(mainActivity.contains("private void addToDefaultPlaylist(Track track)"));
        assertFalse(mainActivity.contains("private void createPlaylist(String name)"));
        assertFalse(mainActivity.contains("private void renamePlaylist(long playlistId, String name)"));
        assertFalse(mainActivity.contains("private void deletePlaylist(long playlistId, String name)"));
        assertFalse(mainActivity.contains("private void removeSelectedPlaylistTrack(long playlistId, Track track)"));
        assertFalse(mainActivity.contains("private void moveSelectedPlaylistTrack(long playlistId, Track track, int trackIndex, int direction)"));
        assertFalse(mainActivity.contains("private void addTrackToPlaylist(long playlistId, long trackId)"));
        assertTrue(mainActivity.contains("private void onDefaultPlaylistTrackAdded(long playlistId, boolean added)"));
        assertTrue(mainActivity.contains("private void onPlaylistCreated(long playlistId)"));
        assertTrue(mainActivity.contains("private void onPlaylistRenamed(long playlistId, boolean renamed)"));
        assertTrue(mainActivity.contains("private void onPlaylistDeleted(long playlistId, String name, boolean deleted)"));
        assertTrue(mainActivity.contains("private void onSelectedPlaylistTrackRemoved(long playlistId, Track track)"));
        assertTrue(mainActivity.contains("private void onSelectedPlaylistTrackMoved(long playlistId, Track track, int direction, boolean moved)"));
        assertTrue(mainActivity.contains("private void onTrackAddedToPlaylist(long playlistId, boolean added)"));
        assertTrue(mainActivity.contains("libraryViewModel.addToDefaultPlaylistJava(track"));
        assertTrue(mainActivity.contains("libraryViewModel.createPlaylistJava(name"));
        assertTrue(mainActivity.contains("libraryViewModel.renamePlaylistJava(playlistId, name"));
        assertTrue(mainActivity.contains("libraryViewModel.deletePlaylistJava(playlistId, name"));
        assertTrue(mainActivity.contains("libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track"));
        assertTrue(mainActivity.contains("libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction"));
        assertTrue(mainActivity.contains("libraryViewModel.addTrackToPlaylistJava(playlistId, trackId"));
        assertFalse(mainActivity.contains("playlistActionResultController.addToDefaultPlaylist(track);"));
        assertFalse(mainActivity.contains("playlistActionResultController.createPlaylist(name);"));
        assertFalse(mainActivity.contains("playlistActionResultController.renamePlaylist(playlistId, name);"));
        assertFalse(mainActivity.contains("playlistActionResultController.deletePlaylist(playlistId, name);"));
        assertFalse(mainActivity.contains("playlistActionResultController.removeSelectedPlaylistTrack(playlistId, track);"));
        assertFalse(mainActivity.contains("playlistActionResultController.moveSelectedPlaylistTrack(playlistId, track, trackIndex, direction);"));
        assertFalse(mainActivity.contains("playlistActionResultController.addTrackToPlaylist(playlistId, trackId);"));
        assertTrue(mainActivity.contains("playlistDialogController.confirmDeletePlaylist(playlist);"));
        assertTrue(mainActivity.contains("playlistDialogController.showAddToPlaylist(openAddToPlaylist.getTrack())"));
        assertFalse(mainActivity.contains("private void showCreatePlaylistDialog()"));
        assertFalse(mainActivity.contains("private void showRenamePlaylistDialog(final Playlist playlist)"));
        assertFalse(mainActivity.contains("private void showAddToPlaylistDialog(final Track track)"));
        assertFalse(mainActivity.contains("private void confirmDeletePlaylist(final Playlist playlist)"));
        assertFalse(mainActivity.contains("new PlaylistDialogBindings("));
        assertFalse(mainActivity.contains("playlistDialogController.showAddToPlaylist(track, libraryStore.playlists())"));
        assertTrue(mainActivity.contains("new PlaylistDialogController.Listener()"));
    }

    @Test
    public void networkRenderCoordinatorIsKotlinAndOwnsNetworkPageDispatch() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String coordinator = read("app/src/main/java/app/yukine/NetworkRenderCoordinator.kt");
        String menuRenderer = read("app/src/main/java/app/yukine/NetworkMenuRenderController.kt");
        String menuEvents = read("app/src/main/java/app/yukine/NetworkMenuEventController.kt");
        String menuViewModel = read("app/src/main/java/app/yukine/NetworkMenuViewModel.kt");
        String menuContracts = read("feature/navigation/src/main/java/app/yukine/NetworkMenuDestinationContracts.kt");
        String menuDestination = read("feature/navigation/src/main/java/app/yukine/network/NetworkMenuDestination.kt");
        String trackListRenderer = read("app/src/main/java/app/yukine/NetworkTrackListRenderController.kt");
        String sourcesRenderer = read("app/src/main/java/app/yukine/NetworkSourcesRenderController.kt");
        String sourcesViewModel = read("app/src/main/java/app/yukine/NetworkSourcesViewModel.kt");
        String sourcesContracts = read("feature/navigation/src/main/java/app/yukine/NetworkSourcesDestinationContracts.kt");
        String sourcesDestination = read("feature/navigation/src/main/java/app/yukine/network/NetworkSourcesDestination.kt");
        String sourcesEvents = read("app/src/main/java/app/yukine/NetworkSourcesEventController.kt");
        String navGraph = read("feature/navigation/src/main/java/app/yukine/navigation/EchoNavGraph.kt");
        String networkDestination = read("feature/navigation/src/main/java/app/yukine/network/NetworkDestination.kt");

        assertFalse(exists("app/src/main/java/app/yukine/NetworkCoordinator.kt"));
        assertFalse(mainActivity.contains("NetworkCoordinator networkCoordinator"));
        assertFalse(mainActivity.contains("new NetworkCoordinator("));
        assertFalse(mainActivity.contains("new NetworkCoordinator.Listener()"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkRenderCoordinator.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuEventController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuChromeBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuChromeBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuActionBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkTrackListRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkTrackListRenderBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkTrackListRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkSourcesRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkSourcesEventController.java"));
        assertFalse(mainActivity.contains("private NetworkMenuRenderController networkMenuRenderController;"));
        assertFalse(mainActivity.contains("private NetworkTrackListRenderController networkTrackListRenderController;"));
        assertFalse(mainActivity.contains("private NetworkSourcesRenderController networkSourcesRenderController;"));
        assertFalse(mainActivity.contains("private StreamingSearchRenderController streamingSearchRenderController;"));
        assertTrue(coordinator.contains("internal class NetworkRenderCoordinator"));
        assertTrue(coordinator.contains("private val libraryStore: MainLibraryStore"));
        assertTrue(coordinator.contains("MainRoutes.NETWORK_STREAMING -> renderStreamingNetwork()"));
        assertTrue(coordinator.contains("MainRoutes.NETWORK_STREAMING_HUB -> renderStreamingNetwork()"));
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
        assertTrue(menuRenderer.contains("listener.publishNetworkMenu(title, metrics, actions)"));
        assertTrue(menuRenderer.contains("MainRoutes.NETWORK_STREAMING"));
        assertFalse(exists("app/src/main/java/app/yukine/network/NetworkDestination.kt"));
        assertTrue(networkDestination.contains("fun NetworkDestination("));
        assertTrue(networkDestination.contains("networkPage: String"));
        assertFalse(networkDestination.contains("EchoNavHostState"));
        assertFalse(networkDestination.contains("StreamingSearchScreen"));
        assertTrue(networkDestination.contains("MainRoutes.NETWORK_STREAMING_HUB -> streamingContent()"));
        assertTrue(navGraph.contains("streamingContent = {"));
        assertTrue(navGraph.contains("StreamingSearchScreen("));
        assertFalse(networkDestination.contains("This network page is not available yet."));
        assertTrue(menuContracts.contains("data class NetworkMenuUiState"));
        assertFalse(menuViewModel.contains("data class NetworkMenuUiState"));
        assertTrue(menuViewModel.contains("val uiState: StateFlow<NetworkMenuUiState>"));
        assertTrue(menuDestination.contains("fun NetworkMenuDestination("));
        assertTrue(menuDestination.contains("SettingsScreen("));
        assertTrue(networkDestination.contains("NetworkMenuDestination(state = menuState)"));
        assertFalse(networkDestination.contains("SettingsScreen("));
        assertTrue(menuEvents.contains("internal class NetworkMenuEventController"));
        assertFalse(menuEvents.contains("NetworkMenuContentSink"));
        assertTrue(menuEvents.contains("private val networkMenuViewModel: NetworkMenuViewModel"));
        assertTrue(menuEvents.contains(": NetworkMenuRenderController.Listener"));
        assertTrue(menuEvents.contains("fun interface StreamTracksProvider"));
        assertTrue(menuEvents.contains("fun interface StreamTrackCountProvider"));
        assertTrue(menuEvents.contains("fun interface WebDavTracksProvider"));
        assertTrue(menuEvents.contains("fun interface RemoteSourcesProvider"));
        assertTrue(menuEvents.contains("fun interface Requests"));
        assertTrue(menuEvents.contains("statusSink.setStatus(labels.text(\"no.streams.to.play\"))"));
        assertTrue(menuEvents.contains("deleteConfirmation.confirmDeleteAllStreams()"));
        assertTrue(menuEvents.contains("requests.syncAllWebDavSources(sourceIds)"));
        assertTrue(menuEvents.contains("documentPicker.openM3uFilePicker()"));
        assertTrue(menuEvents.contains("networkMenuViewModel.updateMenu(title, metrics, actions)"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuActionBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkMenuActionBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkLibrarySourceBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkLibrarySourceBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkSourcesRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/NetworkSourcesRenderBindings.java"));
        assertTrue(mainActivity.contains("() -> documentPickerController.openM3uFilePicker()"));
        assertFalse(mainActivity.contains("private void openM3uFilePicker()"));
        assertTrue(mainActivity.contains("networkDialogController::showAddStream")
                || mainActivity.contains("() -> networkDialogController.showAddStream()"));
        assertTrue(mainActivity.contains("networkDialogController::showImportM3u")
                || mainActivity.contains("() -> networkDialogController.showImportM3u()"));
        assertTrue(mainActivity.contains("networkDialogController::showAddWebDav")
                || mainActivity.contains("() -> networkDialogController.showAddWebDav()"));
        assertTrue(mainActivity.contains("NetworkMenuEventController menuEvents = new NetworkMenuEventController("));
        assertFalse(mainActivity.contains("private NetworkMenuEventController networkMenuEventController;"));
        assertFalse(mainActivity.contains("new NetworkMenuDialogBindings("));
        assertTrue(mainActivity.contains("libraryStore::streamTracks")
                || mainActivity.contains("() -> libraryStore.streamTracks()"));
        assertTrue(mainActivity.contains("libraryStore::streamTrackCount")
                || mainActivity.contains("() -> libraryStore.streamTrackCount()"));
        assertTrue(mainActivity.contains("libraryStore::webDavTracks")
                || mainActivity.contains("() -> libraryStore.webDavTracks()"));
        assertTrue(mainActivity.contains("libraryStore::remoteSources")
                || mainActivity.contains("() -> libraryStore.remoteSources()"));
        assertTrue(mainActivity.contains("(tracks, index) -> playTrackListFromHost(tracks, index)"));
        assertFalse(mainActivity.contains("(title, metrics, actions) -> networkMenuViewModel.updateMenu(title, metrics, actions)"));
        assertFalse(mainActivity.contains("new NetworkMenuPlayerBindings("));
        assertFalse(mainActivity.contains("new NetworkMenuEventController.Player()"));
        assertTrue(mainActivity.contains("new NetworkMenuRenderController(menuEvents)"));
        assertFalse(mainActivity.contains("new NetworkMenuRenderController(this, new NetworkMenuRenderController.Listener()"));
        assertFalse(mainActivity.contains("new NetworkMenuContentSink("));
        assertFalse(mainActivity.contains("new NetworkMenuEventController.ContentSink()"));
        assertFalse(mainActivity.contains("new NetworkMenuEventController.Dialogs()"));
        assertFalse(mainActivity.contains("new NetworkMenuEventController.Player()"));
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
        assertTrue(trackListRenderer.contains("internal data class NetworkTrackListRequest("));
        assertFalse(trackListRenderer.contains("NetworkPageAction"));
        assertTrue(mainActivity.contains("NetworkTrackListRenderController trackListRenderer")
                && mainActivity.contains("new NetworkTrackListRenderController(new NetworkTrackListRenderController.Listener()"));
        assertFalse(mainActivity.contains("new NetworkTrackListRenderBindings("));
        assertTrue(mainActivity.contains("sourcesEvents.syncRemoteSource(sourceId);"));
        assertFalse(mainActivity.contains("private String remoteSourceName(long sourceId)"));
        assertTrue(mainActivity.contains("NetworkSourcesEventController sourcesEvents = new NetworkSourcesEventController("));
        assertTrue(mainActivity.contains("sourcesEvents.playRemoteSourceTracks(source);"));
        assertFalse(mainActivity.contains("NetworkSourcesEventController[] networkSourcesEventControllerRef"));
        assertFalse(mainActivity.contains("private ArrayList<Track> webDavTracksForSource(long sourceId)"));
        assertFalse(mainActivity.contains("private void playRemoteSourceTracks(RemoteSource source)"));
        assertTrue(mainActivity.contains("private void renderNetworkTrackList(NetworkTrackListRequest request)"));
        assertTrue(mainActivity.contains("new NetworkTrackListRequest("));
        assertTrue(sourcesRenderer.contains("internal class NetworkSourcesRenderController"));
        assertTrue(sourcesRenderer.contains("interface Listener"));
        assertTrue(sourcesRenderer.contains("private val viewModel: NetworkSourcesViewModel"));
        assertTrue(sourcesRenderer.contains("fun render(languageMode: String, remoteSources: List<RemoteSource>, allTracks: List<Track>)"));
        assertTrue(sourcesRenderer.contains("CollectionRowStateFactory.networkSourceRow("));
        assertTrue(sourcesRenderer.contains("viewModel.updateSources(title, remoteSources, rows, actions, headerActions, emptyText, labels)"));
        assertFalse(sourcesRenderer.contains("listener.publishNetworkSourcesChrome("));
        assertFalse(sourcesRenderer.contains("NetworkSourcesScreenFactory.create("));
        assertTrue(sourcesContracts.contains("data class NetworkSourcesUiState"));
        assertFalse(sourcesViewModel.contains("data class NetworkSourcesUiState"));
        assertTrue(sourcesViewModel.contains("val uiState: StateFlow<NetworkSourcesUiState>"));
        assertFalse(sourcesViewModel.contains("val screen: StateFlow<MainActivityNetworkSourcesUiState>"));
        assertFalse(exists("app/src/main/java/app/yukine/network/NetworkSourcesDestination.kt"));
        assertTrue(sourcesDestination.contains("fun NetworkSourcesDestination("));
        assertTrue(sourcesDestination.contains("NetworkSourcesScreen("));
        assertTrue(sourcesEvents.contains("internal class NetworkSourcesEventController"));
        assertTrue(sourcesEvents.contains(": NetworkSourcesRenderController.Listener"));
        assertTrue(sourcesEvents.contains("private val routeController: MainRouteController"));
        assertFalse(sourcesEvents.contains("MainStatePublisher"));
        assertTrue(sourcesEvents.contains("fun interface RemoteSourceNameProvider"));
        assertTrue(sourcesEvents.contains("fun interface WebDavTracksForSourceProvider"));
        assertTrue(sourcesEvents.contains("fun interface ShowEditWebDavAction"));
        assertTrue(sourcesEvents.contains("requestController.testRemoteSource(sourceId)"));
        assertTrue(sourcesEvents.contains("requestController.syncRemoteSource(sourceId, remoteSourceNameProvider.remoteSourceName(sourceId))"));
        assertFalse(sourcesEvents.contains("publishNetworkSources(title, rows)"));
        assertTrue(mainActivity.contains("NetworkSourcesEventController sourcesEvents = new NetworkSourcesEventController("));
        assertTrue(mainActivity.contains("new NetworkSourcesRenderController(networkSourcesViewModel, sourcesEvents)"));
        assertFalse(mainActivity.contains("new NetworkSourcesRenderBindings("));
        assertTrue(mainActivity.contains("libraryStore::remoteSourceName")
                || mainActivity.contains("sourceId -> libraryStore.remoteSourceName(sourceId, settingsStore.languageMode())"));
        assertTrue(mainActivity.contains("libraryStore::webDavTracksForSource")
                || mainActivity.contains("sourceId -> libraryStore.webDavTracksForSource(sourceId)"));
        assertTrue(mainActivity.contains("(tracks, index) -> playTrackListFromHost(tracks, index)"));
        assertFalse(mainActivity.contains("private void testRemoteSource("));
        assertFalse(mainActivity.contains("private void syncRemoteSource("));
        assertFalse(mainActivity.contains("new NetworkSourcesEventController.Player()"));
        assertFalse(mainActivity.contains("navHostState.setNetworkSourceActions(new ArrayList<>(state.getActions()))"));
        assertFalse(mainActivity.contains("navNetworkSourceActions = new ArrayList<>(state.getActions())"));
        assertFalse(mainActivity.contains("new NetworkSourcesRenderController(this, viewModel, new NetworkSourcesRenderController.Listener()"));
        assertFalse(mainActivity.contains("new NetworkSourcesRenderController(\n                networkSourcesViewModel,\n                new NetworkSourcesRenderController.Listener()"));
        assertFalse(mainActivity.contains("private void publishNetworkSourcesUiState("));
        assertFalse(mainActivity.contains("private void showEditWebDavDialog("));
        assertFalse(mainActivity.contains("private void confirmDeleteRemoteSource("));
        assertFalse(mainActivity.contains("private void selectRemoteSourceAndNavigateNetworkPage("));
        assertFalse(mainActivity.contains("new NetworkSourcesEventController.Player()"));
    }

    @Test
    public void legacyMainStatePublisherIsRemoved() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String mainViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String queueViewModel = read("app/src/main/java/app/yukine/queue/QueueViewModel.kt");

        assertFalse(exists("app/src/main/java/app/yukine/MainStatePublisher.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/MainStatePublisher.java"));
        assertFalse(mainActivity.contains("MainStatePublisher"));
        assertFalse(mainActivity.contains("statePublisher"));
        assertFalse(mainViewModel.contains("trackListState"));
        assertFalse(mainViewModel.contains("libraryGroupsState"));
        assertFalse(mainViewModel.contains("playlistTracksState"));
        assertFalse(mainViewModel.contains("playlistListState"));
        assertFalse(mainViewModel.contains("queueState"));
        assertFalse(mainViewModel.contains("recommendationScreen"));
        assertFalse(mainViewModel.contains("RecommendationScreenState"));
        assertFalse(mainViewModel.contains("updateTrackList"));
        assertFalse(mainViewModel.contains("updateLibraryGroups"));
        assertFalse(mainViewModel.contains("updatePlaylistTracks"));
        assertFalse(mainViewModel.contains("updatePlaylistList"));
        assertFalse(mainViewModel.contains("updateQueue"));
        assertTrue(queueViewModel.contains("override val uiState: StateFlow<QueueDestinationState>"));
        assertTrue(queueViewModel.contains("_uiState.value = QueueDestinationState(rows)"));
        assertFalse(queueViewModel.contains("MainActivityQueueUiState"));
    }

    @Test
    public void settingsContextProviderIsKotlinAndSettingsViewModelOwnsPageDispatch() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String contextProvider = read("app/src/main/java/app/yukine/SettingsContextProvider.kt");
        String labelFormatter = read("app/src/main/java/app/yukine/SettingsLabelFormatter.kt");
        String settingsViewModel = read("app/src/main/java/app/yukine/SettingsViewModel.kt");
        String settingsDestination = read("feature/navigation/src/main/java/app/yukine/settings/SettingsDestination.kt");
        String settingsDestinationContracts = read("feature/navigation/src/main/java/app/yukine/SettingsDestinationContracts.kt");
        String pageStateBuilder = read("app/src/main/java/app/yukine/SettingsPageStateBuilder.kt");
        String settingsPage = read("app/src/main/java/app/yukine/SettingsPage.kt");
        String settingsBackStack = read("app/src/main/java/app/yukine/SettingsBackStack.kt");
        String backPolicy = read("app/src/main/java/app/yukine/MainBackNavigationPolicy.kt");
        String libraryGrouping = read("app/src/main/java/app/yukine/LibraryGrouping.kt");
        String permissionController = read("app/src/main/java/app/yukine/MainPermissionController.kt");
        String permissionListener = read("app/src/main/java/app/yukine/MainPermissionListener.kt");
        String platformModule = read("app/src/main/java/app/yukine/di/PlatformModule.kt");
        String appPermissions = read("app/src/main/java/app/yukine/AppPermissions.kt");

        assertFalse(exists("app/src/main/java/app/yukine/SettingsRenderCoordinator.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsRenderCoordinator.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageRenderController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageEventController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageEventController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageChromeBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageChromeBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsGatewayBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsEffectBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsScrollStateSink.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsScrollStateSink.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPage.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsPageStateBuilder.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryGrouping.java"));
        assertFalse(exists("app/src/main/java/app/yukine/MainPermissionController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/AppPermissions.java"));
        assertTrue(contextProvider.contains("internal class SettingsContextProvider"));
        assertTrue(contextProvider.contains("private val settingsStore: MainSettingsStore"));
        assertTrue(contextProvider.contains("private val libraryStore: MainLibraryStore"));
        assertTrue(contextProvider.contains("private val playbackStore: MainPlaybackStore"));
        assertTrue(contextProvider.contains("fun preferencesSnapshot(): SettingsPreferencesSnapshot"));
        assertTrue(contextProvider.contains("fun runtimeStatus(): RuntimeSettingsStatus"));
        assertFalse(contextProvider.contains("fun render("));
        assertFalse(contextProvider.contains("SettingsPage.StreamingGateway ->"));
        assertFalse(contextProvider.contains("renderer.renderStreamingGateway("));
        assertTrue(contextProvider.contains("LibraryGrouping.uniqueAlbumCount(allTracks)"));
        assertFalse(contextProvider.contains("renderer.renderHome("));
        assertTrue(mainActivity.contains("settingsViewModel.renderPageFromHost("));
        assertTrue(mainActivity.contains("settingsContextProvider.preferencesSnapshot()"));
        assertTrue(mainActivity.contains("settingsContextProvider.runtimeStatus()"));
        assertFalse(mainActivity.contains("settingsRenderCoordinator.render("));
        assertTrue(settingsPage.contains("sealed class SettingsPage("));
        assertTrue(settingsPage.contains("data object PageBackground : SettingsPage(MainRoutes.SETTINGS_PAGE_BACKGROUND)"));
        assertTrue(settingsPage.contains("fun fromRoute(route: String?): SettingsPage"));
        assertTrue(settingsPage.contains("fun route(page: SettingsPage): String = page.route"));
        assertTrue(settingsBackStack.contains("fun parent(settingsPage: SettingsPage): SettingsPage"));
        assertFalse(settingsBackStack.contains("fun parentPage(settingsPage: String): String"));
        assertFalse(settingsBackStack.contains("SettingsPage.fromRoute(settingsPage)"));
        assertTrue(backPolicy.contains("SettingsPage.Home != settingsPage"));
        assertTrue(backPolicy.contains("SettingsBackStack.parent(settingsPage).route"));
        assertTrue(labelFormatter.contains("internal object SettingsLabelFormatter"));
        assertFalse(labelFormatter.contains("SettingsViewModel"));
        assertFalse(labelFormatter.contains("interface Listener"));
        assertFalse(labelFormatter.contains("val scrollState = SettingsListScrollState()"));
        assertTrue(settingsViewModel.contains("val scrollState = SettingsListScrollState()"));
        assertFalse(labelFormatter.contains("SettingsScrollStateSink"));
        assertFalse(labelFormatter.contains("fun updateSettingsContext("));
        assertFalse(labelFormatter.contains("fun renderPage("));
        assertFalse(labelFormatter.contains("fun publishScrollState()"));
        assertFalse(labelFormatter.contains("scrollStateSink.publishSettingsScrollState(scrollState)"));
        assertFalse(labelFormatter.contains("private fun renderSettingsScreen("));
        assertFalse(labelFormatter.contains("SettingsPageStateBuilder."));
        assertTrue(pageStateBuilder.contains("fun streamingGateway("));
        assertTrue(pageStateBuilder.contains("fun library("));
        assertTrue(pageStateBuilder.contains("fun lyricsGroup("));
        assertTrue(pageStateBuilder.contains("fun lyrics("));
        assertTrue(pageStateBuilder.contains("StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT"));
        assertTrue(pageStateBuilder.contains("StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT"));
        assertTrue(settingsViewModel.contains("sealed interface SettingsItem"));
        assertTrue(settingsViewModel.contains("sealed interface SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object OpenNetworkSources : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object OpenDownloads : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object LoadLibrary : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object OpenAudioFilePicker : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object OpenAudioFolderPicker : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object ReloadCurrentLyrics : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data class StartSleepTimer(val minutes: Int) : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object CancelSleepTimer : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object OpenFloatingLyricsPermission : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data class ChoosePageBackground(val page: String) : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object ExportBackup : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data object ImportBackup : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data class ApplyStreamingGatewayEndpoint(val endpoint: String) : SettingsEffect"));
        assertTrue(settingsViewModel.contains("data class ShowStatus(val message: String) : SettingsEffect"));
        assertTrue(settingsViewModel.contains("sealed interface SettingsEvent"));
        assertTrue(settingsViewModel.contains("data class NavigateSettingsPage(val page: SettingsPage)"));
        assertFalse(settingsViewModel.contains("interface SettingsGateway"));
        assertTrue(settingsViewModel.contains("data class SettingsUiState"));
        assertTrue(settingsViewModel.contains("data class SettingsPreferencesSnapshot("));
        assertTrue(settingsViewModel.contains("data class RuntimeSettingsStatus("));
        assertTrue(settingsViewModel.contains("data class SettingsState("));
        assertTrue(settingsViewModel.contains(") : SettingsDestinationState"));
        assertTrue(settingsViewModel.contains("val page: SettingsPage = SettingsPage.Home"));
        assertTrue(settingsViewModel.contains("val preferences: SettingsPreferencesSnapshot = SettingsPreferencesSnapshot()"));
        assertTrue(settingsViewModel.contains("val runtime: RuntimeSettingsStatus = RuntimeSettingsStatus()"));
        assertTrue(settingsViewModel.contains("val actions: List<SettingsAction> = emptyList()"));
        assertTrue(settingsViewModel.contains("val ui: SettingsUiState = SettingsUiState()"));
        assertTrue(settingsViewModel.contains("override val destinationTitle: String"));
        assertTrue(settingsViewModel.contains("get() = ui.title"));
        assertTrue(settingsViewModel.contains("override val destinationActions: List<SettingsAction>"));
        assertTrue(settingsDestinationContracts.contains("interface SettingsDestinationState"));
        assertTrue(settingsDestinationContracts.contains("data class SettingsChromeState"));
        assertTrue(settingsDestinationContracts.contains("val pageBackgrounds: PageBackgrounds = PageBackgrounds.empty()"));
        assertTrue(settingsDestinationContracts.contains("val nowPlayingGesturesEnabled: Boolean = true"));
        assertTrue(settingsDestinationContracts.contains("val destinationTitle: String"));
        assertTrue(settingsDestinationContracts.contains("val destinationMetrics: List<SettingsMetric>"));
        assertTrue(settingsDestinationContracts.contains("val destinationActions: List<SettingsAction>"));
        assertFalse(exists("app/src/main/java/app/yukine/settings/SettingsDestination.kt"));
        assertTrue(settingsDestination.contains("state: StateFlow<SettingsDestinationState>"));
        assertTrue(settingsDestination.contains("title = settingsState.destinationTitle"));
        assertFalse(settingsDestination.contains("import app.yukine.SettingsState"));
        assertFalse(settingsDestination.contains("StateFlow<SettingsState>"));
        assertTrue(settingsViewModel.contains("class SettingsViewModel @JvmOverloads constructor("));
        assertTrue(settingsViewModel.contains("val state: StateFlow<SettingsState>"));
        assertTrue(settingsViewModel.contains("val chromeState: StateFlow<SettingsChromeState>"));
        assertTrue(settingsViewModel.contains("private fun syncChromeState(preferences: SettingsPreferencesSnapshot)"));
        assertFalse(settingsViewModel.contains("fun bindGateway(nextGateway: SettingsGateway?)"));
        assertTrue(settingsViewModel.contains("fun bindEffectListener(nextListener: SettingsEffectListener?)"));
        assertTrue(settingsViewModel.contains("fun drainEffects(): List<SettingsEffect>"));
        assertTrue(settingsViewModel.contains("fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?)"));
        assertTrue(settingsViewModel.contains("fun onEvent(event: SettingsEvent)"));
        assertTrue(settingsViewModel.contains("fun updateSettingsContext("));
        assertTrue(settingsViewModel.contains("fun renderCurrentPage("));
        assertTrue(settingsViewModel.contains("private fun buildPageContent("));
        assertTrue(settingsViewModel.contains("preferences = preferences"));
        assertTrue(settingsViewModel.contains("runtime = runtime"));
        assertTrue(settingsViewModel.contains("renderCurrentPage(event.page, current.preferences, current.runtime)"));
        assertTrue(settingsViewModel.contains("_uiState.value = content.uiState"));
        assertFalse(settingsViewModel.contains("SettingsPageStateBuilder.buildContent(title, metrics, actions)"));
        assertTrue(settingsViewModel.contains("SettingsPageStateBuilder.streamingGateway("));
        assertTrue(settingsViewModel.contains("SettingsPageStateBuilder.lyricsGroup("));
        assertTrue(settingsViewModel.contains("SettingsPageStateBuilder.lyrics("));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ChoosePageBackground -> emitEffect(SettingsEffect.ChoosePageBackground(event.page))"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ClearPageBackground -> clearPageBackground(event.page)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ExportBackup -> emitEffect(SettingsEffect.ExportBackup)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ImportBackup -> emitEffect(SettingsEffect.ImportBackup)"));
        assertTrue(settingsViewModel.contains("SettingsEvent.LoadLibrary -> emitEffect(SettingsEffect.LoadLibrary)"));
        assertTrue(settingsViewModel.contains("fun clearPageBackground(page: String)"));
        assertFalse(settingsViewModel.contains("gateway?.choosePageBackground"));
        assertFalse(settingsViewModel.contains("gateway?.clearPageBackground"));
        assertFalse(settingsViewModel.contains("gateway?.exportBackup"));
        assertFalse(settingsViewModel.contains("gateway?.importBackup"));
        assertTrue(pageStateBuilder.contains("internal object SettingsPageStateBuilder"));
        assertTrue(pageStateBuilder.contains("fun build("));
        assertTrue(pageStateBuilder.contains("fun home("));
        assertTrue(pageStateBuilder.contains("fun aboutGroup("));
        assertTrue(pageStateBuilder.contains("fun appearanceGroup("));
        assertTrue(pageStateBuilder.contains("fun sourcesGroup("));
        assertTrue(pageStateBuilder.contains("fun playbackGroup("));
        assertTrue(pageStateBuilder.contains("fun theme("));
        assertTrue(pageStateBuilder.contains("fun advancedTheme("));
        assertTrue(pageStateBuilder.contains("fun accent("));
        assertTrue(pageStateBuilder.contains("fun language("));
        assertTrue(pageStateBuilder.contains("fun pageBackgrounds("));
        assertTrue(pageStateBuilder.contains("fun concurrentPlayback("));
        assertTrue(pageStateBuilder.contains("fun audioEffects("));
        assertTrue(pageStateBuilder.contains("fun nowPlayingGestures("));
        assertTrue(pageStateBuilder.contains("fun playbackRestore("));
        assertTrue(pageStateBuilder.contains("fun replayGain("));
        assertTrue(pageStateBuilder.contains("fun statusBarLyrics("));
        assertTrue(pageStateBuilder.contains("fun floatingLyrics("));
        assertTrue(pageStateBuilder.contains("fun sleepTimer("));
        assertTrue(pageStateBuilder.contains("fun playbackSpeed("));
        assertTrue(pageStateBuilder.contains("fun appVolume("));
        assertTrue(pageStateBuilder.contains("fun streamingAudioQuality("));
        assertTrue(pageStateBuilder.contains("fun shareStyle("));
        assertTrue(pageStateBuilder.contains("private fun booleanLeafPage("));
        assertTrue(pageStateBuilder.contains("SettingsBackStack.parent(currentPage)"));
        assertTrue(pageStateBuilder.contains("SettingsUiState("));
        assertTrue(pageStateBuilder.contains("stableActions.map { action -> action.toSettingsItem() }"));
        assertTrue(pageStateBuilder.contains("fun home("));
        assertTrue(pageStateBuilder.contains("fun aboutGroup("));
        assertTrue(pageStateBuilder.contains("fun appearanceGroup("));
        assertTrue(pageStateBuilder.contains("fun sourcesGroup("));
        assertTrue(pageStateBuilder.contains("fun playbackGroup("));
        assertTrue(pageStateBuilder.contains("fun theme("));
        assertTrue(pageStateBuilder.contains("fun advancedTheme("));
        assertTrue(pageStateBuilder.contains("fun accent("));
        assertTrue(pageStateBuilder.contains("fun language("));
        assertTrue(pageStateBuilder.contains("fun pageBackgrounds("));
        assertTrue(pageStateBuilder.contains("fun concurrentPlayback("));
        assertTrue(pageStateBuilder.contains("fun audioEffects("));
        assertTrue(pageStateBuilder.contains("fun nowPlayingGestures("));
        assertTrue(pageStateBuilder.contains("fun playbackRestore("));
        assertTrue(pageStateBuilder.contains("fun replayGain("));
        assertTrue(pageStateBuilder.contains("fun statusBarLyrics("));
        assertTrue(pageStateBuilder.contains("fun floatingLyrics("));
        assertTrue(pageStateBuilder.contains("fun sleepTimer("));
        assertTrue(pageStateBuilder.contains("fun playbackSpeed("));
        assertTrue(pageStateBuilder.contains("fun appVolume("));
        assertTrue(pageStateBuilder.contains("fun streamingAudioQuality("));
        assertTrue(pageStateBuilder.contains("fun shareStyle("));
        assertTrue(mainActivity.contains("settingsViewModel.bindEffectListener(effect -> {"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(((SettingsEffect.ShowStatus) effect).getMessage())"));
        assertTrue(mainActivity.contains("documentPickerController.openAudioFilePicker();"));
        assertTrue(mainActivity.contains("documentPickerController.openAudioFolderPicker();"));
        assertTrue(mainActivity.contains("lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode())"));
        assertTrue(mainActivity.contains("backgroundImagePickerController.open(((SettingsEffect.ChoosePageBackground) effect).getPage())"));
        assertTrue(mainActivity.contains("backupRestoreLauncher.exportBackup();"));
        assertTrue(mainActivity.contains("backupRestoreLauncher.importBackup();"));
        assertFalse(settingsViewModel.contains("fun navigateSettingsPage(page: String)"));
        assertFalse(settingsViewModel.contains("gateway?.navigateSettingsPage"));
        assertFalse(settingsViewModel.contains("gateway?.openNetworkSources"));
        assertFalse(settingsViewModel.contains("gateway?.openDownloads"));
        assertFalse(settingsViewModel.contains("gateway?.openAudioFilePicker"));
        assertFalse(settingsViewModel.contains("gateway?.openAudioFolderPicker"));
        assertFalse(settingsViewModel.contains("gateway?.openFloatingLyricsPermission"));
        assertFalse(settingsViewModel.contains("gateway?.applyPlaybackSpeed"));
        assertFalse(settingsViewModel.contains("gateway?.applyAppVolume"));
        assertFalse(settingsViewModel.contains("gateway?.setReplayGainEnabled"));
        assertFalse(settingsViewModel.contains("gateway?.setOnlineLyricsEnabled"));
        assertFalse(settingsViewModel.contains("gateway?.applyLyricsOffset"));
        assertFalse(settingsViewModel.contains("gateway?.applyAudioEffectSettings"));
        assertFalse(settingsViewModel.contains("gateway?.setStatusBarLyricsEnabled"));
        assertFalse(settingsViewModel.contains("gateway?.setFloatingLyricsEnabled"));
        assertTrue(labelFormatter.contains("@JvmStatic"));
        assertTrue(labelFormatter.contains("fun playbackSpeedLabel(speed: Float): String"));
        assertTrue(labelFormatter.contains("fun appVolumeLabel(volume: Float): String"));
        assertTrue(labelFormatter.contains("fun lyricsOffsetLabel(offsetMs: Long): String"));
        assertFalse(mainActivity.contains("SettingsPageEventController settingsPageEventController"));
        assertFalse(mainActivity.contains("new SettingsPageChromeBindings("));
        assertFalse(mainActivity.contains("navSettingsActions"));
        assertFalse(mainActivity.contains("setSettingsActions("));
        assertFalse(mainActivity.contains("settingsPageRenderController = new SettingsPageRenderController(settingsViewModel);"));
        assertFalse(mainActivity.contains("SettingsPageRenderController"));
        assertFalse(mainActivity.contains("settingsPageRenderController"));
        assertTrue(mainActivity.contains("settingsViewModel.scrollToTopOnNextRender();"));
        assertFalse(mainActivity.contains("settingsPageRenderController.getScrollState()"));
        assertFalse(mainActivity.contains("navSettingsScrollState"));
        assertFalse(mainActivity.contains("setSettingsScrollState("));
        assertTrue(settingsViewModel.contains("fun renderPageFromHost("));
        assertTrue(mainActivity.contains("settingsViewModel.renderPageFromHost("));
        assertFalse(mainActivity.contains("settingsViewModel.bindGateway(new SettingsGatewayBindings("));
        assertFalse(mainActivity.contains("new SettingsGatewayBindings("));
        assertTrue(mainActivity.contains("settingsViewModel.bindEffectListener(effect -> {"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(((SettingsEffect.ShowStatus) effect).getMessage())"));
        assertTrue(mainActivity.contains("documentPickerController.openAudioFilePicker();"));
        assertTrue(mainActivity.contains("documentPickerController.openAudioFolderPicker();"));
        assertTrue(mainActivity.contains("lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode())"));
        assertTrue(mainActivity.contains("backgroundImagePickerController.open(((SettingsEffect.ChoosePageBackground) effect).getPage())"));
        assertTrue(mainActivity.contains("backupRestoreLauncher.exportBackup();"));
        assertTrue(mainActivity.contains("backupRestoreLauncher.importBackup();"));
        assertFalse(mainActivity.contains("this::navigateSettingsPage"));
        assertFalse(mainActivity.contains("this::clearPageBackground"));
        assertFalse(mainActivity.contains("private void clearPageBackground"));
        assertFalse(mainActivity.contains("SettingsEffectBindings settingsEffectBindings = new SettingsEffectBindings("));
        assertFalse(mainActivity.contains("settingsViewModel.bindEffectListener(settingsEffectBindings::onEffect)"));
        assertFalse(mainActivity.contains("settingsViewModel.bindGateway(new ActivitySettingsGateway())"));
        assertFalse(mainActivity.contains("private final class ActivitySettingsGateway implements SettingsGateway"));
        assertFalse(mainActivity.contains("new SettingsPageRenderController.Listener()"));
        assertFalse(mainActivity.contains("new SettingsPageEventController.ContentSink()"));
        assertTrue(libraryGrouping.contains("internal object LibraryGrouping"));
        assertTrue(libraryGrouping.contains("@JvmField val SONGS: String"));
        assertTrue(libraryGrouping.contains("@JvmStatic"));
        assertTrue(libraryGrouping.contains("fun groupTracks("));
        assertTrue(libraryGrouping.contains("fun uniqueArtistCount("));
        assertTrue(permissionController.contains("internal class MainPermissionController"));
        assertTrue(permissionController.contains("internal fun interface PermissionRequestLauncher"));
        assertTrue(permissionController.contains("internal fun interface NeededPermissionsProvider"));
        assertTrue(permissionController.contains("interface Listener"));
        assertTrue(permissionController.contains("fun hasAudioPermission(): Boolean"));
        assertTrue(permissionController.contains("fun hasNotificationPermission(): Boolean"));
        assertTrue(permissionController.contains("fun requestNeededPermissions()"));
        assertTrue(permissionController.contains("ActivityResultContracts.RequestMultiplePermissions()"));
        assertTrue(permissionController.contains("permissionRequestLauncher.launch(permissions)"));
        assertFalse(permissionController.contains("fun handlePermissionsResult(requestCode: Int): Boolean"));
        assertFalse(permissionController.contains("REQUEST_AUDIO_PERMISSIONS"));
        assertFalse(exists("app/src/main/java/app/yukine/PermissionResultBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PermissionResultBindings.java"));
        assertFalse(mainActivity.contains("new PermissionResultBindings("));
        assertFalse(mainActivity.contains("new MainPermissionController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainPermissionListenerFactory permissionListenerFactory;"));
        assertTrue(mainActivity.contains("new MainPermissionController(this, permissionListenerFactory.create("));
        assertTrue(mainActivity.contains("() -> permissionController != null && permissionController.hasAudioPermission()"));
        assertTrue(mainActivity.contains("allowCachedFirst -> loadLibrary(allowCachedFirst)"));
        assertTrue(mainActivity.contains("this::showOnboarding"));
        assertTrue(mainActivity.contains("this::mountNavHostShell"));
        assertTrue(permissionListener.contains("internal class MainPermissionListener("));
        assertTrue(permissionListener.contains(": MainPermissionController.Listener"));
        assertTrue(permissionListener.contains("audioPermissionStatusSource.hasAudioPermission()"));
        assertTrue(permissionListener.contains("libraryLoader.loadLibrary(false)"));
        assertTrue(permissionListener.contains("onboardingVisibilitySource.showOnboarding()"));
        assertTrue(permissionListener.contains("navHostMounter.mountNavHostShell()"));
        assertTrue(platformModule.contains("fun provideMainPermissionListenerFactory(): MainPermissionListenerFactory"));
        assertTrue(mainActivity.contains("loadLibrary(false);"));
        assertFalse(mainActivity.contains("private void loadLibraryOnStartup()"));
        assertTrue(appPermissions.contains("internal object AppPermissions"));
        assertTrue(appPermissions.contains("@JvmStatic"));
        assertTrue(appPermissions.contains("fun neededPermissions("));
        assertFalse(appPermissions.contains("fun requestNeededPermissions("));
        assertFalse(appPermissions.contains("requestPermissions("));
        assertTrue(appPermissions.contains("fun hasAudioPermission("));
        assertTrue(appPermissions.contains("fun hasNotificationPermission("));
        assertTrue(appPermissions.contains("Manifest.permission.READ_MEDIA_AUDIO"));
        assertFalse(mainActivity.contains("public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)"));
        assertFalse(mainActivity.contains("permissionController.handlePermissionsResult(requestCode)"));
    }

    @Test
    public void backgroundImagePickerIsOwnedOutsideMainActivity() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String controller = read("app/src/main/java/app/yukine/BackgroundImagePickerController.kt");
        String listener = read("app/src/main/java/app/yukine/MainBackgroundImagePickerListener.kt");
        String platformModule = read("app/src/main/java/app/yukine/di/PlatformModule.kt");
        assertTrue(controller.contains("internal class BackgroundImagePickerController"));
        assertFalse(controller.contains("internal fun interface BackgroundLanguageModeProvider"));
        assertFalse(controller.contains("internal fun interface BackgroundTransformProvider"));
        assertTrue(controller.contains("private val languageModeProvider: () -> String"));
        assertTrue(controller.contains("private val transformProvider: (String) -> BackgroundTransform"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun open(page: String)"));
        assertTrue(controller.contains("registerForActivityResult(ActivityResultContracts.StartActivityForResult())"));
        assertTrue(controller.contains("registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->"));
        assertTrue(controller.contains("documentPickerLauncher.launch(intent)"));
        assertTrue(controller.contains("previewResultLauncher.launch("));
        assertTrue(controller.contains("languageModeProvider()"));
        assertTrue(controller.contains("transformProvider(page)"));
        assertTrue(controller.contains("takePersistableUriPermission"));
        assertFalse(exists("app/src/main/java/app/yukine/BackgroundImagePickerBindings.kt"));
        assertTrue(mainActivity.contains("new BackgroundImagePickerController("));
        assertFalse(mainActivity.contains("new BackgroundImagePickerController.Listener()"));
        assertFalse(mainActivity.contains("public void backgroundImagePicked(String page, Uri uri, BackgroundTransform transform)"));
        assertFalse(mainActivity.contains("public void backgroundImageCopyFailed(String page)"));
        assertTrue(mainActivity.contains("@Inject MainBackgroundImagePickerListenerFactory backgroundImagePickerListenerFactory;"));
        assertTrue(mainActivity.contains("backgroundImagePickerListenerFactory.create("));
        assertTrue(mainActivity.contains("(page, uri, transform) -> settingsViewModel.applyPageBackgrounds("));
        assertTrue(mainActivity.contains("settingsStore.pageBackgrounds().withBackground(page, uri.toString(), transform)"));
        assertTrue(mainActivity.contains("page -> statusMessageController.setStatus(AppLanguage.text("));
        assertTrue(listener.contains("internal class MainBackgroundImagePickerListener("));
        assertTrue(listener.contains(": BackgroundImagePickerController.Listener"));
        assertTrue(listener.contains("pageImageApplier.apply(page, uri, transform)"));
        assertTrue(listener.contains("copyFailedStatusSink.setCopyFailedStatus(page)"));
        assertTrue(platformModule.contains("fun provideMainBackgroundImagePickerListenerFactory(): MainBackgroundImagePickerListenerFactory"));
        assertFalse(mainActivity.contains("REQUEST_PAGE_BACKGROUND_IMAGE"));
        assertFalse(mainActivity.contains("backgroundImagePickerController.handleActivityResult(requestCode, resultCode, data)"));
        assertFalse(mainActivity.contains("REQUEST_PAGE_BACKGROUND_PREVIEW"));
        assertFalse(mainActivity.contains("pendingPageBackgroundTarget"));
        assertFalse(mainActivity.contains("private void openPageBackgroundPicker"));
        assertFalse(mainActivity.contains("private void handlePageBackgroundResult"));
        assertFalse(mainActivity.contains("intent.setType(\"image/*\")"));
    }

    @Test
    public void backupRestoreLauncherIsOwnedOutsideMainActivity() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String launcher = read("app/src/main/java/app/yukine/BackupRestoreLauncher.kt");

        assertFalse(exists("app/src/main/java/app/yukine/BackupRestoreBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/BackupRestoreBindings.java"));
        assertTrue(launcher.contains("internal class BackupRestoreLauncher"));
        assertFalse(launcher.contains("internal fun interface BackupStatusSink"));
        assertTrue(launcher.contains("private val statusSink: (String) -> Unit"));
        assertTrue(launcher.contains("fun exportBackup()"));
        assertTrue(launcher.contains("fun importBackup()"));
        assertTrue(launcher.contains("registerForActivityResult(ActivityResultContracts.StartActivityForResult())"));
        assertTrue(launcher.contains("activityResultLauncher.launch(intent)"));
        assertTrue(launcher.contains("Intent.ACTION_CREATE_DOCUMENT"));
        assertTrue(launcher.contains("Intent.ACTION_OPEN_DOCUMENT"));
        assertTrue(launcher.contains("intent.type = \"application/zip\""));
        assertTrue(launcher.contains("BackupManager.export(context, uri)"));
        assertTrue(launcher.contains("BackupManager.restore(context, uri)"));
        assertTrue(launcher.contains("statusSink(statusKey)"));
        assertTrue(mainActivity.contains("new BackupRestoreLauncher("));
        assertTrue(mainActivity.contains("statusKey -> {"));
        assertTrue(mainActivity.contains("statusMessageController.setStatusKey(statusKey);"));
        assertTrue(mainActivity.contains("return kotlin.Unit.INSTANCE;"));
        assertFalse(mainActivity.contains("new BackupRestoreBindings("));
        assertFalse(mainActivity.contains("backupRestoreLauncher.handleActivityResult(requestCode, resultCode, data)"));
        assertTrue(mainActivity.contains("backupRestoreLauncher.exportBackup();"));
        assertTrue(mainActivity.contains("backupRestoreLauncher.importBackup();"));
        assertFalse(mainActivity.contains("setStatus(AppLanguage.text(\r\n                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),\r\n                        statusKey\r\n                ))"));
        assertFalse(mainActivity.contains("setStatus(AppLanguage.text(\n                        settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode(),\n                        statusKey\n                ))"));
        assertFalse(mainActivity.contains("REQUEST_EXPORT_BACKUP"));
        assertFalse(mainActivity.contains("REQUEST_IMPORT_BACKUP"));
        assertFalse(mainActivity.contains("private void exportBackup()"));
        assertFalse(mainActivity.contains("private void importBackup()"));
        assertFalse(mainActivity.contains("private void handleBackupResult"));
        assertFalse(mainActivity.contains("new Intent(Intent.ACTION_CREATE_DOCUMENT)"));
        assertFalse(mainActivity.contains("new Intent(Intent.ACTION_OPEN_DOCUMENT)"));
        assertFalse(mainActivity.contains("BackupManager.INSTANCE.export"));
        assertFalse(mainActivity.contains("BackupManager.INSTANCE.restore"));
    }

    @Test
    public void mainSettingsStoreIsKotlinStateHolder() throws Exception {
        String settingsStore = read("app/src/main/java/app/yukine/MainSettingsStore.kt");
        String executors = read("app/src/main/java/app/yukine/MainExecutors.kt");
        String loadPreferenceUseCase = read("app/src/main/java/app/yukine/LoadSettingsPreferencesUseCase.kt");
        String preferenceUseCase = read("app/src/main/java/app/yukine/ApplySettingsPreferenceUseCase.kt");
        String settingsViewModel = read("app/src/main/java/app/yukine/SettingsViewModel.kt");
        String settingsModule = read("app/src/main/java/app/yukine/SettingsModule.kt");
        String playlistUseCases = read("app/src/main/java/app/yukine/PlaylistActionUseCases.kt");
        String playlistActionContracts = read("app/src/main/java/app/yukine/LibraryPlaylistActionContracts.kt");
        String libraryViewModel = read("app/src/main/java/app/yukine/LibraryViewModel.kt");
        String mainLibraryGateway = read("app/src/main/java/app/yukine/MainLibraryGateway.kt");
        String documentGateway = read("app/src/main/java/app/yukine/ContentResolverLibraryDocumentGateway.kt");
        String playlistActionGateway = read("app/src/main/java/app/yukine/MainLibraryPlaylistActionGateway.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");
        String collectionUseCases = read("app/src/main/java/app/yukine/LibraryCollectionUseCases.kt");
        String importUseCases = read("app/src/main/java/app/yukine/LibraryImportUseCases.kt");
        String documentPickerController = read("app/src/main/java/app/yukine/DocumentPickerController.kt");
        String settingsControls = read("app/src/main/java/app/yukine/SettingsControls.kt");
        String settingsRuntimeApplier = read("app/src/main/java/app/yukine/SettingsRuntimeApplier.kt");
        String settingsPlaybackServiceControlsAdapter = read("app/src/main/java/app/yukine/SettingsPlaybackServiceControlsAdapter.kt");
        String playHistoryActionController = read("app/src/main/java/app/yukine/PlayHistoryActionController.kt");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");

        assertFalse(exists("app/src/main/java/app/yukine/MainSettingsStore.java"));
        assertFalse(exists("app/src/main/java/app/yukine/MainExecutors.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsActionsController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsActionController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsActionController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsActionBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsActionBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsRuntimeApplier.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistActionsController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistExportController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistExportController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistExportBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryActionsController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryGatewayBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryCollectionGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryCollectionGatewayBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryImportGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryImportGatewayBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryDocumentGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryDocumentGatewayBindings.java"));
        assertFalse(exists("app/src/test/java/app/yukine/LibraryDocumentGatewayBindingsTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryPlaylistActionGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryPlaylistActionGatewayBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsAppliedListenerBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsAppliedListenerBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsEffectBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/SettingsCoordinator.kt"));
        assertTrue(settingsStore.contains("internal class MainSettingsStore"));
        assertTrue(settingsStore.contains("private var themeMode: String = EchoTheme.MODE_SYSTEM"));
        assertTrue(settingsStore.contains("private var languageMode: String = AppLanguage.MODE_SYSTEM"));
        assertTrue(settingsStore.contains("fun load(preferences: LoadedSettingsPreferences)"));
        assertTrue(settingsStore.contains("fun sync(preferences: SettingsPreferencesSnapshot)"));
        assertFalse(settingsStore.contains("MusicLibraryRepository"));
        assertFalse(settingsStore.contains("repository.loadThemeMode()"));
        assertTrue(loadPreferenceUseCase.contains("internal class LoadSettingsPreferencesUseCase"));
        assertTrue(loadPreferenceUseCase.contains("operations.loadThemeMode()"));
        assertTrue(loadPreferenceUseCase.contains("operations.loadConcurrentPlaybackEnabled()"));
        assertTrue(mainActivity.contains("@Inject LoadSettingsPreferencesUseCase loadSettingsPreferencesUseCase;"));
        assertTrue(mainActivity.contains("settingsStore.load(loadSettingsPreferencesUseCase.execute());"));
        assertFalse(mainActivity.contains("new LoadSettingsPreferencesUseCase("));
        assertFalse(mainActivity.contains("new MusicLibrarySettingsPreferenceLoadOperations(repository)"));
        assertTrue(settingsStore.contains("EchoTheme.setMode(themeMode)"));
        assertTrue(settingsStore.contains("fun setPlaybackSpeed(playbackSpeed: Float)"));
        assertTrue(executors.contains("internal class MainExecutors"));
        assertTrue(executors.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(executors.contains("Executors.newFixedThreadPool(2)"));
        assertTrue(executors.contains("Executors.newFixedThreadPool(3)"));
        assertTrue(executors.contains("fun io(task: Runnable)"));
        assertTrue(executors.contains("fun shutdownNow()"));
        assertTrue(settingsViewModel.contains("fun interface SettingsPreferenceGateway"));
        assertTrue(settingsViewModel.contains("fun interface SettingsRuntimeEffectListener"));
        assertTrue(settingsViewModel.contains("fun interface SettingsStoreMirror"));
        assertTrue(settingsViewModel.contains("fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?)"));
        assertTrue(settingsViewModel.contains("fun bindStoreMirror(nextMirror: SettingsStoreMirror?)"));
        assertTrue(settingsViewModel.contains("fun bindRuntimeEffectListener(nextListener: SettingsRuntimeEffectListener?)"));
        assertTrue(settingsViewModel.contains("data class ShowStatus(val message: String) : SettingsEffect"));
        assertTrue(settingsViewModel.contains("private fun emitAppliedStatus(message: String)"));
        assertTrue(settingsViewModel.contains("private fun applyRuntimeEffect(effect: SettingsRuntimeEffect): Boolean"));
        assertTrue(settingsViewModel.contains("private fun updatePreferences(transform: (SettingsPreferencesSnapshot) -> SettingsPreferencesSnapshot)"));
        assertTrue(settingsViewModel.contains("private fun updateRuntime(transform: (RuntimeSettingsStatus) -> RuntimeSettingsStatus)"));
        assertTrue(settingsViewModel.contains("fun applyThemeMode(nextMode: String)"));
        assertTrue(settingsViewModel.contains("EchoTheme.normalizeMode(nextMode)"));
        assertTrue(settingsViewModel.contains("updatePreferences { it.copy(themeMode = mode) }"));
        assertTrue(settingsViewModel.contains("savePreference(SettingsPreferenceKey.ThemeMode, mode)"));
        assertTrue(settingsViewModel.contains("data class SettingsAppliedStatusText("));
        assertTrue(settingsViewModel.contains("fun prepareAppliedStatusText("));
        assertFalse(settingsViewModel.contains("repository.saveThemeMode("));
        assertTrue(settingsViewModel.contains("fun applyPlaybackSpeed(speed: Float)"));
        assertTrue(settingsViewModel.contains("private fun normalizePlaybackSpeed(speed: Float): Float"));
        assertTrue(settingsViewModel.contains("fun applyLyricsOffset(offsetMs: Long)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ApplyThemeMode -> applyThemeMode(event.mode)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ApplyPlaybackSpeed -> applyPlaybackSpeed(event.speed)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.SetOnlineLyricsEnabled -> setOnlineLyricsEnabled(event.enabled)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ApplyLyricsOffset -> applyLyricsOffset(event.offsetMs)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.ApplyAudioEffectSettings -> applyAudioEffectSettings(event.settings)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.SetStatusBarLyricsEnabled -> setStatusBarLyricsEnabled(event.enabled)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.SetFloatingLyricsEnabled -> setFloatingLyricsEnabled(event.enabled)"));
        assertTrue(settingsViewModel.contains("is SettingsEvent.SetReplayGainEnabled -> setReplayGainEnabled(event.enabled)"));
        assertTrue(mainActivity.contains("settingsViewModel.bindPreferenceGateway"));
        assertTrue(mainActivity.contains("@Inject ApplySettingsPreferenceUseCase applySettingsPreferenceUseCase;"));
        assertTrue(mainActivity.contains("settingsViewModel.bindPreferenceGateway(applySettingsPreferenceUseCase::execute);"));
        assertFalse(mainActivity.contains("new ApplySettingsPreferenceUseCase("));
        assertFalse(mainActivity.contains("new MusicLibrarySettingsPreferenceOperations(repository)"));
        assertTrue(mainActivity.contains("settingsViewModel.bindStoreMirror(settingsStore::sync);"));
        assertFalse(mainActivity.contains("settingsViewModel.bindAppliedListener"));
        assertTrue(mainActivity.contains("settingsRuntimeApplierFactory.create("));
        assertTrue(mainActivity.contains("() -> playbackService == null ? null : new MainSettingsPlaybackServiceControls(playbackService)"));
        assertTrue(mainActivity.contains("() -> lyricsViewModel"));
        assertTrue(mainActivity.contains("() -> permissionController"));
        assertTrue(mainActivity.contains("settingsViewModel.bindRuntimeEffectListener(settingsRuntimeApplier::apply);"));
        assertFalse(mainActivity.contains("SettingsCoordinator settingsCoordinator"));
        assertFalse(mainActivity.contains("new SettingsCoordinator("));
        assertFalse(mainActivity.contains("settingsCoordinator.bindRuntimeApplier()"));
        assertFalse(mainActivity.contains("new SettingsAppliedListener()"));
        assertFalse(mainActivity.contains("new SettingsAppliedListenerBindings("));
        assertTrue(mainActivity.contains("settingsViewModel.bindEffectListener(effect -> {"));
        assertTrue(mainActivity.contains("applyStreamingGatewayEndpoint(((SettingsEffect.ApplyStreamingGatewayEndpoint) effect).getEndpoint())"));
        assertFalse(mainActivity.contains("new SettingsActionController("));
        assertFalse(mainActivity.contains("settingsActionController::startSleepTimer"));
        assertFalse(mainActivity.contains("settingsActionController::reloadCurrentLyrics"));
        assertFalse(mainActivity.contains("settingsActionController::applyThemeMode"));
        assertFalse(mainActivity.contains("settingsActionController::applyStreamingAudioQuality"));
        assertFalse(mainActivity.contains("settingsActionController::setOnlineLyricsEnabled"));
        assertFalse(mainActivity.contains("settingsActionController::applyLyricsOffset"));
        assertFalse(mainActivity.contains("settingsActionController::applyAudioEffectSettings"));
        assertFalse(mainActivity.contains("settingsActionController::setStatusBarLyricsEnabled"));
        assertFalse(mainActivity.contains("settingsActionController::setFloatingLyricsEnabled"));
        assertFalse(mainActivity.contains("settingsActionController::applyStreamingGatewayEndpoint"));
        assertTrue(mainActivity.contains("lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode())"));
        assertTrue(mainActivity.contains("applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(((SettingsEffect.StartSleepTimer) effect).getMinutes()))"));
        assertTrue(mainActivity.contains("applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer())"));
        assertTrue(mainActivity.contains("applyStreamingGatewayEndpoint(((SettingsEffect.ApplyStreamingGatewayEndpoint) effect).getEndpoint())"));
        assertFalse(mainActivity.contains("private void startSleepTimer(int minutes)"));
        assertFalse(mainActivity.contains("private void applyThemeMode(String nextMode)"));
        assertFalse(mainActivity.contains("settingsViewModel.applyThemeMode(nextMode);"));
        assertFalse(mainActivity.contains("settingsViewModel.applyAccentMode(nextAccent);"));
        assertFalse(mainActivity.contains("settingsViewModel.applyLanguageMode(nextLanguageMode);"));
        assertFalse(mainActivity.contains("settingsViewModel.applyPlaybackSpeed(speed);"));
        assertFalse(mainActivity.contains("settingsViewModel.applyAppVolume(volume);"));
        assertFalse(mainActivity.contains("settingsViewModel.applyStreamingAudioQuality(quality);"));
        assertFalse(mainActivity.contains("settingsViewModel.setConcurrentPlaybackEnabled(enabled);"));
        assertFalse(mainActivity.contains("settingsViewModel.setOnlineLyricsEnabled(enabled);"));
        assertFalse(mainActivity.contains("settingsViewModel.applyLyricsOffset(offsetMs);"));
        assertFalse(mainActivity.contains("applyPlaybackActionResult(nowPlayingViewModel.startSleepTimer(minutes));"));
        assertTrue(mainActivity.contains("applyPlaybackActionResult(nowPlayingViewModel.cancelSleepTimer())"));
        assertTrue(settingsControls.contains("internal interface SettingsPlaybackServiceControls"));
        assertTrue(settingsControls.contains("internal interface SettingsLyricsControls"));
        assertTrue(settingsControls.contains("internal interface SettingsFloatingLyricsControls"));
        assertFalse(settingsControls.contains("SettingsStatusSink"));
        assertFalse(settingsViewModel.contains("appliedListener"));
        assertFalse(settingsViewModel.contains("SettingsAppliedListener"));
        assertFalse(mainActivity.contains("this::renderSelectedTab,\n                this::renderNowBar,"));
        assertFalse(mainActivity.contains("this::selectedTab\n        ));"));
        assertTrue(settingsRuntimeApplier.contains("internal class SettingsRuntimeApplier("));
        assertTrue(settingsRuntimeApplier.contains("sealed interface SettingsRuntimeEffect"));
        assertTrue(settingsRuntimeApplier.contains("data object ApplyThemeSurface : SettingsRuntimeEffect"));
        assertTrue(settingsRuntimeApplier.contains("data class ApplyPlaybackSpeed(val speed: Float) : SettingsRuntimeEffect"));
        assertTrue(settingsRuntimeApplier.contains("data class ApplyFloatingLyrics(val enabled: Boolean) : SettingsRuntimeEffect"));
        assertTrue(settingsRuntimeApplier.contains("internal class MainSettingsRuntimeApplierFactory("));
        assertTrue(settingsRuntimeApplier.contains("playbackServiceControlsProvider: SettingsPlaybackServiceControlsProvider"));
        assertFalse(settingsRuntimeApplier.contains("EchoPlaybackService"));
        assertFalse(settingsRuntimeApplier.contains("internal class MainSettingsPlaybackServiceControls("));
        assertTrue(settingsPlaybackServiceControlsAdapter.contains("internal class MainSettingsPlaybackServiceControls("));
        assertTrue(settingsPlaybackServiceControlsAdapter.contains("private val service: SettingsPlaybackServicePort"));
        assertTrue(settingsPlaybackServiceControlsAdapter.contains("service.setPlaybackSpeed(speed)"));
        assertTrue(settingsRuntimeApplier.contains("internal class MainSettingsLyricsControls("));
        assertTrue(settingsRuntimeApplier.contains("viewModel.setOnlineEnabled(enabled)"));
        assertTrue(settingsRuntimeApplier.contains("internal class MainSettingsFloatingLyricsControls("));
        assertTrue(settingsRuntimeApplier.contains("FloatingLyricsService.stop(context)"));
        assertTrue(settingsRuntimeApplier.contains("permissionController.openOverlayPermissionSettings()"));
        assertTrue(settingsRuntimeApplier.contains("FloatingLyricsService.start(context)"));
        assertTrue(settingsRuntimeApplier.contains("fun apply(effect: SettingsRuntimeEffect): Boolean"));
        assertTrue(settingsRuntimeApplier.contains("private val playbackServiceControlsProvider: SettingsPlaybackServiceControlsProvider"));
        assertTrue(settingsRuntimeApplier.contains("private val lyricsControlsProvider: SettingsLyricsControlsProvider"));
        assertTrue(settingsRuntimeApplier.contains("private val floatingLyricsControlsProvider: SettingsFloatingLyricsControlsProvider"));
        assertTrue(settingsViewModel.contains("applyRuntimeEffect(SettingsRuntimeEffect.ApplyThemeSurface)"));
        assertTrue(settingsViewModel.contains("applyRuntimeEffect(SettingsRuntimeEffect.ApplyPlaybackSpeed(normalizedSpeed))"));
        assertTrue(settingsViewModel.contains("applyRuntimeEffect(SettingsRuntimeEffect.ApplyFloatingLyrics(enabled))"));
        assertFalse(settingsRuntimeApplier.contains("UpdateLanguage"));
        assertFalse(settingsRuntimeApplier.contains("updateLanguage("));
        assertFalse(mainActivity.contains("updateLanguage(languageMode)"));
        assertFalse(mainActivity.contains("FloatingLyricsService.start(MainActivityBase.this)"));
        assertFalse(mainActivity.contains("FloatingLyricsService.stop(MainActivityBase.this)"));
        assertFalse(mainActivity.contains("SettingsRuntimeLanguageUpdater"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"theme.applied\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"accent.applied\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"language.applied\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"speed.applied\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"volume.applied\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"online.lyrics.enabled\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"online.lyrics.disabled\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"concurrent.playback.enabled\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"concurrent.playback.disabled\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"lyrics.offset.applied\")"));
        assertFalse(mainActivity.contains("SettingsPageRenderController.playbackSpeedLabel(settingsStore.playbackSpeed())"));
        assertFalse(mainActivity.contains("SettingsPageRenderController.appVolumeLabel(settingsStore.appVolume())"));
        assertFalse(mainActivity.contains("SettingsPageRenderController.lyricsOffsetLabel(offsetMs)"));
        assertFalse(mainActivity.contains("private SettingsActionsController settingsActionsController"));
        assertFalse(mainActivity.contains("settingsActionsController."));
        assertTrue(preferenceUseCase.contains("internal class ApplySettingsPreferenceUseCase"));
        assertTrue(preferenceUseCase.contains("SettingsPreferenceKey.ThemeMode -> operations.saveThemeMode"));
        assertTrue(preferenceUseCase.contains("SettingsPreferenceKey.LyricsOffsetMs -> operations.saveLyricsOffsetMs"));
        assertTrue(preferenceUseCase.contains("MusicLibrarySettingsPreferenceOperations"));
        assertTrue(settingsModule.contains("internal object SettingsModule"));
        assertTrue(settingsModule.contains("LoadSettingsPreferencesUseCase(MusicLibrarySettingsPreferenceLoadOperations(repository))"));
        assertTrue(settingsModule.contains("ApplySettingsPreferenceUseCase(MusicLibrarySettingsPreferenceOperations(repository))"));
        assertTrue(mainActivity.contains("@Inject MainLibraryGatewayFactory libraryGatewayFactory;"));
        assertTrue(mainActivity.contains("libraryViewModel.bindGateway(libraryGatewayFactory.create("));
        assertTrue(mainActivity.contains("(tracks, index) -> MainActivityBase.this.playTrackListFromHost(tracks, index)"));
        assertTrue(mainActivity.contains("status -> statusMessageController.setStatus(status)"));
        assertTrue(mainActivity.contains("(trackId, favorite) -> viewModel.setFavorite(trackId, favorite)"));
        assertTrue(mainActivity.contains("routeController,"));
        assertTrue(mainActivity.contains("() -> documentPickerController.openAudioFilePicker()"));
        assertFalse(mainActivity.contains("libraryViewModel.bindGateway(new LibraryGateway() {"));
        assertFalse(mainActivity.contains("statusMessageController.setStatus(AppLanguage.text(settingsStore.languageMode(), key));"));
        assertTrue(mainLibraryGateway.contains("internal fun interface MainLibraryGatewayFactory"));
        assertTrue(mainLibraryGateway.contains("internal interface LibraryRouteActions"));
        assertTrue(mainLibraryGateway.contains("internal class MainLibraryGateway("));
        assertTrue(mainLibraryGateway.contains(") : LibraryGateway"));
        assertTrue(mainLibraryGateway.contains("statusSink.setStatus(AppLanguage.text(languageModeProvider.languageMode(), key))"));
        assertTrue(mainLibraryGateway.contains("favoriteApplier.setFavorite(trackId, favorite)"));
        assertTrue(mainLibraryGateway.contains("nowBarRenderer.renderNowBar()"));
        assertTrue(mainLibraryGateway.contains("selectedTabRenderer.renderSelectedTab()"));
        assertTrue(mainLibraryGateway.contains("collectionsLoader.loadCollections()"));
        assertTrue(mainLibraryGateway.contains("playlistAdder.showAddToPlaylist(track)"));
        assertTrue(mainLibraryGateway.contains("routeActions.setLibraryMode(mode)"));
        assertTrue(mainLibraryGateway.contains("routeActions.selectLibraryGroup(key, title)"));
        assertTrue(mainLibraryGateway.contains("routeActions.selectLibraryGroup(\"playlist:$playlistId\", title)"));
        assertTrue(mainLibraryGateway.contains("routeActions.setSelectedPlaylistId(playlistId)"));
        assertTrue(mainLibraryGateway.contains("routeActions.clearLibraryGroup()"));
        assertTrue(mainLibraryGateway.contains("routeActions.setSelectedPlaylistId(-1L)"));
        assertTrue(mainLibraryGateway.contains("routeActions.setSearchQuery(query)"));
        assertTrue(mainLibraryGateway.contains("audioImporter.openAudioFilePicker()"));
        assertTrue(mainLibraryGateway.contains("libraryScanner.scanLibrary(false)"));
        assertFalse(mainActivity.contains("new LibraryGatewayBindings("));
        assertFalse(libraryViewModel.contains("LibraryEventSink"));
        assertTrue(playlistActionContracts.contains("interface LibraryPlaylistActionGateway"));
        assertTrue(playlistActionContracts.contains("fun addToDefaultPlaylist("));
        assertTrue(playlistActionContracts.contains("fun createPlaylist("));
        assertTrue(playlistActionContracts.contains("fun renamePlaylist("));
        assertTrue(playlistActionContracts.contains("fun deletePlaylist("));
        assertTrue(libraryViewModel.contains("fun removeSelectedPlaylistTrack("));
        assertTrue(libraryViewModel.contains("fun moveSelectedPlaylistTrack("));
        assertTrue(playlistActionContracts.contains("fun addTrackToPlaylist("));
        assertTrue(playlistActionContracts.contains("data class LibraryPlaylistActionPresentation("));
        assertTrue(libraryViewModel.contains("fun defaultPlaylistAddPresentation("));
        assertTrue(libraryViewModel.contains("fun playlistCreatedPresentation("));
        assertTrue(libraryViewModel.contains("fun playlistRenamedPresentation("));
        assertTrue(libraryViewModel.contains("fun playlistDeletedPresentation("));
        assertTrue(libraryViewModel.contains("fun selectedPlaylistTrackRemovedPresentation("));
        assertTrue(libraryViewModel.contains("fun selectedPlaylistTrackMovedPresentation("));
        assertTrue(libraryViewModel.contains("fun trackAddedToPlaylistPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.bindPlaylistActionGateway"));
        assertTrue(mainActivity.contains("libraryViewModel.addToDefaultPlaylistJava(track"));
        assertTrue(mainActivity.contains("libraryViewModel.createPlaylistJava(name"));
        assertTrue(mainActivity.contains("libraryViewModel.renamePlaylistJava(playlistId, name"));
        assertTrue(mainActivity.contains("libraryViewModel.deletePlaylistJava(playlistId, name"));
        assertTrue(mainActivity.contains("libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track"));
        assertTrue(mainActivity.contains("libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction"));
        assertTrue(mainActivity.contains("libraryViewModel.addTrackToPlaylistJava(playlistId, trackId"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistActionResultController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistActionResultController.java"));
        assertFalse(exists("app/src/test/java/app/yukine/PlaylistActionResultControllerTest.kt"));
        assertFalse(mainActivity.contains("new PlaylistActionResultController("));
        assertFalse(mainActivity.contains("playlistActionResultController."));
        assertFalse(mainActivity.contains("private void addToDefaultPlaylist(Track track)"));
        assertFalse(mainActivity.contains("private void createPlaylist(String name)"));
        assertFalse(mainActivity.contains("private void renamePlaylist(long playlistId, String name)"));
        assertFalse(mainActivity.contains("private void deletePlaylist(long playlistId, String name)"));
        assertFalse(mainActivity.contains("private void removeSelectedPlaylistTrack(long playlistId, Track track)"));
        assertFalse(mainActivity.contains("private void moveSelectedPlaylistTrack(long playlistId, Track track, int trackIndex, int direction)"));
        assertFalse(mainActivity.contains("private void addTrackToPlaylist(long playlistId, long trackId)"));
        assertTrue(mainActivity.contains("private void onDefaultPlaylistTrackAdded(long playlistId, boolean added)"));
        assertTrue(mainActivity.contains("private void onPlaylistCreated(long playlistId)"));
        assertTrue(mainActivity.contains("private void onPlaylistRenamed(long playlistId, boolean renamed)"));
        assertTrue(mainActivity.contains("private void onPlaylistDeleted(long playlistId, String name, boolean deleted)"));
        assertTrue(mainActivity.contains("private void onSelectedPlaylistTrackRemoved(long playlistId, Track track)"));
        assertTrue(mainActivity.contains("private void onSelectedPlaylistTrackMoved(long playlistId, Track track, int direction, boolean moved)"));
        assertTrue(mainActivity.contains("private void onTrackAddedToPlaylist(long playlistId, boolean added)"));
        assertTrue(mainActivity.contains("libraryViewModel.addToDefaultPlaylistJava(track"));
        assertTrue(mainActivity.contains("libraryViewModel.createPlaylistJava(name"));
        assertTrue(mainActivity.contains("libraryViewModel.renamePlaylistJava(playlistId, name"));
        assertTrue(mainActivity.contains("libraryViewModel.deletePlaylistJava(playlistId, name"));
        assertTrue(mainActivity.contains("libraryViewModel.removeSelectedPlaylistTrackJava(playlistId, track"));
        assertTrue(mainActivity.contains("libraryViewModel.moveSelectedPlaylistTrackJava(playlistId, track, trackIndex, direction"));
        assertTrue(mainActivity.contains("libraryViewModel.addTrackToPlaylistJava(playlistId, trackId"));
        assertTrue(mainActivity.contains("libraryViewModel.defaultPlaylistAddPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.playlistCreatedPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.playlistRenamedPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.playlistDeletedPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.selectedPlaylistTrackRemovedPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.selectedPlaylistTrackMovedPresentation("));
        assertTrue(mainActivity.contains("libraryViewModel.trackAddedToPlaylistPresentation("));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"added.to.playlist\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"could.not.add.to.playlist\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"playlist.created\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"playlist.renamed\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"playlist.rename.failed\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"deleted.playlist.prefix\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"could.not.delete.playlist\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"removed.from.playlist.prefix\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"moved.up.prefix\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"moved.down.prefix\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"move.failed\")"));
        assertFalse(mainActivity.contains("private PlaylistActionsController playlistActionsController"));
        assertFalse(mainActivity.contains("playlistActionsController."));
        assertTrue(playlistUseCases.contains("internal interface PlaylistActionOperations"));
        assertTrue(playlistUseCases.contains("internal class MusicLibraryPlaylistActionOperations"));
        assertTrue(playlistUseCases.contains("internal class AddToDefaultPlaylistUseCase"));
        assertTrue(playlistUseCases.contains("operations.ensureDefaultPlaylist()"));
        assertTrue(playlistUseCases.contains("internal class MovePlaylistTrackUseCase"));
        assertTrue(playlistUseCases.contains("operations.movePlaylistTrackAt(playlistId, trackIndex, direction)"));
        assertTrue(libraryViewModel.contains("interface LibraryCollectionGateway"));
        assertTrue(libraryViewModel.contains("fun loadCollections("));
        assertTrue(libraryViewModel.contains("fun clearPlayHistory("));
        assertFalse(libraryViewModel.contains("fun saveLibraryFavorite("));
        assertTrue(mainActivity.contains("@Inject LibraryCollectionGateway libraryCollectionGateway;"));
        assertTrue(mainActivity.contains("libraryViewModel.bindCollectionGateway(libraryCollectionGateway);"));
        assertFalse(mainActivity.contains("libraryViewModel.bindCollectionGateway(new LibraryCollectionGateway() {"));
        assertFalse(mainActivity.contains("LibraryCollectionsSnapshot loaded = new LoadLibraryCollectionsUseCase(libraryCollectionOperations).execute(selectedPlaylistId);"));
        assertFalse(mainActivity.contains("return new ClearPlayHistoryUseCase(libraryCollectionOperations).execute();"));
        assertFalse(mainActivity.contains("new SetLibraryFavoriteUseCase(libraryCollectionOperations).execute(trackId, favorite);"));
        assertFalse(mainActivity.contains("public void setFavorite(long trackId, boolean favorite)"));
        assertFalse(mainActivity.contains("new LibraryCollectionGatewayBindings("));
        assertTrue(mainActivity.contains("libraryViewModel.loadCollectionsJava(selectedPlaylistId()"));
        assertFalse(mainActivity.contains("libraryViewModel.clearPlayHistoryJava"));
        assertTrue(playHistoryActionController.contains("viewModel.clearPlayHistory"));
        assertTrue(mainActivity.contains("libraryStore.filteredSelectedPlaylistTracks(searchQuery())"));
        assertFalse(mainActivity.contains("private LibraryActionsController libraryActionsController"));
        assertFalse(mainActivity.contains("libraryActionsController."));
        assertTrue(collectionUseCases.contains("internal class LoadLibraryCollectionsUseCase"));
        assertTrue(collectionUseCases.contains("operations.ensureDefaultPlaylist()"));
        assertTrue(collectionUseCases.contains("operations.loadRecentlyPlayed(PLAY_HISTORY_RECAP_LIMIT)"));
        assertTrue(collectionUseCases.contains("internal class ClearPlayHistoryUseCase"));
        assertTrue(collectionUseCases.contains("internal class MainLibraryCollectionGateway("));
        assertTrue(collectionUseCases.contains(") : LibraryCollectionGateway"));
        assertTrue(collectionUseCases.contains("LoadLibraryCollectionsUseCase(operations).execute(selectedPlaylistId)"));
        assertTrue(collectionUseCases.contains("LibraryCollectionsResult("));
        assertFalse(collectionUseCases.contains("internal class SetLibraryFavoriteUseCase"));
        assertFalse(collectionUseCases.contains("fun setFavorite(trackId: Long, favorite: Boolean)"));
        assertFalse(libraryViewModel.contains("LibraryPlayHistoryClearedCallback"));
        assertFalse(libraryViewModel.contains("fun clearPlayHistoryJava("));
        assertTrue(libraryViewModel.contains("interface LibraryImportGateway"));
        assertTrue(libraryViewModel.contains("interface LibraryDocumentGateway"));
        assertTrue(libraryViewModel.contains("fun loadLibrary("));
        assertTrue(libraryViewModel.contains("fun importAudioUris("));
        assertTrue(libraryViewModel.contains("fun importAudioTree("));
        assertTrue(libraryViewModel.contains("fun parseMissingAudioSpecs("));
        assertTrue(libraryViewModel.contains("fun importStreamM3u("));
        assertTrue(libraryViewModel.contains("fun importPlaylistM3u("));
        assertTrue(libraryViewModel.contains("fun exportPlaylist("));
        assertFalse(libraryViewModel.contains("LibraryPlaylistExportCallback"));
        assertTrue(mainActivity.contains("@Inject LibraryImportGateway libraryImportGateway;"));
        assertTrue(mainActivity.contains("libraryViewModel.bindImportGateway(libraryImportGateway);"));
        assertFalse(mainActivity.contains("libraryViewModel.bindImportGateway(new LibraryImportGateway() {"));
        assertFalse(mainActivity.contains("return toLibraryLoadResultUi(new LoadLibraryUseCase(libraryImportOperations).cached());"));
        assertFalse(mainActivity.contains("return toLibraryLoadResultUi(new LoadLibraryUseCase(libraryImportOperations).refresh());"));
        assertFalse(mainActivity.contains("return toLibraryLoadResultUi(new ImportAudioUrisUseCase(libraryImportOperations).execute(uris));"));
        assertFalse(mainActivity.contains("return toLibraryLoadResultUi(new ImportAudioTreeUseCase(libraryImportOperations).execute(treeUri));"));
        assertFalse(mainActivity.contains("AudioSpecsParseResult result = new ParseMissingAudioSpecsUseCase(libraryImportOperations).execute();"));
        assertFalse(mainActivity.contains("private LibraryLoadResultUi toLibraryLoadResultUi("));
        assertFalse(mainActivity.contains("new LibraryImportGatewayBindings("));
        assertTrue(documentGateway.contains("internal class ContentResolverLibraryDocumentGateway("));
        assertTrue(documentGateway.contains(") : LibraryDocumentGateway"));
        assertTrue(documentGateway.contains("LoadLibraryUseCase(operations)"));
        assertTrue(documentGateway.contains("ImportStreamM3uTextUseCase(operations)"));
        assertTrue(documentGateway.contains("ImportPlaylistM3uTextUseCase(operations)"));
        assertTrue(documentGateway.contains("LoadPlaylistExportTracksUseCase(operations)"));
        assertTrue(documentGateway.contains("M3uDocumentHelper.readText(contentResolver, playlistUri)"));
        assertTrue(documentGateway.contains("M3uDocumentHelper.writeText("));
        assertTrue(mainActivity.contains("@Inject LibraryDocumentGateway libraryDocumentGateway;"));
        assertTrue(mainActivity.contains("libraryViewModel.bindDocumentGateway(libraryDocumentGateway);"));
        assertTrue(mainActivity.contains("@Inject LibraryPlaylistActionGateway libraryPlaylistActionGateway;"));
        assertTrue(mainActivity.contains("libraryViewModel.bindPlaylistActionGateway(libraryPlaylistActionGateway);"));
        assertFalse(mainActivity.contains("new LibraryPlaylistActionGateway() {"));
        assertTrue(playlistActionGateway.contains("internal class MainLibraryPlaylistActionGateway("));
        assertTrue(playlistActionGateway.contains(") : LibraryPlaylistActionGateway"));
        assertTrue(playlistActionGateway.contains("AddToDefaultPlaylistUseCase(operations)"));
        assertTrue(playlistActionGateway.contains("LibraryDefaultPlaylistAddResultUi(result.playlistId, result.added)"));
        assertTrue(playlistActionGateway.contains("CreatePlaylistUseCase(operations)"));
        assertTrue(playlistActionGateway.contains("RenamePlaylistUseCase(operations)"));
        assertTrue(playlistActionGateway.contains("DeletePlaylistUseCase(operations)"));
        assertTrue(playlistActionGateway.contains("RemoveTrackFromPlaylistUseCase(operations)"));
        assertTrue(playlistActionGateway.contains("MovePlaylistTrackUseCase(operations)"));
        assertTrue(playlistActionGateway.contains("AddTrackToPlaylistUseCase(operations)"));
        assertTrue(libraryModule.contains("fun provideLibraryCollectionGateway("));
        assertTrue(libraryModule.contains("MainLibraryCollectionGateway(MusicLibraryCollectionOperations(repository))"));
        assertTrue(libraryModule.contains("fun provideLibraryImportGateway("));
        assertTrue(libraryModule.contains("MainLibraryImportGateway(MusicLibraryImportOperations(repository))"));
        assertTrue(libraryModule.contains("fun provideLibraryDocumentGateway("));
        assertTrue(libraryModule.contains("@ApplicationContext context: Context"));
        assertTrue(libraryModule.contains("ContentResolverLibraryDocumentGateway("));
        assertTrue(libraryModule.contains("context.contentResolver"));
        assertTrue(libraryModule.contains("fun provideLibraryPlaylistActionGateway("));
        assertTrue(libraryModule.contains("MusicLibraryPlaylistActionOperations(repository, syncStore)"));
        assertTrue(libraryModule.contains("fun provideMainPlayHistoryActionControllerFactory(): MainPlayHistoryActionControllerFactory"));
        assertTrue(libraryModule.contains("fun provideMainLibraryGatewayFactory(): MainLibraryGatewayFactory"));
        assertTrue(libraryModule.contains("MainLibraryGateway("));
        assertFalse(mainActivity.contains("libraryViewModel.bindDocumentGateway(new ContentResolverLibraryDocumentGateway(getContentResolver(), libraryImportOperations))"));
        assertFalse(mainActivity.contains("new LibraryPlaylistActionGatewayBindings("));
        assertTrue(mainActivity.contains("libraryViewModel.loadLibraryJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importAudioUrisJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importAudioTreeJava("));
        assertTrue(mainActivity.contains("libraryViewModel.parseMissingAudioSpecsJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importStreamM3uJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importPlaylistM3uJava("));
        assertTrue(mainActivity.contains("libraryViewModel.exportPlaylistJava("));
        assertTrue(mainActivity.contains("libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName)"));
        assertFalse(mainActivity.contains("libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName,"));
        assertTrue(documentGateway.contains("importStreamM3uTextUseCase.execute(playlistRead.text)"));
        assertTrue(documentGateway.contains("loadPlaylistExportTracksUseCase.execute(playlistId)"));
        assertFalse(mainActivity.contains("new LoadLibraryUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new ImportAudioUrisUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new ImportAudioTreeUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new ParseMissingAudioSpecsUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new ImportStreamM3uTextUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new ImportPlaylistM3uTextUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new LoadPlaylistExportTracksUseCase(libraryImportOperations);"));
        assertFalse(mainActivity.contains("new AddToDefaultPlaylistUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new CreatePlaylistUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new RenamePlaylistUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new DeletePlaylistUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new AddTrackToPlaylistUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new RemoveTrackFromPlaylistUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new MovePlaylistTrackUseCase(playlistActionOperations);"));
        assertFalse(mainActivity.contains("new AddToDefaultPlaylistUseCase(playlistActionOperations).execute(track);"));
        assertFalse(mainActivity.contains("new CreatePlaylistUseCase(playlistActionOperations).execute(name);"));
        assertFalse(mainActivity.contains("new RenamePlaylistUseCase(playlistActionOperations).execute(playlistId, name);"));
        assertFalse(mainActivity.contains("new DeletePlaylistUseCase(playlistActionOperations).execute(playlistId);"));
        assertFalse(mainActivity.contains("new AddTrackToPlaylistUseCase(playlistActionOperations).execute(playlistId, trackId);"));
        assertFalse(mainActivity.contains("new RemoveTrackFromPlaylistUseCase(playlistActionOperations).execute(playlistId, track);"));
        assertFalse(mainActivity.contains("new MovePlaylistTrackUseCase(playlistActionOperations).execute(playlistId, track, trackIndex, direction);"));
        assertFalse(mainActivity.contains("new MusicLibraryPlaylistActionOperations(repository, streamingPlaylistSyncStore);"));
        assertFalse(mainActivity.contains("new ContentResolverLibraryDocumentGateway(getContentResolver(), libraryImportOperations)"));
        assertFalse(mainActivity.contains("M3uDocumentHelper.readText(getContentResolver(), playlistUri)"));
        assertFalse(mainActivity.contains("M3uDocumentHelper.writeText("));
        assertFalse(mainActivity.contains("repository.refreshFromDevice()"));
        assertFalse(mainActivity.contains("repository.importAudioUris(uris)"));
        assertFalse(mainActivity.contains("repository.parseMissingAudioSpecs()"));
        assertFalse(mainActivity.contains("repository.importM3uTextWithResult("));
        assertTrue(importUseCases.contains("internal interface LibraryImportOperations"));
        assertTrue(importUseCases.contains("internal class LoadLibraryUseCase"));
        assertTrue(importUseCases.contains("internal class ImportAudioUrisUseCase"));
        assertTrue(importUseCases.contains("internal class ImportStreamM3uTextUseCase"));
        assertTrue(importUseCases.contains("internal class LoadPlaylistExportTracksUseCase"));
        assertTrue(importUseCases.contains("internal class MainLibraryImportGateway("));
        assertTrue(importUseCases.contains(") : LibraryImportGateway"));
        assertTrue(importUseCases.contains("LoadLibraryUseCase(operations).cached().toUi()"));
        assertTrue(importUseCases.contains("ParseMissingAudioSpecsUseCase(operations).execute()"));
        assertTrue(importUseCases.contains("LibraryLoadResultUi(tracks, favorites, \"Library updated\")"));
        assertFalse(mainActivity.contains("new MusicLibraryCollectionOperations(repository)"));
        assertFalse(mainActivity.contains("new MusicLibraryImportOperations(repository)"));
        assertFalse(mainActivity.contains("void onCollectionsLoaded(LibraryActionsController.CollectionsSnapshot snapshot)"));
        assertFalse(mainActivity.contains("void onPlayHistoryCleared(int removed)"));
        assertFalse(mainActivity.contains("void onFavoriteSaved()"));
        assertTrue(documentPickerController.contains("private var pendingPlaylistExportId: Long = -1L"));
        assertTrue(documentPickerController.contains("private var pendingPlaylistExportName: String = \"\""));
        assertTrue(documentPickerController.contains("fun openPlaylistExportDocument(playlistId: Long, playlistName: String)"));
        assertTrue(documentPickerController.contains("listener.exportPlaylist(exportUri, playlistId, playlistName)"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistExportBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/PlaylistExportBindings.java"));
        assertFalse(mainActivity.contains("new PlaylistExportController("));
        assertTrue(mainActivity.contains("(playlistId, playlistName) -> documentPickerController.openPlaylistExportDocument(playlistId, playlistName)"));
        assertFalse(mainActivity.contains("exportPlaylist(Uri exportUri, long playlistId, String playlistName)"));
        assertTrue(mainActivity.contains("(exportUri, playlistId, playlistName) -> libraryViewModel.exportPlaylistJava(exportUri, playlistId, playlistName)"));
        assertFalse(mainActivity.contains("new PlaylistExportBindings("));
    }

    @Test
    public void streamingRepositoryProviderUsesInjectableGatewayAndPlaybackAdapter() throws Exception {
        String provider = read("app/src/main/java/app/yukine/StreamingRepositoryProvider.kt");
        String repository = read("app/src/main/java/app/yukine/streaming/StreamingRepository.kt");
        String module = read("app/src/main/java/app/yukine/di/StreamingDataModule.kt");

        assertTrue(provider.contains("StreamingGatewayFactory"));
        assertTrue(provider.contains("StreamingPlaybackTrackAdapter"));
        assertTrue(provider.contains("gatewayFactory.remote(settingsStore.endpoint())"));
        assertFalse(provider.contains("RemoteStreamingGateway("));
        assertTrue(repository.contains("private val playbackTrackAdapter: StreamingPlaybackTrackAdapter"));
        assertTrue(repository.contains("playbackTrackAdapter.toTrack(source, resolvedMetadata)"));
        assertFalse(repository.contains("StreamingPlaybackAdapter.toTrack(source, metadata)"));
        assertTrue(module.contains("provideStreamingGatewayFactory"));
        assertTrue(module.contains("provideStreamingPlaybackTrackAdapter"));
    }

    @Test
    public void playbackServiceUsesInjectableStreamingHeaderStore() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackMediaSourceProvider.kt");
        String module = read("app/src/main/java/app/yukine/di/StreamingDataModule.kt");
        String cacheRepository = read("app/src/main/java/app/yukine/streaming/cache/StreamingCacheRepository.kt");

        assertTrue(service.contains("StreamingPlaybackHeaderStore"));
        assertTrue(service.contains("new PlaybackMediaSourceProvider(this, repository, streamingPlaybackHeaderStore)"));
        assertTrue(owner.contains("fun restoredTrackForPreparation(track: Track?): Track?"));
        assertTrue(owner.contains("fun restoreHeadersForTrack(track: Track?): Boolean"));
        assertTrue(owner.contains("fun restoreHeadersForDataPath(dataPath: String?): Boolean"));
        assertTrue(owner.contains("private fun restorePlaybackHeadersForMediaSource(track: Track?)"));
        assertTrue(owner.contains("StreamingDataPathMetadata.isStreamingTrack(track?.dataPath)"));
        assertTrue(service.contains("private PlaybackCurrentTrackPreparationOwner playbackCurrentTrackPreparationOwner;"));
        assertTrue(service.contains("playbackCurrentTrackPreparationOwner = PlaybackCurrentTrackPreparationOwner.fromMediaSourceProvider("));
        assertTrue(owner.contains("streamingPlaybackHeaderStore.restoredTrackFor(track)"));
        assertTrue(owner.contains("streamingPlaybackHeaderStore.restoreForDataPath(dataPath)"));
        assertFalse(service.contains("mediaSourceProvider::prepareTrackForPlayback"));
        assertFalse(service.contains("PlaybackMediaSourceProvider::unplayableMessageForTrack"));
        assertFalse(service.contains("mediaSourceProvider.restoreHeadersForTrack(track)"));
        assertFalse(service.contains("streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath)"));
        assertFalse(service.contains("streamingPlaybackHeaderStore.restoredTrackFor(track)"));
        assertFalse(service.contains("StreamingPlaybackHeaders.INSTANCE"));
        assertTrue(module.contains("provideStreamingPlaybackHeaderStore"));
        assertTrue(cacheRepository.contains("fun cachedPlaybackBlocking(provider: StreamingProviderName, providerTrackId: String): String?"));
        assertTrue(cacheRepository.contains("dao.playbackBlocking(provider.wireName, providerTrackId, clock())?.payloadJson"));
        assertTrue(cacheRepository.contains("runBlocking(Dispatchers.IO)"));
    }

    @Test
    public void streamingActionsLiveInMainActivityViewModelAndGateway() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String handlers = read("app/src/main/java/app/yukine/StreamingActionHandlers.kt");
        String importUseCase = read("app/src/main/java/app/yukine/ImportStreamingPlaylistUseCase.kt");
        String streamingModule = read("app/src/main/java/app/yukine/StreamingModule.kt");
        String libraryModule = read("app/src/main/java/app/yukine/LibraryModule.kt");
        String mainStreamingLocalPlaylistOperations = read("app/src/main/java/app/yukine/MainStreamingLocalPlaylistOperations.kt");
        String resolveUseCase = read("app/src/main/java/app/yukine/ResolveStreamingPlaybackUseCase.kt");
        String syncUseCase = read("app/src/main/java/app/yukine/SyncStreamingPlaylistUseCase.kt");
        String linkUseCase = read("app/src/main/java/app/yukine/GetStreamingPlaylistLinkUseCase.kt");
        String loginPlaylistUseCase = read("app/src/main/java/app/yukine/EnsureStreamingLoginPlaylistUseCase.kt");
        String trackMatchUseCase = read("app/src/main/java/app/yukine/StreamingTrackMatchUseCase.kt");
        String toggleFavoriteUseCase = read("app/src/main/java/app/yukine/ToggleFavoriteUseCase.kt");
        String loadPlaylistTracksUseCase = read("app/src/main/java/app/yukine/LoadPlaylistTracksUseCase.kt");
        String loadLyricsSettingsUseCase = read("app/src/main/java/app/yukine/LoadLyricsSettingsUseCase.kt");
        String loadTrackLyricsUseCase = read("app/src/main/java/app/yukine/LoadTrackLyricsUseCase.kt");
        String lyricsViewModel = read("app/src/main/java/app/yukine/LyricsViewModel.kt");
        String networkActionsViewModel = read("app/src/main/java/app/yukine/NetworkActionsViewModel.kt");
        String mainActivityViewModel = read("app/src/main/java/app/yukine/MainActivityViewModel.kt");
        String playlistsRenderController = read("app/src/main/java/app/yukine/LibraryPlaylistsRenderController.kt");
        String streamingPlaybackController = read("app/src/main/java/app/yukine/StreamingPlaybackController.kt");
        String mainStreamingPlaybackListener = read("app/src/main/java/app/yukine/MainStreamingPlaybackListener.kt");
        String mainNowPlayingStateListener = read("app/src/main/java/app/yukine/MainNowPlayingStateListener.kt");
        String playbackUiModule = read("app/src/main/java/app/yukine/PlaybackUiModule.kt");
        String streamingPlaylistController = read("app/src/main/java/app/yukine/StreamingPlaylistController.kt");
        String mainStreamingPlaylistListener = read("app/src/main/java/app/yukine/MainStreamingPlaylistListener.kt");
        String streamingPlaylistDialogController = read("app/src/main/java/app/yukine/StreamingPlaylistDialogController.java");
        String mainStreamingPlaylistDialogListener = read("app/src/main/java/app/yukine/MainStreamingPlaylistDialogListener.kt");
        String streamingPlaylistImportDialogController = read("app/src/main/java/app/yukine/StreamingPlaylistImportDialogController.java");
        String mainStreamingPlaylistImportDialogListener = read("app/src/main/java/app/yukine/MainStreamingPlaylistImportDialogListener.kt");
        String streamingManualCookieController = read("app/src/main/java/app/yukine/StreamingManualCookieController.kt");
        String mainStreamingManualCookieListener = read("app/src/main/java/app/yukine/MainStreamingManualCookieListener.kt");
        String streamingManualCookieDialogController = read("app/src/main/java/app/yukine/StreamingManualCookieDialogController.java");
        String luoxueSourceImportController = read("app/src/main/java/app/yukine/LuoxueSourceImportController.kt");
        String luoxueSourceImportDialogController = read("app/src/main/java/app/yukine/LuoxueSourceImportDialogController.java");
        String defaultStreamingSearchActionHandler = read("app/src/main/java/app/yukine/DefaultStreamingSearchActionHandler.kt");
        String streamingViewModel = read("app/src/main/java/app/yukine/StreamingViewModel.kt");
        String streamingRecommendationViewModel = read("app/src/main/java/app/yukine/StreamingRecommendationViewModel.kt");
        String heartbeatRecommendationController = read("app/src/main/java/app/yukine/HeartbeatRecommendationController.kt");
        String streamingAuthCallbackController = read("app/src/main/java/app/yukine/StreamingAuthCallbackController.kt");
        String mainStreamingActionGateway = read("app/src/main/java/app/yukine/MainStreamingActionGateway.kt");

        assertFalse(exists("app/src/main/java/app/yukine/StreamingActionsController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingResolvedPlaybackController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingResolvedPlaybackController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaybackController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaybackBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaybackBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaylistController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaylistBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaylistBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaylistDialogController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingPlaylistImportDialogController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingManualCookieController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingManualCookieBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingManualCookieBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingManualCookieDialogController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LuoxueSourceImportController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LuoxueSourceImportDialogController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchActionHandlerBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchActionHandlerBindings.java"));
        assertFalse(exists("app/src/test/java/app/yukine/StreamingSearchActionHandlerBindingsTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingAuthCallbackBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingAuthCallbackBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingActionGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingActionGatewayBindings.java"));
        assertTrue(handlers.contains("internal interface StreamingSearchActionHandler"));
        assertTrue(handlers.contains("interface MainActivityStreamingActionGateway"));
        assertFalse(mainActivity.contains("new StreamingActionGatewayBindings("));
        assertTrue(mainActivity.contains("@Inject MainStreamingActionGatewayFactory streamingActionGatewayFactory;"));
        assertTrue(mainActivity.contains("return streamingActionGatewayFactory.create("));
        assertTrue(mainActivity.contains("this::adaptiveStreamingQuality"));
        assertTrue(mainActivity.contains("() -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()"));
        assertTrue(mainActivity.contains("StreamingAuthLauncher.INSTANCE.launch(MainActivityBase.this, launch)"));
        assertTrue(mainActivity.contains("(tracks, index) -> playTrackListFromHost(tracks, index)"));
        assertTrue(mainActivity.contains("provider -> streamingPlaylistController.onStreamingLoginSuccess(provider)"));
        assertTrue(mainActivity.contains("provider -> streamingViewModel.selectStreamingProvider(provider)"));
        assertTrue(mainActivity.contains("streamingManualCookieController.showStreamingCookieDialog();"));
        assertFalse(mainActivity.contains("new MainActivityStreamingActionGateway()"));
        assertTrue(mainStreamingActionGateway.contains("internal fun interface MainStreamingActionGatewayFactory"));
        assertTrue(mainStreamingActionGateway.contains("internal class MainStreamingActionGateway("));
        assertTrue(mainStreamingActionGateway.contains(") : MainActivityStreamingActionGateway"));
        assertTrue(mainStreamingActionGateway.contains("return qualityProvider.streamingPlaybackQuality()"));
        assertTrue(mainStreamingActionGateway.contains("return languageModeProvider.languageMode()"));
        assertTrue(mainStreamingActionGateway.contains("return authLauncher.openAuthLaunch(launch)"));
        assertTrue(mainStreamingActionGateway.contains("trackPlayer.playTrackList(listOf(track), 0)"));
        assertTrue(mainStreamingActionGateway.contains("loginSuccessHandler.onStreamingLoginSuccess(provider)"));
        assertTrue(mainStreamingActionGateway.contains("providerSelector.selectStreamingProvider(provider)"));
        assertTrue(mainStreamingActionGateway.contains("manualCookiePresenter.showStreamingCookieDialog()"));
        assertFalse(mainActivity.contains("private void openManualStreamingCookieImport("));
        assertTrue(defaultStreamingSearchActionHandler.contains("internal fun interface MainStreamingSearchActionHandlerFactory"));
        assertTrue(defaultStreamingSearchActionHandler.contains("internal class DefaultStreamingSearchActionHandler("));
        assertTrue(defaultStreamingSearchActionHandler.contains(") : StreamingSearchActionHandler"));
        assertTrue(defaultStreamingSearchActionHandler.contains("private val streamingViewModel: StreamingViewModel"));
        assertTrue(defaultStreamingSearchActionHandler.contains("private val actionGateway: MainActivityStreamingActionGateway"));
        assertFalse(defaultStreamingSearchActionHandler.contains("MainActivityViewModel"));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.selectStreamingProvider(provider)"));
        assertTrue(streamingViewModel.contains("@HiltViewModel"));
        assertTrue(streamingViewModel.contains("private val streamingRepositorySource: StreamingRepositorySource"));
        assertTrue(streamingViewModel.contains("fun bindStreamingRepository(repository: StreamingRepository)"));
        assertTrue(streamingViewModel.contains("fun configureStreamingRepository(): Job"));
        assertTrue(streamingViewModel.contains("fun clearExpiredStreamingCache()"));
        assertTrue(streamingViewModel.contains("fun searchStreaming("));
        assertTrue(streamingViewModel.contains("fun searchNextStreamingPage()"));
        assertTrue(streamingViewModel.contains("fun startStreamingAuth("));
        assertTrue(streamingViewModel.contains("fun signOutStreaming(provider: StreamingProviderName): Job"));
        assertTrue(streamingViewModel.contains("fun completeStreamingAuth("));
        assertTrue(streamingViewModel.contains("fun resolveStreamingPlaybackTrack("));
        assertTrue(streamingViewModel.contains("streamingRepository.resolvePlaybackTrack(provider, providerTrackId, quality, metadata)"));
        assertTrue(streamingViewModel.contains("streamingRepository.search(provider, query, normalizedMediaTypes, page, pageSize)"));
        assertTrue(streamingViewModel.contains("fun loadUserPlaylists(provider: StreamingProviderName): Job"));
        assertTrue(streamingViewModel.contains("streamingRepository.userPlaylists(provider)"));
        assertTrue(streamingViewModel.contains("fun fetchUserLikedTracks("));
        assertTrue(streamingViewModel.contains("streamingRepository.userLikedTracks(provider)"));
        assertTrue(streamingViewModel.contains("fun fetchDailyRecommendations("));
        assertTrue(streamingViewModel.contains("streamingRepository.dailyRecommendations(provider)"));
        assertFalse(streamingViewModel.contains("fun fetchHeartbeatRecommendations("));
        assertTrue(streamingRecommendationViewModel.contains("override fun fetchHeartbeatRecommendations("));
        assertTrue(streamingRecommendationViewModel.contains("repository.heartbeatRecommendations("));
        assertTrue(streamingViewModel.contains("fun fetchStreamingPlaylistTracks("));
        assertTrue(streamingViewModel.contains("suspend fun loadStreamingPlaylistTracks("));
        assertTrue(streamingViewModel.contains("streamingRepository.playlist("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.loadUserPlaylists(provider)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.fetchUserLikedTracks(provider, onResolved)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.fetchDailyRecommendations(provider, onResolved)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.fetchHeartbeatRecommendations(provider, providerTrackId, providerPlaylistId, onResolved)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.fetchStreamingPlaylistTracks(provider, providerPlaylistId, onResolved)"));
        assertFalse(mainActivityViewModel.contains("streamingRepository.userPlaylists(provider)"));
        assertFalse(mainActivityViewModel.contains("streamingRepository.dailyRecommendations(provider)"));
        assertFalse(mainActivityViewModel.contains("streamingRepository.heartbeatRecommendations("));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.searchAllStreaming("));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.signOutStreaming(provider)"));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.resolveStreamingPlaybackTrack("));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.searchNextStreamingPage()"));
        assertTrue(defaultStreamingSearchActionHandler.contains("StreamingCapabilityResolver.canAuth"));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.failStreamingRequest("));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.startStreamingAuth("));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.login(provider)"));
        assertTrue(defaultStreamingSearchActionHandler.contains("actionGateway.openAuthLaunch(streamingViewModel.state.pendingAuthLaunch)"));
        assertTrue(defaultStreamingSearchActionHandler.contains("streamingViewModel.clearStreamingAuthLaunch()"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.search(query)"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.signOut(provider)"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.playStreamingTrack(track)"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.loadNextPage()"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.searchStreaming("));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.searchNextStreamingPage()"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.startStreamingAuth("));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.signOutStreaming(provider)"));
        assertFalse(defaultStreamingSearchActionHandler.contains("viewModel.resolveStreamingPlaybackTrack("));
        assertTrue(defaultStreamingSearchActionHandler.contains("actionGateway.playResolvedTrack(track)"));
        assertTrue(streamingAuthCallbackController.contains("internal class StreamingAuthCallbackController"));
        assertTrue(streamingAuthCallbackController.contains("private val streamingViewModel: StreamingViewModel"));
        assertTrue(streamingAuthCallbackController.contains("private val actionGateway: MainActivityStreamingActionGateway"));
        assertTrue(streamingAuthCallbackController.contains("URI(it)"));
        assertTrue(streamingAuthCallbackController.contains("StreamingProviderName::fromWireName"));
        assertTrue(streamingAuthCallbackController.contains("streamingViewModel.completeStreamingAuth(provider, uri.toString(), cookieHeader)"));
        assertTrue(streamingAuthCallbackController.contains("actionGateway.onStreamingLoginSuccess(loggedInProvider)"));
        assertTrue(streamingAuthCallbackController.contains("streamingViewModel.clearStreamingAuthLaunch()"));
        assertTrue(mainActivity.contains("streamingSearchActionHandler = streamingSearchActionHandlerFactory.create(streamingViewModel, streamingActionGateway);"));
        assertFalse(mainActivity.contains("streamingSearchActionHandler = new DefaultStreamingSearchActionHandler(streamingViewModel, streamingActionGateway);"));
        assertFalse(mainActivity.contains("new StreamingSearchActionHandlerBindings(viewModel, streamingViewModel"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchEventController.kt"));
        assertTrue(mainActivity.contains("new StreamingAuthCallbackController(\r\n                streamingViewModel,\r\n                streamingActionGateway\r\n        )")
                || mainActivity.contains("new StreamingAuthCallbackController(\n                streamingViewModel,\n                streamingActionGateway\n        )"));
        assertFalse(mainActivity.contains("new StreamingAuthCallbackBindings(viewModel, streamingViewModel"));
        assertFalse(mainActivity.contains("new StreamingSearchEventController(\n                viewModel,"));
        assertFalse(mainActivity.contains("new StreamingSearchEventController(\r\n                viewModel,"));
        assertFalse(mainActivity.contains("new StreamingAuthCallbackController(viewModel)"));
        assertTrue(mainActivityViewModel.contains(": ViewModel()"));
        assertFalse(mainActivityViewModel.contains(": ViewModel(), StreamingSearchActionHandler"));
        assertFalse(mainActivityViewModel.contains(": ViewModel(), StreamingSearchActionHandler, StreamingAuthCallbackHandler"));
        assertFalse(mainActivityViewModel.contains("override fun selectProvider("));
        assertFalse(mainActivityViewModel.contains("override fun handleAuthCallback("));
        assertFalse(mainActivityViewModel.contains("fun handleAuthCallback(callbackUri: String?, cookieHeader: String?)"));
        assertFalse(mainActivityViewModel.contains("URI(it)"));
        assertFalse(mainActivityViewModel.contains("queryParameter(uri.rawQuery, \"provider\")"));
        assertFalse(mainActivityViewModel.contains("fun search(query: String)"));
        assertFalse(mainActivityViewModel.contains("fun searchStreaming("));
        assertFalse(mainActivityViewModel.contains("fun searchNextStreamingPage()"));
        assertFalse(mainActivityViewModel.contains("fun startStreamingAuth("));
        assertFalse(mainActivityViewModel.contains("fun signOutStreaming(provider: StreamingProviderName)"));
        assertFalse(mainActivityViewModel.contains("fun completeStreamingAuth("));
        assertFalse(mainActivityViewModel.contains("fun login(provider: StreamingProviderName)"));
        assertFalse(mainActivityViewModel.contains("fun signOut(provider: StreamingProviderName)"));
        assertFalse(mainActivityViewModel.contains("fun playStreamingTrack(track: StreamingTrack)"));
        assertFalse(mainActivityViewModel.contains("fun loadNextPage()"));
        assertFalse(mainActivityViewModel.contains("fun openAuthLaunch()"));
        assertFalse(mainActivity.contains("viewModel.bindStreamingActionGateway("));
        assertFalse(mainActivityViewModel.contains("StreamingCapabilityResolver.canSearch"));
        assertFalse(mainActivityViewModel.contains("StreamingCapabilityResolver.canPlayback"));
        assertFalse(mainActivityViewModel.contains("sourceMessage(descriptor, \"streaming.search.unavailable\")"));
        assertFalse(mainActivityViewModel.contains("sourceMessage(descriptor, \"streaming.auth.unsupported\")"));
        assertFalse(mainActivityViewModel.contains("sourceMessage(descriptor, \"streaming.playback.unsupported\")"));
        assertTrue(defaultStreamingSearchActionHandler.contains("text(\"streaming.track.unavailable\")"));
        assertFalse(mainActivityViewModel.contains("text(\"streaming.track.unavailable\")"));
        assertTrue(mainActivityViewModel.contains("data class StreamingManualCookieDialogState"));
        assertTrue(mainActivityViewModel.contains("data class StreamingManualCookieAuthRequest"));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistImportDialogState("));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistImportStartRequest("));
        assertFalse(mainActivityViewModel.contains("fun prepareManualCookieDialogState("));
        assertFalse(mainActivityViewModel.contains("fun prepareManualCookieAuthRequest("));
        assertFalse(mainActivityViewModel.contains("fun manualCookieEmptyStatus("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistImportDialogState("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistImportStartRequest("));
        assertTrue(streamingViewModel.contains("fun prepareManualCookieDialogState("));
        assertTrue(streamingViewModel.contains("fun prepareManualCookieAuthRequest("));
        assertTrue(streamingViewModel.contains("fun manualCookieEmptyStatus("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistImportDialogState("));
        assertTrue(streamingViewModel.contains("manualCookie=1"));
        assertFalse(mainActivityViewModel.contains("callbackUri = \"$STREAMING_AUTH_REDIRECT_URI?provider=${cleanProvider.wireName}&manualCookie=1\""));
        assertFalse(mainActivityViewModel.contains("streamingActionGateway?.playResolvedTrack(track)"));
        assertFalse(mainActivityViewModel.contains("\u93c6\u5099\u7b09"));
        assertFalse(mainActivityViewModel.contains("\u5a34\u4f78\u735f"));
        assertFalse(mainActivity.contains("new MainActivityStreamingActionGateway()"));
        assertFalse(mainActivity.contains("viewModel.bindStreamingActionGateway(new ActivityStreamingActionGateway())"));
        assertFalse(mainActivity.contains("private final class ActivityStreamingActionGateway implements MainActivityStreamingActionGateway"));
        assertTrue(mainActivity.contains("StreamingAuthLauncher.INSTANCE.launch(MainActivityBase.this, launch)"));
        assertTrue(mainActivity.contains("new StreamingPlaylistDialogController("));
        assertTrue(mainActivity.contains("streamingPlaylistDialogListenerFactory.create("));
        assertFalse(mainActivity.contains("new StreamingPlaylistDialogController.Listener()"));
        assertTrue(mainActivity.contains("streamingPlaylistControllerRef[0].runStreamingPlaylistImport(provider, playlistName, tracks);"));
        assertTrue(mainActivity.contains("streamingPlaylistControllerRef[0].importSelectedAccountPlaylists(provider, playlists);"));
        assertTrue(mainActivity.contains("streamingPlaylistControllerRef[0].importStreamingLikedTracks(provider);"));
        assertTrue(mainActivity.contains("() -> streamingPlaylistDialogController.showImportStreamingFavoritesProviderPicker()"));
        assertTrue(mainActivity.contains("new StreamingPlaylistImportDialogController("));
        assertTrue(mainActivity.contains("streamingPlaylistImportDialogController.showImportDialog()"));
        assertTrue(mainActivity.contains("streamingPlaylistController.importStreamingPlaylistFromLink(linkOrId)"));
        assertTrue(mainActivity.contains("new StreamingManualCookieDialogController("));
        assertTrue(mainActivity.contains("streamingManualCookieController = new StreamingManualCookieController("));
        assertFalse(mainActivity.contains("new StreamingManualCookieController.Listener()"));
        assertTrue(mainActivity.contains("streamingManualCookieListenerFactory.create("));
        assertTrue(mainActivity.contains("streamingManualCookieController.showStreamingCookieDialog();"));
        assertTrue(mainActivity.contains("streamingManualCookieDialogController.show(dialogState)"));
        assertTrue(mainActivity.contains("streamingPlaylistController.onStreamingLoginSuccess(provider)"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(status)"));
        assertTrue(mainActivity.contains("(provider, cookieHeader) -> streamingManualCookieController.saveStreamingCookie(provider, cookieHeader)"));
        assertTrue(streamingManualCookieController.contains("internal class StreamingManualCookieController("));
        assertTrue(streamingManualCookieController.contains("private val streamingViewModel: StreamingViewModel"));
        assertTrue(streamingManualCookieController.contains("fun interface LanguageProvider"));
        assertFalse(streamingManualCookieController.contains("MainActivityViewModel"));
        assertTrue(streamingManualCookieController.contains("fun showStreamingCookieDialog()"));
        assertTrue(streamingManualCookieController.contains("streamingViewModel.prepareManualCookieDialogState("));
        assertTrue(streamingManualCookieController.contains("fun saveStreamingCookie(provider: StreamingProviderName, cookieHeader: String?)"));
        assertTrue(streamingManualCookieController.contains("streamingViewModel.prepareManualCookieAuthRequest(provider, cookieHeader, languageMode)"));
        assertTrue(streamingManualCookieController.contains("streamingViewModel.manualCookieEmptyStatus(languageMode)"));
        assertTrue(streamingManualCookieController.contains("streamingViewModel.completeStreamingAuth(request.provider, request.callbackUri, request.cookieHeader)"));
        assertTrue(mainStreamingManualCookieListener.contains("internal class MainStreamingManualCookieListener("));
        assertTrue(mainStreamingManualCookieListener.contains(": StreamingManualCookieController.Listener"));
        assertTrue(mainStreamingManualCookieListener.contains("selectedProviderSource.selectedProvider()"));
        assertTrue(mainStreamingManualCookieListener.contains("dialogPresenter.showManualCookieDialog(dialogState)"));
        assertTrue(mainStreamingManualCookieListener.contains("loginSuccessHandler.onStreamingLoginSuccess(provider)"));
        assertTrue(mainStreamingManualCookieListener.contains("statusSink.setStatus(status)"));
        assertTrue(streamingManualCookieDialogController.contains("final class StreamingManualCookieDialogController"));
        assertTrue(streamingManualCookieDialogController.contains("void show(StreamingManualCookieDialogState dialogState)"));
        assertTrue(streamingManualCookieDialogController.contains("input.setMinLines(3)"));
        assertTrue(streamingManualCookieDialogController.contains("confirmAction.save(dialogState.getProvider(), input.getText().toString())"));
        assertTrue(streamingPlaylistDialogController.contains("final class StreamingPlaylistDialogController"));
        assertTrue(streamingPlaylistDialogController.contains("void showStreamingProviderPicker(String playlistName, List<Track> tracks)"));
        assertTrue(streamingPlaylistDialogController.contains("void showStreamingPlaylistLoadedDialog(String message)"));
        assertTrue(streamingPlaylistDialogController.contains("void showAccountPlaylistImportPicker(StreamingProviderName provider, List<StreamingPlaylist> playlists)"));
        assertTrue(streamingPlaylistDialogController.contains("void showImportStreamingFavoritesProviderPicker()"));
        assertTrue(streamingPlaylistDialogController.contains("streamingViewModel.prepareStreamingImportProviderPickerRequest("));
        assertTrue(streamingPlaylistDialogController.contains("streamingViewModel.streamingPlaylistLoadedDialogTitle(languageMode())"));
        assertTrue(streamingPlaylistDialogController.contains("new CheckBox(context)"));
        assertTrue(streamingPlaylistDialogController.contains("listener.importSelectedAccountPlaylists(provider, selected)"));
        assertTrue(streamingPlaylistDialogController.contains("listener.runStreamingPlaylistImport(descriptor.getName(), playlistName, tracks)"));
        assertTrue(streamingPlaylistDialogController.contains("listener.importStreamingLikedTracks(request.getPickerState().getProviders().get(which).getName())"));
        assertTrue(mainStreamingPlaylistDialogListener.contains("internal class MainStreamingPlaylistDialogListener("));
        assertTrue(mainStreamingPlaylistDialogListener.contains(": StreamingPlaylistDialogController.Listener"));
        assertTrue(mainStreamingPlaylistDialogListener.contains("statusSink.setStatus(status)"));
        assertTrue(mainStreamingPlaylistDialogListener.contains("playlistImportRunner.runStreamingPlaylistImport(provider, playlistName, tracks)"));
        assertTrue(mainStreamingPlaylistDialogListener.contains("accountPlaylistImportSink.importSelectedAccountPlaylists(provider, playlists)"));
        assertTrue(mainStreamingPlaylistDialogListener.contains("likedTracksImportSink.importStreamingLikedTracks(provider)"));
        assertTrue(mainStreamingPlaylistListener.contains("internal class MainStreamingPlaylistListener("));
        assertTrue(mainStreamingPlaylistListener.contains(": StreamingPlaylistController.Listener"));
        assertTrue(mainStreamingPlaylistListener.contains("playlistIdSource.selectedPlaylistId()"));
        assertTrue(mainStreamingPlaylistListener.contains("playlistIdSink.setSelectedPlaylistId(playlistId)"));
        assertTrue(mainStreamingPlaylistListener.contains("providerPickerPresenter.showStreamingProviderPicker(playlistName, tracks)"));
        assertTrue(mainStreamingPlaylistListener.contains("accountPlaylistPickerPresenter.showAccountPlaylistImportPicker(provider, playlists)"));
        assertTrue(mainStreamingPlaylistListener.contains("statusSink.setStatus(status)"));
        assertTrue(mainStreamingPlaylistListener.contains("selectedTabRenderer.renderSelectedTab()"));
        assertTrue(streamingPlaylistImportDialogController.contains("final class StreamingPlaylistImportDialogController"));
        assertTrue(streamingPlaylistImportDialogController.contains("void showImportDialog()"));
        assertTrue(streamingPlaylistImportDialogController.contains("listener.selectedProvider() == StreamingProviderName.LUOXUE"));
        assertTrue(streamingPlaylistImportDialogController.contains("listener.showLuoxueSourceImportDialog()"));
        assertTrue(streamingPlaylistImportDialogController.contains("streamingViewModel.prepareStreamingPlaylistImportDialogState(languageProvider.languageMode())"));
        assertTrue(streamingPlaylistImportDialogController.contains("input.setSingleLine(true)"));
        assertTrue(streamingPlaylistImportDialogController.contains("listener.importStreamingPlaylistFromLink(input.getText().toString())"));
        assertFalse(mainActivity.contains("new StreamingPlaylistImportDialogController.Listener()"));
        assertTrue(mainActivity.contains("streamingPlaylistImportDialogListenerFactory.create("));
        assertTrue(mainStreamingPlaylistImportDialogListener.contains("internal class MainStreamingPlaylistImportDialogListener("));
        assertTrue(mainStreamingPlaylistImportDialogListener.contains(": StreamingPlaylistImportDialogController.Listener"));
        assertTrue(mainStreamingPlaylistImportDialogListener.contains("selectedProviderSource.selectedProvider()"));
        assertTrue(mainStreamingPlaylistImportDialogListener.contains("luoxueDialogPresenter.showLuoxueSourceImportDialog()"));
        assertTrue(mainStreamingPlaylistImportDialogListener.contains("playlistLinkImportSink.importStreamingPlaylistFromLink(linkOrId)"));
        assertFalse(mainActivity.contains("private void showStreamingCookieDialogContent("));
        assertFalse(mainActivity.contains("streamingManualCookieController.saveStreamingCookie(dialogState.getProvider(), input.getText().toString())"));
        assertTrue(luoxueSourceImportController.contains("internal class LuoxueSourceImportController("));
        assertTrue(luoxueSourceImportController.contains("fun openFilePicker()"));
        assertTrue(luoxueSourceImportController.contains("fun importSelectedUris(uris: List<Uri>?)"));
        assertTrue(luoxueSourceImportController.contains("fun importFromUrls(rawUrls: String?)"));
        assertTrue(luoxueSourceImportController.contains("LuoxueSourceImporter.parseMany"));
        assertTrue(luoxueSourceImportController.contains("sourceStore.saveAll(cleanSources)"));
        assertTrue(luoxueSourceImportController.contains("completionAction.onImportComplete()"));
        assertTrue(luoxueSourceImportController.contains("internal class ContentResolverLuoxueSourceDocumentReader("));
        assertTrue(luoxueSourceImportController.contains("M3uDocumentHelper.readText(contentResolver, uri)"));
        assertFalse(exists("app/src/main/java/app/yukine/LuoxueSourceImportBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LuoxueSourceImportBindings.java"));
        assertTrue(luoxueSourceImportDialogController.contains("final class LuoxueSourceImportDialogController"));
        assertTrue(luoxueSourceImportDialogController.contains("void showImportDialog()"));
        assertTrue(luoxueSourceImportDialogController.contains("importController.openFilePicker()"));
        assertTrue(luoxueSourceImportDialogController.contains("importController.importFromUrls(input.getText().toString())"));
        assertTrue(mainActivity.contains("new LuoxueSourceImportController("));
        assertTrue(mainActivity.contains("new ContentResolverLuoxueSourceDocumentReader(getContentResolver())"));
        assertTrue(mainActivity.contains("new LuoxueSourceImportDialogController("));
        assertTrue(mainActivity.contains("luoxueSourceImportDialogController.showImportDialog()"));
        assertFalse(mainActivity.contains("private void openLuoxueSourceFilePicker()"));
        assertFalse(mainActivity.contains("private void importSelectedLuoxueSourceUris("));
        assertFalse(mainActivity.contains("private void showLuoxueSourceUrlDialog()"));
        assertFalse(mainActivity.contains("private void importLuoxueSourcesFromUrls("));
        assertFalse(mainActivity.contains("private void saveImportedLuoxueSources("));
        assertFalse(mainActivity.contains("private void showLuoxueSourceImportDialog()"));
        assertFalse(mainActivity.contains("LuoxueSourceImporter.INSTANCE"));
        assertFalse(mainActivity.contains("M3uDocumentHelper.readText(getContentResolver(), uri)"));
        assertFalse(mainActivity.contains("viewModel.prepareManualCookieDialogState("));
        assertFalse(mainActivity.contains("viewModel.prepareManualCookieAuthRequest(provider, cookieHeader)"));
        assertFalse(mainActivity.contains("viewModel.manualCookieEmptyStatus()"));
        assertFalse(mainActivity.contains("viewModel.completeStreamingAuth(request.getProvider(), request.getCallbackUri(), request.getCookieHeader()"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistImportDialogState()"));
        assertFalse(mainActivity.contains("streamingViewModel.prepareStreamingPlaylistImportDialogState(settingsStore.languageMode())"));
        assertFalse(mainActivity.contains("private void showImportStreamingPlaylistDialog()"));
        assertFalse(mainActivity.contains("streamingPlaylistController.importStreamingPlaylistFromLink(input.getText().toString())"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistImportStartRequest("));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistImportStartRequest("));
        assertFalse(mainActivity.contains("MainActivity.this.playTrackList(tracks, 0)"));
        assertFalse(mainActivity.contains("StreamingResolvedPlaybackController"));
        assertFalse(mainActivity.contains("StreamingActionsController"));
        assertFalse(mainActivity.contains("STREAMING_AUTH_REDIRECT_URI"));
        assertFalse(mainActivity.contains("provider == app.yukine.streaming.StreamingProviderName.MOCK"));
        assertFalse(mainActivity.contains("provider == app.yukine.streaming.StreamingProviderName.M3U8"));
        assertFalse(mainActivity.contains("provider == app.yukine.streaming.StreamingProviderName.PLUGIN"));
        assertFalse(mainActivity.contains("cookieHeader == null ? \"\" : cookieHeader.trim()"));
        assertFalse(mainActivity.contains("\"&manualCookie=1\""));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.manual.cookie\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.cookie.empty\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.cookie.saved\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.playlist.link.invalid\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.paste.playlist.link\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.import.playlist.from\")"));
        assertFalse(mainActivity.contains("input.setHint(\"MUSIC_U=...; os=pc; appver=...\")"));
        assertTrue(importUseCase.contains("internal class ImportStreamingPlaylistUseCase"));
        assertTrue(importUseCase.contains("StreamingPlaybackAdapter.placeholderTrack"));
        assertTrue(importUseCase.contains("operations.importStreamingPlaylist(playlistName, placeholders)"));
        assertTrue(importUseCase.contains("operations.linkPlaylist(result.playlistId, provider, cleanProviderPlaylistId)"));
        assertTrue(streamingModule.contains("fun provideImportStreamingPlaylistUseCase("));
        assertTrue(streamingModule.contains("fun provideStreamingPlaylistSyncStore(@ApplicationContext context: Context): StreamingPlaylistSyncStore"));
        assertTrue(streamingModule.contains("ImportStreamingPlaylistUseCase(MusicLibraryStreamingPlaylistImportOperations(repository, syncStore))"));
        assertTrue(streamingModule.contains("fun provideStreamingLocalPlaylistOperations("));
        assertTrue(streamingModule.contains("MainStreamingLocalPlaylistOperations("));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("internal class MainStreamingLocalPlaylistOperations("));
        assertTrue(mainStreamingLocalPlaylistOperations.contains(": StreamingLocalPlaylistOperations"));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("importStreamingPlaylistUseCase.execute("));
        assertTrue(mainActivity.contains("@Inject StreamingLocalPlaylistOperations streamingLocalPlaylistOperations;"));
        assertFalse(mainActivity.contains("@Inject StreamingPlaylistSyncStore streamingPlaylistSyncStore;"));
        assertTrue(libraryModule.contains("syncStore: StreamingPlaylistSyncStore"));
        assertTrue(mainActivity.contains("streamingViewModel.bindStreamingLocalPlaylistOperations(streamingLocalPlaylistOperations);"));
        assertFalse(mainActivity.contains("streamingViewModel.bindStreamingLocalPlaylistOperations(new StreamingLocalPlaylistOperations() {"));
        assertFalse(mainActivity.contains("new app.yukine.streaming.StreamingPlaylistSyncStore(this)"));
        assertFalse(mainActivity.contains("new ImportStreamingPlaylistUseCase("));
        assertTrue(mainActivityViewModel.contains("interface StreamingLocalPlaylistOperations"));
        assertTrue(mainActivityViewModel.contains("data class StreamingProviderPickerState"));
        assertTrue(mainActivityViewModel.contains("data class StreamingProviderPickerRequest("));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistImportStatus"));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistExportPresentation("));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistImportTarget"));
        assertFalse(mainActivityViewModel.contains("fun streamingImportProviderPickerState("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingImportProviderPickerRequest("));
        assertFalse(mainActivityViewModel.contains("fun streamingPlaylistImportStatus("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistExportPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistImportTarget("));
        assertTrue(streamingViewModel.contains("fun streamingImportProviderPickerState("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingImportProviderPickerRequest("));
        assertTrue(streamingViewModel.contains("fun streamingPlaylistImportStatus("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistExportPresentation("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistExportRequest("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingFavoritesExportRequest("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistImportTarget("));
        assertTrue(streamingViewModel.contains("StreamingPlaylistLinkParser.parse("));
        assertFalse(mainActivityViewModel.contains("StreamingPlaylistLinkParser.parse("));
        assertFalse(mainActivityViewModel.contains("fun importStreamingPlaylistToLocal("));
        assertFalse(mainActivityViewModel.contains("fun importStreamingLikedTracksToLocal("));
        assertTrue(streamingViewModel.contains("private var streamingLocalPlaylistOperations: StreamingLocalPlaylistOperations?"));
        assertTrue(streamingViewModel.contains("fun bindStreamingLocalPlaylistOperations(operations: StreamingLocalPlaylistOperations?)"));
        assertTrue(streamingViewModel.contains("fun importStreamingPlaylistToLocal("));
        assertTrue(streamingViewModel.contains("fun importStreamingLikedTracksToLocal("));
        assertTrue(streamingViewModel.contains("private suspend fun importStreamingTracksToLocal("));
        assertTrue(streamingViewModel.contains("operations.importStreamingPlaylist("));
        assertTrue(streamingViewModel.contains("withContext(ioDispatcher)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId, onImported)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.importStreamingLikedTracksToLocal(provider, playlistName, onImported)"));
        assertFalse(mainActivityViewModel.contains("private suspend fun importStreamingTracksToLocal("));
        assertFalse(mainActivityViewModel.contains("operations.importStreamingPlaylist("));
        assertFalse(mainActivity.contains("viewModel.bindStreamingLocalPlaylistOperations("));
        assertFalse(mainActivity.contains("StreamingLocalPlaylistOperationsBindings"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.importStreamingLikedTracksToLocal(provider, playlistName)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistImportStartRequest("));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistImportPresentation(result, languageMode)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingLikedImportPresentation(result, languageMode)"));
        assertTrue(streamingPlaylistController.contains("listener.showStreamingPlaylistLoadedDialog(presentation.status)"));
        assertFalse(mainActivity.contains("viewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId"));
        assertFalse(mainActivity.contains("viewModel.importStreamingLikedTracksToLocal(provider, playlistName"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistImportStartRequest("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingImportProviderPickerRequest("));
        assertFalse(mainActivity.contains("streamingViewModel.prepareStreamingImportProviderPickerRequest("));
        assertFalse(mainActivity.contains("private void showStreamingProviderPicker("));
        assertFalse(mainActivity.contains("private void showStreamingPlaylistLoadedDialog("));
        assertFalse(mainActivity.contains("private void showAccountPlaylistImportPicker("));
        assertFalse(mainActivity.contains("private void showImportStreamingFavoritesProviderPicker()"));
        assertFalse(mainActivity.contains("private String accountPlaylistLabel("));
        assertFalse(mainActivity.contains("new android.widget.CheckBox("));
        assertFalse(mainActivity.contains("StreamingAccountPlaylistImportText.noAccountPlaylists(settingsStore.languageMode())"));
        assertFalse(mainActivity.contains("StreamingAccountPlaylistImportText.title(settingsStore.languageMode())"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistExportPresentation(importStatus, languageMode)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistExportPresentation(importStatus)"));
        assertFalse(mainActivity.contains("private ImportStreamingPlaylistUseCase importStreamingPlaylistUseCase"));
        assertFalse(mainActivity.contains("private void importSelectedPlaylistToStreaming("));
        assertFalse(mainActivity.contains("private void importFavoritesToStreaming("));
        assertFalse(mainActivity.contains("private void runStreamingPlaylistImport("));
        assertFalse(mainActivity.contains("private void importStreamingPlaylistFromLink("));
        assertFalse(mainActivity.contains("private void importStreamingPlaylistFromProviderRef("));
        assertFalse(mainActivity.contains("private void importStreamingPlaylist("));
        assertFalse(mainActivity.contains("private void importStreamingLikedTracks("));
        assertFalse(mainActivity.contains("final java.util.List<app.yukine.streaming.StreamingTrack> finalStreamingTracks = streamingTracks"));
        assertFalse(mainActivity.contains("repository.importStreamingPlaylist(playlistName, placeholders)"));
        assertFalse(mainActivity.contains("StreamingPlaylistLinkParser.INSTANCE.parse"));
        assertFalse(mainActivity.contains("StreamingPlaylistLinkParser.ParsedPlaylistRef"));
        assertFalse(mainActivity.contains("if (!descriptor.getCapabilities().getSupportsSearch()) continue;"));
        assertFalse(mainActivity.contains("descriptor.getName() == app.yukine.streaming.StreamingProviderName.MOCK"));
        assertFalse(mainActivity.contains("summary.getMatchedTracks().size()"));
        assertFalse(mainActivity.contains("summary.getUnresolvedTracks().isEmpty()"));
        assertFalse(mainActivity.contains("summary.getTotalRequested()"));
        assertFalse(mainActivity.contains("String status = AppLanguage.text(settingsStore.languageMode(), \"streaming.import.matched.prefix\")"));
        assertFalse(mainActivity.contains("+ AppLanguage.text(settingsStore.languageMode(), \"streaming.import.unresolved.suffix\")"));
        assertFalse(mainActivity.contains("streamingPlaylistSyncStore.linkPlaylist(\r\n                            result.playlistId"));
        assertFalse(mainActivity.contains("streamingPlaylistSyncStore.linkPlaylist(\n                            result.playlistId"));
        assertTrue(resolveUseCase.contains("internal class ResolveStreamingPlaybackUseCase"));
        assertTrue(resolveUseCase.contains("StreamingPlaybackAdapter.isUnresolvedStreamingTrack"));
        assertTrue(resolveUseCase.contains("StreamingPlaybackAdapter.streamingProviderName"));
        assertTrue(resolveUseCase.contains("fun metadataFor("));
        assertTrue(resolveUseCase.contains("fun replaceResolvedTrack("));
        assertTrue(resolveUseCase.contains("fun prepareRecovery("));
        assertTrue(resolveUseCase.contains("fun clearRecovery(key: String?)"));
        assertTrue(resolveUseCase.contains("interface StreamingPlaybackResolvePlanner : StreamingPreResolvePlanner"));
        assertTrue(mainActivity.contains("ResolveStreamingPlaybackUseCase"));
        assertTrue(mainActivityViewModel.contains("interface StreamingPlaybackTaskQueue"));
        assertTrue(mainActivityViewModel.contains("fun scheduleCurrentPlaybackRecovery(task: StreamingPlaybackTask)"));
        assertTrue(mainActivityViewModel.contains("fun scheduleCurrentUrlResolve(task: StreamingPlaybackTask)"));
        assertTrue(mainActivityViewModel.contains("fun scheduleNextUrlResolve(task: StreamingPlaybackTask)"));
        assertTrue(read("app/src/main/java/app/yukine/StreamingPlaybackTaskScheduler.java").contains("private final PriorityQueue<ScheduledTask> criticalQueue"));
        assertTrue(read("app/src/main/java/app/yukine/StreamingPlaybackTaskScheduler.java").contains("private final Queue<ScheduledTask> nextResolveQueue"));
        assertTrue(streamingViewModel.contains("fun bindStreamingPlaybackCoordinator("));
        assertTrue(streamingViewModel.contains("private var streamingPlaybackPlanner: StreamingPlaybackResolvePlanner?"));
        assertTrue(streamingViewModel.contains("private var streamingPlaybackTaskQueue: StreamingPlaybackTaskQueue?"));
        assertTrue(streamingViewModel.contains("fun resolveStreamingTrackForPlayback("));
        assertTrue(streamingViewModel.contains("fun preResolveNextStreamingTrack("));
        assertTrue(streamingViewModel.contains("planner.prepareNextPreResolve(snapshot, queue)"));
        assertTrue(streamingViewModel.contains("planner.clearPreResolve(request.key)"));
        assertTrue(streamingViewModel.contains("fun resolveStreamingTrackListForPlayback("));
        assertTrue(streamingViewModel.contains("planner.prepare(tracks, index)"));
        assertTrue(streamingViewModel.contains("planner.replaceResolvedTrack(request, resolved)"));
        assertTrue(streamingViewModel.contains("fun prepareCurrentStreamingQueueResolveTarget("));
        assertTrue(streamingViewModel.contains("fun recoverStreamingBuffering("));
        assertTrue(streamingViewModel.contains("planner.prepareRecovery(snapshot, selectedQuality, adaptiveQuality)"));
        assertTrue(streamingViewModel.contains("planner.clearRecovery(request.key)"));
        assertFalse(mainActivityViewModel.contains("fun preResolveNextStreamingTrack("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.preResolveNextStreamingTrack("));
        assertFalse(mainActivityViewModel.contains("planner.prepareNextPreResolve(snapshot, queue)"));
        assertFalse(mainActivityViewModel.contains("planner.clearPreResolve(request.key)"));
        assertFalse(mainActivityViewModel.contains("fun resolveStreamingTrackListForPlayback("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.resolveStreamingTrackListForPlayback("));
        assertFalse(mainActivityViewModel.contains("planner.prepare(tracks, index)"));
        assertFalse(mainActivityViewModel.contains("planner.replaceResolvedTrack(request, resolved)"));
        assertTrue(mainActivityViewModel.contains("data class StreamingQueueResolveTarget"));
        assertFalse(mainActivityViewModel.contains("fun prepareCurrentStreamingQueueResolveTarget("));
        assertTrue(mainActivityViewModel.contains("data class StreamingRecommendationTrackList"));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaybackStatusText("));
        assertTrue(mainActivityViewModel.contains("data class StreamingStatusText("));
        assertTrue(mainActivityViewModel.contains("data class StreamingDailyRecommendationRequest("));
        assertTrue(mainActivityViewModel.contains("data class StreamingHeartbeatRecommendationRequest("));
        assertTrue(mainActivityViewModel.contains("data class StreamingRecommendationPresentation("));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistExportRequest("));
        assertFalse(streamingViewModel.contains("private val heartbeatRecommendationUseCase = StreamingHeartbeatRecommendationUseCase()"));
        assertTrue(streamingRecommendationViewModel.contains("private val heartbeatRecommendationUseCase = StreamingHeartbeatRecommendationUseCase()"));
        assertFalse(mainActivityViewModel.contains("private val heartbeatRecommendationUseCase = StreamingHeartbeatRecommendationUseCase()"));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaybackStatusText("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingPlaybackStatusText("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingStatusText("));
        assertFalse(mainActivityViewModel.contains("fun streamingPlaylistLoadedDialogTitle()"));
        assertTrue(streamingViewModel.contains("fun prepareRecommendationTrackList("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingDailyRecommendationRequest("));
        assertFalse(streamingViewModel.contains("fun prepareStreamingHeartbeatRecommendationRequest("));
        assertTrue(streamingRecommendationViewModel.contains("override fun prepareStreamingHeartbeatRecommendationRequest("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingHeartbeatRecommendationRequest("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingHeartbeatRecommendationRequest("));
        assertTrue(streamingViewModel.contains("fun streamingDailyRecommendationEmptyStatus("));
        assertFalse(streamingViewModel.contains("fun streamingHeartbeatRecommendationEmptyStatus("));
        assertTrue(streamingRecommendationViewModel.contains("override fun streamingHeartbeatRecommendationEmptyStatus("));
        assertFalse(mainActivityViewModel.contains("fun streamingHeartbeatRecommendationEmptyStatus()"));
        assertTrue(streamingViewModel.contains("fun prepareStreamingRecommendationPresentation("));
        assertFalse(streamingViewModel.contains("fun prepareHeartbeatRecommendationPresentation("));
        assertTrue(streamingRecommendationViewModel.contains("override fun prepareHeartbeatRecommendationPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareHeartbeatRecommendationPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistExportRequest("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingFavoritesExportRequest("));
        assertFalse(streamingViewModel.contains("fun prepareHeartbeatRecommendationPlaylist("));
        assertTrue(streamingRecommendationViewModel.contains("fun prepareHeartbeatRecommendationPlaylist("));
        assertFalse(mainActivityViewModel.contains("fun prepareHeartbeatRecommendationPlaylist("));
        assertFalse(streamingViewModel.contains("fun prepareHeartbeatRecommendationAppend("));
        assertTrue(streamingRecommendationViewModel.contains("fun prepareHeartbeatRecommendationAppend("));
        assertFalse(mainActivityViewModel.contains("fun prepareHeartbeatRecommendationAppend("));
        assertFalse(streamingViewModel.contains("fun prepareHeartbeatRecommendationAppendPresentation("));
        assertTrue(streamingRecommendationViewModel.contains("override fun prepareHeartbeatRecommendationAppendPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareHeartbeatRecommendationAppendPresentation("));
        assertTrue(streamingRecommendationViewModel.contains("StreamingPlaybackAdapter.placeholderTrack(it)"));
        assertFalse(mainActivityViewModel.contains("StreamingPlaybackAdapter.placeholderTrack(it)"));
        assertFalse(mainActivityViewModel.contains("fun recoverStreamingBuffering("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.recoverStreamingBuffering("));
        assertFalse(mainActivityViewModel.contains("planner.prepareRecovery(snapshot, selectedQuality, adaptiveQuality)"));
        assertFalse(mainActivityViewModel.contains("planner.clearRecovery(request.key)"));
        assertFalse(mainActivity.contains("viewModel.bindStreamingPlaybackCoordinator("));
        assertTrue(mainActivity.contains("streamingViewModel.bindStreamingPlaybackCoordinator("));
        assertTrue(mainActivity.contains("streamingPlaybackTaskScheduler"));
        assertFalse(mainActivity.contains("StreamingPlaybackTaskQueueAdapter("));
        assertFalse(mainActivity.contains("private final class ActivityStreamingPlaybackTaskQueue implements StreamingPlaybackTaskQueue"));
        assertTrue(mainActivity.contains("streamingPlaybackController.preResolveNextStreamingTrack(snapshot)"));
        assertFalse(mainActivity.contains("private void preResolveNextStreamingTrack("));
        assertFalse(mainActivity.contains("new StreamingPlaybackController(streamingViewModel, nowPlayingViewModel, new StreamingPlaybackController.Listener()"));
        assertTrue(mainActivity.contains("@Inject MainStreamingPlaybackListenerFactory streamingPlaybackListenerFactory;"));
        assertTrue(mainActivity.contains("streamingPlaybackListenerFactory.create("));
        assertTrue(mainActivity.contains("() -> settingsStore == null ? AppLanguage.MODE_SYSTEM : settingsStore.languageMode()"));
        assertTrue(mainActivity.contains("this::adaptiveStreamingQuality"));
        assertTrue(mainActivity.contains("this::selectedStreamingQuality"));
        assertTrue(mainActivity.contains("this::playbackQueueSnapshot"));
        assertTrue(mainActivity.contains("snapshot -> heartbeatRecommendationController.maybeAppendHeartbeatRecommendations(snapshot)"));
        assertTrue(mainActivity.contains("this::applyPlaybackActionResult"));
        assertTrue(mainActivity.contains("status -> statusMessageController.setStatus(status)"));
        assertTrue(mainActivity.contains("streamingPlaybackController::resolveAndPlayStreamingTrack"));
        assertTrue(mainActivity.contains("streamingPlaybackController.recoverStreamingBuffering(snapshot)"));
        assertTrue(streamingPlaybackController.contains("internal class StreamingPlaybackController("));
        assertTrue(streamingPlaybackController.contains("private val streamingViewModel: StreamingViewModel"));
        assertFalse(streamingPlaybackController.contains("MainActivityViewModel"));
        assertTrue(streamingPlaybackController.contains("fun preResolveNextStreamingTrack(snapshot: PlaybackStateSnapshot?)"));
        assertTrue(streamingPlaybackController.contains("listener.maybeAppendHeartbeatRecommendations(snapshot)"));
        assertTrue(streamingPlaybackController.contains("val queueSnapshot = listener.queueSnapshot()"));
        assertEquals(1, countOccurrences(streamingPlaybackController, "listener.queueSnapshot()"));
        assertTrue(streamingPlaybackController.contains("            queueSnapshot,\n"));
        assertTrue(streamingPlaybackController.contains("streamingViewModel.preResolveNextStreamingTrack("));
        assertTrue(streamingPlaybackController.contains("nowPlayingViewModel.replaceQueuedTrack(oldTrackId, resolved)"));
        assertTrue(streamingPlaybackController.contains("nowPlayingViewModel.warmPlaybackTrack(resolved)"));
        assertTrue(streamingPlaybackController.contains("resolved.tracks.getOrNull(resolved.index)?.let(nowPlayingViewModel::warmPlaybackTrack)"));
        assertFalse(streamingPlaybackController.contains("nowPlayingViewModel.precacheTrack(resolved)"));
        assertFalse(streamingPlaybackController.contains("nowPlayingViewModel::precacheTrack"));
        assertTrue(streamingPlaybackController.contains("fun resolveAndPlayStreamingTrack(tracks: List<Track>?, index: Int): Boolean"));
        assertTrue(streamingPlaybackController.contains("streamingViewModel.resolveStreamingTrackListForPlayback("));
        assertTrue(streamingPlaybackController.contains("listener.applyPlaybackActionResult("));
        assertTrue(streamingPlaybackController.contains("fun recoverStreamingBuffering(snapshot: PlaybackStateSnapshot?)"));
        assertTrue(streamingPlaybackController.contains("streamingViewModel.recoverStreamingBuffering("));
        assertTrue(streamingPlaybackController.contains("streamingViewModel.prepareStreamingPlaybackStatusText("));
        assertTrue(streamingPlaybackController.contains("nowPlayingViewModel.replaceCurrentTrackAndResume(resolved.track, resolved.positionMs)"));
        assertFalse(mainActivity.contains("new StreamingPlaybackBindings("));
        assertTrue(mainStreamingPlaybackListener.contains("internal class MainStreamingPlaybackListener("));
        assertTrue(mainStreamingPlaybackListener.contains(": StreamingPlaybackController.Listener"));
        assertTrue(mainStreamingPlaybackListener.contains("languageProvider.languageMode()"));
        assertTrue(mainStreamingPlaybackListener.contains("adaptiveQualityProvider.adaptiveStreamingQuality()"));
        assertTrue(mainStreamingPlaybackListener.contains("selectedQualityProvider.selectedStreamingQuality()"));
        assertTrue(mainStreamingPlaybackListener.contains("queueSnapshotSource.queueSnapshot()"));
        assertTrue(mainStreamingPlaybackListener.contains("heartbeatAppendHandler.maybeAppendHeartbeatRecommendations(snapshot)"));
        assertTrue(mainStreamingPlaybackListener.contains("resultSink.apply(result)"));
        assertTrue(mainStreamingPlaybackListener.contains("statusSink.setStatus(status)"));
        assertTrue(playbackUiModule.contains("fun provideMainStreamingPlaybackListenerFactory(): MainStreamingPlaybackListenerFactory"));
        assertFalse(mainActivity.contains("viewModel.preResolveNextStreamingTrack("));
        assertFalse(mainActivity.contains("private boolean resolveAndPlayStreamingTrack("));
        assertFalse(mainActivity.contains("private void recoverStreamingBuffering("));
        assertFalse(mainActivity.contains("viewModel.resolveStreamingTrackListForPlayback("));
        assertFalse(mainActivity.contains("viewModel.recoverStreamingBuffering("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaybackStatusText("));
        assertTrue(mainActivity.contains("streamingViewModel.prepareStreamingPlaybackStatusText("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingStatusText("));
        assertFalse(mainActivity.contains("streamingViewModel.prepareStreamingStatusText("));
        assertFalse(mainActivity.contains("viewModel.streamingPlaylistLoadedDialogTitle()"));
        assertFalse(mainActivity.contains("streamingViewModel.streamingPlaylistLoadedDialogTitle("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingDailyRecommendationRequest(provider)"));
        assertFalse(exists("app/src/main/java/app/yukine/DailyRecommendationController.kt"));
        assertFalse(streamingRecommendationViewModel.contains("streamingViewModel.prepareStreamingDailyRecommendationRequest(provider, languageMode)"));
        assertTrue(streamingRecommendationViewModel.contains("fun playDailyRecommendations("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingHeartbeatRecommendationRequest(provider)"));
        assertFalse(heartbeatRecommendationController.contains("streamingViewModel.prepareStreamingHeartbeatRecommendationRequest(provider, languageMode)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.prepareStreamingHeartbeatRecommendationRequest(provider, languageMode)"));
        assertFalse(mainActivity.contains("viewModel.streamingDailyRecommendationEmptyStatus()"));
        assertFalse(streamingRecommendationViewModel.contains("streamingViewModel.streamingDailyRecommendationEmptyStatus(languageMode)"));
        assertFalse(mainActivity.contains("viewModel.streamingHeartbeatRecommendationEmptyStatus()"));
        assertFalse(heartbeatRecommendationController.contains("streamingViewModel.streamingHeartbeatRecommendationEmptyStatus(languageMode)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.streamingHeartbeatRecommendationEmptyStatus(languageMode)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingRecommendationPresentation(streamingTracks, emptyStatus, title)"));
        assertFalse(mainActivity.contains("streamingViewModel::prepareStreamingRecommendationPresentation"));
        assertFalse(mainActivity.contains("new DailyRecommendationController(streamingRecommendationViewModel"));
        assertFalse(mainActivity.contains("new RecommendationActionCallbacks() {"));
        assertTrue(streamingRecommendationViewModel.contains("fun onAction("));
        assertFalse(mainActivity.contains("streamingViewModel.prepareHeartbeatRecommendationPresentation(streamingTracks, emptyStatus, playingStatus)"));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.prepareHeartbeatRecommendationPresentation("));
        assertFalse(mainActivity.contains("viewModel.prepareHeartbeatRecommendationAppendPresentation(streamingTracks)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistExportRequest("));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistExportRequest("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingFavoritesExportRequest("));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingFavoritesExportRequest("));
        assertFalse(mainActivity.contains("StreamingPreResolveRequest request = resolveStreamingPlaybackUseCase.prepareNextPreResolve(snapshot, queue)"));
        assertFalse(mainActivity.contains("resolveStreamingPlaybackUseCase.clearPreResolve(request.getKey())"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, completion -> {\r\n            viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, completion -> {\n            viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("ResolveStreamingPlaybackRequest request = resolveStreamingPlaybackUseCase.prepare(tracks, index)"));
        assertFalse(mainActivity.contains("resolveStreamingPlaybackUseCase.replaceResolvedTrack(request, resolved)"));
        assertFalse(mainActivity.contains("StreamingPlaybackAdapter.INSTANCE.isUnresolvedStreamingTrack"));
        assertFalse(mainActivity.contains("StreamingPlaybackAdapter.isUnresolvedStreamingTrack"));
        assertFalse(mainActivity.contains("StreamingPlaybackAdapter.INSTANCE.placeholderTrack"));
        assertFalse(mainActivity.contains("private ArrayList<Track> placeholderTracksFor("));
        assertFalse(mainActivity.contains("private ArrayList<Track> heartbeatPlaceholderTracksFor("));
        assertFalse(mainActivity.contains("private app.yukine.streaming.StreamingProviderName recommendationProvider("));
        assertFalse(mainActivity.contains("private final StreamingHeartbeatRecommendationUseCase heartbeatRecommendationUseCase"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, completion ->\r\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, completion ->\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("StreamingRecoveryRequest request = resolveStreamingPlaybackUseCase.prepareRecovery("));
        assertFalse(mainActivity.contains("resolveStreamingPlaybackUseCase.clearRecovery(request.getKey())"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, completion ->\r\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, completion ->\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("private app.yukine.streaming.StreamingTrack streamingMetadataFor("));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.playback."));
        assertFalse(mainActivity.contains("AppLanguage.text(languageMode, \"streaming.playback."));
        assertTrue(syncUseCase.contains("internal class SyncStreamingPlaylistUseCase"));
        assertTrue(syncUseCase.contains("StreamingPlaybackAdapter.placeholderTrack"));
        assertTrue(syncUseCase.contains("operations.syncStreamingPlaylist(link.localPlaylistId, placeholders)"));
        assertTrue(syncUseCase.contains("operations.markSynced(link.localPlaylistId)"));
        assertTrue(streamingModule.contains("fun provideSyncStreamingPlaylistUseCase("));
        assertTrue(streamingModule.contains("SyncStreamingPlaylistUseCase(MusicLibraryStreamingPlaylistSyncOperations(repository, syncStore))"));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("syncStreamingPlaylistUseCase.execute(link, streamingTracks)"));
        assertFalse(mainActivity.contains("new SyncStreamingPlaylistUseCase("));
        assertFalse(mainActivity.contains("syncStreamingPlaylistUseCase.execute(link, streamingTracks)"));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistSyncTarget"));
        assertTrue(mainActivityViewModel.contains("data class StreamingPlaylistSyncStartRequest("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistSyncTarget("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistSyncStartRequest("));
        assertFalse(mainActivityViewModel.contains("fun syncStreamingPlaylistToLocal("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistSyncTarget("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistSyncStartRequest("));
        assertTrue(streamingViewModel.contains("fun syncStreamingPlaylistToLocal("));
        assertTrue(streamingViewModel.contains("operations.syncStreamingPlaylist(link, tracks)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingPlaylistSyncStartRequest("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.syncStreamingPlaylistToLocal(link, onSynced)"));
        assertFalse(mainActivityViewModel.contains("operations.syncStreamingPlaylist(link, tracks)"));
        assertTrue(mainActivity.contains("new StreamingPlaylistController("));
        assertTrue(mainActivity.contains("streamingPlaylistController.syncSelectedPlaylistFromStreaming()"));
        assertFalse(mainActivity.contains("private void syncSelectedPlaylistFromStreaming()"));
        assertFalse(mainActivity.contains("private void syncStreamingPlaylists("));
        assertFalse(mainActivity.contains("streamingPlaylistController.syncStreamingPlaylists(links)"));
        assertTrue(streamingPlaylistController.contains("internal class StreamingPlaylistController("));
        assertTrue(streamingPlaylistController.contains("private val streamingViewModel: StreamingViewModel"));
        assertTrue(streamingPlaylistController.contains("fun interface LanguageProvider"));
        assertFalse(streamingPlaylistController.contains("MainActivityViewModel"));
        assertTrue(streamingPlaylistController.contains("fun syncSelectedPlaylistFromStreaming()"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistSyncStartRequest("));
        assertTrue(streamingPlaylistController.contains("fun syncStreamingPlaylists(links: List<StreamingPlaylistSyncStore.LinkedPlaylist>)"));
        assertTrue(streamingPlaylistController.contains("fun syncStreamingPlaylist(link: StreamingPlaylistSyncStore.LinkedPlaylist)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.syncStreamingPlaylistToLocal(link)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistSyncPresentation(result, languageMode)"));
        assertFalse(mainActivity.contains("new StreamingPlaylistController.Listener()"));
        assertTrue(mainActivity.contains("streamingPlaylistListenerFactory.create("));
        assertTrue(mainActivity.contains("routeController.selectedPlaylistId()"));
        assertTrue(mainActivity.contains("routeController.setSelectedPlaylistId(playlistId)"));
        assertFalse(mainActivity.contains("public void refreshLibraryAfterStreamingImport()"));
        assertTrue(mainActivity.contains("loadLibrary(true);"));
        assertFalse(mainActivity.contains("private void refreshLibraryAfterStreamingImport()"));
        assertFalse(mainActivity.contains("MainActivity.this.refreshLibraryAfterStreamingImport();"));
        assertTrue(mainActivity.contains("streamingPlaylistDialogController.showStreamingProviderPicker(playlistName, tracks)"));
        assertTrue(mainActivity.contains("streamingPlaylistDialogController.showAccountPlaylistImportPicker(provider, playlists)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistSyncStartRequest("));
        assertFalse(mainActivity.contains("viewModel.syncStreamingPlaylistToLocal(link"));
        assertFalse(mainActivity.contains("private SyncStreamingPlaylistUseCase syncStreamingPlaylistUseCase"));
        assertFalse(mainActivity.contains("private GetStreamingPlaylistLinkUseCase getStreamingPlaylistLinkUseCase"));
        assertFalse(mainActivity.contains("getStreamingPlaylistLinkUseCase.execute(playlistId)"));
        assertFalse(mainActivity.contains("viewModel.fetchUserLikedTracks(link.getProvider()"));
        assertFalse(mainActivity.contains("viewModel.fetchStreamingPlaylistTracks(link.getProvider(), link.getProviderPlaylistId()"));
        assertFalse(mainActivity.contains("repository.syncStreamingPlaylist(playlistId, placeholders)"));
        assertTrue(linkUseCase.contains("internal class GetStreamingPlaylistLinkUseCase"));
        assertTrue(linkUseCase.contains("operations.getLink(localPlaylistId)"));
        assertTrue(streamingModule.contains("fun provideGetStreamingPlaylistLinkUseCase("));
        assertTrue(streamingModule.contains("GetStreamingPlaylistLinkUseCase(StreamingPlaylistSyncStoreLinkOperations(syncStore))"));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("streamingPlaylistLinkUseCase.execute(localPlaylistId)"));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("streamingPlaylistLinkUseCase.execute(provider, providerPlaylistId)"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingLocalPlaylistOperationsBindings.kt"));
        assertFalse(exists("app/src/test/java/app/yukine/StreamingLocalPlaylistOperationsBindingsTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingTrackMatchStoreBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingTrackMatchStoreBindings.java"));
        assertFalse(mainActivity.contains("new StreamingLocalPlaylistOperationsBindings("));
        assertFalse(mainActivity.contains("new StreamingTrackMatchStore()"));
        assertFalse(mainActivity.contains("streamingPlaylistSyncStore.getLink(playlistId)"));
        assertTrue(loginPlaylistUseCase.contains("internal class EnsureStreamingLoginPlaylistUseCase"));
        assertTrue(loginPlaylistUseCase.contains("operations.createPlaylist(playlistName)"));
        assertTrue(loginPlaylistUseCase.contains("operations.loadPlaylists()"));
        assertTrue(loginPlaylistUseCase.contains("operations.linkPlaylist(playlistId, provider, \"\")"));
        assertTrue(streamingModule.contains("fun provideEnsureStreamingLoginPlaylistUseCase("));
        assertTrue(streamingModule.contains("EnsureStreamingLoginPlaylistUseCase(MusicLibraryStreamingLoginPlaylistOperations(repository, syncStore))"));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider)"));
        assertFalse(mainActivity.contains("new EnsureStreamingLoginPlaylistUseCase("));
        assertTrue(mainActivityViewModel.contains("data class StreamingLoginPlaylistRequest"));
        assertTrue(mainActivityViewModel.contains("data class StreamingLocalPlaylistImportPresentation("));
        assertTrue(mainActivityViewModel.contains("data class StreamingLocalPlaylistSyncPresentation("));
        assertTrue(mainActivityViewModel.contains("data class StreamingLoginPlaylistPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingLoginPlaylistRequest("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingLikedPlaylistName("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistImportPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingLikedImportPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingPlaylistSyncPresentation("));
        assertFalse(mainActivityViewModel.contains("fun prepareStreamingLoginPlaylistPresentation("));
        assertTrue(mainActivityViewModel.contains("fun ensureStreamingLoginPlaylist("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingLoginPlaylistRequest("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingLikedPlaylistName("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistImportPresentation("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingLikedImportPresentation("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingPlaylistSyncPresentation("));
        assertTrue(streamingViewModel.contains("fun prepareStreamingLoginPlaylistPresentation("));
        assertTrue(streamingViewModel.contains("fun ensureStreamingLoginPlaylist("));
        assertTrue(streamingViewModel.contains("operations.ensureStreamingLoginPlaylist(playlistName, provider)"));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingLoginPlaylistRequest("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingLikedPlaylistName("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingPlaylistImportPresentation("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingLikedImportPresentation("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingPlaylistSyncPresentation("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.prepareStreamingLoginPlaylistPresentation("));
        assertFalse(mainActivityViewModel.contains("return streamingViewModel.ensureStreamingLoginPlaylist(playlistName, provider, onEnsured)"));
        assertFalse(mainActivityViewModel.contains("operations.ensureStreamingLoginPlaylist(playlistName, provider)"));
        assertTrue(mainStreamingLocalPlaylistOperations.contains("ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider)"));
        assertTrue(streamingPlaylistController.contains("fun onStreamingLoginSuccess(provider: StreamingProviderName)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingLoginPlaylistRequest(provider, languageMode)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.ensureStreamingLoginPlaylist(request.playlistName, request.provider)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingLoginPlaylistPresentation("));
        assertTrue(mainStreamingActionGateway.contains("loginSuccessHandler.onStreamingLoginSuccess(provider)"));
        assertFalse(mainActivity.contains("private void onStreamingLoginSuccess("));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingLoginPlaylistRequest(provider)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistImportPresentation(result)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingPlaylistImportPresentation(result, languageMode)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingLikedImportPresentation(result)"));
        assertTrue(streamingPlaylistController.contains("streamingViewModel.prepareStreamingLikedImportPresentation(result, languageMode)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingPlaylistSyncPresentation(result)"));
        assertFalse(mainActivity.contains("viewModel.prepareStreamingLoginPlaylistPresentation(request, result)"));
        assertFalse(mainActivity.contains("viewModel.ensureStreamingLoginPlaylist(request.getPlaylistName(), request.getProvider()"));
        assertFalse(mainActivity.contains("private EnsureStreamingLoginPlaylistUseCase ensureStreamingLoginPlaylistUseCase"));
        assertFalse(mainActivity.contains("String displayName = provider.getWireName()"));
        assertFalse(mainActivity.contains("displayName = desc.getDisplayName()"));
        assertFalse(mainActivity.contains("streaming.my.playlist.prefix"));
        assertFalse(mainActivity.contains("streaming.my.playlist.suffix"));
        assertFalse(mainActivity.contains("streaming.liked.playlist.prefix"));
        assertFalse(mainActivity.contains("streaming.liked.playlist.suffix"));
        assertFalse(mainActivity.contains("setStatus(AppLanguage.text(settingsStore.languageMode(), \"streaming.no.tracks.to.import\"))"));
        assertFalse(mainActivity.contains("setStatus(AppLanguage.text(settingsStore.languageMode(), \"streaming.sync.started\"))"));
        assertFalse(mainActivity.contains("String status = AppLanguage.text(settingsStore.languageMode(), \"streaming.playlist.imported.prefix\")"));
        assertFalse(mainActivity.contains("String status = AppLanguage.text(languageMode, \"streaming.liked.imported.prefix\")"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.sync.complete\") + \" (\" + result.getSyncedCount() + \")\""));
        assertFalse(mainActivity.contains("AppLanguage.text(languageMode, \"streaming.playlist.created\") + \": \" + request.getPlaylistName()"));
        assertFalse(mainActivity.contains("final app.yukine.streaming.StreamingProviderName finalProvider = provider"));
        assertFalse(mainActivity.contains("EnsureStreamingLoginPlaylistResult result =\r\n                    ensureStreamingLoginPlaylistUseCase.execute(playlistName, finalProvider)"));
        assertFalse(mainActivity.contains("EnsureStreamingLoginPlaylistResult result =\n                    ensureStreamingLoginPlaylistUseCase.execute(playlistName, finalProvider)"));
        assertFalse(mainActivity.contains("repository.createPlaylist(playlistName)"));
        assertFalse(mainActivity.contains("repository.loadPlaylists()"));
        assertTrue(trackMatchUseCase.contains("internal class StreamingTrackMatchUseCase("));
        assertTrue(trackMatchUseCase.contains(") : StreamingTrackMatchStore"));
        assertTrue(trackMatchUseCase.contains("StreamingDataPathMetadata.provider(track.dataPath)"));
        assertTrue(trackMatchUseCase.contains("StreamingDataPathMetadata.providerTrackId(track.dataPath)"));
        assertFalse(trackMatchUseCase.contains("StreamingPlaybackAdapter"));
        assertTrue(trackMatchUseCase.contains("operations.loadStreamingTrackMatch(track, provider.wireName)"));
        assertTrue(trackMatchUseCase.contains("operations.saveStreamingTrackMatch(track, provider.wireName, cleanTrackId)"));
        assertTrue(trackMatchUseCase.contains("fun heartbeatSeedCandidates("));
        assertTrue(trackMatchUseCase.contains("fun snapshotQueueForHeartbeat("));
        assertTrue(trackMatchUseCase.contains("fun heartbeatSeedMissMessage("));
        assertTrue(trackMatchUseCase.contains("private fun addHeartbeatSnapshotCandidates("));
        assertTrue(mainActivity.contains("StreamingTrackMatchUseCase"));
        assertTrue(mainActivityViewModel.contains("interface StreamingTrackMatchStore"));
        assertTrue(mainActivityViewModel.contains("fun directProviderTrackId(track: Track, provider: StreamingProviderName): String = \"\""));
        assertTrue(mainActivityViewModel.contains("fun providerTrackIdFromCandidates("));
        assertTrue(mainActivityViewModel.contains("fun heartbeatSeedCandidates("));
        assertTrue(mainActivityViewModel.contains("fun snapshotQueueForHeartbeat("));
        assertTrue(mainActivityViewModel.contains("fun heartbeatSeedMissMessage("));
        assertFalse(mainActivityViewModel.contains("fun loadStreamingProviderTrackId("));
        assertFalse(mainActivityViewModel.contains("fun streamingProviderTrackIdFor("));
        assertFalse(mainActivityViewModel.contains("fun saveStreamingProviderTrackId("));
        assertTrue(mainActivityViewModel.contains("data class HeartbeatRecommendationSeedRequest("));
        assertFalse(mainActivityViewModel.contains("fun prepareHeartbeatRecommendationSeedRequest("));
        assertFalse(mainActivityViewModel.contains("fun resolveHeartbeatRecommendationSeed("));
        assertFalse(streamingViewModel.contains("fun prepareHeartbeatRecommendationSeedRequest("));
        assertFalse(streamingViewModel.contains("private suspend fun resolveHeartbeatRecommendationSeedId("));
        assertFalse(streamingViewModel.contains("private suspend fun searchHeartbeatSeedMatch("));
        assertTrue(streamingRecommendationViewModel.contains("private suspend fun resolveHeartbeatRecommendationSeedId("));
        assertTrue(streamingRecommendationViewModel.contains("private suspend fun searchHeartbeatSeedMatch("));
        assertFalse(mainActivity.contains("viewModel.bindStreamingTrackMatchStore"));
        assertTrue(mainActivity.contains("streamingViewModel.bindStreamingTrackMatchStore"));
        assertTrue(mainActivity.contains("streamingRecommendationViewModel.bindStreamingTrackMatchStore(streamingTrackMatchUseCase)"));
        assertTrue(mainActivity.contains("heartbeatSeedBinder = new HeartbeatRecommendationSeedBinder("));
        assertTrue(mainActivity.contains("heartbeatSeedBinder.bind(streamingTrackMatchUseCase);"));
        assertFalse(mainActivity.contains("new HeartbeatRecommendationSeedResolver(\n                streamingTrackMatchUseCase,")
                || mainActivity.contains("new HeartbeatRecommendationSeedResolver(\r\n                streamingTrackMatchUseCase,"));
        assertFalse(mainActivity.contains("streamingTrackMatchStore = new StreamingTrackMatchStoreBindings"));
        assertFalse(mainActivity.contains("new HeartbeatRecommendationSeedResolver(streamingTrackMatchStore)"));
        assertFalse(mainActivity.contains("viewModel.streamingProviderTrackIdFor("));
        assertTrue(mainActivity.contains("streamingViewModel.streamingProviderTrackIdFor("));
        assertFalse(mainActivity.contains("streamingViewModel.prepareHeartbeatRecommendationSeedRequest("));
        assertFalse(mainActivity.contains("heartbeatSeedRequestProvider.bind(new HeartbeatRecommendationSeedResolver("));
        assertFalse(mainActivity.contains("HeartbeatRecommendationSeedResolverBindings"));
        assertTrue(mainActivity.contains("this::heartbeatLibraryContextTracks"));
        assertFalse(mainActivity.contains("viewModel.resolveHeartbeatRecommendationSeed("));
        assertFalse(heartbeatRecommendationController.contains("streamingViewModel.resolveHeartbeatRecommendationSeed("));
        assertTrue(heartbeatRecommendationController.contains("recommendationPlayer.resolveHeartbeatRecommendationSeed("));
        assertFalse(mainActivity.contains("viewModel.loadStreamingProviderTrackId(track, provider"));
        assertFalse(mainActivity.contains("viewModel.resolveStreamingTrackMatch(provider, track"));
        assertFalse(mainActivity.contains("viewModel.saveStreamingProviderTrackId(track, provider, resolvedTrackId)"));
        assertFalse(mainActivity.contains("private String streamingProviderTrackId(Track track"));
        assertFalse(mainActivity.contains("private List<Track> heartbeatSeedCandidates()"));
        assertFalse(mainActivity.contains("private String streamingProviderTrackIdFromCandidates("));
        assertFalse(mainActivity.contains("private String currentStreamingProviderTrackId("));
        assertFalse(mainActivity.contains("private List<Track> snapshotQueueForHeartbeat()"));
        assertFalse(mainActivity.contains("private String currentStreamingProviderPlaylistId("));
        assertFalse(mainActivity.contains("private void addHeartbeatSnapshotCandidates("));
        assertFalse(mainActivity.contains("private void addHeartbeatSeedCandidate("));
        assertFalse(mainActivity.contains("private void resolveHeartbeatSeedAt("));
        assertFalse(mainActivity.contains("private void resolveHeartbeatSeedBySearch("));
        assertFalse(mainActivity.contains("private void cacheHeartbeatSeedMatch("));
        assertFalse(mainActivity.contains("builder.append(\"Heartbeat seed missing provider=\")"));
        assertFalse(mainActivity.contains("private String neteaseProviderTrackIdForLyrics(Track track) {\r\n        return streamingTrackMatchUseCase.providerTrackIdFor("));
        assertFalse(mainActivity.contains("private String neteaseProviderTrackIdForLyrics(Track track) {\n        return streamingTrackMatchUseCase.providerTrackIdFor("));
        assertFalse(mainActivity.contains("executors.io(() -> {\r\n            final String cachedTrackId = streamingTrackMatchUseCase.providerTrackIdFor(track, provider);"));
        assertFalse(mainActivity.contains("executors.io(() -> {\n            final String cachedTrackId = streamingTrackMatchUseCase.providerTrackIdFor(track, provider);"));
        assertFalse(mainActivity.contains("executors.io(() -> streamingTrackMatchUseCase.saveProviderTrackId(track, provider, providerTrackId))"));
        assertFalse(mainActivity.contains("executors.io(() -> streamingTrackMatchUseCase.saveProviderTrackId(track, provider, resolvedTrackId))"));
        assertFalse(mainActivity.contains("repository.loadStreamingTrackMatch("));
        assertFalse(mainActivity.contains("repository.saveStreamingTrackMatch("));
        assertTrue(toggleFavoriteUseCase.contains("internal class ToggleFavoriteUseCase"));
        assertTrue(toggleFavoriteUseCase.contains("operations.setFavorite(track, favorite)"));
        assertTrue(mainActivity.contains("ToggleFavoriteUseCase"));
        String libraryViewModel = read("app/src/main/java/app/yukine/LibraryViewModel.kt");
        assertTrue(libraryViewModel.contains("fun interface LibraryFavoriteWriter"));
        assertTrue(libraryViewModel.contains("fun interface LibraryFavoriteIdsProvider"));
        assertTrue(libraryViewModel.contains("fun bindFavoriteIdsProvider(nextProvider: LibraryFavoriteIdsProvider?)"));
        assertFalse(libraryViewModel.contains("data class LibraryUiState("));
        assertFalse(libraryViewModel.contains("val uiState: StateFlow<LibraryUiState>"));
        assertFalse(libraryViewModel.contains("fun updateState("));
        assertTrue(libraryViewModel.contains("gateway?.applyFavorite(track.id, nextFavorite)"));
        assertTrue(libraryViewModel.contains("val liveFavoriteIds = favoriteIdsProvider?.favoriteIds()"));
        assertFalse(exists("app/src/main/java/app/yukine/LibrarySmallGatewayBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibrarySmallGatewayBindings.java"));
        assertTrue(mainActivity.contains("libraryViewModel.bindFavoriteWriter((track, favorite) -> toggleFavoriteUseCase.execute(track, favorite))"));
        assertTrue(mainActivity.contains("libraryViewModel.bindFavoriteIdsProvider("));
        assertTrue(mainActivity.contains("track -> libraryViewModel.onEvent(new LibraryEvent.ToggleFavorite(track))"));
        assertFalse(mainActivity.contains("new LibraryFavoriteWriterBindings("));
        assertFalse(mainActivity.contains("private void toggleFavorite(Track track)"));
        assertFalse(mainActivity.contains("repository.setFavorite(track, favorite)"));
        assertTrue(loadPlaylistTracksUseCase.contains("internal class LoadPlaylistTracksUseCase"));
        assertTrue(loadPlaylistTracksUseCase.contains("operations.loadPlaylistTracks(playlistId)"));
        assertTrue(mainActivity.contains("LoadPlaylistTracksUseCase"));
        assertFalse(mainActivity.contains("new LibraryPlaylistTrackLoaderBindings("));
        assertTrue(libraryViewModel.contains("data class PlayPlaylist"));
        assertTrue(libraryViewModel.contains("data class OpenPlaylist"));
        assertTrue(libraryViewModel.contains("loader.loadPlaylistTracks(playlistId)"));
        assertTrue(mainActivity.contains("libraryViewModel.bindPlaylistTrackLoader(playlistId -> loadPlaylistTracksUseCase.execute(playlistId))"));
        assertFalse(mainActivity.contains("new LibraryPlaylistTrackLoaderBindings("));
        assertTrue(playlistsRenderController.contains("listener.playPlaylist(playlist.id)"));
        assertTrue(playlistsRenderController.contains("listener.openPlaylist(playlist.id, playlist.name)"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryPlaylistsRenderBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LibraryPlaylistsRenderBindings.java"));
        assertTrue(mainActivity.contains("new LibraryPlaylistsRenderController(libraryViewModel, new LibraryPlaylistsRenderController.Listener()"));
        assertTrue(mainActivity.contains("libraryViewModel.onEvent(new LibraryEvent.PlayPlaylist(playlistId))"));
        assertTrue(mainActivity.contains("libraryViewModel.onEvent(new LibraryEvent.OpenPlaylist(playlistId, title))"));
        assertTrue(mainActivity.contains("playlistDialogController.confirmDeletePlaylist(playlist);"));
        assertTrue(mainActivity.contains("renderLibraryPlaylistTrackList(request);"));
        assertFalse(mainActivity.contains("private void playLibraryPlaylist("));
        assertFalse(mainActivity.contains("repository.loadPlaylistTracks(playlistId)"));
        assertTrue(loadLyricsSettingsUseCase.contains("internal class LoadLyricsSettingsUseCase"));
        assertTrue(loadLyricsSettingsUseCase.contains("operations.loadOnlineLyricsEnabled()"));
        assertTrue(loadLyricsSettingsUseCase.contains("operations.loadLyricsOffsetMs()"));
        assertTrue(mainActivity.contains("LoadLyricsSettingsUseCase"));
        assertTrue(mainActivity.contains("loadLyricsSettingsUseCase.execute()"));
        assertFalse(mainActivity.contains("repository.loadOnlineLyricsEnabled()"));
        assertFalse(mainActivity.contains("repository.loadLyricsOffsetMs()"));
        assertTrue(loadTrackLyricsUseCase.contains("internal interface TrackLyricsOperations"));
        assertTrue(loadTrackLyricsUseCase.contains("internal class LoadTrackLyricsUseCase"));
        assertTrue(loadTrackLyricsUseCase.contains("operations.loadForTrack(track, onlineEnabled, neteaseProviderTrackId.orEmpty())"));
        assertTrue(mainActivity.contains("lyricsViewModel.configure("));
        assertTrue(mainActivity.contains("lyricsLoader,"));
        assertFalse(mainActivity.contains("new LoadTrackLyricsUseCaseLyricsLoader("));
        assertFalse(mainActivity.contains("new LoadTrackLyricsUseCase(new LyricsRepositoryLoadOperations())"));
        assertTrue(mainActivity.contains("lyricsViewModel.load(track, neteaseProviderTrackIdForLyrics(track))"));
        assertTrue(mainActivity.contains("lyricsViewModel.bindListener(new LyricsStateRefreshListener("));
        assertTrue(mainActivity.contains("lyricsViewModel.bindReloadGateway("));
        assertTrue(mainActivity.contains("() -> playbackStore == null ? null : playbackStore.snapshot().currentTrack"));
        assertTrue(mainActivity.contains("this::neteaseProviderTrackIdForLyrics"));
        assertFalse(mainActivity.contains("this::setStatus"));
        assertFalse(mainActivity.contains("settingsActionController::reloadCurrentLyrics"));
        assertTrue(mainActivity.contains("lyricsViewModel.reloadCurrentLyrics(settingsStore.languageMode())"));
        assertTrue(mainActivity.contains("() -> lyricsViewModel == null ? new LyricsState() : lyricsViewModel.stateSnapshot()"));
        assertTrue(mainNowPlayingStateListener.contains("lyricsStateSource.lyricsState()"));
        assertFalse(mainActivity.contains("private LyricsState lyricsState()"));
        assertFalse(mainActivity.contains("private void ensureLyricsLoaded("));
        assertFalse(mainActivity.contains("new LyricsStateListener()"));
        assertFalse(mainActivity.contains("private void onLyricsStateChanged()"));
        assertFalse(mainActivity.contains("private LyricsController lyricsController"));
        assertFalse(mainActivity.contains("LyricsReloadController"));
        assertFalse(mainActivity.contains("LyricsReloadBindings"));
        assertFalse(mainActivity.contains("lyricsReloadController"));
        assertFalse(mainActivity.contains("setStatus(playbackStore.snapshot().currentTrack == null"));
        assertFalse(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"reloading.lyrics\")"));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsStateListenerBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsStateListenerBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsReloadController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsReloadBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsReloadController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/LyricsReloadBindings.java"));
        assertTrue(lyricsViewModel.contains("internal class LyricsStateRefreshListener("));
        assertTrue(lyricsViewModel.contains(") : LyricsStateListener"));
        assertTrue(lyricsViewModel.contains("internal fun interface LyricsNowPlayingContentUpdater"));
        assertTrue(lyricsViewModel.contains("renderNowBarAction.run()"));
        assertTrue(lyricsViewModel.contains("MainRoutes.TAB_NOW == selectedTabProvider.selectedTab()"));
        assertTrue(lyricsViewModel.contains("fun bindReloadGateway("));
        assertTrue(lyricsViewModel.contains("fun reloadCurrentLyrics(languageMode: String? = AppLanguage.MODE_SYSTEM): Job"));
        assertTrue(lyricsViewModel.contains("currentTrackProvider?.currentTrack()"));
        assertTrue(lyricsViewModel.contains("providerTrackIdResolver?.providerTrackId(it)"));
        assertTrue(lyricsViewModel.contains("reloadStatusSink?.setStatus("));
        assertTrue(lyricsViewModel.contains("if (track == null) \"no.track.selected\" else \"reloading.lyrics\""));
        assertTrue(networkActionsViewModel.contains("internal data class NetworkActionUseCases"));
        assertTrue(networkActionsViewModel.contains("private var useCases: NetworkActionUseCases?"));
        assertTrue(networkActionsViewModel.contains("actions.addStreamUrlUseCase.execute(title, url)"));
        assertTrue(networkActionsViewModel.contains("actions.syncWebDavSourceUseCase.execute(sourceId, sourceName)"));
        assertFalse(networkActionsViewModel.contains("MusicLibraryRepository"));
        assertFalse(networkActionsViewModel.contains("MusicLibraryWebDavSourceOperations("));
        assertFalse(networkActionsViewModel.contains("MusicLibraryNetworkLibraryOperations("));
        assertTrue(mainActivity.contains("networkActionsViewModel.bindUseCases(networkActionUseCases);"));
        assertFalse(mainActivity.contains("new NetworkActionUseCases("));
        assertFalse(mainActivity.contains("new MusicLibraryWebDavSourceOperations(repository)"));
        assertFalse(mainActivity.contains("new MusicLibraryNetworkLibraryOperations(repository)"));
    }

    @Test
    public void streamingSearchRenderControllerIsKotlin() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String controller = read("app/src/main/java/app/yukine/StreamingSearchRenderController.kt");
        String mainStreamingSearchRenderListener = read("app/src/main/java/app/yukine/MainStreamingSearchRenderListener.kt");
        String authCallbackController = read("app/src/main/java/app/yukine/StreamingAuthCallbackController.kt");
        String screen = read("feature/ui-common/src/main/java/app/yukine/ui/StreamingSearchScreen.kt");
        String contracts = read("feature/ui-common/src/main/java/app/yukine/StreamingSearchContracts.kt");

        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchRenderController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchEventController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchEventController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchChromeBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchChromeBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchNavigatorBindings.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingSearchNavigatorBindings.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingAuthCallbackController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/ActivityIntentController.java"));
        assertTrue(controller.contains("internal class StreamingSearchRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun interface LanguageProvider"));
        assertTrue(controller.contains("languageProvider.languageMode()"));
        assertTrue(controller.contains("listener.publishStreamingSearchChrome(labels, actions)"));
        assertFalse(controller.contains("StreamingSearchScreenFactory.create"));
        assertTrue(controller.contains("StreamingSearchLabels("));
        assertTrue(controller.contains("StreamingSearchActions"));
        assertTrue(mainActivity.contains("() -> settingsStore.languageMode()"));
        assertTrue(contracts.contains("data class StreamingSearchState"));
        assertFalse(contracts.contains("data class MainActivityStreamingState"));
        assertTrue(contracts.contains("StreamingPlaylistImportSummary"));
        assertFalse(screen.contains("app.yukine.streaming.StreamingPlaylistImporter.StreamingPlaylistImportSummary"));
        assertTrue(screen.contains("manual-account-connect"));
        assertFalse(screen.contains("manual-cookie"));
        assertFalse(screen.contains("provider-capabilities"));
        assertFalse(screen.contains("provider-health"));
        assertFalse(screen.contains("debug-title"));
        assertFalse(screen.contains("debug-log"));
        assertFalse(screen.contains("\"??????\""));
        assertFalse(screen.contains("\"杩斿洖\""));
        assertFalse(screen.contains("\"鍔犺浇鏇村\""));
        assertFalse(screen.contains("\"????????\""));
        assertTrue(mainActivity.contains("StreamingSearchRenderController streamingSearchRenderController = new StreamingSearchRenderController("));
        assertFalse(mainActivity.contains("private StreamingSearchRenderController streamingSearchRenderController;"));
        assertFalse(mainActivity.contains("new StreamingSearchRenderController.Listener()"));
        assertTrue(mainActivity.contains("streamingSearchRenderListenerFactory.create("));
        assertTrue(mainStreamingSearchRenderListener.contains("internal class MainStreamingSearchRenderListener("));
        assertTrue(mainStreamingSearchRenderListener.contains(": StreamingSearchRenderController.Listener"));
        assertTrue(mainStreamingSearchRenderListener.contains("actionHandler.selectProvider(provider)"));
        assertTrue(mainStreamingSearchRenderListener.contains("actionHandler.search(query)"));
        assertTrue(mainStreamingSearchRenderListener.contains("actionHandler.login(provider)"));
        assertTrue(mainStreamingSearchRenderListener.contains("actionHandler.playStreamingTrack(track)"));
        assertTrue(mainStreamingSearchRenderListener.contains("actionHandler.loadNextPage()"));
        assertTrue(mainStreamingSearchRenderListener.contains("playlistImporter.importStreamingPlaylistFromProviderRef("));
        assertTrue(mainStreamingSearchRenderListener.contains("recommendationActionRunner.runRecommendationAction("));
        assertTrue(mainStreamingSearchRenderListener.contains("chromePublisher.publishStreamingSearchChrome(labels, actions)"));
        assertTrue(mainActivity.contains("streamingSearchActionHandler.search(searchQuery())"));
        assertFalse(mainActivity.contains("viewModel.search(searchQuery())"));
        assertTrue(mainActivity.contains("NETWORK_STREAMING.equals(networkPage())"));
        assertTrue(mainActivity.contains("MainRoutes.NETWORK_STREAMING_HUB.equals(networkPage())"));
        assertTrue(authCallbackController.contains("internal class StreamingAuthCallbackController"));
        assertTrue(authCallbackController.contains("private val streamingViewModel: StreamingViewModel"));
        assertTrue(authCallbackController.contains("private val actionGateway: MainActivityStreamingActionGateway"));
        assertTrue(authCallbackController.contains("fun handleInitialIntent(intent: Intent?): Boolean"));
        assertTrue(authCallbackController.contains("fun handleNewIntent(intent: Intent?): Boolean"));
        assertTrue(authCallbackController.contains("URI(it)"));
        assertTrue(authCallbackController.contains("StreamingProviderName::fromWireName"));
        assertTrue(authCallbackController.contains("streamingViewModel.completeStreamingAuth(provider, uri.toString(), cookieHeader)"));
        assertTrue(authCallbackController.contains("actionGateway.onStreamingLoginSuccess(loggedInProvider)"));
        assertTrue(authCallbackController.contains("streamingViewModel.clearStreamingAuthLaunch()"));
        assertTrue(authCallbackController.contains("StreamingWebAuthActivity.EXTRA_COOKIE_HEADER"));
        assertFalse(mainActivity.contains("new StreamingSearchNavigatorBindings("));
        assertTrue(mainActivity.contains("() -> navigateNetworkPage(MainRoutes.NETWORK_HOME)"));
        assertTrue(mainActivity.contains("streamingPlaylistController.importStreamingPlaylistFromProviderRef(provider, providerPlaylistId)"));
        assertTrue(mainActivity.contains("provider -> streamingPlaylistController.showAccountPlaylistSyncPicker(provider)"));
        assertTrue(mainActivity.contains("provider -> streamingPlaylistController.importStreamingLikedTracks(provider)"));
        assertTrue(mainActivity.contains("action -> runRecommendationAction(action)"));
        assertTrue(mainActivity.contains("() -> streamingPlaylistImportDialogController.showImportDialog()"));
        assertTrue(mainActivity.contains("() -> streamingManualCookieController.showStreamingCookieDialog()"));
        assertTrue(mainActivity.contains("(labels, actions) -> streamingViewModel.updateStreamingSearchChrome(labels, actions)"));
        assertTrue(mainStreamingSearchRenderListener.contains("RecommendationAction.PlayDaily(selectedProviderSource.selectedProvider())"));
        assertTrue(mainStreamingSearchRenderListener.contains("RecommendationAction.PlayHeartbeat(selectedProviderSource.selectedProvider())"));
        assertFalse(mainActivity.contains("new StreamingSearchChromeBindings("));
        assertTrue(mainActivity.contains("StreamingAuthCallbackController"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingCoordinator.kt"));
        assertFalse(mainActivity.contains("StreamingCoordinator streamingCoordinator"));
        assertFalse(mainActivity.contains("new StreamingCoordinator("));
        assertFalse(mainActivity.contains("streamingCoordinator."));
        assertTrue(mainActivity.contains("streamingAuthCallbackController.handleInitialIntent(getIntent())"));
        assertTrue(mainActivity.contains("streamingAuthCallbackController.handleNewIntent(intent)"));
        assertTrue(mainActivity.indexOf("streamingPlaylistController = new StreamingPlaylistController(")
                < mainActivity.indexOf("streamingAuthCallbackController.handleInitialIntent(getIntent())"));
        assertTrue(mainActivity.indexOf("streamingManualCookieController = new StreamingManualCookieController(")
                < mainActivity.indexOf("streamingAuthCallbackController.handleInitialIntent(getIntent())"));
        assertFalse(mainActivity.contains("streamingSearchEventController"));
        assertFalse(mainActivity.contains("StreamingSearchEventController"));
        assertFalse(mainActivity.contains("new StreamingSearchEventController.ContentSink()"));
        assertFalse(mainActivity.contains("streamingActionsController.handleAuthCallback("));
        assertFalse(mainActivity.contains("public void selectProvider(StreamingProviderName provider)"));
        assertFalse(mainActivity.contains("public void playStreamingTrack(StreamingTrack track)"));
        assertTrue(mainStreamingSearchRenderListener.contains("override fun selectProvider(provider: StreamingProviderName)"));
        assertTrue(mainStreamingSearchRenderListener.contains("override fun playStreamingTrack(track: app.yukine.streaming.StreamingTrack)"));
    }

    @Test
    public void streamingAuthLauncherIsKotlinAndKeepsNativeLoginBranches() throws Exception {
        String launcher = read("app/src/main/java/app/yukine/StreamingAuthLauncher.kt");

        assertFalse(exists("app/src/main/java/app/yukine/StreamingAuthLauncher.java"));
        assertTrue(launcher.contains("internal object StreamingAuthLauncher"));
        assertTrue(launcher.contains("StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE"));
        assertTrue(launcher.contains("StreamingWebAuthActivity::class.java"));
        assertTrue(launcher.contains("CustomTabsIntent.Builder()"));
        assertTrue(launcher.contains("Intent.ACTION_VIEW"));
        assertTrue(launcher.contains("Intent.FLAG_ACTIVITY_NEW_TASK"));
    }

    @Test
    public void streamingWebAuthActivityIsKotlinAndKeepsCookieIsolationFlow() throws Exception {
        String activity = read("app/src/main/java/app/yukine/StreamingWebAuthActivity.kt");
        String manifest = read("app/src/main/AndroidManifest.xml");

        assertFalse(exists("app/src/main/java/app/yukine/StreamingWebAuthActivity.java"));
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
    public void liveLyricsNotificationServiceKeepsOppoFluidCloudContract() throws Exception {
        String service = read("app/src/main/java/app/yukine/LiveLyricsNotificationService.kt");
        String playbackService = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String widgetProvider = read("app/src/main/java/app/yukine/playback/EchoPlaybackWidgetProvider.java");
        String restoreReceiver = read("app/src/main/java/app/yukine/playback/PlaybackRestoreReceiver.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackNotificationManager.kt");
        String lyricsOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackLyricsManager.kt");
        String lyricsStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackLyricsStateOwner.java");
        String lyricsSettingsStore = read("app/src/main/java/app/yukine/playback/PlaybackLyricsSettingsStore.java");
        String notificationArtworkBridgeOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkBridgeOwner.java"
        );
        String actionOwner = read("app/src/main/java/app/yukine/playback/service/PlaybackServiceActions.java");
        String manifest = read("app/src/main/AndroidManifest.xml");
        String normalizedPlaybackService = playbackService.replace("\r\n", "\n");

        assertTrue(manifest.contains("android.permission.POST_PROMOTED_NOTIFICATIONS"));
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"));
        assertTrue(manifest.contains("android:name=\".LiveLyricsNotificationService\""));
        assertTrue(manifest.contains("android:foregroundServiceType=\"specialUse\""));
        assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"));
        assertTrue(manifest.contains("User-started custom live text notification"));
        assertTrue(service.contains("class LiveLyricsNotificationService : Service()"));
        assertTrue(service.contains("FloatingLyricsPublisher.state.collectLatest"));
        assertTrue(service.contains("Notification.EXTRA_TEXT_LINES"));
        assertTrue(service.contains("EXTRA_CURRENT_LYRIC"));
        assertTrue(service.contains("CHANNEL_ID = \"echo_live_lyrics_cloud\""));
        assertTrue(service.contains("EXTRA_REQUEST_PROMOTED_ONGOING = \"android.requestPromotedOngoing\""));
        assertTrue(service.contains("putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)"));
        assertTrue(service.contains("setRequestPromotedOngoing"));
        assertTrue(service.contains("setShortCriticalText"));
        assertTrue(service.contains("EXTRA_TITLE = \"extra_title\""));
        assertTrue(service.contains("EXTRA_BODY = \"extra_body\""));
        assertTrue(service.contains("EXTRA_SHORT_TEXT = \"extra_short_text\""));
        assertTrue(service.contains("setLargeIcon"));
        assertTrue(service.contains("PlaybackServiceActions.PREVIOUS"));
        assertTrue(service.contains("PlaybackServiceActions.PAUSE"));
        assertTrue(service.contains("PlaybackServiceActions.RESTORE_AND_PLAY"));
        assertTrue(service.contains("PlaybackServiceActions.NEXT"));
        assertFalse(service.contains("EchoPlaybackService.ACTION_PREVIOUS"));
        assertFalse(service.contains("EchoPlaybackService.ACTION_PAUSE"));
        assertFalse(service.contains("EchoPlaybackService.ACTION_RESTORE_AND_PLAY"));
        assertFalse(service.contains("EchoPlaybackService.ACTION_NEXT"));
        assertTrue(widgetProvider.contains("PlaybackServiceActions.PREVIOUS"));
        assertTrue(widgetProvider.contains("PlaybackServiceActions.PAUSE"));
        assertTrue(widgetProvider.contains("PlaybackServiceActions.RESTORE_AND_PLAY"));
        assertTrue(widgetProvider.contains("PlaybackServiceActions.NEXT"));
        assertFalse(widgetProvider.contains("EchoPlaybackService.ACTION_PREVIOUS"));
        assertFalse(widgetProvider.contains("EchoPlaybackService.ACTION_PAUSE"));
        assertFalse(widgetProvider.contains("EchoPlaybackService.ACTION_RESTORE_AND_PLAY"));
        assertFalse(widgetProvider.contains("EchoPlaybackService.ACTION_NEXT"));
        assertTrue(restoreReceiver.contains("PlaybackServiceActions.RESTORE_AND_PLAY"));
        assertTrue(restoreReceiver.contains("PlaybackServiceActions.RESTORE"));
        assertFalse(restoreReceiver.contains("EchoPlaybackService.ACTION_RESTORE_AND_PLAY"));
        assertFalse(restoreReceiver.contains("EchoPlaybackService.ACTION_RESTORE"));
        assertFalse(playbackService.contains("LiveLyricsNotificationService.start(this)"));
        assertFalse(playbackService.contains("LiveLyricsNotificationService.stop(this)"));
        assertFalse(playbackService.contains("EXTRA_CURRENT_LYRIC = \"app.yukine.extra.CURRENT_LYRIC\""));
        assertFalse(playbackService.contains("EXTRA_LYRIC_TRACK_TITLE = \"app.yukine.extra.LYRIC_TRACK_TITLE\""));
        assertFalse(playbackService.contains("EMPTY_NOTIFICATION_TITLE"));
        assertFalse(playbackService.contains("EMPTY_NOTIFICATION_TEXT"));
        assertFalse(playbackService.contains("EXTRA_REQUEST_PROMOTED_ONGOING"));
        assertFalse(playbackService.contains("NOTIFICATION_ACCENT"));
        assertFalse(playbackService.contains("NOTIFICATION_ARTWORK_TARGET_PX"));
        assertFalse(playbackService.contains("NOTIFICATION_ARTWORK_CACHE_ENTRIES"));
        assertFalse(playbackService.contains("FOREGROUND_NOTIFICATION_MIN_INTERVAL_MS"));
        assertFalse(playbackService.contains("BACKGROUND_NOTIFICATION_MIN_INTERVAL_MS"));
        assertFalse(playbackService.contains("BACKGROUND_LYRIC_NOTIFICATION_MIN_INTERVAL_MS"));
        assertFalse(playbackService.contains("repository.loadStatusBarLyricsEnabled()"));
        assertFalse(playbackService.contains("playbackLyricsManager.setStatusBarLyricsEnabled(repository.loadStatusBarLyricsEnabled())"));
        assertTrue(playbackService.contains("PlaybackLyricsSettingsStore.fromRepository(repository).restoreInto(playbackLyricsManager)"));
        assertFalse(playbackService.contains("private PlaybackLyricsStateOwner playbackLyricsStateOwner;"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackActiveStateOwner.java")));
        assertFalse(playbackService.contains("PlaybackActiveStateOwner"));
        assertTrue(playbackService.contains("final PlaybackLyricsStateOwner playbackLyricsStateOwner = new PlaybackLyricsStateOwner("));
        assertTrue(playbackService.contains("                playbackLyricsStateOwner,"));
        assertFalse(playbackService.contains("PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState("));
        assertTrue(normalizedPlaybackService.contains(
                "                playbackQueueStateOwner,\n" +
                        "                playbackPlayerStateOwner::isPlaying,\n" +
                        "                playbackCurrentTrackPreparationRuntimeOwner::preparing"));
        assertFalse(normalizedPlaybackService.contains(
                "                        currentTrackSupplier,\n" +
                        "                        playbackPlayerStateOwner::isPlaying,"));
        assertFalse(playbackService.contains("new PlaybackLyricsManager.StateProvider()"));
        assertFalse(playbackService.contains("new PlaybackLyricsStateOwner.PlaybackStateProvider()"));
        assertTrue(lyricsOwner.contains("interface NotificationBridge"));
        assertTrue(lyricsOwner.contains("fun hasNotificationWorthyState(): Boolean"));
        assertTrue(lyricsOwner.contains("fun notifyMediaNotification(force: Boolean)"));
        assertTrue(lyricsOwner.contains("fun refreshPlaybackSession()"));
        assertTrue(lyricsOwner.contains("override fun setStatusBarLyricsEnabled(enabled: Boolean)"));
        assertTrue(lyricsOwner.contains("private var released = false"));
        assertTrue(lyricsOwner.contains("if (released)"));
        assertTrue(lyricsOwner.contains("notificationBridge.refreshPlaybackSession()"));
        assertTrue(lyricsOwner.contains("notificationBridge.notifyMediaNotification(true)"));
        assertTrue(lyricsOwner.contains("notificationBridge.hasNotificationWorthyState()"));
        assertTrue(lyricsStateOwner.contains("final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider"));
        assertFalse(lyricsStateOwner.contains("interface AppVisibilityProvider"));
        assertFalse(lyricsStateOwner.contains("interface PlaybackStateProvider"));
        assertFalse(lyricsStateOwner.contains("static PlaybackStateProvider playbackStateProviderFromPlaybackState("));
        assertTrue(lyricsStateOwner.contains("import java.util.function.BooleanSupplier;"));
        assertFalse(lyricsStateOwner.contains("import java.util.function.Supplier;"));
        assertTrue(lyricsStateOwner.contains("private final BooleanSupplier appVisibilitySupplier;"));
        assertTrue(lyricsStateOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertTrue(lyricsStateOwner.contains("private final BooleanSupplier playingStateProvider;"));
        assertTrue(lyricsStateOwner.contains("private final BooleanSupplier preparingStateProvider;"));
        assertTrue(lyricsStateOwner.contains("return appVisibilitySupplier.getAsBoolean();"));
        assertFalse(lyricsStateOwner.contains("PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider"));
        assertFalse(lyricsStateOwner.contains("queueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertFalse(lyricsStateOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.queueStateSnapshot();"));
        assertFalse(lyricsStateOwner.contains("Supplier<Track> currentTrackSupplier"));
        assertFalse(lyricsStateOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertTrue(lyricsStateOwner.contains("PlaybackQueueStateOwner queueStateOwner"));
        assertTrue(lyricsStateOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertFalse(lyricsStateOwner.contains("return playbackStateProvider.currentTrack();"));
        assertFalse(lyricsStateOwner.contains("return playbackStateProvider.isPlaying();"));
        assertFalse(lyricsStateOwner.contains("return playbackStateProvider.isPreparing();"));
        assertTrue(lyricsStateOwner.contains("return playingStateProvider != null && playingStateProvider.getAsBoolean();"));
        assertTrue(lyricsStateOwner.contains("return preparingStateProvider != null && preparingStateProvider.getAsBoolean();"));
        assertFalse(lyricsOwner.contains("fun hasNotificationWorthyState(): Boolean {\n        return stateProvider.currentTrack()"));
        assertFalse(lyricsOwner.contains("fun isQueueEmpty(): Boolean"));
        assertFalse(lyricsOwner.contains("fun isPreparing(): Boolean\n        fun notifyMediaNotification(force: Boolean)"));
        assertFalse(lyricsOwner.contains("fun isPreparing(): Boolean\n        fun refreshPlaybackSession()"));
        assertTrue(lyricsSettingsStore.contains("interface LyricsSettings"));
        assertTrue(lyricsSettingsStore.contains("void restoreInto(LyricsPublisher lyricsPublisher)"));
        assertTrue(lyricsSettingsStore.contains("repository::loadStatusBarLyricsEnabled"));
        assertTrue(lyricsSettingsStore.contains("lyricsPublisher.setStatusBarLyricsEnabled(lyricsSettings.loadStatusBarLyricsEnabled())"));
        assertTrue(lyricsSettingsStore.contains("static Consumer<Boolean> statusBarLyricsEnabledActionFromSupplier("));
        assertTrue(lyricsSettingsStore.contains("lyricsPublisher.setStatusBarLyricsEnabled(enabled);"));
        assertTrue(playbackService.contains(
                "PlaybackLyricsSettingsStore.statusBarLyricsEnabledActionFromSupplier(() -> playbackLyricsManager)"));
        assertTrue(normalizedPlaybackService.contains(
                "public void setStatusBarLyricsEnabled(boolean enabled) {\n        statusBarLyricsEnabledAction.accept(enabled);\n    }"));
        assertFalse(playbackService.contains("playbackLyricsManager.setStatusBarLyricsEnabled(enabled);"));
        assertFalse(normalizedPlaybackService.contains("public void setStatusBarLyricsEnabled(boolean enabled) {\n        if (playbackLyricsManager != null) {\n            playbackLyricsManager.setStatusBarLyricsEnabled(enabled);\n        }\n        refreshPlaybackSession();"));
        assertFalse(normalizedPlaybackService.contains("public void setStatusBarLyricsEnabled(boolean enabled) {\n        if (playbackLyricsManager != null) {\n            playbackLyricsManager.setStatusBarLyricsEnabled(enabled);\n        }\n        publishPlaybackNotification(true);"));
        assertFalse(normalizedPlaybackService.contains("\n    private boolean hasNotificationWorthyState() {"));
        assertTrue(playbackService.contains("playbackNotificationManager.hasNotificationWorthyState()"));
        assertFalse(playbackService.contains("PlaybackNotificationManager.isNotificationWorthy("));
        assertTrue(playbackService.contains("playbackNotificationManager.lyricsNotificationBridge(playbackSessionRefresher)"));
        assertFalse(playbackService.contains("playbackNotificationManager.lyricsNotificationBridge(EchoPlaybackService.this::refreshPlaybackSession)"));
        assertFalse(playbackService.contains("new PlaybackLyricsManager.NotificationBridge()"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackSessionRefreshOwner.java")));
        assertFalse(playbackService.contains("PlaybackSessionRefreshOwner"));
        assertFalse(playbackService.contains("playbackSessionRefreshOwner"));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "interface SessionRefresher extends PlaybackNotificationManager.SessionRefresher"));
        assertTrue(notificationArtworkBridgeOwner.contains("sessionOperations.refreshPlayer();"));
        assertFalse(playbackService.contains("return currentTrack() != null || !isQueueEmpty() || playbackRuntimeStateManager.preparing() || isPlaying();"));
        assertFalse(playbackService.contains("private boolean notificationWorthyState()"));
        assertTrue(owner.contains("class PlaybackNotificationManager"));
        assertTrue(owner.contains("interface ForegroundController"));
        assertTrue(owner.contains("interface StateProvider"));
        assertFalse(owner.contains("interface ForegroundPresenter"));
        assertTrue(owner.contains("private val foregroundController: ForegroundController"));
        assertTrue(owner.contains("private val stateProvider: StateProvider"));
        assertTrue(owner.contains("foregroundController.activityPendingIntent()"));
        assertTrue(owner.contains("foregroundController.serviceActionPendingIntent(PlaybackServiceActions.PREVIOUS, 1)"));
        assertTrue(owner.contains("foregroundController.startPlaybackForeground(notification)"));
        assertTrue(owner.contains("stateProvider.currentTrack()"));
        assertTrue(owner.contains("fun hasNotificationWorthyState(): Boolean"));
        assertTrue(owner.contains("fun isNotificationWorthy("));
        assertTrue(owner.contains("stateProvider.isQueueEmpty()"));
        assertTrue(owner.contains("stateProvider.playbackSessionPlatformToken()"));
        assertTrue(owner.contains("fun updateMediaNotification(force: Boolean)"));
        assertTrue(owner.contains("fun interface SessionRefresher"));
        assertTrue(owner.contains("fun lyricsNotificationBridge(sessionRefresher: SessionRefresher): PlaybackLyricsManager.NotificationBridge"));
        assertTrue(owner.contains("callbacks.publishPlaybackNotification(force)"));
        assertTrue(owner.contains("fun playbackNotification(track: Track?)"));
        assertTrue(owner.contains("fun mediaMetadataForTrack(track: Track): MediaMetadata"));
        assertTrue(owner.contains("fun shortCriticalText(value: String)"));
        assertFalse(playbackService.contains("private MediaMetadata mediaMetadataForPlaybackTrack"));
        assertFalse(playbackService.contains("this::mediaMetadataForPlaybackTrack"));
        assertTrue(playbackService.contains("playbackNotificationManager::mediaMetadataForTrack"));
        assertTrue(owner.contains("PlaybackServiceActions.PREVIOUS"));
        assertTrue(owner.contains("PlaybackServiceActions.PAUSE"));
        assertTrue(owner.contains("PlaybackServiceActions.RESTORE_AND_PLAY"));
        assertTrue(owner.contains("PlaybackServiceActions.NEXT"));
        assertFalse(owner.contains("\"app.yukine.action.PREVIOUS\""));
        assertFalse(owner.contains("\"app.yukine.action.PAUSE\""));
        assertFalse(owner.contains("\"app.yukine.action.RESTORE_AND_PLAY\""));
        assertFalse(owner.contains("\"app.yukine.action.NEXT\""));
        assertTrue(playbackService.contains("public static final String ACTION_PREVIOUS = PlaybackServiceActions.PREVIOUS;"));
        assertTrue(playbackService.contains("playbackNotificationManager.handleServiceAction(action);"));
        assertTrue(owner.contains("PlaybackServiceActions.isPlaybackServiceAction(action)"));
        assertTrue(actionOwner.contains("public final class PlaybackServiceActions"));
        assertTrue(actionOwner.contains("public static boolean isPlaybackServiceAction(String action)"));
    }

    @Test
    public void playbackNotificationChannelIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackNotificationChannelOwner.kt");

        assertFalse(service.contains("private NotificationManager notificationManager()"));
        assertFalse(service.contains("private void createNotificationChannel()"));
        assertFalse(service.contains("private static final String CHANNEL_ID"));
        assertTrue(service.contains("new PlaybackNotificationChannelOwner(this).createNotificationChannel()"));
        assertTrue(owner.contains("class PlaybackNotificationChannelOwner"));
        assertTrue(owner.contains("fun createNotificationChannel()"));
        assertTrue(owner.contains("CHANNEL_ID = \"echo_next_playback\""));
        assertTrue(owner.contains("Yukine playback controls"));
    }

    @Test
    public void playbackAudioEffectsAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String playerFactory = read("app/src/main/java/app/yukine/playback/manager/PlaybackPlayerFactory.kt");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackAudioEffectManager.kt");
        String settingsStore = read("app/src/main/java/app/yukine/playback/PlaybackAudioEffectSettingsStore.java");

        assertFalse(service.contains("private AudioEffectSettings audioEffectSettings"));
        assertFalse(service.contains("repository.loadAudioEffectSettings()"));
        assertFalse(service.contains("repository.saveAudioEffectSettings("));
        assertFalse(service.contains("private Equalizer equalizer"));
        assertFalse(service.contains("private BassBoost bassBoost"));
        assertFalse(service.contains("private Virtualizer virtualizer"));
        assertFalse(service.contains("private LoudnessEnhancer loudnessEnhancer"));
        assertFalse(service.contains("private void bindAudioEffects()"));
        assertFalse(service.contains("private void applyEqualizerSettings()"));
        assertFalse(service.contains("private void releaseAudioEffects()"));
        assertTrue(service.contains("PlaybackAudioEffectSettingsStore.fromRepository(repository)"));
        assertTrue(service.contains("playbackAudioEffectSettingsStore.restore()"));
        assertTrue(service.contains("playbackAudioEffectSettingsStore.current()"));
        assertTrue(service.contains("playbackAudioEffectSettingsStore.apply(settings)"));
        assertTrue(service.contains("audioEffectManager.bind(player, appliedSettings)"));
        assertTrue(service.contains("audioEffectManager.bind(player, audioEffectSettings())"));
        assertTrue(playerFactory.contains("audioEffectManager.release()"));
        assertTrue(owner.contains("class PlaybackAudioEffectManager"));
        assertTrue(owner.contains("fun bind(player: ExoPlayer?, settings: AudioEffectSettings?)"));
        assertTrue(owner.contains("fun release()"));
        assertTrue(owner.contains("Equalizer(0, sessionId)"));
        assertTrue(owner.contains("BassBoost(0, sessionId)"));
        assertTrue(owner.contains("Virtualizer(0, sessionId)"));
        assertTrue(owner.contains("LoudnessEnhancer(sessionId)"));
        assertTrue(settingsStore.contains("interface AudioEffectSettingsPersistence"));
        assertTrue(settingsStore.contains("AudioEffectSettings restore()"));
        assertTrue(settingsStore.contains("AudioEffectSettings apply(AudioEffectSettings settings)"));
        assertTrue(settingsStore.contains("AudioEffectSettings current()"));
        assertTrue(settingsStore.contains("repository.loadAudioEffectSettings()"));
        assertTrue(settingsStore.contains("repository.saveAudioEffectSettings(settings)"));
    }

    @Test
    public void playbackQueuePersistenceIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedService = service.replace("\r\n", "\n");
        String queueManagerOwner = read("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt");
        String queueStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueStateOwner.java");
        String queueStoreOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueStore.kt");
        String positionOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackPositionManager.kt");
        String queuePersistenceOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackQueuePersistenceOwner.java"
        );
        String currentPreparationQueueOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackCurrentTrackPreparationQueueOwner.java"
        );
        String queueRestoreOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueRestoreOwner.java");

        assertFalse(service.contains("repository.loadPlaybackQueue()"));
        assertFalse(service.contains("repository.savePlaybackQueue("));
        assertFalse(service.contains("repository.loadPlaybackPositionTrackId()"));
        assertFalse(service.contains("repository.loadPlaybackPositionMs()"));
        assertFalse(service.contains("repository.savePlaybackPosition("));
        assertFalse(service.contains("repository.savePlaybackResumeRequested("));
        assertTrue(service.contains("playbackQueueRestoreOwner().restorePlaybackQueue();"));
        assertFalse(service.contains("playbackQueueManager.restorePlaybackQueue()"));
        assertTrue(queueRestoreOwner.contains("playbackQueueManager.restorePlaybackQueue();"));
        assertTrue(service.contains("playbackQueueRestoreOwner().restoreLastPlayback(playWhenRestored);"));
        assertFalse(service.contains("playbackQueueManager.restoreLastPlayback(playWhenRestored)"));
        assertTrue(queueRestoreOwner.contains("playbackQueueManager.restoreLastPlayback(playWhenRestored)"));
        assertFalse(service.contains("repository.loadPlaybackResumeRequested()"));
        assertFalse(service.contains("boolean shouldPlay = playWhenRestored ||"));
        assertFalse(service.contains("prepareCurrent(shouldPlay)"));
        assertTrue(queueManagerOwner.contains("data class RestorePlaybackResult"));
        assertTrue(queueManagerOwner.contains("fun restorePlaybackQueue()"));
        assertFalse(queueManagerOwner.contains("fun restorePlaybackQueue(): QueueStateSnapshot"));
        assertTrue(queueManagerOwner.contains("fun restoreLastPlayback(playWhenRestored: Boolean): RestorePlaybackResult"));
        assertTrue(queueManagerOwner.contains("playWhenReady = playWhenRestored || queueStore.loadResumeRequested()"));
        assertFalse(service.contains("queueStore().save("));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueuePositionPersistenceOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackShutdownQueueLifecycleStoreOwner.java")));
        assertFalse(service.contains("PlaybackQueuePositionPersistenceOwner"));
        assertFalse(service.contains("playbackQueuePositionPersistenceOwner"));
        assertFalse(service.contains("PlaybackShutdownQueueLifecycleStoreOwner"));
        assertFalse(service.contains("playbackShutdownQueueLifecycleStoreOwner"));
        assertTrue(service.contains("PlaybackQueuePersistenceOwner persistenceOwner = playbackQueuePersistenceOwner();"));
        assertTrue(service.contains("persistenceOwner.persistQueueState();"));
        assertFalse(service.contains("playbackQueueManager.persistQueueState();"));
        assertTrue(queuePersistenceOwner.contains("playbackQueueManager.persistQueueState();"));
        assertFalse(service.contains("private void persistPlaybackQueue()"));
        assertTrue(queueManagerOwner.contains("fun persistQueueState()"));
        assertTrue(queueManagerOwner.contains("queueStore.save("));
        assertFalse(service.contains("queueStore().loadPlaybackPositionTrackId()"));
        assertFalse(service.contains("queueStore().loadPlaybackPositionMs()"));
        assertFalse(service.contains("queueStore().savePlaybackPosition("));
        assertTrue(service.contains("playbackPositionManager.persistCurrentPosition(force);"));
        assertFalse(service.contains("private final PlaybackQueuePersistenceOwner playbackQueuePersistenceOwner"));
        assertFalse(service.contains("private PlaybackQueuePersistenceOwner playbackQueuePersistenceOwner;"));
        assertTrue(service.contains("final PlaybackQueuePersistenceOwner playbackQueuePersistenceOwner = new PlaybackQueuePersistenceOwner("));
        assertTrue(service.contains("private PlaybackQueuePersistenceOwner playbackQueuePersistenceOwner()"));
        assertFalse(service.contains("withPlaybackQueuePersistenceOwner("));
        assertFalse(service.contains("Consumer<PlaybackQueuePersistenceOwner>"));
        assertTrue(service.contains("new PlaybackQueueStoreImpl(repository)"));
        assertFalse(service.contains("PlaybackQueuePersistenceOwner.fromPlaybackQueueManager("));
        assertFalse(queuePersistenceOwner.contains("static PlaybackQueuePersistenceOwner fromPlaybackQueueManager("));
        assertFalse(service.contains("private void persistPlaybackPositionThrottled(boolean force)"));
        assertFalse(service.contains("EchoPlaybackService.this::persistPlaybackPositionThrottled"));
        assertFalse(service.contains("persistPlaybackPositionThrottled(true);"));
        assertFalse(service.contains("playbackQueuePersistenceOwner::persistCurrentPlaybackPosition"));
        assertTrue(service.contains("EchoPlaybackService.this::persistCurrentPlaybackPosition"));
        assertTrue(service.contains("private void persistCurrentPlaybackPosition(boolean force)"));
        assertFalse(service.contains("withPlaybackQueuePersistenceOwner(owner -> owner.persistCurrentPlaybackPosition(force));"));
        assertFalse(service.contains("playbackQueuePersistenceOwner.persistCurrentPlaybackPosition(true)"));
        assertFalse(service.contains("playbackQueueManager.persistCurrentPlaybackPosition(force)"));
        assertFalse(queuePersistenceOwner.contains("playbackQueueManager.persistCurrentPlaybackPosition(force);"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackPositionStateOwner.java")));
        assertFalse(service.contains("PlaybackPositionStateOwner"));
        assertTrue(service.contains("PlaybackPositionManager.stateProviderFromPlaybackState("));
        String positionStateProviderWiring = normalizedService.substring(
                normalizedService.indexOf("PlaybackPositionManager.stateProviderFromPlaybackState("),
                normalizedService.indexOf(
                        "                )\n        );\n        playbackSleepTimerCommandOwner"
                )
        );
        assertFalse(positionStateProviderWiring.contains("                        playbackQueueStateOwner::currentTrack,\n"));
        assertFalse(positionStateProviderWiring.contains("                        () -> playbackQueueManager,\n"));
        assertTrue(positionStateProviderWiring.contains("                        playbackQueueManager,\n"));
        assertFalse(positionStateProviderWiring.contains(
                "playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertTrue(service.contains("                        playbackPlayerStateOwner::positionMs"));
        assertFalse(service.contains("new PlaybackPositionManager.StateProvider()"));
        assertFalse(service.contains("private long restoredPositionFor(Track track)"));
        assertFalse(service.contains("playbackPositionManager.restoredPositionFor(track)"));
        assertFalse(service.contains("playbackQueueManager.restoredPositionFor(track)"));
        assertFalse(currentPreparationQueueOwner.contains("playbackQueueManager.restoredPositionFor(track);"));
        assertTrue(service.contains("playbackPositionManager::restoredPositionFor"));
        assertTrue(service.contains("private PlaybackCurrentTrackPreparationQueueOwner playbackCurrentTrackPreparationQueueOwner"));
        assertTrue(service.contains("new PlaybackCurrentTrackPreparationQueueOwner("));
        assertFalse(service.contains("PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager("));
        assertFalse(service.contains("private void clearRestoredPosition()"));
        assertFalse(service.contains("tracks -> mediaSourceProvider.mediaSourcesForTracks("));
        assertFalse(service.contains("private PlaybackQueueStore queueStore"));
        assertFalse(service.contains("private PlaybackQueueStore queueStore()"));
        assertFalse(service.contains("queueStore().saveResumeRequested("));
        assertFalse(service.contains("private void savePlaybackResumeRequested(boolean requested)"));
        assertTrue(service.contains("PlaybackQueueStore queueStore = new PlaybackQueueStoreImpl(repository);"));
        assertTrue(service.contains("persistenceOwner.savePlaybackResumeRequested("));
        assertFalse(service.contains("playbackQueueManager.savePlaybackResumeRequested("));
        assertFalse(queuePersistenceOwner.contains("playbackQueueManager.savePlaybackResumeRequested(requested);"));
        assertTrue(queuePersistenceOwner.contains("queueStore.saveResumeRequested(requested);"));
        assertTrue(queueManagerOwner.contains("private fun savePlaybackResumeRequested(requested: Boolean)"));
        assertFalse(queueManagerOwner.contains("\n    fun savePlaybackResumeRequested(requested: Boolean)"));
        assertTrue(queueStoreOwner.contains("interface PlaybackQueueStore"));
        assertTrue(queueStoreOwner.contains("fun load(): PlaybackQueueState"));
        assertTrue(queueStoreOwner.contains("fun save(tracks: List<Track>, currentIndex: Int)"));
        assertTrue(queueStoreOwner.contains("fun savePlaybackPosition(trackId: Long, positionMs: Long)"));
        assertTrue(queueStoreOwner.contains("fun saveResumeRequested(requested: Boolean)"));
        assertTrue(positionOwner.contains("class PlaybackPositionManager"));
        assertTrue(positionOwner.contains("fun restoredPositionFor(track: Track?)"));
        assertTrue(positionOwner.contains("fun consumeRestoredPositionAfterPrepare(startPositionMs: Long)"));
        assertTrue(positionOwner.contains("fun clearRestoredPosition()"));
        assertTrue(positionOwner.contains("fun persistCurrentPosition(force: Boolean)"));
        assertTrue(positionOwner.contains("fun setExplicitRestoredPosition(track: Track?, positionMs: Long)"));
        assertTrue(positionOwner.contains("fun stateProviderFromPlaybackState("));
        assertFalse(positionOwner.contains("currentTrackSupplier: Supplier<Track?>?"));
        assertFalse(positionOwner.contains("queueStateSupplier: Supplier<PlaybackQueueManager.QueueStateSnapshot?>?"));
        assertFalse(positionOwner.contains("queueManagerSupplier: Supplier<PlaybackQueueManager?>?"));
        assertTrue(positionOwner.contains("playbackQueueManager: PlaybackQueueManager?"));
        assertTrue(positionOwner.contains("playbackPositionSupplier: LongSupplier?"));
        assertFalse(positionOwner.contains("override fun currentTrack(): Track? = currentTrackSupplier?.get()"));
        assertFalse(positionOwner.contains(
                "override fun currentTrack(): Track? = queueManagerSupplier?.get()?.queueStateSnapshot()?.currentTrack"));
        assertTrue(positionOwner.contains("override fun currentTrack(): Track? = playbackQueueManager?.queueStateSnapshot()?.currentTrack"));
        assertFalse(positionOwner.contains("queueStateSupplier?.get()?.currentTrack"));
        assertTrue(positionOwner.contains("override fun positionMs(): Long = playbackPositionSupplier?.asLong ?: 0L"));
        assertFalse(queueStateOwner.contains("Supplier<PlaybackQueueManager.QueueStateSnapshot>"));
        assertFalse(queueStateOwner.contains("public PlaybackQueueManager.QueueStateSnapshot get()"));
        assertFalse(queueStateOwner.contains("return queueStateSnapshot();"));
    }

    @Test
    public void playbackQueueMutationKeepsOneClearlyNamedEntryPointCluster() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt");
        String mediaSourceProvider = read(
                "feature/playback/src/main/java/app/yukine/playback/manager/PlaybackMediaSourceProvider.kt"
        );
        String positionOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackPositionManager.kt");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueCommandOwner.java");
        String playerStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackPlayerStateOwner.java");
        String mirroredPlayerOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueMirroredPlayerOwner.java");
        String mirroredTrackMatcherOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackMirroredQueueTrackMatcherOwner.java"
        );
        String mirroredTransitionOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackQueueMirroredTransitionOwner.java"
        );
        String stateSnapshotOwner = read("app/src/main/java/app/yukine/playback/PlaybackStateSnapshotOwner.java");
        String streamingDiagnosticsOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackStreamingDiagnosticsRecorderOwner.java"
        );
        String currentPreparationOwner = read("app/src/main/java/app/yukine/playback/PlaybackCurrentTrackPreparationOwner.java");
        String currentPreparationQueueOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackCurrentTrackPreparationQueueOwner.java"
        );
        String normalizedService = service.replace("\r\n", "\n");
        String queuePlaybackActions = owner.substring(
                owner.indexOf("interface QueuePlaybackActions"),
                owner.indexOf("interface StreamingRestoreProvider"));

        assertTrue(service.contains("private PlaybackQueueManager playbackQueueManager"));
        assertTrue(owner.contains("private val queue: MutableList<Track>"));
        assertTrue(owner.contains("import java.util.concurrent.CopyOnWriteArrayList"));
        assertTrue(owner.contains("CopyOnWriteArrayList()"));
        assertFalse(owner.contains("interface QueueProvider"));
        assertFalse(owner.contains("queueProvider"));
        assertFalse(owner.contains("PlaybackMediaSourceProvider"));
        assertEquals(1, countOccurrences(owner, "    constructor("));
        assertFalse(owner.contains("NoopQueuePlaybackActions, playbackPositionManager"));
        assertFalse(owner.contains("NoopStreamingRestoreProvider, NoopMirroredQueuePlayer"));
        assertFalse(service.contains("new PlaybackQueueManager.QueueProvider()"));
        assertFalse(service.contains("CopyOnWriteArrayList<Track> queue"));
        assertFalse(service.contains("new CopyOnWriteArrayList<>()"));
        String queueMutationOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueMutationOwner.java");
        String queueNavigationOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueNavigationOwner.java");
        String queueRestoreOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueRestoreOwner.java");
        String queuePersistenceOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackQueuePersistenceOwner.java"
        );
        String queueCompletionOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueCompletionOwner.java");
        String currentReplacementOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackCurrentTrackReplacementOwner.java"
        );
        assertFalse(service.contains("private final PlaybackQueueMutationOwner playbackQueueMutationOwner"));
        assertFalse(service.contains("private PlaybackQueueMutationOwner playbackQueueMutationOwner;"));
        assertTrue(service.contains("private PlaybackQueueMutationOwner playbackQueueMutationOwner()"));
        assertFalse(service.contains("withPlaybackQueueMutationOwner("));
        assertFalse(service.contains("Consumer<PlaybackQueueMutationOwner>"));
        assertTrue(service.contains("new PlaybackQueueMutationOwner("));
        assertTrue(service.replace("\r\n", "\n").contains(
                "new PlaybackQueueMutationOwner(\n"
                        + "                playbackQueueManager,\n"
                        + "                EchoPlaybackService.this::stopAndClear"
        ));
        assertFalse(service.contains("playbackQueueStateOwner,\n                EchoPlaybackService.this::stopAndClear"));
        assertTrue(service.contains("EchoPlaybackService.this::stopAndClear"));
        assertFalse(service.contains("PlaybackQueueMutationOwner.fromPlaybackQueueManager("));
        assertFalse(queueMutationOwner.contains("static PlaybackQueueMutationOwner fromPlaybackQueueManager("));
        assertTrue(queueMutationOwner.contains("private final PlaybackQueueStateOwner queueStateOwner"));
        assertFalse(queueMutationOwner.contains(
                "PlaybackQueueStateOwner queueStateOwner,\n            Runnable stopAndClearAction"));
        assertTrue(queueMutationOwner.contains("this.queueStateOwner = new PlaybackQueueStateOwner(playbackQueueManager);"));
        assertFalse(queueMutationOwner.contains("!playbackQueueManager.queueStateSnapshot().isQueueEmpty()"));
        assertTrue(queueMutationOwner.contains("!queueStateOwner.isQueueEmpty()"));
        assertFalse(queueMutationOwner.contains("!playbackQueueManager.queueSnapshot().isEmpty()"));
        assertFalse(service.contains("private final PlaybackQueueNavigationOwner playbackQueueNavigationOwner"));
        assertFalse(service.contains("private PlaybackQueueNavigationOwner playbackQueueNavigationOwner;"));
        assertTrue(service.contains("private PlaybackQueueNavigationOwner playbackQueueNavigationOwner()"));
        assertFalse(service.contains("withPlaybackQueueNavigationOwner("));
        assertFalse(service.contains("Consumer<PlaybackQueueNavigationOwner>"));
        assertTrue(service.contains("new PlaybackQueueNavigationOwner("));
        assertFalse(service.contains("PlaybackQueueNavigationOwner.fromPlaybackQueueManager("));
        assertFalse(queueNavigationOwner.contains("static PlaybackQueueNavigationOwner fromPlaybackQueueManager("));
        assertTrue(service.contains("this::onMirroredQueueReused"));
        assertFalse(service.contains("private final PlaybackQueueRestoreOwner playbackQueueRestoreOwner"));
        assertFalse(service.contains("private PlaybackQueueRestoreOwner playbackQueueRestoreOwner;"));
        assertTrue(service.contains("private PlaybackQueueRestoreOwner playbackQueueRestoreOwner()"));
        assertFalse(service.contains("withPlaybackQueueRestoreOwner("));
        assertFalse(service.contains("Consumer<PlaybackQueueRestoreOwner>"));
        assertTrue(service.contains("new PlaybackQueueRestoreOwner("));
        assertFalse(service.contains("PlaybackQueueRestoreOwner.fromPlaybackQueueManager("));
        assertFalse(queueRestoreOwner.contains("static PlaybackQueueRestoreOwner fromPlaybackQueueManager("));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueueResumeRequestOwner.java")));
        assertFalse(service.contains("PlaybackQueueResumeRequestOwner"));
        assertFalse(service.contains("playbackQueueResumeRequestOwner"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueueStopClearOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackQueueStopClearOwnerTest.java")));
        assertFalse(service.contains("PlaybackQueueStopClearOwner"));
        assertFalse(service.contains("playbackQueueStopClearOwner"));
        assertFalse(service.contains("private PlaybackQueueCompletionOwner playbackQueueCompletionOwner;"));
        assertFalse(service.contains("private final PlaybackQueueCompletionOwner playbackQueueCompletionOwner"));
        assertFalse(service.contains(
                "private final PlaybackQueueCompletionOwner.CompletionBoundary playbackQueueCompletionBoundary"));
        assertTrue(service.contains("new PlaybackQueueCompletionOwner("));
        assertFalse(service.contains("PlaybackQueueCompletionOwner.fromPlaybackQueueManager("));
        assertFalse(queueCompletionOwner.contains("static PlaybackQueueCompletionOwner fromPlaybackQueueManager("));
        assertFalse(service.contains("PlaybackQueueCompletionOwner.fromPlaybackQueueManagerProvider("));
        assertFalse(queueCompletionOwner.contains("private final PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions;"));
        assertFalse(queueCompletionOwner.contains("PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions"));
        assertFalse(queueCompletionOwner.contains("void prepareCurrent(boolean playWhenReady);"));
        assertFalse(queueCompletionOwner.contains("void repeatCurrent();"));
        String queueCompletionBoundaryWiring = normalizedService.substring(
                normalizedService.indexOf(
                        "private PlaybackQueueCompletionOwner playbackQueueCompletionOwner()"),
                normalizedService.indexOf("    private PlaybackQueueNavigationOwner playbackQueueNavigationOwner()")
        );
        assertFalse(queueCompletionBoundaryWiring.contains("                playbackQueueCommandOwner,"));
        assertFalse(queueCompletionBoundaryWiring.contains("playbackQueueCommandOwner.prepareCurrent(true);"));
        assertFalse(queueCompletionBoundaryWiring.contains("public void prepareCurrent(boolean playWhenReady)"));
        assertFalse(queueCompletionBoundaryWiring.contains("EchoPlaybackService.this.prepareCurrent(playWhenReady);"));
        assertTrue(normalizedService.contains(
                "playbackQueueCompletionOwner().playAfterCompletion();"));
        assertTrue(normalizedService.contains(
                "playbackQueueCompletionOwner().prepareStopAndClearPlaybackState();"));
        assertTrue(normalizedService.contains(
                "playbackQueueCompletionOwner().prepareStopAtEndOfQueue();"));
        assertTrue(normalizedService.contains(
                "playbackQueueCompletionOwner().stopAfterAutomaticAdvance(transition.completedIndex());"));
        assertFalse(normalizedService.contains(
                "playbackQueueCompletionOwner().prepareStopAfterAutomaticAdvance(completedIndex);"));
        assertFalse(normalizedService.contains("withPlaybackQueueCompletionOwner("));
        assertFalse(normalizedService.contains("Consumer<PlaybackQueueCompletionOwner>"));
        assertTrue(normalizedService.contains(
                "private PlaybackQueueCompletionOwner playbackQueueCompletionOwner()"));
        assertTrue(normalizedService.contains(
                "return new PlaybackQueueCompletionOwner(\n"
                        + "                playbackQueueManager,\n"
                        + "                new PlaybackQueueCompletionOwner.CompletionBoundary() {"));
        assertTrue(queueCompletionBoundaryWiring.contains("public void stopAndClear()"));
        assertTrue(queueCompletionBoundaryWiring.contains("EchoPlaybackService.this.stopAndClear();"));
        assertTrue(queueCompletionBoundaryWiring.contains("public void stopAtEndOfQueue()"));
        assertTrue(queueCompletionBoundaryWiring.contains("EchoPlaybackService.this.stopAtEndOfQueue();"));
        assertTrue(queueCompletionBoundaryWiring.contains("public void skipToNext()"));
        assertTrue(queueCompletionBoundaryWiring.contains("EchoPlaybackService.this.skipToNext();"));
        assertFalse(normalizedService.contains(
                "new PlaybackQueueCompletionOwner(\n                playbackQueueManager,\n                playbackQueueCompletionBoundary,\n                playbackQueueCommandOwner\n        )"));
        assertFalse(normalizedService.contains("new PlaybackQueueCompletionOwner(\n                () -> playbackQueueManager"));
        assertFalse(service.contains("private final PlaybackCurrentTrackReplacementOwner playbackCurrentTrackReplacementOwner"));
        assertFalse(service.contains("private PlaybackCurrentTrackReplacementOwner playbackCurrentTrackReplacementOwner"));
        assertTrue(service.contains("new PlaybackCurrentTrackReplacementOwner("));
        assertFalse(service.contains("PlaybackCurrentTrackReplacementOwner.fromPlaybackQueueManager("));
        assertFalse(currentReplacementOwner.contains("static PlaybackCurrentTrackReplacementOwner fromPlaybackQueueManager("));
        assertFalse(service.contains("PlaybackCurrentTrackReplacementOwner.fromPlaybackQueueManagerProvider("));
        assertTrue(service.contains("playbackQueueMutationOwner().playQueue(tracks, startIndex, C.TIME_UNSET);"));
        assertFalse(queueMutationOwner.contains("void playQueue(List<Track> tracks, int startIndex)"));
        assertFalse(service.contains("playbackQueueManager.playQueue(tracks, startIndex, startPositionMs)"));
        assertFalse(service.contains("playbackQueueManager.advanceQueueIndexToNext()"));
        assertFalse(service.contains("private void advanceQueueIndexToNext()"));
        assertTrue(owner.contains("private fun advanceQueueIndexToNext()"));
        assertFalse(owner.contains("\n    fun advanceQueueIndexToNext()"));
        assertFalse(service.contains("private int clampedCurrentIndex()"));
        assertTrue(service.contains("playbackQueueMutationOwner().appendToQueue(tracks);"));
        assertFalse(service.contains("playbackQueueManager.appendToQueue(tracks)"));
        assertTrue(queueMutationOwner.contains(
                "final class PlaybackQueueMutationOwner implements PlaybackControllerMediaItemsOwner.QueuePlayer"));
        assertFalse(queueMutationOwner.contains("interface PlaybackQueueManagerProvider"));
        assertTrue(queueMutationOwner.contains("private final PlaybackQueueManager playbackQueueManager"));
        assertFalse(queueMutationOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(queueMutationOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertFalse(queueMutationOwner.contains("playbackQueueManagerProvider"));
        assertFalse(queueMutationOwner.contains("interface QueueMutationOperations"));
        assertFalse(queueMutationOwner.contains("QueueMutationOperations"));
        assertFalse(queueMutationOwner.contains("PlaybackQueueManagerOperations"));
        assertFalse(queueMutationOwner.contains("import java.util.function.BiConsumer;"));
        assertFalse(queueMutationOwner.contains("import java.util.function.Consumer;"));
        assertFalse(queueMutationOwner.contains("private final PlaybackControllerMediaItemsOwner.QueuePlayer playQueue;"));
        assertFalse(queueMutationOwner.contains("private final Consumer<List<Track>> appendToQueue;"));
        assertFalse(queueMutationOwner.contains("private final Runnable clearQueue;"));
        assertFalse(queueMutationOwner.contains("BiConsumer<Integer, Integer> moveQueueTrack"));
        assertFalse(queueMutationOwner.contains("BiConsumer<Long, Track> replaceQueuedTrackById"));
        assertTrue(queueMutationOwner.contains("private final Runnable stopAndClearAction;"));
        assertTrue(queueMutationOwner.contains("private void stopAndClear()"));
        assertTrue(queueMutationOwner.contains("playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);"));
        assertTrue(queueMutationOwner.contains("playbackQueueManager.appendToQueue(tracks);"));
        assertFalse(queueCompletionOwner.contains("interface QueueCompletionOperations"));
        assertFalse(queueCompletionOwner.contains("QueueCompletionOperations"));
        assertFalse(queueCompletionOwner.contains("PlaybackQueueManagerOperations"));
        assertFalse(queueCompletionOwner.contains("import java.util.function.BooleanSupplier;"));
        assertFalse(queueCompletionOwner.contains("import java.util.function.Consumer;"));
        assertFalse(queueCompletionOwner.contains("import java.util.function.IntConsumer;"));
        assertFalse(queueCompletionOwner.contains(
                "private final Supplier<PlaybackQueueManager.PlaybackCompletionAction> playbackCompletionAction;"));
        assertFalse(queueCompletionOwner.contains(
                "private final Consumer<PlaybackQueueManager.PlaybackCompletionAction> preparePlaybackCompletion;"));
        assertFalse(queueCompletionOwner.contains("private final BooleanSupplier prepareStopAndClearPlaybackState;"));
        assertFalse(queueCompletionOwner.contains("private final BooleanSupplier prepareStopAtEndOfQueue;"));
        assertFalse(queueCompletionOwner.contains("private final IntConsumer prepareStopAfterAutomaticAdvance;"));
        assertTrue(queueCompletionOwner.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertFalse(queueCompletionOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertFalse(queueCompletionOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertFalse(queueCompletionOwner.contains(
                "Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertTrue(queueCompletionOwner.contains("playbackQueueManager.prepareStopAndClearPlaybackState();"));
        assertFalse(queueCompletionOwner.contains("queueCompletionOperationsProvider"));
        assertFalse(queueCompletionOwner.contains("playbackQueueManagerProvider"));
        assertFalse(currentReplacementOwner.contains("interface CurrentTrackReplacementOperations"));
        assertFalse(currentReplacementOwner.contains("CurrentTrackReplacementOperations"));
        assertFalse(currentReplacementOwner.contains("PlaybackQueueManagerOperations"));
        assertFalse(currentReplacementOwner.contains("import java.util.function.BiFunction;"));
        assertFalse(currentReplacementOwner.contains("interface RecoveryDiagnosticsRecorder"));
        assertFalse(currentReplacementOwner.contains("interface RecoveryScheduler"));
        assertFalse(currentReplacementOwner.contains(
                "private final BiFunction<Track, Long, PlaybackQueueManager.CurrentTrackReplacementRecovery> replaceCurrentTrackAndResume;"));
        assertTrue(currentReplacementOwner.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertFalse(currentReplacementOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertTrue(currentReplacementOwner.contains(
                "private final Consumer<PlaybackQueueManager.CurrentTrackReplacementRecovery> recoveryDiagnosticsRecorder;"));
        assertTrue(currentReplacementOwner.contains("private final Consumer<Boolean> recoveryScheduler;"));
        assertFalse(currentReplacementOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertFalse(currentReplacementOwner.contains(
                "Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(currentReplacementOwner.contains("currentTrackReplacementOperationsProvider"));
        assertFalse(currentReplacementOwner.contains("playbackQueueManagerProvider"));
        assertTrue(service.contains("playbackQueueMutationOwner().moveQueueTrack(fromIndex, toIndex);"));
        assertFalse(service.contains("playbackQueueManager.moveQueueTrack(fromIndex, toIndex)"));
        assertTrue(queueMutationOwner.contains("playbackQueueManager.moveQueueTrack(fromIndex, toIndex);"));
        assertTrue(service.contains("playbackQueueMutationOwner().removeTracksById(trackIds);"));
        assertFalse(service.contains("playbackQueueManager.removeTracksById(trackIds)"));
        assertTrue(queueMutationOwner.contains("playbackQueueManager.removeTracksById(trackIds)"));
        assertTrue(service.contains("playbackQueueMutationOwner().retainTracksById(trackIdsToKeep);"));
        assertFalse(service.contains("playbackQueueManager.retainTracksById(trackIdsToKeep)"));
        assertTrue(queueMutationOwner.contains("playbackQueueManager.retainTracksById(trackIdsToKeep)"));
        assertTrue(service.contains("playbackQueueMutationOwner().clearQueue();"));
        assertFalse(service.contains("playbackQueueManager.clearQueue()"));
        assertTrue(queueMutationOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(queueMutationOwner.contains("playbackQueueManager.queueStateSnapshot().isQueueEmpty()"));
        assertFalse(queueMutationOwner.contains("PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateOwner == null"));
        assertFalse(queueMutationOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(queueMutationOwner.contains("if (!queueSnapshot.isQueueEmpty())"));
        assertFalse(queueMutationOwner.contains("!playbackQueueManager.queueSnapshot().isEmpty()"));
        assertTrue(queueMutationOwner.contains("if (!queueStateOwner.isQueueEmpty())"));
        assertTrue(queueMutationOwner.contains("stopAndClear();"));
        assertFalse(service.contains("public void replaceQueuedTrack(Track replacement)"));
        assertFalse(service.contains("playbackQueueMutationOwner.replaceQueuedTrack(replacement);"));
        assertFalse(service.contains("playbackQueueManager.replaceQueuedTrack(replacement)"));
        assertFalse(queueMutationOwner.contains("void replaceQueuedTrack(Track replacement)"));
        assertFalse(queueMutationOwner.contains("playbackQueueManager.replaceQueuedTrack(replacement);"));
        assertTrue(service.contains("playbackQueueMutationOwner().replaceQueuedTrackById(oldTrackId, replacement);"));
        assertFalse(service.contains("playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement)"));
        assertTrue(queueMutationOwner.contains("playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement)"));
        assertTrue(service.contains(
                "playbackQueueNavigationOwner().skipToNextImmediately();"));
        assertFalse(service.contains("playbackQueueManager.skipToNextImmediately()"));
        assertTrue(queueNavigationOwner.contains(
                "playbackQueueManager != null && playbackQueueManager.skipToNextImmediately()"
        ));
        assertTrue(service.contains(
                "playbackQueueNavigationOwner().playFirstQueuedTrack()"));
        assertFalse(service.contains("private void playFirstQueuedTrack()"));
        assertFalse(service.contains("playbackQueueManager.playFirstQueuedTrack()"));
        assertTrue(queueNavigationOwner.contains("playbackQueueManager.playFirstQueuedTrack();"));
        assertTrue(queueNavigationOwner.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertFalse(queueNavigationOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(queueNavigationOwner.contains("playbackQueueManagerProvider"));
        assertFalse(queueNavigationOwner.contains("interface QueueNavigationOperations"));
        assertFalse(queueNavigationOwner.contains("QueueNavigationOperations"));
        assertFalse(queueNavigationOwner.contains("PlaybackQueueManagerOperations"));
        assertFalse(queueNavigationOwner.contains("interface MirroredQueueReuseHandler"));
        assertFalse(queueNavigationOwner.contains("import java.util.function.BiPredicate;"));
        assertFalse(queueNavigationOwner.contains("import java.util.function.BooleanSupplier;"));
        assertTrue(queueNavigationOwner.contains("import java.util.function.Consumer;"));
        assertFalse(queueNavigationOwner.contains("private final Runnable playFirstQueuedTrack;"));
        assertFalse(queueNavigationOwner.contains("private final BooleanSupplier skipToNextImmediately;"));
        assertFalse(queueNavigationOwner.contains("private final BooleanSupplier skipToPrevious;"));
        assertFalse(queueNavigationOwner.contains("private final BiPredicate<Boolean, Long> reuseMirroredQueueIfAvailable;"));
        assertFalse(queueNavigationOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertFalse(queueNavigationOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertTrue(queueNavigationOwner.contains("private final Consumer<Boolean> mirroredQueueReuseHandler;"));
        assertTrue(queueNavigationOwner.contains("mirroredQueueReuseHandler.accept(playWhenReady);"));
        assertFalse(queueNavigationOwner.contains("queueNavigationOperationsProvider"));
        assertTrue(service.contains(
                "playbackQueueNavigationOwner().skipToPrevious();"));
        assertFalse(service.contains("playbackQueueManager.skipToPrevious()"));
        assertTrue(queueNavigationOwner.contains(
                "playbackQueueManager != null && playbackQueueManager.skipToPrevious()"
        ));
        assertFalse(service.contains("playbackQueueMirroredTransitionOwner.prepareMirroredTransitionPlaybackState()"));
        assertFalse(mirroredTransitionOwner.contains("void prepareMirroredTransitionPlaybackState()"));
        assertFalse(owner.contains("\n    fun prepareMirroredTransitionPlaybackState()"));
        assertTrue(owner.contains("private fun prepareMirroredTransitionPlaybackState()"));
        assertTrue(mirroredTransitionOwner.contains("playbackQueueManager.applyMirroredTransitionIndex("));
        assertFalse(service.contains("playbackQueueStateOwner::queueStateSnapshot"));
        assertEquals(0, countOccurrences(service, "playbackQueueStateOwner::queueStateSnapshot"));
        assertEquals(0, countOccurrences(service, "playbackQueueStateOwner::currentTrack"));
        assertFalse(service.contains("final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier"));
        assertFalse(service.contains("playbackQueueManager.canSkipFailedTrack(failed)"));
        assertFalse(service.contains("playbackQueueManager.canCrossfadeAdvance(repeatMode)"));
        assertFalse(queueNavigationOwner.contains("playbackQueueManager.canSkipFailedTrack(failed);"));
        assertFalse(queueNavigationOwner.contains("PlaybackErrorRecoveryCommandOwner.FailedTrackPolicy"));
        assertFalse(queueNavigationOwner.contains("playbackQueueManager.canCrossfadeAdvance(repeatMode);"));
        assertFalse(queueNavigationOwner.contains("PlaybackCrossfadeStateOwner.CrossfadeAdvancePolicy"));
        assertTrue(service.contains("playbackQueueRestoreOwner().restoreLastPlayback(playWhenRestored);"));
        assertFalse(service.contains("playbackQueueManager.restoreLastPlayback(playWhenRestored)"));
        assertTrue(queueRestoreOwner.contains("playbackQueueManager.restoreLastPlayback(playWhenRestored);"));
        assertTrue(service.contains("playbackQueueRestoreOwner().restorePlaybackQueue();"));
        assertFalse(service.contains("playbackQueueManager.restorePlaybackQueue()"));
        assertTrue(queueRestoreOwner.contains("playbackQueueManager.restorePlaybackQueue();"));
        assertTrue(service.contains("playbackQueueRestoreOwner().setPlaybackRestoreEnabled(enabled);"));
        assertFalse(service.contains("playbackQueueManager.setPlaybackRestoreEnabled(enabled)"));
        assertTrue(queueRestoreOwner.contains("playbackQueueManager.setPlaybackRestoreEnabled(enabled);"));
        assertTrue(queueRestoreOwner.contains("private final PlaybackQueueManager playbackQueueManager"));
        assertFalse(queueRestoreOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(queueRestoreOwner.contains("playbackQueueManagerProvider"));
        assertFalse(queueRestoreOwner.contains("interface QueueRestoreOperations"));
        assertFalse(queueRestoreOwner.contains("QueueRestoreOperations"));
        assertFalse(queueRestoreOwner.contains("interface RestorePlaybackBoundary"));
        assertFalse(service.contains("new PlaybackQueueRestoreOwner.RestorePlaybackBoundary()"));
        assertFalse(queueRestoreOwner.contains("import java.util.function.Consumer;"));
        assertFalse(queueRestoreOwner.contains("import java.util.function.Function;"));
        assertFalse(queueRestoreOwner.contains("private final Runnable restorePlaybackQueue;"));
        assertFalse(queueRestoreOwner.contains("private final Function<Boolean, PlaybackQueueManager.RestorePlaybackResult> restoreLastPlayback;"));
        assertFalse(queueRestoreOwner.contains("private final Consumer<Boolean> setPlaybackRestoreEnabled;"));
        assertFalse(queueRestoreOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertFalse(queueRestoreOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertTrue(queueRestoreOwner.contains("private final Runnable createPlayerIfNeeded;"));
        assertFalse(queueRestoreOwner.contains("private final Consumer<Boolean> prepareCurrent;"));
        assertFalse(queueRestoreOwner.contains("private final Runnable statePublisher;"));
        assertTrue(queueRestoreOwner.contains("private final PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions;"));
        assertTrue(service.contains("EchoPlaybackService.this::createPlayerIfNeeded"));
        String queueRestoreWiring = normalizedService.substring(
                normalizedService.indexOf("private PlaybackQueueRestoreOwner playbackQueueRestoreOwner()"),
                normalizedService.indexOf("    private boolean reuseMirroredQueueIfAvailable")
        );
        assertTrue(queueRestoreWiring.contains("                playbackQueueCommandOwner"));
        assertFalse(queueRestoreWiring.contains("EchoPlaybackService.this::prepareCurrent"));
        assertFalse(queueRestoreWiring.contains("EchoPlaybackService.this::publishState"));
        assertFalse(queueRestoreOwner.contains("queueRestoreOperationsProvider"));
        assertTrue(queueRestoreOwner.contains("createPlayerIfNeeded.run();"));
        assertTrue(queueRestoreOwner.contains("queuePlaybackActions.prepareCurrent(playWhenReady);"));
        assertTrue(queueRestoreOwner.contains("queuePlaybackActions.publishState();"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueuePositionPersistenceOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackShutdownQueueLifecycleStoreOwner.java")));
        assertFalse(service.contains("PlaybackQueuePositionPersistenceOwner"));
        assertFalse(service.contains("playbackQueuePositionPersistenceOwner"));
        assertFalse(service.contains("PlaybackShutdownQueueLifecycleStoreOwner"));
        assertFalse(service.contains("playbackShutdownQueueLifecycleStoreOwner"));
        assertTrue(service.contains("playbackQueuePersistenceOwner().requestPlaybackResume();"));
        assertFalse(service.contains("playbackQueueManager.savePlaybackResumeRequested(true)"));
        assertTrue(service.contains("playbackQueuePersistenceOwner().clearPlaybackResumeRequest();"));
        assertFalse(service.contains("playbackQueueManager.savePlaybackResumeRequested(false)"));
        assertTrue(queuePersistenceOwner.contains("void requestPlaybackResume()"));
        assertTrue(queuePersistenceOwner.contains("void clearPlaybackResumeRequest()"));
        assertTrue(service.contains(
                "playbackQueueCompletionOwner().prepareStopAndClearPlaybackState();"));
        assertFalse(service.contains("queueStopPrepared"));
        assertFalse(service.contains("playbackQueueManager.prepareStopAndClearPlaybackState()"));
        assertTrue(queueCompletionOwner.contains("playbackQueueManager.prepareStopAndClearPlaybackState();"));
        assertFalse(queueCompletionOwner.contains("completionBoundary.prepareStopAndClearFallbackState();"));
        assertFalse(service.contains("prepareStopAndClearFallbackState()"));
        assertTrue(service.contains("playbackQueueCompletionOwner().playAfterCompletion();"));
        assertFalse(service.contains("private void playAfterCompletion()"));
        assertFalse(service.contains("\n                playAfterCompletion();"));
        assertFalse(service.contains("playbackQueueManager.playbackCompletionAction()"));
        assertFalse(service.contains("playbackQueueManager.preparePlaybackCompletionAction()"));
        assertFalse(service.contains("playbackQueueManager.preparePlaybackCompletion(completionAction)"));
        assertTrue(service.contains(
                "playbackQueueCompletionOwner().prepareStopAtEndOfQueue();"));
        assertFalse(service.contains("if (!new PlaybackQueueCompletionOwner("));
        assertFalse(service.contains("playbackQueueManager.prepareStopAtEndOfQueue()"));
        assertTrue(service.contains(
                "playbackQueueCompletionOwner().stopAfterAutomaticAdvance(transition.completedIndex());"));
        assertFalse(service.contains("playbackQueueCompletionOwner().prepareStopAfterAutomaticAdvance(completedIndex);"));
        assertFalse(service.contains("playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex)"));
        assertTrue(queueCompletionOwner.contains("playbackQueueManager.preparePlaybackCompletionAction();"));
        assertTrue(queueCompletionOwner.contains("if (completionAction == null)"));
        assertFalse(queueCompletionOwner.contains("playbackQueueManager.playbackCompletionAction();"));
        assertFalse(queueCompletionOwner.contains("case REPEAT_CURRENT"));
        assertFalse(queueCompletionOwner.contains("playbackQueueManager.preparePlaybackCompletion(completionAction);"));
        assertFalse(queueCompletionOwner.contains("boolean prepareStopAtEndOfQueue()"));
        assertTrue(queueCompletionOwner.contains("playbackQueueManager.prepareStopAtEndOfQueue();"));
        assertFalse(queueCompletionOwner.contains("completionBoundary.prepareStopAtEndFallbackState();"));
        assertFalse(service.contains("prepareStopAtEndFallbackState()"));
        assertTrue(queueCompletionOwner.contains("void stopAfterAutomaticAdvance(int completedIndex)"));
        assertTrue(queueCompletionOwner.contains("playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);"));
        assertFalse(queueCompletionOwner.contains("private void stopAndClear()"));
        assertFalse(queueCompletionOwner.contains("private void stopAtEndOfQueue()"));
        assertFalse(queueCompletionOwner.contains("private void skipToNext()"));
        assertFalse(queueCompletionOwner.contains("private void prepareStopAndClearFallbackState()"));
        assertFalse(queueCompletionOwner.contains("queuePlaybackActions.prepareCurrent(true);"));
        assertTrue(owner.contains("queuePlaybackActions.prepareCurrent(true)"));
        assertFalse(queueCompletionOwner.contains("completionBoundary.repeatCurrent();"));
        assertFalse(queueCompletionOwner.contains("completionBoundary.prepareCurrent(playWhenReady);"));
        assertTrue(queueCompletionOwner.contains("completionBoundary.stopAtEndOfQueue();"));
        assertTrue(queueCompletionOwner.contains("completionBoundary.skipToNext();"));
        assertTrue(service.contains(
                "new PlaybackCurrentTrackReplacementOwner(\n                playbackQueueManager,"));
        assertTrue(service.contains(").replaceCurrentTrackAndResume(replacement, positionMs);"));
        assertFalse(service.contains(
                "new PlaybackCurrentTrackReplacementOwner(\n                    () -> playbackQueueManager"));
        assertFalse(service.contains("playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs)"));
        assertTrue(currentReplacementOwner.contains("playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);"));
        assertTrue(currentReplacementOwner.contains("recoveryDiagnosticsRecorder.accept(recovery);"));
        assertTrue(currentReplacementOwner.contains("recoveryScheduler.accept(recovery.getPlayWhenReady());"));
        assertTrue(service.contains(
                "if (reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs))"));
        assertTrue(service.contains("private boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs)"));
        assertFalse(service.contains("private boolean seekExistingMirroredQueue(boolean playWhenReady, long startPositionMs)"));
        assertFalse(service.contains("if (seekExistingMirroredQueue(playWhenReady, startPositionMs))"));
        assertFalse(service.contains("playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs)"));
        assertTrue(service.contains(
                "return playbackQueueNavigationOwner().reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);"));
        assertTrue(queueNavigationOwner.contains(
                "playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);"));
        assertTrue(service.contains("private PlaybackStreamingDiagnosticsRecorderOwner playbackStreamingDiagnosticsRecorderOwner;"));
        assertTrue(service.contains("new PlaybackStreamingDiagnosticsRecorderOwner("));
        assertFalse(service.contains("() -> streamingDiagnostics"));
        assertFalse(service.contains("private PlaybackRecoveryDiagnosticsRecorderOwner playbackRecoveryDiagnosticsRecorderOwner;"));
        assertFalse(service.contains("new PlaybackRecoveryDiagnosticsRecorderOwner("));
        assertFalse(service.contains("PlaybackStreamingDiagnosticsRecorderOwner.fromStreamingDiagnosticsProvider("));
        assertFalse(streamingDiagnosticsOwner.contains("static PlaybackStreamingDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider("));
        assertTrue(service.contains("mediaSourceProvider::streamingQualityForTrack"));
        assertTrue(service.contains("playbackStreamingDiagnosticsRecorderOwner.record(recovery)"));
        assertTrue(service.contains("playbackRecoveryScheduler.scheduleCurrentPlaybackRecovery(playWhenReady)"));
        assertFalse(service.contains("if (playbackRecoveryDiagnosticsRecorderOwner != null)"));
        assertFalse(service.contains("if (playbackRecoveryScheduler != null)"));
        assertTrue(service.contains("acquireWifiLockIfStreamingAction.run();"));
        assertTrue(service.contains("private void onMirroredQueueReused(boolean playWhenReady)"));
        assertTrue(service.contains("startProgressUpdates();"));
        assertFalse(service.contains("private void clearQueueState()"));
        assertTrue(owner.contains("fun skipToNextImmediately(): Boolean"));
        assertTrue(owner.contains("fun skipToPrevious(): Boolean"));
        assertTrue(owner.contains("fun reuseMirroredQueueIfAvailable(playWhenReady: Boolean, startPositionMs: Long): Boolean"));
        assertTrue(owner.contains("private fun reuseMirroredQueueAtCurrentIndex(playWhenReady: Boolean): Boolean"));
        assertFalse(owner.contains("\n    fun reuseMirroredQueueAtCurrentIndex(playWhenReady: Boolean): Boolean"));
        assertTrue(owner.contains("if (reuseMirroredQueueAtCurrentIndex(true))"));
        assertTrue(owner.contains("private fun mirroredQueueTracksForPreparation(): List<Track>?"));
        assertFalse(owner.contains("\n    fun mirroredQueueTracksForPreparation(): List<Track>?"));
        assertTrue(owner.contains("data class QueuePreparation"));
        assertTrue(owner.contains("fun queuePreparationForNewPlayer(): QueuePreparation"));
        assertFalse(owner.contains("fun acquireWifiLockIfStreaming()"));
        assertFalse(owner.contains("fun startProgressUpdates()"));
        assertFalse(owner.contains("fun resetWaveformIfTrackChanged(track: Track)"));
        assertFalse(owner.contains("fun applyPlaybackParametersToPlayer()"));
        assertFalse(owner.contains("fun applyPlaybackModeToPlayer()"));
        assertFalse(owner.contains("fun setPreparing(preparing: Boolean)"));
        assertFalse(owner.contains("fun setPlayerMirrorsQueue(enabled: Boolean)"));
        assertFalse(owner.contains("fun recordStreamingRecovery(track: Track, restoredPositionMs: Long)"));
        assertFalse(owner.contains("fun schedulePrepareCurrent(playWhenReady: Boolean)"));
        assertTrue(service.contains("private void onMirroredQueueReused(boolean playWhenReady)"));
        assertTrue(service.contains("playbackStreamingDiagnosticsRecorderOwner.record(recovery)"));
        assertFalse(service.contains("streamingDiagnostics.recordRecovery("));
        assertTrue(service.contains("PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY"));
        assertTrue(service.contains("playbackRecoveryScheduler.scheduleCurrentPlaybackRecovery(playWhenReady)"));
        assertTrue(currentReplacementOwner.contains(
                "recoveryScheduler.accept(recovery.getPlayWhenReady());"));
        assertFalse(service.contains("() -> mainHandler.post(() -> prepareCurrent(recovery.getPlayWhenReady()))"));
        assertFalse(service.contains("private PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner;"));
        assertTrue(service.contains("final PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner ="));
        assertTrue(service.contains("new PlaybackQueueMirroredPlayerOwner("));
        assertTrue(service.contains("PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher("));
        assertFalse(service.contains("PlaybackQueueMirroredPlayerOwner.fromPlaybackQueueManager("));
        assertFalse(owner.contains("static BooleanSupplier fromPlaybackQueueManager("));
        assertFalse(service.contains("PlaybackQueueMirroredPlayerOwner.fromPlaybackQueueManagerProvider("));
        assertTrue(normalizedService.contains(
                "                        playbackQueueStateOwner,\n" +
                "                        EchoPlaybackService.this::resetWaveformIfTrackChanged,"));
        assertFalse(normalizedService.contains(
                "                        currentTrackSupplier,\n" +
                "                        EchoPlaybackService.this::resetWaveformIfTrackChanged,"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner::setPreparing"));
        assertFalse(service.contains("playbackRuntimeStateManager::setPreparing"));
        assertFalse(service.contains("private boolean mirroredQueueMatchesCurrentPlayer()"));
        assertFalse(service.contains("playbackQueueManager.matchesMirroredQueue("));
        assertFalse(service.contains("private PlaybackMirroredQueueTrackMatcherOwner playbackMirroredQueueTrackMatcherOwner;"));
        assertTrue(service.contains("final PlaybackMirroredQueueTrackMatcherOwner playbackMirroredQueueTrackMatcherOwner ="));
        assertTrue(service.contains("new PlaybackMirroredQueueTrackMatcherOwner("));
        assertTrue(service.contains("playbackMirroredQueueTrackMatcherOwner::matches"));
        assertFalse(service.contains("new PlaybackQueueManager.QueueTrackMatcher()"));
        assertFalse(service.contains("mediaSourceProvider.mediaItemMatchesTrackForReuse(player.getMediaItemAt(index), track)"));
        assertFalse(service.contains("EchoPlaybackService.this.resetWaveformIfTrackChanged(track);"));
        assertFalse(service.contains("EchoPlaybackService.this.applyPlaybackModeAndParametersToPlayer();"));
        assertTrue(mirroredTrackMatcherOwner.contains(
                "final class PlaybackMirroredQueueTrackMatcherOwner"));
        assertFalse(mirroredTrackMatcherOwner.contains(
                "implements PlaybackQueueManager.QueueTrackMatcher"));
        assertFalse(mirroredTrackMatcherOwner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertFalse(mirroredTrackMatcherOwner.contains("interface PlayerProvider"));
        assertFalse(mirroredTrackMatcherOwner.contains("interface PlayerMediaItemProvider"));
        assertFalse(mirroredTrackMatcherOwner.contains("interface TrackMediaItemMatcher"));
        assertTrue(mirroredTrackMatcherOwner.contains("import java.util.function.BiPredicate;"));
        assertTrue(mirroredTrackMatcherOwner.contains("import java.util.function.IntFunction;"));
        assertTrue(mirroredTrackMatcherOwner.contains("import java.util.function.Supplier;"));
        assertTrue(mirroredTrackMatcherOwner.contains("private final IntFunction<MediaItem> playerMediaItemProvider;"));
        assertTrue(mirroredTrackMatcherOwner.contains("private final BiPredicate<MediaItem, Track> trackMediaItemMatcher;"));
        assertTrue(mirroredTrackMatcherOwner.contains("PlaybackMediaSourceProvider"));
        assertFalse(service.contains("mediaSourceProvider::mediaItemMatchesTrackForReuse"));
        assertTrue(mirroredTrackMatcherOwner.contains("BiPredicate<MediaItem, Track> trackMediaItemMatcher"));
        assertFalse(mirroredTrackMatcherOwner.contains("static PlaybackMirroredQueueTrackMatcherOwner fromMediaSourceProvider("));
        assertFalse(mirroredTrackMatcherOwner.contains("fromPlayerProvider("));
        assertTrue(mirroredTrackMatcherOwner.contains("mediaSourceProvider.mediaItemMatchesTrackForReuse(mediaItem, track)"));
        assertTrue(mirroredTrackMatcherOwner.contains("player.getMediaItemAt(index)"));
        assertTrue(streamingDiagnosticsOwner.contains("final class PlaybackStreamingDiagnosticsRecorderOwner"));
        assertTrue(streamingDiagnosticsOwner.contains("implements PlaybackStatePublisher.BufferingRecorder"));
        assertFalse(streamingDiagnosticsOwner.contains("interface StreamingDiagnosticsProvider"));
        assertFalse(streamingDiagnosticsOwner.contains("interface StreamingQualityProvider"));
        assertTrue(streamingDiagnosticsOwner.contains(
                "private final PlaybackStreamingDiagnostics streamingDiagnostics;"));
        assertFalse(streamingDiagnosticsOwner.contains(
                "private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider;"));
        assertTrue(streamingDiagnosticsOwner.contains("private final Function<Track, String> streamingQualityProvider;"));
        assertFalse(streamingDiagnosticsOwner.contains("interface StreamingDiagnosticsOperations"));
        assertFalse(streamingDiagnosticsOwner.contains("StreamingDiagnosticsOperationsProvider"));
        assertFalse(streamingDiagnosticsOwner.contains("private PlaybackStreamingDiagnostics streamingDiagnostics()"));
        assertFalse(streamingDiagnosticsOwner.contains("streamingDiagnosticsProvider.get()"));
        assertTrue(streamingDiagnosticsOwner.contains("streamingQualityProvider.apply(track)"));
        assertTrue(streamingDiagnosticsOwner.contains("diagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);"));
        assertTrue(streamingDiagnosticsOwner.contains(
                "diagnostics.recordRecovery(track, recovery.getRestoredPositionMs(), quality);"));
        assertTrue(mirroredPlayerOwner.contains("final class PlaybackQueueMirroredPlayerOwner implements PlaybackQueueManager.MirroredQueuePlayer"));
        assertFalse(mirroredPlayerOwner.contains("interface MirroredQueueMatcher"));
        assertFalse(mirroredPlayerOwner.contains("interface MirrorStateProvider"));
        assertFalse(mirroredPlayerOwner.contains("interface PlayerAvailability"));
        assertFalse(mirroredPlayerOwner.contains("interface PlayerMediaItemCountProvider"));
        assertFalse(mirroredPlayerOwner.contains("interface PlaybackQueueManagerProvider"));
        assertFalse(mirroredPlayerOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertTrue(mirroredPlayerOwner.contains("Supplier<List<Track>> queueSnapshotProvider"));
        assertFalse(mirroredPlayerOwner.contains("playbackQueueManagerProvider"));
        assertFalse(mirroredPlayerOwner.contains("interface MirroredQueueOperations"));
        assertFalse(mirroredPlayerOwner.contains("MirroredQueueOperations"));
        assertFalse(mirroredPlayerOwner.contains("PlaybackQueueManagerOperations"));
        assertTrue(mirroredPlayerOwner.contains("import java.util.function.BiConsumer;"));
        assertTrue(mirroredPlayerOwner.contains("import java.util.function.BiPredicate;"));
        assertTrue(mirroredPlayerOwner.contains("import java.util.function.BooleanSupplier;"));
        assertTrue(mirroredPlayerOwner.contains("import java.util.function.Consumer;"));
        assertTrue(mirroredPlayerOwner.contains("import java.util.function.IntSupplier;"));
        assertFalse(mirroredPlayerOwner.contains(
                "Supplier<BiPredicate<Integer, PlaybackQueueManager.QueueTrackMatcher>> mirroredQueueMatcherSupplier"));
        assertFalse(mirroredPlayerOwner.contains("mirroredQueueMatcherSupplier"));
        assertFalse(mirroredPlayerOwner.contains("interface PreparingStateController"));
        assertFalse(mirroredPlayerOwner.contains("interface CurrentTrackProvider"));
        assertFalse(mirroredPlayerOwner.contains("interface WaveformResetter"));
        assertFalse(mirroredPlayerOwner.contains("interface PlaybackParameterApplier"));
        assertFalse(mirroredPlayerOwner.contains("interface PlayerSeeker"));
        assertFalse(mirroredPlayerOwner.contains("interface PlayWhenReadySetter"));
        assertFalse(mirroredPlayerOwner.contains("interface PlayerStarter"));
        assertFalse(mirroredPlayerOwner.contains("interface MirrorStateController"));
        assertFalse(mirroredPlayerOwner.contains("interface FailureLogger"));
        assertTrue(mirroredPlayerOwner.contains("private final BooleanSupplier mirroredQueueMatcher;"));
        assertTrue(mirroredPlayerOwner.contains("private final BooleanSupplier playerAvailability;"));
        assertTrue(mirroredPlayerOwner.contains("private final Consumer<Boolean> preparingStateController;"));
        assertFalse(mirroredPlayerOwner.contains("private final Supplier<Track> currentTrackProvider;"));
        assertFalse(mirroredPlayerOwner.contains("currentTrackProvider.get()"));
        assertFalse(mirroredPlayerOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(mirroredPlayerOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.queueStateSnapshot();"));
        assertFalse(mirroredPlayerOwner.contains("return snapshot == null ? null : snapshot.getCurrentTrack();"));
        assertFalse(mirroredPlayerOwner.contains("private final Supplier<Track> currentTrackSupplier;"));
        assertFalse(mirroredPlayerOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertTrue(mirroredPlayerOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(mirroredPlayerOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(mirroredPlayerOwner.contains("? PlaybackQueueManager.QueueStateSnapshot.empty()"));
        assertFalse(mirroredPlayerOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(mirroredPlayerOwner.contains("return snapshot.getCurrentTrack();"));
        assertTrue(mirroredPlayerOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertFalse(mirroredPlayerOwner.contains("queueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertTrue(mirroredPlayerOwner.contains("private final Consumer<Track> waveformResetter;"));
        assertTrue(mirroredPlayerOwner.contains("private final Runnable playbackParameterApplier;"));
        assertTrue(mirroredPlayerOwner.contains("private final BiConsumer<Integer, Long> playerSeeker;"));
        assertTrue(mirroredPlayerOwner.contains("private final Consumer<Boolean> playWhenReadySetter;"));
        assertTrue(mirroredPlayerOwner.contains("private final Runnable playerStarter;"));
        assertTrue(mirroredPlayerOwner.contains("private final Consumer<Boolean> mirrorStateController;"));
        assertTrue(mirroredPlayerOwner.contains("private final Consumer<IllegalStateException> failureLogger;"));
        assertTrue(mirroredPlayerOwner.contains("preparingStateController.accept(false);"));
        assertTrue(mirroredPlayerOwner.contains("waveformResetter.accept(track);"));
        assertTrue(mirroredPlayerOwner.contains("playbackParameterApplier.run();"));
        assertTrue(mirroredPlayerOwner.contains("playerSeeker.accept(index, positionMs);"));
        assertTrue(mirroredPlayerOwner.contains("playWhenReadySetter.accept(playWhenReady);"));
        assertTrue(mirroredPlayerOwner.contains("playerStarter.run();"));
        assertTrue(mirroredPlayerOwner.contains("mirrorStateController.accept(false);"));
        assertTrue(mirroredPlayerOwner.contains("failureLogger.accept(error);"));
        assertFalse(mirroredPlayerOwner.contains("playbackQueueManager.matchesMirroredQueue("));
        assertFalse(mirroredPlayerOwner.contains("playbackQueueManager::matchesMirroredQueue"));
        assertTrue(mirroredPlayerOwner.contains("queueSnapshotProvider.get();"));
        assertTrue(mirroredPlayerOwner.contains("queueTrackMatcher.test(i, track)"));
        assertFalse(owner.contains("\n        fun currentTrack(): Track?"));
        assertFalse(owner.contains("\n        fun currentIndex(): Int"));
        assertFalse(owner.contains("\n        fun setCurrentIndex(index: Int)"));
        assertTrue(owner.contains("\n    private fun currentIndex(): Int"));
        assertTrue(owner.contains("\n    private fun setCurrentIndex(index: Int)"));
        assertFalse(owner.contains("\n    fun currentIndex(): Int"));
        assertFalse(owner.contains("\n    fun setCurrentIndex(index: Int)"));
        assertFalse(owner.contains("\n        fun savePlaybackResumeRequested(requested: Boolean)"));
        assertFalse(owner.contains("\n        fun seekMirroredQueueToCurrentIndex(playWhenReady: Boolean): Boolean"));
        assertFalse(owner.contains("\n        fun playbackPositionMs(): Long"));
        assertTrue(owner.contains("fun currentTrack(): Track?"));
        String queueStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueStateOwner.java");
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackCurrentTrackOwner.java")));
        assertFalse(service.contains("PlaybackCurrentTrackOwner"));
        assertFalse(service.contains("playbackCurrentTrackOwner"));
        assertFalse(service.contains("private Track currentTrack()"));
        assertFalse(service.contains("Track track = playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack();"));
        assertTrue(service.contains("playbackQueueCommandOwner.prepareCurrentOrRunFallback("));
        assertFalse(service.contains("playbackQueueCommandOwner.prepareCurrentIfAvailable(true)"));
        assertTrue(service.contains("playbackQueueCommandOwner.runIfCurrentTrackMissing("));
        assertFalse(service.contains("playbackQueueCommandOwner.hasCurrentTrack()"));
        assertFalse(service.contains("playbackQueueStateOwner.currentTrack()"));
        assertFalse(service.contains("playbackQueueManager.currentTrack();"));
        assertTrue(queueStateOwner.contains("Track currentTrack()"));
        assertTrue(queueStateOwner.contains("return queueStateSnapshot().getCurrentTrack();"));
        assertTrue(queueStateOwner.contains("int currentIndex()"));
        assertTrue(queueStateOwner.contains("return queueStateSnapshot().getCurrentIndex();"));
        assertTrue(queueStateOwner.contains("int queueSize()"));
        assertTrue(queueStateOwner.contains("return queueStateSnapshot().getQueueSize();"));
        assertFalse(queueStateOwner.contains("public PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot()"));
        assertTrue(queueStateOwner.contains("private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot()"));
        assertFalse(queueStateOwner.contains("playbackQueueManager::currentTrack"));
        assertFalse(service.contains("return queue.get(currentIndex());"));
        assertTrue(owner.contains("fun queueSnapshot(): List<Track>"));
        assertTrue(owner.contains("Collections.unmodifiableList(ArrayList(queue))"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueueSnapshotOwner.java")));
        assertFalse(service.contains("PlaybackQueueSnapshotOwner"));
        assertFalse(service.contains("playbackQueueSnapshotOwner"));
        assertTrue(service.contains("return playbackQueueStateOwner.queueSnapshot();"));
        assertFalse(service.contains("playbackQueueManager.queueSnapshot();"));
        assertFalse(queueStateOwner.contains("interface QueueSnapshotOperations"));
        assertFalse(queueStateOwner.contains("interface QueueSnapshotOperationsProvider"));
        assertFalse(queueStateOwner.contains("Supplier<List<Track>> queueSnapshotSupplier"));
        assertTrue(queueStateOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertTrue(queueStateOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertFalse(queueStateOwner.contains("queueSnapshotOperationsProvider"));
        assertTrue(queueStateOwner.contains("playbackQueueManager == null ? null : playbackQueueManager.queueSnapshot();"));
        assertTrue(queueStateOwner.contains("return snapshot == null ? Collections.emptyList() : snapshot;"));
        assertFalse(queueStateOwner.contains("default List<Track> queueSnapshot()"));
        assertFalse(service.contains("Collections.unmodifiableList(new ArrayList<>(queue))"));
        assertFalse(service.contains("return new ArrayList<>(queue);"));
        assertTrue(owner.contains("fun queueStateSnapshot(): QueueStateSnapshot"));
        assertTrue(owner.contains("fun empty(): QueueStateSnapshot = QueueStateSnapshot("));
        assertFalse(owner.contains("isQueueEmpty = queue.isEmpty()"));
        assertFalse(owner.contains("hasCurrentTrack = currentTrack != null"));
        assertFalse(owner.contains("hasMultipleTracks = queue.size >= 2"));
        assertFalse(owner.contains("isAtEndOfQueue = index >= queue.size - 1"));
        assertTrue(owner.contains("val isQueueEmpty: Boolean"));
        assertTrue(owner.contains("get() = queueSize <= 0"));
        assertTrue(owner.contains("val hasCurrentTrack: Boolean"));
        assertTrue(owner.contains("get() = currentTrack != null"));
        assertTrue(owner.contains("val hasMultipleTracks: Boolean"));
        assertTrue(owner.contains("get() = queueSize >= 2"));
        assertTrue(owner.contains("val isAtEndOfQueue: Boolean"));
        assertTrue(owner.contains("get() = queueSize > 0 && currentIndex >= queueSize - 1"));
        assertTrue(service.contains("private final PlaybackQueueStateOwner playbackQueueStateOwner"));
        assertTrue(service.contains("new PlaybackQueueStateOwner(() -> playbackQueueManager)"));
        assertFalse(service.contains("new PlaybackQueueStateOwner()"));
        assertFalse(service.contains("playbackQueueStateOwner.bindPlaybackQueueManager(playbackQueueManager);"));
        assertFalse(service.contains("PlaybackQueueStateOwner.fromPlaybackQueueManager("));
        assertFalse(queueStateOwner.contains("static PlaybackQueueStateOwner fromPlaybackQueueManager("));
        assertTrue(queueStateOwner.contains("import java.util.function.Supplier;"));
        assertTrue(queueStateOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertTrue(queueStateOwner.contains("PlaybackQueueStateOwner(PlaybackQueueManager playbackQueueManager)"));
        assertTrue(queueStateOwner.contains("PlaybackQueueStateOwner(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier)"));
        assertFalse(queueStateOwner.contains("void bindPlaybackQueueManager("));
        assertTrue(queueStateOwner.contains(
                "return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();"));
        assertTrue(queueStateOwner.contains("boolean isQueueEmpty()"));
        assertTrue(queueStateOwner.contains("boolean hasMultipleTracks()"));
        assertTrue(queueStateOwner.contains("boolean isAtEndOfQueue()"));
        assertTrue(queueStateOwner.contains("PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot()"));
        assertFalse(service.contains("playbackQueueStateOwner::isQueueEmpty"));
        assertFalse(service.contains("playbackQueueStateOwner::hasMultipleTracks"));
        assertFalse(service.contains("playbackQueueStateOwner::isAtEndOfQueue"));
        assertFalse(service.contains("playbackQueueStateOwner.queueStateSnapshot().isQueueEmpty()"));
        assertTrue(service.contains("playbackNotificationStateOwner = new PlaybackNotificationStateOwner("));
        assertTrue(service.contains("playbackStateSnapshotOwner = new PlaybackStateSnapshotOwner("));
        assertFalse(service.contains("                playbackQueueStateOwner::isQueueEmpty,"));
        assertFalse(service.contains("                queueStateSupplier,"));
        assertFalse(service.contains("                playbackQueueStateOwner::canSkipFailedTrack,"));
        assertFalse(service.contains("playbackQueueManager.queueStateSnapshot()"));
        assertFalse(queueStateOwner.contains("final class PlaybackQueueStateOwner implements"));
        assertTrue(queueStateOwner.contains("final class PlaybackQueueStateOwner {"));
        assertFalse(queueStateOwner.contains("PlaybackNotificationStateOwner.QueueStateProvider"));
        assertFalse(queueStateOwner.contains("PlaybackStateSnapshotOwner.QueueStateProvider"));
        assertFalse(queueStateOwner.contains("PlaybackCrossfadeStateOwner.QueueStateProvider"));
        assertFalse(queueStateOwner.contains("interface PlaybackQueueManagerProvider"));
        assertFalse(queueStateOwner.contains("Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier"));
        assertFalse(queueStateOwner.contains("Supplier<PlaybackQueueManager.QueueStateSnapshot> fallbackQueueStateSnapshotSupplier"));
        assertTrue(queueStateOwner.contains("PlaybackQueueStateOwner(Supplier<PlaybackQueueManager>"));
        assertFalse(queueStateOwner.contains("fallbackQueueStateSnapshot"));
        assertFalse(queueStateOwner.contains("PlaybackQueueStateOwner(Supplier<PlaybackQueueManager.QueueStateSnapshot>"));
        assertFalse(queueStateOwner.contains("playbackQueueManagerProvider"));
        assertEquals(0, countOccurrences(queueStateOwner, "    interface "));
        assertEquals(0, countOccurrences(queueStateOwner, "interface QueueStateOperations"));
        assertEquals(0, countOccurrences(queueStateOwner, "interface QueueSnapshotOperations"));
        assertEquals(0, countOccurrences(queueStateOwner, "interface UpcomingTracksOperations"));
        assertFalse(queueStateOwner.contains("interface QueueOperations"));
        assertFalse(queueStateOwner.contains("interface QueueReadOperations"));
        assertFalse(queueStateOwner.contains("interface QueueProvider"));
        assertFalse(queueStateOwner.contains("interface QueueStateOperations"));
        assertFalse(queueStateOwner.contains("interface QueueStateOperationsProvider"));
        assertFalse(queueStateOwner.contains("Supplier<QueueStateOperations> queueStateOperationsSupplier"));
        assertFalse(queueStateOwner.contains("queueStateOperationsProvider"));
        assertTrue(queueStateOwner.contains("playbackQueueManager.queueStateSnapshot();"));
        assertFalse(queueStateOwner.contains("interface UpcomingTracksOperations"));
        assertFalse(queueStateOwner.contains("interface UpcomingTracksOperationsProvider"));
        assertFalse(queueStateOwner.contains("IntFunction<List<Track>> upcomingTracksSupplier"));
        assertFalse(queueStateOwner.contains("upcomingTracksOperationsProvider"));
        assertTrue(queueStateOwner.contains("upcomingTracksForPrecache(int maxCount)"));
        assertTrue(queueStateOwner.contains(
                "playbackQueueManager.upcomingTracksForPrecache(maxCount);"));
        assertFalse(queueStateOwner.contains("new QueueStateOperations()"));
        assertFalse(queueStateOwner.contains("default List<Track> upcomingTracksForPrecache(int maxCount)"));
        assertTrue(queueStateOwner.contains("return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;"));
        assertFalse(owner.contains("fun canCrossfadeAdvance(repeatMode: Int): Boolean"));
        assertFalse(owner.contains("private fun safeCurrentIndex(): Int"));
        assertTrue(owner.contains("startIndex = currentIndex()"));
        assertFalse(owner.contains("\n    fun safeCurrentIndex(): Int"));
        assertTrue(owner.contains("fun replaceCurrentQueueTrack(replacement: Track?)"));
        assertFalse(owner.contains("fun replaceCurrentQueueTrack(replacement: Track?): Boolean"));
        assertFalse(owner.contains("interface QueueTrackMatcher"));
        assertFalse(owner.contains("fun matchesMirroredQueue(itemCount: Int, matcher: QueueTrackMatcher): Boolean"));
        assertFalse(owner.contains("private fun clearQueueState()"));
        assertFalse(owner.contains("\n    fun clearQueueState()"));
        assertTrue(owner.contains("queue.clear()"));
        assertTrue(owner.contains("setCurrentIndex(-1)"));
        assertTrue(owner.contains("fun persistQueueState()"));
        assertFalse(owner.contains("private fun isAtEndOfQueue(): Boolean"));
        assertTrue(owner.contains("repeatMode() == REPEAT_OFF && queueStateSnapshot().isAtEndOfQueue"));
        assertTrue(owner.contains("data class MirroredTransitionResult"));
        assertTrue(owner.contains("fun applyMirroredTransitionIndex(nextIndex: Int, automaticAdvance: Boolean): MirroredTransitionResult?"));
        assertFalse(service.contains("private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot()"));
        assertFalse(service.contains("PlaybackQueueManager.QueueStateSnapshot.empty()"));
        assertFalse(stateSnapshotOwner.contains("PlaybackQueueManager.QueueStateSnapshot.empty()"));
        assertFalse(stateSnapshotOwner.contains("queueStateOwner.queueStateSnapshot()"));
        assertFalse(service.contains("new PlaybackQueueManager.QueueStateSnapshot(null, -1, 0, true, false, false, true)"));
        assertFalse(service.contains("private boolean isQueueEmpty()"));
        assertFalse(service.contains("EchoPlaybackService.this.queueStateSnapshot()"));
        assertFalse(service.contains("return queueStateSnapshot().getHasMultipleTracks();"));
        assertTrue(owner.contains("val startIndex: Int"));
        assertTrue(owner.contains("startIndex = currentIndex()"));
        assertFalse(owner.contains("private fun safeCurrentIndex(): Int"));
        assertFalse(service.contains("queuePreparation.getStartIndex()"));
        assertTrue(service.contains("queuePreparation.startIndex()"));
        assertFalse(service.contains("return playbackQueueManager == null ? 0 : playbackQueueManager.safeCurrentIndex();"));
        assertFalse(service.contains("private boolean isAtEndOfQueue()"));
        assertFalse(service.contains("playbackQueueStateOwner::queueStateSnapshot"));
        assertFalse(service.contains("playbackQueueManager.canCrossfadeAdvance(repeatMode)"));
        assertTrue(service.contains("mirroredTransitionOwner.applyMirroredTransitionReason("));
        assertTrue(service.contains("mirroredTransitionOwner.canApplyMirroredTransition()"));
        assertFalse(service.contains("if (!playbackQueueMirrorStateOwner.playerMirrorsQueue()"));
        assertFalse(service.contains("isAutomaticMediaItemAdvance(reason)"));
        assertFalse(service.contains("static boolean isAutomaticMediaItemAdvance(int reason)"));
        assertFalse(service.contains("playbackQueueManager.applyMirroredTransitionIndex(nextIndex, isAutomaticMediaItemAdvance(reason))"));
        assertFalse(service.contains("playbackQueueMirroredTransitionOwner.prepareMirroredTransitionPlaybackState();"));
        assertFalse(service.contains("playbackQueueManager.prepareMirroredTransitionPlaybackState();"));
        assertFalse(service.contains("playbackRuntimeStateManager.applyCurrentTrackVolumeToPlayer();"));
        assertTrue(service.contains("new PlaybackQueueMirroredTransitionOwner("));
        assertFalse(service.contains("PlaybackQueueMirroredTransitionOwner.fromPlaybackQueueManager("));
        assertFalse(mirroredTransitionOwner.contains("static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManager("));
        assertFalse(service.contains("PlaybackQueueMirroredTransitionOwner.fromPlaybackQueueManagerProvider("));
        assertFalse(service.contains("private final PlaybackQueueMirroredTransitionOwner playbackQueueMirroredTransitionOwner"));
        assertFalse(service.contains("private PlaybackQueueMirroredTransitionOwner playbackQueueMirroredTransitionOwner"));
        assertTrue(service.contains("EchoPlaybackService.this::applyCurrentTrackVolumeToPlayer"));
        int mirroredTransitionStart = normalizedService.indexOf("new PlaybackQueueMirroredTransitionOwner(");
        String mirroredTransitionWiring = normalizedService.substring(
                mirroredTransitionStart,
                normalizedService.indexOf(
                        ");",
                        mirroredTransitionStart
                )
        );
        assertTrue(mirroredTransitionWiring.contains(
                "new PlaybackQueueMirroredTransitionOwner(\n"
                        + "                            playbackQueueManager,\n"
                        + "                            playbackQueueStateOwner,"
        ));
        assertFalse(mirroredTransitionWiring.contains("() -> playbackQueueStateOwner.queueStateSnapshot()"));
        assertTrue(service.contains("private void applyCurrentTrackVolumeToPlayer()"));
        assertTrue(service.contains(
                "playbackRuntimeSettingsStore.applyCurrentTrackVolumeToPlayer(playbackRuntimeStateManager);"
        ));
        assertFalse(service.contains(
                "PlaybackQueueMirroredTransitionOwner.currentTrackVolumeApplierFromRuntimeStateManagerProvider("
        ));
        assertTrue(mirroredTransitionOwner.contains("final class PlaybackQueueMirroredTransitionOwner"));
        assertFalse(mirroredTransitionOwner.contains("interface MirroredTransitionOperations"));
        assertFalse(mirroredTransitionOwner.contains("MirroredTransitionOperations"));
        assertFalse(mirroredTransitionOwner.contains("PlaybackQueueManagerOperations"));
        assertFalse(mirroredTransitionOwner.contains("interface CurrentTrackVolumeApplier"));
        assertTrue(mirroredTransitionOwner.contains("private final Runnable currentTrackVolumeApplier;"));
        assertFalse(mirroredTransitionOwner.contains("import java.util.function.BiFunction;"));
        assertTrue(mirroredTransitionOwner.contains("import java.util.function.BooleanSupplier;"));
        assertFalse(mirroredTransitionOwner.contains(
                "private final BiFunction<Integer, Boolean, PlaybackQueueManager.MirroredTransitionResult> applyMirroredTransitionIndex;"));
        assertFalse(mirroredTransitionOwner.contains(
                "private final BooleanSupplier prepareMirroredTransitionPlaybackState;"));
        assertTrue(mirroredTransitionOwner.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertFalse(mirroredTransitionOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertFalse(mirroredTransitionOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertTrue(mirroredTransitionOwner.contains("private final BooleanSupplier playerMirrorsQueue;"));
        assertFalse(mirroredTransitionOwner.contains("private final BooleanSupplier queueEmptySupplier;"));
        assertFalse(mirroredTransitionOwner.contains("private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;"));
        assertFalse(mirroredTransitionOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(mirroredTransitionOwner.contains("QueueStateProvider queueStateProvider"));
        assertTrue(mirroredTransitionOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(mirroredTransitionOwner.contains("private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot()"));
        assertFalse(mirroredTransitionOwner.contains("PlaybackQueueManager playbackQueueManager = playbackQueueManager();"));
        assertFalse(mirroredTransitionOwner.contains("playbackQueueManager.queueStateSnapshot();"));
        assertFalse(mirroredTransitionOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(mirroredTransitionOwner.contains("return mirrorsQueue && !queueSnapshot.isQueueEmpty();"));
        assertFalse(mirroredTransitionOwner.contains("boolean queueEmpty = snapshot.isQueueEmpty();"));
        assertTrue(mirroredTransitionOwner.contains(
                "boolean queueEmpty = queueStateOwner == null || queueStateOwner.isQueueEmpty();"));
        assertTrue(mirroredTransitionOwner.contains("return mirrorsQueue && !queueEmpty;"));
        assertTrue(mirroredTransitionOwner.contains("currentTrack = queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertFalse(mirroredTransitionOwner.contains("currentTrack = snapshot.getCurrentTrack();"));
        assertTrue(mirroredTransitionOwner.contains("boolean canApplyMirroredTransition()"));
        assertTrue(mirroredTransitionOwner.contains("static final class Transition"));
        assertTrue(mirroredTransitionOwner.contains("Track currentTrack()"));
        assertTrue(mirroredTransitionOwner.contains("Transition applyMirroredTransitionReason("));
        assertTrue(service.contains("PlaybackQueueMirroredTransitionOwner.Transition transition"));
        assertTrue(service.contains("Track track = transition.currentTrack();"));
        assertTrue(mirroredTransitionOwner.contains("static boolean isAutomaticMediaItemAdvance(int reason)"));
        assertTrue(mirroredTransitionOwner.contains("return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;"));
        assertFalse(mirroredTransitionOwner.contains(
                "Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(mirroredTransitionOwner.contains(
                "return fromPlaybackQueueManager(playbackQueueManagerSupplier, null);"));
        assertFalse(mirroredTransitionOwner.contains(
                "return fromPlaybackQueueManager(playbackQueueManagerSupplier, null, null, currentTrackVolumeApplier);"));
        assertFalse(mirroredTransitionOwner.contains("this(playbackQueueManagerSupplier, null);"));
        assertFalse(mirroredTransitionOwner.contains("mirroredTransitionOperationsProvider"));
        assertFalse(mirroredTransitionOwner.contains("playbackQueueManagerProvider"));
        assertFalse(mirroredTransitionOwner.contains("PlaybackRuntimeStateManager"));
        assertFalse(mirroredTransitionOwner.contains("interface RuntimeStateManagerProvider"));
        assertTrue(mirroredTransitionOwner.contains("playbackQueueManager.applyMirroredTransitionIndex("));
        assertFalse(mirroredTransitionOwner.contains("playbackQueueManager.prepareMirroredTransitionPlaybackState();"));
        assertTrue(mirroredTransitionOwner.contains("currentTrackVolumeApplier.run();"));
        assertFalse(mirroredTransitionOwner.contains("manager.applyCurrentTrackVolumeToPlayer();"));
        assertTrue(currentPreparationOwner.contains("queuePreparationController.replaceCurrentQueueTrack(restoredTrack);"));
        assertTrue(currentPreparationQueueOwner.contains("final class PlaybackCurrentTrackPreparationQueueOwner"));
        assertTrue(currentPreparationQueueOwner.contains(
                "implements PlaybackCurrentTrackPreparationOwner.QueuePreparationController"));
        assertFalse(currentPreparationQueueOwner.contains("interface PlaybackQueueManagerProvider"));
        assertTrue(currentPreparationQueueOwner.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertFalse(currentPreparationQueueOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(currentPreparationQueueOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertFalse(currentPreparationQueueOwner.contains("Supplier<QueueOperations> queueOperationsSupplier"));
        assertFalse(currentPreparationQueueOwner.contains("playbackQueueManagerProvider"));
        assertFalse(currentPreparationQueueOwner.contains("queueOperationsProvider"));
        assertFalse(currentPreparationQueueOwner.contains("interface QueueOperations"));
        assertTrue(currentPreparationQueueOwner.contains("import java.util.function.Function;"));
        assertFalse(currentPreparationQueueOwner.contains("import java.util.function.Consumer;"));
        assertFalse(currentPreparationQueueOwner.contains("import java.util.function.LongConsumer;"));
        assertFalse(currentPreparationQueueOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertFalse(currentPreparationQueueOwner.contains("private final Consumer<Track> replaceCurrentQueueTrack;"));
        assertFalse(currentPreparationQueueOwner.contains("private final Function<Track, Long> restoredPositionFor;"));
        assertFalse(currentPreparationQueueOwner.contains("private final Supplier<PlaybackQueueManager.QueuePreparation> queuePreparationForNewPlayer;"));
        assertTrue(currentPreparationOwner.contains("private final Function<Track, Long> restoredPositionProvider;"));
        assertTrue(currentPreparationQueueOwner.contains("private final Function<List<Track>, List<MediaSource>> mediaSourcesForTracks;"));
        assertFalse(currentPreparationQueueOwner.contains("private final LongConsumer consumeRestoredPositionAfterPrepare;"));
        assertTrue(currentPreparationQueueOwner.contains("PlaybackMediaSourceProvider mediaSourceProvider"));
        assertTrue(currentPreparationQueueOwner.contains("mediaSourceProvider.mediaSourcesForTracks("));
        assertTrue(currentPreparationQueueOwner.contains("metadataProvider == null ? null : metadataProvider::apply"));
        assertTrue(currentPreparationQueueOwner.contains("playbackQueueManager.replaceCurrentQueueTrack(track);"));
        assertFalse(currentPreparationQueueOwner.contains("playbackQueueManager.restoredPositionFor(track);"));
        assertTrue(service.contains("private PlaybackCurrentTrackPreparationQueueOwner playbackCurrentTrackPreparationQueueOwner"));
        assertTrue(service.contains("new PlaybackCurrentTrackPreparationQueueOwner("));
        assertFalse(service.contains("PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager("));
        assertFalse(currentPreparationQueueOwner.contains("static PlaybackCurrentTrackPreparationQueueOwner fromPlaybackQueueManager("));
        assertFalse(currentPreparationQueueOwner.contains("static PlaybackCurrentTrackPreparationQueueOwner fromMediaSourceProvider("));
        assertFalse(service.contains("PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManagerProvider("));
        assertFalse(service.contains("new PlaybackCurrentTrackPreparationOwner.QueuePreparationController()"));
        assertFalse(service.contains("playbackQueueManager.replaceCurrentQueueTrack(restoredTrack);"));
        assertFalse(service.contains("private void replaceCurrentQueueTrack(Track track)"));
        assertTrue(service.contains("playbackCurrentTrackPreparationQueueOwner.queuePreparationForNewPlayer()"));
        assertFalse(service.contains("playbackQueueManager.queuePreparationForNewPlayer()"));
        assertTrue(currentPreparationQueueOwner.contains("playbackQueueManager.queuePreparationForNewPlayer();"));
        assertFalse(service.contains("playbackQueueManager.mirroredQueueTracksForPreparation()"));
        assertFalse(service.contains("return playbackQueueManager.matchesMirroredQueue("));
        assertFalse(mirroredPlayerOwner.contains("playbackQueueManager.matchesMirroredQueue("));
        assertFalse(mirroredPlayerOwner.contains("playbackQueueManager::matchesMirroredQueue"));
        assertFalse(service.contains("new PlaybackQueueManager.QueueTrackMatcher()"));
        assertFalse(service.contains("playbackQueueManager.prepareStopAndClearPlaybackState();"));
        assertFalse(service.contains("playbackQueueManager.clearQueueState();"));
        assertTrue(service.contains("persistenceOwner.persistQueueState();"));
        assertFalse(service.contains("playbackQueueManager.persistQueueState();"));
        assertFalse(service.contains("queue.isEmpty()"));
        assertFalse(service.contains("queueSize() < 2"));
        assertFalse(service.contains("queue.size() < 2"));
        assertFalse(service.contains("nextIndex < 0 || nextIndex >= queueSize() || nextIndex == currentIndex()"));
        assertFalse(service.contains("setCurrentIndex(nextIndex);"));
        assertFalse(service.contains("currentIndex() >= queueSize() - 1"));
        assertFalse(service.contains("currentIndex() >= queue.size() - 1"));
        assertFalse(service.contains("return Math.max(0, Math.min(currentIndex(), queue.size() - 1));"));
        assertFalse(service.contains("queue.set(currentIndex(), track);"));
        assertFalse(service.contains("player.getMediaItemCount() != queue.size()"));
        assertFalse(service.contains("for (int i = 0; i < queue.size(); i++)"));
        assertFalse(service.contains("Track track = queue.get(i);"));
        assertFalse(service.contains("queue.clear();"));
        assertFalse(service.contains("queueStore().save(new ArrayList<>(queue), currentIndex());"));
        assertFalse(owner.contains("\n    fun isQueueEmpty(): Boolean"));
        assertFalse(owner.contains("\n    fun queueSize(): Int"));
        assertFalse(owner.contains("\n    fun hasMultipleTracks(): Boolean"));
        assertTrue(owner.contains("private fun savePlaybackResumeRequested(requested: Boolean)"));
        assertFalse(owner.contains("\n    fun savePlaybackResumeRequested(requested: Boolean)"));
        assertTrue(owner.contains("private fun playbackPositionMs(): Long"));
        assertTrue(owner.contains("return playbackPositionManager?.positionMs() ?: 0L"));
        assertTrue(owner.contains("queueStore.saveResumeRequested(requested)"));
        assertFalse(owner.contains("fun consumeRestoredPositionAfterPrepare(startPositionMs: Long)"));
        assertTrue(service.contains("playbackPositionManager.consumeRestoredPositionAfterPrepare(startPositionMs);"));
        assertFalse(service.contains("playbackCurrentTrackPreparationQueueOwner.consumeRestoredPositionAfterPrepare(startPositionMs);"));
        assertFalse(service.contains("playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs)"));
        assertFalse(currentPreparationQueueOwner.contains(
                "playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);"));
        assertFalse(service.contains("private void consumeRestoredPositionAfterPrepare(long startPositionMs)"));
        assertTrue(positionOwner.contains("fun positionMs(): Long"));
        assertFalse(owner.contains("\n        fun isRestorableQueueTrack(track: Track): Boolean"));
        assertFalse(service.contains("private boolean isRestorableQueueTrack(Track track)"));
        assertFalse(service.contains("EchoPlaybackService.this.isRestorableQueueTrack(track)"));
        assertFalse(service.contains("public long playbackPositionMs()"));
        assertFalse(owner.contains("private fun isRestorableQueueTrack(track: Track): Boolean"));
        assertFalse(owner.contains("File(path).exists()"));
        assertFalse(owner.contains("PlaybackMediaSourceProvider"));
        assertFalse(owner.contains("PlaybackMediaSourceProvider.isRestorableQueueTrack(track)"));
        assertTrue(mediaSourceProvider.contains("fun isRestorableQueueTrack(track: Track?): Boolean"));
        assertTrue(mediaSourceProvider.contains("fun isStreamingPlaceholder(track: Track?): Boolean"));
        assertTrue(mediaSourceProvider.contains("File(path).exists()"));
        assertTrue(owner.contains("mirroredQueuePlayer.matchesCurrentQueue()"));
        assertTrue(owner.contains("mirroredQueuePlayer.seekTo(targetIndex, startAtMs, playWhenReady)"));
        assertFalse(service.contains("private PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner;"));
        assertTrue(service.contains("final PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner ="));
        assertTrue(service.contains("new PlaybackQueueMirroredPlayerOwner("));
        assertTrue(service.contains("                playbackQueueMirroredPlayerOwner,"));
        assertFalse(service.contains("new PlaybackQueueManager.MirroredQueuePlayer()"));
        assertFalse(owner.contains("\n        streamingRestoreProvider.restoreTrackForPlayback(track)\n"));
        assertTrue(owner.contains("queue[currentIndex()] = streamingRestoreProvider.restoreTrackForPlayback(track) ?: track"));
        assertTrue(owner.contains("queue[targetIndex] = streamingRestoreProvider.restoreTrackForPlayback(track) ?: track"));
        assertTrue(owner.contains("val restoredTrack = streamingRestoreProvider.restoreTrackForPlayback(track) ?: return null"));
        assertTrue(owner.contains("tracks.add(restoredTrack)"));
        assertTrue(owner.contains("val queueTrack = streamingRestoreProvider.restoreTrackForPlayback(track) ?: continue"));
        assertFalse(owner.contains("streamingRestoreProvider.restoreForDataPath(track.dataPath)"));
        assertFalse(service.contains("private PlaybackQueueStreamingRestoreOwner playbackQueueStreamingRestoreOwner;"));
        assertTrue(service.contains("final PlaybackQueueStreamingRestoreOwner playbackQueueStreamingRestoreOwner ="));
        assertTrue(service.contains("new PlaybackQueueStreamingRestoreOwner(mediaSourceProvider)"));
        assertTrue(service.contains("                playbackQueueStreamingRestoreOwner,"));
        assertFalse(service.contains("new PlaybackQueueManager.StreamingRestoreProvider()"));
        assertTrue(owner.contains("private fun clearErrorMessage()"));
        assertTrue(owner.contains("playbackRuntimeStateManager?.setErrorMessage(\"\")"));
        assertTrue(owner.contains("private fun clearLastMarkedTrack()"));
        assertTrue(owner.contains("playbackTransitionStateManager?.setLastMarkedTrack(null)"));
        assertTrue(owner.contains("private fun shuffleEnabled(): Boolean"));
        assertTrue(owner.contains("return playbackRuntimeStateManager?.shuffleEnabled() ?: false"));
        assertTrue(owner.contains("private fun repeatMode(): Int"));
        assertTrue(owner.contains("return playbackRuntimeStateManager?.repeatMode() ?: REPEAT_ALL"));
        assertTrue(owner.contains("private fun preparing(): Boolean"));
        assertTrue(owner.contains("return playbackRuntimeStateManager?.preparing() ?: false"));
        assertFalse(queuePlaybackActions.contains("fun isPlaying(): Boolean"));
        assertTrue(owner.contains("private fun isPlaying(): Boolean"));
        assertTrue(owner.contains("return playbackRuntimeStateManager?.isPlaying() ?: false"));
        assertTrue(queuePlaybackActions.contains("fun prepareCurrent(playWhenReady: Boolean)"));
        assertTrue(queuePlaybackActions.contains("fun publishState()"));
        assertFalse(queuePlaybackActions.contains("fun stopAndClear()"));
        assertTrue(owner.contains("queuePlaybackActions.prepareCurrent("));
        assertTrue(owner.contains("queuePlaybackActions.publishState()"));
        assertFalse(owner.contains("queuePlaybackActions.stopAndClear()"));
        assertFalse(owner.contains("queueProvider.prepareCurrent("));
        assertFalse(owner.contains("queueProvider.publishState()"));
        assertFalse(owner.contains("queueProvider.stopAndClear()"));
        assertFalse(owner.contains("queueProvider.isPlaying()"));
        assertFalse(owner.contains("queuePlaybackActions.isPlaying()"));
        assertFalse(service.contains("new PlaybackQueueManager.QueuePlaybackActions()"));
        assertFalse(service.contains("private PlaybackQueueCommandOwner playbackQueueCommandOwner;"));
        assertTrue(service.contains("final PlaybackQueueCommandOwner playbackQueueCommandOwner ="));
        assertTrue(service.contains("new PlaybackQueueCommandOwner("));
        assertTrue(service.contains("EchoPlaybackService.this::stopAndClear"));
        assertTrue(service.contains("playbackQueueCommandOwner"));
        assertTrue(commandOwner.contains("final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions"));
        assertFalse(commandOwner.contains("interface PlaybackStateProvider"));
        assertFalse(commandOwner.contains("interface PlaybackPreparer"));
        assertFalse(commandOwner.contains("interface StatePublisher"));
        assertFalse(commandOwner.contains("PlaybackNotificationCommandOwner.PlaybackCommands"));
        assertFalse(commandOwner.contains("private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;"));
        assertFalse(playerStateOwner.contains("PlaybackQueueCommandOwner.PlaybackStateProvider"));
        assertTrue(commandOwner.contains("import java.util.function.BiConsumer;"));
        assertTrue(commandOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertTrue(commandOwner.contains("private final BiConsumer<Track, Boolean> playbackPreparer;"));
        assertTrue(commandOwner.contains("private final Runnable statePublisher;"));
        assertFalse(commandOwner.contains("private final Runnable stopAndClearCommand;"));
        assertTrue(commandOwner.contains("boolean prepareCurrentOrRunFallback(boolean playWhenReady, Runnable fallbackAction)"));
        assertTrue(commandOwner.contains("private boolean prepareCurrentIfAvailable(boolean playWhenReady)"));
        assertTrue(commandOwner.contains("boolean runIfCurrentTrackMissing(Runnable missingCurrentTrackAction)"));
        assertFalse(commandOwner.contains("boolean hasCurrentTrack()"));
        assertTrue(commandOwner.contains("Track track = currentTrack();"));
        assertTrue(commandOwner.contains("private Track currentTrack()"));
        assertFalse(commandOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(commandOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(commandOwner.contains("return snapshot.getCurrentTrack();"));
        assertTrue(commandOwner.contains("missingCurrentTrackAction.run();"));
        assertTrue(commandOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertTrue(commandOwner.contains("playbackPreparer.accept(track, playWhenReady);"));
        assertTrue(commandOwner.contains("statePublisher.run();"));
        assertFalse(commandOwner.contains("stopAndClearCommand.run();"));
        assertFalse(owner.contains("queueProvider.canPrepareMirroredQueueTrack(track)"));
        assertFalse(service.contains("canMirrorQueueToPlayer()"));
        assertFalse(service.contains("private boolean canMirrorQueueToPlayer()"));
        assertFalse(service.contains("EchoPlaybackService.this.seekMirroredQueueToCurrentIndex(playWhenReady)"));
        assertFalse(service.contains("private boolean seekMirroredQueueToCurrentIndex(boolean playWhenReady)"));
        assertFalse(service.contains("boolean wasEmpty = queue.isEmpty()"));
        assertFalse(service.contains("int removedBeforeCurrent = 0"));
        assertFalse(service.contains("boolean targetAlreadyQueued = false"));
        assertFalse(service.contains("ArrayList<Track> collapsedQueue"));
        assertFalse(service.contains("random.nextInt(queue.size())"));
        assertFalse(service.contains("private void replaceAndCollapseQueuedTrack("));
        assertFalse(service.contains("setCurrentIndex(currentIndex() <= 0 ? queue.size() - 1 : currentIndex() - 1)"));
        assertFalse(service.contains("queue.set(currentIndex(), replacement)"));
        assertFalse(service.contains("streamingDiagnostics.recordRecovery(replacement"));
        assertFalse(service.contains("player.seekTo(targetIndex, Math.max(0L, startPositionMs))"));
        assertFalse(service.contains("if (queueTrack == null || Uri.EMPTY.equals(queueTrack.contentUri))"));
        assertFalse(service.contains("streamingPlaybackHeaderStore.restoreForDataPath(queueTrack.dataPath)"));
    }

    @Test
    public void playbackQueueManagerPublicApiStaysExplicitlyAudited() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt");
        String normalizedOwner = owner.replace("\r\n", "\n");
        for (Path source : sourceFiles("app/src/main/java/app/yukine/playback")) {
            assertSourceDoesNotContain(source, "interface QueueProvider");
            assertSourceDoesNotContain(source, "PlaybackQueueManager.QueueProvider");
            assertSourceDoesNotContain(source, "interface PlaybackQueueManagerProvider");
            assertSourceDoesNotContain(source, "queueProvider.");
            assertSourceDoesNotContain(source, "fromPlaybackQueueManager(");
        }
        for (Path source : sourceFiles("feature/playback/src/main/java")) {
            assertSourceDoesNotContain(source, "interface QueueProvider");
            assertSourceDoesNotContain(source, "PlaybackQueueManager.QueueProvider");
            assertSourceDoesNotContain(source, "interface PlaybackQueueManagerProvider");
            assertSourceDoesNotContain(source, "queueProvider.");
            assertSourceDoesNotContain(source, "fromPlaybackQueueManager(");
        }
        java.util.Set<String> queueMutationApi = new java.util.TreeSet<>(java.util.Arrays.asList(
                "appendToQueue",
                "moveQueueTrack",
                "playFirstQueuedTrack",
                "playQueue",
                "removeTracksById",
                "replaceCurrentQueueTrack",
                "replaceCurrentTrackAndResume",
                "replaceQueuedTrackById",
                "retainTracksById",
                "setPlaybackRestoreEnabled",
                "skipToNextImmediately",
                "skipToPrevious"
        ));
        java.util.Set<String> queueRestoreAndPersistenceApi = new java.util.TreeSet<>(java.util.Arrays.asList(
                "persistQueueState",
                "preparePlaybackCompletionAction",
                "prepareStopAfterAutomaticAdvance",
                "prepareStopAndClearPlaybackState",
                "prepareStopAtEndOfQueue",
                "restoreLastPlayback",
                "restorePlaybackQueue"
        ));
        java.util.Set<String> queueMirrorApi = new java.util.TreeSet<>(java.util.Arrays.asList(
                "applyMirroredTransitionIndex",
                "reuseMirroredQueueIfAvailable"
        ));
        java.util.Set<String> queueDerivedReadApi = new java.util.TreeSet<>(java.util.Arrays.asList(
                "queuePreparationForNewPlayer",
                "queueSnapshot",
                "queueStateSnapshot",
                "upcomingTracksForPrecache"
        ));
        java.util.Set<String> expectedPublicApi = new java.util.TreeSet<>();
        expectedPublicApi.addAll(queueMutationApi);
        expectedPublicApi.addAll(queueRestoreAndPersistenceApi);
        expectedPublicApi.addAll(queueMirrorApi);
        expectedPublicApi.addAll(queueDerivedReadApi);

        assertEquals(expectedPublicApi, kotlinClassLevelFunNames(owner));
        assertFalse(owner.contains("fun clearQueue(): Boolean"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "queuePreparationForNewPlayer",
                "queueSnapshot",
                "queueStateSnapshot",
                "upcomingTracksForPrecache"
        )), queueDerivedReadApi);
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "PlaybackQueueStateOwner.java"
        )), playbackSourceFileNamesContaining("playbackQueueManager.queueStateSnapshot()"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "PlaybackRuntimeStateManager.kt"
        )), playbackSourceFileNamesContaining("playbackQueueManagerSupplier?.get()?.queueStateSnapshot()"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "PlaybackPositionManager.kt"
        )), playbackSourceFileNamesContaining("playbackQueueManager?.queueStateSnapshot()"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "PlaybackQueueStateOwner.java"
        )), playbackSourceFileNamesContaining("playbackQueueManager.queueSnapshot()"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "PlaybackQueueStateOwner.java"
        )), playbackSourceFileNamesContaining("playbackQueueManager.upcomingTracksForPrecache("));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "PlaybackCurrentTrackPreparationQueueOwner.java"
        )), playbackSourceFileNamesContaining("playbackQueueManager.queuePreparationForNewPlayer()"));
        String queueStateSnapshot = normalizedOwner.substring(
                normalizedOwner.indexOf("data class QueueStateSnapshot("),
                normalizedOwner.indexOf("        companion object {", normalizedOwner.indexOf("data class QueueStateSnapshot("))
        );
        String queueStateSnapshotConstructor = normalizedOwner.substring(
                normalizedOwner.indexOf("data class QueueStateSnapshot("),
                normalizedOwner.indexOf("\n    ) {", normalizedOwner.indexOf("data class QueueStateSnapshot("))
        );
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "currentIndex",
                "currentTrack",
                "queueSize"
        )), kotlinConstructorPropertyNames(queueStateSnapshotConstructor));
        assertTrue(queueStateSnapshot.contains("val isQueueEmpty: Boolean"));
        assertTrue(queueStateSnapshot.contains("get() = queueSize <= 0"));
        assertTrue(queueStateSnapshot.contains("val hasCurrentTrack: Boolean"));
        assertTrue(queueStateSnapshot.contains("get() = currentTrack != null"));
        assertTrue(queueStateSnapshot.contains("val hasMultipleTracks: Boolean"));
        assertTrue(queueStateSnapshot.contains("get() = queueSize >= 2"));
        assertTrue(queueStateSnapshot.contains("val isAtEndOfQueue: Boolean"));
        assertTrue(queueStateSnapshot.contains("get() = queueSize > 0 && currentIndex >= queueSize - 1"));
        assertFalse(owner.contains("interface QueueProvider"));
        assertFalse(owner.contains("queueProvider"));
        assertFalse(owner.contains("PlaybackMediaSourceProvider"));
        assertFalse(service.contains("new PlaybackQueueManager.QueueProvider()"));
        assertEquals(3, countOccurrences(normalizedOwner, "\n    interface "));
        String queuePlaybackActions = owner.substring(
                owner.indexOf("interface QueuePlaybackActions"),
                owner.indexOf("interface StreamingRestoreProvider"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "prepareCurrent",
                "publishState"
        )), kotlinInterfaceFunNames(queuePlaybackActions));
        assertFalse(queuePlaybackActions.contains("currentTrack"));
        assertFalse(queuePlaybackActions.contains("currentIndex"));
        assertFalse(queuePlaybackActions.contains("queueSnapshot"));
        assertFalse(queuePlaybackActions.contains("queueStateSnapshot"));
        assertFalse(queuePlaybackActions.contains("restoredPosition"));
        assertFalse(queuePlaybackActions.contains("isPlaying"));
        assertFalse(queuePlaybackActions.contains("preparing"));
        String streamingRestoreProvider = normalizedOwner.substring(
                normalizedOwner.indexOf("interface StreamingRestoreProvider"),
                normalizedOwner.indexOf("interface MirroredQueuePlayer"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "restoreTrackForPlayback"
        )), kotlinInterfaceFunNames(streamingRestoreProvider));
        assertFalse(streamingRestoreProvider.contains("queueStateSnapshot"));
        assertFalse(streamingRestoreProvider.contains("cache"));
        assertFalse(streamingRestoreProvider.contains("mediaItem"));
        String mirroredQueuePlayer = normalizedOwner.substring(
                normalizedOwner.indexOf("interface MirroredQueuePlayer"),
                normalizedOwner.indexOf("data class CurrentTrackReplacementRecovery"));
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "matchesCurrentQueue",
                "seekTo"
        )), kotlinInterfaceFunNames(mirroredQueuePlayer));
        assertFalse(mirroredQueuePlayer.contains("currentTrack"));
        assertFalse(mirroredQueuePlayer.contains("queueStateSnapshot"));
        assertFalse(mirroredQueuePlayer.contains("publishState"));
        assertFalse(owner.contains("\n    fun currentIndex(): Int"));
        assertFalse(owner.contains("\n    fun setCurrentIndex(index: Int)"));
        assertFalse(owner.contains("\n    fun currentTrack(): Track?"));
        assertFalse(owner.contains("\n    fun isQueueEmpty(): Boolean"));
        assertFalse(owner.contains("\n    fun queueSize(): Int"));
        assertFalse(owner.contains("\n    fun hasMultipleTracks(): Boolean"));
        assertFalse(owner.contains("\n    fun canSkipFailedTrack("));
        assertFalse(owner.contains("\n    fun canCrossfadeAdvance("));
        assertFalse(service.contains("playbackQueueManager."));
        assertFalse(service.contains("playbackQueueManager.currentTrack()"));
        assertFalse(service.contains("playbackQueueManager.queueSnapshot()"));
        assertFalse(service.contains("playbackQueueManager.queueStateSnapshot()"));
    }

    @Test
    public void playbackMediaItemReuseIdentityIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackMediaSourceProvider.kt");
        String dataSource = read("app/src/main/java/app/yukine/playback/PlaybackMediaLibraryDataSource.java");
        String queueRestoreOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueStreamingRestoreOwner.java");
        String preparationOwner = read("app/src/main/java/app/yukine/playback/PlaybackCurrentTrackPreparationOwner.java");
        String preparationQueueOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackCurrentTrackPreparationQueueOwner.java"
        );
        String preparationRuntimeOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackCurrentTrackPreparationRuntimeOwner.java"
        );
        String mirroredTrackMatcherOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackMirroredQueueTrackMatcherOwner.java"
        );
        String precacheManager = read("app/src/main/java/app/yukine/playback/PlaybackPrecacheManager.java");

        assertTrue(owner.contains("fun mediaItemMatchesTrackForReuse("));
        assertTrue(owner.contains("fun mediaItemIdentityMatchesForReuse("));
        assertTrue(owner.contains("fun playbackMediaItemForTrack(track: Track, metadata: MediaMetadata?): MediaItem"));
        assertTrue(owner.contains("fun unplayableMessageForTrack(track: Track?): String?"));
        assertTrue(owner.contains("fun restoredTrackForPreparation(track: Track?): Track?"));
        assertTrue(queueRestoreOwner.contains("final class PlaybackQueueStreamingRestoreOwner implements PlaybackQueueManager.StreamingRestoreProvider"));
        assertFalse(queueRestoreOwner.contains("interface StreamingRestoreResolver"));
        assertTrue(queueRestoreOwner.contains("import app.yukine.playback.manager.PlaybackMediaSourceProvider;"));
        assertTrue(queueRestoreOwner.contains("import java.util.function.Consumer;"));
        assertTrue(queueRestoreOwner.contains("import java.util.function.Function;"));
        assertFalse(queueRestoreOwner.contains("static PlaybackQueueStreamingRestoreOwner fromMediaSourceProvider("));
        assertTrue(queueRestoreOwner.contains("private final Function<Track, Track> restoredTrackForPreparation;"));
        assertTrue(queueRestoreOwner.contains("private final Consumer<String> restoreHeadersForDataPath;"));
        assertTrue(queueRestoreOwner.contains("PlaybackMediaSourceProvider.isRestorableQueueTrack(track)"));
        assertTrue(queueRestoreOwner.contains("mediaSourceProvider.restoredTrackForPreparation(track)"));
        assertTrue(queueRestoreOwner.contains("mediaSourceProvider.restoreHeadersForDataPath(dataPath)"));
        assertTrue(queueRestoreOwner.contains("public Track restoreTrackForPlayback(Track track)"));
        assertTrue(queueRestoreOwner.contains("Track playbackTrack = restoredTrack == null ? track : restoredTrack;"));
        assertTrue(queueRestoreOwner.contains("restoreHeadersForDataPath.accept(playbackTrack.dataPath);"));
        assertFalse(queueRestoreOwner.contains("public Track restoredTrackFor(Track track)"));
        assertFalse(queueRestoreOwner.contains("public void restoreForDataPath(String dataPath)"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueueStreamingRestoreResolverOwner.java")));
        assertFalse(service.contains("PlaybackQueueStreamingRestoreResolverOwner"));
        assertTrue(service.contains("new PlaybackQueueStreamingRestoreOwner(mediaSourceProvider)"));
        assertFalse(service.contains("mediaSourceProvider::restoredTrackForPreparation"));
        assertFalse(service.contains("mediaSourceProvider::restoreHeadersForDataPath"));
        assertFalse(service.contains("new PlaybackQueueStreamingRestoreOwner.StreamingRestoreResolver()"));
        assertTrue(owner.contains("data class PlaybackPreparation"));
        assertTrue(owner.contains("fun prepareTrackForPlayback(track: Track): PlaybackPreparation"));
        assertTrue(owner.contains("val playable: Boolean"));
        assertTrue(owner.contains("playable = unplayableMessage == null"));
        assertTrue(owner.contains("fun restoreHeadersForTrack(track: Track?): Boolean"));
        assertTrue(owner.contains("fun mediaSourceForTrack(track: Track, metadataProvider: ((Track) -> MediaMetadata)?): MediaSource"));
        assertTrue(owner.contains("restorePlaybackHeadersForMediaSource(track)"));
        assertTrue(owner.contains("fun mediaSourcesForTracks("));
        assertTrue(owner.contains("StreamingDataPathMetadata.isStreamingTrack(track.dataPath)"));
        assertTrue(owner.contains("fun streamingQualityForTrack(track: Track?): String"));
        assertTrue(owner.contains("fun mediaCacheKey(track: Track?): String?"));
        assertTrue(owner.contains("fun mediaCacheKey(dataPath: String?, uri: String?): String?"));
        assertTrue(owner.contains("if (dataPath.startsWith(\"streaming:\"))"));
        assertTrue(owner.contains("if (dataPath.startsWith(\"webdav:\")) return dataPath"));
        assertTrue(owner.contains(".setUri(track.contentUri)"));
        assertTrue(owner.contains(".setMediaId(track.id.toString())"));
        assertTrue(owner.contains(".setCustomCacheKey(mediaCacheKey(track))"));
        assertFalse(owner.contains("fun isHttpUri(uri: Uri?): Boolean"));
        assertFalse(service.contains("import app.yukine.common.StreamingDataPathMetadata;"));
        assertFalse(service.contains("StreamingDataPathMetadata.quality("));
        assertFalse(service.contains("mediaSourceProvider.streamingQualityForTrack(track)"));
        assertFalse(service.contains("mediaSourceProvider::prepareTrackForPlayback"));
        assertFalse(service.contains("PlaybackMediaSourceProvider::unplayableMessageForTrack"));
        assertTrue(service.contains("playbackCurrentTrackPreparationOwner.prepareCurrentTrack(track)"));
        assertTrue(service.contains("if (!preparedTrack.playable())"));
        assertTrue(preparationOwner.contains("PlaybackMediaSourceProvider.PlaybackPreparation preparation"));
        assertTrue(preparationOwner.contains("if (preparation != null && !preparation.getPlayable())"));
        assertTrue(preparationOwner.contains("final class PlaybackCurrentTrackPreparationOwner"));
        assertTrue(preparationOwner.contains("PlaybackMediaSourceProvider"));
        assertTrue(preparationOwner.contains("static PlaybackCurrentTrackPreparationOwner fromMediaSourceProvider("));
        assertTrue(preparationOwner.contains("mediaSourceProvider.prepareTrackForPlayback(track)"));
        assertTrue(preparationOwner.contains("mediaSourceProvider.mediaSourceForTrack("));
        assertTrue(preparationOwner.contains("metadataProvider == null ? null : metadataProvider::apply"));
        assertTrue(preparationOwner.contains("import java.util.function.Consumer;"));
        assertTrue(preparationOwner.contains("import java.util.function.Function;"));
        assertFalse(preparationOwner.contains("interface PlaybackPreparationProvider"));
        assertFalse(preparationOwner.contains("interface MediaSourceResolver"));
        assertTrue(preparationOwner.contains(
                "private final Function<Track, PlaybackMediaSourceProvider.PlaybackPreparation> playbackPreparationProvider;"));
        assertTrue(preparationOwner.contains("private final Function<Track, MediaSource> mediaSourceResolver;"));
        assertFalse(preparationOwner.contains("interface RestoredTrackProvider"));
        assertFalse(preparationOwner.contains("interface UnplayableMessageProvider"));
        assertTrue(preparationOwner.contains("interface QueuePreparationController"));
        assertTrue(preparationOwner.contains("interface RuntimeStateController"));
        assertFalse(preparationOwner.contains("interface StatePublisher"));
        assertFalse(preparationOwner.contains("interface RefusalLogger"));
        assertTrue(preparationOwner.contains("private final Runnable statePublisher;"));
        assertTrue(preparationOwner.contains("private final Consumer<Track> refusalLogger;"));
        assertTrue(preparationOwner.contains("playbackPreparationProvider.apply(track)"));
        assertTrue(preparationOwner.contains("return mediaSourceResolver.apply(track);"));
        assertTrue(preparationOwner.contains("preparation.getUnplayableMessage()"));
        assertTrue(preparationOwner.contains("queuePreparationController.replaceCurrentQueueTrack(restoredTrack)"));
        assertTrue(preparationOwner.contains("runtimeStateController.setErrorMessage(unplayableMessage)"));
        assertTrue(preparationOwner.contains("refusalLogger.accept(preparedTrack);"));
        assertTrue(preparationOwner.contains("statePublisher.run();"));
        assertTrue(preparationQueueOwner.contains(
                "final class PlaybackCurrentTrackPreparationQueueOwner"));
        assertTrue(preparationRuntimeOwner.contains(
                "final class PlaybackCurrentTrackPreparationRuntimeOwner"));
        assertTrue(preparationRuntimeOwner.contains(
                "implements PlaybackCurrentTrackPreparationOwner.RuntimeStateController"));
        assertFalse(preparationRuntimeOwner.contains("interface RuntimeStateOperations"));
        assertFalse(preparationRuntimeOwner.contains("interface RuntimeStateOperationsProvider"));
        assertFalse(preparationRuntimeOwner.contains("PlaybackRuntimeStateManagerOperations"));
        assertTrue(preparationRuntimeOwner.contains("import java.util.function.BooleanSupplier;"));
        assertTrue(preparationRuntimeOwner.contains("import java.util.function.Consumer;"));
        assertTrue(preparationRuntimeOwner.contains("private final Consumer<Boolean> setPreparing;"));
        assertTrue(preparationRuntimeOwner.contains("private final Consumer<String> setErrorMessage;"));
        assertTrue(preparationRuntimeOwner.contains("private final BooleanSupplier preparing;"));
        assertTrue(preparationRuntimeOwner.contains("runtimeStateManager::setPreparing"));
        assertTrue(preparationRuntimeOwner.contains("runtimeStateManager::setErrorMessage"));
        assertTrue(preparationRuntimeOwner.contains("runtimeStateManager::preparing"));
        assertTrue(preparationRuntimeOwner.contains("void beginPreparing()"));
        assertTrue(preparationRuntimeOwner.contains("void markPlaybackReady()"));
        assertTrue(preparationRuntimeOwner.contains("void markUnableToOpenCurrentTrack()"));
        assertTrue(preparationRuntimeOwner.contains("setPreparing(false);"));
        assertTrue(preparationRuntimeOwner.contains("setErrorMessage(\"\");"));
        assertTrue(preparationRuntimeOwner.contains("setErrorMessage(\"Unable to open this track.\");"));
        assertTrue(preparationRuntimeOwner.contains("return preparing != null && preparing.getAsBoolean();"));
        assertTrue(service.contains("new PlaybackCurrentTrackPreparationQueueOwner("));
        assertFalse(service.contains("PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager("));
        assertTrue(preparationQueueOwner.contains("PlaybackMediaSourceProvider mediaSourceProvider"));
        assertTrue(preparationQueueOwner.contains("mediaSourceProvider.mediaSourcesForTracks("));
        assertTrue(preparationQueueOwner.contains("metadataProvider == null ? null : metadataProvider::apply"));
        assertTrue(service.contains("private final PlaybackCurrentTrackPreparationRuntimeOwner playbackCurrentTrackPreparationRuntimeOwner"));
        assertTrue(service.contains("PlaybackCurrentTrackPreparationRuntimeOwner.fromRuntimeStateManager(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.beginPreparing();"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.markPlaybackReady();"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.markUnableToOpenCurrentTrack();"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner::preparing"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.preparing()"));
        assertFalse(service.contains("playbackRuntimeStateManager.setPreparing(true)"));
        assertFalse(service.contains("playbackRuntimeStateManager.preparing()"));
        assertFalse(service.contains("playbackRuntimeStateManager::preparing"));
        assertFalse(service.contains("playbackRuntimeStateManager.setErrorMessage(\"Unable to open this track.\")"));
        assertFalse(service.contains("new PlaybackCurrentTrackPreparationOwner.QueuePreparationController()"));
        assertFalse(service.contains("new PlaybackCurrentTrackPreparationOwner.RuntimeStateController()"));
        assertFalse(service.contains("if (unplayableMessage != null)"));
        assertFalse(service.contains("Track restoredTrack = mediaSourceProvider.restoredTrackForPreparation(track);"));
        assertFalse(service.contains("String unplayableMessage = PlaybackMediaSourceProvider.unplayableMessageForTrack(track);"));
        assertFalse(service.contains("mediaSourceProvider.mediaItemForTrack(track,"));
        assertFalse(service.contains("PlaybackMediaSourceProvider.playbackMediaItemForTrack("));
        assertFalse(service.contains("PlaybackMediaSourceProvider.mediaCacheKey("));
        assertFalse(service.contains("PlaybackMediaSourceProvider.mediaItemIdentityMatchesForReuse("));
        assertTrue(dataSource.contains("MediaItem mediaItemForTrack(Track track, Function<Track, MediaMetadata> metadataProvider)"));
        assertTrue(dataSource.contains("return mediaSourceProvider.mediaItemForTrack(track, metadataProvider::apply);"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackMediaSourceResolutionOwner.java")));
        assertFalse(service.contains("PlaybackMediaSourceResolutionOwner"));
        assertFalse(service.contains("playbackMediaSourceResolutionOwner"));
        assertFalse(service.contains("player.setMediaSource(mediaSourceProvider.mediaSourceForTrack("));
        assertFalse(service.contains("track -> mediaSourceProvider.mediaSourceForTrack("));
        assertTrue(service.contains("prepareSingleTrack(preparedTrack.track(), preparedTrack.mediaSource()"));
        assertFalse(service.contains("List<MediaSource> mediaSources = mediaSourceProvider.mediaSourcesForTracks("));
        assertFalse(service.contains("tracks -> mediaSourceProvider.mediaSourcesForTracks("));
        assertTrue(service.contains("playbackNotificationManager::mediaMetadataForTrack"));
        assertFalse(service.contains("playbackPrecacheManager.releaseAudioCache();"));
        assertFalse(service.contains("PlaybackPrecacheManager.audioCacheReleaseActionFromPrecacheManagerSupplier("));
        assertTrue(service.contains("PlaybackPrecacheManager::releaseAudioCache"));
        assertFalse(service.contains("PlaybackPrecacheManager.audioCacheReleaserFromPrecacheManagerProvider("));
        assertFalse(service.contains("mediaSourceProvider::releaseAudioCache"));
        assertTrue(precacheManager.contains("void releaseAudioCache()"));
        assertTrue(precacheManager.contains("audioCacheReleased.compareAndSet(false, true)"));
        assertTrue(precacheManager.contains("mediaSourceProvider.releaseAudioCache();"));
        assertFalse(service.contains("mediaSourceProvider.mediaItemMatchesTrackForReuse("));
        assertTrue(mirroredTrackMatcherOwner.contains("PlaybackMediaSourceProvider"));
        assertFalse(service.contains("mediaSourceProvider::mediaItemMatchesTrackForReuse"));
        assertTrue(service.contains("new PlaybackMirroredQueueTrackMatcherOwner("));
        assertFalse(mirroredTrackMatcherOwner.contains("fromPlayerProvider("));
        assertTrue(mirroredTrackMatcherOwner.contains("mediaSourceProvider.mediaItemMatchesTrackForReuse(mediaItem, track)"));
        assertFalse(service.contains("private boolean isStreamingPlaceholder"));
        assertFalse(service.contains("track.dataPath.startsWith(\"streaming:\")"));
        assertFalse(service.contains("Uri.EMPTY.equals(track.contentUri)"));
        assertFalse(service.contains("track.contentUri.toString()"));
        assertFalse(service.contains("dataPath.startsWith(\"streaming:\""));
        assertFalse(service.contains("dataPath.startsWith(\"webdav:\""));
        assertFalse(service.contains("|url="));
        assertFalse(service.contains(".setUri(track.contentUri)"));
        assertFalse(service.contains(".setMediaId(track.id.toString())"));
        assertFalse(service.contains(".setCustomCacheKey("));
        assertFalse(service.contains("new ArrayList<MediaSource>()"));
        assertFalse(service.contains("for (Track queueTrack : mirroredQueueTracks)"));
        assertFalse(service.contains("new MediaItem.Builder()"));
        assertFalse(service.contains("playbackMediaLibraryCallback.mediaItemForPlaybackTrack(track)"));
        assertFalse(service.contains("private MediaSource mediaSourceForTrack(Track track)"));
        assertFalse(service.contains("static String mediaCacheKey("));
        assertFalse(service.contains("static boolean mediaItemMatchesTrackForReuse("));
        assertFalse(service.contains("static boolean mediaItemIdentityMatchesForReuse("));
        assertFalse(service.contains("private static boolean cacheKeyMatchesForReuse("));
    }

    @Test
    public void playbackSleepTimerStateIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackSleepTimerManager.kt");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackSleepTimerCommandOwner.java");

        assertFalse(service.contains("sleepTimerEndsAtMs"));
        assertFalse(service.contains("sleepTimerRunnable"));
        assertFalse(service.contains("scheduleSleepTimer()"));
        assertFalse(service.contains("cancelSleepTimerInternal("));
        assertTrue(service.contains("private PlaybackSleepTimerManager playbackSleepTimerManager"));
        assertFalse(service.contains("playbackSleepTimerManager.startMinutes(minutes)"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.startSleepTimerMinutes(minutes);"));
        assertFalse(service.contains("playbackSleepTimerManager.cancel("));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.cancelSleepTimer(true);"));
        assertFalse(service.contains("playbackSleepTimerManager.remainingMs()"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.sleepTimerRemainingMs()"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.bindPlaybackSleepTimerManager(playbackSleepTimerManager);"));
        assertTrue(commandOwner.contains("long sleepTimerRemainingMs()"));
        assertTrue(commandOwner.contains("manager.remainingMs();"));
        assertTrue(owner.contains("class PlaybackSleepTimerManager"));
        assertTrue(owner.contains("fun startMinutes(minutes: Int)"));
        assertTrue(owner.contains("fun cancel(publish: Boolean)"));
        assertTrue(owner.contains("fun remainingMs(): Long"));
    }

    @Test
    public void playbackWifiLockIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedService = service.replace("\r\n", "\n");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackWifiLockManager.kt");
        String lockOwner = read("app/src/main/java/app/yukine/playback/PlaybackWifiLockOwner.java");

        assertFalse(service.contains("private android.net.wifi.WifiManager.WifiLock wifiLock"));
        assertFalse(service.contains("private void acquireWifiLockIfStreaming()"));
        assertFalse(service.contains("private void releaseWifiLock()"));
        assertFalse(service.contains("new PlaybackWifiLockManager.Lock()"));
        assertFalse(service.contains("new PlaybackWifiLockManager.StreamingTrackProvider()"));
        assertTrue(service.contains("PlaybackWifiLockOwner.fromWifiLock(wifiLock)"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackWifiLockStreamingTrackOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackWifiLockStreamingTrackOwnerTest.java")));
        assertFalse(service.contains("PlaybackWifiLockStreamingTrackOwner"));
        assertFalse(normalizedService.contains(
                "                playbackQueueStateOwner::currentTrack,\n" +
                        "                mediaSourceProvider::isHttpTrack"));
        assertFalse(service.contains(
                "                playbackQueueManager,\n" +
                        "                mediaSourceProvider::isHttpTrack"));
        assertEquals(1, countOccurrences(service, "playbackNotificationStateOwner::currentTrack"));
        assertEquals(1, countOccurrences(service,
                "final Supplier<Track> currentTrackSupplier = playbackNotificationStateOwner::currentTrack;"));
        assertTrue(service.contains(
                "                currentTrackSupplier,\n" +
                        "                mediaSourceProvider::isHttpTrack"));
        assertFalse(service.contains(
                "                playbackQueueStateOwner::queueStateSnapshot,\n" +
                        "                mediaSourceProvider::isHttpTrack"));
        assertTrue(service.contains("private PlaybackWifiLockManager playbackWifiLockManager"));
        assertTrue(service.contains("private final Runnable acquireWifiLockIfStreamingAction"));
        assertTrue(service.contains("private final Runnable releaseWifiLockAction"));
        assertTrue(service.contains("PlaybackWifiLockManager.acquireIfStreamingAction(() -> playbackWifiLockManager)"));
        assertTrue(service.contains("PlaybackWifiLockManager.releaseAction(() -> playbackWifiLockManager)"));
        assertTrue(service.contains("acquireWifiLockIfStreamingAction.run();"));
        assertFalse(service.contains("playbackWifiLockManager.acquireIfStreaming()"));
        assertTrue(service.contains("PlaybackWifiLockManager::release"));
        assertTrue(owner.contains("class PlaybackWifiLockManager"));
        assertTrue(owner.contains("interface Lock"));
        assertFalse(owner.contains("interface StreamingTrackProvider"));
        assertFalse(owner.contains("streamingTrackProvider.currentTrack()"));
        assertTrue(owner.contains("private val currentTrackSupplier: Supplier<Track?>?"));
        assertTrue(owner.contains("return currentTrackSupplier?.get()"));
        assertFalse(owner.contains("private val playbackQueueManager: PlaybackQueueManager?"));
        assertFalse(owner.contains("return playbackQueueManager?.queueStateSnapshot()?.currentTrack"));
        assertFalse(owner.contains("Supplier<PlaybackQueueManager.QueueStateSnapshot?>"));
        assertFalse(owner.contains("queueStateSupplier?.get()?.currentTrack"));
        assertFalse(owner.contains("private val mediaSourceProvider: PlaybackMediaSourceProvider"));
        assertTrue(owner.contains("private val streamingTrackPredicate: Predicate<Track?>"));
        assertTrue(owner.contains("streamingTrackPredicate.test(track)"));
        assertFalse(owner.contains("fun isHttpUri(uri: Uri?): Boolean"));
        assertTrue(owner.contains("fun acquireIfStreaming()"));
        assertTrue(owner.contains("fun release()"));
        assertTrue(owner.contains("fun acquireIfStreamingAction("));
        assertTrue(owner.contains("fun releaseAction("));
        assertTrue(lockOwner.contains("final class PlaybackWifiLockOwner implements PlaybackWifiLockManager.Lock"));
        assertTrue(lockOwner.contains("static PlaybackWifiLockOwner fromWifiLock(WifiManager.WifiLock wifiLock)"));
        assertTrue(lockOwner.contains("private static final class AndroidWifiLockOperations"));
        assertTrue(lockOwner.contains("wifiLock.acquire()"));
        assertTrue(lockOwner.contains("wifiLock.release()"));
    }

    @Test
    public void playbackNoisyReceiverIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackNoisyReceiverManager.kt");

        assertFalse(service.contains("private final BroadcastReceiver noisyReceiver"));
        assertFalse(service.contains("noisyReceiverRegistered"));
        assertFalse(service.contains("private void registerNoisyReceiver()"));
        assertFalse(service.contains("private void unregisterNoisyReceiver()"));
        assertTrue(service.contains("private PlaybackNoisyReceiverManager playbackNoisyReceiverManager"));
        assertTrue(service.contains("playbackNoisyReceiverManager.register()"));
        assertTrue(service.contains("PlaybackNoisyReceiverManager::unregister"));
        assertFalse(service.contains("playbackNoisyReceiverManager.unregister()"));
        assertTrue(owner.contains("class PlaybackNoisyReceiverManager"));
        assertTrue(owner.contains("interface Registrar"));
        assertTrue(owner.contains("interface Actions"));
        assertTrue(owner.contains("fun register()"));
        assertTrue(owner.contains("fun unregister()"));
    }

    @Test
    public void playbackProgressUpdatesAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackProgressUpdateManager.kt");

        assertFalse(service.contains("private final Runnable progressRunnable"));
        assertFalse(service.contains("mainHandler.removeCallbacks(progressRunnable)"));
        assertFalse(service.contains("mainHandler.postDelayed(progressRunnable"));
        assertTrue(service.contains("private PlaybackProgressUpdateManager playbackProgressUpdateManager"));
        assertTrue(service.contains("private void startProgressUpdates()"));
        assertTrue(service.contains("private void stopProgressUpdates()"));
        assertTrue(service.contains("playbackProgressUpdateManager.startIfNeeded()"));
        assertTrue(service.contains("playbackProgressUpdateManager.stop()"));
        assertFalse(service.contains("playbackProgressUpdateCommandOwner.startProgressUpdates()"));
        assertFalse(service.contains("playbackProgressUpdateCommandOwner.stopProgressUpdates()"));
        assertTrue(owner.contains("class PlaybackProgressUpdateManager"));
        assertTrue(owner.contains("interface CallbackScheduler"));
        assertTrue(owner.contains("interface StateProvider"));
        assertTrue(owner.contains("interface Actions"));
        assertTrue(owner.contains("fun startIfNeeded()"));
        assertTrue(owner.contains("fun stop()"));
    }

    @Test
    public void playbackStateBroadcastsAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/PlaybackStatePublisher.kt");
        String widgetOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackStatePublisherWidgetOwner.java"
        );
        String streamingDiagnosticsOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackStreamingDiagnosticsRecorderOwner.java"
        );

        assertFalse(service.contains("private final Set<PlaybackStateListener> listeners"));
        assertFalse(service.contains("listeners.add(listener)"));
        assertFalse(service.contains("listeners.remove(listener)"));
        assertFalse(service.contains("for (PlaybackStateListener listener : listeners)"));
        assertTrue(service.contains("playbackStatePublisher.registerListener(listener)"));
        assertTrue(service.contains("playbackStatePublisher.unregisterListener(listener)"));
        assertTrue(service.contains("playbackStatePublisher.publishState()"));
        assertTrue(service.contains("playbackStatePublisher.publishBufferingState("));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackStatePublisherNotificationOwner.java")));
        assertFalse(service.contains("PlaybackStatePublisherNotificationOwner"));
        assertTrue(service.contains(
                "PlaybackNotificationCommandOwner.notificationUpdaterFromNotificationManagerSupplier("));
        assertTrue(service.contains("() -> playbackNotificationManager"));
        assertFalse(service.contains("playbackNotificationManager.updateMediaNotification(force);"));
        assertTrue(service.contains("                playbackNotificationArtworkSource,"));
        assertTrue(service.contains("PlaybackStatePublisherWidgetOwner.fromContext(this)"));
        assertFalse(service.contains("PlaybackStatePublisherWidgetOwner.fromContextProvider(() -> this)"));
        assertTrue(service.contains("private PlaybackStreamingDiagnosticsRecorderOwner playbackStreamingDiagnosticsRecorderOwner;"));
        assertTrue(service.contains("new PlaybackStreamingDiagnosticsRecorderOwner("));
        assertFalse(service.contains("() -> streamingDiagnostics"));
        assertFalse(service.contains("private PlaybackBufferingDiagnosticsRecorderOwner playbackBufferingDiagnosticsRecorderOwner;"));
        assertFalse(service.contains("new PlaybackBufferingDiagnosticsRecorderOwner("));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackBufferingDiagnosticsRecorderOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackRecoveryDiagnosticsRecorderOwner.java")));
        assertFalse(service.contains("PlaybackStreamingDiagnosticsRecorderOwner.fromStreamingDiagnosticsProvider("));
        assertFalse(streamingDiagnosticsOwner.contains(
                "static PlaybackStreamingDiagnosticsRecorderOwner fromStreamingDiagnosticsProvider("));
        assertTrue(service.contains("playbackStatePublisher.publishBufferingState(playbackStreamingDiagnosticsRecorderOwner);"));
        assertFalse(service.contains("force -> playbackNotificationManager.updateMediaNotification(force)"));
        assertFalse(service.contains("track -> playbackNotificationArtworkManager.notificationArtworkFor(track)"));
        assertFalse(service.contains("EchoPlaybackWidgetProvider.update(this, snapshot, artwork)"));
        assertFalse(service.contains("new PlaybackStatePublisher.BufferingRecorder()"));
        assertFalse(service.contains("streamingDiagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs)"));
        assertTrue(owner.contains("class PlaybackStatePublisher"));
        assertTrue(owner.contains("fun registerListener(listener: PlaybackStateListener?)"));
        assertTrue(owner.contains("fun unregisterListener(listener: PlaybackStateListener?)"));
        assertTrue(owner.contains("fun publishBufferingState("));
        assertTrue(owner.contains("fun release()"));
        assertTrue(owner.contains("listeners.clear()"));
        assertFalse(service.contains("private void publishPlaybackNotification(boolean force)"));
        assertFalse(service.contains("EchoPlaybackService.this::publishPlaybackNotification"));
        assertTrue(widgetOwner.contains(
                "final class PlaybackStatePublisherWidgetOwner implements PlaybackStatePublisher.WidgetUpdater"));
        assertTrue(widgetOwner.contains("interface WidgetOperations"));
        assertFalse(widgetOwner.contains("interface ContextProvider"));
        assertFalse(widgetOwner.contains("interface WidgetOperationsProvider"));
        assertTrue(widgetOwner.contains("private final Context context;"));
        assertFalse(widgetOwner.contains("Supplier<Context>"));
        assertTrue(widgetOwner.contains("static PlaybackStatePublisherWidgetOwner fromContext(Context context)"));
        assertFalse(widgetOwner.contains("fromContextProvider("));
        assertFalse(widgetOwner.contains("Supplier<WidgetOperations>"));
        assertFalse(widgetOwner.contains("widgetOperationsProvider"));
        assertFalse(widgetOwner.contains("EchoPlaybackWidgetOperations"));
        assertTrue(widgetOwner.contains("private final WidgetOperations widgetOperations;"));
        assertTrue(widgetOwner.contains("EchoPlaybackWidgetProvider::update"));
        assertTrue(widgetOwner.contains("widgetOperations.update(context, snapshot, artwork);"));
        assertTrue(streamingDiagnosticsOwner.contains(
                "final class PlaybackStreamingDiagnosticsRecorderOwner"));
        assertTrue(streamingDiagnosticsOwner.contains(
                "implements PlaybackStatePublisher.BufferingRecorder"));
        assertFalse(streamingDiagnosticsOwner.contains("interface StreamingDiagnosticsProvider"));
        assertTrue(streamingDiagnosticsOwner.contains(
                "private final PlaybackStreamingDiagnostics streamingDiagnostics;"));
        assertFalse(streamingDiagnosticsOwner.contains(
                "private final Supplier<PlaybackStreamingDiagnostics> streamingDiagnosticsProvider;"));
        assertFalse(streamingDiagnosticsOwner.contains("private PlaybackStreamingDiagnostics streamingDiagnostics()"));
        assertFalse(streamingDiagnosticsOwner.contains("streamingDiagnosticsProvider.get()"));
        assertFalse(streamingDiagnosticsOwner.contains("interface StreamingDiagnosticsOperations"));
        assertFalse(streamingDiagnosticsOwner.contains("StreamingDiagnosticsOperationsProvider"));
        assertTrue(streamingDiagnosticsOwner.contains("diagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);"));
    }

    @Test
    public void playbackErrorRecoveryIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackErrorRecoveryManager.kt");
        String queueOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt");
        String queueNavigationOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueNavigationOwner.java");
        String queueStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueStateOwner.java");

        assertFalse(service.contains("Retrying streaming track after error"));
        assertFalse(service.contains("Skipping unplayable track"));
        assertTrue(service.contains("playbackErrorRecoveryManager.onPlaybackReady()"));
        assertTrue(service.contains("playbackErrorRecoveryManager.onPlayerError(error)"));
        assertFalse(service.contains("public boolean hasMultipleQueueTracks()"));
        assertFalse(service.contains("return EchoPlaybackService.this.hasMultipleQueueTracks();"));
        String errorRecoveryCommandWiring = service.substring(
                service.indexOf("playbackErrorRecoveryCommandOwner = new PlaybackErrorRecoveryCommandOwner("),
                service.indexOf("        playbackErrorRecoveryManager = new PlaybackErrorRecoveryManager(")
        );
        assertFalse(errorRecoveryCommandWiring.contains("queueStateSupplier"));
        assertFalse(errorRecoveryCommandWiring.contains("playbackQueueStateOwner::currentTrack"));
        assertFalse(errorRecoveryCommandWiring.contains("playbackQueueStateOwner::hasMultipleTracks"));
        assertTrue(errorRecoveryCommandWiring.contains("                playbackQueueStateOwner,"));
        assertFalse(service.contains("playbackQueueManager.canSkipFailedTrack(failed)"));
        assertFalse(queueNavigationOwner.contains("playbackQueueManager.canSkipFailedTrack(failed);"));
        assertTrue(owner.contains("class PlaybackErrorRecoveryManager"));
        assertFalse(owner.contains("private val mediaSourceProvider: PlaybackMediaSourceProvider"));
        assertTrue(owner.contains("private val streamingTrackPredicate: Predicate<Track?>"));
        assertTrue(owner.contains("private var lastErrorTrackId"));
        assertTrue(owner.contains("streamingTrackPredicate.test(failed)"));
        assertFalse(owner.contains("fun isHttpUri(uri: Uri?): Boolean"));
        assertTrue(owner.contains("fun canSkipFailedTrack(failed: Track?): Boolean"));
        assertTrue(owner.contains("actions.canSkipFailedTrack(failed)"));
        assertFalse(owner.contains("fun hasMultipleQueueTracks(): Boolean"));
        assertFalse(owner.contains("actions.hasMultipleQueueTracks()"));
        assertFalse(queueOwner.contains("fun canSkipFailedTrack(failed: Track?): Boolean"));
        assertFalse(queueStateOwner.contains("PlaybackErrorRecoveryCommandOwner.FailedTrackPolicy"));
        assertFalse(queueStateOwner.contains("public boolean canSkipFailedTrack("));
        assertFalse(queueStateOwner.contains("boolean canSkipFailedTrack("));
        assertFalse(queueStateOwner.contains("failed != null && failed.id != -1L && queueStateSnapshot().getHasMultipleTracks()"));
        assertTrue(queueStateOwner.contains("boolean hasMultipleTracks()"));
        assertTrue(queueStateOwner.contains("boolean isAtEndOfQueue()"));
        assertFalse(owner.contains("fun queueSize(): Int"));
        assertFalse(owner.contains("actions.queueSize() > 1"));
        assertTrue(owner.contains("fun onPlaybackReady()"));
        assertTrue(owner.contains("fun onPlayerError(error: Exception)"));
    }

    @Test
    public void playbackRestoreEnablementIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt");
        String store = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueStore.kt");
        String restoreOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueRestoreOwner.java");

        assertFalse(service.contains("private boolean playbackRestoreEnabled"));
        assertFalse(service.contains("if (playbackQueueManager == null && !repository.loadPlaybackRestoreEnabled())"));
        assertFalse(service.contains("if (!playbackRestoreEnabled)"));
        assertFalse(service.contains("repository.loadPlaybackRestoreEnabled()"));
        assertFalse(service.contains("repository.savePlaybackRestoreEnabled("));
        assertTrue(service.contains("playbackQueueRestoreOwner().setPlaybackRestoreEnabled(enabled);"));
        assertFalse(service.contains("playbackQueueManager.setPlaybackRestoreEnabled(enabled)"));
        assertTrue(restoreOwner.contains("playbackQueueManager.setPlaybackRestoreEnabled(enabled);"));
        assertTrue(owner.contains("private var playbackRestoreEnabled = queueStore.loadPlaybackRestoreEnabled()"));
        assertTrue(owner.contains("fun setPlaybackRestoreEnabled(enabled: Boolean)"));
        assertTrue(owner.contains("queueStore.savePlaybackRestoreEnabled(enabled)"));
        assertTrue(owner.contains("if (!playbackRestoreEnabled)"));
        assertTrue(store.contains("fun loadPlaybackRestoreEnabled(): Boolean"));
        assertTrue(store.contains("fun savePlaybackRestoreEnabled(enabled: Boolean)"));
        assertTrue(store.contains("repository.loadPlaybackRestoreEnabled()"));
        assertTrue(store.contains("repository.savePlaybackRestoreEnabled(enabled)"));
    }

    @Test
    public void playbackHistoryRecordingIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedService = service.replace("\r\n", "\n");
        String owner = read("app/src/main/java/app/yukine/playback/PlaybackPlayHistoryRecorder.java");

        assertFalse(service.contains("repository.markPlayed("));
        assertFalse(service.contains("playbackTransitionStateManager.lastMarkedTrack() == null"));
        assertFalse(service.contains("playbackTransitionStateManager.setLastMarkedTrack(track);"));
        assertFalse(service.contains("private PlaybackPlayHistoryRecorder playbackPlayHistoryRecorder;"));
        assertTrue(service.contains("private Runnable recordPlaybackStartHistoryAction"));
        assertTrue(service.contains("PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction("));
        assertTrue(normalizedService.contains(
                "PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(\n" +
                        "                playbackPlayHistoryRecorder,\n" +
                        "                () -> player != null && player.getPlayWhenReady(),\n" +
                        "                playbackQueueStateOwner\n" +
                        "        );"));
        assertFalse(normalizedService.contains(
                "PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(\n" +
                        "                playbackPlayHistoryRecorder,\n" +
                        "                () -> player != null && player.getPlayWhenReady(),\n" +
                        "                currentTrackSupplier\n" +
                        "        );"));
        assertTrue(service.contains("final PlaybackPlayHistoryRecorder playbackPlayHistoryRecorder = PlaybackPlayHistoryRecorder.fromRepository("));
        assertFalse(service.contains("() -> playbackPlayHistoryRecorder"));
        assertTrue(service.contains("recordPlaybackStartHistoryAction.run();"));
        assertFalse(service.contains("playbackPlayHistoryRecorder.recordIfPlaybackStarted("));
        assertTrue(service.contains("PlaybackPlayHistoryRecorder.fromRepository("));
        assertFalse(owner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertTrue(owner.contains("interface HistorySink"));
        assertTrue(owner.contains("static Runnable recordIfPlaybackStartedAction("));
        assertTrue(owner.contains("PlaybackQueueStateOwner queueStateOwner"));
        assertFalse(owner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(owner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(owner.contains("snapshot.getCurrentTrack()"));
        assertTrue(owner.contains("queueStateOwner == null ? null : queueStateOwner.currentTrack()"));
        assertFalse(owner.contains("queueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertFalse(owner.contains("Supplier<Track> currentTrack"));
        assertFalse(owner.contains("currentTrack == null ? null : currentTrack.get()"));
        assertTrue(owner.contains("void recordIfPlaybackStarted(boolean playWhenReady, Track track)"));
        assertTrue(owner.contains("historySink.markPlayed(track.id);"));
        assertTrue(owner.contains("transitionStateManager.lastMarkedTrack()"));
        assertTrue(owner.contains("transitionStateManager.setLastMarkedTrack(track);"));
    }

    @Test
    public void playbackRuntimeStateIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedService = service.replace("\r\n", "\n");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackRuntimeStateManager.kt");
        String modeStore = read("app/src/main/java/app/yukine/playback/PlaybackModeSettingsStore.java");
        String runtimeStore = read("app/src/main/java/app/yukine/playback/PlaybackRuntimeSettingsStore.java");
        String stateSnapshotOwner = read("app/src/main/java/app/yukine/playback/PlaybackStateSnapshotOwner.java");
        String crossfadeCommandOwner = read("app/src/main/java/app/yukine/playback/PlaybackCrossfadeCommandOwner.java");

        assertFalse(service.contains("private float playbackSpeed"));
        assertFalse(service.contains("private float appVolume"));
        assertFalse(service.contains("private boolean shuffleEnabled"));
        assertFalse(service.contains("private int repeatMode"));
        assertFalse(service.contains("private boolean replayGainEnabled"));
        assertFalse(service.contains("private boolean concurrentPlaybackEnabled"));
        assertFalse(service.contains("private boolean preparing"));
        assertFalse(service.contains("private String errorMessage"));
        assertFalse(service.contains("shuffleEnabled = repository.loadShuffleEnabled()"));
        assertFalse(service.contains("repeatMode = repository.loadRepeatMode()"));
        assertFalse(service.contains("repository.loadShuffleEnabled()"));
        assertFalse(service.contains("repository.loadRepeatMode()"));
        assertFalse(service.contains("playbackSpeed = playbackRuntimeStateManager.normalizePlaybackSpeed(speed)"));
        assertFalse(service.contains("appVolume = playbackRuntimeStateManager.normalizeAppVolume(volume)"));
        assertFalse(service.contains("repository.saveShuffleEnabled(enabled)"));
        assertFalse(service.contains("repository.saveRepeatMode(mode)"));
        assertFalse(service.contains("repository.saveShuffleEnabled("));
        assertFalse(service.contains("repository.saveRepeatMode("));
        assertFalse(service.contains("replayGainEnabled = repository.loadReplayGainEnabled()"));
        assertFalse(service.contains("repository.loadReplayGainEnabled()"));
        assertFalse(service.contains("repository.loadConcurrentPlaybackEnabled()"));
        assertFalse(service.contains("repository.loadPlaybackSpeed()"));
        assertFalse(service.contains("repository.loadAppVolume()"));
        assertFalse(service.contains("static int media3RepeatModeForAppRepeatMode("));
        assertFalse(service.contains("static int appRepeatModeForMedia3RepeatMode("));
        assertTrue(owner.contains("fun media3RepeatModeForAppRepeatMode(appRepeatMode: Int, playerMirrorsQueue: Boolean): Int"));
        assertFalse(service.contains("concurrentPlaybackEnabled = enabled"));
        assertFalse(service.contains("return playbackSpeed;"));
        assertFalse(service.contains("return appVolume;"));
        assertFalse(service.contains("return shuffleEnabled;"));
        assertFalse(service.contains("return repeatMode;"));
        assertFalse(service.contains("return concurrentPlaybackEnabled;"));
        assertTrue(service.contains("playbackModeSettingsStore.restoreInto(playbackRuntimeStateManager)"));
        assertTrue(service.contains("private PlaybackRuntimeSettingsStore playbackRuntimeSettingsStore;"));
        assertTrue(service.contains("playbackRuntimeSettingsStore = PlaybackRuntimeSettingsStore.fromRepository(repository)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.restoreInto(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackModeSettingsStore.setShuffleEnabled(playbackRuntimeStateManager, enabled)"));
        assertTrue(service.contains("playbackModeSettingsStore.setRepeatMode(playbackRuntimeStateManager, mode)"));
        assertTrue(service.contains("playbackModeSettingsStore.cycleRepeatMode(playbackRuntimeStateManager)"));
        String setShuffleMethod = service.substring(
                service.indexOf("public void setShuffleEnabled(boolean enabled)"),
                service.indexOf("public void setRepeatMode(int mode)")
        );
        String setRepeatMethod = service.substring(
                service.indexOf("public void setRepeatMode(int mode)"),
                service.indexOf("public void cycleRepeatMode()")
        );
        String cycleRepeatMethod = service.substring(
                service.indexOf("public void cycleRepeatMode()"),
                service.indexOf("public void setPlaybackSpeed(float speed)")
        );
        assertFalse(setShuffleMethod.contains("applyPlaybackModeToPlayer();"));
        assertFalse(setRepeatMethod.contains("applyPlaybackModeToPlayer();"));
        assertFalse(cycleRepeatMethod.contains("applyPlaybackModeToPlayer();"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.setPlaybackSpeed(playbackRuntimeStateManager, speed)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.setAppVolume(playbackRuntimeStateManager, volume)"));
        assertTrue(service.contains(
                "playbackRuntimeSettingsStore.setConcurrentPlaybackEnabled(playbackRuntimeStateManager, enabled)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.setReplayGainEnabled(playbackRuntimeStateManager, enabled)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.playbackSpeed(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.appVolume(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.concurrentPlaybackEnabled(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.currentTrackVolume(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.applyPlaybackParametersToPlayer(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.applyCurrentTrackVolumeToPlayer(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackRuntimeSettingsStore.applyAudioFocusHandling(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackModeSettingsStore.repeatMode(playbackRuntimeStateManager)"));
        assertTrue(service.contains("playbackModeSettingsStore.applyPlaybackModeToPlayer(playbackRuntimeStateManager)"));
        assertFalse(service.contains("private void applyPlaybackModeToPlayer()"));
        assertFalse(service.contains("playbackRuntimeStateManager.setPlaybackSpeed(speed)"));
        assertFalse(service.contains("playbackRuntimeStateManager.setAppVolume(volume)"));
        assertFalse(service.contains("playbackRuntimeStateManager.setReplayGainEnabled(enabled)"));
        assertFalse(service.contains("playbackRuntimeStateManager.setConcurrentPlaybackEnabled(enabled)"));
        assertFalse(service.contains("playbackRuntimeStateManager.playbackSpeed()"));
        assertFalse(service.contains("playbackRuntimeStateManager.appVolume()"));
        assertFalse(service.contains("playbackRuntimeStateManager.concurrentPlaybackEnabled()"));
        assertFalse(service.contains("playbackRuntimeStateManager.currentTrackVolume()"));
        assertFalse(service.contains("playbackRuntimeStateManager.repeatMode()"));
        assertFalse(service.contains("playbackRuntimeStateManager.applyPlaybackModeToPlayer()"));
        assertFalse(service.contains("playbackRuntimeStateManager.applyPlaybackParametersToPlayer()"));
        assertFalse(service.contains("playbackRuntimeStateManager.applyCurrentTrackVolumeToPlayer()"));
        assertFalse(service.contains("playbackRuntimeStateManager.applyPlaybackModeAndParametersToPlayer()"));
        assertFalse(service.contains("playbackRuntimeStateManager.applyAudioFocusHandling()"));
        String setPlaybackSpeedMethod = service.substring(
                service.indexOf("public void setPlaybackSpeed(float speed)"),
                service.indexOf("public float playbackSpeed()")
        );
        String setAppVolumeMethod = service.substring(
                service.indexOf("public void setAppVolume(float volume)"),
                service.indexOf("public float appVolume()")
        );
        String setConcurrentPlaybackMethod = service.substring(
                service.indexOf("public void setConcurrentPlaybackEnabled(boolean enabled)"),
                service.indexOf("public boolean concurrentPlaybackEnabled()")
        );
        String setReplayGainMethod = service.substring(
                service.indexOf("public void setReplayGainEnabled(boolean enabled)"),
                service.indexOf("public AudioEffectSettings audioEffectSettings()")
        );
        String crossfadeCommandOwnerConstruction = service.substring(
                service.indexOf("playbackCrossfadeCommandOwner = new PlaybackCrossfadeCommandOwner("),
                service.indexOf("playbackCrossfadeStateOwner = new PlaybackCrossfadeStateOwner(")
        );
        assertFalse(setPlaybackSpeedMethod.contains("applyPlaybackParametersToPlayer();"));
        assertFalse(setAppVolumeMethod.contains("applyPlaybackParametersToPlayer();"));
        assertFalse(setConcurrentPlaybackMethod.contains("applyAudioFocusHandling();"));
        assertFalse(setReplayGainMethod.contains("applyPlaybackParametersToPlayer();"));
        assertFalse(crossfadeCommandOwnerConstruction.contains("playbackRuntimeStateManager.applyCurrentTrackVolumeToPlayer();"));
        assertFalse(crossfadeCommandOwnerConstruction.contains(
                "playbackRuntimeSettingsStore.applyCurrentTrackVolumeToPlayer(playbackRuntimeStateManager);"
        ));
        assertTrue(crossfadeCommandOwnerConstruction.contains(
                "EchoPlaybackService.this::applyCurrentTrackVolumeToPlayer"
        ));
        assertFalse(crossfadeCommandOwnerConstruction.contains(
                "PlaybackCrossfadeCommandOwner.appVolumeApplierFromRuntimeStateManagerProvider("
        ));
        assertFalse(crossfadeCommandOwner.contains("PlaybackRuntimeStateManager"));
        assertFalse(crossfadeCommandOwner.contains("interface RuntimeStateManagerProvider"));
        assertFalse(crossfadeCommandOwner.contains("manager.applyCurrentTrackVolumeToPlayer();"));
        assertTrue(runtimeStore.contains(
                "void applyCurrentTrackVolumeToPlayer(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains("runtimeStateManager.applyCurrentTrackVolumeToPlayer();"));
        assertFalse(service.contains("playbackRuntimeStateManager.setPreparing(true)"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.beginPreparing();"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner::setPreparing"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false)"));
        assertFalse(service.contains("playbackRuntimeStateManager.setPreparing(false)"));
        assertFalse(service.contains("playbackRuntimeStateManager.setErrorMessage("));
        assertFalse(service.contains("playbackErrorRecoveryCommandOwner.setErrorMessage(\"\")"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner.preparing()"));
        assertFalse(service.contains("playbackRuntimeStateManager.preparing()"));
        assertFalse(service.contains("playbackRuntimeStateManager::preparing"));
        assertFalse(service.contains("playbackRuntimeStateManager.errorMessage()"));
        assertTrue(stateSnapshotOwner.contains("runtimeStateManager.errorMessage()"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackRuntimeStateOwner.java")));
        assertFalse(service.contains("PlaybackRuntimeStateOwner"));
        assertTrue(service.contains("PlaybackRuntimeStateManager.stateProviderFromPlaybackState("));
        String runtimeStateProviderWiring = normalizedService.substring(
                normalizedService.indexOf("private final PlaybackRuntimeStateManager playbackRuntimeStateManager ="),
                normalizedService.indexOf("    private final PlaybackCurrentTrackPreparationRuntimeOwner")
        );
        assertFalse(runtimeStateProviderWiring.contains("                            playbackQueueStateOwner::currentTrack\n"));
        assertTrue(runtimeStateProviderWiring.contains("                            () -> playbackQueueManager\n"));
        assertFalse(runtimeStateProviderWiring.contains("                            playbackQueueManager\n"));
        assertFalse(runtimeStateProviderWiring.contains(
                "playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertFalse(service.contains("new PlaybackRuntimeStateManager.StateProvider()"));
        assertTrue(owner.contains("private var shuffleEnabled"));
        assertTrue(owner.contains("private var repeatMode"));
        assertTrue(owner.contains("fun setShuffleEnabled(enabled: Boolean)"));
        assertTrue(owner.contains("fun setRepeatMode(mode: Int)"));
        assertTrue(owner.contains("fun cycleRepeatMode()"));
        assertTrue(owner.contains("fun setPlaybackSpeed(speed: Float)"));
        assertTrue(owner.contains("fun setAppVolume(volume: Float)"));
        assertTrue(owner.contains("fun setReplayGainEnabled(enabled: Boolean)"));
        assertTrue(owner.contains("fun setConcurrentPlaybackEnabled(enabled: Boolean)"));
        assertTrue(owner.contains("applyPlaybackParametersToPlayer()"));
        assertTrue(owner.contains("applyAudioFocusHandling()"));
        assertTrue(owner.contains("fun shuffleEnabled(): Boolean"));
        assertTrue(owner.contains("fun repeatMode(): Int"));
        assertTrue(modeStore.contains("interface ModeSettings"));
        assertTrue(modeStore.contains("void restoreInto(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(modeStore.contains("void setShuffleEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled)"));
        assertTrue(modeStore.contains("void setRepeatMode(PlaybackRuntimeStateManager runtimeStateManager, int mode)"));
        assertTrue(modeStore.contains("void cycleRepeatMode(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(modeStore.contains("runtimeStateManager.applyPlaybackModeToPlayer();"));
        assertTrue(modeStore.contains("repository.loadShuffleEnabled()"));
        assertTrue(modeStore.contains("repository.loadRepeatMode()"));
        assertTrue(modeStore.contains("repository.saveShuffleEnabled(enabled)"));
        assertTrue(modeStore.contains("repository.saveRepeatMode(repeatMode)"));
        assertTrue(runtimeStore.contains("interface RuntimeSettings"));
        assertTrue(runtimeStore.contains("void restoreInto(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains("void setPlaybackSpeed(PlaybackRuntimeStateManager runtimeStateManager, float speed)"));
        assertTrue(runtimeStore.contains("void setAppVolume(PlaybackRuntimeStateManager runtimeStateManager, float volume)"));
        assertTrue(runtimeStore.contains(
                "void setConcurrentPlaybackEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled)"));
        assertTrue(runtimeStore.contains(
                "void setReplayGainEnabled(PlaybackRuntimeStateManager runtimeStateManager, boolean enabled)"));
        assertTrue(runtimeStore.contains("float playbackSpeed(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains("float appVolume(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains("float currentTrackVolume(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains(
                "boolean concurrentPlaybackEnabled(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains(
                "void applyPlaybackParametersToPlayer(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains(
                "void applyAudioFocusHandling(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(runtimeStore.contains("runtimeStateManager.setPlaybackSpeed(speed);"));
        assertTrue(runtimeStore.contains("runtimeStateManager.setAppVolume(volume);"));
        assertTrue(runtimeStore.contains("runtimeStateManager.setConcurrentPlaybackEnabled(enabled);"));
        assertTrue(runtimeStore.contains("runtimeStateManager.setReplayGainEnabled(enabled);"));
        assertTrue(runtimeStore.contains("runtimeStateManager.playbackSpeed()"));
        assertTrue(runtimeStore.contains("runtimeStateManager.appVolume()"));
        assertTrue(runtimeStore.contains("runtimeStateManager.currentTrackVolume()"));
        assertTrue(runtimeStore.contains("runtimeStateManager.concurrentPlaybackEnabled()"));
        assertTrue(runtimeStore.contains("runtimeStateManager.applyPlaybackParametersToPlayer();"));
        assertTrue(runtimeStore.contains("runtimeStateManager.applyAudioFocusHandling();"));
        assertTrue(modeStore.contains(
                "void applyPlaybackModeToPlayer(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(modeStore.contains("int repeatMode(PlaybackRuntimeStateManager runtimeStateManager)"));
        assertTrue(modeStore.contains("runtimeStateManager.repeatMode()"));
        assertTrue(runtimeStore.contains("repository.loadReplayGainEnabled()"));
        assertTrue(runtimeStore.contains("repository.loadConcurrentPlaybackEnabled()"));
        assertTrue(runtimeStore.contains("repository.loadPlaybackSpeed()"));
        assertTrue(runtimeStore.contains("repository.loadAppVolume()"));
        assertTrue(owner.contains("private var playbackSpeed"));
        assertTrue(owner.contains("private var appVolume"));
        assertTrue(owner.contains("fun setPlaybackSpeed(speed: Float)"));
        assertTrue(owner.contains("fun setAppVolume(volume: Float)"));
        assertTrue(owner.contains("fun playbackSpeed(): Float"));
        assertTrue(owner.contains("fun appVolume(): Float"));
        assertTrue(owner.contains("private var replayGainEnabled"));
        assertTrue(owner.contains("private var concurrentPlaybackEnabled"));
        assertTrue(owner.contains("fun setReplayGainEnabled(enabled: Boolean)"));
        assertTrue(owner.contains("fun setConcurrentPlaybackEnabled(enabled: Boolean)"));
        assertTrue(owner.contains("fun replayGainEnabled(): Boolean"));
        assertTrue(owner.contains("fun concurrentPlaybackEnabled(): Boolean"));
        assertTrue(owner.contains("private var preparing"));
        assertTrue(owner.contains("private var errorMessage"));
        assertTrue(owner.contains("fun setPreparing(preparing: Boolean)"));
        assertTrue(owner.contains("fun preparing(): Boolean"));
        assertTrue(owner.contains("fun setErrorMessage(message: String?)"));
        assertTrue(owner.contains("fun errorMessage(): String"));
        assertFalse(owner.contains("fun queueIsEmpty(): Boolean"));
        assertTrue(owner.contains("fun stateProviderFromPlaybackState("));
        assertTrue(owner.contains("playerSupplier: Supplier<ExoPlayer?>?"));
        assertTrue(owner.contains("mirroredQueueSupplier: BooleanSupplier?"));
        assertTrue(owner.contains("playbackQueueManagerSupplier: Supplier<PlaybackQueueManager?>?"));
        assertFalse(owner.contains("playbackQueueManager: PlaybackQueueManager?"));
        assertFalse(owner.contains("currentTrackSupplier: Supplier<Track?>?"));
        assertFalse(owner.contains("queueStateSupplier: Supplier<PlaybackQueueManager.QueueStateSnapshot?>?"));
        assertTrue(owner.contains("override fun player(): ExoPlayer? = playerSupplier?.get()"));
        assertTrue(owner.contains("override fun playerMirrorsQueue(): Boolean = mirroredQueueSupplier?.asBoolean == true"));
        assertTrue(owner.contains("playbackQueueManagerSupplier?.get()?.queueStateSnapshot()?.currentTrack"));
        assertFalse(owner.contains("override fun currentTrack(): Track? = playbackQueueManager?.queueStateSnapshot()?.currentTrack"));
        assertFalse(owner.contains("override fun currentTrack(): Track? = currentTrackSupplier?.get()"));
        assertFalse(owner.contains("queueStateSupplier?.get()?.currentTrack"));
    }

    @Test
    public void playbackServiceShutdownIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/PlaybackShutdownCoordinator.kt");
        String taskScheduler = read("app/src/main/java/app/yukine/playback/PlaybackTaskScheduler.java");
        String errorRecovery = read("app/src/main/java/app/yukine/playback/manager/PlaybackErrorRecoveryManager.kt");
        String progressUpdates = read("app/src/main/java/app/yukine/playback/manager/PlaybackProgressUpdateManager.kt");
        String sleepTimer = read("app/src/main/java/app/yukine/playback/manager/PlaybackSleepTimerManager.kt");
        String precacheManager = read("app/src/main/java/app/yukine/playback/PlaybackPrecacheManager.java");
        String statePublisher = read("app/src/main/java/app/yukine/playback/PlaybackStatePublisher.kt");
        String playbackResourcesOwner = read("app/src/main/java/app/yukine/playback/PlaybackShutdownPlaybackResourcesOwner.java");
        String serviceResourcesOwner = read("app/src/main/java/app/yukine/playback/PlaybackShutdownServiceResourcesOwner.java");
        String lifecycleResourcesOwner = read("app/src/main/java/app/yukine/playback/PlaybackShutdownLifecycleResourcesOwner.java");
        String queuePersistenceOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackQueuePersistenceOwner.java"
        );
        String mainHandlerSchedulerOwner = read("app/src/main/java/app/yukine/playback/PlaybackMainHandlerSchedulerOwner.java");
        String noisyReceiverRegistrarOwner = read("app/src/main/java/app/yukine/playback/PlaybackNoisyReceiverRegistrarOwner.java");
        String noisyReceiverManager = read("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackNoisyReceiverManager.kt");
        String warmupCoordinator = read("app/src/main/java/app/yukine/playback/PlaybackWarmupCoordinator.kt");
        String visualizationAnalyzer = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationAnalyzer.kt");
        String visualizationCacheManager = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationCacheManager.java");
        String notificationArtworkManager = read("app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkManager.java");
        String lyricsManager = read("app/src/main/java/app/yukine/playback/manager/PlaybackLyricsManager.kt");
        String crossfadeManager = read("app/src/main/java/app/yukine/playback/manager/PlaybackCrossfadeAdvanceManager.kt");
        String recoveryScheduler = read("app/src/main/java/app/yukine/playback/manager/PlaybackRecoveryScheduler.kt");
        String normalizedStatePublisher = statePublisher.replace("\r\n", "\n");
        String normalizedService = service.replace("\r\n", "\n");
        String normalizedOwner = owner.replace("\r\n", "\n");
        String normalizedTaskScheduler = taskScheduler.replace("\r\n", "\n");
        String normalizedErrorRecovery = errorRecovery.replace("\r\n", "\n");
        String normalizedVisualizationCacheManager = visualizationCacheManager.replace("\r\n", "\n");
        String normalizedProgressUpdates = progressUpdates.replace("\r\n", "\n");
        String normalizedPrecacheManager = precacheManager.replace("\r\n", "\n");
        String normalizedNotificationArtworkManager = notificationArtworkManager.replace("\r\n", "\n");
        String normalizedRecoveryScheduler = recoveryScheduler.replace("\r\n", "\n");
        String normalizedSleepTimer = sleepTimer.replace("\r\n", "\n");
        String normalizedCrossfadeManager = crossfadeManager.replace("\r\n", "\n");
        String normalizedLyricsManager = lyricsManager.replace("\r\n", "\n");

        assertTrue(owner.contains("internal class PlaybackShutdownCoordinator"));
        assertTrue(owner.contains("interface PlaybackResources"));
        assertTrue(owner.contains("interface ServiceResources"));
        assertTrue(playbackResourcesOwner.contains(
                "final class PlaybackShutdownPlaybackResourcesOwner implements PlaybackShutdownCoordinator.PlaybackResources"));
        assertTrue(playbackResourcesOwner.contains("public void releaseLyrics()"));
        assertTrue(playbackResourcesOwner.contains("public void releaseWifiLock()"));
        assertTrue(playbackResourcesOwner.contains("public void releasePlayer()"));
        assertTrue(playbackResourcesOwner.contains("static <T> Runnable releaseFrom(Supplier<T> provider, Consumer<T> releaseAction)"));
        assertTrue(playbackResourcesOwner.contains("releaseAction.accept(resource);"));
        assertTrue(playbackResourcesOwner.contains("resetQueueMirrorState.run();"));
        assertTrue(playbackResourcesOwner.contains("resetRuntimePreparingState.run();"));
        assertTrue(serviceResourcesOwner.contains(
                "final class PlaybackShutdownServiceResourcesOwner implements PlaybackShutdownCoordinator.ServiceResources"));
        assertTrue(serviceResourcesOwner.contains("public void shutdownTaskSchedulers()"));
        assertTrue(serviceResourcesOwner.contains("public void releasePrecache()"));
        assertTrue(lifecycleResourcesOwner.contains(
                "final class PlaybackShutdownLifecycleResourcesOwner implements PlaybackShutdownCoordinator.LifecycleResources"));
        assertTrue(lifecycleResourcesOwner.contains("interface PlaybackQueueLifecycleStore"));
        assertTrue(lifecycleResourcesOwner.contains("public void persistPlaybackQueue()"));
        assertTrue(lifecycleResourcesOwner.contains("public void savePlaybackResumeRequested(boolean requested)"));
        assertTrue(lifecycleResourcesOwner.contains("public boolean hasNotificationWorthyState()"));
        assertTrue(queuePersistenceOwner.contains(
                "final class PlaybackQueuePersistenceOwner"));
        assertTrue(queuePersistenceOwner.contains(
                "implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore"));
        assertFalse(queuePersistenceOwner.contains("interface PlaybackQueueManagerProvider"));
        assertTrue(queuePersistenceOwner.contains("private final PlaybackQueueManager playbackQueueManager;"));
        assertTrue(queuePersistenceOwner.contains("private final PlaybackQueueStore queueStore;"));
        assertFalse(queuePersistenceOwner.contains("Supplier<PlaybackQueueManager> playbackQueueManagerSupplier"));
        assertFalse(queuePersistenceOwner.contains("playbackQueueManagerProvider"));
        assertFalse(queuePersistenceOwner.contains("interface QueuePersistenceOperations"));
        assertFalse(queuePersistenceOwner.contains("QueuePersistenceOperations"));
        assertFalse(queuePersistenceOwner.contains("import java.util.function.Consumer;"));
        assertFalse(queuePersistenceOwner.contains("private final Runnable persistQueueState;"));
        assertFalse(queuePersistenceOwner.contains("private final Consumer<Boolean> savePlaybackResumeRequested;"));
        assertFalse(queuePersistenceOwner.contains("private final Consumer<Boolean> persistCurrentPlaybackPosition;"));
        assertFalse(queuePersistenceOwner.contains("private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;"));
        assertFalse(queuePersistenceOwner.contains("private final Supplier<PlaybackQueueStore> queueStoreSupplier;"));
        assertFalse(queuePersistenceOwner.contains("private PlaybackQueueManager playbackQueueManager()"));
        assertFalse(queuePersistenceOwner.contains("private PlaybackQueueStore queueStore()"));
        assertFalse(queuePersistenceOwner.contains("fromPlaybackQueueManager("));
        assertTrue(queuePersistenceOwner.contains("playbackQueueManager.persistQueueState();"));
        assertFalse(queuePersistenceOwner.contains("playbackQueueManager.savePlaybackResumeRequested(requested);"));
        assertTrue(queuePersistenceOwner.contains("queueStore.saveResumeRequested(requested);"));
        assertFalse(queuePersistenceOwner.contains("playbackQueueManager.persistCurrentPlaybackPosition(force);"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackShutdownPlaybackStateOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackShutdownPlaybackStateOwnerTest.java")));
        assertTrue(lifecycleResourcesOwner.contains("static PlaybackStateProvider playbackStateProviderFromPlaybackState("));
        assertTrue(lifecycleResourcesOwner.contains("BooleanSupplier playbackStateProvider"));
        assertTrue(lifecycleResourcesOwner.contains("BooleanSupplier preparingStateProvider"));
        assertTrue(lifecycleResourcesOwner.contains("return playbackStateProvider != null && playbackStateProvider.getAsBoolean();"));
        assertTrue(lifecycleResourcesOwner.contains("return preparingStateProvider != null && preparingStateProvider.getAsBoolean();"));
        assertTrue(mainHandlerSchedulerOwner.contains("final class PlaybackMainHandlerSchedulerOwner implements"));
        assertTrue(mainHandlerSchedulerOwner.contains("PlaybackSleepTimerManager.CallbackScheduler"));
        assertTrue(mainHandlerSchedulerOwner.contains("PlaybackErrorRecoveryManager.RetryScheduler"));
        assertTrue(mainHandlerSchedulerOwner.contains("PlaybackProgressUpdateManager.CallbackScheduler"));
        assertTrue(mainHandlerSchedulerOwner.contains("PlaybackCrossfadeAdvanceManager.CallbackScheduler"));
        assertTrue(mainHandlerSchedulerOwner.contains("PlaybackRecoveryScheduler.MainScheduler"));
        assertTrue(mainHandlerSchedulerOwner.contains("PlaybackPrecacheManager.CallbackScheduler"));
        assertTrue(mainHandlerSchedulerOwner.contains("void clearCallbacks()"));
        assertTrue(noisyReceiverRegistrarOwner.contains(
                "final class PlaybackNoisyReceiverRegistrarOwner implements PlaybackNoisyReceiverManager.Registrar"));
        assertTrue(noisyReceiverRegistrarOwner.contains("interface ReceiverRegistry"));
        assertTrue(noisyReceiverRegistrarOwner.contains("if (sdkInt >= 33)"));
        assertTrue(noisyReceiverRegistrarOwner.contains("Context.RECEIVER_NOT_EXPORTED"));
        assertTrue(noisyReceiverRegistrarOwner.contains("receiverRegistry.unregisterReceiver(receiver);"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackNoisyReceiverActionsOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackNoisyReceiverActionsOwnerTest.java")));
        assertTrue(noisyReceiverManager.contains("fun actionsFromPlaybackState("));
        assertTrue(noisyReceiverManager.contains("playbackStateProvider: BooleanSupplier?"));
        assertTrue(noisyReceiverManager.contains("pauseAction: Runnable?"));
        assertTrue(noisyReceiverManager.contains("pauseAction?.run()"));
        assertTrue(owner.contains("fun releaseLyrics()"));
        assertTrue(owner.contains("fun shutdownTaskSchedulers()"));
        assertTrue(owner.contains("fun releaseWarmup()"));
        assertTrue(owner.contains("fun releaseVisualizationAnalyzer()"));
        assertTrue(owner.contains("fun releaseRecoveryScheduler()"));
        assertTrue(owner.contains("fun releaseErrorRecovery()"));
        assertTrue(owner.contains("fun releaseProgressUpdates()"));
        assertTrue(owner.contains("fun releaseSleepTimer()"));
        assertTrue(owner.contains("fun releaseCrossfade()"));
        assertTrue(owner.contains("fun clearMainCallbacks()"));
        assertTrue(owner.contains("fun releaseVisualizationCache()"));
        assertTrue(owner.contains("fun releasePrecache()"));
        assertTrue(owner.contains("fun releaseStatePublisher()"));
        assertTrue(owner.contains("private var lyricsReleased = false"));
        assertTrue(owner.contains("private var transportResourcesReleased = false"));
        assertTrue(owner.contains("private var serviceResourcesReleased = false"));
        assertTrue(owner.contains("private fun releaseServiceResources()"));
        assertFalse(normalizedOwner.contains("\n    fun releaseServiceResources()"));
        assertTrue(normalizedOwner.contains("fun handleServiceDestroyed() {\n        if (serviceResourcesReleased) {\n            return\n        }\n        serviceResourcesReleased = true\n        lifecycleResources.persistPlaybackPosition()\n        releaseServiceResources()\n    }"));
        assertTrue(normalizedOwner.contains("private fun releaseServiceResources() {\n        releaseLyricsOnce()\n        serviceResources.unregisterNoisyReceiver()\n        serviceResources.releaseWarmup()\n        serviceResources.releaseVisualizationAnalyzer()\n        serviceResources.releaseRecoveryScheduler()\n        serviceResources.shutdownTaskSchedulers()\n        serviceResources.releasePrecache()\n        serviceResources.releaseErrorRecovery()\n        serviceResources.releaseProgressUpdates()\n        serviceResources.releaseSleepTimer()\n        serviceResources.releaseCrossfade()\n        serviceResources.clearMainCallbacks()"));
        assertTrue(normalizedOwner.contains("serviceResources.releaseWarmup()\n        serviceResources.releaseVisualizationAnalyzer()\n        serviceResources.releaseRecoveryScheduler()\n        serviceResources.shutdownTaskSchedulers()"));
        assertTrue(normalizedOwner.contains("serviceResources.shutdownTaskSchedulers()\n        serviceResources.releasePrecache()\n        serviceResources.releaseErrorRecovery()"));
        assertTrue(normalizedOwner.contains("serviceResources.releaseErrorRecovery()\n        serviceResources.releaseProgressUpdates()\n        serviceResources.releaseSleepTimer()\n        serviceResources.releaseCrossfade()\n        serviceResources.clearMainCallbacks()"));
        assertTrue(normalizedOwner.contains("serviceResources.releaseVisualizationCache()\n        serviceResources.releaseNotificationArtwork()"));
        assertTrue(normalizedOwner.contains("serviceResources.releaseNotificationArtwork()\n        serviceResources.releaseStatePublisher()\n        releaseTransportResourcesOnce()"));
        assertTrue(service.contains("new PlaybackShutdownCoordinator("));
        assertFalse(service.contains("new PlaybackShutdownCoordinator.PlaybackResources()"));
        assertFalse(service.contains("new PlaybackShutdownCoordinator.ServiceResources()"));
        assertFalse(service.contains("new PlaybackShutdownCoordinator.LifecycleResources()"));
        assertTrue(service.contains("private PlaybackShutdownPlaybackResourcesOwner playbackShutdownPlaybackResourcesOwner;"));
        assertTrue(service.contains("playbackShutdownPlaybackResourcesOwner = new PlaybackShutdownPlaybackResourcesOwner("));
        String shutdownPlaybackResourcesConstructor = service.substring(
                service.indexOf("playbackShutdownPlaybackResourcesOwner = new PlaybackShutdownPlaybackResourcesOwner("),
                service.indexOf("playbackShutdownCoordinator = new PlaybackShutdownCoordinator(")
        );
        assertFalse(service.contains("() -> playbackQueueMirrorStateOwner.setPlayerMirrorsQueue(false)"));
        assertTrue(service.contains("() -> playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false)"));
        assertTrue(service.contains("() -> playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false)"));
        assertFalse(service.contains("() -> playbackRuntimeStateManager.setPreparing(false)"));
        assertTrue(service.contains("private void releasePlaybackPlayerResources()"));
        assertTrue(service.contains("playbackShutdownPlaybackResourcesOwner.releasePlayer();"));
        String releasePlayerMethod = service.substring(
                service.indexOf("private void releasePlayer()"),
                service.indexOf("private void releasePlaybackPlayerResources()")
        );
        assertFalse(releasePlayerMethod.contains("playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false)"));
        assertFalse(releasePlayerMethod.contains("playbackRuntimeStateManager.setPreparing(false)"));
        assertFalse(releasePlayerMethod.contains("playbackPrecacheManager.releaseAudioCache();"));
        assertFalse(releasePlayerMethod.contains("PlaybackPrecacheManager.audioCacheReleaseActionFromPrecacheManagerSupplier("));
        assertTrue(releasePlayerMethod.contains("PlaybackShutdownPlaybackResourcesOwner.releaseFrom("));
        assertTrue(releasePlayerMethod.contains("PlaybackPrecacheManager::releaseAudioCache"));
        assertTrue(service.contains("private PlaybackShutdownServiceResourcesOwner playbackShutdownServiceResourcesOwner;"));
        assertTrue(service.contains("playbackShutdownServiceResourcesOwner = new PlaybackShutdownServiceResourcesOwner("));
        assertTrue(service.contains("private PlaybackShutdownLifecycleResourcesOwner playbackShutdownLifecycleResourcesOwner;"));
        assertTrue(service.contains("playbackShutdownLifecycleResourcesOwner = new PlaybackShutdownLifecycleResourcesOwner("));
        assertFalse(service.contains("new PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore()"));
        assertFalse(service.contains("new PlaybackShutdownLifecycleResourcesOwner.PlaybackStateProvider()"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueuePositionPersistenceOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackShutdownQueueLifecycleStoreOwner.java")));
        assertFalse(service.contains("PlaybackQueuePositionPersistenceOwner"));
        assertFalse(service.contains("playbackQueuePositionPersistenceOwner"));
        assertFalse(service.contains("PlaybackShutdownQueueLifecycleStoreOwner"));
        assertFalse(service.contains("playbackShutdownQueueLifecycleStoreOwner"));
        assertTrue(service.contains("new PlaybackQueuePersistenceOwner("));
        assertFalse(service.contains("PlaybackQueuePersistenceOwner.fromPlaybackQueueManager("));
        assertFalse(service.contains("new PlaybackShutdownPlaybackStateOwner("));
        assertTrue(service.contains("PlaybackShutdownLifecycleResourcesOwner.playbackStateProviderFromPlaybackState("));
        assertTrue(service.contains("                        playbackPlayerStateOwner::isPlaying,"));
        assertTrue(service.contains("                        playbackCurrentTrackPreparationRuntimeOwner::preparing"));
        assertFalse(service.contains("                        playbackRuntimeStateManager::preparing"));
        assertTrue(service.contains("private PlaybackMainHandlerSchedulerOwner playbackMainHandlerSchedulerOwner;"));
        assertTrue(service.contains("playbackMainHandlerSchedulerOwner = new PlaybackMainHandlerSchedulerOwner(mainHandler);"));
        assertTrue(service.contains("new PlaybackNoisyReceiverRegistrarOwner(EchoPlaybackService.this)"));
        assertFalse(service.contains("new PlaybackNoisyReceiverManager.Registrar()"));
        assertFalse(service.contains("new PlaybackNoisyReceiverManager.Actions()"));
        assertFalse(service.contains("new PlaybackNoisyReceiverActionsOwner("));
        assertTrue(service.contains("PlaybackNoisyReceiverManager.actionsFromPlaybackState("));
        assertTrue(service.contains("                        playbackPlayerStateOwner::isPlaying,"));
        assertTrue(service.contains("                        EchoPlaybackService.this::pause"));
        assertTrue(service.contains("                playbackShutdownPlaybackResourcesOwner,"));
        assertTrue(service.contains("                playbackShutdownServiceResourcesOwner,"));
        assertTrue(service.contains("                playbackShutdownLifecycleResourcesOwner"));
        assertTrue(shutdownPlaybackResourcesConstructor.contains("PlaybackShutdownPlaybackResourcesOwner.releaseFrom("));
        assertTrue(shutdownPlaybackResourcesConstructor.contains("app.yukine.playback.manager.LyricsPublisher::release"));
        assertTrue(shutdownPlaybackResourcesConstructor.contains("PlaybackWifiLockManager::release"));
        assertFalse(shutdownPlaybackResourcesConstructor.contains("playbackLyricsManager.release();"));
        assertFalse(shutdownPlaybackResourcesConstructor.contains("playbackWifiLockManager.release();"));
        assertTrue(service.contains("PlaybackShutdownServiceResourcesOwner.releaseFrom("));
        assertTrue(service.contains("PlaybackWarmupCoordinator::release"));
        assertTrue(service.contains("PlaybackVisualizationAnalyzer::release"));
        assertTrue(service.contains("PlaybackRecoveryScheduler::release"));
        assertFalse(service.contains("playbackWarmupCoordinator.release();"));
        assertFalse(service.contains("playbackVisualizationAnalyzer.release();"));
        assertFalse(service.contains("playbackRecoveryScheduler.release();"));
        assertFalse(service.contains("playbackTaskScheduler.shutdownNow();"));
        assertFalse(service.contains("visualizationTaskScheduler.shutdownNow();"));
        assertTrue(service.contains("PlaybackShutdownServiceResourcesOwner.shutdownPlaybackTaskSchedulers("));
        assertTrue(serviceResourcesOwner.contains("static Runnable shutdownPlaybackTaskSchedulers(PlaybackTaskScheduler... schedulers)"));
        assertTrue(serviceResourcesOwner.contains("static <T> Runnable releaseFrom(Supplier<T> provider, Consumer<T> releaseAction)"));
        assertTrue(serviceResourcesOwner.contains("releaseAction.accept(resource);"));
        assertTrue(serviceResourcesOwner.contains("scheduler.shutdownNow();"));
        assertTrue(service.contains("PlaybackErrorRecoveryManager::release"));
        assertTrue(service.contains("PlaybackProgressUpdateManager::release"));
        assertTrue(service.contains("PlaybackSleepTimerManager::release"));
        assertTrue(service.contains("PlaybackCrossfadeAdvanceManager::release"));
        assertTrue(service.contains("PlaybackMainHandlerSchedulerOwner::clearCallbacks"));
        assertFalse(service.contains("playbackErrorRecoveryManager.release();"));
        assertFalse(service.contains("playbackProgressUpdateManager.release();"));
        assertFalse(service.contains("playbackSleepTimerManager.release();"));
        assertFalse(service.contains("playbackCrossfadeAdvanceManager.release();"));
        assertFalse(service.contains("playbackMainHandlerSchedulerOwner.clearCallbacks();"));
        assertFalse(service.contains("new PlaybackSleepTimerManager.CallbackScheduler()"));
        assertFalse(service.contains("new PlaybackErrorRecoveryManager.RetryScheduler()"));
        assertFalse(service.contains("new PlaybackProgressUpdateManager.CallbackScheduler()"));
        assertFalse(service.contains("new PlaybackCrossfadeAdvanceManager.CallbackScheduler()"));
        assertFalse(service.contains("new PlaybackRecoveryScheduler.MainScheduler()"));
        assertFalse(service.contains("new PlaybackPrecacheManager.CallbackScheduler()"));
        assertTrue(service.contains("PlaybackVisualizationCacheManager::release"));
        assertTrue(service.contains("PlaybackStatePublisher::release"));
        assertFalse(service.contains("playbackVisualizationCacheManager.release();"));
        assertFalse(service.contains("playbackStatePublisher.release();"));
        assertTrue(serviceResourcesOwner.contains("public void clearMainCallbacks()"));
        assertTrue(errorRecovery.contains("fun removeCallbacks(runnable: Runnable)"));
        assertTrue(errorRecovery.contains("fun release()"));
        assertTrue(errorRecovery.contains("private var released = false"));
        assertTrue(errorRecovery.contains("if (released)"));
        assertTrue(normalizedErrorRecovery.contains("fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        cancelPendingRetry()\n    }"));
        assertTrue(errorRecovery.contains("private fun cancelPendingRetry()"));
        assertTrue(errorRecovery.contains("pendingRetry?.let { scheduler.removeCallbacks(it) }"));
        assertTrue(progressUpdates.contains("fun stop()"));
        assertTrue(progressUpdates.contains("fun release()"));
        assertTrue(progressUpdates.contains("private var released = false"));
        assertTrue(progressUpdates.contains("if (released)"));
        assertTrue(normalizedProgressUpdates.contains("fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        stop()\n    }"));
        assertTrue(progressUpdates.contains("scheduler.removeCallbacks(progressRunnable)"));
        assertTrue(sleepTimer.contains("fun cancel(publish: Boolean)"));
        assertTrue(sleepTimer.contains("fun release()"));
        assertTrue(sleepTimer.contains("private var released = false"));
        assertTrue(sleepTimer.contains("if (released)"));
        assertTrue(normalizedSleepTimer.contains("fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        cancel(publish = false)\n    }"));
        assertTrue(sleepTimer.contains("scheduler.removeCallbacks(timerRunnable)"));
        assertTrue(precacheManager.contains("private volatile boolean released;"));
        assertTrue(normalizedPrecacheManager.contains("if (released) {\n            return;\n        }"));
        assertTrue(precacheManager.contains("released = true;"));
        assertTrue(precacheManager.contains("if (released || cacheKey == null)"));
        assertTrue(precacheManager.contains("if (released || task == null || playbackCacheExecutor.isShutdown())"));
        assertTrue(precacheManager.contains("if (released || playbackCacheExecutor.isShutdown())"));
        assertTrue(precacheManager.contains("&& !released"));
        assertTrue(statePublisher.contains("fun release()"));
        assertTrue(normalizedStatePublisher.contains("fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        listeners.clear()\n    }"));
        assertTrue(statePublisher.contains("listeners.clear()"));
        assertTrue(statePublisher.contains("if (released)"));
        assertTrue(warmupCoordinator.contains("fun release()"));
        assertTrue(warmupCoordinator.contains("if (released || track == null)"));
        assertTrue(visualizationAnalyzer.contains("fun release()"));
        assertTrue(visualizationAnalyzer.contains("private var released = false"));
        assertTrue(visualizationAnalyzer.contains("if (released || track == null"));
        assertTrue(visualizationAnalyzer.contains("return@Runnable"));
        assertTrue(visualizationCacheManager.contains("private volatile boolean released;"));
        assertTrue(visualizationCacheManager.contains("released = true;"));
        assertTrue(normalizedVisualizationCacheManager.contains("void release() {\n        if (released) {\n            return;\n        }\n        released = true;\n        cacheGeneration.incrementAndGet();"));
        assertTrue(visualizationCacheManager.contains("if (released)"));
        assertTrue(visualizationCacheManager.contains("if (cacheKey == null)"));
        assertTrue(visualizationCacheManager.contains("return !released && cacheGeneration.get() == generation;"));
        assertTrue(notificationArtworkManager.contains("private volatile boolean released;"));
        assertTrue(notificationArtworkManager.contains("released = true;"));
        assertTrue(normalizedNotificationArtworkManager.contains("void release() {\n        if (released) {\n            return;\n        }\n        released = true;\n        artworkGeneration.incrementAndGet();"));
        assertTrue(notificationArtworkManager.contains("if (released || track == null || track.albumArtUri == null)"));
        assertTrue(notificationArtworkManager.contains("return !released && artworkGeneration.get() == generation;"));
        assertTrue(lyricsManager.contains("private var released = false"));
        assertTrue(lyricsManager.contains("if (released)"));
        assertTrue(normalizedLyricsManager.contains("override fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        FloatingLyricsPublisher.removeListener(floatingLyricsListener)\n        LiveLyricsNotificationService.stop(context)\n    }"));
        assertTrue(lyricsManager.contains("LiveLyricsNotificationService.stop(context)"));
        assertTrue(crossfadeManager.contains("private var released = false"));
        assertTrue(crossfadeManager.contains("fun release()"));
        assertTrue(normalizedCrossfadeManager.contains("fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        cancel()\n    }"));
        assertTrue(crossfadeManager.contains("scheduler.removeCallbacks(it)"));
        assertTrue(crossfadeManager.contains("activeFadeRunnable !== this"));
        assertTrue(recoveryScheduler.contains("internal class PlaybackRecoveryScheduler"));
        assertTrue(recoveryScheduler.contains("fun scheduleCurrentPlaybackRecovery(playWhenReady: Boolean)"));
        assertTrue(recoveryScheduler.contains("private var released = false"));
        assertTrue(normalizedRecoveryScheduler.contains("fun release() {\n        if (released) {\n            return\n        }\n        released = true\n        cancel()\n    }"));
        assertTrue(recoveryScheduler.contains("mainScheduler.removeCallbacks(it)"));
        assertTrue(taskScheduler.contains("queue.clear();"));
        assertTrue(taskScheduler.contains("worker.interrupt();"));
        assertTrue(taskScheduler.contains("private final Runnable beforeTaskRun;"));
        assertTrue(taskScheduler.contains("beforeTaskRun.run();"));
        assertTrue(normalizedTaskScheduler.contains("if (!running) {\n                    return;\n                }"));
        assertTrue(taskScheduler.contains("catch (RuntimeException ignored)"));
        assertTrue(taskScheduler.contains("Keep later playback tasks alive"));
        assertTrue(normalizedService.contains("public void onDestroy() {\n        if (playbackShutdownCoordinator != null) {\n            playbackShutdownCoordinator.handleServiceDestroyed();"));
        assertTrue(normalizedService.contains("        super.onDestroy();\n    }"));
        assertFalse(normalizedService.contains("public void onDestroy() {\n        persistPlaybackPositionThrottled(true);\n        mainHandler.removeCallbacksAndMessages(null);"));
        assertFalse(normalizedService.contains("public void onDestroy() {\n        if (playbackPrecacheManager != null)"));
        assertFalse(normalizedService.contains("public void onDestroy() {\n        playbackTaskScheduler.shutdownNow();"));
        assertFalse(normalizedService.contains("public void onDestroy() {\n        if (playbackLyricsManager != null)"));
        assertFalse(normalizedService.contains("public void onDestroy() {\n        if (playbackStatePublisher != null)"));
        assertFalse(service.contains("private void releaseServiceResources()"));
        assertFalse(service.contains("private void shutdownTaskSchedulers()"));
        assertFalse(service.contains("private void releaseAsyncPlaybackOwners()"));
    }

    @Test
    public void playbackTransitionStateIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackTransitionStateManager.kt");
        String crossfadeOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackCrossfadeAdvanceManager.kt");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackCrossfadeCommandOwner.java");

        assertFalse(service.contains("private Track lastMarkedTrack"));
        assertFalse(service.contains("private boolean fadeOutAdvancing"));
        assertFalse(service.contains("CROSSFADE_FADE_OUT_MS"));
        assertFalse(service.contains("CROSSFADE_FADE_STEP_MS"));
        assertFalse(service.contains("mainHandler.post(new Runnable()"));
        assertFalse(service.contains("lastMarkedTrack = null"));
        assertFalse(service.contains("fadeOutAdvancing = false"));
        assertFalse(service.contains("fadeOutAdvancing = true"));
        assertFalse(service.contains("playbackTransitionStateManager.setLastMarkedTrack(track)"));
        assertFalse(service.contains("playbackTransitionStateManager.setFadeOutAdvancing(true)"));
        assertFalse(service.contains("playbackTransitionStateManager.setFadeOutAdvancing(false)"));
        assertFalse(service.contains("playbackTransitionStateManager.setFadeOutAdvancing(enabled)"));
        assertFalse(service.contains("playbackCrossfadeAdvanceManager.startFadeOutThenNext()"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner.startFadeOutThenNext()"));
        assertFalse(service.contains("playbackCrossfadeAdvanceManager.cancel();"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner.cancelCrossfadeAdvance();"));
        assertFalse(service.contains("playbackTransitionStateManager.clear()"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner.bindPlaybackCrossfadeAdvanceManager(playbackCrossfadeAdvanceManager);"));
        assertTrue(commandOwner.contains("transitionState.accept(enabled);"));
        assertTrue(owner.contains("private var lastMarkedTrack"));
        assertTrue(owner.contains("private var fadeOutAdvancing"));
        assertTrue(owner.contains("fun setLastMarkedTrack(track: Track?)"));
        assertTrue(owner.contains("fun lastMarkedTrack(): Track?"));
        assertTrue(owner.contains("fun setFadeOutAdvancing(enabled: Boolean)"));
        assertTrue(owner.contains("fun fadeOutAdvancing(): Boolean"));
        assertTrue(owner.contains("fun clear()"));
        assertTrue(crossfadeOwner.contains("internal class PlaybackCrossfadeAdvanceManager"));
        assertTrue(crossfadeOwner.contains("fun startFadeOutThenNext(): Boolean"));
        assertTrue(crossfadeOwner.contains("actions.setFadeOutAdvancing(true)"));
        assertTrue(crossfadeOwner.contains("actions.setFadeOutAdvancing(false)"));
        assertTrue(crossfadeOwner.contains("fun cancel()"));
        assertTrue(crossfadeOwner.contains("fun release()"));
    }

    @Test
    public void playbackQueueRuntimeStateIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueRuntimeStateManager.kt");
        String queueOwner = read("app/src/main/java/app/yukine/playback/manager/PlaybackQueueManager.kt");
        String queueCompletionOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueCompletionOwner.java");
        String queueStateOwner = read("app/src/main/java/app/yukine/playback/PlaybackQueueStateOwner.java");

        assertFalse(service.contains("private boolean playerMirrorsQueue"));
        assertFalse(service.contains("private int currentIndex ="));
        assertFalse(service.contains("playerMirrorsQueue = true"));
        assertFalse(service.contains("playerMirrorsQueue = false"));
        assertFalse(service.contains("currentIndex = "));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackQueueMirrorStateOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackQueueMirrorStateOwnerTest.java")));
        assertFalse(service.contains("private final PlaybackQueueMirrorStateOwner playbackQueueMirrorStateOwner"));
        assertFalse(service.contains("PlaybackQueueMirrorStateOwner.fromRuntimeStateManager("));
        assertTrue(service.contains("private final PlaybackQueueRuntimeStateManager playbackQueueRuntimeStateManager"));
        assertTrue(service.contains("new PlaybackQueueRuntimeStateManager()"));
        assertFalse(service.contains("playbackQueueMirrorStateOwner::playerMirrorsQueue"));
        assertFalse(service.contains("playbackQueueMirrorStateOwner::setPlayerMirrorsQueue"));
        assertFalse(service.contains("playbackQueueMirrorStateOwner.setPlayerMirrorsQueue(true)"));
        assertFalse(service.contains("playbackQueueMirrorStateOwner.setPlayerMirrorsQueue(false)"));
        assertTrue(service.contains("playbackQueueRuntimeStateManager::playerMirrorsQueue"));
        assertTrue(service.contains("playbackQueueRuntimeStateManager::setPlayerMirrorsQueue"));
        assertTrue(service.contains("playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(true)"));
        assertTrue(service.contains("playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false)"));
        assertFalse(service.contains("playbackQueueRuntimeStateManager.currentIndex()"));
        assertFalse(service.contains("playbackQueueRuntimeStateManager.setCurrentIndex(index)"));
        assertFalse(service.contains("playbackQueueRuntimeStateManager.setClampedCurrentIndex(index, queue.size())"));
        assertFalse(service.contains("return playbackQueueManager == null ? -1 : playbackQueueManager.currentIndex();"));
        assertFalse(service.contains("private int currentIndex()"));
        assertFalse(service.contains("playbackQueueManager.setCurrentIndex(index);"));
        assertFalse(service.contains("private void setCurrentIndex(int index)"));
        assertFalse(service.contains("private void setClampedCurrentIndex(int index)"));
        assertFalse(service.contains("playbackQueueManager.setClampedCurrentIndex(index);"));
        assertFalse(service.contains("playbackQueueStateOwner::upcomingTracksForPrecache"));
        assertFalse(service.contains("playbackQueueManager::upcomingTracksForPrecache"));
        assertTrue(service.contains(
                "playbackQueueCompletionOwner().stopAfterAutomaticAdvance(transition.completedIndex());"));
        assertFalse(service.contains("playbackQueueCompletionOwner().prepareStopAfterAutomaticAdvance(completedIndex);"));
        assertFalse(service.contains("playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);"));
        assertTrue(queueCompletionOwner.contains("void stopAfterAutomaticAdvance(int completedIndex)"));
        assertTrue(queueCompletionOwner.contains("playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);"));
        assertFalse(service.contains("saveTrackPlaybackPosition(completed, 0L);"));
        assertFalse(service.contains("private void saveTrackPlaybackPosition(Track track, long positionMs)"));
        assertFalse(service.contains("playbackQueueManager.setClampedCurrentIndex(index, queueSize());"));
        assertFalse(service.contains("playbackQueueManager.clampCurrentIndex(queueSize())"));
        assertTrue(owner.contains("private var playerMirrorsQueue"));
        assertTrue(owner.contains("fun playerMirrorsQueue(): Boolean"));
        assertTrue(owner.contains("fun setPlayerMirrorsQueue(enabled: Boolean)"));
        assertFalse(owner.contains("private var currentIndex"));
        assertFalse(owner.contains("fun currentIndex(): Int"));
        assertFalse(owner.contains("fun setCurrentIndex(index: Int)"));
        assertFalse(owner.contains("fun clampCurrentIndex(queueSize: Int): Int"));
        assertFalse(owner.contains("fun setClampedCurrentIndex(index: Int, queueSize: Int)"));
        assertTrue(queueStateOwner.contains("upcomingTracksForPrecache(int maxCount)"));
        assertTrue(queueStateOwner.contains(
                "playbackQueueManager.upcomingTracksForPrecache(maxCount);"));
        assertFalse(queueStateOwner.contains("default List<Track> upcomingTracksForPrecache(int maxCount)"));
        assertTrue(queueOwner.contains("private var currentIndex = -1"));
        assertTrue(queueOwner.contains("private fun currentIndex(): Int"));
        assertFalse(queueOwner.contains("\n    fun currentIndex(): Int"));
        assertTrue(queueOwner.contains("private fun setCurrentIndex(index: Int)"));
        assertFalse(queueOwner.contains("\n    fun setCurrentIndex(index: Int)"));
        assertFalse(queueOwner.contains("private fun clampCurrentIndex(): Int"));
        assertFalse(queueOwner.contains("\n    fun clampCurrentIndex(): Int"));
        assertFalse(queueOwner.contains("private fun safeCurrentIndex(): Int"));
        assertTrue(queueOwner.contains("startIndex = currentIndex()"));
        assertFalse(queueOwner.contains("\n    fun safeCurrentIndex(): Int"));
        assertFalse(queueOwner.contains("private fun setClampedCurrentIndex(index: Int)"));
        assertFalse(queueOwner.contains("\n    fun setClampedCurrentIndex(index: Int)"));
        assertTrue(queueOwner.contains("private fun currentTrack(): Track?"));
        assertFalse(queueOwner.contains("\n    fun currentTrack(): Track?"));
        assertFalse(service.contains("PlaybackCurrentTrackOwner.fromPlaybackQueueManagerProvider(() -> playbackQueueManager)"));
        assertFalse(service.contains("private final PlaybackCurrentTrackOwner playbackCurrentTrackOwner"));
        assertFalse(service.contains("private Track currentTrack()"));
        assertEquals(0, countOccurrences(service, "playbackQueueStateOwner::currentTrack"));
        assertFalse(service.contains("final Supplier<Track> currentTrackSupplier = playbackQueueStateOwner::currentTrack;"));
        assertFalse(service.contains("Track track = playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack();"));
        assertEquals(0, countOccurrences(service, "playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertEquals(0, countOccurrences(service, "playbackQueueStateOwner.currentTrack()"));
        assertFalse(service.contains("private void prepareCurrent(final boolean playWhenReady)"));
        assertTrue(service.contains("private void prepareCurrent(Track track, final boolean playWhenReady)"));
        assertFalse(service.contains("prepareCurrent(track, true);"));
        assertTrue(service.contains("playbackQueueCommandOwner.prepareCurrentOrRunFallback("));
        assertFalse(service.contains("playbackQueueCommandOwner.prepareCurrentIfAvailable(true)"));
        assertTrue(service.contains("playbackQueueCommandOwner.runIfCurrentTrackMissing("));
        assertTrue(service.contains("playbackQueueCommandOwner.prepareCurrent(true);"));
        assertFalse(service.contains("playbackQueueCommandOwner.hasCurrentTrack()"));
        assertFalse(service.contains("playbackQueueManager.currentTrack()"));
        assertTrue(queueStateOwner.contains("return queueStateSnapshot().getCurrentTrack();"));
        assertFalse(queueStateOwner.contains("playbackQueueManager::currentTrack"));
        assertFalse(queueOwner.contains("fun clampCurrentIndex(queueSize: Int): Int"));
        assertFalse(queueOwner.contains("fun setClampedCurrentIndex(index: Int, queueSize: Int)"));
    }

    @Test
    public void playbackSessionLifecycleIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedService = service.replace("\r\n", "\n");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackSessionManager.kt");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackSessionCommandOwner.java");
        String controllerMediaItemsOwner = read("app/src/main/java/app/yukine/playback/PlaybackControllerMediaItemsOwner.java");
        String notificationArtworkBridgeOwner = read(
                "app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkBridgeOwner.java"
        );

        assertFalse(service.contains("private Player sessionPlayer"));
        assertFalse(service.contains("private MediaLibrarySession mediaSession"));
        assertFalse(service.contains("releaseMediaSession()"));
        assertFalse(service.contains("bindMediaSessionPlayer()"));
        assertFalse(service.contains("new PlaybackSessionPlayer.Delegate()"));
        assertFalse(service.contains("private boolean setControllerMediaItems("));
        assertFalse(service.contains("PlaybackMediaLibraryCallback.ControllerQueue controllerQueue"));
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackSessionGateway.kt"));
        assertFalse(service.contains("PlaybackSessionGateway"));
        assertFalse(service.contains("playbackSessionGateway"));
        assertFalse(service.contains("private PlaybackControllerMediaItemsOwner playbackControllerMediaItemsOwner;"));
        assertTrue(service.contains("final PlaybackControllerMediaItemsOwner playbackControllerMediaItemsOwner ="));
        assertTrue(service.contains("new PlaybackControllerMediaItemsOwner("));
        assertTrue(service.contains("                        playbackQueueMutationOwner()"));
        assertTrue(service.contains("                playbackControllerMediaItemsOwner,"));
        assertFalse(service.contains("private PlaybackSessionCommandOwner playbackSessionCommandOwner;"));
        assertTrue(service.contains("final PlaybackSessionCommandOwner playbackSessionCommandOwner = new PlaybackSessionCommandOwner("));
        assertTrue(service.contains("return new PlaybackSessionPlayer(player, playbackSessionCommandOwner);"));
        assertTrue(normalizedService.contains(
                "                playbackQueueStateOwner,\n" +
                        "                playbackNotificationManager::mediaMetadataForTrack"));
        assertFalse(service.contains(
                "                    playbackQueueStateOwner::currentTrack,\n" +
                        "                    playbackNotificationManager::mediaMetadataForTrack"));
        assertTrue(service.contains("playbackSessionManager.bind()"));
        assertTrue(service.contains("playbackSessionManager.release()"));
        assertFalse(service.contains("playbackSessionManager.refreshPlayer()"));
        assertTrue(service.contains("playbackSessionManager.session()"));
        assertTrue(owner.contains("class PlaybackSessionManager"));
        assertTrue(owner.contains("fun bind()"));
        assertTrue(owner.contains("fun refreshPlayer()"));
        assertTrue(owner.contains("fun release()"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackSessionRefreshOwner.java")));
        assertFalse(service.contains("PlaybackSessionRefreshOwner"));
        assertFalse(service.contains("playbackSessionRefreshOwner"));
        assertFalse(service.contains("private PlaybackNotificationArtworkBridgeOwner.SessionRefresher playbackSessionRefresher;"));
        assertTrue(service.contains(
                "final PlaybackNotificationArtworkBridgeOwner.SessionRefresher playbackSessionRefresher ="));
        assertTrue(notificationArtworkBridgeOwner.contains(
                "static SessionRefresher sessionRefresherFromPlaybackSessionManager("));
        assertTrue(notificationArtworkBridgeOwner.contains("Supplier<PlaybackSessionManager> playbackSessionManagerSupplier"));
        assertFalse(notificationArtworkBridgeOwner.contains("interface PlaybackSessionManagerProvider"));
        assertFalse(notificationArtworkBridgeOwner.contains("interface SessionOperationsProvider"));
        assertFalse(notificationArtworkBridgeOwner.contains("PlaybackSessionManager playbackSessionManager();"));
        assertTrue(notificationArtworkBridgeOwner.contains("manager::refreshPlayer"));
        assertTrue(notificationArtworkBridgeOwner.contains("sessionOperations.refreshPlayer();"));
        assertTrue(commandOwner.contains("final class PlaybackSessionCommandOwner implements PlaybackSessionPlayer.Delegate"));
        assertFalse(commandOwner.contains("interface SeekController"));
        assertFalse(commandOwner.contains("interface RepeatModeController"));
        assertFalse(commandOwner.contains("interface MetadataProvider"));
        assertTrue(commandOwner.contains("interface ControllerMediaItems"));
        assertTrue(commandOwner.contains("import java.util.function.LongConsumer;"));
        assertTrue(commandOwner.contains("import java.util.function.IntConsumer;"));
        assertTrue(commandOwner.contains("import java.util.function.Function;"));
        assertFalse(commandOwner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertTrue(commandOwner.contains("private final LongConsumer seekController;"));
        assertTrue(commandOwner.contains("private final IntConsumer repeatModeController;"));
        assertTrue(commandOwner.contains("private final Function<Track, MediaMetadata> metadataProvider;"));
        assertFalse(commandOwner.contains("interface StateProvider"));
        assertFalse(commandOwner.contains("stateProvider.currentTrack()"));
        assertFalse(commandOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(commandOwner.contains("return queueStateSnapshot().getCurrentTrack();"));
        assertFalse(commandOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.queueStateSnapshot();"));
        assertFalse(commandOwner.contains("private final Supplier<Track> currentTrackSupplier;"));
        assertFalse(commandOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertTrue(commandOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(commandOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(commandOwner.contains("PlaybackQueueManager.QueueStateSnapshot.empty()"));
        assertFalse(commandOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(commandOwner.contains("return snapshot.getCurrentTrack();"));
        assertTrue(commandOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertFalse(commandOwner.contains("queueStateOwner.queueStateSnapshot().getCurrentTrack()"));
        assertTrue(commandOwner.contains("playbackCommands.skipToNext();"));
        assertTrue(commandOwner.contains("controllerMediaItems.setControllerMediaItems(mediaItems, startIndex, startPositionMs)"));
        assertTrue(commandOwner.contains("seekController.accept(positionMs);"));
        assertTrue(commandOwner.contains("repeatModeController.accept(appRepeatMode);"));
        assertTrue(commandOwner.contains("metadataProvider.apply(track)"));
        assertTrue(controllerMediaItemsOwner.contains(
                "final class PlaybackControllerMediaItemsOwner implements PlaybackSessionCommandOwner.ControllerMediaItems"));
        assertTrue(controllerMediaItemsOwner.contains("interface ControllerQueueResolver"));
        assertTrue(controllerMediaItemsOwner.contains("interface QueuePlayer"));
        assertTrue(controllerMediaItemsOwner.contains(
                "controllerQueueResolver.controllerQueueForMediaItems(mediaItems, startIndex, startPositionMs)"));
        assertTrue(controllerMediaItemsOwner.contains("queuePlayer.playQueue("));
    }

    @Test
    public void playbackSleepTimerCommandsAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackSleepTimerCommandOwner.java");

        assertFalse(service.contains("new PlaybackSleepTimerManager.Actions()"));
        assertFalse(service.contains("playbackSleepTimerManager.startMinutes(minutes)"));
        assertFalse(service.contains("playbackSleepTimerManager.cancel("));
        assertTrue(service.contains("private PlaybackSleepTimerCommandOwner playbackSleepTimerCommandOwner;"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner = new PlaybackSleepTimerCommandOwner("));
        assertTrue(service.contains("EchoPlaybackService.this::pause"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.startSleepTimerMinutes(minutes);"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.cancelSleepTimer(true);"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.cancelSleepTimer(false);"));
        assertTrue(commandOwner.contains("final class PlaybackSleepTimerCommandOwner implements PlaybackSleepTimerManager.Actions"));
        assertFalse(commandOwner.contains("interface StatePublisher"));
        assertFalse(commandOwner.contains("interface SleepTimerManagerProvider"));
        assertFalse(commandOwner.contains("PlaybackNotificationCommandOwner.PlaybackCommands"));
        assertFalse(commandOwner.contains("private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;"));
        assertTrue(commandOwner.contains("private final Runnable pauseCommand;"));
        assertTrue(commandOwner.contains("private final Runnable statePublisher;"));
        assertFalse(commandOwner.contains("Supplier<PlaybackSleepTimerManager>"));
        assertFalse(commandOwner.contains("sleepTimerManagerProvider"));
        assertTrue(commandOwner.contains("private PlaybackSleepTimerManager sleepTimerManager;"));
        assertTrue(commandOwner.contains(
                "void bindPlaybackSleepTimerManager(PlaybackSleepTimerManager sleepTimerManager)"));
        assertTrue(commandOwner.contains("pauseCommand.run();"));
        assertTrue(commandOwner.contains("statePublisher.run();"));
        assertFalse(commandOwner.contains("sleepTimerManagerProvider.get();"));
        assertTrue(service.contains("playbackSleepTimerCommandOwner.bindPlaybackSleepTimerManager(playbackSleepTimerManager);"));
        assertTrue(commandOwner.contains("void startSleepTimerMinutes(int minutes)"));
        assertTrue(commandOwner.contains("manager.startMinutes(minutes);"));
        assertTrue(commandOwner.contains("void cancelSleepTimer(boolean publish)"));
        assertTrue(commandOwner.contains("manager.cancel(publish);"));
        assertTrue(commandOwner.contains("long sleepTimerRemainingMs()"));
        assertTrue(commandOwner.contains("manager.remainingMs();"));
    }

    @Test
    public void playbackProgressUpdateCommandsAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackProgressUpdateCommandOwner.java");
        String manager = read("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackProgressUpdateManager.kt");

        assertFalse(service.contains("new PlaybackProgressUpdateManager.Actions()"));
        assertFalse(service.contains("private PlaybackProgressUpdateCommandOwner playbackProgressUpdateCommandOwner;"));
        assertTrue(service.contains("final PlaybackProgressUpdateCommandOwner playbackProgressUpdateCommandOwner ="));
        assertTrue(service.contains("new PlaybackProgressUpdateCommandOwner("));
        assertTrue(service.contains("playbackProgressUpdateCommandOwner"));
        assertFalse(
                service.contains("EchoPlaybackService.this::persistCurrentPlaybackPosition,\r\n"
                        + "                () -> playbackProgressUpdateManager")
                        || service.contains("EchoPlaybackService.this::persistCurrentPlaybackPosition,\n"
                        + "                () -> playbackProgressUpdateManager")
        );
        assertFalse(service.contains("new PlaybackProgressUpdateManager.StateProvider()"));
        assertFalse(service.contains("private PlaybackProgressUpdateStateOwner playbackProgressUpdateStateOwner;"));
        assertFalse(service.contains("new PlaybackProgressUpdateStateOwner("));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackProgressUpdateStateOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackProgressUpdateStateOwnerTest.java")));
        assertTrue(service.contains("PlaybackProgressUpdateManager.stateProviderFromPlaybackState("));
        assertTrue(commandOwner.contains("final class PlaybackProgressUpdateCommandOwner implements PlaybackProgressUpdateManager.Actions"));
        assertFalse(commandOwner.contains("interface StatePublisher"));
        assertFalse(commandOwner.contains("interface PositionPersister"));
        assertFalse(commandOwner.contains("interface ProgressUpdateManagerProvider"));
        assertTrue(commandOwner.contains("private final Runnable statePublisher;"));
        assertTrue(commandOwner.contains("private final Consumer<Boolean> positionPersister;"));
        assertFalse(commandOwner.contains("Supplier<PlaybackProgressUpdateManager>"));
        assertFalse(commandOwner.contains("progressUpdateManagerProvider"));
        assertTrue(commandOwner.contains("statePublisher.run();"));
        assertTrue(commandOwner.contains("positionPersister.accept(false);"));
        assertFalse(commandOwner.contains("progressUpdateManagerProvider.get();"));
        assertFalse(commandOwner.contains("void startProgressUpdates()"));
        assertFalse(commandOwner.contains("void stopProgressUpdates()"));
        assertFalse(commandOwner.contains("manager.startIfNeeded();"));
        assertFalse(commandOwner.contains("manager.stop();"));
        assertTrue(manager.contains("fun stateProviderFromPlaybackState("));
        assertTrue(manager.contains("playbackStateProvider: BooleanSupplier?"));
        assertTrue(manager.contains("preparingStateProvider: BooleanSupplier?"));
        assertTrue(manager.contains("override fun isPlaying(): Boolean = playbackStateProvider?.asBoolean == true"));
        assertTrue(manager.contains("override fun isPreparing(): Boolean = preparingStateProvider?.asBoolean == true"));
    }

    @Test
    public void playbackErrorRecoveryCommandsAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackErrorRecoveryCommandOwner.java");
        String errorRecoveryWiring = service.substring(
                service.indexOf("playbackErrorRecoveryCommandOwner = new PlaybackErrorRecoveryCommandOwner("),
                service.indexOf("playbackErrorRecoveryManager = new PlaybackErrorRecoveryManager(")
        );

        assertFalse(service.contains("new PlaybackErrorRecoveryManager.Actions()"));
        assertFalse(service.contains("private String debugTrack(Track track)"));
        assertTrue(service.contains("private PlaybackErrorRecoveryCommandOwner playbackErrorRecoveryCommandOwner;"));
        assertTrue(service.contains("playbackErrorRecoveryCommandOwner = new PlaybackErrorRecoveryCommandOwner("));
        assertTrue(errorRecoveryWiring.contains("playbackQueueStateOwner,"));
        assertFalse(errorRecoveryWiring.contains("queueStateSupplier"));
        assertFalse(errorRecoveryWiring.contains("playbackQueueStateOwner::currentTrack"));
        assertFalse(errorRecoveryWiring.contains("playbackQueueStateOwner::hasMultipleTracks"));
        assertFalse(errorRecoveryWiring.contains("playbackQueueStateOwner::canSkipFailedTrack"));
        assertTrue(errorRecoveryWiring.contains("playbackQueueCommandOwner::prepareCurrent"));
        assertFalse(errorRecoveryWiring.contains("EchoPlaybackService.this::prepareCurrent"));
        assertTrue(errorRecoveryWiring.contains("EchoPlaybackService.this::skipToNext"));
        assertTrue(service.contains("playbackErrorRecoveryCommandOwner"));
        assertTrue(service.contains("playbackCurrentTrackPreparationRuntimeOwner::setErrorMessage"));
        assertFalse(service.contains("playbackRuntimeStateManager::setErrorMessage"));
        assertTrue(service.contains("playbackErrorRecoveryCommandOwner.setErrorMessage(\"Unable to play this track.\")"));
        assertTrue(service.contains("playbackErrorRecoveryCommandOwner.setErrorMessage(\"Playback is not ready.\")"));
        assertFalse(service.contains("playbackRuntimeStateManager.setErrorMessage(\"Unable to play this track.\")"));
        assertFalse(service.contains("playbackRuntimeStateManager.setErrorMessage(\"Playback is not ready.\")"));
        assertTrue(service.contains("playbackErrorRecoveryCommandOwner.debugTrack(track)"));
        assertTrue(service.contains("playbackErrorRecoveryCommandOwner.debugCurrentTrack()"));
        assertFalse(service.contains("playbackErrorRecoveryCommandOwner.debugTrack(playbackQueueStateOwner.currentTrack())"));
        assertTrue(commandOwner.contains("final class PlaybackErrorRecoveryCommandOwner implements PlaybackErrorRecoveryManager.Actions"));
        assertFalse(commandOwner.contains("interface CurrentTrackProvider"));
        assertFalse(commandOwner.contains("interface FailedTrackPolicy"));
        assertFalse(commandOwner.contains("interface TrackDebugger"));
        assertFalse(commandOwner.contains("interface PlaybackPreparer"));
        assertFalse(commandOwner.contains("interface ErrorMessageStore"));
        assertFalse(commandOwner.contains("interface StatePublisher"));
        assertFalse(commandOwner.contains("interface WarningLogger"));
        assertFalse(commandOwner.contains("PlaybackNotificationCommandOwner.PlaybackCommands"));
        assertFalse(commandOwner.contains("private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;"));
        assertFalse(commandOwner.contains("private final Supplier<Track> currentTrackProvider;"));
        assertFalse(commandOwner.contains("private final Predicate<Track> failedTrackPolicy;"));
        assertFalse(commandOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(commandOwner.contains("PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateOwner == null"));
        assertFalse(commandOwner.contains("private final Supplier<Track> currentTrackSupplier;"));
        assertFalse(commandOwner.contains("private final BooleanSupplier hasMultipleTracksSupplier;"));
        assertFalse(commandOwner.contains("private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;"));
        assertTrue(commandOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertFalse(commandOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertFalse(commandOwner.contains("hasMultipleTracksSupplier.getAsBoolean()"));
        assertTrue(commandOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertFalse(commandOwner.contains("return queueStateSnapshot().getCurrentTrack();"));
        assertFalse(commandOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(commandOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(commandOwner.contains("return snapshot.getCurrentTrack();"));
        assertFalse(commandOwner.contains("&& snapshot.getHasMultipleTracks();"));
        assertTrue(commandOwner.contains("&& queueStateOwner.hasMultipleTracks();"));
        assertTrue(commandOwner.contains("private final Consumer<Boolean> playbackPreparer;"));
        assertTrue(commandOwner.contains("private final Runnable skipToNextCommand;"));
        assertTrue(commandOwner.contains("private final Consumer<String> errorMessageStore;"));
        assertTrue(commandOwner.contains("private final Runnable statePublisher;"));
        assertTrue(commandOwner.contains("private final BiConsumer<String, Exception> warningLogger;"));
        assertTrue(commandOwner.contains("return \"track=<null>\";"));
        assertTrue(commandOwner.contains("\"trackId=\" + track.id"));
        assertFalse(commandOwner.contains("return failedTrackPolicy.test(failed);"));
        assertTrue(commandOwner.contains("playbackPreparer.accept(playWhenReady);"));
        assertTrue(commandOwner.contains("skipToNextCommand.run();"));
        assertTrue(commandOwner.contains("errorMessageStore.accept(message);"));
        assertTrue(commandOwner.contains("statePublisher.run();"));
        assertTrue(commandOwner.contains("warningLogger.accept(message, error);"));
    }

    @Test
    public void playbackCrossfadeCommandsAreOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackCrossfadeCommandOwner.java");
        String stateOwner = read("app/src/main/java/app/yukine/playback/PlaybackCrossfadeStateOwner.java");

        assertFalse(service.contains("new PlaybackCrossfadeAdvanceManager.Actions()"));
        assertFalse(service.contains("playbackCrossfadeAdvanceManager.startFadeOutThenNext()"));
        assertFalse(service.contains("playbackCrossfadeAdvanceManager.cancel();"));
        assertTrue(service.contains("private PlaybackCrossfadeCommandOwner playbackCrossfadeCommandOwner;"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner = new PlaybackCrossfadeCommandOwner("));
        assertTrue(service.contains("playbackCrossfadeCommandOwner"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner.startFadeOutThenNext()"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner.cancelCrossfadeAdvance();"));
        assertFalse(service.contains("new PlaybackCrossfadeAdvanceManager.StateProvider()"));
        assertFalse(service.contains("private PlaybackCrossfadeStateOwner playbackCrossfadeStateOwner;"));
        assertTrue(service.contains("final PlaybackCrossfadeStateOwner playbackCrossfadeStateOwner = new PlaybackCrossfadeStateOwner("));
        assertTrue(service.contains("                playbackPlayerStateOwner::isPlaying,"));
        assertFalse(service.contains(
                "                playbackPlayerStateOwner::isPlaying,\r\n"
                        + "                queueStateSupplier,"));
        assertTrue(service.contains(
                "                () -> playbackModeSettingsStore == null\n"
                        + "                        ? REPEAT_ALL\n"
                        + "                        : playbackModeSettingsStore.repeatMode(playbackRuntimeStateManager),\n"
                        + "                playbackQueueStateOwner,"));
        assertFalse(service.contains("                playbackQueueStateOwner::hasMultipleTracks,"));
        assertFalse(service.contains("                playbackQueueStateOwner::isAtEndOfQueue,"));
        assertFalse(service.contains("playbackQueueNavigationOwner,\r\n                () -> playbackRuntimeStateManager == null"));
        assertTrue(service.contains("                playbackCrossfadeStateOwner,"));
        assertTrue(commandOwner.contains("final class PlaybackCrossfadeCommandOwner implements PlaybackCrossfadeAdvanceManager.Actions"));
        assertFalse(commandOwner.contains("interface TransitionState"));
        assertFalse(commandOwner.contains("interface PlayerVolumeController"));
        assertFalse(commandOwner.contains("interface ImmediateSkipCommand"));
        assertFalse(commandOwner.contains("interface AppVolumeApplier"));
        assertFalse(commandOwner.contains("interface CrossfadeAdvanceManagerProvider"));
        assertTrue(commandOwner.contains("private final Consumer<Boolean> transitionState;"));
        assertTrue(commandOwner.contains("private final Consumer<Float> playerVolumeController;"));
        assertTrue(commandOwner.contains("private final Runnable immediateSkipCommand;"));
        assertTrue(commandOwner.contains("private final Runnable appVolumeApplier;"));
        assertFalse(commandOwner.contains("Supplier<PlaybackCrossfadeAdvanceManager>"));
        assertFalse(commandOwner.contains("crossfadeAdvanceManagerProvider"));
        assertTrue(commandOwner.contains("private PlaybackCrossfadeAdvanceManager crossfadeAdvanceManager;"));
        assertTrue(commandOwner.contains(
                "void bindPlaybackCrossfadeAdvanceManager(PlaybackCrossfadeAdvanceManager crossfadeAdvanceManager)"));
        assertTrue(commandOwner.contains("transitionState.accept(enabled);"));
        assertTrue(commandOwner.contains("playerVolumeController.accept(volume);"));
        assertTrue(commandOwner.contains("immediateSkipCommand.run();"));
        assertTrue(commandOwner.contains("appVolumeApplier.run();"));
        assertTrue(service.contains("playbackCrossfadeCommandOwner.bindPlaybackCrossfadeAdvanceManager(playbackCrossfadeAdvanceManager);"));
        assertTrue(commandOwner.contains("boolean startFadeOutThenNext()"));
        assertTrue(commandOwner.contains("manager.startFadeOutThenNext();"));
        assertTrue(commandOwner.contains("void cancelCrossfadeAdvance()"));
        assertTrue(commandOwner.contains("manager.cancel();"));
        assertTrue(stateOwner.contains("final class PlaybackCrossfadeStateOwner implements PlaybackCrossfadeAdvanceManager.StateProvider"));
        assertFalse(stateOwner.contains("interface TransitionStateProvider"));
        assertFalse(stateOwner.contains("interface PlayerAvailabilityProvider"));
        assertFalse(stateOwner.contains("interface PlaybackStateProvider"));
        assertFalse(stateOwner.contains("interface RepeatModeProvider"));
        assertFalse(stateOwner.contains("interface QueueStateProvider"));
        assertFalse(stateOwner.contains("interface BaseVolumeProvider"));
        assertTrue(stateOwner.contains("private final BooleanSupplier transitionStateProvider;"));
        assertTrue(stateOwner.contains("private final BooleanSupplier playerAvailabilityProvider;"));
        assertTrue(stateOwner.contains("private final BooleanSupplier playbackStateProvider;"));
        assertTrue(stateOwner.contains("private final IntSupplier repeatModeProvider;"));
        assertTrue(stateOwner.contains("private final DoubleSupplier baseVolumeProvider;"));
        assertTrue(stateOwner.contains("import java.util.function.DoubleSupplier;"));
        assertFalse(stateOwner.contains("import java.util.function.Supplier;"));
        assertFalse(stateOwner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertFalse(stateOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(stateOwner.contains("private final BooleanSupplier hasMultipleTracksProvider;"));
        assertFalse(stateOwner.contains("private final BooleanSupplier atEndOfQueueProvider;"));
        assertFalse(stateOwner.contains(
                "private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;"
        ));
        assertTrue(stateOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertTrue(stateOwner.contains("return transitionStateProvider.getAsBoolean();"));
        assertTrue(stateOwner.contains("return playerAvailabilityProvider.getAsBoolean();"));
        assertTrue(stateOwner.contains("return playbackStateProvider.getAsBoolean();"));
        assertFalse(stateOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider == null"));
        assertFalse(stateOwner.contains(": queueStateProvider.queueStateSnapshot();"));
        assertFalse(stateOwner.contains("PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateOwner == null"));
        assertFalse(stateOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(stateOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(stateOwner.contains("if (!snapshot.getHasMultipleTracks())"));
        assertTrue(stateOwner.contains("if (queueStateOwner == null || !queueStateOwner.hasMultipleTracks())"));
        assertFalse(stateOwner.contains("if (hasMultipleTracksProvider == null || !hasMultipleTracksProvider.getAsBoolean())"));
        assertFalse(stateOwner.contains("boolean atEndOfQueue = atEndOfQueueProvider != null && atEndOfQueueProvider.getAsBoolean();"));
        assertFalse(stateOwner.contains("return repeatModeProvider.getAsInt() != REPEAT_OFF || !snapshot.isAtEndOfQueue();"));
        assertTrue(stateOwner.contains(
                "return repeatModeProvider.getAsInt() != REPEAT_OFF || !queueStateOwner.isAtEndOfQueue();"));
        assertTrue(stateOwner.contains("return baseVolumeProvider == null ? 1.0f : (float) baseVolumeProvider.getAsDouble();"));
    }

    @Test
    public void playbackRecoverySchedulerUsesSemanticPrepareActionWithoutForwardingOwner() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String scheduler = read("feature/playback/src/main/java/app/yukine/playback/manager/PlaybackRecoveryScheduler.kt");

        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackRecoveryCommandOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackRecoveryCommandOwnerTest.java")));
        assertFalse(service.contains("PlaybackRecoveryCommandOwner"));
        assertTrue(service.contains("                playbackQueueCommandOwner::prepareCurrent"));
        assertTrue(scheduler.contains("fun interface Actions"));
        assertTrue(scheduler.contains("fun prepareCurrent(playWhenReady: Boolean)"));
    }

    @Test
    public void playbackMediaLibraryCallbackIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackMediaLibraryCallback.kt");

        assertFalse(service.contains("private final class EchoMediaLibraryCallback"));
        assertFalse(service.contains("private MediaItem itemForAutoMediaId("));
        assertFalse(service.contains("private List<MediaItem> childrenForAutoParent("));
        assertFalse(service.contains("private List<MediaItem> groupedAutoItems("));
        assertFalse(service.contains("private List<Track> filterTracksByArtist("));
        assertFalse(service.contains("private List<Track> filterTracksByAlbum("));
        assertFalse(service.contains("private List<MediaItem> autoItemsForTracks("));
        assertFalse(service.contains("private MediaItem autoMediaItemForTrack("));
        assertFalse(service.contains("private MediaItem browsableItem("));
        assertFalse(service.contains("private List<MediaItem> pagedItems("));
        assertFalse(service.contains("new PlaybackMediaLibraryCallback.DataSource()"));
        assertFalse(service.contains("return repository.loadCachedTracks();"));
        assertFalse(service.contains("return repository.loadPlaylists();"));
        assertFalse(service.contains("return repository.loadRecentlyPlayed(limit);"));
        assertFalse(service.contains("return repository.loadPlaylistTracks(playlistId);"));
        assertFalse(service.contains("private PlaybackMediaLibraryCallback playbackMediaLibraryCallback;"));
        assertTrue(service.contains("final PlaybackMediaLibraryCallback playbackMediaLibraryCallback = new PlaybackMediaLibraryCallback("));
        assertTrue(service.contains("() -> createSessionPlayer(playbackSessionCommandOwner)"));
        assertTrue(service.contains("PlaybackMediaLibraryDataSource.fromRepository("));
        assertFalse(service.contains("private static final String AUTO_ROOT"));
        assertFalse(service.contains("private static final String AUTO_ALL"));
        assertFalse(service.contains("private static final String AUTO_RECENT"));
        assertFalse(service.contains("private static final String AUTO_PLAYLISTS"));
        assertFalse(service.contains("private static final String AUTO_PLAYLIST_PREFIX"));
        assertFalse(service.contains("private static final String AUTO_ARTISTS"));
        assertFalse(service.contains("private static final String AUTO_ARTIST_PREFIX"));
        assertFalse(service.contains("private static final String AUTO_ALBUMS"));
        assertFalse(service.contains("private static final String AUTO_ALBUM_PREFIX"));
        assertFalse(service.contains("private static final String AUTO_TRACK_PREFIX"));
        assertTrue(owner.contains("class PlaybackMediaLibraryCallback"));
        assertTrue(owner.contains("fun mediaItemForTrack(track: Track): MediaItem"));
        assertFalse(owner.contains("fun cacheKeyForTrack(track: Track): String?"));
        assertFalse(owner.contains("fun mediaItemForPlaybackTrack(track: Track): MediaItem"));
        assertTrue(owner.contains("fun onGetLibraryRoot("));
        assertTrue(owner.contains("fun onGetChildren("));
        assertTrue(owner.contains("fun onSetMediaItems("));
        assertTrue(owner.contains("fun onAddMediaItems("));
        assertTrue(owner.contains("AUTO_ROOT"));
        assertTrue(owner.contains("AUTO_TRACK_PREFIX"));
    }

    @Test
    public void playbackServiceActionMappingIsOwnedOutsideEchoPlaybackService() throws Exception {
        String service = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String normalizedService = service.replace("\r\n", "\n");
        String owner = read("app/src/main/java/app/yukine/playback/manager/PlaybackNotificationManager.kt");
        String foregroundOwner = read("app/src/main/java/app/yukine/playback/PlaybackNotificationForegroundOwner.java");
        String commandOwner = read("app/src/main/java/app/yukine/playback/PlaybackNotificationCommandOwner.java");
        String stateOwner = read("app/src/main/java/app/yukine/playback/PlaybackNotificationStateOwner.java");
        String favoriteCommandOwner = read("app/src/main/java/app/yukine/playback/PlaybackFavoriteCommandOwner.java");
        String artworkSource = read("feature/playback/src/main/java/app/yukine/playback/PlaybackNotificationArtworkSource.java");
        String toggleFavoriteUseCase = read("app/src/main/java/app/yukine/ToggleFavoriteUseCase.kt");

        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackServiceActionHandler.java"));
        assertTrue(service.contains("playbackNotificationManager.handleServiceAction(action);"));
        assertTrue(normalizedService.contains("implements PlaybackServiceHostPort, PlaybackNotificationCommandOwner.PlaybackCommands"));
        assertTrue(service.contains("playbackNotificationCommandOwner = PlaybackNotificationCommandOwner.fromNotificationOwners("));
        assertTrue(service.contains("playbackNotificationCommandOwner"));
        assertFalse(service.contains("new PlaybackNotificationManager.ActionCallbacks()"));
        assertFalse(service.contains("private void publishPlaybackNotification(boolean force)"));
        assertFalse(service.contains("EchoPlaybackService.this::publishPlaybackNotification"));
        assertTrue(commandOwner.contains("static PlaybackNotificationCommandOwner fromNotificationOwners("));
        assertTrue(commandOwner.contains("Supplier<PlaybackStatePublisher> statePublisherProvider"));
        assertTrue(commandOwner.contains("Supplier<PlaybackNotificationManager> notificationManagerProvider"));
        assertTrue(commandOwner.contains("BooleanSupplier notificationWorthySupplier"));
        assertTrue(commandOwner.contains("statePublisher.publishNotification(force);"));
        assertTrue(commandOwner.contains("notificationManager.updateMediaNotification(force);"));
        assertFalse(service.contains("private PlaybackNotificationForegroundOwner playbackNotificationForegroundOwner;"));
        assertTrue(service.contains(
                "final PlaybackNotificationForegroundOwner playbackNotificationForegroundOwner =\n"
                        + "                new PlaybackNotificationForegroundOwner("));
        assertTrue(service.contains("                playbackNotificationForegroundOwner"));
        assertTrue(service.contains("                playbackNotificationForegroundOwner,"));
        assertTrue(service.contains("                playbackNotificationForegroundOwner::stopForegroundAndSelf"));
        assertTrue(service.contains("playbackNotificationCommandOwner.stopForegroundAndSelf();"));
        assertFalse(service.contains("        stopForeground(true);\r\n        publishState();\r\n        stopSelf();"));
        assertFalse(service.contains("new PlaybackNotificationManager.ForegroundController()"));
        assertFalse(service.contains("private PlaybackNotificationStateOwner playbackNotificationStateOwner;"));
        assertTrue(service.contains("final PlaybackNotificationStateOwner playbackNotificationStateOwner = new PlaybackNotificationStateOwner("));
        assertTrue(normalizedService.contains(
                "final PlaybackNotificationStateOwner playbackNotificationStateOwner = new PlaybackNotificationStateOwner(\n"
                        + "                playbackQueueStateOwner,"));
        assertFalse(service.contains("                playbackQueueStateOwner::queueStateSnapshot,"));
        assertFalse(service.contains("                playbackQueueStateOwner::isQueueEmpty,"));
        assertTrue(service.contains("                playbackNotificationStateOwner,"));
        assertFalse(service.contains("PlaybackNotificationStateOwner.playbackStateProviderFromPlaybackState("));
        assertTrue(service.contains("                playbackPlayerStateOwner::isPlaying,"));
        assertTrue(service.contains("                playbackCurrentTrackPreparationRuntimeOwner::preparing,"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackActiveStateOwner.java")));
        assertFalse(service.contains("PlaybackActiveStateOwner"));
        assertFalse(service.contains("new PlaybackNotificationManager.StateProvider()"));
        assertFalse(service.contains("new PlaybackNotificationStateOwner.PlaybackStateProvider()"));
        assertFalse(exists("app/src/main/java/app/yukine/playback/PlaybackNotificationLyricsTextOwner.java"));
        assertTrue(service.contains("                () -> playbackLyricsManager,"));
        assertFalse(service.contains("new PlaybackNotificationManager.LyricsTextProvider()"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkProviderOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackNotificationArtworkProviderOwnerTest.java")));
        assertFalse(service.contains("PlaybackNotificationArtworkProviderOwner"));
        assertFalse(service.contains("private PlaybackNotificationArtworkSource playbackNotificationArtworkSource;"));
        assertTrue(service.contains(
                "final PlaybackNotificationArtworkSource playbackNotificationArtworkSource =\n"
                        + "                PlaybackNotificationArtworkSource.fromSupplier("));
        assertTrue(service.contains("                playbackNotificationArtworkSource,"));
        assertTrue(service.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                currentTrackSupplier,"));
        assertFalse(service.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                playbackQueueStateOwner::currentTrack,"));
        assertFalse(service.contains(
                "playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(\n"
                        + "                this,\n"
                        + "                playbackQueueManager,"));
        assertFalse(service.contains("new PlaybackNotificationManager.ArtworkProvider()"));
        assertFalse(Files.exists(Path.of("app/src/main/java/app/yukine/playback/PlaybackNotificationArtworkStateOwner.java")));
        assertFalse(Files.exists(Path.of("app/src/test/java/app/yukine/playback/PlaybackNotificationArtworkStateOwnerTest.java")));
        assertFalse(service.contains("PlaybackNotificationArtworkStateOwner"));
        assertFalse(normalizedService.contains(
                "PlaybackNotificationStateOwner.playbackStateProviderFromPlaybackState(\n"
                        + "                        playbackQueueStateOwner::currentTrack,"
        ));
        assertFalse(service.contains("                queueStateSupplier,"));
        assertFalse(service.contains("                playbackQueueStateOwner::isQueueEmpty,"));
        assertFalse(stateOwner.contains("static PlaybackStateProvider playbackStateProviderFromPlaybackState("));
        assertFalse(stateOwner.contains("final class PlaybackActiveStateOwner"));
        assertFalse(service.contains("new PlaybackNotificationArtworkManager.StateProvider()"));
        assertTrue(normalizedService.contains("@Inject\n    ToggleFavoriteUseCase toggleFavoriteUseCase;"));
        String toggleCurrentFavoriteMethod = normalizedService.substring(
                normalizedService.indexOf("    public void toggleCurrentFavorite()"),
                normalizedService.indexOf("    public void restoreLastPlayback(boolean playWhenRestored)")
        );
        assertTrue(toggleCurrentFavoriteMethod.contains("PlaybackFavoriteCommandOwner.toggleCurrentFavorite("));
        assertFalse(toggleCurrentFavoriteMethod.contains("Track track = playbackQueueStateOwner.currentTrack();"));
        assertFalse(toggleCurrentFavoriteMethod.contains("toggleFavoriteUseCase.toggle(track)"));
        assertTrue(favoriteCommandOwner.contains("final class PlaybackFavoriteCommandOwner"));
        assertFalse(favoriteCommandOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(favoriteCommandOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(favoriteCommandOwner.contains("Track track = snapshot.getCurrentTrack();"));
        assertTrue(favoriteCommandOwner.contains("Track track = queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertTrue(favoriteCommandOwner.contains("toggleFavoriteUseCase.toggle(track)"));
        assertTrue(favoriteCommandOwner.contains("statePublisher.run();"));
        assertFalse(service.contains("private PlaybackFavoriteCommandOwner playbackFavoriteCommandOwner;"));
        assertTrue(service.contains("toggleFavoriteUseCase.isFavorite(track)"));
        assertFalse(service.contains("new ToggleFavoriteUseCase("));
        assertFalse(service.contains("repository.setFavorite(track, !repository.isFavorite(track.id));"));
        assertFalse(service.contains("private boolean isPlaybackServiceAction(String action)"));
        assertFalse(service.contains("if (ACTION_PLAY.equals(action))"));
        assertFalse(service.contains("if (PlaybackServiceActions.PLAY.equals(action))"));
        assertFalse(service.contains("} else if (ACTION_PAUSE.equals(action))"));
        assertFalse(service.contains("ACTION_NEXT.equals(action)"));
        assertFalse(service.contains("ACTION_PREVIOUS.equals(action)"));
        assertFalse(service.contains("ACTION_RESTORE_AND_PLAY.equals(action)"));
        assertFalse(service.contains("playbackNotificationManager.updateMediaNotification(false)"));
        assertFalse(service.contains("playbackNotificationManager.updateMediaNotification(true)"));
        assertTrue(commandOwner.contains(
                "static PlaybackStatePublisher.NotificationUpdater notificationUpdaterFromNotificationManagerSupplier("));
        assertTrue(commandOwner.contains("notificationManager.updateMediaNotification(force);"));
        assertFalse(service.contains("playbackNotificationManager.updateMediaNotification(force);"));
        assertFalse(service.contains("playbackNotificationManager.handleServiceAction(null)"));
        assertTrue(owner.contains("interface ActionCallbacks"));
        assertTrue(owner.contains("fun handleServiceAction(action: String?): Boolean"));
        assertTrue(owner.contains("PlaybackServiceActions.PLAY -> callbacks.play()"));
        assertTrue(owner.contains("PlaybackServiceActions.RESTORE_AND_PLAY -> callbacks.restoreLastPlayback(true)"));
        assertTrue(owner.contains("callbacks.stopForegroundAndSelf()"));
        assertTrue(foregroundOwner.contains("implements PlaybackNotificationManager.ForegroundController"));
        assertFalse(foregroundOwner.contains("PlaybackNotificationCommandOwner.ForegroundController"));
        assertFalse(foregroundOwner.contains("interface ActivityPendingIntentProvider"));
        assertFalse(foregroundOwner.contains("interface ServiceActionPendingIntentProvider"));
        assertFalse(foregroundOwner.contains("interface ForegroundStarter"));
        assertFalse(foregroundOwner.contains("interface ForegroundStopper"));
        assertTrue(foregroundOwner.contains("import java.util.function.BiFunction;"));
        assertTrue(foregroundOwner.contains("import java.util.function.Consumer;"));
        assertTrue(foregroundOwner.contains("import java.util.function.Supplier;"));
        assertTrue(foregroundOwner.contains("private final Supplier<PendingIntent> activityPendingIntentProvider;"));
        assertTrue(foregroundOwner.contains("private final BiFunction<String, Integer, PendingIntent> serviceActionPendingIntentProvider;"));
        assertTrue(foregroundOwner.contains("private final Consumer<Notification> foregroundStarter;"));
        assertTrue(foregroundOwner.contains("private final Runnable foregroundStopper;"));
        assertTrue(foregroundOwner.contains("return activityPendingIntentProvider.get();"));
        assertTrue(foregroundOwner.contains("return serviceActionPendingIntentProvider.apply(action, requestCode);"));
        assertTrue(foregroundOwner.contains("foregroundStarter.accept(notification);"));
        assertFalse(foregroundOwner.contains("public void stopForegroundAndSelf("));
        assertTrue(foregroundOwner.contains("void stopForegroundAndSelf("));
        assertTrue(foregroundOwner.contains("foregroundStopper.run();"));
        assertTrue(commandOwner.contains("final class PlaybackNotificationCommandOwner implements PlaybackNotificationManager.ActionCallbacks"));
        assertTrue(commandOwner.contains("interface PlaybackCommands"));
        assertFalse(commandOwner.contains("interface NotificationPublisher"));
        assertFalse(commandOwner.contains("interface StatePublisherProvider"));
        assertFalse(commandOwner.contains("interface NotificationManagerProvider"));
        assertFalse(commandOwner.contains("interface NotificationStateProvider"));
        assertFalse(commandOwner.contains("interface ForegroundController"));
        assertTrue(commandOwner.contains("import java.util.function.BooleanSupplier;"));
        assertTrue(commandOwner.contains("import java.util.function.Consumer;"));
        assertTrue(commandOwner.contains("import java.util.function.Supplier;"));
        assertTrue(commandOwner.contains("private final Consumer<Boolean> notificationPublisher;"));
        assertTrue(commandOwner.contains("private final BooleanSupplier notificationWorthySupplier;"));
        assertTrue(commandOwner.contains("private final Runnable foregroundStopper;"));
        assertTrue(commandOwner.contains("void toggleCurrentFavorite()"));
        assertTrue(commandOwner.contains("boolean hasNotificationWorthyState()"));
        assertTrue(commandOwner.contains("void publishPlaybackNotificationIfWorthy()"));
        assertTrue(commandOwner.contains("playbackCommands.restoreLastPlayback(playWhenReady);"));
        assertTrue(commandOwner.contains("notificationPublisher.accept(force);"));
        assertTrue(commandOwner.contains("notificationWorthySupplier.getAsBoolean()"));
        assertTrue(commandOwner.contains("foregroundStopper.run();"));
        assertTrue(stateOwner.contains("final class PlaybackNotificationStateOwner implements PlaybackNotificationManager.StateProvider"));
        assertFalse(stateOwner.contains("interface QueueStateProvider"));
        assertFalse(stateOwner.contains("interface PlaybackStateProvider"));
        assertFalse(stateOwner.contains("interface FavoriteStateProvider"));
        assertFalse(stateOwner.contains("interface SessionTokenProvider"));
        assertTrue(stateOwner.contains("import java.util.function.BooleanSupplier;"));
        assertTrue(stateOwner.contains("import java.util.function.Predicate;"));
        assertTrue(stateOwner.contains("import java.util.function.Supplier;"));
        assertFalse(stateOwner.contains("private final BooleanSupplier queueEmptySupplier;"));
        assertFalse(stateOwner.contains("private final Supplier<Track> currentTrackSupplier;"));
        assertFalse(stateOwner.contains(
                "private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;"
        ));
        assertTrue(stateOwner.contains("private final PlaybackQueueStateOwner queueStateOwner;"));
        assertTrue(stateOwner.contains("private final BooleanSupplier playingStateProvider;"));
        assertTrue(stateOwner.contains("private final BooleanSupplier preparingStateProvider;"));
        assertFalse(stateOwner.contains("private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;"));
        assertFalse(stateOwner.contains("import app.yukine.playback.manager.PlaybackQueueManager;"));
        assertTrue(stateOwner.contains("private final Predicate<Track> favoriteStateProvider;"));
        assertTrue(stateOwner.contains("private final Supplier<MediaSession.Token> sessionTokenSupplier;"));
        assertFalse(stateOwner.contains("PlaybackQueueManager.QueueStateSnapshot queueSnapshot = queueStateOwner == null"));
        assertFalse(stateOwner.contains("PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null"));
        assertFalse(stateOwner.contains(": queueStateOwner.queueStateSnapshot();"));
        assertFalse(stateOwner.contains("return snapshot.isQueueEmpty();"));
        assertFalse(stateOwner.contains("return snapshot.getCurrentTrack();"));
        assertTrue(stateOwner.contains("return queueStateOwner == null ? null : queueStateOwner.currentTrack();"));
        assertTrue(stateOwner.contains("return queueStateOwner == null || queueStateOwner.isQueueEmpty();"));
        assertFalse(stateOwner.contains("queueStateSnapshot().getCurrentTrack()"));
        assertFalse(stateOwner.contains("return queueEmptySupplier == null || queueEmptySupplier.getAsBoolean();"));
        assertFalse(stateOwner.contains("return currentTrackSupplier == null ? null : currentTrackSupplier.get();"));
        assertFalse(stateOwner.contains("return playbackStateProvider.currentTrack();"));
        assertFalse(stateOwner.contains("return playbackStateProvider.isPlaying();"));
        assertFalse(stateOwner.contains("return playbackStateProvider.isPreparing();"));
        assertTrue(stateOwner.contains("return playingStateProvider != null && playingStateProvider.getAsBoolean();"));
        assertTrue(stateOwner.contains("return preparingStateProvider != null && preparingStateProvider.getAsBoolean();"));
        assertTrue(stateOwner.contains("return favoriteStateProvider.test(track);"));
        assertTrue(stateOwner.contains("return sessionTokenSupplier.get();"));
        assertFalse(owner.contains("interface LyricsTextProvider"));
        assertTrue(owner.contains("Supplier<out LyricsPublisher?>?"));
        assertTrue(owner.contains("lyricsPublisherSupplier?.get()?.notificationLyricText(track).orEmpty()"));
        assertTrue(owner.contains("lyricsPublisherSupplier?.get()?.sanitizeNotificationLyric(value).orEmpty()"));
        assertFalse(service.contains("playbackLyricsManager.notificationLyricText("));
        assertFalse(service.contains("playbackLyricsManager.sanitizeNotificationLyric("));
        assertTrue(artworkSource.contains("public interface PlaybackNotificationArtworkSource extends PlaybackStatePublisher.ArtworkProvider"));
        assertTrue(artworkSource.contains("static PlaybackNotificationArtworkSource fromSupplier("));
        assertTrue(artworkSource.contains("Supplier<PlaybackNotificationArtworkSource> artworkSourceProvider"));
        assertTrue(artworkSource.contains("PlaybackNotificationArtworkSource source = artworkSource();"));
        assertTrue(artworkSource.contains("return source == null ? null : source.notificationArtworkFor(track);"));
        assertTrue(artworkSource.contains("return source == null ? null : source.notificationArtworkDataFor(track);"));
        assertTrue(artworkSource.contains("Bitmap notificationArtworkFor(Track track);"));
        assertTrue(artworkSource.contains("byte[] notificationArtworkDataFor(Track track);"));
        assertTrue(owner.contains("fun isFavorite(track: Track?): Boolean"));
        assertTrue(owner.contains("val isFavorite = stateProvider.isFavorite(track)"));
        assertFalse(owner.contains("val isFavorite = false"));
        assertTrue(toggleFavoriteUseCase.contains("fun toggle(track: Track?): Boolean"));
        assertTrue(toggleFavoriteUseCase.contains("fun isFavorite(track: Track?): Boolean"));
        assertTrue(toggleFavoriteUseCase.contains("operations.setFavorite(track, !operations.isFavorite(track.id))"));
    }

    @Test
    public void streamingGatewaySettingsBoundaryIsKotlin() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String settingsStore = read("app/src/main/java/app/yukine/StreamingGatewaySettingsStore.kt");

        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewaySettingsStore.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewayController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewayController.java"));
        assertFalse(exists("app/src/test/java/app/yukine/StreamingGatewayControllerTest.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewayEventController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewayEventController.java"));
        assertFalse(exists("app/src/main/java/app/yukine/StreamingGatewayHostBindings.kt"));
        assertTrue(settingsStore.contains("class StreamingGatewaySettingsStore"));
        assertTrue(settingsStore.contains("interface StreamingGatewayEndpointStore"));
        assertTrue(settingsStore.contains("@JvmStatic"));
        assertTrue(settingsStore.contains("fun normalize(value: String?)"));
        assertFalse(mainActivity.contains("StreamingGatewayEventController"));
        assertFalse(mainActivity.contains("StreamingGatewayController streamingGatewayController"));
        assertFalse(mainActivity.contains("new StreamingGatewayController("));
        assertTrue(mainActivity.contains("private kotlinx.coroutines.Job refreshStreamingProviders()"));
        assertTrue(mainActivity.contains("kotlinx.coroutines.Job job = streamingViewModel.refreshStreamingProviders();"));
        assertTrue(mainActivity.contains("private kotlinx.coroutines.Job applyStreamingGatewayEndpoint(String endpoint)"));
        assertTrue(mainActivity.contains("streamingGatewaySettingsStore.setEndpoint(endpoint);"));
        assertTrue(mainActivity.contains("streamingViewModel.configureStreamingRepository();"));
        assertTrue(mainActivity.contains("AppLanguage.text(settingsStore.languageMode(), \"streaming.gateway.applied\")"));
        assertTrue(mainActivity.contains("syncStreamingProvidersAndRender();"));
        assertTrue(mainActivity.contains("streamingRecommendationViewModel.updateProviders(streamingViewModel.getState().getProviders());"));
        assertFalse(mainActivity.contains("new StreamingGatewayHostBindings("));
        assertFalse(mainActivity.contains("new StreamingGatewayController.ViewModelBridge()"));
        assertFalse(mainActivity.contains("new StreamingGatewayController.Listener()"));
    }

    @Test
    public void passivePlaybackAndAnimationUpdatesKeepTheCurrentViewport() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String nowPlayingViewModel = read("app/src/main/java/app/yukine/NowPlayingViewModel.kt");
        String nowPlaying = read("app/src/main/java/app/yukine/ui/NowPlayingScreen.kt");
        String shellController = read("app/src/main/java/app/yukine/MainUiShellController.java");
        String labelFormatter = read("app/src/main/java/app/yukine/SettingsLabelFormatter.kt");
        String settingsViewModel = read("app/src/main/java/app/yukine/SettingsViewModel.kt");
        String settingsScreen = read("app/src/main/java/app/yukine/ui/SettingsScreen.kt");
        String trackListScreen = read("app/src/main/java/app/yukine/ui/TrackListScreen.kt");
        String collectionsScreen = read("app/src/main/java/app/yukine/ui/CollectionsScreen.kt");
        String queueScreen = read("app/src/main/java/app/yukine/ui/QueueScreen.kt");
        String playlistListScreen = read("app/src/main/java/app/yukine/ui/PlaylistListScreen.kt");
        String playlistTrackScreen = read("app/src/main/java/app/yukine/ui/PlaylistTrackScreen.kt");

        assertTrue(mainActivity.contains("private boolean scrollContentToTopOnNextRender"));
        assertFalse(mainActivity.contains("renderAndPersistSelectedTab(true)"));
        assertFalse(mainActivity.contains("uiShellController.navigateContentRoute(selectedTab())"));
        assertTrue(mainActivity.contains("renderSelectedTabForNavHostState();"));
        assertTrue(mainActivity.contains("public void onTabChanged(app.yukine.navigation.TabRoute tab)"));
        assertTrue(mainActivity.contains("navigateToTab(tab.getRoute(), true, true);"));
        assertTrue(mainActivity.contains("userInitiated && sameTab && previousDirectory.equals(currentDirectoryKey())"));
        assertTrue(mainActivity.contains("private void requestCurrentDirectoryScrollToTop()"));
        assertEquals(2, countOccurrences(mainActivity, "requestCurrentDirectoryScrollToTop()"));
        assertEquals(1, countOccurrences(mainActivity, "scrollContentToTopOnNextRender = true;"));
        assertTrue(mainActivity.contains("settingsViewModel.scrollToTopOnNextRender();"));
        assertFalse(mainActivity.contains("settingsPageRenderController.scrollToTopOnNextRender();"));
        assertFalse(mainActivity.contains("uiShellController.useScrollingContentContainer();"));
        assertFalse(mainActivity.contains("uiShellController.updateTabBar(selectedTab());"));
        assertFalse(mainActivity.contains("uiShellController.hasContentHost()"));
        assertFalse(mainActivity.contains("uiShellController.hasTabBar()"));
        assertFalse(mainActivity.contains("uiShellController.prepareHorizontalContentTransition("));
        assertFalse(mainActivity.contains("uiShellController.hasHeader()"));
        assertFalse(mainActivity.contains("private void clearRemoteSourceAndNavigateNetworkPage("));
        assertTrue(mainActivity.contains("routeController.clearSelectedRemoteSource();"));
        assertFalse(mainActivity.contains("TAB_NETWORK.equals(selectedTab()) || TAB_SETTINGS.equals(selectedTab())"));
        assertFalse(mainActivity.contains("private void navigateSettingsPage(String page)"));
        assertFalse(mainActivity.contains("routeController.setSettingsPage(page);\r\n        renderAndPersistSelectedTab();")
                || mainActivity.contains("routeController.setSettingsPage(page);\n        renderAndPersistSelectedTab();"));
        assertFalse(labelFormatter.contains("val scrollState = SettingsListScrollState()"));
        assertTrue(settingsViewModel.contains("val scrollState = SettingsListScrollState()"));
        assertTrue(settingsViewModel.contains("fun scrollToTopOnNextRender()"));
        assertFalse(labelFormatter.contains("viewModel.renderCurrentPage(page, preferences, runtime)"));
        assertTrue(mainActivity.contains("settingsViewModel.renderPageFromHost("));
        assertFalse(labelFormatter.contains("scrollStateSink.publishSettingsScrollState(scrollState)"));
        assertFalse(mainActivity.contains("settingsPageRenderController.getScrollState()"));
        assertTrue(settingsScreen.contains("rememberLazyListState("));
        assertTrue(settingsScreen.contains("scrollState.save(listState)"));
        assertTrue(settingsScreen.contains("fun scrollToTop()"));
        assertFalse(nowPlayingViewModel.contains("PlaybackActionResultUi(player.snapshot(), null, false, false, true, true)"));
        assertFalse(nowPlaying.contains("animateScrollToItem"));
        assertFalse(exists("app/src/main/java/app/yukine/ScrollDirectionFrameLayout.java"));
        assertFalse(exists("app/src/main/java/app/yukine/ContentHostController.kt"));
        assertFalse(exists("app/src/main/java/app/yukine/ui/SearchBarController.kt"));
        assertFalse(trackListScreen.contains("Modifier.animateItem()"));
        assertFalse(collectionsScreen.contains("Modifier.animateItem()"));
        assertFalse(queueScreen.contains("Modifier.animateItem()"));
        assertFalse(playlistListScreen.contains("Modifier.animateItem()"));
        assertFalse(playlistTrackScreen.contains("Modifier.animateItem()"));
        assertFalse(shellController.contains("addVirtualContent"));
        assertFalse(shellController.contains("animateContentIfPending"));
    }

    @Test
    public void swipeAndWaveformRegressionsStayFixed() throws Exception {
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");
        String shellController = read("app/src/main/java/app/yukine/MainUiShellController.java");
        String nowBar = read("app/src/main/java/app/yukine/ui/NowBar.kt");
        String localWaveform = read("app/src/main/java/app/yukine/ui/PlaybackWaveform.kt");
        String streamingWaveform = read("app/src/main/java/app/yukine/playback/StreamingWaveformGenerator.java");
        String playbackService = read("app/src/main/java/app/yukine/playback/EchoPlaybackService.java");
        String waveformMergePolicy = read("app/src/main/java/app/yukine/playback/PlaybackWaveformMergePolicy.java");
        String sessionPlayer = read("app/src/main/java/app/yukine/playback/manager/PlaybackSessionPlayer.kt");

        assertFalse(shellController.contains("String selectedTab();"));
        assertTrue(mainActivity.contains("private String selectedTab()"));
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
        assertFalse(exists("app/src/main/java/app/yukine/ScrollDirectionFrameLayout.java"));
        assertFalse(shellController.contains("selectAdjacentTab"));
        assertFalse(shellController.contains("listener.onTabSelected(nextTab, false);"));
        assertFalse(shellController.contains("contentRouteHostController.selectedRoute()"));
        assertFalse(mainActivity.contains("SwipeGesturePolicy.verticalDistanceDominates"));
        assertFalse(mainActivity.contains("tracking && !horizontalSwipeLocked && !verticalScrollLocked && listener != null"));
        assertFalse(mainActivity.contains("verticalScrollLocked = true;"));
        assertFalse(mainActivity.contains("horizontalSwipeListener.onHorizontalSwipe(dxFromDown < 0f);"));

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
        String visualizationAnalyzer = read("app/src/main/java/app/yukine/playback/PlaybackVisualizationAnalyzer.kt");
        assertTrue(visualizationAnalyzer.contains("continuousCachedBytes(cacheKey)"));
        assertTrue(visualizationAnalyzer.contains("cacheDataSourceForTrack(waveformTrack)"));
        assertTrue(visualizationAnalyzer.contains("PlaybackWaveformMergePolicy.merge("));
        assertTrue(sessionPlayer.contains("isAppQueueNavigationCommand(command)"));
        assertTrue(sessionPlayer.contains("Player.COMMAND_SEEK_TO_NEXT"));
        assertTrue(sessionPlayer.contains("Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM"));
        assertTrue(sessionPlayer.contains("Player.COMMAND_SEEK_TO_PREVIOUS"));
        assertTrue(sessionPlayer.contains("Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM"));
        assertFalse(playbackService.contains("static boolean isAppQueueNavigationCommand(int command)"));
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
        assertTrue(nowBar.contains("rememberLiveWaveformPhase(playing)"));
        assertTrue(nowBar.contains("liveWaveformPeak(basePeak, index, waveformMotionPhase.value, playing)"));
        assertTrue(nowBar.contains("rememberInfiniteTransition(label = \"waveformPulse\")"));
        assertTrue(nowBar.contains("val generatedWidth = width * (generatedBars.toFloat() / barCount.toFloat())"));
        assertTrue(nowBar.contains("val pendingPlayedWidth = playedWidth - pendingPlayedStart"));
        assertTrue(nowBar.contains("val railHeight = max(2.dp.toPx(), trackHeight * 0.16f)"));
        assertFalse(shellController.contains("setOnTapListener"));
        assertFalse(shellController.contains("dispatchTouchEvent(MotionEvent event)"));
        assertFalse(shellController.contains("isPointInsideNowBar"));
        assertFalse(shellController.contains("collapseNowBarWaveform();"));
        assertFalse(nowBar.contains("nowBarBlankCollapseInteraction"));
        assertFalse(nowBar.contains(".matchParentSize()\n                        .clickable("));
        assertTrue(nowBar.contains("onClick = onCollapseWaveform"));

        String playbackProgress = read("app/src/main/java/app/yukine/ui/PlaybackProgressState.kt");
        assertTrue(playbackProgress.contains("while (position.value < duration)"));
        assertTrue(playbackProgress.contains("if (position.value != nextPosition)"));
        assertTrue(playbackProgress.contains("if (nextPosition >= duration)"));
    }

    @Test
    public void mainShellIsMountedThroughComposeNavHostRoot() throws Exception {
        String echoApp = read("app/src/main/java/app/yukine/EchoApp.kt");
        String shellController = read("app/src/main/java/app/yukine/MainUiShellController.java");
        String mainActivity = read("app/src/main/java/app/yukine/MainActivity.java");

        assertTrue(echoApp.contains("object EchoAppHost"));
        assertTrue(echoApp.contains("fun installNavHost(activity: ComponentActivity, mount: EchoNavHostMount)"));
        assertTrue(echoApp.contains("EchoNavHostBridge("));
        assertTrue(echoApp.contains("EchoTheme.EchoTheme"));
        assertFalse(echoApp.contains("AndroidView("));
        assertFalse(echoApp.contains("EchoLegacyRootFactory"));
        assertFalse(echoApp.contains("fun install(activity"));
        assertTrue(mainActivity.contains("EchoAppHost.installNavHost(this, new ActivityNavHostMount())"));
        assertFalse(shellController.contains("EchoAppHost.install(activity"));
        assertFalse(shellController.contains("private View createLegacyRootView("));
        assertFalse(shellController.contains("activity.setContentView(frame);"));
    }

    @Test
    public void streamingCapabilityResolverIsCoreModelBoundary() throws Exception {
        String resolver = read("core/model/src/main/java/app/yukine/streaming/StreamingCapabilityResolver.kt");
        String providerModels = read("core/model/src/main/java/app/yukine/streaming/StreamingProviderModels.kt");
        String contentModels = read("core/model/src/main/java/app/yukine/streaming/StreamingContentModels.kt");
        String trackMatchPolicy = read("core/model/src/main/java/app/yukine/streaming/StreamingTrackMatchPolicy.kt");
        String qualityPreference = read("core/common/src/main/java/app/yukine/streaming/StreamingQualityPreference.kt");
        String networkQuality = read("core/common/src/main/java/app/yukine/streaming/StreamingNetworkQuality.kt");
        String playlistLinkParser = read("core/common/src/main/java/app/yukine/streaming/StreamingPlaylistLinkParser.kt");
        String cookieHeaderParser = read("core/common/src/main/java/app/yukine/streaming/StreamingCookieHeaderParser.kt");
        String streamingContracts = read("feature/streaming/src/main/java/app/yukine/streaming/StreamingContracts.kt");

        assertFalse(exists("feature/streaming/src/main/java/app/yukine/streaming/StreamingCapabilityResolver.kt"));
        assertFalse(exists("feature/streaming/src/main/java/app/yukine/streaming/StreamingTrackMatchPolicy.kt"));
        assertFalse(exists("feature/streaming/src/main/java/app/yukine/streaming/StreamingQualityPreference.kt"));
        assertFalse(exists("feature/streaming/src/main/java/app/yukine/streaming/StreamingNetworkQuality.kt"));
        assertFalse(exists("feature/streaming/src/main/java/app/yukine/streaming/StreamingPlaylistLinkParser.kt"));
        assertFalse(exists("feature/streaming/src/main/java/app/yukine/streaming/StreamingCookieHeaderParser.kt"));
        assertTrue(resolver.contains("object StreamingCapabilityResolver"));
        assertTrue(providerModels.contains("data class StreamingProviderDescriptor"));
        assertTrue(providerModels.contains("data class StreamingProviderCapability"));
        assertTrue(contentModels.contains("data class StreamingTrack"));
        assertTrue(contentModels.contains("data class StreamingSearchResult"));
        assertTrue(contentModels.contains("data class StreamingPlaybackSource"));
        assertFalse(contentModels.contains("interface StreamingProvider"));
        assertTrue(trackMatchPolicy.contains("object StreamingTrackMatchPolicy"));
        assertTrue(qualityPreference.contains("object StreamingQualityPreference"));
        assertTrue(networkQuality.contains("object StreamingNetworkQuality"));
        assertTrue(playlistLinkParser.contains("object StreamingPlaylistLinkParser"));
        assertTrue(cookieHeaderParser.contains("object StreamingCookieHeaderParser"));
        assertTrue(streamingContracts.contains("interface StreamingProvider"));
        assertFalse(streamingContracts.contains("data class StreamingProviderDescriptor"));
        assertFalse(streamingContracts.contains("data class StreamingProviderCapability"));
        assertFalse(streamingContracts.contains("data class StreamingTrack"));
        assertFalse(streamingContracts.contains("data class StreamingSearchResult"));
        assertFalse(streamingContracts.contains("data class StreamingPlaybackSource"));
    }

    @Test
    public void trackDownloadFileNamePolicyIsCoreCommonBoundary() throws Exception {
        String policy = read("core/common/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt");
        String manager = read("app/src/main/java/app/yukine/TrackDownloadManager.kt");

        assertFalse(exists("app/src/main/java/app/yukine/TrackDownloadFileNamePolicy.kt"));
        assertTrue(policy.contains("object TrackDownloadFileNamePolicy"));
        assertTrue(policy.contains("fun audioFileName(track: Track, extension: String): String"));
        assertTrue(policy.contains("fun artworkFileName(track: Track, extension: String): String"));
        assertTrue(manager.contains("TrackDownloadFileNamePolicy.audioFileName"));
        assertTrue(manager.contains("TrackDownloadFileNamePolicy.artworkFileName"));
    }

    private static String read(String path) throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            for (String candidatePath : candidatePaths(path)) {
                Path candidate = current.resolve(candidatePath);
                if (Files.isRegularFile(candidate)) {
                    String content = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
                    String companion = readCompanion(candidate, candidatePath);
                    if (companion != null) {
                        return content + "\n" + companion;
                    }
                    return content;
                }
                Path appCandidate = current.resolve("echo-android").resolve(candidatePath);
                if (Files.isRegularFile(appCandidate)) {
                    String content = new String(Files.readAllBytes(appCandidate), StandardCharsets.UTF_8);
                    String companion = readCompanion(appCandidate, candidatePath);
                    if (companion != null) {
                        return content + "\n" + companion;
                    }
                    return content;
                }
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

    private static int countPrivatePlaybackFields(String source) {
        int count = 0;
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("private Playback") && trimmed.endsWith(";")) {
                count++;
            }
        }
        return count;
    }

    private static int countPrivatePlaybackFieldDeclarations(String source, boolean ownerOnly) {
        int count = 0;
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("private ") || !(trimmed.endsWith(";") || trimmed.endsWith("="))) {
                continue;
            }
            String[] parts = trimmed.replace(";", "").split("\\s+");
            if (parts.length < 3) {
                continue;
            }
            String type = "final".equals(parts[1]) ? parts[2] : parts[1];
            if (!type.startsWith("Playback")) {
                continue;
            }
            String simpleType = type.contains(".") ? type.substring(0, type.indexOf('.')) : type;
            if (!ownerOnly || simpleType.endsWith("Owner")) {
                count++;
            }
        }
        return count;
    }

    private static java.util.List<String> nonOverridePublicMethodLines(Path source) throws Exception {
        String[] lines = new String(Files.readAllBytes(source), StandardCharsets.UTF_8).split("\\R", -1);
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (!line.startsWith("    public ") || !line.contains("(")) {
                continue;
            }
            int previous = previousNonBlankLine(lines, index);
            if (previous < 0 || !"@Override".equals(lines[previous].trim())) {
                violations.add((index + 1) + ": " + line.trim());
            }
        }
        return violations;
    }

    private static int previousNonBlankLine(String[] lines, int index) {
        for (int previous = index - 1; previous >= 0; previous--) {
            if (!lines[previous].trim().isEmpty()) {
                return previous;
            }
        }
        return -1;
    }

    private static java.util.Set<String> kotlinClassLevelFunNames(String source) {
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (String line : source.split("\\R")) {
            if (!line.startsWith("    fun ")) {
                continue;
            }
            int nameStart = "    fun ".length();
            int nameEnd = line.indexOf('(', nameStart);
            if (nameEnd > nameStart) {
                names.add(line.substring(nameStart, nameEnd));
            }
        }
        return names;
    }

    private static java.util.Set<String> kotlinInterfaceFunNames(String source) {
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("fun ")) {
                continue;
            }
            int nameStart = "fun ".length();
            int nameEnd = trimmed.indexOf('(', nameStart);
            if (nameEnd > nameStart) {
                names.add(trimmed.substring(nameStart, nameEnd));
            }
        }
        return names;
    }

    private static java.util.Set<String> kotlinConstructorPropertyNames(String source) {
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("val ") && !trimmed.startsWith("var ")) {
                continue;
            }
            int nameStart = trimmed.indexOf(' ') + 1;
            int nameEnd = trimmed.indexOf(':', nameStart);
            if (nameEnd > nameStart) {
                names.add(trimmed.substring(nameStart, nameEnd));
            }
        }
        return names;
    }

    private static int countFiles(String directory, String glob) throws Exception {
        Path root = directory(directory);
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, glob)) {
            for (Path ignored : stream) {
                count++;
            }
        }
        return count;
    }

    private static java.util.List<Path> sourceFiles(String directory) throws Exception {
        Path root = directory(directory);
        java.util.List<Path> files = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".kt") || name.endsWith(".java");
                    })
                    .forEach(files::add);
        }
        files.sort(java.util.Comparator.comparing(Path::toString));
        return files;
    }

    private static java.util.Set<String> playbackSourceFileNamesContaining(String needle) throws Exception {
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (Path source : sourceFiles("app/src/main/java/app/yukine/playback")) {
            addFileNameIfContains(names, source, needle);
        }
        for (Path source : sourceFiles("feature/playback/src/main/java/app/yukine/playback")) {
            addFileNameIfContains(names, source, needle);
        }
        return names;
    }

    private static void addFileNameIfContains(
            java.util.Set<String> names,
            Path source,
            String needle
    ) throws Exception {
        String content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        if (content.contains(needle)) {
            names.add(source.getFileName().toString());
        }
    }

    private static void assertSourceDoesNotContain(Path source, String forbidden) throws Exception {
        String content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertFalse(source + " must not depend on " + forbidden, content.contains(forbidden));
    }

    private static void assertPlaybackSourceDoesNotDependOnActivityUi(String label, String content) {
        for (String forbidden : PLAYBACK_UI_BOUNDARY_FORBIDDEN_REFERENCES) {
            assertFalse(label + " must not depend on UI boundary " + forbidden, content.contains(forbidden));
        }
    }

    private static void assertViewModelDoesNotDependOnPlaybackServiceHost(Path source) throws Exception {
        assertSourceDoesNotContain(source, "EchoPlaybackService");
        assertSourceDoesNotContain(source, "PlaybackServiceHostPort");
        assertSourceDoesNotContain(source, "NowPlayingPlaybackServicePort");
        assertSourceDoesNotContain(source, "SettingsPlaybackServicePort");
        assertSourceDoesNotContain(source, "PlaybackServiceConnectionController");
        assertSourceDoesNotContain(source, "SettingsPlaybackServiceControls");
    }

    private static boolean isAllowedConcretePlaybackServiceBoundary(String normalizedPath) {
        return normalizedPath.endsWith("/app/src/main/java/app/yukine/LiveLyricsNotificationService.kt")
                || normalizedPath.endsWith("/app/src/main/java/app/yukine/NowPlayingPlaybackServiceStarter.kt")
                || normalizedPath.endsWith("/app/src/main/java/app/yukine/PlaybackServiceConnectionController.kt");
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue(signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue(bodyStart >= 0);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char c = source.charAt(index);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(signatureIndex, index + 1);
                }
            }
        }
        throw new AssertionError("Missing method body end for " + signature);
    }

    private static boolean exists(String path) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            for (String candidatePath : existenceCandidatePaths(path)) {
                if (Files.exists(current.resolve(candidatePath))) {
                    return true;
                }
                if (Files.exists(current.resolve("echo-android").resolve(candidatePath))) {
                    return true;
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private static String[] existenceCandidatePaths(String path) {
        if ("app/src/main/java/app/yukine/MainActivity.java".equals(path)) {
            return new String[]{path};
        }
        return candidatePaths(path);
    }

    private static Path directory(String path) throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path appCandidate = current.resolve("echo-android").resolve(path);
            if (Files.isDirectory(appCandidate)) {
                return appCandidate;
            }
            current = current.getParent();
        }
        throw new java.io.FileNotFoundException(path);
    }

    private static String[] candidatePaths(String path) {
        if ("app/src/main/java/app/yukine/MainActivity.java".equals(path)) {
            return new String[]{"app/src/main/java/app/yukine/MainActivity.kt"};
        }
        if ("app/src/main/java/app/yukine/data/EmbeddedArtwork.java".equals(path)) {
            return new String[]{
                    path,
                    "feature/data/src/main/java/app/yukine/data/EmbeddedArtwork.java",
                    "core/common/src/main/java/app/yukine/common/EmbeddedArtwork.java"
            };
        }
        if (path.startsWith("app/src/main/java/app/yukine/ui/")) {
            return new String[]{
                    path,
                    path.replace(
                            "app/src/main/java/app/yukine/ui/",
                            "feature/ui-common/src/main/java/app/yukine/ui/")
            };
        }
        if (path.startsWith("app/src/main/java/app/yukine/playback/")) {
            return new String[]{
                    path,
                    path.replace(
                            "app/src/main/java/app/yukine/playback/",
                            "feature/playback/src/main/java/app/yukine/playback/")
            };
        }
        if (path.startsWith("app/src/main/java/app/yukine/streaming/")) {
            return new String[]{
                    path,
                    path.replace(
                            "app/src/main/java/app/yukine/streaming/",
                            "feature/streaming/src/main/java/app/yukine/streaming/")
            };
        }
        if (path.startsWith("app/src/main/java/app/yukine/data/")) {
            return new String[]{
                    path,
                    path.replace(
                            "app/src/main/java/app/yukine/data/",
                            "feature/data/src/main/java/app/yukine/data/")
            };
        }
        if (path.startsWith("app/src/main/java/app/yukine/model/")) {
            return new String[]{
                    path,
                    path.replace(
                            "app/src/main/java/app/yukine/model/",
                            "core/model/src/main/java/app/yukine/model/")
            };
        }
        return new String[]{path};
    }

    private static String readCompanion(Path candidate, String candidatePath) throws Exception {
        if (!"app/src/main/java/app/yukine/MainActivity.java".equals(candidatePath)
                && !"app/src/main/java/app/yukine/MainActivity.kt".equals(candidatePath)) {
            return null;
        }
        Path base = candidate.getParent().resolve("MainActivityBase.java");
        if (!Files.isRegularFile(base)) {
            return null;
        }
        return new String(Files.readAllBytes(base), StandardCharsets.UTF_8);
    }

    private static void assertContainsUtf8Chinese(String source, String expected) {
        assertTrue(source.contains(expected));
        assertFalse(source.contains(utf8ReadAsGbk(expected)));
        assertFalse(source.contains("�"));
    }

    private static String utf8ReadAsGbk(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), java.nio.charset.Charset.forName("GBK"));
    }
}
