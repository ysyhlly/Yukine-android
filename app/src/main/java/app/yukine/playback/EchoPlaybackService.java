package app.yukine.playback;

import android.app.PendingIntent;
import android.app.Notification;
import android.content.pm.ServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import app.yukine.R;
import app.yukine.common.EmbeddedArtwork;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.PlaybackServiceHostPort;
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
import app.yukine.playback.service.PlaybackServiceActions;
import app.yukine.playback.state.PlaybackStateListener;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.streaming.StreamingPlaybackHeaderStore;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.SessionError;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
@OptIn(markerClass = UnstableApi.class)
public final class EchoPlaybackService extends MediaLibraryService
        implements PlaybackServiceHostPort, PlaybackNotificationCommandOwner.PlaybackCommands {
    public static final String ACTION_PLAY = PlaybackServiceActions.PLAY;
    public static final String ACTION_PAUSE = PlaybackServiceActions.PAUSE;
    public static final String ACTION_PREVIOUS = PlaybackServiceActions.PREVIOUS;
    public static final String ACTION_NEXT = PlaybackServiceActions.NEXT;
    public static final String ACTION_STOP = PlaybackServiceActions.STOP;
    public static final String ACTION_TOGGLE_FAVORITE = PlaybackServiceActions.TOGGLE_FAVORITE;
    public static final String ACTION_RESTORE = PlaybackServiceActions.RESTORE;
    public static final String ACTION_RESTORE_AND_PLAY = PlaybackServiceActions.RESTORE_AND_PLAY;

    public static final int REPEAT_ALL = PlaybackRepeatMode.REPEAT_ALL;
    public static final int REPEAT_ONE = PlaybackRepeatMode.REPEAT_ONE;
    public static final int REPEAT_OFF = PlaybackRepeatMode.REPEAT_OFF;

    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "EchoPlaybackService";
    private final LocalBinder binder = new LocalBinder();
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
            new PlaybackPlayerStateOwner(() -> player);
    private PlaybackQueueManager playbackQueueManager;
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier =
            () -> playbackQueueManager == null ? null : playbackQueueManager.queueStateSnapshot();
    private final PlaybackQueueStateOwner playbackQueueStateOwner =
            new PlaybackQueueStateOwner(queueStateSnapshotSupplier);
    private final PlaybackQueueRuntimeStateManager playbackQueueRuntimeStateManager =
            new PlaybackQueueRuntimeStateManager();
    private final PlaybackQueueCommandOwner playbackQueueCommandOwner =
            new PlaybackQueueCommandOwner(
                    queueStateSnapshotSupplier,
                    EchoPlaybackService.this::prepareCurrent,
                    EchoPlaybackService.this::publishState
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
    private app.yukine.playback.manager.LyricsPublisher playbackLyricsManager;
    private final Consumer<Boolean> statusBarLyricsEnabledAction =
            PlaybackLyricsSettingsStore.statusBarLyricsEnabledActionFromSupplier(() -> playbackLyricsManager);
    private PlaybackModeSettingsStore playbackModeSettingsStore;
    private PlaybackStatePublisher playbackStatePublisher;
    private PlaybackStateSnapshotOwner playbackStateSnapshotOwner;
    private PlaybackStreamingDiagnosticsRecorderOwner playbackStreamingDiagnosticsRecorderOwner;
    private PlaybackErrorRecoveryCommandOwner playbackErrorRecoveryCommandOwner;
    private PlaybackErrorRecoveryManager playbackErrorRecoveryManager;
    private Runnable recordPlaybackStartHistoryAction = () -> {
    };
    private PlaybackPositionManager playbackPositionManager;
    private PlaybackNotificationManager playbackNotificationManager;
    private PlaybackNotificationCommandOwner playbackNotificationCommandOwner;
    private PlaybackCurrentTrackPreparationQueueOwner playbackCurrentTrackPreparationQueueOwner;
    private PlaybackCurrentTrackPreparationOwner playbackCurrentTrackPreparationOwner;
    private PlaybackSleepTimerCommandOwner playbackSleepTimerCommandOwner;
    private PlaybackSleepTimerManager playbackSleepTimerManager;
    private PlaybackRealtimeVisualizationOwner playbackRealtimeVisualizationOwner;
    private PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer;
    private PlaybackVisualizationCacheManager playbackVisualizationCacheManager;
    private PlaybackNotificationArtworkManager playbackNotificationArtworkManager;
    private PlaybackPrecacheManager playbackPrecacheManager;
    private PlaybackWarmupCoordinator playbackWarmupCoordinator;
    private PlaybackCrossfadeCommandOwner playbackCrossfadeCommandOwner;
    private PlaybackCrossfadeAdvanceManager playbackCrossfadeAdvanceManager;
    private PlaybackRecoveryScheduler playbackRecoveryScheduler;
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
    private PlaybackProgressUpdateManager playbackProgressUpdateManager;
    @Inject
    MusicLibraryRepository repository;
    @Inject
    StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    @Inject
    ToggleFavoriteUseCase toggleFavoriteUseCase;
    private PlaybackAudioEffectSettingsStore playbackAudioEffectSettingsStore;
    private PlaybackMediaSourceProvider mediaSourceProvider;
    private PlaybackPlayerFactory playerFactory;
    private PlaybackRuntimeSettingsStore playbackRuntimeSettingsStore;
    private final PlaybackTransitionStateManager playbackTransitionStateManager = new PlaybackTransitionStateManager();
    private volatile boolean appVisible;

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
                playbackQueueCompletionOwner().playAfterCompletion();
                return;
            }
            publishState();
            if (playbackState == Player.STATE_BUFFERING && player.getPlayWhenReady()) {
                publishBufferingState();
            }
            startProgressUpdates();
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            if (player == null) {
                return;
            }
            PlaybackQueueMirroredTransitionOwner mirroredTransitionOwner =
                    new PlaybackQueueMirroredTransitionOwner(
                            playbackQueueManager,
                            queueStateSnapshotSupplier,
                            EchoPlaybackService.this::applyCurrentTrackVolumeToPlayer,
                            playbackQueueRuntimeStateManager::playerMirrorsQueue
                    );
            if (!mirroredTransitionOwner.canApplyMirroredTransition()) {
                return;
            }
            int nextIndex = player.getCurrentMediaItemIndex();
            PlaybackQueueMirroredTransitionOwner.Transition transition =
                    mirroredTransitionOwner.applyMirroredTransitionReason(nextIndex, reason);
            if (transition == null) {
                return;
            }
            if (transition.stopAfterAutomaticAdvance()) {
                playbackQueueCompletionOwner().stopAfterAutomaticAdvance(transition.completedIndex());
                return;
            }
            Track track = transition.currentTrack();
            if (track != null) {
                resetWaveformIfTrackChanged(track);
            }
            publishState();
            startProgressUpdates();
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
            startProgressUpdates();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false);
            if (playbackErrorRecoveryManager != null) {
                playbackErrorRecoveryManager.onPlayerError(error);
                return;
            }
            Log.w(TAG, "Playback failed for "
                    + playbackErrorRecoveryCommandOwner.debugCurrentTrack(), error);
            playbackErrorRecoveryCommandOwner.setErrorMessage("Unable to play this track.");
            publishState();
        }
    };

    public final class LocalBinder extends Binder {
        public EchoPlaybackService getService() {
            return EchoPlaybackService.this;
        }
    }

    @Override
    @UnstableApi
    public void onCreate() {
        super.onCreate();
        mediaSourceProvider = new PlaybackMediaSourceProvider(this, repository, streamingPlaybackHeaderStore);
        playerFactory = new PlaybackPlayerFactory(this, realtimeBassAudioProcessor);
        playbackAudioEffectSettingsStore = PlaybackAudioEffectSettingsStore.fromRepository(repository);
        playbackAudioEffectSettingsStore.restore();
        new PlaybackNotificationChannelOwner(this).createNotificationChannel();
        playbackMainHandlerSchedulerOwner = new PlaybackMainHandlerSchedulerOwner(mainHandler);
        PlaybackQueueStore queueStore = new PlaybackQueueStoreImpl(repository);
        playbackModeSettingsStore = PlaybackModeSettingsStore.fromRepository(repository);
        playbackModeSettingsStore.restoreInto(playbackRuntimeStateManager);
        playbackRuntimeSettingsStore = PlaybackRuntimeSettingsStore.fromRepository(repository);
        playbackRuntimeSettingsStore.restoreInto(playbackRuntimeStateManager);
        final PlaybackPlayHistoryRecorder playbackPlayHistoryRecorder = PlaybackPlayHistoryRecorder.fromRepository(
                repository,
                playbackTransitionStateManager
        );
        recordPlaybackStartHistoryAction = PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(
                playbackPlayHistoryRecorder,
                () -> player != null && player.getPlayWhenReady(),
                queueStateSnapshotSupplier
        );
        playbackPositionManager = new PlaybackPositionManager(
                queueStore,
                PlaybackPositionManager.stateProviderFromPlaybackState(
                        queueStateSnapshotSupplier,
                        playbackPlayerStateOwner::positionMs
                )
        );
        playbackSleepTimerCommandOwner = new PlaybackSleepTimerCommandOwner(
                EchoPlaybackService.this::pause,
                EchoPlaybackService.this::publishState
        );
        playbackSleepTimerManager = new PlaybackSleepTimerManager(
                playbackMainHandlerSchedulerOwner,
                playbackSleepTimerCommandOwner
        );
        playbackSleepTimerCommandOwner.bindPlaybackSleepTimerManager(playbackSleepTimerManager);
        playbackErrorRecoveryCommandOwner = new PlaybackErrorRecoveryCommandOwner(
                queueStateSnapshotSupplier,
                playbackQueueCommandOwner::prepareCurrent,
                EchoPlaybackService.this::skipToNext,
                playbackCurrentTrackPreparationRuntimeOwner::setErrorMessage,
                EchoPlaybackService.this::publishState,
                (message, error) -> Log.w(TAG, message, error)
        );
        playbackErrorRecoveryManager = new PlaybackErrorRecoveryManager(
                playbackMainHandlerSchedulerOwner,
                playbackErrorRecoveryCommandOwner,
                mediaSourceProvider::isHttpTrack,
                1500L
        );
        playbackProgressUpdateManager = new PlaybackProgressUpdateManager(
                playbackMainHandlerSchedulerOwner,
                PlaybackProgressUpdateManager.stateProviderFromPlaybackState(
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                ),
                PlaybackProgressUpdateManager.actionsFromCallbacks(
                        EchoPlaybackService.this::publishState,
                        EchoPlaybackService.this::persistCurrentPlaybackPosition
                )
        );
        playbackCrossfadeCommandOwner = new PlaybackCrossfadeCommandOwner(
                playbackTransitionStateManager::setFadeOutAdvancing,
                volume -> {
                    if (player != null) {
                        player.setVolume(volume);
                    }
                },
                () -> playbackQueueNavigationOwner().skipToNextImmediately(),
                EchoPlaybackService.this::applyCurrentTrackVolumeToPlayer
        );
        final PlaybackCrossfadeStateOwner playbackCrossfadeStateOwner = new PlaybackCrossfadeStateOwner(
                playbackTransitionStateManager::fadeOutAdvancing,
                () -> player != null,
                playbackPlayerStateOwner::isPlaying,
                () -> playbackModeSettingsStore == null
                        ? REPEAT_ALL
                        : playbackModeSettingsStore.repeatMode(playbackRuntimeStateManager),
                queueStateSnapshotSupplier,
                () -> playbackRuntimeSettingsStore == null
                        ? 1.0f
                        : playbackRuntimeSettingsStore.currentTrackVolume(playbackRuntimeStateManager)
        );
        playbackCrossfadeAdvanceManager = new PlaybackCrossfadeAdvanceManager(
                playbackMainHandlerSchedulerOwner,
                playbackCrossfadeStateOwner,
                playbackCrossfadeCommandOwner
        );
        playbackCrossfadeCommandOwner.bindPlaybackCrossfadeAdvanceManager(playbackCrossfadeAdvanceManager);
        playbackRecoveryScheduler = new PlaybackRecoveryScheduler(
                task -> playbackTaskScheduler.schedule(
                        PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                        task
                ),
                playbackMainHandlerSchedulerOwner,
                playbackQueueCommandOwner::prepareCurrent
        );
        createPlayerIfNeeded();
        final PlaybackNotificationForegroundOwner playbackNotificationForegroundOwner =
                new PlaybackNotificationForegroundOwner(
                        EchoPlaybackService.this::activityPendingIntent,
                        EchoPlaybackService.this::serviceActionPendingIntent,
                        notification -> EchoPlaybackService.this.startPlaybackForeground(notification),
                        () -> {
                            EchoPlaybackService.this.stopForeground(true);
                            EchoPlaybackService.this.stopSelf();
                        }
                );
        playbackNotificationCommandOwner = PlaybackNotificationCommandOwner.fromNotificationOwners(
                () -> playbackStatePublisher,
                () -> playbackNotificationManager,
                () -> playbackNotificationManager != null
                        && playbackNotificationManager.hasNotificationWorthyState(),
                EchoPlaybackService.this,
                playbackNotificationForegroundOwner::stopForegroundAndSelf
        );
        final PlaybackNotificationStateOwner playbackNotificationStateOwner = new PlaybackNotificationStateOwner(
                queueStateSnapshotSupplier,
                playbackPlayerStateOwner::isPlaying,
                playbackCurrentTrackPreparationRuntimeOwner::preparing,
                track -> toggleFavoriteUseCase != null && toggleFavoriteUseCase.isFavorite(track),
                () -> {
                    MediaLibrarySession session = playbackSessionManager == null ? null : playbackSessionManager.session();
                    return session == null ? null : session.getPlatformToken();
                }
        );
        final Supplier<Track> currentTrackSupplier = playbackNotificationStateOwner::currentTrack;
        final PlaybackNotificationArtworkSource playbackNotificationArtworkSource =
                PlaybackNotificationArtworkSource.fromSupplier(
                        () -> playbackNotificationArtworkManager
                );
        playbackNotificationManager = new PlaybackNotificationManager(
                this,
                playbackNotificationForegroundOwner,
                playbackNotificationStateOwner,
                () -> playbackLyricsManager,
                playbackNotificationArtworkSource,
                playbackNotificationCommandOwner
        );
        final PlaybackNotificationArtworkBridgeOwner.SessionRefresher playbackSessionRefresher =
                PlaybackNotificationArtworkBridgeOwner.sessionRefresherFromPlaybackSessionManager(
                        () -> playbackSessionManager
                );
        final PlaybackLyricsStateOwner playbackLyricsStateOwner = new PlaybackLyricsStateOwner(
                () -> appVisible,
                playbackQueueStateOwner,
                playbackPlayerStateOwner::isPlaying,
                playbackCurrentTrackPreparationRuntimeOwner::preparing
        );
        playbackLyricsManager = new PlaybackLyricsManager(
                this,
                playbackLyricsStateOwner,
                playbackNotificationManager.lyricsNotificationBridge(playbackSessionRefresher)
        );
        PlaybackLyricsSettingsStore.fromRepository(repository).restoreInto(playbackLyricsManager);
        final PlaybackQueueStreamingRestoreOwner playbackQueueStreamingRestoreOwner =
                new PlaybackQueueStreamingRestoreOwner(mediaSourceProvider);
        final PlaybackMirroredQueueTrackMatcherOwner playbackMirroredQueueTrackMatcherOwner =
                new PlaybackMirroredQueueTrackMatcherOwner(
                        () -> player,
                        mediaSourceProvider
                );
        final PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner =
                new PlaybackQueueMirroredPlayerOwner(
                        PlaybackQueueMirroredPlayerOwner.mirroredQueueMatcher(
                                playbackQueueRuntimeStateManager::playerMirrorsQueue,
                                () -> player == null ? -1 : player.getMediaItemCount(),
                                () -> playbackQueueManager == null
                                        ? Collections.emptyList()
                                        : playbackQueueManager.queueSnapshot(),
                                playbackMirroredQueueTrackMatcherOwner::matches
                        ),
                        () -> player != null,
                        playbackCurrentTrackPreparationRuntimeOwner::setPreparing,
                        queueStateSnapshotSupplier,
                        EchoPlaybackService.this::resetWaveformIfTrackChanged,
                        EchoPlaybackService.this::applyPlaybackModeAndParametersToPlayer,
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
                playbackQueueStreamingRestoreOwner,
                playbackQueueMirroredPlayerOwner,
                playbackRuntimeStateManager,
                playbackTransitionStateManager
        );
        playbackCurrentTrackPreparationQueueOwner = new PlaybackCurrentTrackPreparationQueueOwner(
                playbackQueueManager,
                mediaSourceProvider,
                playbackNotificationManager::mediaMetadataForTrack
        );
        playbackCurrentTrackPreparationOwner = PlaybackCurrentTrackPreparationOwner.fromMediaSourceProvider(
                mediaSourceProvider,
                playbackNotificationManager::mediaMetadataForTrack,
                playbackCurrentTrackPreparationQueueOwner,
                playbackPositionManager::restoredPositionFor,
                playbackCurrentTrackPreparationRuntimeOwner,
                EchoPlaybackService.this::publishState,
                track -> Log.w(TAG, "Refusing to prepare empty uri for "
                        + playbackErrorRecoveryCommandOwner.debugTrack(track))
        );
        final PlaybackMediaLibraryCallback playbackMediaLibraryCallback = new PlaybackMediaLibraryCallback(
                PlaybackMediaLibraryDataSource.fromRepository(
                        getString(R.string.app_name),
                        repository,
                        mediaSourceProvider,
                        playbackNotificationManager::mediaMetadataForTrack
                )
        );
        final PlaybackControllerMediaItemsOwner playbackControllerMediaItemsOwner =
                new PlaybackControllerMediaItemsOwner(
                        (mediaItems, startIndex, startPositionMs) ->
                                playbackMediaLibraryCallback.controllerQueueForMediaItems(
                                        mediaItems,
                                        startIndex,
                                        startPositionMs
                                ),
                        playbackQueueMutationOwner()
                );
        final PlaybackSessionCommandOwner playbackSessionCommandOwner = new PlaybackSessionCommandOwner(
                EchoPlaybackService.this,
                EchoPlaybackService.this::seekTo,
                EchoPlaybackService.this::setRepeatMode,
                playbackControllerMediaItemsOwner,
                queueStateSnapshotSupplier,
                playbackNotificationManager::mediaMetadataForTrack
        );
        playbackSessionManager = new PlaybackSessionManager(
                this,
                () -> createSessionPlayer(playbackSessionCommandOwner),
                playbackMediaLibraryCallback,
                this::activityPendingIntent
        );
        final PlaybackBufferedProgressOwner playbackBufferedProgressOwner =
                new PlaybackBufferedProgressOwner(
                        playbackPlayerStateOwner::positionMs,
                        playbackPlayerStateOwner::bufferedPositionMs
                );
        final PlaybackVisualizationStateOwner playbackVisualizationStateOwner =
                new PlaybackVisualizationStateOwner(
                        () -> appVisible,
                        playbackBufferedProgressOwner,
                        EchoPlaybackService.this::publishState
                );
        playbackVisualizationAnalyzer = new PlaybackVisualizationAnalyzer(
                this,
                visualizationTaskScheduler,
                playbackVisualizationStateOwner,
                mediaSourceProvider
        );
        playbackRealtimeVisualizationOwner =
                PlaybackRealtimeVisualizationOwner.fromRealtimeBassDetector(
                        playbackPlayerStateOwner::isPlaying,
                        realtimeBassDetector
                );
        playbackStateSnapshotOwner = new PlaybackStateSnapshotOwner(
                queueStateSnapshotSupplier,
                playbackPlayerStateOwner,
                PlaybackStateSnapshotOwner.fromRuntimeStateManager(playbackRuntimeStateManager),
                playbackSleepTimerCommandOwner::sleepTimerRemainingMs,
                PlaybackStateSnapshotOwner.fromVisualizationAnalyzer(playbackVisualizationAnalyzer),
                playbackRealtimeVisualizationOwner::beat,
                REPEAT_ALL
        );
        PlaybackVisualizationCacheStateOwner playbackVisualizationCacheStateOwner =
                new PlaybackVisualizationCacheStateOwner(
                        mainHandler,
                        currentTrackSupplier,
                        task -> visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, task)
                );
        playbackVisualizationCacheManager = PlaybackVisualizationCacheManager.fromMediaSourceProvider(
                playbackVisualizationCacheStateOwner,
                mediaSourceProvider
        );
        playbackWarmupCoordinator = new PlaybackWarmupCoordinator(
                track -> {
                    if (playbackPrecacheManager != null) {
                        playbackPrecacheManager.precacheTrack(track);
                    }
                },
                track -> {
                    if (playbackVisualizationCacheManager != null) {
                        playbackVisualizationCacheManager.scheduleVisualizationCache(track);
                    }
                }
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
                () -> EchoPlaybackService.this.persistCurrentPlaybackPosition(true),
                PlaybackShutdownLifecycleResourcesOwner.playbackQueueLifecycleStore(
                        playbackQueueManager,
                        queueStore
                ),
                PlaybackShutdownLifecycleResourcesOwner.playbackStateProviderFromPlaybackState(
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                ),
                playbackNotificationCommandOwner::hasNotificationWorthyState,
                () -> playbackNotificationCommandOwner.publishPlaybackNotification(true)
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
                EchoPlaybackService.this::releasePlayer,
                () -> playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false),
                () -> playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false)
        );
        playbackShutdownCoordinator = new PlaybackShutdownCoordinator(
                playbackShutdownPlaybackResourcesOwner,
                playbackShutdownServiceResourcesOwner,
                playbackShutdownLifecycleResourcesOwner
        );
        playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(
                this,
                currentTrackSupplier,
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
                PlaybackStatePublisherWidgetOwner.fromContext(this)
        );
        playbackStreamingDiagnosticsRecorderOwner =
                new PlaybackStreamingDiagnosticsRecorderOwner(
                        streamingDiagnostics,
                        mediaSourceProvider::streamingQualityForTrack
                );
        playbackPrecacheManager = PlaybackPrecacheManager.fromMediaSourceProvider(
                PlaybackPlayerStateOwner.mediaItemSupplierFromPlayerSupplier(() -> player),
                streamingDiagnostics,
                playbackQueueManager,
                mediaSourceProvider,
                playbackMainHandlerSchedulerOwner
        );
        playbackQueueRestoreOwner().restorePlaybackQueue();
        playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
        playbackLyricsManager.bind();
        playbackNoisyReceiverManager = new PlaybackNoisyReceiverManager(
                new PlaybackNoisyReceiverRegistrarOwner(EchoPlaybackService.this),
                PlaybackNoisyReceiverManager.actionsFromPlaybackState(
                        playbackPlayerStateOwner::isPlaying,
                        EchoPlaybackService.this::pause
                )
        );
        playbackNoisyReceiverManager.register();
        android.net.wifi.WifiManager wifiManager =
                (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        android.net.wifi.WifiManager.WifiLock wifiLock = null;
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "echo:playback");
        }
        playbackWifiLockManager = new PlaybackWifiLockManager(
                PlaybackWifiLockOwner.fromWifiLock(wifiLock),
                currentTrackSupplier,
                mediaSourceProvider::isHttpTrack
        );
        publishState();
    }

    @UnstableApi
    private Player createSessionPlayer(PlaybackSessionCommandOwner playbackSessionCommandOwner) {
        return new PlaybackSessionPlayer(player, playbackSessionCommandOwner);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null
                && (MediaLibraryService.SERVICE_INTERFACE.equals(intent.getAction())
                || androidx.media3.session.MediaSessionService.SERVICE_INTERFACE.equals(intent.getAction()))) {
            return super.onBind(intent);
        }
        return binder;
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return playbackSessionManager == null ? null : playbackSessionManager.session();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent == null ? "" : intent.getAction();
        if (playbackNotificationManager != null) {
            playbackNotificationManager.handleServiceAction(action);
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.handleTaskRemoved();
        } else {
            persistCurrentPlaybackPosition(true);
            if (playbackQueueManager != null) {
                playbackQueueManager.persistQueueState();
            }
            savePlaybackResumeRequested(
                    playbackPlayerStateOwner.isPlaying()
                            || playbackCurrentTrackPreparationRuntimeOwner.preparing()
            );
            if (playbackNotificationCommandOwner != null) {
                playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.handleServiceDestroyed();
        } else {
            persistCurrentPlaybackPosition(true);
        }
        super.onDestroy();
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
        if (visible) {
            if (playbackNotificationCommandOwner != null) {
                playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
            }
        }
    }

    public void playQueue(List<Track> tracks, int startIndex) {
        playbackQueueMutationOwner().playQueue(tracks, startIndex, C.TIME_UNSET);
    }

    public void appendToQueue(List<Track> tracks) {
        playbackQueueMutationOwner().appendToQueue(tracks);
    }

    public void play() {
        if (player == null) {
            playbackQueueCommandOwner.prepareCurrentOrRunFallback(
                    true,
                    () -> playbackQueueNavigationOwner().playFirstQueuedTrack()
            );
            return;
        }
        if (playbackCurrentTrackPreparationRuntimeOwner.preparing()) {
            return;
        }
        if (playbackQueueCommandOwner.runIfCurrentTrackMissing(
                () -> playbackQueueNavigationOwner().playFirstQueuedTrack()
        )) {
            return;
        }
        if (player.getMediaItemCount() == 0) {
            playbackQueueCommandOwner.prepareCurrent(true);
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0L);
        }
        player.play();
        savePlaybackResumeRequested(true);
        acquireWifiLockIfStreamingAction.run();
        publishState();
        startProgressUpdates();
    }

    public void pause() {
        playbackCrossfadeCommandOwner.cancelCrossfadeAdvance();
        if (player != null && playbackPlayerStateOwner.isPlaying()) {
            player.pause();
        }
        savePlaybackResumeRequested(false);
        releaseWifiLockAction.run();
        persistCurrentPlaybackPosition(true);
        publishState();
    }

    public void seekTo(long positionMs) {
        if (player == null || playbackCurrentTrackPreparationRuntimeOwner.preparing()) {
            return;
        }
        try {
            player.seekTo(Math.max(0L, positionMs));
            persistCurrentPlaybackPosition(true);
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
        playbackQueueNavigationOwner().skipToNextImmediately();
    }

    public void skipToPrevious() {
        if (playbackPlayerStateOwner.positionMs() > 3000L) {
            seekTo(0L);
            return;
        }
        playbackQueueNavigationOwner().skipToPrevious();
    }

    public List<Track> queueSnapshot() {
        return playbackQueueManager == null ? Collections.emptyList() : playbackQueueManager.queueSnapshot();
    }

    public void moveQueueTrack(int fromIndex, int toIndex) {
        playbackQueueMutationOwner().moveQueueTrack(fromIndex, toIndex);
    }

    public PlaybackStreamingDiagnostics.Snapshot streamingDiagnostics() {
        return streamingDiagnostics.snapshot();
    }

    public void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        new PlaybackCurrentTrackReplacementOwner(
                playbackQueueManager,
                recovery -> playbackStreamingDiagnosticsRecorderOwner.record(recovery),
                playWhenReady -> playbackRecoveryScheduler.scheduleCurrentPlaybackRecovery(playWhenReady)
        ).replaceCurrentTrackAndResume(replacement, positionMs);
    }

    public void removeTracksById(Set<Long> trackIds) {
        playbackQueueMutationOwner().removeTracksById(trackIds);
    }

    @Override
    public void warmPlaybackTrack(Track track) {
        if (playbackWarmupCoordinator != null) {
            playbackWarmupCoordinator.warmup(track);
        }
    }

    public void retainTracksById(Set<Long> trackIdsToKeep) {
        playbackQueueMutationOwner().retainTracksById(trackIdsToKeep);
    }

    public void clearQueue() {
        playbackQueueMutationOwner().clearQueue();
    }

    public void toggleCurrentFavorite() {
        PlaybackFavoriteCommandOwner.toggleCurrentFavorite(
                queueStateSnapshotSupplier,
                toggleFavoriteUseCase,
                EchoPlaybackService.this::publishState
        );
    }

    public void restoreLastPlayback(boolean playWhenRestored) {
        playbackQueueRestoreOwner().restoreLastPlayback(playWhenRestored);
    }

    public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        playbackQueueMutationOwner().replaceQueuedTrackById(oldTrackId, replacement);
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

    public void setPlaybackRestoreEnabled(boolean enabled) {
        playbackQueueRestoreOwner().setPlaybackRestoreEnabled(enabled);
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
    private void prepareCurrent(Track track, final boolean playWhenReady) {
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
        if (reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs)) {
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
        player.clearMediaItems();
        player.setMediaSources(mediaSources, queuePreparation.startIndex(), Math.max(0L, startPositionMs));
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(true);
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            playbackPositionManager.consumeRestoredPositionAfterPrepare(startPositionMs);
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
        player.stop();
        player.clearMediaItems();
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
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
            }
            playbackPositionManager.consumeRestoredPositionAfterPrepare(startPositionMs);
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
                PlaybackShutdownPlaybackResourcesOwner.releaseFrom(
                        () -> playbackPrecacheManager,
                        PlaybackPrecacheManager::releaseAudioCache
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
        playbackQueueCompletionOwner().prepareStopAndClearPlaybackState();
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.releasePlaybackResources();
        } else {
            releasePlaybackPlayerResources();
        }
        stopProgressUpdates();
        playbackNotificationCommandOwner.stopForegroundAndSelf();
        publishState();
    }

    private void stopAtEndOfQueue() {
        playbackQueueCompletionOwner().prepareStopAtEndOfQueue();
        stopProgressUpdates();
        if (player == null) {
            createPlayerIfNeeded();
        } else {
            try {
                player.setPlayWhenReady(false);
                player.seekTo(0L);
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
        if (playbackModeSettingsStore != null) {
            playbackModeSettingsStore.applyPlaybackModeToPlayer(playbackRuntimeStateManager);
        }
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
            playbackStatePublisher.publishBufferingState(playbackStreamingDiagnosticsRecorderOwner);
            return;
        }
        if (playbackStreamingDiagnosticsRecorderOwner != null) {
            playbackStreamingDiagnosticsRecorderOwner.record(snapshot());
        }
    }

    private PlaybackQueueCompletionOwner playbackQueueCompletionOwner() {
        return new PlaybackQueueCompletionOwner(
                playbackQueueManager,
                EchoPlaybackService.this::stopAndClear,
                EchoPlaybackService.this::stopAtEndOfQueue,
                EchoPlaybackService.this::skipToNext
        );
    }

    private PlaybackQueueNavigationOwner playbackQueueNavigationOwner() {
        return new PlaybackQueueNavigationOwner(
                playbackQueueManager,
                this::onMirroredQueueReused
        );
    }

    private PlaybackQueueMutationOwner playbackQueueMutationOwner() {
        return new PlaybackQueueMutationOwner(
                playbackQueueManager,
                EchoPlaybackService.this::stopAndClear
        );
    }

    private PlaybackQueueRestoreOwner playbackQueueRestoreOwner() {
        return new PlaybackQueueRestoreOwner(
                playbackQueueManager,
                EchoPlaybackService.this::createPlayerIfNeeded,
                playbackQueueCommandOwner
        );
    }

    private void persistCurrentPlaybackPosition(boolean force) {
        if (playbackPositionManager != null) {
            playbackPositionManager.persistCurrentPosition(force);
        }
    }

    private void startProgressUpdates() {
        if (playbackProgressUpdateManager != null) {
            playbackProgressUpdateManager.startIfNeeded();
        }
    }

    private void stopProgressUpdates() {
        if (playbackProgressUpdateManager != null) {
            playbackProgressUpdateManager.stop();
        }
    }

    private void savePlaybackResumeRequested(boolean requested) {
        new PlaybackQueueStoreImpl(repository).saveResumeRequested(requested);
    }

    private boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
        return playbackQueueNavigationOwner().reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
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

    private void onMirroredQueueReused(boolean playWhenReady) {
        if (playWhenReady) {
            acquireWifiLockIfStreamingAction.run();
        }
        startProgressUpdates();
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

    private boolean startPlaybackForeground(Notification notification) {
        if (notification == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to start playback foreground notification", error);
            return false;
        }
    }

    private PendingIntent activityPendingIntent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(getPackageName());
        }
        intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, pendingIntentFlags());
    }

    private PendingIntent serviceActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, EchoPlaybackService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(this, requestCode, intent, pendingIntentFlags());
        }
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}

