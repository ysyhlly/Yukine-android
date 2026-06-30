package app.yukine.playback;

import android.app.PendingIntent;
import android.app.Notification;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import app.yukine.R;
import app.yukine.common.EmbeddedArtwork;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.NowPlayingPlaybackServicePort;
import app.yukine.SettingsPlaybackServicePort;
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
import app.yukine.model.Playlist;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;
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
import android.util.Base64;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
@OptIn(markerClass = UnstableApi.class)
public final class EchoPlaybackService extends MediaLibraryService implements NowPlayingPlaybackServicePort, SettingsPlaybackServicePort {
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

    private static final String CHANNEL_ID = "echo_next_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "EchoPlaybackService";
    private static final String EMPTY_NOTIFICATION_TITLE = "Yukine";
    private static final String EMPTY_NOTIFICATION_TEXT = "Ready to resume playback";
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";
    private static final int NOTIFICATION_ACCENT = 0xFFEBC9A6;
    private static final long PLAYBACK_VISUALIZATION_CACHE_DELAY_MS = 1800L;
    private static final float[] EMPTY_REALTIME_BANDS = new float[0];
    private static final int NOTIFICATION_ARTWORK_TARGET_PX = 512;
    private static final int NOTIFICATION_ARTWORK_CACHE_ENTRIES = 8;
    private static final long FOREGROUND_NOTIFICATION_MIN_INTERVAL_MS = 900L;
    private static final long BACKGROUND_NOTIFICATION_MIN_INTERVAL_MS = 4500L;
    private static final long BACKGROUND_LYRIC_NOTIFICATION_MIN_INTERVAL_MS = 5000L;
    private static final String AUTO_ROOT = "echo:auto:root";
    private static final String AUTO_ALL = "echo:auto:all";
    private static final String AUTO_RECENT = "echo:auto:recent";
    private static final String AUTO_PLAYLISTS = "echo:auto:playlists";
    private static final String AUTO_PLAYLIST_PREFIX = "echo:auto:playlist:";
    private static final String AUTO_ARTISTS = "echo:auto:artists";
    private static final String AUTO_ARTIST_PREFIX = "echo:auto:artist:";
    private static final String AUTO_ALBUMS = "echo:auto:albums";
    private static final String AUTO_ALBUM_PREFIX = "echo:auto:album:";
    private static final String AUTO_TRACK_PREFIX = "echo:auto:track:";
    private final LocalBinder binder = new LocalBinder();
    private final CopyOnWriteArrayList<Track> queue = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final PlaybackTaskScheduler playbackTaskScheduler =
            new PlaybackTaskScheduler("EchoPlaybackScheduler", Process.THREAD_PRIORITY_AUDIO);
    private final PlaybackTaskScheduler visualizationTaskScheduler =
            new PlaybackTaskScheduler("YukineVisualizationScheduler", Process.THREAD_PRIORITY_BACKGROUND);
    private final RealtimeBassDetector realtimeBassDetector = new RealtimeBassDetector();
    private final YukineRealtimeBassAudioProcessor realtimeBassAudioProcessor =
            new YukineRealtimeBassAudioProcessor(realtimeBassDetector);
    private final PlaybackStreamingDiagnostics streamingDiagnostics = new PlaybackStreamingDiagnostics();

