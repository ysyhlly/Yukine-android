package app.yukine

import androidx.activity.ComponentActivity
import javax.inject.Inject

/** App composition root. It assembles focused bindings but owns no lifecycle or feature policy. */
internal class MainActivityComposition @Inject constructor(
    private val dependencies: MainActivityDependencies
) {
    fun create(activity: ComponentActivity): MainActivityFeatureBindings {
        val deps = dependencies
        val viewModels = MainActivityViewModels.from(activity)
        viewModels.lyricsViewModel.bindTrackVisibilityGateway(object : LyricsTrackVisibilityGateway {
            override suspend fun load(): LyricsTrackVisibility {
                val preferences = dependencies.customLyricsRepository.loadDisplayPreferences()
                return LyricsTrackVisibility(
                    primary = preferences.primary,
                    translation = preferences.translation,
                    romanization = preferences.romanization
                )
            }

            override suspend fun save(visibility: LyricsTrackVisibility) {
                dependencies.customLyricsRepository.saveDisplayPreferences(
                    app.yukine.data.LyricsDisplayPreferences(
                        primary = visibility.primary,
                        translation = visibility.translation,
                        romanization = visibility.romanization
                    )
                )
            }
        })
        val settings = SettingsFeatureBinding(
            viewModels,
            deps.settingsStore,
            deps.loadSettingsPreferencesUseCase,
            deps.applySettingsPreferenceUseCase,
            deps.loadLyricsSettingsUseCase
        )
        val platform = PlatformFeatureBinding(
            activity,
            viewModels,
            deps.mainHandler,
            deps.executors,
            deps.settingsStore,
            deps.trackShareOperations,
            deps.trackDownloadManager,
            deps.resolveStreamingPlaybackUseCase,
            deps.libraryDeletionUseCase,
            deps.luoxueSourceStore,
            deps.customLyricsRepository,
            deps.repository
        )
        val navigation = NavigationFeatureBinding(
            activity,
            viewModels.navigationViewModel,
            settings.viewModel(),
            deps.settingsStore
        )
        val streaming = StreamingFeatureBinding(
            activity,
            viewModels,
            deps.mainHandler,
            deps.executors,
            deps.settingsStore,
            platform.statusMessages(),
            deps.streamingGatewaySettingsStore,
            deps.streamingRepositorySource,
            deps.streamingCacheRepository,
            deps.streamingPlaybackTaskScheduler,
            deps.resolveStreamingPlaybackUseCase,
            deps.streamingTrackMatchUseCase,
            deps.streamingLocalPlaylistOperations,
            deps.streamingPlaylistSyncStore,
            deps.luoxueTrackMetadataResolver,
            deps.repository
        )
        val favoriteSyncRepository = SharedPreferencesFavoriteSyncRepository(activity)
        val favoriteSyncLibrary = MusicLibraryUnifiedFavoriteLibrary(deps.repository)
        val favoriteSyncCoordinator = FavoriteSyncCoordinator(
            favoriteSyncRepository,
            StreamingFavoriteProviderAdapter(deps.streamingRepositorySource),
            favoriteSyncLibrary,
            deps.streamingTrackMatchUseCase,
            deps.favoriteSyncEventBus,
            AndroidFavoriteSyncNetworkPolicy(activity),
            FavoriteSyncCanonicalReconciler(favoriteSyncRepository, favoriteSyncLibrary)
        )
        val library = LibraryFeatureBinding(
            activity,
            viewModels,
            viewModels.navigationViewModel,
            settings.viewModel(),
            deps.settingsStore,
            navigation,
            platform.statusMessages(),
            deps.repository,
            deps.toggleFavoriteUseCase,
            deps.loadPlaylistTracksUseCase,
            deps.libraryCollectionGateway,
            deps.libraryImportGateway,
            deps.libraryDocumentGateway,
            deps.libraryPlaylistActionGateway,
            deps.homeDashboardRepository,
            deps.mainHandler,
            deps.recordingMatchRepository,
            LibraryMultiSourceSyncCoordinator(
                MusicLibraryMultiSourceSyncOperations(
                    deps.repository,
                    deps.streamingRepositorySource,
                    deps.streamingTrackMatchUseCase
                )
            ),
            favoriteSyncCoordinator
        )
        val playback = PlaybackFeatureBinding(
            activity,
            viewModels,
            deps.mainHandler,
            deps.settingsStore,
            deps.nowPlayingPlaybackServiceStarter,
            deps.playbackServiceCommandQueue,
            deps.resolveStreamingPlaybackUseCase,
            deps.luoxueTrackMetadataResolver,
            streaming.qualityPolicy(),
            platform.statusMessages()
        )
        playback.bindConnection(
            viewModels.lyricsViewModel,
            library.collectionsOwner(),
            streaming.viewModel(),
            library.viewModel(),
            library::sourceCandidatesFor,
            streaming::preResolveNext,
            streaming::recoverBuffering,
            streaming::resolveCurrentQueueTrackIfNeeded
        )
        playback.nowPlayingViewModel.bindLyricsVisibilitySetter(
            viewModels.lyricsViewModel::setTrackVisible
        )
        platform.bindFeatures(library, playback, streaming)
        val network = NetworkFeatureBinding(
            activity,
            viewModels,
            deps.networkActionUseCases,
            deps.settingsStore,
            platform.statusMessages(),
            navigation,
            library,
            playback
        )
        streaming.bindPlayback(
            playback,
            navigation,
            library.store(),
            viewModels.lyricsViewModel,
            network::confirmClearQueue
        )
        streaming.bindDialogs(
            library.collectionsOwner(),
            library.importOwner(),
            platform.luoxueSourceImportDialogController()
        )
        val streamingSearch = library.bindUi(
            playback,
            streaming,
            platform.documentPickerController(),
            platform.libraryFileDeleteLauncher(),
            platform.downloadRequestController(),
            platform.permissionController(),
            platform.luoxueSourceImportDialogController(),
            network::editStream,
            network::confirmClearPlayHistory
        )
        settings.bindFeatures(
            activity,
            deps.mainHandler,
            deps.executors,
            platform.statusMessages(),
            platform.uiShellController(),
            platform.customBackgroundAccentController(),
            navigation,
            library,
            playback,
            streaming,
            viewModels.lyricsViewModel,
            deps.lyricsLoader,
            platform.permissionController(),
            platform.documentPickerController(),
            platform.luoxueSourceImportDialogController(),
            platform.backupRestoreLauncher(),
            viewModels.navigationViewModel,
            deps.streamingGatewaySettingsStore,
            deps.luoxueSourceStore,
            deps.repository,
            { platform.importCurrentLyrics(playback) },
            platform::importLyricsDirectory,
            platform::showLyricsImportReport
        )
        network.bindUi(streamingSearch, platform.documentPickerController(), settings)
        library.bindPlaybackStateSources(playback, streaming, viewModels.lyricsViewModel)
        platform.bindPlaybackEffects(playback, navigation, library)
        val onboarding = OnboardingFeatureBinding(
            platform.permissionController(),
            platform.documentPickerController(),
            library,
            navigation,
            deps.repository,
            deps.settingsStore,
            platform.statusMessages(),
            deps.mainHandler
        )
        platform.attachOnboarding(onboarding.owner())
        return MainActivityFeatureBindings(
            viewModels = viewModels,
            platform = platform,
            settings = settings,
            navigation = navigation,
            streaming = streaming,
            library = library,
            playback = playback,
            network = network,
            onboarding = onboarding
        )
    }
}

/** Typed result of app composition; intentionally contains no forwarding methods. */
internal data class MainActivityFeatureBindings(
    val viewModels: MainActivityViewModels,
    val platform: PlatformFeatureBinding,
    val settings: SettingsFeatureBinding,
    val navigation: NavigationFeatureBinding,
    val streaming: StreamingFeatureBinding,
    val library: LibraryFeatureBinding,
    val playback: PlaybackFeatureBinding,
    val network: NetworkFeatureBinding,
    val onboarding: OnboardingFeatureBinding
)
