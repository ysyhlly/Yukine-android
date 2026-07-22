package app.yukine.playback;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import app.yukine.diagnostics.DiagnosticLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import app.yukine.R;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.PlaybackServiceHostPort;
import app.yukine.StreamingRepositorySource;
import app.yukine.playback.manager.PlaybackAudioEffectManager;
import app.yukine.playback.manager.PlaybackAudioOutputCoordinator;
import app.yukine.playback.manager.NativeAudioFocusController;
import app.yukine.playback.manager.AudioDeviceCapabilityProbe;
import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackPlayerFactory;
import app.yukine.playback.manager.AudioOutputMode;
import app.yukine.playback.manager.BitPerfectGuard;
import app.yukine.playback.usb.UsbAudioDeviceManager;
import app.yukine.playback.usb.UsbExclusiveAudioSink;
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
import app.yukine.playback.manager.PlaybackRecoveryScheduler;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;
import app.yukine.playback.manager.PlaybackSessionManager;
import app.yukine.playback.manager.PlaybackSleepTimerManager;
import app.yukine.playback.manager.PlaybackTransitionStateManager;
import app.yukine.playback.manager.PlaybackWifiLockManager;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.state.PlaybackStateListener;
import app.yukine.playback.service.PlaybackServiceActions;
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
            new YukineRealtimeBassAudioProcessor(
                    realtimeBassDetector,
                    () -> mainHandler.post(this::onFirstPcmAudioOutput)
            );
    private final PlaybackStreamingDiagnostics streamingDiagnostics = PlaybackStreamingDiagnostics.process();

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
    private PlaybackCachedFingerprintOwner playbackCachedFingerprintOwner;
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
    private NativeAudioFocusController audioFocusController;
    private final NativeAudioFocusController.Callback focusCallback = new NativeAudioFocusController.Callback() {
        @Override public void onFocusGained() { /* no-op, playback already normal */ }
        @Override public void onFocusLostPermanently() {
            if (player != null) player.pause();
        }
        @Override public void onFocusLostTransiently() {
            if (player != null) player.pause();
        }
        @Override public void onPlayRequested() {
            if (player != null) player.play();
        }
    };
    private final Runnable acquireWifiLockIfStreamingAction =
            PlaybackWifiLockManager.acquireIfStreamingAction(() -> playbackWifiLockManager);
    private final Runnable releaseWifiLockAction =
            PlaybackWifiLockManager.releaseAction(() -> playbackWifiLockManager);
    private PlaybackNoisyReceiverManager playbackNoisyReceiverManager;
    private PlaybackProgressUpdateCommandOwner playbackProgressUpdateCommandOwner;
    private PlaybackProgressUpdateManager playbackProgressUpdateManager;
    private final MusicLibraryRepository repository;
    private final PlaybackSourceHealthFeedbackOwner playbackSourceHealthFeedbackOwner;
    private final StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    private final StreamingRepositorySource streamingRepositorySource;
    private final PlaybackPersistenceOwner persistenceOwner;
    private PlaybackAudioEffectSettingsStore playbackAudioEffectSettingsStore;
    private PlaybackMediaSourceProvider mediaSourceProvider;
    private PlaybackPlayerFactory playerFactory;
    private PlaybackRuntimeSettingsStore playbackRuntimeSettingsStore;
    private PlaybackStreamingUrlRecovery playbackStreamingUrlRecovery;
    private final PlaybackTransitionStateManager playbackTransitionStateManager = new PlaybackTransitionStateManager();
    private volatile boolean appVisible;
    private volatile boolean destroyed;
    private volatile boolean bitPerfectEnabled;
    private volatile boolean usbExclusiveEnabled;
    private volatile AudioOutputMode currentAudioOutputMode = AudioOutputMode.STANDARD;
    private final PlaybackAudioOutputCoordinator audioOutputCoordinator =
            new PlaybackAudioOutputCoordinator();
    private long outputReevaluationGeneration;
    private long usbSinkGeneration;
    private boolean usbFallbackInProgress;
    private String bitPerfectFallbackReasonOverride;
    private String usbFallbackReasonOverride;
    private AudioDeviceCapabilityProbe audioDeviceCapabilityProbe;
    private UsbAudioDeviceManager usbAudioDeviceManager;
    private UsbExclusiveAudioSink usbExclusiveAudioSink;
    private Boolean pendingRestorePlayWhenReady;
    private final PlaybackServiceActionBuffer serviceActionBuffer = new PlaybackServiceActionBuffer();

    PlaybackServiceRuntime(
            EchoPlaybackService service,
            MusicLibraryRepository repository,
            StreamingPlaybackHeaderStore streamingPlaybackHeaderStore,
            StreamingRepositorySource streamingRepositorySource,
            PlaybackPersistenceOwner persistenceOwner
    ) {
        this.service = service;
        this.repository = repository;
        playbackSourceHealthFeedbackOwner = new PlaybackSourceHealthFeedbackOwner(
                playbackTaskScheduler,
                repository::recordSuccessfulPlayback
        );
        this.streamingPlaybackHeaderStore = streamingPlaybackHeaderStore;
        this.streamingRepositorySource = streamingRepositorySource;
        this.persistenceOwner = persistenceOwner;
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (player == null) {
                return;
            }
            if (playbackState == Player.STATE_READY) {
                streamingDiagnostics.recordPlayerReady(playbackQueueStateOwner.currentTrack());
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
            DiagnosticLog.e(
                    TAG,
                    "Player error code=" + error.errorCode
                            + ", codeName=" + error.getErrorCodeName()
                            + ", outputMode=" + currentAudioOutputMode,
                    error
            );
            // Offload failure auto-fallback: if we're in HARDWARE_OFFLOAD mode and the error
            // is audio-track related, degrade to DIRECT_PCM and retry transparently.
            if (currentAudioOutputMode == AudioOutputMode.HARDWARE_OFFLOAD
                    && isOffloadRelatedError(error)) {
                fallbackToDirectPcm();
                return;
            }
            // USB exclusive failure auto-fallback: if we're in USB_EXCLUSIVE mode and the error
            // is audio-track related (USB write failure), degrade to DIRECT_PCM.
            if (currentAudioOutputMode == AudioOutputMode.USB_EXCLUSIVE
                    && isUsbSinkRelatedError(error)) {
                fallbackFromUsb(
                        AudioFallbackReason.TRANSFER_FAILED,
                        "USB output initialization failed, using Direct PCM"
                );
                return;
            }
            if (playbackErrorRecoveryManager != null) {
                playbackErrorRecoveryManager.onPlayerError(error);
                return;
            }
            DiagnosticLog.w(TAG, "Playback failed for "
                    + playbackErrorRecoveryCommandOwner.debugTrack(playbackQueueStateOwner.currentTrack()), error);
            playbackErrorRecoveryCommandOwner.setErrorMessage("Unable to play this track.");
            publishState();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady,
                @Player.PlayWhenReadyChangeReason int reason) {
            if (audioFocusController == null) return;
            if (playWhenReady) {
                audioFocusController.acquire();
            } else if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                // User-initiated pause or other reason — release focus.
                // Focus-loss-triggered pause is handled by the controller itself.
                audioFocusController.release();
            }
        }
    };

    private void onFirstPcmAudioOutput() {
        if (destroyed) {
            return;
        }
        Track track = playbackQueueStateOwner.currentTrack();
        streamingDiagnostics.recordFirstAudioOutput(track);
        playbackSourceHealthFeedbackOwner.recordFirstAudioOutput(track);
    }

    @UnstableApi
    void create() {
        destroyed = false;
        serviceActionBuffer.reset();
        persistenceOwner.initialize(mainHandler, this::restorePersistenceSnapshot);
        mediaSourceProvider = new PlaybackMediaSourceProvider(
                service,
                repository::cachedRemoteSource,
                streamingPlaybackHeaderStore
        );
        playbackCurrentTrackPreparationQueueOwner =
                PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager(
                        () -> playbackQueueManager,
                        mediaSourceProvider,
                        track -> playbackNotificationManager == null
                                ? null
                                : playbackNotificationManager.mediaMetadataForTrack(track)
                );
        audioDeviceCapabilityProbe = new AudioDeviceCapabilityProbe(service);
        audioDeviceCapabilityProbe.register(mainHandler);
        // Initialize USB audio device manager for USB exclusive output
        usbAudioDeviceManager = new UsbAudioDeviceManager(service);
        usbAudioDeviceManager.register();
        usbAudioDeviceManager.setOnDeviceChanged(() -> {
            audioDeviceCapabilityProbe.refresh();
            handleAudioDeviceCapabilitiesChanged();
            return null;
        });
        currentAudioOutputMode = audioOutputCoordinator.updateRequests(
                bitPerfectEnabled, usbExclusiveEnabled,
                audioDeviceCapabilityProbe.getCurrentProfile());
        audioOutputCoordinator.onTargetMode(
                currentAudioOutputMode,
                outputNativeSampleRateHz(),
                usbExclusiveDeviceName()
        );
        playerFactory = new PlaybackPlayerFactory(service, realtimeBassAudioProcessor, currentAudioOutputMode, null);
        audioEffectManager.setBitPerfectGuard(new BitPerfectGuard(() -> currentAudioOutputMode));
        audioDeviceCapabilityProbe.setOnDeviceChanged(() -> {
            handleAudioDeviceCapabilitiesChanged();
            return null;
        });
        playbackAudioEffectSettingsStore = new PlaybackAudioEffectSettingsStore(
                persistenceOwner.audioEffectSettings()
        );
        new PlaybackNotificationChannelOwner(service).createNotificationChannel();
        playbackMainHandlerSchedulerOwner = new PlaybackMainHandlerSchedulerOwner(mainHandler);
        PlaybackQueueStore queueStore = persistenceOwner.queueStore();
        playbackModeSettingsStore = new PlaybackModeSettingsStore(persistenceOwner.modeSettings());
        playbackRuntimeSettingsStore = new PlaybackRuntimeSettingsStore(persistenceOwner.runtimeSettings());
        playbackPlayHistoryRecorder = new PlaybackPlayHistoryRecorder(
                persistenceOwner::markPlayed,
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
                (message, error) -> DiagnosticLog.w(TAG, message, error),
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
                        if (audioFocusController != null) {
                            audioFocusController.markUserVolumeChange();
                        }
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
                persistenceOwner::isFavorite,
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
                error -> DiagnosticLog.w(TAG, "Unable to reuse mirrored queue", error)
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
                track -> DiagnosticLog.w(TAG, "Refusing to prepare empty uri for "
                        + playbackErrorRecoveryCommandOwner.debugTrack(track))
        );
        playbackMediaLibraryCallback = new PlaybackMediaLibraryCallback(
                PlaybackMediaLibraryDataSource.fromRepository(
                        service.getString(R.string.app_name),
                        repository,
                        mediaSourceProvider,
                        playbackNotificationManager::mediaMetadataForTrack
                ),
                persistenceOwner.databaseExecutor()
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
                new PlaybackStateSnapshotOwner.RuntimeStateProvider() {
                    @Override public boolean preparing() { return playbackRuntimeStateManager.preparing(); }
                    @Override public String errorMessage() { return playbackRuntimeStateManager.errorMessage(); }
                    @Override public boolean shuffleEnabled() { return playbackRuntimeStateManager.shuffleEnabled(); }
                    @Override public int repeatMode() { return playbackRuntimeStateManager.repeatMode(); }
                    @Override public float playbackSpeed() { return playbackRuntimeStateManager.playbackSpeed(); }
                    @Override public float appVolume() { return playbackRuntimeStateManager.appVolume(); }
                    @Override public boolean bitPerfectActive() { return PlaybackServiceRuntime.this.bitPerfectActive(); }
                    @Override public int outputSampleRateHz() { return outputNativeSampleRateHz(); }
                    @Override public String bitPerfectFallbackReason() { return PlaybackServiceRuntime.this.bitPerfectFallbackReason(); }
                    @Override public boolean audioExclusiveActive() {
                        return audioFocusController != null && audioFocusController.isExclusiveMode();
                    }
                    @Override public AudioOutputSnapshot audioOutput() {
                        return audioOutputCoordinator.snapshot();
                    }
                },
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
        playbackCachedFingerprintOwner = new PlaybackCachedFingerprintOwner(
                service,
                repository,
                mediaSourceProvider,
                mainHandler,
                playbackQueueStateOwner::currentTrack,
                task -> visualizationTaskScheduler.schedule(
                        PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE,
                        task
                )
        );
        playbackWarmupCoordinator = new PlaybackWarmupCoordinator(
                PlaybackPrecacheManager.precacheTrackActionFromSupplier(() -> playbackPrecacheManager),
                PlaybackVisualizationCacheManager.scheduleVisualizationCacheActionFromSupplier(
                        () -> playbackVisualizationCacheManager
                ),
                playbackCachedFingerprintOwner::schedule
        );
        playbackShutdownServiceResourcesOwner = new PlaybackShutdownServiceResourcesOwner(
                PlaybackShutdownServiceResourcesOwner.releaseFrom(
                        () -> playbackNoisyReceiverManager,
                        PlaybackNoisyReceiverManager::unregister
                ),
                () -> {
                    if (playbackWarmupCoordinator != null) {
                        playbackWarmupCoordinator.release();
                    }
                    if (playbackCachedFingerprintOwner != null) {
                        playbackCachedFingerprintOwner.release();
                    }
                },
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
                service::clearPlaybackNotification,
                persistenceOwner::flushPendingWrites
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
        playbackStatePublisher.registerListener(IdentityEnhancementPlaybackGate::update);
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
            int wifiMode = PlaybackWifiLockOwner.preferredModeForSdk(android.os.Build.VERSION.SDK_INT);
            wifiLock = wifiManager.createWifiLock(wifiMode, "echo:playback");
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
        if (PlaybackServiceActions.STOP.equals(action)) {
            pendingRestorePlayWhenReady = null;
        }
        for (String dispatchableAction : serviceActionBuffer.accept(action)) {
            dispatchServiceAction(dispatchableAction);
        }
    }

    boolean requiresBootstrapForeground(String action) {
        return serviceActionBuffer.requiresBootstrapForeground(action);
    }

    private void dispatchServiceAction(String action) {
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
        destroyed = true;
        IdentityEnhancementPlaybackGate.clear();
        if (audioDeviceCapabilityProbe != null) {
            audioDeviceCapabilityProbe.unregister();
        }
        if (usbAudioDeviceManager != null) {
            usbAudioDeviceManager.unregister();
            usbAudioDeviceManager = null;
        }
        usbSinkGeneration++;
        usbExclusiveAudioSink = null;
        if (audioFocusController != null) {
            audioFocusController.destroy();
        }
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
        // Directly acquire focus — don't rely solely on onPlayWhenReadyChanged,
        // which won't fire if playWhenReady is already true (e.g., during buffering).
        if (audioFocusController != null) {
            audioFocusController.acquire();
        }
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
        if (persistenceOwner.toggleFavorite(track)) {
            publishState();
        }
    }

    private void restorePersistenceSnapshot() {
        if (destroyed || playbackShutdownCoordinator == null) {
            return;
        }
        playbackAudioEffectSettingsStore.restore();
        playbackModeSettingsStore.restoreInto(playbackRuntimeStateManager);
        playbackRuntimeSettingsStore.restoreInto(playbackRuntimeStateManager);
        // Rebuild player factory if bit-perfect or USB exclusive was persisted as enabled.
        boolean restoredBitPerfect = playbackRuntimeStateManager.bitPerfectActive();
        boolean restoredUsbExclusive = persistenceOwner.usbExclusiveEnabled();
        if (restoredBitPerfect != bitPerfectEnabled || restoredUsbExclusive != usbExclusiveEnabled) {
            bitPerfectEnabled = restoredBitPerfect;
            usbExclusiveEnabled = restoredUsbExclusive;
            currentAudioOutputMode = audioOutputCoordinator.updateRequests(
                    bitPerfectEnabled, usbExclusiveEnabled,
                    audioDeviceCapabilityProbe.getCurrentProfile());
            audioOutputCoordinator.onTargetMode(
                    currentAudioOutputMode,
                    outputNativeSampleRateHz(),
                    usbExclusiveDeviceName()
            );
            if (currentAudioOutputMode == AudioOutputMode.USB_EXCLUSIVE && usbAudioDeviceManager != null) {
                usbExclusiveAudioSink = createUsbExclusiveAudioSink();
            } else {
                usbExclusiveAudioSink = null;
            }
            playerFactory = new PlaybackPlayerFactory(service, realtimeBassAudioProcessor, currentAudioOutputMode, usbExclusiveAudioSink);
        }
        PlaybackLyricsSettingsStore lyricsSettingsStore =
                new PlaybackLyricsSettingsStore(persistenceOwner.lyricsSettings());
        lyricsSettingsStore.restoreInto(playbackLyricsManager);
        applyEffectiveFocusMode();
        applyPlaybackModeAndParametersToPlayer();
        if (!bitPerfectActive()) {
            audioEffectManager.bind(player, playbackAudioEffectSettingsStore.current());
        }
        Boolean pendingRestore = pendingRestorePlayWhenReady;
        pendingRestorePlayWhenReady = null;
        List<String> serviceActions = serviceActionBuffer.markReadyAndDrain();
        if (pendingRestore != null) {
            restoreLastPlayback(pendingRestore);
        } else if (playbackQueueManager != null && playbackQueueManager.queueSnapshot().isEmpty()) {
            playbackQueueManager.restorePlaybackQueue();
        }
        publishState();
        playbackNotificationCommandOwner.publishPlaybackNotificationIfWorthy();
        for (String serviceAction : serviceActions) {
            if (destroyed) {
                break;
            }
            dispatchServiceAction(serviceAction);
        }
    }

    public void restoreLastPlayback(boolean playWhenRestored) {
        if (!serviceActionBuffer.isReady()) {
            pendingRestorePlayWhenReady = pendingRestorePlayWhenReady == null
                    ? playWhenRestored
                    : pendingRestorePlayWhenReady || playWhenRestored;
            return;
        }
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

    public float realtimeTransientBeat() {
        return playbackRealtimeVisualizationOwner == null ? 0f : playbackRealtimeVisualizationOwner.transientBeat();
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
        persistenceOwner.updatePlaybackSpeed(speed);
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
        persistenceOwner.updateAppVolume(volume);
        publishState();
    }

    public float appVolume() {
        return playbackRuntimeSettingsStore == null
                ? 1.0f
                : playbackRuntimeSettingsStore.appVolume(playbackRuntimeStateManager);
    }

    public void setConcurrentPlaybackEnabled(boolean enabled) {
        // Inverted: concurrentPlaybackEnabled=false means exclusive mode is ON.
        if (audioFocusController != null) {
            audioFocusController.setMode(enabled
                    ? NativeAudioFocusController.Mode.COOPERATIVE
                    : NativeAudioFocusController.Mode.EXCLUSIVE);
        }
    }

    public void setAudioExclusiveEnabled(boolean enabled) {
        persistenceOwner.updateAudioExclusiveEnabled(enabled);
        applyEffectiveFocusMode();
    }

    public void setBitPerfectEnabled(boolean enabled) {
        if (bitPerfectEnabled == enabled) {
            return;
        }
        bitPerfectEnabled = enabled;
        bitPerfectFallbackReasonOverride = null;
        persistenceOwner.updateBitPerfectEnabled(enabled);
        playbackRuntimeStateManager.setBitPerfectActive(enabled);
        audioEffectManager.onBitPerfectStateChanged(enabled);
        // Resolve the appropriate output mode based on device capabilities.
        if (audioDeviceCapabilityProbe == null) return;
        AudioOutputMode targetMode = audioOutputCoordinator.updateRequests(
                enabled,
                usbExclusiveEnabled,
                audioDeviceCapabilityProbe.getCurrentProfile()
        );
        currentAudioOutputMode = targetMode;
        audioOutputCoordinator.onTargetMode(targetMode, outputNativeSampleRateHz(), usbExclusiveDeviceName());
        // Rebuild the player factory with the new mode and recreate the player
        // to apply the offload/standard AudioSink configuration.
        if (targetMode == AudioOutputMode.USB_EXCLUSIVE && usbAudioDeviceManager != null) {
            usbExclusiveAudioSink = createUsbExclusiveAudioSink();
        } else if (targetMode != AudioOutputMode.USB_EXCLUSIVE) {
            usbExclusiveAudioSink = null;
        }
        playerFactory = new PlaybackPlayerFactory(service, realtimeBassAudioProcessor, targetMode, usbExclusiveAudioSink);
        rebuildPlayerPreservingPosition(targetMode);
        publishState();
    }

    public boolean bitPerfectEnabled() {
        return bitPerfectEnabled;
    }

    public boolean bitPerfectActive() {
        return bitPerfectEnabled && playbackRuntimeStateManager.bitPerfectActive();
    }

    public int outputNativeSampleRateHz() {
        return audioDeviceCapabilityProbe != null
                ? audioDeviceCapabilityProbe.getCurrentProfile().getNativeSampleRateHz()
                : 48000;
    }

    public String bitPerfectFallbackReason() {
        if (!bitPerfectEnabled) return null;
        if (bitPerfectFallbackReasonOverride != null) return bitPerfectFallbackReasonOverride;
        AudioDeviceCapabilityProbe.AudioDeviceProfile profile =
                audioDeviceCapabilityProbe != null
                        ? audioDeviceCapabilityProbe.getCurrentProfile()
                        : null;
        if (profile != null && !profile.getSupportsOffload()) {
            return "Device does not advertise offload support, using Direct PCM";
        }
        return null;
    }

    public void setUsbExclusiveEnabled(boolean enabled) {
        if (usbExclusiveEnabled == enabled) {
            return;
        }
        usbExclusiveEnabled = enabled;
        usbFallbackReasonOverride = null;
        persistenceOwner.updateUsbExclusiveEnabled(enabled);
        // Resolve the appropriate output mode based on device capabilities.
        if (audioDeviceCapabilityProbe == null) return;
        AudioOutputMode targetMode = audioOutputCoordinator.updateRequests(
                bitPerfectEnabled, usbExclusiveEnabled,
                audioDeviceCapabilityProbe.getCurrentProfile());
        currentAudioOutputMode = targetMode;
        audioOutputCoordinator.onTargetMode(targetMode, outputNativeSampleRateHz(), usbExclusiveDeviceName());
        // Create USB audio sink if needed
        if (targetMode == AudioOutputMode.USB_EXCLUSIVE && usbAudioDeviceManager != null) {
            usbExclusiveAudioSink = createUsbExclusiveAudioSink();
        } else {
            usbExclusiveAudioSink = null;
        }
        // Rebuild the player factory with the new mode and recreate the player.
        playerFactory = new PlaybackPlayerFactory(
                service, realtimeBassAudioProcessor, targetMode, usbExclusiveAudioSink);
        rebuildPlayerPreservingPosition(targetMode);
        if (targetMode != AudioOutputMode.USB_EXCLUSIVE && usbAudioDeviceManager != null) {
            usbAudioDeviceManager.closeConnection();
        }
        publishState();
    }

    public boolean usbExclusiveEnabled() {
        return usbExclusiveEnabled;
    }

    public boolean usbExclusiveActive() {
        return usbExclusiveEnabled && audioOutputCoordinator.usbActive();
    }

    /**
     * Resolves the effective audio focus mode.
     * USB transport policy never changes focus policy; only the audio-exclusive preference does.
     */
    private NativeAudioFocusController.Mode resolveEffectiveFocusMode() {
        if (persistenceOwner.audioExclusiveEnabled()) {
            return NativeAudioFocusController.Mode.EXCLUSIVE;
        }
        return NativeAudioFocusController.Mode.COOPERATIVE;
    }

    /**
     * Syncs the focus controller to the effective mode. Skips if mode unchanged.
     * Re-acquires focus if currently playing and mode changed.
     */
    private void applyEffectiveFocusMode() {
        if (audioFocusController == null) return;
        NativeAudioFocusController.Mode effective = resolveEffectiveFocusMode();
        boolean currentlyExclusive = audioFocusController.isExclusiveMode();
        if (currentlyExclusive == (effective == NativeAudioFocusController.Mode.EXCLUSIVE)) {
            return; // Mode unchanged, skip
        }
        audioFocusController.setMode(effective);
        if (player != null && player.getPlayWhenReady()) {
            audioFocusController.release();
            audioFocusController.acquire();
        }
    }

    public String usbExclusiveDeviceName() {
        if (usbAudioDeviceManager == null) return "";
        UsbAudioDeviceManager.UsbAudioDeviceInfo info = usbAudioDeviceManager.getActiveDevice();
        return info != null ? info.getDeviceName() : "";
    }

    public String usbFallbackReason() {
        if (!usbExclusiveEnabled) return null;
        if (usbFallbackReasonOverride != null) return usbFallbackReasonOverride;
        AudioDeviceCapabilityProbe.AudioDeviceProfile profile =
                audioDeviceCapabilityProbe != null
                        ? audioDeviceCapabilityProbe.getCurrentProfile()
                        : null;
        if (profile != null && !profile.isUsbAudioDeviceConnected()) {
            return "No USB audio device connected";
        }
        return null;
    }

    public AudioOutputMode currentAudioOutputMode() {
        return currentAudioOutputMode;
    }

    private UsbExclusiveAudioSink createUsbExclusiveAudioSink() {
        final long sinkGeneration = ++usbSinkGeneration;
        usbFallbackInProgress = false;
        return new UsbExclusiveAudioSink(usbAudioDeviceManager, bitPerfectEnabled, snapshot ->
                mainHandler.post(() -> {
                    if (destroyed || sinkGeneration != usbSinkGeneration) return;
                    audioOutputCoordinator.onUsbSnapshot(snapshot);
                    if ((snapshot.phase == AudioOutputPhase.FALLBACK
                            || snapshot.phase == AudioOutputPhase.ERROR)
                            && currentAudioOutputMode == AudioOutputMode.USB_EXCLUSIVE) {
                        fallbackFromUsb(snapshot.fallbackReason, snapshot.lastError);
                    } else {
                        publishState();
                    }
                })
        );
    }

    private void scheduleOutputReevaluation(boolean immediate) {
        final long generation = ++outputReevaluationGeneration;
        Runnable reevaluate = () -> {
            if (destroyed || generation != outputReevaluationGeneration) return;
            AudioOutputMode resolved = audioOutputCoordinator.updateRequests(
                    bitPerfectEnabled,
                    usbExclusiveEnabled,
                    audioDeviceCapabilityProbe.getCurrentProfile()
            );
            switchOutputMode(resolved);
        };
        if (immediate) {
            mainHandler.post(reevaluate);
        } else {
            mainHandler.postDelayed(reevaluate, 2_000L);
        }
    }

    private void handleAudioDeviceCapabilitiesChanged() {
        if (destroyed || (!bitPerfectEnabled && !usbExclusiveEnabled)) return;
        AudioOutputMode newMode = audioOutputCoordinator.updateRequests(
                bitPerfectEnabled,
                usbExclusiveEnabled,
                audioDeviceCapabilityProbe.getCurrentProfile()
        );
        if (newMode == currentAudioOutputMode) return;
        boolean usbDetached = currentAudioOutputMode == AudioOutputMode.USB_EXCLUSIVE
                && newMode != AudioOutputMode.USB_EXCLUSIVE;
        if (usbDetached) {
            audioOutputCoordinator.onSystemFallback(
                    newMode,
                    outputNativeSampleRateHz(),
                    AudioFallbackReason.DEVICE_DETACHED,
                    "USB audio device detached"
            );
        }
        // Both Android AudioDeviceCallback and USB broadcasts may describe the same change.
        // The generation token in scheduleOutputReevaluation collapses them into one switch.
        scheduleOutputReevaluation(usbDetached);
    }

    public boolean concurrentPlaybackEnabled() {
        return !persistenceOwner.audioExclusiveEnabled();
    }

    public void setStatusBarLyricsEnabled(boolean enabled) {
        statusBarLyricsEnabledAction.accept(enabled);
        persistenceOwner.updateStatusBarLyricsEnabled(enabled);
    }

    public void setSystemMediaLyricsTitleEnabled(boolean enabled) {
        systemMediaLyricsTitleEnabledAction.accept(enabled);
        persistenceOwner.updateSystemMediaLyricsTitleEnabled(enabled);
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
        persistenceOwner.updateReplayGainEnabled(enabled);
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
        if (!bitPerfectActive()) {
            audioEffectManager.bind(player, appliedSettings);
        }
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
        prepareCurrent(playWhenReady, C.TIME_UNSET);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareCurrent(final boolean playWhenReady, final long explicitStartPositionMs) {
        Track track = playbackQueueStateOwner.currentTrack();
        if (track == null) {
            return;
        }
        AudioFallbackReason dsdBlockReason = mediaSourceProvider.dsdPlaybackBlockReason(
                track,
                bitPerfectEnabled,
                usbExclusiveEnabled
        );
        if (dsdBlockReason != null) {
            String message = dsdBlockReason == AudioFallbackReason.REMOTE_DSD_NOT_CACHED
                    ? "Remote DSD files must be fully downloaded before playback."
                    : "DSD playback requires both Bit-Perfect and USB exclusive output.";
            audioOutputCoordinator.onSystemFallback(
                    currentAudioOutputMode,
                    outputNativeSampleRateHz(),
                    dsdBlockReason,
                    message
            );
            playbackErrorRecoveryCommandOwner.setErrorMessage(message);
            publishState();
            return;
        }
        PlaybackCurrentTrackPreparationOwner.PreparedTrack preparedTrack =
                playbackCurrentTrackPreparationOwner.prepareCurrentTrack(track, explicitStartPositionMs);
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
        try {
            createPlayerIfNeeded();
        } catch (Exception error) {
            DiagnosticLog.w(TAG, "Player creation failed for " + playbackErrorRecoveryCommandOwner.debugTrack(track), error);
            playbackCurrentTrackPreparationRuntimeOwner.markUnableToOpenCurrentTrack();
            publishState();
            return;
        }
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
            streamingDiagnostics.recordPrepareStarted(track);
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
            DiagnosticLog.w(TAG, "Unable to prepare mirrored queue for "
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
        try {
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
            streamingDiagnostics.recordPrepareStarted(track);
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
        } catch (RuntimeException error) {
            DiagnosticLog.w(TAG, "Unable to prepare player for "
                    + playbackErrorRecoveryCommandOwner.debugTrack(track), error);
            playbackCurrentTrackPreparationRuntimeOwner.markUnableToOpenCurrentTrack();
            releasePlaybackPlayerResources();
            publishState();
        }
    }

    private void releasePlayer() {
        if (audioFocusController != null) {
            audioFocusController.release();
        }
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
        if (audioFocusController != null) {
            audioFocusController.markUserVolumeChange();
        }
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.applyPlaybackParametersToPlayer(playbackRuntimeStateManager);
        }
    }

    private void applyCurrentTrackVolumeToPlayer() {
        if (audioFocusController != null) {
            audioFocusController.markUserVolumeChange();
        }
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.applyCurrentTrackVolumeToPlayer(playbackRuntimeStateManager);
        }
    }

    private void applyPlaybackModeAndParametersToPlayer() {
        applyPlaybackParametersToPlayer();
        applyPlaybackModeToPlayer();
    }

    private void applyAudioAttributes() {
        if (playbackRuntimeSettingsStore != null) {
            playbackRuntimeSettingsStore.applyAudioAttributes(playbackRuntimeStateManager);
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
        // Always disable Media3 internal focus management — focus is fully handled
        // by NativeAudioFocusController via native AudioManager APIs.
        applyAudioAttributes();
        // Create native audio focus controller (no initialization order dependency).
        if (audioFocusController == null) {
            audioFocusController = new NativeAudioFocusController(service, new Handler(Looper.getMainLooper()));
            audioFocusController.setCallback(focusCallback);
        }
        audioFocusController.setMode(resolveEffectiveFocusMode());
        // If already in playing state, acquire focus immediately.
        if (player.getPlayWhenReady()) {
            audioFocusController.acquire();
        }
        player.addListener(playerListener);
        applyPlaybackModeAndParametersToPlayer();
        // Skip audio effects binding when Bit-Perfect output is active
        // (EQ/BassBoost/Virtualizer are incompatible with offload/direct PCM).
        if (!bitPerfectActive()) {
            audioEffectManager.bind(player, audioEffectSettings());
        }
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

    /**
     * Switches the audio output mode at runtime, rebuilding the player to apply
     * the new AudioSink configuration. Preserves playback position.
     */
    private void switchOutputMode(AudioOutputMode newMode) {
        if (destroyed || newMode == currentAudioOutputMode) return;
        DiagnosticLog.w(TAG, "Switching audio output mode: " + currentAudioOutputMode + " -> " + newMode);
        currentAudioOutputMode = newMode;
        audioOutputCoordinator.onTargetMode(newMode, outputNativeSampleRateHz(), usbExclusiveDeviceName());
        // Create USB audio sink if switching to USB exclusive mode
        if (newMode == AudioOutputMode.USB_EXCLUSIVE && usbAudioDeviceManager != null) {
            usbExclusiveAudioSink = createUsbExclusiveAudioSink();
        } else {
            usbExclusiveAudioSink = null;
        }
        playerFactory = new PlaybackPlayerFactory(
                service, realtimeBassAudioProcessor, newMode, usbExclusiveAudioSink);
        rebuildPlayerPreservingPosition(newMode);
        if (newMode != AudioOutputMode.USB_EXCLUSIVE && usbAudioDeviceManager != null) {
            // The old player's sink must cancel native transfers before its permission-backed
            // connection is closed. Closing the FD first can enqueue a stale transfer failure
            // that tears down the freshly rebuilt Direct PCM player.
            usbAudioDeviceManager.closeConnection();
        }
        publishState();
    }

    private void rebuildPlayerPreservingPosition(AudioOutputMode outputMode) {
        ExoPlayer previousPlayer = player;
        boolean hadPlayer = previousPlayer != null;
        boolean playWhenReady = hadPlayer && previousPlayer.getPlayWhenReady();
        long savedPositionMs = hadPlayer ? positionMs() : 0L;
        releasePlaybackPlayerResources();
        if (!hadPlayer) {
            return;
        }
        prepareCurrent(playWhenReady, savedPositionMs);
        if (outputMode != AudioOutputMode.STANDARD) {
            audioEffectManager.bind(null, null);
        }
    }

    /**
     * Falls back from HARDWARE_OFFLOAD to DIRECT_PCM when an offload-related error occurs.
     * Preserves playback position and resumes playback.
     */
    private void fallbackToDirectPcm() {
        DiagnosticLog.w(TAG, "Offload error detected, falling back to DIRECT_PCM");
        bitPerfectFallbackReasonOverride = "Offload failed, using Direct PCM";
        audioOutputCoordinator.onSystemFallback(
                AudioOutputMode.DIRECT_PCM,
                outputNativeSampleRateHz(),
                AudioFallbackReason.OFFLOAD_FAILED,
                bitPerfectFallbackReasonOverride
        );
        switchOutputMode(AudioOutputMode.DIRECT_PCM);
    }

    /**
     * Falls back from USB_EXCLUSIVE to DIRECT_PCM when a USB write error occurs.
     * Preserves playback position and resumes playback.
     */
    private void fallbackFromUsb() {
        fallbackFromUsb(AudioFallbackReason.TRANSFER_FAILED, "USB output failed, using Direct PCM");
    }

    private void fallbackFromUsb(AudioFallbackReason reason, String error) {
        if (destroyed || currentAudioOutputMode != AudioOutputMode.USB_EXCLUSIVE
                || usbFallbackInProgress) {
            return;
        }
        usbFallbackInProgress = true;
        DiagnosticLog.w(TAG, "USB exclusive error detected, falling back to DIRECT_PCM");
        usbFallbackReasonOverride = error;
        audioOutputCoordinator.onSystemFallback(
                AudioOutputMode.DIRECT_PCM,
                outputNativeSampleRateHz(),
                reason,
                error
        );
        try {
            switchOutputMode(AudioOutputMode.DIRECT_PCM);
        } finally {
            usbFallbackInProgress = false;
        }
    }

    private boolean isOffloadRelatedError(PlaybackException error) {
        int code = error.errorCode;
        return code == PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED
                || code == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED;
    }

    private boolean isUsbSinkRelatedError(PlaybackException error) {
        if (error == null) return false;
        if (isOffloadRelatedError(error)
                || error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED) {
            return true;
        }
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof androidx.media3.exoplayer.audio.AudioSink.ConfigurationException) {
                return true;
            }
            for (StackTraceElement frame : cause.getStackTrace()) {
                if (frame.getClassName().startsWith(
                        "app.yukine.playback.usb.UsbExclusiveAudioSink")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

}