    private ExoPlayer player;
    private final PlaybackQueueRuntimeStateManager playbackQueueRuntimeStateManager = new PlaybackQueueRuntimeStateManager();
    private final PlaybackRuntimeStateManager playbackRuntimeStateManager =
            new PlaybackRuntimeStateManager(new PlaybackRuntimeStateManager.StateProvider() {
                @Override
                public ExoPlayer player() {
                    return player;
                }

                @Override
                public boolean playerMirrorsQueue() {
                    return playbackQueueRuntimeStateManager.playerMirrorsQueue();
                }

                @Override
                public Track currentTrack() {
                    return EchoPlaybackService.this.currentTrack();
                }
            });
    private final PlaybackAudioEffectManager audioEffectManager =
            new PlaybackAudioEffectManager(TAG);
    private PlaybackSessionManager playbackSessionManager;
    private app.yukine.playback.manager.LyricsPublisher playbackLyricsManager;
    private PlaybackMediaLibraryCallback playbackMediaLibraryCallback;
    private PlaybackStatePublisher playbackStatePublisher;
    private PlaybackErrorRecoveryManager playbackErrorRecoveryManager;
    private PlaybackQueueStore queueStore;
    private PlaybackQueueManager playbackQueueManager;
    private PlaybackPositionManager playbackPositionManager;
    private PlaybackNotificationManager playbackNotificationManager;
    private PlaybackSleepTimerManager playbackSleepTimerManager;
    private PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer;
    private PlaybackVisualizationCacheManager playbackVisualizationCacheManager;
    private PlaybackNotificationArtworkManager playbackNotificationArtworkManager;
    private PlaybackPrecacheManager playbackPrecacheManager;
    private PlaybackWarmupCoordinator playbackWarmupCoordinator;
    private PlaybackCrossfadeAdvanceManager playbackCrossfadeAdvanceManager;
    private PlaybackRecoveryScheduler playbackRecoveryScheduler;
    private PlaybackShutdownCoordinator playbackShutdownCoordinator;
    private PlaybackWifiLockManager playbackWifiLockManager;
    private PlaybackNoisyReceiverManager playbackNoisyReceiverManager;
    private PlaybackProgressUpdateManager playbackProgressUpdateManager;
    @Inject
    MusicLibraryRepository repository;
    @Inject
    StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    @Inject
    ToggleFavoriteUseCase toggleFavoriteUseCase;
    private AudioEffectSettings audioEffectSettings = AudioEffectSettings.DEFAULT;
    private PlaybackMediaSourceProvider mediaSourceProvider;
    private PlaybackPlayerFactory playerFactory;
    private final PlaybackTransitionStateManager playbackTransitionStateManager = new PlaybackTransitionStateManager();
    private volatile boolean appVisible;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (player == null) {
                return;
            }
            if (playbackState == Player.STATE_READY) {
                playbackRuntimeStateManager.setPreparing(false);
                playbackRuntimeStateManager.setErrorMessage("");
                if (playbackErrorRecoveryManager != null) {
                    playbackErrorRecoveryManager.onPlaybackReady();
                }
                Track track = currentTrack();
                if (player.getPlayWhenReady() && track != null && (playbackTransitionStateManager.lastMarkedTrack() == null || playbackTransitionStateManager.lastMarkedTrack().id != track.id)) {
                    repository.markPlayed(track.id);
                    playbackTransitionStateManager.setLastMarkedTrack(track);
                }
            } else if (playbackState == Player.STATE_ENDED) {
                playAfterCompletion();
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
            if (!playbackQueueRuntimeStateManager.playerMirrorsQueue()
                    || player == null
                    || queueStateSnapshot().isQueueEmpty()) {
                return;
            }
            int nextIndex = player.getCurrentMediaItemIndex();
            PlaybackQueueManager.MirroredTransitionResult transition = playbackQueueManager == null
                    ? null
                    : playbackQueueManager.applyMirroredTransitionIndex(nextIndex, isAutomaticMediaItemAdvance(reason));
            if (transition == null) {
                return;
            }
            if (transition.getStopAfterAutomaticAdvance()) {
                stopAfterAutomaticAdvance(transition.getCompletedIndex());
                return;
            }
            Track track = currentTrack();
            playbackQueueManager.prepareMirroredTransitionPlaybackState();
            if (track != null) {
                resetWaveformIfTrackChanged(track);
            }
            if (playbackRuntimeStateManager != null) {
                playbackRuntimeStateManager.applyCurrentTrackVolumeToPlayer();
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
                if (playbackWifiLockManager != null) {
                    playbackWifiLockManager.acquireIfStreaming();
                }
            } else {
                if (playbackWifiLockManager != null) {
                    playbackWifiLockManager.release();
                }
            }
            publishState();
            startProgressUpdates();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            playbackRuntimeStateManager.setPreparing(false);
            if (playbackErrorRecoveryManager != null) {
                playbackErrorRecoveryManager.onPlayerError(error);
                return;
            }
            Log.w(TAG, "Playback failed for " + debugTrack(currentTrack()), error);
            playbackRuntimeStateManager.setErrorMessage("Unable to play this track.");
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
        audioEffectSettings = repository.loadAudioEffectSettings();
        new PlaybackNotificationChannelOwner(this).createNotificationChannel();
        queueStore = new PlaybackQueueStoreImpl(repository);
        playbackPositionManager = new PlaybackPositionManager(queueStore, new PlaybackPositionManager.StateProvider() {
            @Override
            public Track currentTrack() {
                return EchoPlaybackService.this.currentTrack();
            }

            @Override
            public long positionMs() {
                return EchoPlaybackService.this.positionMs();
            }
        });
        playbackSleepTimerManager = new PlaybackSleepTimerManager(
                new PlaybackSleepTimerManager.CallbackScheduler() {
                    @Override
                    public void postDelayed(Runnable runnable, long delayMs) {
                        mainHandler.postDelayed(runnable, delayMs);
                    }

                    @Override
                    public void removeCallbacks(Runnable runnable) {
                        mainHandler.removeCallbacks(runnable);
                    }
                },
                new PlaybackSleepTimerManager.Actions() {
                    @Override
                    public void pausePlayback() {
                        EchoPlaybackService.this.pause();
                    }

                    @Override
                    public void publishState() {
                        EchoPlaybackService.this.publishState();
                    }
                }
        );
        playbackErrorRecoveryManager = new PlaybackErrorRecoveryManager(
                new PlaybackErrorRecoveryManager.RetryScheduler() {
                    @Override
                    public void postDelayed(Runnable runnable, long delayMs) {
                        mainHandler.postDelayed(runnable, delayMs);
                    }

                    @Override
                    public void removeCallbacks(Runnable runnable) {
                        mainHandler.removeCallbacks(runnable);
                    }
                },
                new PlaybackErrorRecoveryManager.Actions() {
                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }

                    @Override
                    public boolean canSkipFailedTrack(Track failed) {
                        return playbackQueueManager != null
                                && playbackQueueManager.canSkipFailedTrack(failed);
                    }

                    @Override
                    public String debugTrack(Track track) {
                        return EchoPlaybackService.this.debugTrack(track);
                    }

                    @Override
                    public void prepareCurrent(boolean playWhenReady) {
                        EchoPlaybackService.this.prepareCurrent(playWhenReady);
                    }

                    @Override
                    public void skipToNext() {
                        EchoPlaybackService.this.skipToNext();
                    }

                    @Override
                    public void setErrorMessage(String message) {
                        playbackRuntimeStateManager.setErrorMessage(message);
                    }

                    @Override
                    public void publishState() {
                        EchoPlaybackService.this.publishState();
                    }

                    @Override
                    public void logWarning(String message, Exception error) {
                        Log.w(TAG, message, error);
                    }
                },
                mediaSourceProvider,
                1500L
        );
        playbackProgressUpdateManager = new PlaybackProgressUpdateManager(
                new PlaybackProgressUpdateManager.CallbackScheduler() {
                    @Override
                    public void postDelayed(Runnable runnable, long delayMs) {
                        mainHandler.postDelayed(runnable, delayMs);
                    }

                    @Override
                    public void removeCallbacks(Runnable runnable) {
                        mainHandler.removeCallbacks(runnable);
                    }
                },
                new PlaybackProgressUpdateManager.StateProvider() {
                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public boolean isPreparing() {
                        return playbackRuntimeStateManager.preparing();
                    }
                },
                new PlaybackProgressUpdateManager.Actions() {
                    @Override
                    public void publishState() {
                        EchoPlaybackService.this.publishState();
                    }

                    @Override
                    public void persistPlaybackPosition() {
                        EchoPlaybackService.this.persistPlaybackPositionThrottled(false);
                    }
                }
        );
        playbackCrossfadeAdvanceManager = new PlaybackCrossfadeAdvanceManager(
                new PlaybackCrossfadeAdvanceManager.CallbackScheduler() {
                    @Override
                    public void post(Runnable runnable) {
                        mainHandler.post(runnable);
                    }

                    @Override
                    public void postDelayed(Runnable runnable, long delayMs) {
                        mainHandler.postDelayed(runnable, delayMs);
                    }

                    @Override
                    public void removeCallbacks(Runnable runnable) {
                        mainHandler.removeCallbacks(runnable);
                    }
                },
                new PlaybackCrossfadeAdvanceManager.StateProvider() {
                    @Override
                    public boolean fadeOutAdvancing() {
                        return playbackTransitionStateManager.fadeOutAdvancing();
                    }

                    @Override
                    public boolean playerAvailable() {
                        return player != null;
                    }

                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public boolean canCrossfadeAdvance() {
                        int repeatMode = playbackRuntimeStateManager != null
                                ? playbackRuntimeStateManager.repeatMode()
                                : REPEAT_ALL;
                        return playbackQueueManager != null
                                && playbackQueueManager.canCrossfadeAdvance(repeatMode);
                    }

                    @Override
                    public float baseVolume() {
                        return playbackRuntimeStateManager == null
                                ? 1.0f
                                : playbackRuntimeStateManager.currentTrackVolume();
                    }
                },
                new PlaybackCrossfadeAdvanceManager.Actions() {
                    @Override
                    public void setFadeOutAdvancing(boolean enabled) {
                        playbackTransitionStateManager.setFadeOutAdvancing(enabled);
                    }

                    @Override
                    public void setPlayerVolume(float volume) {
                        if (player != null) {
                            player.setVolume(volume);
                        }
                    }

                    @Override
                    public void skipToNextImmediately() {
                        EchoPlaybackService.this.skipToNextImmediately();
                    }

                    @Override
                    public void applyAppVolume() {
                        if (playbackRuntimeStateManager != null) {
                            playbackRuntimeStateManager.applyCurrentTrackVolumeToPlayer();
                        }
                    }
                }
        );
        playbackRecoveryScheduler = new PlaybackRecoveryScheduler(
                task -> playbackTaskScheduler.schedule(
                        PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                        task
                ),
                new PlaybackRecoveryScheduler.MainScheduler() {
                    @Override
                    public void post(Runnable task) {
                        mainHandler.post(task);
                    }

                    @Override
                    public void removeCallbacks(Runnable task) {
                        mainHandler.removeCallbacks(task);
                    }
                },
                EchoPlaybackService.this::prepareCurrent
        );
        createPlayerIfNeeded();
        playbackNotificationManager = new PlaybackNotificationManager(
                this,
                new PlaybackNotificationManager.ForegroundController() {
                    @Override
                    public PendingIntent activityPendingIntent() {
                        return EchoPlaybackService.this.activityPendingIntent();
                    }

                    @Override
                    public PendingIntent serviceActionPendingIntent(String action, int requestCode) {
                        return EchoPlaybackService.this.serviceActionPendingIntent(action, requestCode);
                    }

                    @Override
                    public void startPlaybackForeground(Notification notification) {
                        EchoPlaybackService.this.startPlaybackForeground(notification);
                    }
                },
                new PlaybackNotificationManager.StateProvider() {
                    @Override
                    public boolean isQueueEmpty() {
                        return EchoPlaybackService.this.queueStateSnapshot().isQueueEmpty();
                    }

                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public boolean isPreparing() {
                        return playbackRuntimeStateManager.preparing();
                    }

                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }

                    @Override
                    public boolean isFavorite(Track track) {
                        return toggleFavoriteUseCase != null && toggleFavoriteUseCase.isFavorite(track);
                    }

                    @Override
                    public android.media.session.MediaSession.Token playbackSessionPlatformToken() {
                        MediaLibrarySession session = playbackSessionManager == null ? null : playbackSessionManager.session();
                        return session == null ? null : session.getPlatformToken();
                    }
                },
                new PlaybackNotificationManager.LyricsTextProvider() {
                    @Override
                    public String currentNotificationLyric(Track track) {
                        return playbackLyricsManager == null ? "" : playbackLyricsManager.notificationLyricText(track);
                    }

                    @Override
                    public String sanitizeNotificationLyric(String value) {
                        return playbackLyricsManager == null ? "" : playbackLyricsManager.sanitizeNotificationLyric(value);
                    }
                },
                new PlaybackNotificationManager.ArtworkProvider() {
                    @Override
                    public Bitmap notificationArtworkFor(Track track) {
                        return playbackNotificationArtworkManager == null
                                ? null
                                : playbackNotificationArtworkManager.notificationArtworkFor(track);
                    }

                    @Override
                    public byte[] notificationArtworkDataFor(Track track) {
                        return playbackNotificationArtworkManager == null
                                ? null
                                : playbackNotificationArtworkManager.notificationArtworkDataFor(track);
                    }
                },
                new PlaybackNotificationManager.ActionCallbacks() {
                    @Override
                    public void publishPlaybackNotification(boolean force) {
                        EchoPlaybackService.this.publishPlaybackNotification(force);
                    }

                    @Override
                    public void play() {
                        EchoPlaybackService.this.play();
                    }

                    @Override
                    public void pause() {
                        EchoPlaybackService.this.pause();
                    }

                    @Override
                    public void skipToPrevious() {
                        EchoPlaybackService.this.skipToPrevious();
                    }

                    @Override
                    public void skipToNext() {
                        EchoPlaybackService.this.skipToNext();
                    }

                    @Override
                    public void toggleCurrentFavorite() {
                        EchoPlaybackService.this.toggleCurrentFavorite();
                    }

                    @Override
                    public void restoreLastPlayback(boolean playWhenReady) {
                        EchoPlaybackService.this.restoreLastPlayback(playWhenReady);
                    }

                    @Override
                    public void stopAndClear() {
                        EchoPlaybackService.this.stopAndClear();
                    }

                    @Override
                    public void stopForegroundAndSelf() {
                        EchoPlaybackService.this.stopForeground(true);
                        EchoPlaybackService.this.stopSelf();
                    }
                }
        );
        playbackLyricsManager = new PlaybackLyricsManager(
                this,
                new PlaybackLyricsManager.StateProvider() {
                    @Override
                    public boolean isAppVisible() {
                        return appVisible;
                    }

                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }

                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public boolean isPreparing() {
                        return playbackRuntimeStateManager.preparing();
                    }
                },
                playbackNotificationManager.lyricsNotificationBridge(EchoPlaybackService.this::refreshPlaybackSession)
        );
        playbackLyricsManager.setStatusBarLyricsEnabled(repository.loadStatusBarLyricsEnabled());
        playbackRuntimeStateManager.setShuffleEnabled(repository.loadShuffleEnabled());
        playbackRuntimeStateManager.setRepeatMode(repository.loadRepeatMode());
        playbackRuntimeStateManager.setReplayGainEnabled(repository.loadReplayGainEnabled());
        playbackRuntimeStateManager.setConcurrentPlaybackEnabled(repository.loadConcurrentPlaybackEnabled());
        playbackRuntimeStateManager.setPlaybackSpeed(repository.loadPlaybackSpeed());
        playbackRuntimeStateManager.setAppVolume(repository.loadAppVolume());
        playbackQueueManager = new PlaybackQueueManager(
                queueStore,
                queue,
                new PlaybackQueueManager.QueuePlaybackActions() {
                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public void prepareCurrent(boolean playWhenReady) {
                        EchoPlaybackService.this.prepareCurrent(playWhenReady);
                    }

                    @Override
                    public void publishState() {
                        EchoPlaybackService.this.publishState();
                    }

                    @Override
                    public void stopAndClear() {
                        EchoPlaybackService.this.stopAndClear();
                    }

                },
                playbackPositionManager,
                new PlaybackQueueManager.StreamingRestoreProvider() {
                    @Override
                    public Track restoredTrackFor(Track track) {
                        return mediaSourceProvider.restoredTrackForPreparation(track);
                    }

                    @Override
                    public void restoreForDataPath(String dataPath) {
                        mediaSourceProvider.restoreHeadersForDataPath(dataPath);
                    }
                },
                new PlaybackQueueManager.MirroredQueuePlayer() {
                    @Override
                    public boolean matchesCurrentQueue() {
                        return EchoPlaybackService.this.mirroredQueueMatchesCurrentPlayer();
                    }

                    @Override
                    public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
                        if (player == null) {
                            return false;
                        }
                        playbackRuntimeStateManager.setPreparing(false);
                        try {
                            Track track = EchoPlaybackService.this.currentTrack();
                            if (track != null) {
                                EchoPlaybackService.this.resetWaveformIfTrackChanged(track);
                            }
                            EchoPlaybackService.this.applyPlaybackModeAndParametersToPlayer();
                            player.seekTo(index, positionMs);
                            player.setPlayWhenReady(playWhenReady);
                            if (playWhenReady) {
                                player.play();
                            }
                            return true;
                        } catch (IllegalStateException error) {
                            playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
                            Log.w(TAG, "Unable to reuse mirrored queue", error);
                            return false;
                        }
                    }
                },
                playbackRuntimeStateManager,
                playbackTransitionStateManager
        );
        playbackMediaLibraryCallback = new PlaybackMediaLibraryCallback(
                new PlaybackMediaLibraryCallback.DataSource() {
                    @Override
                    public String appName() {
                        return getString(R.string.app_name);
                    }

                    @Override
                    public List<Track> loadCachedTracks() {
                        return repository.loadCachedTracks();
                    }

                    @Override
                    public List<Playlist> loadPlaylists() {
                        return repository.loadPlaylists();
                    }

                    @Override
                    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
                        return repository.loadRecentlyPlayed(limit);
                    }

                    @Override
                    public List<Track> loadPlaylistTracks(long playlistId) {
                        return repository.loadPlaylistTracks(playlistId);
                    }

                    @Override
                    public MediaItem mediaItemForTrack(Track track) {
                        return mediaSourceProvider.mediaItemForTrack(track, EchoPlaybackService.this::mediaMetadataForPlaybackTrack);
                    }
                }
        );
        playbackSessionManager = new PlaybackSessionManager(
                this,
                this::createSessionPlayer,
                playbackMediaLibraryCallback,
                this::activityPendingIntent
        );
        playbackVisualizationAnalyzer = new PlaybackVisualizationAnalyzer(
                this,
                visualizationTaskScheduler,
                new PlaybackVisualizationAnalyzer.StateProvider() {
                    @Override
                    public boolean isAppVisible() {
                        return appVisible;
                    }

                    @Override
                    public float bufferedProgress(long durationMs) {
                        return EchoPlaybackService.this.bufferedProgress(durationMs);
                    }

                    @Override
                    public void publishState() {
                        EchoPlaybackService.this.publishState();
                    }
                },
                mediaSourceProvider
        );
        playbackVisualizationCacheManager = new PlaybackVisualizationCacheManager(
                new PlaybackVisualizationCacheManager.StateProvider() {
                    @Override
                    public Handler mainHandler() {
                        return mainHandler;
                    }

                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }

                    @Override
                    public void scheduleVisualizationCacheTask(Runnable task) {
                        visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, task);
                    }
                },
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
        playbackShutdownCoordinator = new PlaybackShutdownCoordinator(
                new PlaybackShutdownCoordinator.PlaybackResources() {
                    @Override
                    public void releaseLyrics() {
                        if (playbackLyricsManager != null) {
                            playbackLyricsManager.release();
                        }
                    }

                    @Override
                    public void releaseWifiLock() {
                        if (playbackWifiLockManager != null) {
                            playbackWifiLockManager.release();
                        }
                    }

                    @Override
                    public void releasePlayer() {
                        EchoPlaybackService.this.releasePlayer();
                    }
                },
                new PlaybackShutdownCoordinator.ServiceResources() {
                    @Override
                    public void unregisterNoisyReceiver() {
                        if (playbackNoisyReceiverManager != null) {
                            playbackNoisyReceiverManager.unregister();
                        }
                    }

                    @Override
                    public void releaseWarmup() {
                        if (playbackWarmupCoordinator != null) {
                            playbackWarmupCoordinator.release();
                        }
                    }

                    @Override
                    public void releaseVisualizationAnalyzer() {
                        if (playbackVisualizationAnalyzer != null) {
                            playbackVisualizationAnalyzer.release();
                        }
                    }

                    @Override
                    public void releaseRecoveryScheduler() {
                        if (playbackRecoveryScheduler != null) {
                            playbackRecoveryScheduler.release();
                        }
                    }

                    @Override
                    public void shutdownTaskSchedulers() {
                        playbackTaskScheduler.shutdownNow();
                        visualizationTaskScheduler.shutdownNow();
                    }

                    @Override
                    public void releaseErrorRecovery() {
                        if (playbackErrorRecoveryManager != null) {
                            playbackErrorRecoveryManager.release();
                        }
                    }

                    @Override
                    public void releaseProgressUpdates() {
                        if (playbackProgressUpdateManager != null) {
                            playbackProgressUpdateManager.release();
                        }
                    }

                    @Override
                    public void releaseSleepTimer() {
                        if (playbackSleepTimerManager != null) {
                            playbackSleepTimerManager.release();
                        }
                    }

                    @Override
                    public void releaseCrossfade() {
                        if (playbackCrossfadeAdvanceManager != null) {
                            playbackCrossfadeAdvanceManager.release();
                        }
                    }

                    @Override
                    public void clearMainCallbacks() {
                        mainHandler.removeCallbacksAndMessages(null);
                    }

                    @Override
                    public void releaseVisualizationCache() {
                        if (playbackVisualizationCacheManager != null) {
                            playbackVisualizationCacheManager.release();
                        }
                    }

                    @Override
                    public void releaseNotificationArtwork() {
                        if (playbackNotificationArtworkManager != null) {
                            playbackNotificationArtworkManager.release();
                        }
                    }

                    @Override
                    public void releasePrecache() {
                        if (playbackPrecacheManager != null) {
                            playbackPrecacheManager.release();
                        }
                    }

                    @Override
                    public void releaseStatePublisher() {
                        if (playbackStatePublisher != null) {
                            playbackStatePublisher.release();
                        }
                    }
                },
                new PlaybackShutdownCoordinator.LifecycleResources() {
                    @Override
                    public void persistPlaybackPosition() {
                        EchoPlaybackService.this.persistPlaybackPositionThrottled(true);
                    }

                    @Override
                    public void persistPlaybackQueue() {
                        if (playbackQueueManager != null) {
                            playbackQueueManager.persistQueueState();
                        }
                    }

                    @Override
                    public void savePlaybackResumeRequested(boolean requested) {
                        EchoPlaybackService.this.savePlaybackResumeRequested(requested);
                    }

                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public boolean isPreparing() {
                        return playbackRuntimeStateManager.preparing();
                    }

                    @Override
                    public boolean hasNotificationWorthyState() {
                        return notificationWorthyState();
                    }

                    @Override
                    public void publishPlaybackNotification() {
                        EchoPlaybackService.this.publishPlaybackNotification(true);
                    }
                }
        );
        playbackNotificationArtworkManager = new PlaybackNotificationArtworkManager(
                this,
                new PlaybackNotificationArtworkManager.StateProvider() {
                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }
                },
                new PlaybackNotificationArtworkManager.NotificationBridge() {
                    @Override
                    public void refreshPlaybackSession() {
                        refreshPlaybackSession();
                    }

                    @Override
                    public void updateMediaNotification() {
                        publishPlaybackNotification(true);
                    }
                }
        );
        playbackStatePublisher = new PlaybackStatePublisher(
                this::snapshot,
                playbackLyricsManager,
                playbackNotificationManager == null ? null : force -> playbackNotificationManager.updateMediaNotification(force),
                playbackNotificationArtworkManager == null ? null : track -> playbackNotificationArtworkManager.notificationArtworkFor(track),
                (snapshot, artwork) -> EchoPlaybackWidgetProvider.update(this, snapshot, artwork)
        );
        playbackQueueManager.setPlaybackRestoreEnabled(repository.loadPlaybackRestoreEnabled());
        playbackPrecacheManager = new PlaybackPrecacheManager(new PlaybackPrecacheManager.StateProvider() {
            @Override
            public Track currentTrack() {
                return EchoPlaybackService.this.currentTrack();
            }

            @Override
            public MediaItem currentPlayerMediaItem() {
                try {
                    if (player == null
                            || player.getPlaybackState() == Player.STATE_IDLE
                            || player.getMediaItemCount() <= 0) {
                        return null;
                    }
                    return player.getCurrentMediaItem();
                } catch (IllegalStateException ignored) {
                    return null;
                }
            }

            @Override
            public PlaybackStreamingDiagnostics streamingDiagnostics() {
                return streamingDiagnostics;
            }
        }, playbackQueueManager, mediaSourceProvider, new PlaybackPrecacheManager.CallbackScheduler() {
            @Override
            public void postDelayed(Runnable runnable, long delayMs) {
                mainHandler.postDelayed(runnable, delayMs);
            }

            @Override
            public void removeCallbacks(Runnable runnable) {
                mainHandler.removeCallbacks(runnable);
            }
        });
        if (playbackQueueManager != null) {
            playbackQueueManager.restorePlaybackQueue();
        }
        if (notificationWorthyState()) {
            publishPlaybackNotification(true);
        }
        playbackLyricsManager.bind();
        playbackNoisyReceiverManager = new PlaybackNoisyReceiverManager(
                new PlaybackNoisyReceiverManager.Registrar() {
                    @Override
                    public void register(BroadcastReceiver receiver, IntentFilter filter) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            EchoPlaybackService.this.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
                        } else {
                            EchoPlaybackService.this.registerReceiver(receiver, filter);
                        }
                    }

                    @Override
                    public void unregister(BroadcastReceiver receiver) {
                        EchoPlaybackService.this.unregisterReceiver(receiver);
                    }
                },
                new PlaybackNoisyReceiverManager.Actions() {
                    @Override
                    public void pauseIfPlaying() {
                        if (EchoPlaybackService.this.isPlaying()) {
                            EchoPlaybackService.this.pause();
                        }
                    }
                }
        );
        playbackNoisyReceiverManager.register();
        android.net.wifi.WifiManager wifiManager =
                (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        android.net.wifi.WifiManager.WifiLock wifiLock = null;
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "echo:playback");
        }
        final android.net.wifi.WifiManager.WifiLock streamingWifiLock = wifiLock;
        playbackWifiLockManager = new PlaybackWifiLockManager(
                streamingWifiLock == null ? null : new PlaybackWifiLockManager.Lock() {
                    @Override
                    public boolean isHeld() {
                        return streamingWifiLock.isHeld();
                    }

                    @Override
                    public void acquire() {
                        streamingWifiLock.acquire();
                    }

                    @Override
                    public void release() {
                        streamingWifiLock.release();
                    }
                },
                new PlaybackWifiLockManager.StreamingTrackProvider() {
                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }
                },
                mediaSourceProvider
        );
        publishState();
    }

    @UnstableApi
    private Player createSessionPlayer() {
        return new PlaybackSessionPlayer(player, new PlaybackSessionPlayer.Delegate() {
            @Override public void play() { EchoPlaybackService.this.play(); }
            @Override public void pause() { EchoPlaybackService.this.pause(); }
            @Override public void seekTo(long positionMs) { EchoPlaybackService.this.seekTo(positionMs); }
            @Override public void skipToPrevious() { EchoPlaybackService.this.skipToPrevious(); }
            @Override public void skipToNext() { EchoPlaybackService.this.skipToNext(); }
            @Override public void setRepeatMode(int appRepeatMode) { EchoPlaybackService.this.setRepeatMode(appRepeatMode); }
            @Override public void stopAndClear() { EchoPlaybackService.this.stopAndClear(); }
            @Override
            public boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
                return EchoPlaybackService.this.setControllerMediaItems(mediaItems, startIndex, startPositionMs);
            }
            @Override public Track currentTrack() { return EchoPlaybackService.this.currentTrack(); }
            @Override
            public MediaMetadata mediaMetadataForTrack(Track track) {
                return playbackNotificationManager == null ? null : playbackNotificationManager.mediaMetadataForTrack(track);
            }
        });
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
            if (playbackQueueManager != null) {
                playbackQueueManager.persistQueueState();
            }
            savePlaybackResumeRequested(isPlaying() || playbackRuntimeStateManager.preparing());
            if (notificationWorthyState()) {
                publishPlaybackNotification(true);
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
        if (visible && notificationWorthyState()) {
            publishPlaybackNotification(true);
        }
    }

    public void playQueue(List<Track> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        playQueue(tracks, startIndex, C.TIME_UNSET);
    }

    private void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        if (playbackQueueManager != null) {
            playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);
        }
    }

    public void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        if (playbackQueueManager != null) {
            playbackQueueManager.appendToQueue(tracks);
        }
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
        if (playbackRuntimeStateManager.preparing()) {
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
        savePlaybackResumeRequested(true);
        if (playbackWifiLockManager != null) {
            playbackWifiLockManager.acquireIfStreaming();
        }
        publishState();
        startProgressUpdates();
    }

    private void playFirstQueuedTrack() {
        if (playbackQueueManager != null) {
            playbackQueueManager.playFirstQueuedTrack();
        }
    }

    public void pause() {
        if (playbackCrossfadeAdvanceManager != null) {
            playbackCrossfadeAdvanceManager.cancel();
        }
        if (player != null && isPlaying()) {
            player.pause();
        }
        savePlaybackResumeRequested(false);
        if (playbackWifiLockManager != null) {
            playbackWifiLockManager.release();
        }
        persistPlaybackPositionThrottled(true);
        publishState();
    }

    public void seekTo(long positionMs) {
        if (player == null || playbackRuntimeStateManager.preparing()) {
            return;
        }
        try {
            player.seekTo(Math.max(0L, positionMs));
            persistPlaybackPositionThrottled(true);
            publishState();
        } catch (IllegalStateException ignored) {
            playbackRuntimeStateManager.setErrorMessage("Playback is not ready.");
            publishState();
        }
    }

    public void skipToNext() {
        if (startFadeOutThenNext()) {
            return;
        }
        skipToNextImmediately();
    }

    private void skipToNextImmediately() {
        if (playbackQueueManager != null && playbackQueueManager.skipToNextImmediately()) {
            onMirroredQueueReused(true);
        }
    }

    private boolean startFadeOutThenNext() {
        return playbackCrossfadeAdvanceManager != null && playbackCrossfadeAdvanceManager.startFadeOutThenNext();
    }

    public void skipToPrevious() {
        if (positionMs() > 3000L) {
            seekTo(0L);
            return;
        }
        if (playbackQueueManager != null && playbackQueueManager.skipToPrevious()) {
            onMirroredQueueReused(true);
        }
    }

    public List<Track> queueSnapshot() {
        return playbackQueueManager == null ? Collections.emptyList() : playbackQueueManager.queueSnapshot();
    }

    public void moveQueueTrack(int fromIndex, int toIndex) {
        if (playbackQueueManager != null) {
            playbackQueueManager.moveQueueTrack(fromIndex, toIndex);
        }
    }

    public PlaybackStreamingDiagnostics.Snapshot streamingDiagnostics() {
        return streamingDiagnostics.snapshot();
    }

    public void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        if (playbackQueueManager != null) {
            PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                    playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
            if (recovery != null) {
                Track track = recovery.getTrack();
                streamingDiagnostics.recordRecovery(
                        track,
                        recovery.getRestoredPositionMs(),
                        mediaSourceProvider.streamingQualityForTrack(track)
                );
                if (playbackRecoveryScheduler != null) {
                    playbackRecoveryScheduler.scheduleCurrentPlaybackRecovery(recovery.getPlayWhenReady());
                }
            }
        }
    }

    public void removeTracksById(Set<Long> trackIds) {
        if (playbackQueueManager != null) {
            playbackQueueManager.removeTracksById(trackIds);
        }
    }

    @Override
    public void warmPlaybackTrack(Track track) {
        if (playbackWarmupCoordinator != null) {
            playbackWarmupCoordinator.warmup(track);
        }
    }

    public void retainTracksById(Set<Long> trackIdsToKeep) {
        if (playbackQueueManager != null) {
            playbackQueueManager.retainTracksById(trackIdsToKeep);
        }
    }

    public void clearQueue() {
        if (playbackQueueManager != null) {
            playbackQueueManager.clearQueue();
        }
    }

    public void toggleCurrentFavorite() {
        Track track = currentTrack();
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            publishState();
        }
    }

    public void restoreLastPlayback(boolean playWhenRestored) {
        PlaybackQueueManager.QueueStateSnapshot queueState = playbackQueueManager == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : playbackQueueManager.restorePlaybackQueue();
        if (queueState.isQueueEmpty()) {
            publishState();
            return;
        }
        createPlayerIfNeeded();
        if (!queueState.getHasCurrentTrack()) {
            publishState();
            return;
        }
        boolean shouldPlay = playWhenRestored || (repository != null && repository.loadPlaybackResumeRequested());
        prepareCurrent(shouldPlay);
    }

    public void replaceQueuedTrack(Track replacement) {
        if (playbackQueueManager != null) {
            playbackQueueManager.replaceQueuedTrack(replacement);
        }
    }

    public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        if (playbackQueueManager != null) {
            playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement);
        }
    }

    public PlaybackStateSnapshot snapshot() {
        PlaybackQueueManager.QueueStateSnapshot queueState = queueStateSnapshot();
        Track track = queueState.getCurrentTrack();
        long duration = track == null ? 0L : Math.max(track.durationMs, durationMs());
        boolean deferVisualGeneration = shouldDeferPlaybackVisualization();
        PlaybackWaveformSnapshot waveform = waveformSnapshotFor(track, duration, deferVisualGeneration);
        PlaybackSpectrumSnapshot spectrum = spectrumSnapshotFor(track, duration, deferVisualGeneration);
        return new PlaybackStateSnapshot(
                track,
                queueState.getCurrentIndex(),
                queueState.getQueueSize(),
                positionMs(),
                duration,
                isPlaying(),
                playbackRuntimeStateManager.preparing(),
                playbackRuntimeStateManager.errorMessage(),
                playbackRuntimeStateManager != null && playbackRuntimeStateManager.shuffleEnabled(),
                playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL,
                playbackRuntimeStateManager == null ? 1.0f : playbackRuntimeStateManager.playbackSpeed(),
                playbackRuntimeStateManager == null ? 1.0f : playbackRuntimeStateManager.appVolume(),
                sleepTimerRemainingMs(),
                waveform,
                spectrum,
                isPlaying() ? realtimeBassDetector.beat() : 0f
        );
    }

    public float realtimeBeat() {
        return isPlaying() ? realtimeBassDetector.beat() : 0f;
    }

    public float[] realtimeBands() {
        return isPlaying() ? realtimeBassDetector.bands() : EMPTY_REALTIME_BANDS;
    }

    public void setShuffleEnabled(boolean enabled) {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.setShuffleEnabled(enabled);
        }
        repository.saveShuffleEnabled(playbackRuntimeStateManager != null && playbackRuntimeStateManager.shuffleEnabled());
        applyPlaybackModeToPlayer();
        publishState();
    }

    public void setRepeatMode(int mode) {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.setRepeatMode(mode);
        }
        repository.saveRepeatMode(playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : (mode != REPEAT_ALL && mode != REPEAT_ONE && mode != REPEAT_OFF ? REPEAT_ALL : mode));
        applyPlaybackModeToPlayer();
        publishState();
    }

    public void cycleRepeatMode() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.cycleRepeatMode();
        }
        repository.saveRepeatMode(playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL);
        applyPlaybackModeToPlayer();
        publishState();
    }

    public void setPlaybackSpeed(float speed) {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.setPlaybackSpeed(speed);
        }
        applyPlaybackParametersToPlayer();
        publishState();
    }

    public float playbackSpeed() {
        return playbackRuntimeStateManager == null ? 1.0f : playbackRuntimeStateManager.playbackSpeed();
    }

    public void setAppVolume(float volume) {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.setAppVolume(volume);
        }
        applyPlaybackParametersToPlayer();
        publishState();
    }

    public float appVolume() {
        return playbackRuntimeStateManager == null ? 1.0f : playbackRuntimeStateManager.appVolume();
    }

    public void setConcurrentPlaybackEnabled(boolean enabled) {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.setConcurrentPlaybackEnabled(enabled);
        }
        applyAudioFocusHandling();
    }

    public boolean concurrentPlaybackEnabled() {
        return playbackRuntimeStateManager != null && playbackRuntimeStateManager.concurrentPlaybackEnabled();
    }

    public void setStatusBarLyricsEnabled(boolean enabled) {
        if (playbackLyricsManager != null) {
            playbackLyricsManager.setStatusBarLyricsEnabled(enabled);
        }
    }

    public void setPlaybackRestoreEnabled(boolean enabled) {
        if (playbackQueueManager != null) {
            playbackQueueManager.setPlaybackRestoreEnabled(enabled);
        }
        repository.savePlaybackRestoreEnabled(enabled);
    }

    public void setReplayGainEnabled(boolean enabled) {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.setReplayGainEnabled(enabled);
        }
        applyPlaybackParametersToPlayer();
        publishState();
    }

    public AudioEffectSettings audioEffectSettings() {
        return audioEffectSettings;
    }

    public void applyAudioEffectSettings(AudioEffectSettings settings) {
        audioEffectSettings = settings == null ? AudioEffectSettings.DEFAULT : settings;
        if (repository != null) {
            repository.saveAudioEffectSettings(audioEffectSettings);
        }
        audioEffectManager.bind(player, audioEffectSettings);
        publishState();
    }

    private PlaybackQueueStore queueStore() {
        if (queueStore == null && repository != null) {
            queueStore = new PlaybackQueueStoreImpl(repository);
        }
        return queueStore;
    }

    public void startSleepTimerMinutes(int minutes) {
        if (playbackSleepTimerManager != null) {
            playbackSleepTimerManager.startMinutes(minutes);
        }
    }

    public void cancelSleepTimer() {
        if (playbackSleepTimerManager != null) {
            playbackSleepTimerManager.cancel(true);
        }
    }

    public long sleepTimerRemainingMs() {
        return playbackSleepTimerManager == null ? 0L : playbackSleepTimerManager.remainingMs();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareCurrent(final boolean playWhenReady) {
        Track track = currentTrack();
        if (track == null) {
            return;
        }
        PlaybackMediaSourceProvider.PlaybackPreparation preparation =
                mediaSourceProvider.prepareTrackForPlayback(track);
        Track restoredTrack = preparation.getRestoredTrack();
        if (restoredTrack != null) {
            playbackQueueManager.replaceCurrentQueueTrack(restoredTrack);
        }
        track = preparation.getTrack();
        if (!preparation.getPlayable()) {
            String unplayableMessage = preparation.getUnplayableMessage();
            playbackRuntimeStateManager.setPreparing(false);
            playbackRuntimeStateManager.setErrorMessage(unplayableMessage);
            Log.w(TAG, "Refusing to prepare empty uri for " + debugTrack(track));
            publishState();
            return;
        }
        final long startPositionMs = restoredPositionFor(track);
        prepareMirroredQueue(playWhenReady, startPositionMs);
        return;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareMirroredQueue(final boolean playWhenReady, final long startPositionMs) {
        if (seekExistingMirroredQueue(playWhenReady, startPositionMs)) {
            return;
        }
        PlaybackQueueManager.QueuePreparation queuePreparation = playbackQueueManager == null
                ? PlaybackQueueManager.QueuePreparation.empty()
                : playbackQueueManager.queuePreparationForNewPlayer();
        Track track = queuePreparation.getCurrentTrack();
        if (track == null) {
            return;
        }
        List<Track> mirroredQueueTracks = queuePreparation.getMirroredQueueTracks();
        if (mirroredQueueTracks == null || mirroredQueueTracks.isEmpty()) {
            prepareSingleTrack(track, playWhenReady, startPositionMs);
            return;
        }
        List<MediaSource> mediaSources = mediaSourceProvider.mediaSourcesForTracks(
                mirroredQueueTracks,
                this::mediaMetadataForPlaybackTrack
        );
        playbackRuntimeStateManager.setPreparing(true);
        createPlayerIfNeeded();
        playbackTransitionStateManager.setLastMarkedTrack(null);
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        applyPlaybackParametersToPlayer();
        player.clearMediaItems();
        player.setMediaSources(mediaSources, queuePreparation.getStartIndex(), Math.max(0L, startPositionMs));
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(true);
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            consumeRestoredPositionAfterPrepare(startPositionMs);
            publishState();
            publishPlaybackNotification(true);
        } catch (IllegalStateException error) {
            playbackRuntimeStateManager.setPreparing(false);
            Log.w(TAG, "Unable to prepare mirrored queue for " + debugTrack(track), error);
            playbackRuntimeStateManager.setErrorMessage("Unable to open this track.");
            releasePlayer();
            publishState();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareSingleTrack(Track track, final boolean playWhenReady, final long startPositionMs) {
        playbackRuntimeStateManager.setPreparing(true);
        createPlayerIfNeeded();
        playbackTransitionStateManager.setLastMarkedTrack(null);
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        player.stop();
        player.clearMediaItems();
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
        applyPlaybackParametersToPlayer();
        player.setMediaSource(mediaSourceProvider.mediaSourceForTrack(track, this::mediaMetadataForPlaybackTrack));
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs);
            }
            consumeRestoredPositionAfterPrepare(startPositionMs);
            publishState();
            publishPlaybackNotification(true);
        } catch (IllegalStateException error) {
            playbackRuntimeStateManager.setPreparing(false);
            Log.w(TAG, "Unable to prepare player for " + debugTrack(track), error);
            playbackRuntimeStateManager.setErrorMessage("Unable to open this track.");
            releasePlayer();
            publishState();
        }
    }

    private String debugTrack(Track track) {
        if (track == null) {
            return "track=<null>";
        }
        return "trackId=" + track.id
                + ", title=" + track.title
                + ", dataPath=" + track.dataPath
                + ", uri=" + track.contentUri;
    }

    private void releasePlayer() {
        playerFactory.releasePlayer(
                player, playerListener, audioEffectManager,
                this::releasePlaybackSession,
                mediaSourceProvider::releaseAudioCache
        );
        player = null;
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
        playbackRuntimeStateManager.setPreparing(false);
    }

    private void stopAndClear() {
        if (playbackCrossfadeAdvanceManager != null) {
            playbackCrossfadeAdvanceManager.cancel();
        }
        if (playbackSleepTimerManager != null) {
            playbackSleepTimerManager.cancel(false);
        }
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAndClearPlaybackState();
        } else if (playbackPositionManager != null) {
            playbackPositionManager.clearPlaybackPosition();
        }
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.releasePlaybackResources();
        } else {
            releasePlayer();
        }
        if (playbackQueueManager == null) {
            playbackRuntimeStateManager.setPreparing(false);
            playbackRuntimeStateManager.setErrorMessage("");
            playbackTransitionStateManager.clear();
            savePlaybackResumeRequested(false);
        }
        stopProgressUpdates();
        stopForeground(true);
        publishState();
        stopSelf();
    }

    private void playAfterCompletion() {
        PlaybackQueueManager.PlaybackCompletionAction completionAction = playbackQueueManager == null
                ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                : playbackQueueManager.playbackCompletionAction();
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            stopAndClear();
            return;
        }
        if (playbackQueueManager != null) {
            playbackQueueManager.preparePlaybackCompletion(completionAction);
        }
        switch (completionAction) {
            case REPEAT_CURRENT:
                prepareCurrent(true);
                break;
            case STOP_AT_END:
                stopAtEndOfQueue();
                break;
            case ADVANCE_TO_NEXT:
                skipToNext();
                break;
            default:
                stopAndClear();
                break;
        }
    }

    private void stopAtEndOfQueue() {
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAtEndOfQueue();
        } else {
            clearRestoredPosition();
            playbackRuntimeStateManager.setPreparing(false);
            playbackRuntimeStateManager.setErrorMessage("");
            playbackTransitionStateManager.setLastMarkedTrack(null);
            savePlaybackResumeRequested(false);
        }
        stopProgressUpdates();
        if (player == null) {
            createPlayerIfNeeded();
        } else {
            try {
                player.setPlayWhenReady(false);
                player.seekTo(0L);
            } catch (IllegalStateException ignored) {
                releasePlayer();
                createPlayerIfNeeded();
            }
        }
        publishState();
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

    private void publishState() {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.publishState();
            return;
        }
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyPlaybackModeToPlayer();
        }
    }

    private void publishPlaybackNotification(boolean force) {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.publishNotification(force);
            return;
        }
        if (playbackNotificationManager != null) {
            playbackNotificationManager.updateMediaNotification(force);
        }
    }

    private boolean notificationWorthyState() {
        if (playbackNotificationManager != null) {
            return playbackNotificationManager.hasNotificationWorthyState();
        }
        return PlaybackNotificationManager.isNotificationWorthy(
                currentTrack(),
                queueStateSnapshot().isQueueEmpty(),
                playbackRuntimeStateManager != null && playbackRuntimeStateManager.preparing(),
                isPlaying()
        );
    }

    private void applyPlaybackModeToPlayer() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyPlaybackModeToPlayer();
        }
    }

    private void applyPlaybackParametersToPlayer() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyPlaybackParametersToPlayer();
        }
    }

    private void applyPlaybackModeAndParametersToPlayer() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyPlaybackModeAndParametersToPlayer();
        }
    }

    private void applyAudioFocusHandling() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyAudioFocusHandling();
        }
    }

    private void refreshPlaybackSession() {
        if (playbackSessionManager != null) {
            playbackSessionManager.refreshPlayer();
        }
    }

    private void releasePlaybackSession() {
        if (playbackSessionManager != null) {
            playbackSessionManager.release();
        }
    }

    private void publishBufferingState() {
        if (playbackStatePublisher != null) {
            playbackStatePublisher.publishBufferingState(new PlaybackStatePublisher.BufferingRecorder() {
                @Override
                public void record(PlaybackStateSnapshot snapshot) {
                    streamingDiagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
                }
            });
            return;
        }
        PlaybackStateSnapshot snapshot = snapshot();
        streamingDiagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
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
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
        }
        stopAtEndOfQueue();
    }

    private void createPlayerIfNeeded() {
        if (player != null) {
            return;
        }
        player = playerFactory.createPlayer();
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyAudioFocusHandling();
        }
        player.addListener(playerListener);
        applyPlaybackModeAndParametersToPlayer();
        audioEffectManager.bind(player, audioEffectSettings);
        if (playbackSessionManager != null) {
            playbackSessionManager.bind();
        }
    }

    private void savePlaybackResumeRequested(boolean requested) {
        queueStore().saveResumeRequested(requested);
    }

    private void persistPlaybackPositionThrottled(boolean force) {
        if (playbackQueueManager != null) {
            playbackQueueManager.persistCurrentPlaybackPosition(force);
        } else if (playbackPositionManager != null) {
            playbackPositionManager.persistCurrentPosition(force);
        }
    }

    private long restoredPositionFor(Track track) {
        if (playbackQueueManager != null) {
            return playbackQueueManager.restoredPositionFor(track);
        }
        return playbackPositionManager == null ? 0L : playbackPositionManager.restoredPositionFor(track);
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : playbackQueueManager.queueStateSnapshot();
    }

    private void clearRestoredPosition() {
        if (playbackPositionManager != null) {
            playbackPositionManager.clearRestoredPosition();
        }
    }

    private void consumeRestoredPositionAfterPrepare(long startPositionMs) {
        if (playbackQueueManager != null) {
            playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);
        } else if (startPositionMs > 0L) {
            clearRestoredPosition();
        }
    }

    private boolean seekExistingMirroredQueue(boolean playWhenReady, long startPositionMs) {
        boolean reused = playbackQueueManager != null
                && playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
        if (reused) {
            onMirroredQueueReused(playWhenReady);
        }
        return reused;
    }

    private void onMirroredQueueReused(boolean playWhenReady) {
        if (playWhenReady && playbackWifiLockManager != null) {
            playbackWifiLockManager.acquireIfStreaming();
        }
        startProgressUpdates();
    }

    private boolean mirroredQueueMatchesCurrentPlayer() {
        if (!playbackQueueRuntimeStateManager.playerMirrorsQueue()
                || player == null
                || playbackQueueManager == null) {
            return false;
        }
        return playbackQueueManager.matchesMirroredQueue(
                player.getMediaItemCount(),
                new PlaybackQueueManager.QueueTrackMatcher() {
                    @Override
                    public boolean matches(int index, Track track) {
                        return mediaSourceProvider.mediaItemMatchesTrackForReuse(player.getMediaItemAt(index), track);
                    }
                }
        );
    }

    private boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        PlaybackMediaLibraryCallback.ControllerQueue controllerQueue = playbackMediaLibraryCallback == null
                ? null
                : playbackMediaLibraryCallback.controllerQueueForMediaItems(mediaItems, startIndex, startPositionMs);
        if (controllerQueue == null) {
            return false;
        }
        playQueue(
                controllerQueue.getTracks(),
                controllerQueue.getStartIndex(),
                controllerQueue.getStartPositionMs()
        );
        return true;
    }

    private MediaMetadata mediaMetadataForPlaybackTrack(Track track) {
        return playbackNotificationManager == null
                ? new MediaMetadata.Builder().build()
                : playbackNotificationManager.mediaMetadataForTrack(track);
    }

    private PlaybackWaveformSnapshot waveformSnapshotFor(Track track, long durationMs, boolean deferGeneration) {
        return playbackVisualizationAnalyzer == null
                ? PlaybackWaveformSnapshot.empty()
                : playbackVisualizationAnalyzer.waveformSnapshot(track, durationMs, deferGeneration);
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

    private boolean shouldDeferPlaybackVisualization() {
        return playbackVisualizationAnalyzer != null
                && playbackVisualizationAnalyzer.shouldDeferPlaybackVisualization();
    }

    private PlaybackSpectrumSnapshot spectrumSnapshotFor(Track track, long durationMs, boolean deferGeneration) {
        return playbackVisualizationAnalyzer == null
                ? PlaybackSpectrumSnapshot.empty()
                : playbackVisualizationAnalyzer.spectrumSnapshot(track, durationMs, deferGeneration);
    }

    private float bufferedProgress(long durationMs) {
        if (durationMs <= 0L || player == null) {
            return 0.0f;
        }
        try {
            long buffered = Math.max(positionMs(), player.getBufferedPosition());
            return Math.max(0.0f, Math.min(1.0f, buffered / (float) durationMs));
        } catch (IllegalStateException ignored) {
            return 0.0f;
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
        return playbackQueueManager == null ? null : playbackQueueManager.currentTrack();
    }

    private boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private long positionMs() {
        try {
            return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    private long durationMs() {
        try {
            if (player == null) {
                return 0L;
            }
            long duration = player.getDuration();
            return duration == C.TIME_UNSET ? 0L : Math.max(0L, duration);
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }
}

