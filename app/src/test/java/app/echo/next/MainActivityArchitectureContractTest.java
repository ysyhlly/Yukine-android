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
        String actionsViewModel = read("app/src/main/java/app/echo/next/NetworkActionsViewModel.kt");
        String operationSink = read("app/src/main/java/app/echo/next/NetworkOperationSink.kt");
        String webDavUseCases = read("app/src/main/java/app/echo/next/WebDavSourceUseCases.kt");
        String networkUseCases = read("app/src/main/java/app/echo/next/NetworkLibraryUseCases.kt");

        assertTrue(mainActivity.contains("NetworkRequestController"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkRequestController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkDialogEventController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/NetworkActionsController.kt"));
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
        assertTrue(actionsViewModel.contains("internal class NetworkActionsViewModel"));
        assertTrue(actionsViewModel.contains(": ViewModel(), NetworkOperationSink"));
        assertTrue(actionsViewModel.contains("internal data class NetworkActionUseCases"));
        assertTrue(actionsViewModel.contains("private var useCases: NetworkActionUseCases?"));
        assertTrue(actionsViewModel.contains("fun bindUseCases(nextUseCases: NetworkActionUseCases?)"));
        assertTrue(actionsViewModel.contains("fun bindListener(nextListener: Listener?)"));
        assertFalse(actionsViewModel.contains("private val repository: MusicLibraryRepository"));
        assertFalse(actionsViewModel.contains("TestWebDavSourceUseCase(webDavOperations)"));
        assertFalse(actionsViewModel.contains("AddStreamUrlUseCase(networkLibraryOperations)"));
        assertTrue(actionsViewModel.contains("actions.addStreamUrlUseCase.execute(title, url)"));
        assertTrue(actionsViewModel.contains("actions.saveWebDavSourceUseCase.execute(sourceId, name, baseUrl, username, password, rootPath)"));
        assertTrue(actionsViewModel.contains("listener?.onStreamAdded(result.snapshot.cached, result.snapshot.favorites, status)"));
        assertTrue(actionsViewModel.contains("actions.syncWebDavSourceUseCase.execute(sourceId, sourceName)"));
        assertTrue(actionsViewModel.contains("listener?.onAllWebDavSourcesSynced(result.cached, result.favorites, result.status)"));
        assertTrue(mainActivity.contains("new NetworkActionUseCases("));
        assertTrue(mainActivity.contains("networkActionsViewModel.bindUseCases("));
        assertTrue(mainActivity.contains("networkActionsViewModel.bindListener("));
        assertTrue(mainActivity.contains("new NetworkActionsViewModel.Listener()"));
        assertTrue(mainActivity.contains("new MusicLibraryWebDavSourceOperations(repository)"));
        assertTrue(mainActivity.contains("new MusicLibraryNetworkLibraryOperations(repository)"));
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
    public void releaseDocsLinkPlaybackServiceStabilityMatrix() throws Exception {
        String readme = read("README.md");
        String releaseChecklist = read("docs/RELEASE_EXPERIENCE_CHECKLIST.md");
        String matrix = read("docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md");

        assertTrue(readme.contains("docs/PLAYBACK_SERVICE_STABILITY_MATRIX.md"));
        assertTrue(releaseChecklist.contains("PLAYBACK_SERVICE_STABILITY_MATRIX.md"));
        assertTrue(matrix.contains("ACTION_AUDIO_BECOMING_NOISY"));
        assertTrue(matrix.contains("媒体焦点抢占"));
        assertTrue(matrix.contains("来电/通话"));
        assertTrue(matrix.contains("后台被杀"));
        assertTrue(matrix.contains("超大队列"));
        assertTrue(matrix.contains("无效本地 URI"));
        assertTrue(matrix.contains("\u64ad\u653e\u4e2d\u65ad\u7f51"));
    }

    @Test
    public void mainActivityDelegatesStatusLocalizationToStatusMessageController() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String viewModel = read("app/src/main/java/app/echo/next/MainActivityViewModel.kt");
        String streamingSearchRenderController = read("app/src/main/java/app/echo/next/StreamingSearchRenderController.kt");
        String streamingSearchScreen = read("app/src/main/java/app/echo/next/ui/StreamingSearchScreen.kt");
        String statusController = read("app/src/main/java/app/echo/next/StatusMessageController.java");
        String appLanguage = read("app/src/main/java/app/echo/next/AppLanguage.java");

        assertTrue(mainActivity.contains("StatusMessageController statusMessageController"));
        assertTrue(mainActivity.contains("statusMessageController.setStatus(status)"));
        assertFalse(mainActivity.contains("localizeStatus("));
        assertTrue(statusController.contains("final class StatusMessageController"));
        assertTrue(statusController.contains("static String localize("));
        assertTrue(statusController.contains("loading.library"));
        assertTrue(statusController.contains("streaming.cookie.empty"));
        assertTrue(statusController.contains("streaming.choose.login.provider"));
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
        assertTrue(viewModel.contains("\"Streaming playlist $providerPlaylistId\""));
        assertFalse(viewModel.contains("\\u5a34\\u4f5a"));
        assertFalse(appLanguage.contains("\"Cookie is empty\""));
        assertFalse(appLanguage.contains("\"Cookie saved\""));
        assertFalse(viewModel.contains("姝屽崟瀵煎叆澶辫触"));
        assertFalse(viewModel.contains("鏃犳硶鍔犺浇璐︽埛姝屽崟"));
        assertFalse(viewModel.contains("\u6d41\u5a92\u4f53\u8bf7\u6c42\u5931\u8d25"));
        assertFalse(mainActivity.contains("\"此歌单暂无歌曲\""));
        assertTrue(mainActivity.contains("AppLanguage.text(languageMode, \"no.tracks.in.playlist\")"));
        assertFalse(mainActivity.contains("addLibraryModeAction(modes, \"歌曲\""));
        assertFalse(mainActivity.contains("playlist.trackCount + \" 首歌曲\""));
        assertFalse(mainActivity.contains("\"暂无歌单\""));
        assertTrue(mainActivity.contains("AppLanguage.text(languageMode, \"folders\")"));
        assertFalse(mainActivity.contains("private String trackCountLabel("));
        assertTrue(mainActivity.contains("CollectionRowStateFactory.trackCountLabel(playlist.trackCount, languageMode)"));
    }

    @Test
    public void localArtworkUsesEmbeddedPicturesOnly() throws Exception {
        String scanner = read("app/src/main/java/app/echo/next/data/MediaStoreMusicScanner.java");
        String importer = read("app/src/main/java/app/echo/next/data/DocumentMusicImporter.java");
        String embeddedArtwork = read("app/src/main/java/app/echo/next/data/EmbeddedArtwork.java");
        String artworkLoader = read("app/src/main/java/app/echo/next/ui/ArtworkLoader.kt");
        String track = read("app/src/main/java/app/echo/next/model/Track.java");

        assertFalse(scanner.contains("content://media/external/audio/albumart"));
        assertFalse(scanner.contains("albumArtUri(albumId)"));
        assertTrue(scanner.contains("EmbeddedArtwork.uriIfEmbeddedPicture(context, uri)"));
        assertTrue(importer.contains("EmbeddedArtwork.uriIfEmbeddedPicture(context, uri)"));
        assertTrue(embeddedArtwork.contains("retriever.getEmbeddedPicture()"));
        assertTrue(embeddedArtwork.contains("echo-embedded-artwork"));
        assertTrue(artworkLoader.contains("EmbeddedArtwork.isEmbeddedArtworkUri(uri)"));
        assertTrue(track.contains("sanitizeAlbumArtUri(albumArtUri)"));
        assertTrue(track.contains("text.startsWith(\"content://media/\") && text.contains(\"/audio/albumart\")"));
    }

    @Test
    public void playbackStateListenerStaysOutOfMainActivity() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String playbackStateEventController = read("app/src/main/java/app/echo/next/PlaybackStateEventController.kt");
        String playbackStateUpdateController = read("app/src/main/java/app/echo/next/PlaybackStateUpdateController.kt");
        String playbackRenderPolicy = read("app/src/main/java/app/echo/next/PlaybackRenderPolicy.kt");
        String playbackServiceConnectionController = read("app/src/main/java/app/echo/next/PlaybackServiceConnectionController.kt");
        String shellController = read("app/src/main/java/app/echo/next/MainUiShellController.java");
        String nowBarStateFactory = read("app/src/main/java/app/echo/next/NowBarStateFactory.kt");
        String nowPlayingStateFactory = read("app/src/main/java/app/echo/next/NowPlayingStateFactory.kt");
        String nowPlayingRenderController = read("app/src/main/java/app/echo/next/NowPlayingRenderController.kt");
        String nowPlayingOverlayController = read("app/src/main/java/app/echo/next/ui/NowPlayingOverlayController.kt");
        String nowPlayingViewModel = read("app/src/main/java/app/echo/next/NowPlayingViewModel.kt");
        String lyricsViewModel = read("app/src/main/java/app/echo/next/LyricsViewModel.kt");
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
        assertFalse(exists("app/src/main/java/app/echo/next/PlaybackActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/echo/next/NowBarStateFactory.java"));
        assertTrue(nowBarStateFactory.contains("internal object NowBarStateFactory"));
        assertTrue(nowBarStateFactory.contains("@JvmStatic"));
        assertTrue(nowBarStateFactory.contains("fun create("));
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
        assertTrue(nowPlayingOverlayController.contains("state.nowPlayingLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.previousLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.playLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.pauseLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.nextLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.queueLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.moreLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.addToPlaylistLabel"));
        assertTrue(nowPlayingOverlayController.contains("onAddToPlaylist.run()"));
        assertTrue(nowPlayingOverlayController.contains("state.playbackProgressLabel"));
        assertTrue(nowPlayingOverlayController.contains("state.playbackErrorMessage"));
        assertTrue(nowPlayingOverlayController.contains("PlaybackErrorBanner("));
        assertTrue(nowPlayingOverlayController.contains("onRetry = { onPlayPause.run() }"));
        assertTrue(nowPlayingOverlayController.contains("NowPlayingMoreMenu("));
        assertTrue(nowPlayingOverlayController.contains("DropdownMenu("));
        assertTrue(nowPlayingOverlayController.contains("DropdownMenuItem("));
        assertTrue(nowPlayingOverlayController.contains("initialState: NowPlayingUiState"));
        assertTrue(nowPlayingOverlayController.contains("private val eventHandler: NowPlayingEventHandler"));
        assertTrue(nowPlayingOverlayController.contains("state.value.overlayState"));
        assertTrue(nowPlayingOverlayController.contains("eventHandler.onEvent(NowPlayingEvent.AddToPlaylist)"));
        assertTrue(nowPlayingOverlayController.contains("eventHandler.onEvent(NowPlayingEvent.ToggleLyrics)"));
        assertTrue(nowPlayingOverlayController.contains("showingLyrics = true"));
        assertTrue(nowPlayingOverlayController.contains("onToggleLyrics.run()"));
        assertTrue(nowPlayingOverlayController.contains("onToggleView = onToggle"));
        assertTrue(nowPlayingViewModel.contains("sealed interface NowPlayingEvent"));
        assertTrue(nowPlayingViewModel.contains("data class NowPlayingUiState"));
        assertTrue(nowPlayingViewModel.contains("data class PlaybackActionResultUi"));
        assertTrue(nowPlayingViewModel.contains("interface NowPlayingPlaybackGateway"));
        assertTrue(nowPlayingViewModel.contains("class NowPlayingViewModel : ViewModel()"));
        assertTrue(nowPlayingViewModel.contains("fun bindPlaybackGateway(nextGateway: NowPlayingPlaybackGateway?)"));
        assertTrue(nowPlayingViewModel.contains("fun onEvent(event: NowPlayingEvent)"));
        assertTrue(nowPlayingViewModel.contains("fun playTrackList(tracks: List<Track>?, index: Int): PlaybackActionResultUi"));
        assertTrue(nowPlayingViewModel.contains("player.playQueue(tracks, index)"));
        assertTrue(nowPlayingViewModel.contains("fun precacheTrack(track: Track?)"));
        assertTrue(nowPlayingViewModel.contains("player.precacheTrack(track)"));
        assertTrue(nowPlayingViewModel.contains("fun appendToQueue(tracks: List<Track>?)"));
        assertTrue(nowPlayingViewModel.contains("player.appendToQueue(tracks)"));
        assertTrue(nowPlayingViewModel.contains("fun replaceCurrentTrackAndResume(track: Track?, positionMs: Long)"));
        assertTrue(nowPlayingViewModel.contains("player.replaceCurrentTrackAndResume(track, positionMs)"));
        assertTrue(nowPlayingViewModel.contains("player.startPlaybackService(EchoPlaybackService.ACTION_PREVIOUS)"));
        assertTrue(nowPlayingViewModel.contains("data class OpenAddToPlaylist(val track: Track)"));
        assertTrue(nowPlayingViewModel.contains("emitEffect(NowPlayingEffect.OpenAddToPlaylist(track))"));
        assertTrue(shellController.contains("void onNowPlayingEvent(NowPlayingEvent event);"));
        assertTrue(shellController.contains("listener.onNowPlayingEvent(event);"));
        assertFalse(shellController.contains("void onAddCurrentToPlaylist();"));
        assertFalse(shellController.contains("listener.onAddCurrentToPlaylist();"));
        assertTrue(mainActivity.contains("private NowPlayingViewModel nowPlayingViewModel;"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.bindPlaybackGateway(new ActivityNowPlayingPlaybackGateway())"));
        assertTrue(mainActivity.contains("private final class ActivityNowPlayingPlaybackGateway implements NowPlayingPlaybackGateway"));
        assertTrue(mainActivity.contains("playbackService.playQueue(new ArrayList<>(tracks), index)"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.precacheTrack(resolved)"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.appendToQueue(placeholders)"));
        assertTrue(mainActivity.contains("nowPlayingViewModel.replaceCurrentTrackAndResume(resolved.getTrack(), resolved.getPositionMs())"));
        assertFalse(mainActivity.contains("playbackService.precacheTrack(resolved)"));
        assertFalse(mainActivity.contains("playbackService.appendToQueue(placeholders)"));
        assertFalse(mainActivity.contains("playbackService.replaceCurrentTrackAndResume(resolved, snapshot.positionMs)"));
        assertFalse(mainActivity.contains("private PlaybackActionsController playbackActionsController"));
        assertFalse(mainActivity.contains("playbackActionsController."));
        assertTrue(mainActivity.contains("private void handleNowPlayingEvent(NowPlayingEvent event)"));
        assertTrue(mainActivity.contains("showAddToPlaylistDialog(((NowPlayingEffect.OpenAddToPlaylist) effect).getTrack());"));
        assertFalse(mainActivity.contains("private Track currentTrackForEffect("));
        assertFalse(nowPlayingOverlayController.contains("\"正在播放\""));
        assertFalse(nowPlayingOverlayController.contains("\"Close\""));
        assertFalse(nowPlayingOverlayController.contains("\"Lyrics\""));
        assertFalse(nowPlayingOverlayController.contains("\"Show lyrics\""));
        assertFalse(nowPlayingOverlayController.contains("\"Show artwork\""));
        assertFalse(nowPlayingOverlayController.contains("\"No lyrics found\""));
        assertFalse(nowPlayingOverlayController.contains("\"Previous\""));
        assertFalse(nowPlayingOverlayController.contains("\"Pause\""));
        assertFalse(nowPlayingOverlayController.contains("\"Play\""));
        assertFalse(nowPlayingOverlayController.contains("\"Next\""));
        assertFalse(nowPlayingOverlayController.contains("\"Queue\""));
        assertFalse(nowPlayingOverlayController.contains("\"Playback progress\""));
        assertTrue(nowPlayingOverlayController.contains("centeredLyricScrollOffset(viewportHeightPx)"));
        assertTrue(nowPlayingOverlayController.contains("onSizeChanged { viewportHeightPx = it.height }"));
        assertTrue(nowPlayingOverlayController.contains("ACTIVE_LYRIC_ESTIMATED_HEIGHT_PX"));
        assertFalse(exists("app/src/main/java/app/echo/next/LyricsController.java"));
        assertTrue(lyricsViewModel.contains("class LyricsViewModel : ViewModel()"));
        assertTrue(lyricsViewModel.contains("data class LyricsState"));
        assertTrue(lyricsViewModel.contains("enum class LyricsStatusKind"));
        assertTrue(lyricsViewModel.contains("fun interface LyricsLoader"));
        assertTrue(lyricsViewModel.contains("internal class LoadTrackLyricsUseCaseLyricsLoader"));
        assertTrue(lyricsViewModel.contains("withContext(Dispatchers.IO)"));
        assertTrue(lyricsViewModel.contains("LyricsStatusText.status"));
        assertFalse(lyricsViewModel.contains("new LyricsRepository()"));
        assertFalse(lyricsViewModel.contains("\"Loading lyrics\""));
        assertFalse(lyricsViewModel.contains("\"No local lyrics found\""));
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
        assertTrue(nowPlayingRenderController.contains("playbackStore: MainPlaybackStore,\n        lyricsState: LyricsState?,\n        languageMode: String = AppLanguage.MODE_ENGLISH")
                || nowPlayingRenderController.contains("playbackStore: MainPlaybackStore,\r\n        lyricsState: LyricsState?,\r\n        languageMode: String = AppLanguage.MODE_ENGLISH"));
        assertTrue(nowPlayingRenderController.contains("LyricsStatusText.status(languageMode, state.statusKind, state.loadedLineCount)"));
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
        String librarySearchUseCase = read("app/src/main/java/app/echo/next/LibrarySearchUseCase.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/MainLibraryStore.java"));
        assertTrue(libraryStore.contains("internal class MainLibraryStore"));
        assertTrue(libraryStore.contains("private val searchUseCase: LibrarySearchUseCase"));
        assertFalse(libraryStore.contains("MusicLibraryRepository"));
        assertTrue(libraryStore.contains("private val viewModel: MainActivityViewModel"));
        assertTrue(libraryStore.contains("return ArrayList(state().allTracks)"));
        assertTrue(libraryStore.contains("searchUseCase.execute(cachedTracks, searchQuery)"));
        assertTrue(libraryStore.contains("viewModel.updateVisibleTracks(searchUseCase.execute(allTracks(), query))"));
        assertTrue(librarySearchUseCase.contains("internal class LibrarySearchUseCase"));
        assertTrue(librarySearchUseCase.contains("operations.search(source, query)"));
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
        assertTrue(shellController.contains("new TabBarController(activity, localizedTabs(languageMode), visibleMainTab(initialRoute)"));
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
        String screen = read("app/src/main/java/app/echo/next/ui/TrackListScreen.kt");
        String queueScreen = read("app/src/main/java/app/echo/next/ui/QueueScreen.kt");
        String playlistScreen = read("app/src/main/java/app/echo/next/ui/PlaylistTrackScreen.kt");
        String collectionsScreen = read("app/src/main/java/app/echo/next/ui/CollectionsScreen.kt");
        String currentIndicator = read("app/src/main/java/app/echo/next/ui/TrackCurrentIndicator.kt");
        String rowStateFactory = read("app/src/main/java/app/echo/next/TrackRowStateFactory.kt");
        String rowKeyPolicy = read("app/src/main/java/app/echo/next/TrackRowKeyPolicy.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/TrackListRenderController.java"));
        assertTrue(controller.contains("internal class TrackListRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("private val viewModel: LibraryViewModel"));
        assertTrue(controller.contains("TrackRowStateFactory.trackRow("));
        assertTrue(controller.contains("viewModel.updateTrackList(title, rows)"));
        assertTrue(controller.contains("TrackListScreenFactory.create("));
        assertTrue(controller.contains("viewModel.trackList"));
        assertTrue(screen.contains("TrackCurrentIndicator(track.current)"));
        assertTrue(screen.contains("TrackMoreMenu(actions, labels)"));
        assertTrue(screen.contains("DropdownMenu("));
        assertTrue(screen.contains("EchoIconKind.More"));
        assertTrue(queueScreen.contains("TrackCurrentIndicator(track.current"));
        assertTrue(playlistScreen.contains("TrackCurrentIndicator(track.current"));
        assertTrue(collectionsScreen.contains("TrackCurrentIndicator(track.current"));
        assertTrue(currentIndicator.contains("internal fun TrackCurrentIndicator(active: Boolean"));
        assertFalse(screen.contains("private fun CurrentTrackIndicator"));
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
        assertTrue(controller.contains("private val viewModel: LibraryViewModel"));
        assertTrue(controller.contains("LibraryGrouping.groupTracks("));
        assertTrue(controller.contains("viewModel.updateLibraryGroups(title, groupRows)"));
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
        String collectionsViewModel = read("app/src/main/java/app/echo/next/CollectionsViewModel.kt");
        String documentPickerController = read("app/src/main/java/app/echo/next/DocumentPickerController.kt");
        String confirmationDialogController = read("app/src/main/java/app/echo/next/ConfirmationDialogController.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/CollectionsRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/DocumentPickerController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/ConfirmationDialogController.java"));
        assertTrue(controller.contains("internal class CollectionsRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun render("));
        assertTrue(controller.contains("private val viewModel: CollectionsViewModel"));
        assertTrue(controller.contains("CollectionsUiState("));
        assertTrue(controller.contains("CollectionsActions("));
        assertTrue(controller.contains("viewModel.updateCollections("));
        assertTrue(controller.contains("viewModel.updateScreen(state)"));
        assertTrue(controller.contains("CollectionsScreenFactory.create(context, viewModel.screen, actions)"));
        assertTrue(collectionsViewModel.contains("data class PlaylistDetailUiState"));
        assertTrue(collectionsViewModel.contains("val selectedPlaylist: PlaylistDetailUiState? = null"));
        assertTrue(collectionsViewModel.contains("val screen: StateFlow<CollectionsUiState>"));
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
        String sourcesViewModel = read("app/src/main/java/app/echo/next/NetworkSourcesViewModel.kt");
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
        assertTrue(menuRenderer.contains("SettingsScreenFactory.create(context, metrics, actions, title = text(languageMode, \"tab.network\"))"));
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
        assertTrue(sourcesRenderer.contains("private val viewModel: NetworkSourcesViewModel"));
        assertTrue(sourcesRenderer.contains("fun render(languageMode: String, remoteSources: List<RemoteSource>, allTracks: List<Track>)"));
        assertTrue(sourcesRenderer.contains("CollectionRowStateFactory.networkSourceRow("));
        assertTrue(sourcesRenderer.contains("viewModel.updateSources(title, remoteSources, rows)"));
        assertTrue(sourcesRenderer.contains("NetworkSourcesScreenFactory.create("));
        assertTrue(sourcesRenderer.contains("viewModel.screen"));
        assertTrue(sourcesViewModel.contains("data class NetworkSourcesUiState"));
        assertTrue(sourcesViewModel.contains("val screen: StateFlow<MainActivityNetworkSourcesUiState>"));
        assertTrue(sourcesEvents.contains("internal class NetworkSourcesEventController"));
        assertTrue(sourcesEvents.contains(": NetworkSourcesRenderController.Listener"));
        assertTrue(sourcesEvents.contains("private val routeController: MainRouteController"));
        assertTrue(sourcesEvents.contains("private val statePublisher: MainStatePublisher"));
        assertTrue(sourcesEvents.contains("requestController.syncRemoteSource(sourceId, librarySource.remoteSourceName(sourceId))"));
        assertTrue(sourcesEvents.contains("statePublisher.publishNetworkSources(title, rows)"));
        assertTrue(mainActivity.contains("NetworkSourcesEventController networkSourcesEventController"));
        assertTrue(mainActivity.contains("new NetworkSourcesRenderController(this, networkSourcesViewModel, networkSourcesEventController)"));
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
        String settingsViewModel = read("app/src/main/java/app/echo/next/SettingsViewModel.kt");
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
        assertTrue(pageRenderer.contains("private val viewModel: SettingsViewModel"));
        assertTrue(pageRenderer.contains("interface Listener"));
        assertTrue(pageRenderer.contains("private fun renderSettingsScreen("));
        assertTrue(pageRenderer.contains("viewModel.updatePage(title, metrics, actions)"));
        assertTrue(pageRenderer.contains("fun renderStreamingGateway(languageMode: String, endpoint: String, configured: Boolean)"));
        assertTrue(pageRenderer.contains("StreamingGatewaySettingsStore.EMULATOR_HOST_ENDPOINT"));
        assertTrue(pageRenderer.contains("StreamingGatewaySettingsStore.LOCALHOST_ENDPOINT"));
        assertTrue(pageRenderer.contains("renderSettingsScreen(text(languageMode, \"tab.settings\"), metrics, actions)"));
        assertTrue(settingsViewModel.contains("sealed interface SettingsItem"));
        assertTrue(settingsViewModel.contains("sealed interface SettingsEvent"));
        assertTrue(settingsViewModel.contains("interface SettingsGateway"));
        assertTrue(settingsViewModel.contains("data class SettingsUiState"));
        assertTrue(settingsViewModel.contains("class SettingsViewModel @JvmOverloads constructor("));
        assertTrue(settingsViewModel.contains("fun bindGateway(nextGateway: SettingsGateway?)"));
        assertTrue(settingsViewModel.contains("fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?)"));
        assertTrue(settingsViewModel.contains("fun bindAppliedListener(nextListener: SettingsAppliedListener?)"));
        assertTrue(settingsViewModel.contains("fun onEvent(event: SettingsEvent)"));
        assertTrue(pageRenderer.contains("@JvmStatic"));
        assertTrue(pageRenderer.contains("fun playbackSpeedLabel(speed: Float): String"));
        assertTrue(pageRenderer.contains("fun appVolumeLabel(volume: Float): String"));
        assertTrue(pageRenderer.contains("fun lyricsOffsetLabel(offsetMs: Long): String"));
        assertTrue(pageEvents.contains("internal class SettingsPageEventController"));
        assertTrue(pageEvents.contains(": SettingsPageRenderController.Listener"));
        assertTrue(pageEvents.contains("private val viewModel: SettingsViewModel"));
        assertTrue(pageEvents.contains("SettingsEvent.NavigateSettingsPage(page)"));
        assertTrue(pageEvents.contains("SettingsEvent.OpenNetworkSources"));
        assertTrue(pageEvents.contains("SettingsEvent.ApplyStreamingGatewayEndpoint(endpoint)"));
        assertFalse(pageEvents.contains("fun interface Navigator"));
        assertFalse(pageEvents.contains("interface LyricsActions"));
        assertFalse(pageEvents.contains("fun interface StreamingGatewayActions"));
        assertTrue(pageEvents.contains("override fun applyStreamingGatewayEndpoint(endpoint: String)"));
        assertTrue(pageEvents.contains("viewModel.onEvent(SettingsEvent.ApplyStreamingGatewayEndpoint(endpoint))"));
        assertTrue(mainActivity.contains("SettingsPageEventController settingsPageEventController"));
        assertTrue(mainActivity.contains("settingsViewModel.bindGateway(new ActivitySettingsGateway())"));
        assertTrue(mainActivity.contains("private final class ActivitySettingsGateway implements SettingsGateway"));
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
        String loadPreferenceUseCase = read("app/src/main/java/app/echo/next/LoadSettingsPreferencesUseCase.kt");
        String preferenceUseCase = read("app/src/main/java/app/echo/next/ApplySettingsPreferenceUseCase.kt");
        String settingsViewModel = read("app/src/main/java/app/echo/next/SettingsViewModel.kt");
        String playlistUseCases = read("app/src/main/java/app/echo/next/PlaylistActionUseCases.kt");
        String libraryViewModel = read("app/src/main/java/app/echo/next/LibraryViewModel.kt");
        String collectionUseCases = read("app/src/main/java/app/echo/next/LibraryCollectionUseCases.kt");
        String importUseCases = read("app/src/main/java/app/echo/next/LibraryImportUseCases.kt");
        String playlistExportController = read("app/src/main/java/app/echo/next/PlaylistExportController.kt");
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");

        assertFalse(exists("app/src/main/java/app/echo/next/MainSettingsStore.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/MainExecutors.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/SettingsActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/SettingsActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaylistActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/PlaylistExportController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/LibraryActionsController.java"));
        assertTrue(settingsStore.contains("internal class MainSettingsStore"));
        assertTrue(settingsStore.contains("private var themeMode: String = EchoTheme.MODE_SYSTEM"));
        assertTrue(settingsStore.contains("private var languageMode: String = AppLanguage.MODE_SYSTEM"));
        assertTrue(settingsStore.contains("fun load(preferences: LoadedSettingsPreferences)"));
        assertFalse(settingsStore.contains("MusicLibraryRepository"));
        assertFalse(settingsStore.contains("repository.loadThemeMode()"));
        assertTrue(loadPreferenceUseCase.contains("internal class LoadSettingsPreferencesUseCase"));
        assertTrue(loadPreferenceUseCase.contains("operations.loadThemeMode()"));
        assertTrue(loadPreferenceUseCase.contains("operations.loadConcurrentPlaybackEnabled()"));
        assertTrue(settingsStore.contains("EchoTheme.setMode(themeMode)"));
        assertTrue(settingsStore.contains("fun setPlaybackSpeed(playbackSpeed: Float)"));
        assertTrue(executors.contains("internal class MainExecutors"));
        assertTrue(executors.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(executors.contains("Executors.newFixedThreadPool(2)"));
        assertTrue(executors.contains("Executors.newFixedThreadPool(3)"));
        assertTrue(executors.contains("fun io(task: Runnable)"));
        assertTrue(executors.contains("fun shutdownNow()"));
        assertTrue(settingsViewModel.contains("interface SettingsAppliedListener"));
        assertTrue(settingsViewModel.contains("fun interface SettingsPreferenceGateway"));
        assertTrue(settingsViewModel.contains("fun bindPreferenceGateway(nextGateway: SettingsPreferenceGateway?)"));
        assertTrue(settingsViewModel.contains("fun bindAppliedListener(nextListener: SettingsAppliedListener?)"));
        assertTrue(settingsViewModel.contains("fun applyThemeMode(nextMode: String)"));
        assertTrue(settingsViewModel.contains("EchoTheme.normalizeMode(nextMode)"));
        assertTrue(settingsViewModel.contains("savePreference(SettingsPreferenceKey.ThemeMode, mode)"));
        assertFalse(settingsViewModel.contains("repository.saveThemeMode("));
        assertTrue(settingsViewModel.contains("fun applyPlaybackSpeed(speed: Float)"));
        assertTrue(settingsViewModel.contains("private fun normalizePlaybackSpeed(speed: Float): Float"));
        assertTrue(settingsViewModel.contains("fun applyLyricsOffset(offsetMs: Long)"));
        assertTrue(mainActivity.contains("settingsViewModel.bindPreferenceGateway"));
        assertTrue(mainActivity.contains("settingsViewModel.bindAppliedListener"));
        assertTrue(mainActivity.contains("new SettingsAppliedListener()"));
        assertFalse(mainActivity.contains("private SettingsActionsController settingsActionsController"));
        assertFalse(mainActivity.contains("settingsActionsController."));
        assertTrue(preferenceUseCase.contains("internal class ApplySettingsPreferenceUseCase"));
        assertTrue(preferenceUseCase.contains("SettingsPreferenceKey.ThemeMode -> operations.saveThemeMode"));
        assertTrue(preferenceUseCase.contains("SettingsPreferenceKey.LyricsOffsetMs -> operations.saveLyricsOffsetMs"));
        assertTrue(preferenceUseCase.contains("MusicLibrarySettingsPreferenceOperations"));
        assertTrue(libraryViewModel.contains("interface LibraryPlaylistActionGateway"));
        assertTrue(libraryViewModel.contains("fun addToDefaultPlaylist("));
        assertTrue(libraryViewModel.contains("fun createPlaylist("));
        assertTrue(libraryViewModel.contains("fun renamePlaylist("));
        assertTrue(libraryViewModel.contains("fun deletePlaylist("));
        assertTrue(libraryViewModel.contains("fun removeSelectedPlaylistTrack("));
        assertTrue(libraryViewModel.contains("fun moveSelectedPlaylistTrack("));
        assertTrue(libraryViewModel.contains("fun addTrackToPlaylist("));
        assertTrue(mainActivity.contains("libraryViewModel.bindPlaylistActionGateway"));
        assertTrue(mainActivity.contains("libraryViewModel.addToDefaultPlaylistJava"));
        assertTrue(mainActivity.contains("libraryViewModel.createPlaylistJava"));
        assertTrue(mainActivity.contains("libraryViewModel.renamePlaylistJava"));
        assertTrue(mainActivity.contains("libraryViewModel.deletePlaylistJava"));
        assertTrue(mainActivity.contains("libraryViewModel.removeSelectedPlaylistTrackJava"));
        assertTrue(mainActivity.contains("libraryViewModel.moveSelectedPlaylistTrackJava"));
        assertTrue(mainActivity.contains("libraryViewModel.addTrackToPlaylistJava"));
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
        assertTrue(libraryViewModel.contains("fun saveLibraryFavorite("));
        assertTrue(mainActivity.contains("libraryViewModel.bindCollectionGateway"));
        assertTrue(mainActivity.contains("libraryViewModel.loadCollectionsJava(selectedPlaylistId()"));
        assertTrue(mainActivity.contains("libraryViewModel.clearPlayHistoryJava"));
        assertFalse(mainActivity.contains("private LibraryActionsController libraryActionsController"));
        assertFalse(mainActivity.contains("libraryActionsController."));
        assertTrue(collectionUseCases.contains("internal class LoadLibraryCollectionsUseCase"));
        assertTrue(collectionUseCases.contains("operations.ensureDefaultPlaylist()"));
        assertTrue(collectionUseCases.contains("operations.loadRecentlyPlayed(PLAY_HISTORY_RECAP_LIMIT)"));
        assertTrue(collectionUseCases.contains("internal class ClearPlayHistoryUseCase"));
        assertTrue(collectionUseCases.contains("internal class SetLibraryFavoriteUseCase"));
        assertTrue(libraryViewModel.contains("interface LibraryImportGateway"));
        assertTrue(libraryViewModel.contains("interface LibraryDocumentGateway"));
        assertTrue(libraryViewModel.contains("fun loadLibrary("));
        assertTrue(libraryViewModel.contains("fun importAudioUris("));
        assertTrue(libraryViewModel.contains("fun importAudioTree("));
        assertTrue(libraryViewModel.contains("fun parseMissingAudioSpecs("));
        assertTrue(libraryViewModel.contains("fun importStreamM3u("));
        assertTrue(libraryViewModel.contains("fun importPlaylistM3u("));
        assertTrue(libraryViewModel.contains("fun exportPlaylist("));
        assertTrue(mainActivity.contains("libraryViewModel.bindImportGateway"));
        assertTrue(mainActivity.contains("libraryViewModel.bindDocumentGateway"));
        assertTrue(mainActivity.contains("libraryViewModel.loadLibraryJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importAudioUrisJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importAudioTreeJava("));
        assertTrue(mainActivity.contains("libraryViewModel.parseMissingAudioSpecsJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importStreamM3uJava("));
        assertTrue(mainActivity.contains("libraryViewModel.importPlaylistM3uJava("));
        assertTrue(mainActivity.contains("libraryViewModel.exportPlaylistJava("));
        assertTrue(mainActivity.contains("importStreamM3uTextUseCase.execute(playlistRead.text)"));
        assertTrue(mainActivity.contains("loadPlaylistExportTracksUseCase.execute(playlistId)"));
        assertFalse(mainActivity.contains("repository.refreshFromDevice()"));
        assertFalse(mainActivity.contains("repository.importAudioUris(uris)"));
        assertFalse(mainActivity.contains("repository.parseMissingAudioSpecs()"));
        assertFalse(mainActivity.contains("repository.importM3uTextWithResult("));
        assertTrue(importUseCases.contains("internal interface LibraryImportOperations"));
        assertTrue(importUseCases.contains("internal class LoadLibraryUseCase"));
        assertTrue(importUseCases.contains("internal class ImportAudioUrisUseCase"));
        assertTrue(importUseCases.contains("internal class ImportStreamM3uTextUseCase"));
        assertTrue(importUseCases.contains("internal class LoadPlaylistExportTracksUseCase"));
        assertTrue(mainActivity.contains("new MusicLibraryCollectionOperations(repository)"));
        assertTrue(mainActivity.contains("new MusicLibraryImportOperations(repository)"));
        assertFalse(mainActivity.contains("void onCollectionsLoaded(LibraryActionsController.CollectionsSnapshot snapshot)"));
        assertFalse(mainActivity.contains("void onPlayHistoryCleared(int removed)"));
        assertFalse(mainActivity.contains("void onFavoriteSaved()"));
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
    public void streamingActionsLiveInMainActivityViewModelAndGateway() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String handlers = read("app/src/main/java/app/echo/next/StreamingActionHandlers.kt");
        String importUseCase = read("app/src/main/java/app/echo/next/ImportStreamingPlaylistUseCase.kt");
        String resolveUseCase = read("app/src/main/java/app/echo/next/ResolveStreamingPlaybackUseCase.kt");
        String syncUseCase = read("app/src/main/java/app/echo/next/SyncStreamingPlaylistUseCase.kt");
        String linkUseCase = read("app/src/main/java/app/echo/next/GetStreamingPlaylistLinkUseCase.kt");
        String loginPlaylistUseCase = read("app/src/main/java/app/echo/next/EnsureStreamingLoginPlaylistUseCase.kt");
        String trackMatchUseCase = read("app/src/main/java/app/echo/next/StreamingTrackMatchUseCase.kt");
        String toggleFavoriteUseCase = read("app/src/main/java/app/echo/next/ToggleFavoriteUseCase.kt");
        String loadPlaylistTracksUseCase = read("app/src/main/java/app/echo/next/LoadPlaylistTracksUseCase.kt");
        String loadLyricsSettingsUseCase = read("app/src/main/java/app/echo/next/LoadLyricsSettingsUseCase.kt");
        String loadTrackLyricsUseCase = read("app/src/main/java/app/echo/next/LoadTrackLyricsUseCase.kt");
        String networkActionsViewModel = read("app/src/main/java/app/echo/next/NetworkActionsViewModel.kt");
        String mainActivityViewModel = read("app/src/main/java/app/echo/next/MainActivityViewModel.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingActionsController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingActionsController.kt"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingResolvedPlaybackController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingResolvedPlaybackController.kt"));
        assertTrue(handlers.contains("internal interface StreamingSearchActionHandler"));
        assertTrue(handlers.contains("internal interface StreamingAuthCallbackHandler"));
        assertTrue(handlers.contains("interface MainActivityStreamingActionGateway"));
        assertTrue(mainActivityViewModel.contains(": ViewModel(), StreamingSearchActionHandler, StreamingAuthCallbackHandler"));
        assertTrue(mainActivityViewModel.contains("fun bindStreamingActionGateway"));
        assertTrue(mainActivityViewModel.contains("StreamingCapabilityResolver.canSearch"));
        assertTrue(mainActivityViewModel.contains("StreamingCapabilityResolver.canAuth"));
        assertTrue(mainActivityViewModel.contains("StreamingCapabilityResolver.canPlayback"));
        assertTrue(mainActivityViewModel.contains("supportedSearchMediaTypes"));
        assertTrue(mainActivityViewModel.contains("sourceMessage(descriptor, \"streaming.search.unavailable\")"));
        assertTrue(mainActivityViewModel.contains("sourceMessage(descriptor, \"streaming.auth.unsupported\")"));
        assertTrue(mainActivityViewModel.contains("sourceMessage(descriptor, \"streaming.playback.unsupported\")"));
        assertTrue(mainActivityViewModel.contains("text(\"streaming.track.unavailable\")"));
        assertTrue(mainActivityViewModel.contains("streamingActionGateway?.playResolvedTrack(track)"));
        assertFalse(mainActivityViewModel.contains("\u93c6\u5099\u7b09"));
        assertFalse(mainActivityViewModel.contains("\u5a34\u4f78\u735f"));
        assertTrue(mainActivity.contains("viewModel.bindStreamingActionGateway(new ActivityStreamingActionGateway())"));
        assertTrue(mainActivity.contains("private final class ActivityStreamingActionGateway implements MainActivityStreamingActionGateway"));
        assertTrue(mainActivity.contains("return MainActivity.this.adaptiveStreamingQuality()"));
        assertTrue(mainActivity.contains("StreamingAuthLauncher.INSTANCE.launch(MainActivity.this, launch)"));
        assertTrue(mainActivity.contains("MainActivity.this.playTrackList(tracks, 0)"));
        assertFalse(mainActivity.contains("StreamingResolvedPlaybackController"));
        assertFalse(mainActivity.contains("StreamingActionsController"));
        assertTrue(importUseCase.contains("internal class ImportStreamingPlaylistUseCase"));
        assertTrue(importUseCase.contains("StreamingPlaybackAdapter.placeholderTrack"));
        assertTrue(importUseCase.contains("operations.importStreamingPlaylist(playlistName, placeholders)"));
        assertTrue(importUseCase.contains("operations.linkPlaylist(result.playlistId, provider, cleanProviderPlaylistId)"));
        assertTrue(mainActivity.contains("ImportStreamingPlaylistUseCase"));
        assertTrue(mainActivity.contains("importStreamingPlaylistUseCase.execute("));
        assertTrue(mainActivityViewModel.contains("fun interface StreamingLocalPlaylistImporter"));
        assertTrue(mainActivityViewModel.contains("fun importStreamingPlaylistToLocal("));
        assertTrue(mainActivityViewModel.contains("fun importStreamingLikedTracksToLocal("));
        assertTrue(mainActivityViewModel.contains("withContext(Dispatchers.IO)"));
        assertTrue(mainActivity.contains("viewModel.bindStreamingLocalPlaylistImporter"));
        assertTrue(mainActivity.contains("viewModel.importStreamingPlaylistToLocal(provider, providerPlaylistId"));
        assertTrue(mainActivity.contains("viewModel.importStreamingLikedTracksToLocal(provider, playlistName"));
        assertFalse(mainActivity.contains("final java.util.List<app.echo.next.streaming.StreamingTrack> finalStreamingTracks = streamingTracks"));
        assertFalse(mainActivity.contains("repository.importStreamingPlaylist(playlistName, placeholders)"));
        assertFalse(mainActivity.contains("streamingPlaylistSyncStore.linkPlaylist(\r\n                            result.playlistId"));
        assertFalse(mainActivity.contains("streamingPlaylistSyncStore.linkPlaylist(\n                            result.playlistId"));
        assertTrue(resolveUseCase.contains("internal class ResolveStreamingPlaybackUseCase"));
        assertTrue(resolveUseCase.contains("StreamingPlaybackAdapter.isUnresolvedStreamingTrack"));
        assertTrue(resolveUseCase.contains("StreamingPlaybackAdapter.providerName"));
        assertTrue(resolveUseCase.contains("fun metadataFor("));
        assertTrue(resolveUseCase.contains("fun replaceResolvedTrack("));
        assertTrue(resolveUseCase.contains("fun prepareRecovery("));
        assertTrue(resolveUseCase.contains("fun clearRecovery(key: String?)"));
        assertTrue(resolveUseCase.contains("interface StreamingPlaybackResolvePlanner : StreamingPreResolvePlanner"));
        assertTrue(mainActivity.contains("ResolveStreamingPlaybackUseCase"));
        assertTrue(mainActivityViewModel.contains("interface StreamingPlaybackTaskQueue"));
        assertTrue(mainActivityViewModel.contains("fun scheduleCurrentPlaybackRecovery(task: StreamingPlaybackTask)"));
        assertTrue(mainActivityViewModel.contains("fun scheduleCurrentUrlResolve(task: StreamingPlaybackTask)"));
        assertTrue(mainActivityViewModel.contains("fun bindStreamingPlaybackCoordinator("));
        assertTrue(mainActivityViewModel.contains("fun preResolveNextStreamingTrack("));
        assertTrue(mainActivityViewModel.contains("planner.prepareNextPreResolve(snapshot, queue)"));
        assertTrue(mainActivityViewModel.contains("planner.clearPreResolve(request.key)"));
        assertTrue(mainActivityViewModel.contains("fun resolveStreamingTrackListForPlayback("));
        assertTrue(mainActivityViewModel.contains("planner.prepare(tracks, index)"));
        assertTrue(mainActivityViewModel.contains("planner.replaceResolvedTrack(request, resolved)"));
        assertTrue(mainActivityViewModel.contains("fun recoverStreamingBuffering("));
        assertTrue(mainActivityViewModel.contains("planner.prepareRecovery(snapshot, selectedQuality, adaptiveQuality)"));
        assertTrue(mainActivityViewModel.contains("planner.clearRecovery(request.key)"));
        assertTrue(mainActivity.contains("viewModel.bindStreamingPlaybackCoordinator("));
        assertTrue(mainActivity.contains("private final class ActivityStreamingPlaybackTaskQueue implements StreamingPlaybackTaskQueue"));
        assertTrue(mainActivity.contains("viewModel.preResolveNextStreamingTrack("));
        assertTrue(mainActivity.contains("viewModel.resolveStreamingTrackListForPlayback("));
        assertTrue(mainActivity.contains("viewModel.recoverStreamingBuffering("));
        assertFalse(mainActivity.contains("StreamingPreResolveRequest request = resolveStreamingPlaybackUseCase.prepareNextPreResolve(snapshot, queue)"));
        assertFalse(mainActivity.contains("resolveStreamingPlaybackUseCase.clearPreResolve(request.getKey())"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, completion -> {\r\n            viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.NEXT_URL_RESOLVE, completion -> {\n            viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("ResolveStreamingPlaybackRequest request = resolveStreamingPlaybackUseCase.prepare(tracks, index)"));
        assertFalse(mainActivity.contains("resolveStreamingPlaybackUseCase.replaceResolvedTrack(request, resolved)"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, completion ->\r\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_URL_RESOLVE, completion ->\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("StreamingRecoveryRequest request = resolveStreamingPlaybackUseCase.prepareRecovery("));
        assertFalse(mainActivity.contains("resolveStreamingPlaybackUseCase.clearRecovery(request.getKey())"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, completion ->\r\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("streamingPlaybackTaskScheduler.schedule(StreamingPlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY, completion ->\n                viewModel.resolveStreamingTrackForPlayback"));
        assertFalse(mainActivity.contains("private app.echo.next.streaming.StreamingTrack streamingMetadataFor("));
        assertTrue(syncUseCase.contains("internal class SyncStreamingPlaylistUseCase"));
        assertTrue(syncUseCase.contains("StreamingPlaybackAdapter.placeholderTrack"));
        assertTrue(syncUseCase.contains("operations.syncStreamingPlaylist(link.localPlaylistId, placeholders)"));
        assertTrue(syncUseCase.contains("operations.markSynced(link.localPlaylistId)"));
        assertTrue(mainActivity.contains("SyncStreamingPlaylistUseCase"));
        assertTrue(mainActivity.contains("syncStreamingPlaylistUseCase.execute(link, streamingTracks)"));
        assertTrue(mainActivityViewModel.contains("fun interface StreamingLocalPlaylistSyncer"));
        assertTrue(mainActivityViewModel.contains("fun syncStreamingPlaylistToLocal("));
        assertTrue(mainActivity.contains("viewModel.bindStreamingLocalPlaylistSyncer"));
        assertTrue(mainActivity.contains("viewModel.syncStreamingPlaylistToLocal(link"));
        assertFalse(mainActivity.contains("viewModel.fetchUserLikedTracks(link.getProvider()"));
        assertFalse(mainActivity.contains("viewModel.fetchStreamingPlaylistTracks(link.getProvider(), link.getProviderPlaylistId()"));
        assertFalse(mainActivity.contains("repository.syncStreamingPlaylist(playlistId, placeholders)"));
        assertTrue(linkUseCase.contains("internal class GetStreamingPlaylistLinkUseCase"));
        assertTrue(linkUseCase.contains("operations.getLink(localPlaylistId)"));
        assertTrue(mainActivity.contains("GetStreamingPlaylistLinkUseCase"));
        assertTrue(mainActivity.contains("getStreamingPlaylistLinkUseCase.execute(playlistId)"));
        assertFalse(mainActivity.contains("streamingPlaylistSyncStore.getLink(playlistId)"));
        assertTrue(loginPlaylistUseCase.contains("internal class EnsureStreamingLoginPlaylistUseCase"));
        assertTrue(loginPlaylistUseCase.contains("operations.createPlaylist(playlistName)"));
        assertTrue(loginPlaylistUseCase.contains("operations.loadPlaylists()"));
        assertTrue(loginPlaylistUseCase.contains("operations.linkPlaylist(playlistId, provider, \"\")"));
        assertTrue(mainActivity.contains("EnsureStreamingLoginPlaylistUseCase"));
        assertTrue(mainActivityViewModel.contains("fun interface StreamingLoginPlaylistEnsurer"));
        assertTrue(mainActivityViewModel.contains("fun ensureStreamingLoginPlaylist("));
        assertTrue(mainActivity.contains("viewModel.bindStreamingLoginPlaylistEnsurer"));
        assertTrue(mainActivity.contains("ensureStreamingLoginPlaylistUseCase.execute(playlistName, provider)"));
        assertTrue(mainActivity.contains("viewModel.ensureStreamingLoginPlaylist(playlistName, provider"));
        assertFalse(mainActivity.contains("final app.echo.next.streaming.StreamingProviderName finalProvider = provider"));
        assertFalse(mainActivity.contains("EnsureStreamingLoginPlaylistResult result =\r\n                    ensureStreamingLoginPlaylistUseCase.execute(playlistName, finalProvider)"));
        assertFalse(mainActivity.contains("EnsureStreamingLoginPlaylistResult result =\n                    ensureStreamingLoginPlaylistUseCase.execute(playlistName, finalProvider)"));
        assertFalse(mainActivity.contains("repository.createPlaylist(playlistName)"));
        assertFalse(mainActivity.contains("repository.loadPlaylists()"));
        assertTrue(trackMatchUseCase.contains("internal class StreamingTrackMatchUseCase"));
        assertTrue(trackMatchUseCase.contains("StreamingPlaybackAdapter.providerName"));
        assertTrue(trackMatchUseCase.contains("operations.loadStreamingTrackMatch(track, provider.wireName)"));
        assertTrue(trackMatchUseCase.contains("operations.saveStreamingTrackMatch(track, provider.wireName, cleanTrackId)"));
        assertTrue(trackMatchUseCase.contains("fun heartbeatSeedCandidates("));
        assertTrue(trackMatchUseCase.contains("fun snapshotQueueForHeartbeat("));
        assertTrue(trackMatchUseCase.contains("private fun addHeartbeatSnapshotCandidates("));
        assertTrue(mainActivity.contains("StreamingTrackMatchUseCase"));
        assertTrue(mainActivityViewModel.contains("interface StreamingTrackMatchStore"));
        assertTrue(mainActivityViewModel.contains("fun directProviderTrackId(track: Track, provider: StreamingProviderName): String = \"\""));
        assertTrue(mainActivityViewModel.contains("fun loadStreamingProviderTrackId("));
        assertTrue(mainActivityViewModel.contains("fun streamingProviderTrackIdFor("));
        assertTrue(mainActivityViewModel.contains("fun saveStreamingProviderTrackId("));
        assertTrue(mainActivityViewModel.contains("fun resolveHeartbeatRecommendationSeed("));
        assertTrue(mainActivityViewModel.contains("private suspend fun resolveHeartbeatRecommendationSeedId("));
        assertTrue(mainActivityViewModel.contains("private suspend fun searchHeartbeatSeedMatch("));
        assertTrue(mainActivity.contains("viewModel.bindStreamingTrackMatchStore"));
        assertTrue(mainActivity.contains("return streamingTrackMatchUseCase.directProviderTrackId(track, provider)"));
        assertTrue(mainActivity.contains("viewModel.streamingProviderTrackIdFor("));
        assertTrue(mainActivity.contains("streamingTrackMatchUseCase.heartbeatSeedCandidates("));
        assertTrue(mainActivity.contains("streamingTrackMatchUseCase.snapshotQueueForHeartbeat("));
        assertTrue(mainActivity.contains("viewModel.resolveHeartbeatRecommendationSeed(provider, candidates"));
        assertFalse(mainActivity.contains("viewModel.loadStreamingProviderTrackId(track, provider"));
        assertFalse(mainActivity.contains("viewModel.resolveStreamingTrackMatch(provider, track"));
        assertFalse(mainActivity.contains("viewModel.saveStreamingProviderTrackId(track, provider, resolvedTrackId)"));
        assertFalse(mainActivity.contains("private void addHeartbeatSnapshotCandidates("));
        assertFalse(mainActivity.contains("private void addHeartbeatSeedCandidate("));
        assertFalse(mainActivity.contains("private void resolveHeartbeatSeedAt("));
        assertFalse(mainActivity.contains("private void resolveHeartbeatSeedBySearch("));
        assertFalse(mainActivity.contains("private void cacheHeartbeatSeedMatch("));
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
        String libraryViewModel = read("app/src/main/java/app/echo/next/LibraryViewModel.kt");
        assertTrue(libraryViewModel.contains("fun interface LibraryFavoriteWriter"));
        assertTrue(libraryViewModel.contains("gateway?.applyFavorite(track.id, nextFavorite)"));
        assertTrue(mainActivity.contains("libraryViewModel.bindFavoriteWriter"));
        assertTrue(mainActivity.contains("toggleFavoriteUseCase.execute(track, favorite)"));
        assertFalse(mainActivity.contains("private void toggleFavorite(Track track)"));
        assertFalse(mainActivity.contains("repository.setFavorite(track, favorite)"));
        assertTrue(loadPlaylistTracksUseCase.contains("internal class LoadPlaylistTracksUseCase"));
        assertTrue(loadPlaylistTracksUseCase.contains("operations.loadPlaylistTracks(playlistId)"));
        assertTrue(mainActivity.contains("LoadPlaylistTracksUseCase"));
        assertTrue(libraryViewModel.contains("data class PlayPlaylist"));
        assertTrue(libraryViewModel.contains("data class OpenPlaylist"));
        assertTrue(libraryViewModel.contains("loader.loadPlaylistTracks(playlistId)"));
        assertTrue(mainActivity.contains("new LibraryEvent.PlayPlaylist(playlist.id)"));
        assertTrue(mainActivity.contains("new LibraryEvent.OpenPlaylist(playlist.id, playlist.name)"));
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
        assertTrue(mainActivity.contains("new LoadTrackLyricsUseCaseLyricsLoader("));
        assertTrue(mainActivity.contains("new LoadTrackLyricsUseCase(new LyricsRepositoryLoadOperations())"));
        assertTrue(mainActivity.contains("lyricsViewModel.load(track, neteaseProviderTrackIdForLyrics(track))"));
        assertTrue(mainActivity.contains("lyricsState()"));
        assertFalse(mainActivity.contains("private LyricsController lyricsController"));
        assertTrue(networkActionsViewModel.contains("internal data class NetworkActionUseCases"));
        assertTrue(networkActionsViewModel.contains("private var useCases: NetworkActionUseCases?"));
        assertTrue(networkActionsViewModel.contains("actions.addStreamUrlUseCase.execute(title, url)"));
        assertTrue(networkActionsViewModel.contains("actions.syncWebDavSourceUseCase.execute(sourceId, sourceName)"));
        assertFalse(networkActionsViewModel.contains("MusicLibraryRepository"));
        assertFalse(networkActionsViewModel.contains("MusicLibraryWebDavSourceOperations("));
        assertFalse(networkActionsViewModel.contains("MusicLibraryNetworkLibraryOperations("));
        assertTrue(mainActivity.contains("new NetworkActionUseCases("));
        assertTrue(mainActivity.contains("new MusicLibraryWebDavSourceOperations(repository)"));
        assertTrue(mainActivity.contains("new MusicLibraryNetworkLibraryOperations(repository)"));
    }

    @Test
    public void streamingSearchRenderControllerIsKotlin() throws Exception {
        String mainActivity = read("app/src/main/java/app/echo/next/MainActivity.java");
        String controller = read("app/src/main/java/app/echo/next/StreamingSearchRenderController.kt");
        String eventController = read("app/src/main/java/app/echo/next/StreamingSearchEventController.kt");
        String authCallbackController = read("app/src/main/java/app/echo/next/StreamingAuthCallbackController.kt");
        String screen = read("app/src/main/java/app/echo/next/ui/StreamingSearchScreen.kt");

        assertFalse(exists("app/src/main/java/app/echo/next/StreamingSearchRenderController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingSearchEventController.java"));
        assertFalse(exists("app/src/main/java/app/echo/next/StreamingAuthCallbackController.java"));
        assertTrue(controller.contains("internal class StreamingSearchRenderController"));
        assertTrue(controller.contains("interface Listener"));
        assertTrue(controller.contains("fun interface LanguageProvider"));
        assertTrue(controller.contains("languageProvider.languageMode()"));
        assertTrue(controller.contains("StreamingSearchScreenFactory.create"));
        assertTrue(controller.contains("StreamingSearchLabels("));
        assertTrue(controller.contains("viewModel.streaming"));
        assertTrue(controller.contains("StreamingSearchActions"));
        assertTrue(mainActivity.contains("() -> settingsStore.languageMode()"));
        assertTrue(screen.contains("manual-account-connect"));
        assertFalse(screen.contains("manual-cookie"));
        assertFalse(screen.contains("provider-capabilities"));
        assertFalse(screen.contains("provider-health"));
        assertFalse(screen.contains("debug-title"));
        assertFalse(screen.contains("debug-log"));
        assertFalse(screen.contains("\"流媒体\""));
        assertFalse(screen.contains("\"返回\""));
        assertFalse(screen.contains("\"加载更多\""));
        assertFalse(screen.contains("\"没有找到流媒体结果\""));
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
        assertTrue(authCallbackController.contains("actionsController.handleAuthCallback("));
        assertTrue(authCallbackController.contains("intent?.data?.toString()"));
        assertTrue(authCallbackController.contains("StreamingWebAuthActivity.EXTRA_COOKIE_HEADER"));
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
        String nowPlayingViewModel = read("app/src/main/java/app/echo/next/NowPlayingViewModel.kt");
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
        assertTrue(settingsPage.contains("renderSettingsScreen(text(languageMode, \"tab.settings\"), metrics, actions)"));
        assertTrue(settingsScreen.contains("rememberLazyListState("));
        assertTrue(settingsScreen.contains("scrollState.save(listState)"));
        assertTrue(settingsScreen.contains("fun scrollToTop()"));
        assertFalse(nowPlayingViewModel.contains("PlaybackActionResultUi(player.snapshot(), null, false, false, true, true)"));
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
        assertTrue(playbackService.contains("isAppQueueNavigationCommand(command)"));
        assertTrue(playbackService.contains("Player.COMMAND_SEEK_TO_NEXT"));
        assertTrue(playbackService.contains("Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM"));
        assertTrue(playbackService.contains("Player.COMMAND_SEEK_TO_NEXT_WINDOW"));
        assertTrue(playbackService.contains("Player.COMMAND_SEEK_TO_PREVIOUS"));
        assertTrue(playbackService.contains("Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM"));
        assertTrue(playbackService.contains("Player.COMMAND_SEEK_TO_PREVIOUS_WINDOW"));
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
        assertTrue(shellController.contains("setOnTapListener"));
        assertTrue(shellController.contains("collapseNowBarWaveform();"));
        assertTrue(shellController.contains("public boolean dispatchTouchEvent(MotionEvent event)"));
        assertTrue(shellController.contains("boolean collapseAfterDispatch = false;"));
        assertTrue(shellController.contains("boolean handled = super.dispatchTouchEvent(event);"));
        assertTrue(scrollDirection.contains("boolean tapAfterDispatch = false;"));
        assertTrue(scrollDirection.contains("boolean handled = super.dispatchTouchEvent(ev);"));
        assertTrue(shellController.contains("downInsideNowBar = isPointInsideNowBar(downX, downY);"));
        assertTrue(shellController.contains("&& !isPointInsideNowBar(event.getRawX(), event.getRawY())"));
        assertTrue(shellController.contains("private boolean isPointInsideNowBar(float rawX, float rawY)"));
        assertFalse(nowBar.contains("nowBarBlankCollapseInteraction"));
        assertFalse(nowBar.contains(".matchParentSize()\n                        .clickable("));
        assertTrue(nowBar.contains("onClick = onCollapseWaveform"));

        String playbackProgress = read("app/src/main/java/app/echo/next/ui/PlaybackProgressState.kt");
        assertTrue(playbackProgress.contains("while (position.value < duration)"));
        assertTrue(playbackProgress.contains("if (position.value != nextPosition)"));
        assertTrue(playbackProgress.contains("if (nextPosition >= duration)"));
    }

    @Test
    public void mainShellIsMountedThroughEchoAppRoot() throws Exception {
        String echoApp = read("app/src/main/java/app/echo/next/EchoApp.kt");
        String shellController = read("app/src/main/java/app/echo/next/MainUiShellController.java");

        assertTrue(echoApp.contains("fun EchoApp("));
        assertTrue(echoApp.contains("EchoTheme.EchoTheme"));
        assertTrue(echoApp.contains("AndroidView("));
        assertTrue(echoApp.contains("object EchoAppHost"));
        assertTrue(echoApp.contains("activity.setContent"));
        assertTrue(shellController.contains("EchoAppHost.install(activity"));
        assertTrue(shellController.contains("private View createLegacyRootView("));
        assertFalse(shellController.contains("activity.setContentView(frame);"));
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
