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

import java.util.List;
import java.util.Set;

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
            PlaybackPlayerStateOwner.fromPlayerProvider(() -> player);
    private PlaybackQueueManager playbackQueueManager;
    private final PlaybackQueueStateOwner playbackQueueStateOwner =
            PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> playbackQueueManager);
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
                    EchoPlaybackService.this::applyCurrentTrackVolumeToPlayer
            );
    private final PlaybackQueueRestoreOwner playbackQueueRestoreOwner =
            PlaybackQueueRestoreOwner.fromPlaybackQueueManager(
                    () -> playbackQueueManager,
                    EchoPlaybackService.this::createPlayerIfNeeded,
                    EchoPlaybackService.this::prepareCurrent,
                    EchoPlaybackService.this::publishState
            );
    private final PlaybackQueuePersistenceOwner playbackQueuePersistenceOwner =
            PlaybackQueuePersistenceOwner.fromPlaybackQueueManager(() -> playbackQueueManager);
    private final PlaybackQueueStopClearOwner playbackQueueStopClearOwner =
            PlaybackQueueStopClearOwner.fromPlaybackQueueManager(() -> playbackQueueManager);
    private final PlaybackQueueCompletionOwner playbackQueueCompletionOwner =
            PlaybackQueueCompletionOwner.fromPlaybackQueueManager(
                    () -> playbackQueueManager,
                    new PlaybackQueueCompletionOwner.CompletionBoundary() {
                        @Override
                        public void stopAndClear() {
                            EchoPlaybackService.this.stopAndClear();
                        }

                        @Override
                        public void prepareCurrent(boolean playWhenReady) {
                            EchoPlaybackService.this.prepareCurrent(playWhenReady);
                        }

                        @Override
                        public void stopAtEndOfQueue() {
                            EchoPlaybackService.this.stopAtEndOfQueue();
                        }

                        @Override
                        public void skipToNext() {
                            EchoPlaybackService.this.skipToNext();
                        }
                    }
            );
    private final PlaybackQueueRuntimeStateManager playbackQueueRuntimeStateManager = new PlaybackQueueRuntimeStateManager();
    private final PlaybackQueueMirrorStateOwner playbackQueueMirrorStateOwner =
            PlaybackQueueMirrorStateOwner.fromRuntimeStateManager(playbackQueueRuntimeStateManager);
    private final PlaybackRuntimeStateOwner playbackRuntimeStateOwner = new PlaybackRuntimeStateOwner(
            () -> player,
            playbackQueueMirrorStateOwner::playerMirrorsQueue,
            () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack()
    );
    private final PlaybackRuntimeStateManager playbackRuntimeStateManager =
            new PlaybackRuntimeStateManager(playbackRuntimeStateOwner);
    private final PlaybackCurrentTrackPreparationRuntimeOwner playbackCurrentTrackPreparationRuntimeOwner =
            PlaybackCurrentTrackPreparationRuntimeOwner.fromRuntimeStateManager(playbackRuntimeStateManager);
    private final PlaybackAudioEffectManager audioEffectManager =
            new PlaybackAudioEffectManager(TAG);
    private PlaybackSessionManager playbackSessionManager;
    private PlaybackSessionRefreshOwner playbackSessionRefreshOwner;
    private app.yukine.playback.manager.LyricsPublisher playbackLyricsManager;
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
    private PlaybackQueueCommandOwner playbackQueueCommandOwner;
    private PlaybackQueueStreamingRestoreOwner playbackQueueStreamingRestoreOwner;
    private PlaybackQueueMirroredPlayerOwner playbackQueueMirroredPlayerOwner;
    private PlaybackMirroredQueueTrackMatcherOwner playbackMirroredQueueTrackMatcherOwner;
    private PlaybackPositionManager playbackPositionManager;
    private PlaybackPositionStateOwner playbackPositionStateOwner;
    private PlaybackActiveStateOwner playbackActiveStateOwner;
    private PlaybackNotificationManager playbackNotificationManager;
    private PlaybackNotificationForegroundOwner playbackNotificationForegroundOwner;
    private PlaybackNotificationCommandOwner playbackNotificationCommandOwner;
    private PlaybackNotificationStateOwner playbackNotificationStateOwner;
    private PlaybackNotificationArtworkProviderOwner playbackNotificationArtworkProviderOwner;
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
    private PlaybackNoisyReceiverManager playbackNoisyReceiverManager;
    private PlaybackProgressUpdateCommandOwner playbackProgressUpdateCommandOwner;
    private PlaybackProgressUpdateStateOwner playbackProgressUpdateStateOwner;
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
                playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false);
                playbackErrorRecoveryCommandOwner.setErrorMessage("");
                if (playbackErrorRecoveryManager != null) {
                    playbackErrorRecoveryManager.onPlaybackReady();
                }
                if (playbackPlayHistoryRecorder != null) {
                    playbackPlayHistoryRecorder.recordIfPlaybackStarted(
                            player.getPlayWhenReady(),
                            currentTrack()
                    );
                }
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
            if (!playbackQueueMirrorStateOwner.playerMirrorsQueue()
                    || player == null
                    || playbackQueueStateOwner.queueStateSnapshot().isQueueEmpty()) {
                return;
            }
            int nextIndex = player.getCurrentMediaItemIndex();
            PlaybackQueueManager.MirroredTransitionResult transition =
                    playbackQueueMirroredTransitionOwner.applyMirroredTransitionIndex(
                            nextIndex,
                            isAutomaticMediaItemAdvance(reason)
                    );
            if (transition == null) {
                return;
            }
            if (transition.getStopAfterAutomaticAdvance()) {
                stopAfterAutomaticAdvance(transition.getCompletedIndex());
                return;
            }
            Track track = currentTrack();
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
                if (playbackWifiLockManager != null) {
                    playbackWifiLockManager.acquireIfStreaming();
                }
            } else {
                if (playbackWifiLockManager != null) {
                    playbackWifiLockManager.release();
                }
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
                    + playbackErrorRecoveryCommandOwner.debugTrack(currentTrack()), error);
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
        playbackCurrentTrackPreparationQueueOwner =
                PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager(
                        () -> playbackQueueManager,
                        tracks -> mediaSourceProvider.mediaSourcesForTracks(
                                tracks,
                                playbackNotificationManager::mediaMetadataForTrack
                        )
                );
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
        playbackPlayHistoryRecorder = PlaybackPlayHistoryRecorder.fromRepository(
                repository,
                playbackTransitionStateManager
        );
        playbackPositionStateOwner = new PlaybackPositionStateOwner(
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                playbackPlayerStateOwner::positionMs
        );
        playbackPositionManager = new PlaybackPositionManager(queueStore, playbackPositionStateOwner);
        playbackSleepTimerCommandOwner = new PlaybackSleepTimerCommandOwner(
                EchoPlaybackService.this,
                EchoPlaybackService.this::publishState,
                () -> playbackSleepTimerManager
        );
        playbackSleepTimerManager = new PlaybackSleepTimerManager(
                playbackMainHandlerSchedulerOwner,
                playbackSleepTimerCommandOwner
        );
        playbackErrorRecoveryCommandOwner = new PlaybackErrorRecoveryCommandOwner(
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                playbackQueueStateOwner,
                EchoPlaybackService.this::prepareCurrent,
                EchoPlaybackService.this,
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
        playbackProgressUpdateCommandOwner = new PlaybackProgressUpdateCommandOwner(
                EchoPlaybackService.this::publishState,
                EchoPlaybackService.this::persistPlaybackPositionThrottled,
                () -> playbackProgressUpdateManager
        );
        playbackProgressUpdateStateOwner = new PlaybackProgressUpdateStateOwner(
                playbackPlayerStateOwner::isPlaying,
                playbackCurrentTrackPreparationRuntimeOwner::preparing
        );
        playbackProgressUpdateManager = new PlaybackProgressUpdateManager(
                playbackMainHandlerSchedulerOwner,
                playbackProgressUpdateStateOwner,
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
                        ? REPEAT_ALL
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
                EchoPlaybackService.this::prepareCurrent
        );
        createPlayerIfNeeded();
        playbackNotificationForegroundOwner = new PlaybackNotificationForegroundOwner(
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
        playbackActiveStateOwner = new PlaybackActiveStateOwner(
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                playbackPlayerStateOwner::isPlaying,
                playbackCurrentTrackPreparationRuntimeOwner::preparing
        );
        playbackNotificationStateOwner = new PlaybackNotificationStateOwner(
                () -> playbackQueueStateOwner.queueStateSnapshot().isQueueEmpty(),
                playbackActiveStateOwner,
                track -> toggleFavoriteUseCase != null && toggleFavoriteUseCase.isFavorite(track),
                () -> {
                    MediaLibrarySession session = playbackSessionManager == null ? null : playbackSessionManager.session();
                    return session == null ? null : session.getPlatformToken();
                }
        );
        playbackNotificationArtworkProviderOwner = new PlaybackNotificationArtworkProviderOwner(
                () -> playbackNotificationArtworkManager
        );
        playbackNotificationManager = new PlaybackNotificationManager(
                this,
                playbackNotificationForegroundOwner,
                playbackNotificationStateOwner,
                () -> playbackLyricsManager,
                playbackNotificationArtworkProviderOwner,
                playbackNotificationCommandOwner
        );
        playbackSessionRefreshOwner = PlaybackSessionRefreshOwner.fromPlaybackSessionManager(
                () -> playbackSessionManager
        );
        playbackLyricsStateOwner = new PlaybackLyricsStateOwner(
                () -> appVisible,
                playbackActiveStateOwner
        );
        playbackLyricsManager = new PlaybackLyricsManager(
                this,
                playbackLyricsStateOwner,
                playbackNotificationManager.lyricsNotificationBridge(playbackSessionRefreshOwner)
        );
        PlaybackLyricsSettingsStore.fromRepository(repository).restoreInto(playbackLyricsManager);
        playbackQueueCommandOwner = new PlaybackQueueCommandOwner(
                EchoPlaybackService.this::prepareCurrent,
                EchoPlaybackService.this::publishState,
                EchoPlaybackService.this
        );
        playbackQueueStreamingRestoreOwner =
                PlaybackQueueStreamingRestoreOwner.fromMediaSourceProvider(mediaSourceProvider);
        playbackMirroredQueueTrackMatcherOwner =
                PlaybackMirroredQueueTrackMatcherOwner.fromPlayerProvider(
                        () -> player,
                        mediaSourceProvider::mediaItemMatchesTrackForReuse
        );
        playbackQueueMirroredPlayerOwner = new PlaybackQueueMirroredPlayerOwner(
                PlaybackQueueMirroredPlayerOwner.fromPlaybackQueueManager(
                        playbackQueueMirrorStateOwner::playerMirrorsQueue,
                        () -> player != null,
                        () -> player == null ? -1 : player.getMediaItemCount(),
                        () -> playbackQueueManager,
                        playbackMirroredQueueTrackMatcherOwner
                ),
                () -> player != null,
                playbackCurrentTrackPreparationRuntimeOwner::setPreparing,
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                EchoPlaybackService.this::resetWaveformIfTrackChanged,
                EchoPlaybackService.this::applyPlaybackModeAndParametersToPlayer,
                (index, positionMs) -> player.seekTo(index, positionMs),
                playWhenReady -> player.setPlayWhenReady(playWhenReady),
                () -> player.play(),
                playbackQueueMirrorStateOwner::setPlayerMirrorsQueue,
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
        playbackCurrentTrackPreparationOwner = new PlaybackCurrentTrackPreparationOwner(
                mediaSourceProvider::prepareTrackForPlayback,
                track -> mediaSourceProvider.mediaSourceForTrack(
                        track,
                        playbackNotificationManager::mediaMetadataForTrack
                ),
                playbackCurrentTrackPreparationQueueOwner,
                playbackCurrentTrackPreparationRuntimeOwner,
                EchoPlaybackService.this::publishState,
                track -> Log.w(TAG, "Refusing to prepare empty uri for "
                        + playbackErrorRecoveryCommandOwner.debugTrack(track))
        );
        playbackMediaLibraryCallback = new PlaybackMediaLibraryCallback(
                PlaybackMediaLibraryDataSource.fromRepository(
                        getString(R.string.app_name),
                        repository,
                        mediaSourceProvider,
                        playbackNotificationManager::mediaMetadataForTrack
                )
        );
        playbackSessionManager = new PlaybackSessionManager(
                this,
                this::createSessionPlayer,
                playbackMediaLibraryCallback,
                this::activityPendingIntent
        );
        playbackBufferedProgressOwner = PlaybackBufferedProgressOwner.fromPlayerProvider(
                playbackPlayerStateOwner,
                () -> player
        );
        playbackVisualizationStateOwner = new PlaybackVisualizationStateOwner(
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
                REPEAT_ALL
        );
        playbackVisualizationCacheStateOwner = new PlaybackVisualizationCacheStateOwner(
                () -> mainHandler,
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                task -> visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, task)
        );
        playbackVisualizationCacheManager = new PlaybackVisualizationCacheManager(
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
                () -> EchoPlaybackService.this.persistPlaybackPositionThrottled(true),
                playbackQueuePersistenceOwner,
                new PlaybackShutdownPlaybackStateOwner(
                        playbackPlayerStateOwner::isPlaying,
                        playbackCurrentTrackPreparationRuntimeOwner::preparing
                ),
                playbackNotificationCommandOwner::hasNotificationWorthyState,
                () -> playbackNotificationCommandOwner.publishPlaybackNotification(true)
        );
        playbackShutdownPlaybackResourcesOwner = new PlaybackShutdownPlaybackResourcesOwner(
                () -> {
                    if (playbackLyricsManager != null) {
                        playbackLyricsManager.release();
                    }
                },
                () -> {
                    if (playbackWifiLockManager != null) {
                        playbackWifiLockManager.release();
                    }
                },
                EchoPlaybackService.this::releasePlayer,
                () -> playbackQueueMirrorStateOwner.setPlayerMirrorsQueue(false),
                () -> playbackCurrentTrackPreparationRuntimeOwner.setPreparing(false)
        );
        playbackShutdownCoordinator = new PlaybackShutdownCoordinator(
                playbackShutdownPlaybackResourcesOwner,
                playbackShutdownServiceResourcesOwner,
                playbackShutdownLifecycleResourcesOwner
        );
        playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(
                this,
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                new PlaybackNotificationArtworkBridgeOwner(
                        playbackSessionRefreshOwner,
                        playbackNotificationCommandOwner::publishPlaybackNotification
                )
        );
        playbackStatePublisher = new PlaybackStatePublisher(
                this::snapshot,
                playbackLyricsManager,
                force -> {
                    if (playbackNotificationManager != null) {
                        playbackNotificationManager.updateMediaNotification(force);
                    }
                },
                playbackNotificationArtworkProviderOwner,
                PlaybackStatePublisherWidgetOwner.fromContextProvider(() -> this)
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
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                PlaybackPrecacheStateOwner.playerMediaItemSupplierFromPlayerSupplier(() -> player),
                () -> streamingDiagnostics
        );
        playbackPrecacheManager = new PlaybackPrecacheManager(
                playbackPrecacheStateOwner,
                playbackQueueStateOwner::upcomingTracksForPrecache,
                PlaybackPrecacheManager.mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider),
                playbackMainHandlerSchedulerOwner,
                PlaybackPrecacheManager.audioCacheReleaseActionFromMediaSourceProvider(mediaSourceProvider)
        );
        playbackQueueRestoreOwner.restorePlaybackQueue();
        playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
        playbackLyricsManager.bind();
        playbackNoisyReceiverManager = new PlaybackNoisyReceiverManager(
                new PlaybackNoisyReceiverRegistrarOwner(EchoPlaybackService.this),
                new PlaybackNoisyReceiverActionsOwner(
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
                () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
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
                    EchoPlaybackService.this,
                    EchoPlaybackService.this::seekTo,
                    EchoPlaybackService.this::setRepeatMode,
                    playbackControllerMediaItemsOwner,
                    () -> playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack(),
                    playbackNotificationManager::mediaMetadataForTrack
            );
        }
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
            persistPlaybackPositionThrottled(true);
            playbackQueuePersistenceOwner.persistQueueState();
            playbackQueuePersistenceOwner.savePlaybackResumeRequested(
                    isPlaying() || playbackCurrentTrackPreparationRuntimeOwner.preparing()
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
            persistPlaybackPositionThrottled(true);
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
        playbackQueueMutationOwner.playQueue(tracks, startIndex);
    }

    public void appendToQueue(List<Track> tracks) {
        playbackQueueMutationOwner.appendToQueue(tracks);
    }

    public void play() {
        if (player == null) {
            if (currentTrack() != null) {
                prepareCurrent(true);
            } else {
                playFirstQueuedTrack();
            }
            return;
        }
        if (playbackCurrentTrackPreparationRuntimeOwner.preparing()) {
            return;
        }
        Track track = currentTrack();
        if (track == null) {
            playFirstQueuedTrack();
            return;
        }
        if (player.getMediaItemCount() == 0) {
            prepareCurrent(true);
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0L);
        }
        player.play();
        playbackQueuePersistenceOwner.requestPlaybackResume();
        if (playbackWifiLockManager != null) {
            playbackWifiLockManager.acquireIfStreaming();
        }
        publishState();
        playbackProgressUpdateCommandOwner.startProgressUpdates();
    }

    private void playFirstQueuedTrack() {
        playbackQueueNavigationOwner.playFirstQueuedTrack();
    }

    public void pause() {
        playbackCrossfadeCommandOwner.cancelCrossfadeAdvance();
        if (player != null && isPlaying()) {
            player.pause();
        }
        playbackQueuePersistenceOwner.clearPlaybackResumeRequest();
        if (playbackWifiLockManager != null) {
            playbackWifiLockManager.release();
        }
        persistPlaybackPositionThrottled(true);
        publishState();
    }

    public void seekTo(long positionMs) {
        if (player == null || playbackCurrentTrackPreparationRuntimeOwner.preparing()) {
            return;
        }
        try {
            player.seekTo(Math.max(0L, positionMs));
            persistPlaybackPositionThrottled(true);
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

    public void moveQueueTrack(int fromIndex, int toIndex) {
        playbackQueueMutationOwner.moveQueueTrack(fromIndex, toIndex);
    }

    public PlaybackStreamingDiagnostics.Snapshot streamingDiagnostics() {
        return streamingDiagnostics.snapshot();
    }

    public void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        playbackCurrentTrackReplacementOwner.replaceCurrentTrackAndResume(replacement, positionMs);
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
        Track track = currentTrack();
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            publishState();
        }
    }

    public void restoreLastPlayback(boolean playWhenRestored) {
        playbackQueueRestoreOwner.restoreLastPlayback(playWhenRestored);
    }

    public void replaceQueuedTrack(Track replacement) {
        playbackQueueMutationOwner.replaceQueuedTrack(replacement);
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
        if (playbackLyricsManager != null) {
            playbackLyricsManager.setStatusBarLyricsEnabled(enabled);
        }
    }

    public void setPlaybackRestoreEnabled(boolean enabled) {
        playbackQueueRestoreOwner.setPlaybackRestoreEnabled(enabled);
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
        Track track = currentTrack();
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
        player.clearMediaItems();
        player.setMediaSources(mediaSources, queuePreparation.startIndex(), Math.max(0L, startPositionMs));
        playbackQueueMirrorStateOwner.setPlayerMirrorsQueue(true);
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
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
        player.stop();
        player.clearMediaItems();
        playbackQueueMirrorStateOwner.setPlayerMirrorsQueue(false);
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
        boolean queueStopPrepared = playbackQueueStopClearOwner.prepareStopAndClearPlaybackState();
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
                playbackPositionManager.clearRestoredPosition();
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

    static int media3RepeatModeForAppRepeatMode(int appRepeatMode) {
        return media3RepeatModeForAppRepeatMode(appRepeatMode, false);
    }

    static int media3RepeatModeForAppRepeatMode(int appRepeatMode, boolean playerMirrorsQueue) {
        if (appRepeatMode == REPEAT_ONE) {
            return Player.REPEAT_MODE_ONE;
        }
        if (!playerMirrorsQueue) {
            return Player.REPEAT_MODE_OFF;
        }
        if (appRepeatMode == REPEAT_OFF) {
            return Player.REPEAT_MODE_OFF;
        }
        return Player.REPEAT_MODE_ALL;
    }

    static int appRepeatModeForMedia3RepeatMode(int media3RepeatMode) {
        if (media3RepeatMode == Player.REPEAT_MODE_ONE) {
            return REPEAT_ONE;
        }
        if (media3RepeatMode == Player.REPEAT_MODE_OFF) {
            return REPEAT_OFF;
        }
        return REPEAT_ALL;
    }

    static boolean isAppQueueNavigationCommand(int command) {
        return command == Player.COMMAND_SEEK_TO_PREVIOUS
                || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                || command == Player.COMMAND_SEEK_TO_PREVIOUS_WINDOW
                || command == Player.COMMAND_SEEK_TO_NEXT
                || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                || command == Player.COMMAND_SEEK_TO_NEXT_WINDOW;
    }

    static boolean isAutomaticMediaItemAdvance(int reason) {
        return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
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
        playbackQueuePersistenceOwner.persistCurrentPlaybackPosition(force);
    }

    private boolean seekExistingMirroredQueue(boolean playWhenReady, long startPositionMs) {
        return playbackQueueNavigationOwner.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
    }

    private void onMirroredQueueReused(boolean playWhenReady) {
        if (playWhenReady && playbackWifiLockManager != null) {
            playbackWifiLockManager.acquireIfStreaming();
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

    private Track currentTrack() {
        return playbackQueueStateOwner.queueStateSnapshot().getCurrentTrack();
    }

    private boolean isPlaying() {
        return playbackPlayerStateOwner.isPlaying();
    }

    private long positionMs() {
        return playbackPlayerStateOwner.positionMs();
    }

    private long durationMs() {
        return playbackPlayerStateOwner.durationMs();
    }
}

