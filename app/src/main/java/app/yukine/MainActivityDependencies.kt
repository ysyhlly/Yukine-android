package app.yukine

import android.os.Handler
import app.yukine.data.MusicLibraryRepository
import app.yukine.streaming.LuoxueSourceStore
import app.yukine.streaming.LuoxueTrackMetadataResolver
import app.yukine.streaming.cache.StreamingCacheRepository
import javax.inject.Inject

/** Immutable Hilt dependency set for the application entry point; contains no behavior. */
internal class MainActivityDependencies @Inject constructor(
    val mainHandler: Handler,
    val executors: MainExecutors,
    val luoxueSourceStore: LuoxueSourceStore,
    val luoxueTrackMetadataResolver: LuoxueTrackMetadataResolver,
    val streamingCacheRepository: StreamingCacheRepository,
    val streamingRepositorySource: StreamingRepositorySource,
    val streamingPlaybackTaskScheduler: StreamingPlaybackTaskScheduler,
    val resolveStreamingPlaybackUseCase: ResolveStreamingPlaybackUseCase,
    val streamingTrackMatchUseCase: StreamingTrackMatchUseCase,
    val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    val loadPlaylistTracksUseCase: LoadPlaylistTracksUseCase,
    val libraryCollectionGateway: LibraryCollectionGateway,
    val libraryImportGateway: LibraryImportGateway,
    val libraryDocumentGateway: LibraryDocumentGateway,
    val libraryPlaylistActionGateway: LibraryPlaylistActionGateway,
    val homeDashboardRepository: HomeDashboardRepository,
    val loadSettingsPreferencesUseCase: LoadSettingsPreferencesUseCase,
    val applySettingsPreferenceUseCase: ApplySettingsPreferenceUseCase,
    val streamingLocalPlaylistOperations: StreamingLocalPlaylistOperations,
    val loadLyricsSettingsUseCase: LoadLyricsSettingsUseCase,
    val lyricsLoader: LyricsLoader,
    val networkActionUseCases: NetworkActionUseCases,
    val streamingGatewaySettingsStore: StreamingGatewaySettingsStore,
    val repository: MusicLibraryRepository,
    val trackDownloadManager: TrackDownloadManager,
    val trackShareOperations: TrackShareOperations,
    val settingsStore: MainSettingsStore,
    val nowPlayingPlaybackServiceStarter: NowPlayingPlaybackServiceStarter,
    val playbackServiceCommandQueue: PlaybackServiceCommandQueue,
    val libraryDeletionUseCase: LibraryDeletionUseCase,
    val artistInfoRepository: ArtistInfoRepository
)
