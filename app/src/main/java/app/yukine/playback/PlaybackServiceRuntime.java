package app.yukine.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import app.yukine.R;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.PlaybackServiceHostPort;
import app.yukine.StreamingRepositorySource;
import app.yukine.ToggleFavoriteUseCase;
import app.yukine.playback.manager.PlaybackAudioEffectManager;
import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackPlayerFactory;
import app.yukine.playback.manager.PlaybackSessionPlayer;
import app.yukine.playback.manager.PlaybackLyricsManager;
import app.yukine.playback.manager.PlaybackMediaLibraryCallback;
import app.yukine.playback.manager.PlaybackNoisyReceiverManager;
import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackNotificationChannelOwner;
import app.yukine.playback.manager.PlaybackPositionManager;
import app.yukine.playback.manager.PlaybackProgressUpdateManager;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueRuntimeStateManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackQueueStoreImpl;
import app.yukine.playback.manager.PlaybackRecoveryScheduler;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;
import app.yukine.playback.manager.PlaybackSessionManager;
import app.yukine.playback.manager.PlaybackSleepTimerManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;
import app.yukine.playback.manager.PlaybackWifiLockManager;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.state.PlaybackStateListener;
import app.yukine.model.Track;
import app.yukine.streaming.StreamingPlaybackHeaderStore;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
@OptIn(markerClass = UnstableApi.class)
final class PlaybackServiceRuntime
        implements PlaybackServiceHostPort, PlaybackNotificationCommandOwner.PlaybackCommands {
    private static final String TAG = "PlaybackServiceRuntime";
    private final EchoPlaybackService service;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PlaybackMainHandlerSchedulerOwner playbackMainHandlerSchedulerOwner;
    private final PlaybackTaskScheduler playbackTaskScheduler =
            new PlaybackTaskScheduler("EchoPlaybackScheduler", Process.THREAD_PRIORITY_AUDIO);
    private final PlaybackTaskScheduler visualizationTaskScheduler =
            new PlaybackTaskScheduler("YukineVisualizationScheduler", Process.THREAD_PRIORITY_BACKGROUND);
    private final RealtimeBassDetector realtimeBassDetector = new RealtimeBassDetector();
    private final YukineRealtimeBassAudioProcessor realtimeBassAudioProcessor =
            new YukineRealtimeBassAudioProcessor(realtimeBassDetector);
    private final PlaybackStreamingDiagnostics streamingDiagnostics = new PlaybackStreamingDiagnostics();

    private ExoPlayer player;
    private final PlaybackPlayerStateOwner playbackPlayerStateOwner =
            PlaybackPlayerStateOwner.fromPlayerProvider(() -> player);
    private PlaybackQueueManager playbackQueueManager;
    private final PlaybackQueueStateOwner playbackQueueStateOwner =
            PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> playbackQueueManager);
    private final PlaybackQueueRuntimeStateManager playbackQueueRuntimeStateManager = new PlaybackQueueRuntimeStateManager();
    private final PlaybackQueueMutationOwner playbackQueueMutationOwner =
            PlaybackQueueMutationOwner.fromPlaybackQueueManager(() -> playbackQueueManager);
    private final PlaybackQueueNavigationOwner playbackQueueNavigationOwner =
            PlaybackQueueNavigationOwner.fromPlaybackQueueManager(
                    () -> playbackQueueManager,
                    this::onMirroredQueueReused
            );
    private final PlaybackQueueMirroredTransitionOwner playbackQueueMirroredTransitionOwner =
            PlaybackQueueMirroredTransitionOwner.fromPlaybackQueueManager(
                    () -> playbackQueueManager,
                    playbackQueueRuntimeStateManager::playerMirrorsQueue,
                    playbackQueueStateOwner::isQueueEmpty,
                    PlaybackServiceRuntime.this::applyCurrentTrackVolumeToPlayer
            );
    private final PlaybackQueueCompletionOwner playbackQueueCompletionOwner =
            PlaybackQueueCompletionOwner.fromPlaybackQueueManager(
                    () -> playbackQueueManager,
                    new PlaybackQueueCompletionOwner.CompletionBoundary() {
                        @Override
                        public void stopAndClear() {
                            PlaybackServiceRuntime.this.stopAndClear();
                        }

                        @Override
                        public void prepareCurrent(boolean playWhenReady) {
                            PlaybackServiceRuntime.this.prepareCurrent(playWhenReady);
                        }

                        @Override
                        public void stopAtEndOfQueue() {
                            PlaybackServiceRuntime.this.stopAtEndOfQueue();
                        }

                        @Override
                        public void skipToNext() {
                            PlaybackServiceRuntime.this.skipToNext();
                        }
                    }
            );
    private final PlaybackRuntimeStateManager playbackRuntimeStateManager =
            new PlaybackRuntimeStateManager(
                    PlaybackRuntimeStateManager.stateProviderFromPlaybackState(
                            () -> player,
                            playbackQueueRuntimeStateManager::playerMirrorsQueue,
                            playbackQueueStateOwner::currentTrack
                    )
            );
    private final PlaybackCurrentTrackPreparationRuntimeOwner playbackCurrentTrackPreparationRuntimeOwner =
            PlaybackCurrentTrackPreparationRuntimeOwner.fromRuntimeStateManager(playbackRuntimeStateManager);
    private final PlaybackAudioEffectManager audioEffectManager =
            new PlaybackAudioEffectManager(TAG);
    private PlaybackSessionManager playbackSessionManager;
    private PlaybackNotificationArtworkBridgeOwner.SessionRefresher playbackSessionRefresher;
    private app.yukine.playback.manager.LyricsPublisher playbackLyricsManager;
    private final Consumer<Boolean> statusBarLyricsEnabledAction =
            PlaybackLyricsSettingsStore.statusBarLyricsEnabledActionFromSupplier(() -> playbackLyricsManager);
    private final Consumer<Boolean> systemMediaLyricsTitleEnabledAction =
            PlaybackLyricsSettingsStore.systemMediaLyricsTitleEnabledActionFromSupplier(
                    () -> playbackLyricsManager
            );
    private PlaybackLyricsStateOwner playbackLyricsStateOwner;
    private PlaybackMediaLibraryCallback playbackMediaLibraryCallback;
    private PlaybackModeSettingsStore playbackModeSettingsStore;
    private PlaybackStatePublisher playbackStatePublisher;
    private PlaybackStateSnapshotOwner playbackStateSnapshotOwner;
    private PlaybackBufferingDiagnosticsRecorderOwner playbackBufferingDiagnosticsRecorderOwner;
    private PlaybackRecoveryDiagnosticsRecorderOwner playbackRecoveryDiagnosticsRecorderOwner;
    private PlaybackErrorRecoveryCommandOwner playbackErrorRecoveryCommandOwner;
    private PlaybackErrorRecoveryManager playbackErrorRecoveryManager;
    private PlaybackPlayHistoryRecorder playbackPlayHistoryRecorder;
    private final Runnable recordPlaybackStartHistoryAction =
            PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(
                    () -> playbackPlayHistoryRecorder,
                    () -> player != null && player.getPlayWhenReady(),
                    playbackQueueStateOwner::currentTrack
            );
    private PlaybackQueueCommandOwner playbackQueueCommandOwner;
    private PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner;
    private PlaybackMirroredQueueTrackMatcherOwner playbackMirroredQueueTrackMatcherOwner;
    private PlaybackPositionManager playbackPositionManager;
    private PlaybackNotificationManager playbackNotificationManager;
    private PlaybackNotificationForegroundOwner playbackNotificationForegroundOwner;
    private PlaybackNotificationCommandOwner playbackNotificationCommandOwner;
    private PlaybackNotificationStateOwner playbackNotificationStateOwner;
    private PlaybackNotificationArtworkSource playbackNotificationArtworkSource;
    private PlaybackControllerMediaItemsOwner playbackControllerMediaItemsOwner;
    private PlaybackSessionCommandOwner playbackSessionCommandOwner;
    private PlaybackCurrentTrackPreparationQueueOwner playbackCurrentTrackPreparationQueueOwner;
    private PlaybackCurrentTrackPreparationOwner playbackCurrentTrackPreparationOwner;
    private PlaybackSleepTimerCommandOwner playbackSleepTimerCommandOwner;
    private PlaybackSleepTimerManager playbackSleepTimerManager;
    private PlaybackRealtimeVisualizationOwner playbackRealtimeVisualizationOwner;
    private PlaybackBufferedProgressOwner playbackBufferedProgressOwner;
    private PlaybackVisualizationStateOwner playbackVisualizationStateOwner;
    private PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer;
    private PlaybackVisualizationCacheStateOwner playbackVisualizationCacheStateOwner;
    private PlaybackVisualizationCacheManager playbackVisualizationCacheManager;
    private PlaybackNotificationArtworkManager playbackNotificationArtworkManager;
    private PlaybackPrecacheStateOwner playbackPrecacheStateOwner;
    private PlaybackPrecacheManager playbackPrecacheManager;
    private PlaybackWarmupCoordinator playbackWarmupCoordinator;
    private PlaybackCrossfadeCommandOwner playbackCrossfadeCommandOwner;
    private PlaybackCrossfadeStateOwner playbackCrossfadeStateOwner;
    private PlaybackCrossfadeAdvanceManager playbackCrossfadeAdvanceManager;
    private PlaybackRecoveryScheduler playbackRecoveryScheduler;
    private final PlaybackCurrentTrackReplacementOwner playbackCurrentTrackReplacementOwner =
            PlaybackCurrentTrackReplacementOwner.fromPlaybackQueueManager(
                    () -> playbackQueueManager,
                    recovery -> {
                        if (playbackRecoveryDiagnosticsRecorderOwner != null) {
                            playbackRecoveryDiagnosticsRecorderOwner.record(recovery);
                        }
                    },
                    playWhenReady -> {
                        if (playbackRecoveryScheduler != null) {
                            playbackRecoveryScheduler.scheduleCurrentPlaybackRecovery(playWhenReady);
                        }
                    }
            );
    private PlaybackShutdownPlaybackResourcesOwner playbackShutdownPlaybackResourcesOwner;
    private PlaybackShutdownServiceResourcesOwner playbackShutdownServiceResourcesOwner;
    private PlaybackShutdownLifecycleResourcesOwner playbackShutdownLifecycleResourcesOwner;
    private PlaybackShutdownCoordinator playbackShutdownCoordinator;
    private PlaybackWifiLockManager playbackWifiLockManager;
    private final Runnable acquireWifiLockIfStreamingAction =
            PlaybackWifiLockManager.acquireIfStreamingAction(() -> playbackWifiLockManager);
    private final Runnable releaseWifiLockAction =
            PlaybackWifiLockManager.releaseAction(() -> playbackWifiLockManager);
    private PlaybackNoisyReceiverManager playbackNoisyReceiverManager;
    private PlaybackProgressUpdateCommandOwner playbackProgressUpdateCommandOwner;
    private PlaybackProgressUpdateManager playbackProgressUpdateManager;
    private final MusicLibraryRepository repository;
    private final StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    private final StreamingRepositorySource streamingRepositorySource;
    private final ToggleFavoriteUseCase toggleFavoriteUseCase;
    private PlaybackAudioEffectSettingsStore playbackAudioEffectSettingsStore;
    private PlaybackMediaSourceProvider mediaSourceProvider;
    private PlaybackPlayerFactory playerFactory;
    private PlaybackRuntimeSettingsStore playbackRuntimeSettingsStore;
    private PlaybackStreamingUrlRecovery playbackStreamingUrlRecovery;
    private final PlaybackTransitionStateManager playbackTransitionStateManager = new PlaybackTransitionStateManager();
    private volatile boolean appVisible;

    PlaybackServiceRuntime(
            EchoPlaybackService service,
            MusicLibraryRepository repository,
            StreamingPlaybackHeaderStore streamingPlaybackHeaderStore,
            StreamingRepositorySource streamingRepositorySource,
            ToggleFavoriteUseCase toggleFavoriteUseCase
    ) {
        this.service = service;
        this.repository = repository;
        this.streamingPlaybackHeaderStore = streamingPlaybackHeaderStore;
        this.streamingRepositorySource = streamingRepositorySource;
        this.toggleFavoriteUseCase = toggleFavoriteUseCase;
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (player == null) {
                return;
            }
            if (playbackState == Player.STATE_READY) {
                playbackCurrentTrackPreparationRuntimeOwner.markPlaybackReady();
                if (playbackErrorRecoveryManager != null) {
                    playbackErrorRecoveryManager.onPlaybackReady();
                }
                recordPlaybackStartHistoryAction.run();
            } else if (playbackState == Player.STATE_ENDED) {
                playAfterCompletion();
                return;
            }
            publishState();
            if (playbackState == Player.STATE_BUFFERING && player.getPlayWhenReady()) {
                publishBufferingState();
            }
            playbackProgressUpdateCommandOwner.startProgressUpdates();
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            if (player == null) {
                return;
            }
            int nextIndex = player.getCurrentMediaItemIndex();
            if (!playbackQueueMirroredTransitionOwner.canApplyMirroredTransition()) {
                playbackPlayerStateOwner.resetPositionEstimate();
                return;
            }
            PlaybackQueueManager.MirroredTransitionResult transition =
                    playbackQueueMirroredTransitionOwner.applyMirroredTransitionReason(nextIndex, reason);
            if (transition == null) {
                return;
            }
            playbackPlayerStateOwner.beginMediaItemPositionTransition(nextIndex, 0L);
            if (transition.getStopAfterAutomaticAdvance()) {
                stopAfterAutomaticAdvance(transition.getCompletedIndex());
                return;
            }
            Track track = playbackQueueStateOwner.currentTrack();
            playbackQueueMirroredTransitionOwner.prepareMirroredTransitionPlaybackState();
            if (track != null) {
                resetWaveformIfTrackChanged(track);
            }
            publishState();
            playbackProgressUpdateCommandOwner.startProgressUpdates();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            // Bind the streaming WiFi lock to the real playing state so every path that starts
            // playback (explicit play, auto-advance, queue tap, restore) keeps WiFi awake, and
            // every pause/stop releases it. acquire/release are idempotent (guarded by isHeld).
            if (isPlaying) {
                acquireWifiLockIfStreamingAction.run();
            } else {
                releaseWifiLockAction.run();
            }
            publishState();
            playbackProgressUpdateCommandOwner.startProgressUpdates();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false);
            if (playbackErrorRecoveryManager != null) {
                playbackErrorRecoveryManager.onPlayerError(error);
                return;
            }
            Log.w(TAG, "Playback failed for "
                    + playbackErrorRecoveryCommandOwner.debugTrack(playbackQueueStateOwner.currentTrack()), error);
            playbackErrorRecoveryCommandOwner.setErrorMessage("Unable to play this track.");
            publishState();
        }
    };

    @UnstableApi
    void create() {
        mediaSourceProvider = new PlaybackMediaSourceProvider(service, repository, streamingPlaybackHeaderStore);
        playbackCurrentTrackPreparationQueueOwner =
                PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager(
                        () -> playbackQueueManager,
                        mediaSourceProvider,
                        track -> playbackNotificationManager == null
                                ? null
                                : playbackNotificationManager.mediaMetadataForTrack(track)
                );
        playerFactory = new PlaybackPlayerFactory(service, realtimeBassAudioProcessor);
        playbackAudioEffectSettingsStore = PlaybackAudioEffectSettingsStore.fromRepository(repository);
        playbackAudioEffectSettingsStore.restore();
        new PlaybackNotificationChannelOwner(service).createNotificationChannel();
        playbackMainHandlerSchedulerOwner = new PlaybackMainHandlerSchedulerOwner(mainHandler);
        PlaybackQueueStore queueStore = new PlaybackQueueStoreImpl(repository);
        playbackModeSettingsStore = PlaybackModeSettingsStore.fromRepository(repository);
        playbackModeSettingsStore.restoreInto(playbackRuntimeStateManager);
        playbackRuntimeSettingsStore = PlaybackRuntimeSettingsStore.fromRepository(repository);
        playbackRuntimeSettingsStore.restoreInto(playbackRuntimeStateManager);
        playbackPlayHistoryRecorder = PlaybackPlayHistoryRecorder.fromRepository(
                repository,
                playbackTransitionStateManager
        );
        playbackPositionManager = new PlaybackPositionManager(
                queueStore,
                PlaybackPositionManager.stateProviderFromPlaybackState(
                        playbackQueueStateOwner::currentTrack,
                        playbackPlayerStateOwner::positionMs
                )
        );
        playbackSleepTimerCommandOwner = new PlaybackSleepTimerCommandOwner(
                PlaybackServiceRuntime.this,
                PlaybackServiceRuntime.this::publishState,
                () -> playbackSleepTimerManager,
                PlaybackServiceRuntime.this::pauseForSystemInterruption
        );
        playbackSleepTimerManager = new PlaybackSleepTimerManager(
                playbackMainHandlerSchedulerOwner,
                playbackSleepTimerCommandOwner
        );
        playbackStreamingUrlRecovery = new PlaybackStreamingUrlRecovery(
                streamingRepositorySource,
                task -> playbackTaskScheduler.schedule(
                        PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                        task
                ),
                task -> mainHandler.post(task),
                (expectedTrackId, refreshedTrack, positionMs) ->
                        playbackCurrentTrackReplacementOwner.replaceCurrentSourceAndResume(
                                expectedTrackId,
                                refreshedTrack,
                                positionMs
                        ),
                failedTrackId -> {
                    Track currentTrack = playbackQueueStateOwner.currentTrack();
                    if (currentTrack != null && currentTrack.id == failedTrackId) {
                        prepareCurrent(true);
                    }
                }
        );
        playbackErrorRecoveryCommandOwner = new PlaybackErrorRecoveryCommandOwner(
                playbackQueueStateOwner::currentTrack,
                playbackQueueStateOwner,
                PlaybackServiceRuntime.this::prepareCurrent,
                PlaybackServiceRuntime.this,
                playbackCurrentTrackPreparationRuntimeOwner::setErrorMessage,
                PlaybackServiceRuntime.this::publishState,
                (message, error) -> Log.w(TAG, message, error),
                failed -> playbackStreamingUrlRecovery != null
                        && playbackStreamingUrlRecovery.refresh(
                                failed,
                                playbackPlayerStateOwner.positionMs()
                        )
        );
        playbackErrorRecoveryManager = new PlaybackErrorRecoveryManager(
                playbackMainHandlerSchedulerOwner,
                playbackErrorRecoveryCommandOwner,
                mediaSourceProvider::isHttpTrack,
                1500L
        );
        playbackProgressUpdateCommandOwner = new PlaybackProgressUpdateCommandOwner(
                PlaybackServiceRuntime.this::publishState,
                PlaybackServiceRuntime.this::persistPlaybackPositionThrottled,
                () -> playbackProgressUpdateManager
        );
        playbackProgressUpdateManager = new PlaybackProgressUpdateManager(
                playbackMainHandlerSchedulerOwner,
                PlaybackProgressUpdateManager.stateProviderFromPlaybackState(
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                ),
                playbackProgressUpdateCommandOwner
        );
        playbackCrossfadeCommandOwner = new PlaybackCrossfadeCommandOwner(
                playbackTransitionStateManager::setFadeOutAdvancing,
                volume -> {
                    if (player != null) {
                        player.setVolume(volume);
                    }
                },
                playbackQueueNavigationOwner::skipToNextImmediately,
                () -> {
                    if (playbackRuntimeSettingsStore != null) {
                        playbackRuntimeSettingsStore.applyCurrentTrackVolumeToPlayer(playbackRuntimeStateManager);
                    }
                },
                () -> playbackCrossfadeAdvanceManager
        );
        playbackCrossfadeStateOwner = new PlaybackCrossfadeStateOwner(
                playbackTransitionStateManager::fadeOutAdvancing,
                () -> player != null,
                playbackPlayerStateOwner,
                () -> playbackModeSettingsStore == null
                        ? PlaybackRepeatMode.REPEAT_ALL
                        : playbackModeSettingsStore.repeatMode(playbackRuntimeStateManager),
                playbackQueueStateOwner,
                () -> playbackRuntimeSettingsStore == null
                        ? 1.0f
                        : playbackRuntimeSettingsStore.currentTrackVolume(playbackRuntimeStateManager)
        );
        playbackCrossfadeAdvanceManager = new PlaybackCrossfadeAdvanceManager(
                playbackMainHandlerSchedulerOwner,
                playbackCrossfadeStateOwner,
                playbackCrossfadeCommandOwner
        );
        playbackRecoveryScheduler = new PlaybackRecoveryScheduler(
                task -> playbackTaskScheduler.schedule(
                        PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                        task
                ),
                playbackMainHandlerSchedulerOwner,
                PlaybackServiceRuntime.this::prepareCurrent
        );
        createPlayerIfNeeded();
        playbackNotificationForegroundOwner = new PlaybackNotificationForegroundOwner(
                service::activityPendingIntent,
                service::serviceActionPendingIntent,
                service::startPlaybackForeground,
                () -> {
                    service.stopForeground(true);
                    service.stopSelf();
                }
        );
        playbackNotificationCommandOwner = PlaybackNotificationCommandOwner.fromNotificationOwners(
                () -> playbackStatePublisher,
                () -> playbackNotificationManager,
                () -> playbackNotificationManager != null
                        && playbackNotificationManager.hasNotificationWorthyState(),
                PlaybackServiceRuntime.this,
                playbackNotificationForegroundOwner::stopForegroundAndSelf
        );
        playbackNotificationStateOwner = new PlaybackNotificationStateOwner(
                playbackQueueStateOwner::isQueueEmpty,
                PlaybackNotificationStateOwner.playbackStateProviderFromPlaybackState(
                        playbackQueueStateOwner::currentTrack,
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                ),
                track -> toggleFavoriteUseCase != null && toggleFavoriteUseCase.isFavorite(track),
                () -> {
                    MediaLibrarySession session = playbackSessionManager == null ? null : playbackSessionManager.session();
                    return session == null ? null : session.getPlatformToken();
                }
        );
        playbackNotificationArtworkSource = PlaybackNotificationArtworkSource.fromSupplier(
                () -> playbackNotificationArtworkManager
        );
        playbackNotificationManager = new PlaybackNotificationManager(
                service,
                playbackNotificationForegroundOwner,
                playbackNotificationStateOwner,
                () -> playbackLyricsManager,
                playbackNotificationArtworkSource,
                playbackNotificationCommandOwner
        );
        playbackSessionRefresher = PlaybackNotificationArtworkBridgeOwner.sessionRefresherFromPlaybackSessionManager(
                () -> playbackSessionManager
        );
        playbackLyricsStateOwner = new PlaybackLyricsStateOwner(
                () -> appVisible,
                PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(
                        playbackQueueStateOwner::currentTrack,
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                )
        );
        playbackLyricsManager = new PlaybackLyricsManager(
                service,
                playbackLyricsStateOwner,
                playbackNotificationManager.lyricsNotificationBridge(playbackSessionRefresher)
        );
        PlaybackLyricsSettingsStore.fromRepository(repository).restoreInto(playbackLyricsManager);
        playbackQueueCommandOwner = new PlaybackQueueCommandOwner(
                PlaybackServiceRuntime.this::prepareCurrent,
                PlaybackServiceRuntime.this::publishState,
                PlaybackServiceRuntime.this,
                PlaybackQueueCommandOwner.conflatingQueuePersistence(
                        command -> playbackTaskScheduler.schedule(
                                PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE,
                                command
                        ),
                        queueStore::save
                )
        );
        playbackMirroredQueueTrackMatcherOwner =
                PlaybackMirroredQueueTrackMatcherOwner.fromMediaSourceProvider(
                        () -> player,
                        mediaSourceProvider
        );
        playbackQueueMirroredPlayerOwner = new PlaybackQueueMirroredPlayerOwner(
                PlaybackQueueMirroredPlayerOwner.fromPlaybackQueueManager(
                        playbackQueueRuntimeStateManager::playerMirrorsQueue,
                        () -> player != null,
                        () -> player == null ? -1 : player.getMediaItemCount(),
                        () -> playbackQueueManager,
                        playbackMirroredQueueTrackMatcherOwner
                ),
                () -> player != null,
                playbackCurrentTrackPreparationRuntimeOwner::setPreparing,
                playbackQueueStateOwner::currentTrack,
                PlaybackServiceRuntime.this::resetWaveformIfTrackChanged,
                PlaybackServiceRuntime.this::applyPlaybackModeAndParametersToPlayer,
                playbackPlayerStateOwner::beginMediaItemPositionTransition,
                (index, positionMs) -> player.seekTo(index, positionMs),
                playWhenReady -> player.setPlayWhenReady(playWhenReady),
                () -> player.play(),
                playbackQueueRuntimeStateManager::setPlayerMirrorsQueue,
                error -> Log.w(TAG, "Unable to reuse mirrored queue", error)
        );
        playbackQueueManager = new PlaybackQueueManager(
                queueStore,
                playbackQueueCommandOwner,
                playbackPositionManager,
                mediaSourceProvider,
                playbackQueueMirroredPlayerOwner,
                playbackRuntimeStateManager,
                playbackTransitionStateManager
        );
        playbackCurrentTrackPreparationOwner = PlaybackCurrentTrackPreparationOwner.fromMediaSourceProvider(
                mediaSourceProvider,
                playbackNotificationManager::mediaMetadataForTrack,
                playbackCurrentTrackPreparationQueueOwner,
                playbackCurrentTrackPreparationRuntimeOwner,
                PlaybackServiceRuntime.this::publishState,
                track -> Log.w(TAG, "Refusing to prepare empty uri for "
                        + playbackErrorRecoveryCommandOwner.debugTrack(track))
        );
        playbackMediaLibraryCallback = new PlaybackMediaLibraryCallback(
                PlaybackMediaLibraryDataSource.fromRepository(
                        service.getString(R.string.app_name),
                        repository,
                        mediaSourceProvider,
                        playbackNotificationManager::mediaMetadataForTrack
                )
        );
        playbackSessionManager = new PlaybackSessionManager(
                service,
                this::createSessionPlayer,
                playbackMediaLibraryCallback,
                service::activityPendingIntent
        );
        if (player != null) {
            playbackSessionManager.bind();
        }
        playbackBufferedProgressOwner = PlaybackBufferedProgressOwner.fromPlayerProvider(
                playbackPlayerStateOwner,
                () -> player
        );
        playbackVisualizationStateOwner = new PlaybackVisualizationStateOwner(
                () -> appVisible,
                playbackBufferedProgressOwner,
                PlaybackServiceRuntime.this::publishState
        );
        playbackVisualizationAnalyzer = new PlaybackVisualizationAnalyzer(
                service,
                visualizationTaskScheduler,
                playbackVisualizationStateOwner,
                mediaSourceProvider
        );
        playbackRealtimeVisualizationOwner =
                PlaybackRealtimeVisualizationOwner.fromRealtimeBassDetector(
                        playbackPlayerStateOwner,
                        realtimeBassDetector
                );
        playbackStateSnapshotOwner = new PlaybackStateSnapshotOwner(
                playbackQueueStateOwner,
                playbackPlayerStateOwner,
                PlaybackStateSnapshotOwner.fromRuntimeStateManagerProvider(() -> playbackRuntimeStateManager),
                playbackSleepTimerCommandOwner::sleepTimerRemainingMs,
                PlaybackStateSnapshotOwner.fromVisualizationAnalyzerProvider(() -> playbackVisualizationAnalyzer),
                playbackRealtimeVisualizationOwner,
                PlaybackRepeatMode.REPEAT_ALL
        );
        playbackVisualizationCacheStateOwner = new PlaybackVisualizationCacheStateOwner(
                () -> mainHandler,
                playbackQueueStateOwner::currentTrack,
                task -> visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, task)
        );
        playbackVisualizationCacheManager = new PlaybackVisualizationCacheManager(
                playbackVisualizationCacheStateOwner,
                mediaSourceProvider
        );
        playbackWarmupCoordinator = new PlaybackWarmupCoordinator(
                PlaybackPrecacheManager.precacheTrackActionFromSupplier(() -> playbackPrecacheManager),
                PlaybackVisualizationCacheManager.scheduleVisualizationCacheActionFromSupplier(
                        () -> playbackVisualizationCacheManager
                )
        );
        playbackShutdownServiceResourcesOwner = new PlaybackShutdownServiceResourcesOwner(
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackNoisyReceiverManager,
                        PlaybackNoisyReceiverManager::unregister
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackWarmupCoordinator,
                        PlaybackWarmupCoordinator::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackVisualizationAnalyzer,
                        PlaybackVisualizationAnalyzer::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackRecoveryScheduler,
                        PlaybackRecoveryScheduler::release
                ),
                PlaybackShutdownServiceResourcesOwner.shutdownPlaybackTaskSchedulers(
                        playbackTaskScheduler,
                        visualizationTaskScheduler
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackErrorRecoveryManager,
                        PlaybackErrorRecoveryManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackProgressUpdateManager,
                        PlaybackProgressUpdateManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackSleepTimerManager,
                        PlaybackSleepTimerManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackCrossfadeAdvanceManager,
                        PlaybackCrossfadeAdvanceManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackMainHandlerSchedulerOwner,
                        PlaybackMainHandlerSchedulerOwner::clearCallbacks
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackVisualizationCacheManager,
                        PlaybackVisualizationCacheManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackNotificationArtworkManager,
                        PlaybackNotificationArtworkManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackPrecacheManager,
                        PlaybackPrecacheManager::release
                ),
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackStatePublisher,
                        PlaybackStatePublisher::release
                )
        );
        playbackShutdownLifecycleResourcesOwner = new PlaybackShutdownLifecycleResourcesOwner(
                () -> PlaybackServiceRuntime.this.persistPlaybackPositionThrottled(true),
                PlaybackShutdownLifecycleResourcesOwner.playbackQueueLifecycleStoreFromQueueManager(() -> playbackQueueManager),
                PlaybackShutdownLifecycleResourcesOwner.playbackStateProviderFromPlaybackState(
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                ),
                playbackNotificationCommandOwner::hasNotificationWorthyState,
                () -> playbackNotificationCommandOwner.publishPlaybackNotification(true),
                service::clearPlaybackNotification
        );
        playbackShutdownPlaybackResourcesOwner = new PlaybackShutdownPlaybackResourcesOwner(
                PlaybackShutdownPlaybackResourcesOwner.releaseFrom(
                        () -> playbackLyricsManager,
                        app.yukine.playback.manager.LyricsPublisher::release
                ),
                PlaybackShutdownPlaybackResourcesOwner.releaseFrom(
                        () -> playbackWifiLockManager,
                        PlaybackWifiLockManager::release
                ),
                PlaybackServiceRuntime.this::releasePlaybackSession,
                PlaybackServiceRuntime.this::releasePlayer,
                () -> playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false),
                () -> playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false)
        );
        playbackShutdownCoordinator = new PlaybackShutdownCoordinator(
                playbackShutdownPlaybackResourcesOwner,
                playbackShutdownServiceResourcesOwner,
                playbackShutdownLifecycleResourcesOwner
        );
        playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(
                service,
                playbackQueueStateOwner::currentTrack,
                new PlaybackNotificationArtworkBridgeOwner(
                        playbackSessionRefresher,
                        playbackNotificationCommandOwner::publishPlaybackNotification
                )
        );
        playbackStatePublisher = new PlaybackStatePublisher(
                this::snapshot,
                playbackLyricsManager,
                PlaybackNotificationCommandOwner.notificationUpdaterFromNotificationManagerSupplier(
                        () -> playbackNotificationManager
                ),
                playbackNotificationArtworkSource,
                PlaybackStatePublisherWidgetOwner.fromContextProvider(() -> service)
        );
        playbackBufferingDiagnosticsRecorderOwner =
                PlaybackBufferingDiagnosticsRecorderOwner.fromStreamingDiagnosticsProvider(
                        () -> streamingDiagnostics
                );
        playbackRecoveryDiagnosticsRecorderOwner =
                PlaybackRecoveryDiagnosticsRecorderOwner.fromStreamingDiagnosticsProvider(
                        () -> streamingDiagnostics,
                        mediaSourceProvider::streamingQualityForTrack
                );
        playbackPrecacheStateOwner = new PlaybackPrecacheStateOwner(
                playbackQueueStateOwner::currentTrack,
                PlaybackPrecacheStateOwner.playerMediaItemSupplierFromPlayerSupplier(() -> player),
                () -> streamingDiagnostics
        );
        playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider(
                playbackPrecacheStateOwner,
                playbackQueueStateOwner::upcomingTracksForPrecache,
                mediaSourceProvider,
                playbackMainHandlerSchedulerOwner
        );
        if (playbackQueueManager != null) {
            playbackQueueManager.restorePlaybackQueue();
        }
        playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
        playbackLyricsManager.bind();
        playbackNoisyReceiverManager = new PlaybackNoisyReceiverManager(
                new PlaybackNoisyReceiverRegistrarOwner(service),
                PlaybackNoisyReceiverManager.actionsFromPlaybackState(
                        playbackPlayerStateOwner::isPlaying,
                        PlaybackServiceRuntime.this::pauseForSystemInterruption
                )
        );
        playbackNoisyReceiverManager.register();
        android.net.wifi.WifiManager wifiManager =
                (android.net.wifi.WifiManager) service.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
        android.net.wifi.WifiManager.WifiLock wifiLock = null;
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "echo:playback");
        }
        playbackWifiLockManager = new PlaybackWifiLockManager(
                PlaybackWifiLockOwner.fromWifiLock(wifiLock),
                playbackQueueStateOwner::currentTrack,
                mediaSourceProvider::isHttpTrack
        );
        publishState();
    }

    @UnstableApi
    private Player createSessionPlayer() {
        if (playbackSessionCommandOwner == null) {
            playbackControllerMediaItemsOwner = new PlaybackControllerMediaItemsOwner(
                    (mediaItems, startIndex, startPositionMs) -> playbackMediaLibraryCallback == null
                            ? null
                            : playbackMediaLibraryCallback.controllerQueueForMediaItems(
                            mediaItems,
                            startIndex,
                            startPositionMs
                    ),
                    playbackQueueMutationOwner
            );
            playbackSessionCommandOwner = new PlaybackSessionCommandOwner(
                    PlaybackServiceRuntime.this,
                    PlaybackServiceRuntime.this::seekTo,
                    PlaybackServiceRuntime.this::setRepeatMode,
                    playbackControllerMediaItemsOwner,
                    new PlaybackSessionCommandOwner.StateProvider() {
                        @Override
                        public Track currentTrack() {
                            return playbackQueueStateOwner.currentTrack();
                        }

                        @Override
                        public long positionMs() {
                            return playbackPlayerStateOwner.positionMs();
                        }

                        @Override
                        public long sessionPositionMs() {
                            return playbackPlayerStateOwner.sessionPositionMs();
                        }

                        @Override
                        public long durationMs() {
                            return playbackPlayerStateOwner.durationMs();
                        }

                    },
                    playbackNotificationManager::mediaMetadataForTrack
            );
        }
        return new PlaybackSessionPlayer(player, playbackSessionCommandOwner);
    }

    MediaLibrarySession session() {
        return playbackSessionManager == null ? null : playbackSessionManager.session();
    }

    void handleServiceAction(String action) {
        if (playbackNotificationManager != null) {
            playbackNotificationManager.handleServiceAction(action);
        }
    }

    void handleTaskRemoved() {
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.handleTaskRemoved();
        } else {
            persistPlaybackPositionThrottled(true);
            persistPlaybackQueueState();
            savePlaybackResumeRequested(
                    playbackPlayerStateOwner.isPlaying() || playbackCurrentTrackPreparationRuntimeOwner.preparing()
            );
            if (playbackNotificationCommandOwner != null) {
                playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
            }
        }
    }

    void destroy() {
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.handleServiceDestroyed();
        } else {
            persistPlaybackPositionThrottled(true);
        }
    }

    public void registerListener(PlaybackStateListener listener) {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.registerListener(listener);
            return;
        }
        if (listener != null) {
            listener.onPlaybackStateChanged(snapshot());
        }
    }

    public void unregisterListener(PlaybackStateListener listener) {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.unregisterListener(listener);
        }
    }

    public void setAppVisible(boolean visible) {
        appVisible = visible;
        if (playbackLyricsManager != null) {
            playbackLyricsManager.onAppVisibilityChanged();
        }
        if (visible) {
            if (playbackNotificationCommandOwner != null) {
                playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
            }
        }
    }

    public void playQueue(List<Track> tracks, int startIndex) {
        playbackQueueMutationOwner.playQueue(tracks, startIndex, C.TIME_UNSET);
    }

    public void appendToQueue(List<Track> tracks) {
        playbackQueueMutationOwner.appendToQueue(tracks);
    }

    public void play() {
        if (player == null) {
            if (playbackQueueManager != null
                    && playbackQueueManager.prepareCurrentForExplicitPlay()) {
                playbackQueueManager.clearPausedPlaybackPosition();
            } else {
                playFirstQueuedTrack();
            }
            return;
        }
        if (playbackCurrentTrackPreparationRuntimeOwner.preparing()) {
            return;
        }
        Track track = playbackQueueStateOwner.currentTrack();
        if (track == null) {
            playFirstQueuedTrack();
            return;
        }
        if (player.getMediaItemCount() == 0) {
            if (playbackQueueManager != null
                    && playbackQueueManager.prepareCurrentForExplicitPlay()) {
                playbackQueueManager.clearPausedPlaybackPosition();
            } else {
                playFirstQueuedTrack();
            }
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0L);
            playbackPlayerStateOwner.setPositionEstimate(0L);
        }
        boolean wasPlaying = playbackPlayerStateOwner.isPlaying();
        player.play();
        if (!wasPlaying && playbackQueueManager != null) {
            playbackQueueManager.clearPausedPlaybackPosition();
        }
        savePlaybackResumeRequested(true);
        acquireWifiLockIfStreamingAction.run();
        publishState();
        playbackProgressUpdateCommandOwner.startProgressUpdates();
    }

    private void playFirstQueuedTrack() {
        playbackQueueNavigationOwner.playFirstQueuedTrack();
    }

    public void pause() {
        pause(true);
    }

    private void pauseForSystemInterruption() {
        pause(false);
    }

    private void pause(boolean persistForUser) {
        playbackCrossfadeCommandOwner.cancelCrossfadeAdvance();
        boolean wasPlaying = player != null && playbackPlayerStateOwner.isPlaying();
        if (wasPlaying) {
            player.pause();
        }
        savePlaybackResumeRequested(false);
        releaseWifiLockAction.run();
        if (playbackQueueManager != null) {
            if (persistForUser) {
                playbackQueueManager.persistPausedPlaybackPosition();
            } else if (wasPlaying) {
                playbackQueueManager.clearPausedPlaybackPosition();
            }
        }
        publishState();
    }

    public void seekTo(long positionMs) {
        if (player == null || playbackCurrentTrackPreparationRuntimeOwner.preparing()) {
            return;
        }
        try {
            long targetPositionMs = Math.max(0L, positionMs);
            player.seekTo(targetPositionMs);
            playbackPlayerStateOwner.setPositionEstimate(targetPositionMs);
            publishState();
        } catch (IllegalStateException ignored) {
            playbackErrorRecoveryCommandOwner.setErrorMessage("Playback is not ready.");
            publishState();
        }
    }

    public void skipToNext() {
        if (playbackCrossfadeCommandOwner.startFadeOutThenNext()) {
            return;
        }
        playbackQueueNavigationOwner.skipToNextImmediately();
    }

    public void skipToPrevious() {
        if (positionMs() > 3000L) {
            seekTo(0L);
            return;
        }
        playbackQueueNavigationOwner.skipToPrevious();
    }

    public List<Track> queueSnapshot() {
        return playbackQueueStateOwner.queueSnapshot();
    }

    public int queueSize() {
        return playbackQueueStateOwner.queueSize();
    }

    public Track queueTrackAt(int index) {
        return playbackQueueStateOwner.trackAt(index);
    }

    public List<Track> queueWindowFrom(int startIndex, int maxCount) {
        int size = playbackQueueStateOwner.queueSize();
        if (size <= 0 || maxCount <= 0) {
            return java.util.Collections.emptyList();
        }
        int count = Math.min(maxCount, size);
        int safeStart = Math.floorMod(startIndex, size);
        List<Track> tracks = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            Track track = playbackQueueStateOwner.trackAt((safeStart + offset) % size);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    public void moveQueueTrack(int fromIndex, int toIndex) {
        playbackQueueMutationOwner.moveQueueTrack(fromIndex, toIndex);
    }

    public PlaybackStreamingDiagnostics.Snapshot streamingDiagnostics() {
        return streamingDiagnostics.snapshot();
    }

    public void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        playbackCurrentTrackReplacementOwner.replaceCurrentTrackAndResume(replacement, positionMs);
    }

    @Override
    public void replaceCurrentSourceAndResume(long expectedTrackId, Track replacement, long positionMs) {
        playbackCurrentTrackReplacementOwner.replaceCurrentSourceAndResume(
                expectedTrackId,
                replacement,
                positionMs
        );
    }

    public void removeTracksById(Set<Long> trackIds) {
        playbackQueueMutationOwner.removeTracksById(trackIds);
    }

    @Override
    public void warmPlaybackTrack(Track track) {
        if (playbackWarmupCoordinator != null) {
            playbackWarmupCoordinator.warmup(track);
        }
    }

    public void retainTracksById(Set<Long> trackIdsToKeep) {
        playbackQueueMutationOwner.retainTracksById(trackIdsToKeep);
    }

    public void clearQueue() {
        playbackQueueMutationOwner.clearQueue();
    }

    public void toggleCurrentFavorite() {
        Track track = playbackQueueStateOwner.currentTrack();
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            publishState();
        }
    }

    public void restoreLastPlayback(boolean playWhenRestored) {
        PlaybackQueueManager.RestorePlaybackResult restoreResult = playbackQueueManager == null
                ? PlaybackQueueManager.RestorePlaybackResult.empty()
                : playbackQueueManager.restoreLastPlayback(playWhenRestored);
        if (restoreResult == null) {
            restoreResult = PlaybackQueueManager.RestorePlaybackResult.empty();
        }
        if (restoreResult.getShouldCreatePlayer()) {
            createPlayerIfNeeded();
        }
        if (!restoreResult.getShouldPrepare()) {
            publishState();
            return;
        }
        prepareCurrent(restoreResult.getPlayWhenReady());
        if (playWhenRestored && playbackQueueManager != null) {
            playbackQueueManager.clearPausedPlaybackPosition();
        }
    }

    public void replaceQueuedTrack(Track replacement) {
        playbackQueueMutationOwner.replaceQueuedTrack(replacement);
    }

    public void updateQueuedTrackArtwork(long trackId, android.net.Uri artworkUri) {
        playbackQueueMutationOwner.updateQueuedTrackArtwork(trackId, artworkUri);
    }

    public void replaceQueuedTracks(List<Track> replacements) {
        playbackQueueMutationOwner.replaceQueuedTracks(replacements);
    }

    public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        playbackQueueMutationOwner.replaceQueuedTrackById(oldTrackId, replacement);
    }

    public PlaybackStateSnapshot snapshot() {
        return playbackStateSnapshotOwner == null
                ? PlaybackStateSnapshot.empty()
                : playbackStateSnapshotOwner.snapshot();
    }

    public float realtimeBeat() {
        return playbackRealtimeVisualizationOwner == null ? 0f : playbackRealtimeVisualizationOwner.beat();
    }

    public float[] realtimeBands() {
        return playbackRealtimeVisualizationOwner == null ? new float[0] : playbackRealtimeVisualizationOwner.bands();
    }

    public void setShuffleEnabled(boolean enabled) {
        if (playbackModeSettingsStore != null) {
            playbackModeSettingsStore.setShuffleEnabled(playbackRuntimeStateManager, enabled);
        }
        publishState();
    }

    public void setRepeatMode(int mode) {
        if (playbackModeSettingsStore != null) {
            playbackModeSettingsStore.setRepeatMode(playbackRuntimeStateManager, mode);
        }
        publishState();
    }

    public void cycleRepeatMode() {
        if (playbackModeSettingsStore != null) {
            playbackModeSettingsStore.cycleRepeatMode(playbackRuntimeStateManager);
        }
        publishState();
    }

    public void setPlaybackSpeed(float speed) {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.setPlaybackSpeed(playbackRuntimeStateManager, speed);
        }
        publishState();
    }

    public float playbackSpeed() {
        return playbackRuntimeSettingsStore == null
                ? 1.0f
                : playbackRuntimeSettingsStore.playbackSpeed(playbackRuntimeStateManager);
    }

    public void setAppVolume(float volume) {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.setAppVolume(playbackRuntimeStateManager, volume);
        }
        publishState();
    }

    public float appVolume() {
        return playbackRuntimeSettingsStore == null
                ? 1.0f
                : playbackRuntimeSettingsStore.appVolume(playbackRuntimeStateManager);
    }

    public void setConcurrentPlaybackEnabled(boolean enabled) {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.setConcurrentPlaybackEnabled(playbackRuntimeStateManager, enabled);
        }
    }

    public boolean concurrentPlaybackEnabled() {
        return playbackRuntimeSettingsStore != null
                && playbackRuntimeSettingsStore.concurrentPlaybackEnabled(playbackRuntimeStateManager);
    }

    public void setStatusBarLyricsEnabled(boolean enabled) {
        statusBarLyricsEnabledAction.accept(enabled);
    }

    public void setSystemMediaLyricsTitleEnabled(boolean enabled) {
        systemMediaLyricsTitleEnabledAction.accept(enabled);
    }

    public void setPlaybackRestoreEnabled(boolean enabled) {
        if (playbackQueueManager != null) {
            playbackQueueManager.setPlaybackRestoreEnabled(enabled);
        }
    }

    public void setReplayGainEnabled(boolean enabled) {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.setReplayGainEnabled(playbackRuntimeStateManager, enabled);
        }
        publishState();
    }

    public AudioEffectSettings audioEffectSettings() {
        return playbackAudioEffectSettingsStore == null
                ? AudioEffectSettings.DEFAULT
                : playbackAudioEffectSettingsStore.current();
    }

    public void applyAudioEffectSettings(AudioEffectSettings settings) {
        AudioEffectSettings appliedSettings = playbackAudioEffectSettingsStore == null
                ? (settings == null ? AudioEffectSettings.DEFAULT : settings)
                : playbackAudioEffectSettingsStore.apply(settings);
        audioEffectManager.bind(player, appliedSettings);
        publishState();
    }

    public void startSleepTimerMinutes(int minutes) {
        playbackSleepTimerCommandOwner.startSleepTimerMinutes(minutes);
    }

    public void cancelSleepTimer() {
        playbackSleepTimerCommandOwner.cancelSleepTimer(true);
    }

    public long sleepTimerRemainingMs() {
        return playbackSleepTimerCommandOwner.sleepTimerRemainingMs();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareCurrent(final boolean playWhenReady) {
        Track track = playbackQueueStateOwner.currentTrack();
        if (track == null) {
            return;
        }
        PlaybackCurrentTrackPreparationOwner.PreparedTrack preparedTrack =
                playbackCurrentTrackPreparationOwner.prepareCurrentTrack(track);
        if (!preparedTrack.playable()) {
            return;
        }
        prepareMirroredQueue(playWhenReady, preparedTrack);
        return;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareMirroredQueue(
            final boolean playWhenReady,
            PlaybackCurrentTrackPreparationOwner.PreparedTrack preparedTrack
    ) {
        final long startPositionMs = preparedTrack.startPositionMs();
        if (seekExistingMirroredQueue(playWhenReady, startPositionMs)) {
            return;
        }
        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                playbackCurrentTrackPreparationQueueOwner.queuePreparationForNewPlayer();
        Track track = queuePreparation.currentTrack();
        if (track == null) {
            return;
        }
        List<MediaSource> mediaSources = queuePreparation.mirroredQueueMediaSources();
        if (mediaSources == null || mediaSources.isEmpty()) {
            prepareSingleTrack(preparedTrack.track(), preparedTrack.mediaSource(), playWhenReady, startPositionMs);
            return;
        }
        playbackCurrentTrackPreparationRuntimeOwner.beginPreparing();
        createPlayerIfNeeded();
        playbackTransitionStateManager.setLastMarkedTrack(null);
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        applyPlaybackParametersToPlayer();
        // A rebuilt queue starts a different media item. Do not let the previous item's
        // interpolation be treated as a real seek position while the new source buffers.
        playbackPlayerStateOwner.resetPositionEstimate();
        player.clearMediaItems();
        player.setMediaSources(mediaSources, queuePreparation.startIndex(), Math.max(0L, startPositionMs));
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(true);
        // Repeat-all only maps to Media3 REPEAT_MODE_ALL while the player mirrors the queue.
        // Reapply after changing that fact so a previous single-track REPEAT_MODE_ONE cannot leak
        // into a list whose Now Bar already reports list repeat.
        applyPlaybackModeToPlayer();
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            if (startPositionMs > 0L) {
                playbackPlayerStateOwner.setPositionEstimate(startPositionMs);
            }
            playbackCurrentTrackPreparationQueueOwner.consumeRestoredPositionAfterPrepare(startPositionMs);
            publishState();
            playbackNotificationCommandOwner.publishPlaybackNotification(true);
        } catch (IllegalStateException error) {
            Log.w(TAG, "Unable to prepare mirrored queue for "
                    + playbackErrorRecoveryCommandOwner.debugTrack(track), error);
            playbackCurrentTrackPreparationRuntimeOwner.markUnableToOpenCurrentTrack();
            releasePlaybackPlayerResources();
            publishState();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareSingleTrack(
            Track track,
            MediaSource mediaSource,
            final boolean playWhenReady,
            final long startPositionMs
    ) {
        playbackCurrentTrackPreparationRuntimeOwner.beginPreparing();
        createPlayerIfNeeded();
        playbackTransitionStateManager.setLastMarkedTrack(null);
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        // player.stop()/setMediaSource() replaces the logical song. Resetting here prevents
        // an old paused/interpolated position from being handed to streaming source recovery.
        playbackPlayerStateOwner.resetPositionEstimate();
        player.stop();
        player.clearMediaItems();
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
        // A single-source player uses the service's manual queue completion path for list repeat.
        // Reapply here as well so the Media3 mode always matches the app-visible repeat mode.
        applyPlaybackModeToPlayer();
        applyPlaybackParametersToPlayer();
        player.setMediaSource(mediaSource);
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs);
                playbackPlayerStateOwner.setPositionEstimate(startPositionMs);
            }
            playbackCurrentTrackPreparationQueueOwner.consumeRestoredPositionAfterPrepare(startPositionMs);
            publishState();
            playbackNotificationCommandOwner.publishPlaybackNotification(true);
        } catch (IllegalStateException error) {
            Log.w(TAG, "Unable to prepare player for "
                    + playbackErrorRecoveryCommandOwner.debugTrack(track), error);
            playbackCurrentTrackPreparationRuntimeOwner.markUnableToOpenCurrentTrack();
            releasePlaybackPlayerResources();
            publishState();
        }
    }

    private void releasePlayer() {
        playerFactory.releasePlayer(
                player, playerListener, audioEffectManager,
                this::releasePlaybackSession,
                PlaybackPrecacheManager.audioCacheReleaseActionFromPrecacheManagerSupplier(
                        () -> playbackPrecacheManager
                )
        );
        player = null;
    }

    private void releasePlaybackPlayerResources() {
        if (playbackShutdownPlaybackResourcesOwner != null) {
            playbackShutdownPlaybackResourcesOwner.releasePlayer();
            return;
        }
        releasePlayer();
    }

    public void stopAndClear() {
        playbackCrossfadeCommandOwner.cancelCrossfadeAdvance();
        playbackSleepTimerCommandOwner.cancelSleepTimer(false);
        boolean queueStopPrepared = playbackQueueCompletionOwner.prepareStopAndClearPlaybackState();
        if (!queueStopPrepared && playbackPositionManager != null) {
            playbackPositionManager.clearPlaybackPosition();
        }
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.releasePlaybackResources();
        } else {
            releasePlaybackPlayerResources();
        }
        if (!queueStopPrepared) {
            playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false);
            playbackErrorRecoveryCommandOwner.setErrorMessage("");
            playbackTransitionStateManager.clear();
        }
        playbackProgressUpdateCommandOwner.stopProgressUpdates();
        playbackNotificationCommandOwner.stopForegroundAndSelf();
        publishState();
    }

    private void playAfterCompletion() {
        playbackQueueCompletionOwner.playAfterCompletion();
    }

    private void stopAtEndOfQueue() {
        if (!playbackQueueCompletionOwner.prepareStopAtEndOfQueue()) {
            if (playbackPositionManager != null) {
                playbackPositionManager.clearPlaybackPosition();
            }
            playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false);
            playbackErrorRecoveryCommandOwner.setErrorMessage("");
            playbackTransitionStateManager.setLastMarkedTrack(null);
        }
        playbackProgressUpdateCommandOwner.stopProgressUpdates();
        if (player == null) {
            createPlayerIfNeeded();
        } else {
            try {
                player.setPlayWhenReady(false);
                player.seekTo(0L);
                playbackPlayerStateOwner.setPositionEstimate(0L);
            } catch (IllegalStateException ignored) {
                releasePlaybackPlayerResources();
                createPlayerIfNeeded();
            }
        }
        publishState();
    }

    private void publishState() {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.publishState();
            return;
        }
        if (playbackModeSettingsStore != null) {
            playbackModeSettingsStore.applyPlaybackModeToPlayer(playbackRuntimeStateManager);
        }
    }

    private void applyPlaybackModeToPlayer() {
        if (playbackModeSettingsStore != null) {
            playbackModeSettingsStore.applyPlaybackModeToPlayer(playbackRuntimeStateManager);
        }
    }

    private void applyPlaybackParametersToPlayer() {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.applyPlaybackParametersToPlayer(playbackRuntimeStateManager);
        }
    }

    private void applyCurrentTrackVolumeToPlayer() {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.applyCurrentTrackVolumeToPlayer(playbackRuntimeStateManager);
        }
    }

    private void applyPlaybackModeAndParametersToPlayer() {
        applyPlaybackParametersToPlayer();
        applyPlaybackModeToPlayer();
    }

    private void applyAudioFocusHandling() {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.applyAudioFocusHandling(playbackRuntimeStateManager);
        }
    }

    private void releasePlaybackSession() {
        if (playbackSessionManager != null) {
            playbackSessionManager.release();
        }
    }

    private void publishBufferingState() {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.publishBufferingState(playbackBufferingDiagnosticsRecorderOwner);
            return;
        }
        if (playbackBufferingDiagnosticsRecorderOwner != null) {
            playbackBufferingDiagnosticsRecorderOwner.record(snapshot());
        }
    }

    private void stopAfterAutomaticAdvance(int completedIndex) {
        playbackQueueCompletionOwner.prepareStopAfterAutomaticAdvance(completedIndex);
        stopAtEndOfQueue();
    }

    private void createPlayerIfNeeded() {
        if (player != null) {
            return;
        }
        player = playerFactory.createPlayer();
        applyAudioFocusHandling();
        player.addListener(playerListener);
        applyPlaybackModeAndParametersToPlayer();
        audioEffectManager.bind(player, audioEffectSettings());
        if (playbackSessionManager != null) {
            playbackSessionManager.bind();
        }
    }

    private void persistPlaybackPositionThrottled(boolean force) {
        if (playbackQueueManager != null) {
            playbackQueueManager.persistCurrentPlaybackPosition(force);
        }
    }

    private void persistPlaybackQueueState() {
        if (playbackQueueManager != null) {
            playbackQueueManager.persistQueueState();
        }
    }

    private void savePlaybackResumeRequested(boolean requested) {
        if (playbackQueueManager != null) {
            playbackQueueManager.savePlaybackResumeRequested(requested);
        }
    }

    private boolean seekExistingMirroredQueue(boolean playWhenReady, long startPositionMs) {
        return playbackQueueNavigationOwner.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
    }

    private void onMirroredQueueReused(boolean playWhenReady) {
        if (playWhenReady) {
            acquireWifiLockIfStreamingAction.run();
        }
        playbackProgressUpdateCommandOwner.startProgressUpdates();
    }

    private void resetWaveformIfTrackChanged(Track track) {
        if (playbackVisualizationAnalyzer != null) {
            playbackVisualizationAnalyzer.resetWaveformIfTrackChanged(track);
        }
    }

    private void postponePlaybackVisualizationWarmup() {
        if (playbackVisualizationAnalyzer != null) {
            playbackVisualizationAnalyzer.postponePlaybackVisualizationWarmup();
        }
    }

    private long positionMs() {
        return playbackPlayerStateOwner.positionMs();
    }

    private long durationMs() {
        return playbackPlayerStateOwner.durationMs();
    }
}
