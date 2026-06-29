package app.yukine.playback;

import android.app.PendingIntent;
import android.app.Notification;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import app.yukine.R;
import app.yukine.common.StreamingDataPathMetadata;
import app.yukine.common.EmbeddedArtwork;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackAudioEffectManager;
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
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
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
public final class EchoPlaybackService extends MediaLibraryService {
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
    private static final long CROSSFADE_FADE_OUT_MS = 700L;
    private static final long CROSSFADE_FADE_STEP_MS = 70L;
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
    private final Random random = new Random();
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
    private PlaybackShutdownCoordinator playbackShutdownCoordinator;
    private PlaybackWifiLockManager playbackWifiLockManager;
    private PlaybackNoisyReceiverManager playbackNoisyReceiverManager;
    private PlaybackProgressUpdateManager playbackProgressUpdateManager;
    @Inject
    MusicLibraryRepository repository;
    @Inject
    StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
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
            if (!playbackQueueRuntimeStateManager.playerMirrorsQueue() || player == null || queue.isEmpty()) {
                return;
            }
            int nextIndex = player.getCurrentMediaItemIndex();
            if (nextIndex < 0 || nextIndex >= queue.size() || nextIndex == currentIndex()) {
                return;
            }
            int repeat = playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL;
            if (repeat == REPEAT_OFF && isAutomaticMediaItemAdvance(reason)) {
                stopAfterAutomaticAdvance(currentIndex());
                return;
            }
            persistPlaybackPositionThrottled(true);
            setCurrentIndex(nextIndex);
            Track track = currentTrack();
            playbackRuntimeStateManager.setErrorMessage("");
            playbackTransitionStateManager.setLastMarkedTrack(null);
            clearRestoredPosition();
            resetCurrentPlaybackPosition();
            if (track != null) {
                resetWaveformIfTrackChanged(track);
                streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
            }
            if (playbackRuntimeStateManager != null) {
                playbackRuntimeStateManager.applyAppVolume();
            }
            persistPlaybackQueue();
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
                },
                new PlaybackErrorRecoveryManager.Actions() {
                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }

                    @Override
                    public boolean isHttpUri(Uri uri) {
                        return EchoPlaybackService.this.isHttpUri(uri);
                    }

                    @Override
                    public int queueSize() {
                        return queue.size();
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
        createPlayerIfNeeded();
        playbackLyricsManager = new PlaybackLyricsManager(this, new PlaybackLyricsManager.StateProvider() {
            @Override
            public boolean hasNotificationWorthyState() {
                return EchoPlaybackService.this.hasNotificationWorthyState();
            }

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

            @Override
            public void notifyMediaNotification(boolean force) {
                publishPlaybackNotification(force);
            }

            @Override
            public void refreshPlaybackSession() {
                EchoPlaybackService.this.refreshPlaybackSession();
            }

        });
        playbackLyricsManager.setStatusBarLyricsEnabled(repository.loadStatusBarLyricsEnabled());
        playbackNotificationManager = new PlaybackNotificationManager(
                this,
                new PlaybackNotificationManager.ForegroundPresenter() {
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

                    @Override
                    public boolean hasNotificationWorthyState() {
                        return EchoPlaybackService.this.hasNotificationWorthyState();
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
                }
        );
        playbackRuntimeStateManager.setShuffleEnabled(repository.loadShuffleEnabled());
        playbackRuntimeStateManager.setRepeatMode(repository.loadRepeatMode());
        playbackRuntimeStateManager.setReplayGainEnabled(repository.loadReplayGainEnabled());
        playbackRuntimeStateManager.setConcurrentPlaybackEnabled(repository.loadConcurrentPlaybackEnabled());
        playbackRuntimeStateManager.setPlaybackSpeed(repository.loadPlaybackSpeed());
        playbackRuntimeStateManager.setAppVolume(repository.loadAppVolume());
        playbackQueueManager = new PlaybackQueueManager(
                queueStore,
                new PlaybackQueueManager.QueueProvider() {
                    @Override
                    public java.util.List<Track> queue() {
                        return queue;
                    }
                },
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
                        return streamingPlaybackHeaderStore.restoredTrackFor(track);
                    }

                    @Override
                    public void restoreForDataPath(String dataPath) {
                        streamingPlaybackHeaderStore.restoreForDataPath(dataPath);
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
                            EchoPlaybackService.this.applyPlaybackParametersToPlayer();
                            EchoPlaybackService.this.applyPlaybackModeToPlayer();
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
                    public MediaMetadata mediaMetadataForTrack(Track track) {
                        return playbackNotificationManager.mediaMetadataForTrack(track);
                    }

                    @Override
                    public MediaItem mediaItemForTrack(Track track) {
                        return EchoPlaybackService.this.mediaItemForTrack(track);
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
                    public boolean isHttpUri(Uri uri) {
                        return EchoPlaybackService.this.isHttpUri(uri);
                    }

                    @Override
                    public CacheDataSource cacheDataSourceForTrack(Track track) {
                        return mediaSourceProvider.cacheDataSourceForTrack(track);
                    }

                    @Override
                    public String mediaCacheKeyForTrack(Track track) {
                        return mediaSourceProvider.mediaCacheKeyForTrack(track);
                    }

                    @Override
                    public long continuousCachedBytes(String cacheKey) {
                        return mediaSourceProvider.continuousCachedBytes(cacheKey);
                    }

                    @Override
                    public float bufferedProgress(long durationMs) {
                        return EchoPlaybackService.this.bufferedProgress(durationMs);
                    }

                    @Override
                    public long contentLengthForCacheKey(String cacheKey) {
                        return mediaSourceProvider.contentLengthForCacheKey(cacheKey);
                    }

                    @Override
                    public void publishState() {
                        EchoPlaybackService.this.publishState();
                    }
                }
        );
        playbackVisualizationCacheManager = new PlaybackVisualizationCacheManager(
                new PlaybackVisualizationCacheManager.StateProvider() {
                    @Override
                    public boolean isHttpUri(Uri uri) {
                        return EchoPlaybackService.this.isHttpUri(uri);
                    }

                    @Override
                    public String cacheKeyForTrack(Track track) {
                        return EchoPlaybackService.this.cacheKeyForTrack(track);
                    }

                    @Override
                    public long continuousCachedBytes(String cacheKey) {
                        return mediaSourceProvider.continuousCachedBytes(cacheKey);
                    }

                    @Override
                    public PlaybackTaskScheduler visualizationTaskScheduler() {
                        return visualizationTaskScheduler;
                    }

                    @Override
                    public PlaybackVisualizationCacheManager.PlaybackCacheDependencies cacheDependencies() {
                        return new PlaybackVisualizationCacheManager.PlaybackCacheDependencies() {
                            @Override
                            public CacheDataSource cacheDataSourceForTrack(Track track) {
                                return mediaSourceProvider.cacheDataSourceForTrack(track);
                            }
                        };
                    }

                    @Override
                    public Handler mainHandler() {
                        return mainHandler;
                    }

                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }
                }
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
                new Runnable() {
                    @Override
                    public void run() {
                        if (playbackLyricsManager != null) {
                            playbackLyricsManager.release();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (playbackNotificationArtworkManager != null) {
                            playbackNotificationArtworkManager.release();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (playbackPrecacheManager != null) {
                            playbackPrecacheManager.release();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (playbackNoisyReceiverManager != null) {
                            playbackNoisyReceiverManager.unregister();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.removeCallbacksAndMessages(null);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        playbackTaskScheduler.shutdownNow();
                        visualizationTaskScheduler.shutdownNow();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        if (playbackWifiLockManager != null) {
                            playbackWifiLockManager.release();
                        }
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        releasePlayer();
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
            public List<Track> queueSnapshot() {
                return new ArrayList<>(queue);
            }

            @Override
            public int currentIndex() {
                return currentIndex();
            }

            @Override
            public int repeatMode() {
                return playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL;
            }

            @Override
            public Track currentTrack() {
                return EchoPlaybackService.this.currentTrack();
            }

            @Override
            public boolean isHttpUri(Uri uri) {
                return EchoPlaybackService.this.isHttpUri(uri);
            }

            @Override
            public String cacheKeyForTrack(Track track) {
                return EchoPlaybackService.this.cacheKeyForTrack(track);
            }

            @Override
            public boolean currentPlayerLoadsCacheKey(Track track, String cacheKey) {
                if (player == null || track == null || cacheKey == null || cacheKey.isEmpty()) {
                    return false;
                }
                try {
                    if (player.getPlaybackState() == Player.STATE_IDLE || player.getMediaItemCount() <= 0) {
                        return false;
                    }
                    MediaItem mediaItem = player.getCurrentMediaItem();
                    return PlaybackMediaSourceProvider.mediaItemMatchesTrackForReuse(
                            mediaItem,
                            track.id,
                            track.contentUri,
                            cacheKey);
                } catch (IllegalStateException ignored) {
                    return false;
                }
            }

            @Override
            public PlaybackStreamingDiagnostics streamingDiagnostics() {
                return streamingDiagnostics;
            }
        }, mediaSourceProvider, new PlaybackPrecacheManager.CallbackScheduler() {
            @Override
            public void postDelayed(Runnable runnable, long delayMs) {
                mainHandler.postDelayed(runnable, delayMs);
            }

            @Override
            public void removeCallbacks(Runnable runnable) {
                mainHandler.removeCallbacks(runnable);
            }
        });
        restorePlaybackQueue();
        if (hasNotificationWorthyState()) {
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

                    @Override
                    public boolean isHttpUri(Uri uri) {
                        return EchoPlaybackService.this.isHttpUri(uri);
                    }
                }
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
        boolean notificationRequested = isPlaybackServiceAction(action);
        if (notificationRequested) {
            publishPlaybackNotification(true);
        }
        if (ACTION_PLAY.equals(action)) {
            play();
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_PREVIOUS.equals(action)) {
            skipToPrevious();
        } else if (ACTION_NEXT.equals(action)) {
            skipToNext();
        } else if (ACTION_TOGGLE_FAVORITE.equals(action)) {
            toggleCurrentFavorite();
        } else if (ACTION_RESTORE.equals(action)) {
            restoreLastPlayback(false);
        } else if (ACTION_RESTORE_AND_PLAY.equals(action)) {
            restoreLastPlayback(true);
        } else if (ACTION_STOP.equals(action)) {
            stopAndClear();
        }
        if (notificationRequested && hasNotificationWorthyState()) {
            publishPlaybackNotification(true);
        } else if (notificationRequested && !ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    private boolean isPlaybackServiceAction(String action) {
        return PlaybackServiceActions.isPlaybackServiceAction(action);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        persistPlaybackPositionThrottled(true);
        persistPlaybackQueue();
        savePlaybackResumeRequested(isPlaying() || playbackRuntimeStateManager.preparing());
        if (hasNotificationWorthyState()) {
            publishPlaybackNotification(true);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        persistPlaybackPositionThrottled(true);
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.releaseServiceResources();
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
        if (visible && hasNotificationWorthyState()) {
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
        playbackTransitionStateManager.setFadeOutAdvancing(false);
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
        if (queue.isEmpty()) {
            return;
        }
        if (startFadeOutThenNext()) {
            return;
        }
        skipToNextImmediately();
    }

    private void advanceQueueIndexToNext() {
        if (playbackQueueManager != null) {
            playbackQueueManager.advanceQueueIndexToNext();
        }
    }

    private void skipToNextImmediately() {
        if (playbackQueueManager != null && playbackQueueManager.skipToNextImmediately()) {
            onMirroredQueueReused(true);
        }
    }

    private boolean startFadeOutThenNext() {
        if (playbackTransitionStateManager.fadeOutAdvancing() || player == null || !isPlaying() || queue.size() < 2) {
            return false;
        }
        int repeat = playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL;
        if (repeat == REPEAT_OFF && currentIndex() >= queue.size() - 1) {
            return false;
        }
        playbackTransitionStateManager.setFadeOutAdvancing(true);
        final float baseVolume = playbackRuntimeStateManager == null
                ? 1.0f
                : playbackRuntimeStateManager.normalizeAppVolume(
                        playbackRuntimeStateManager.appVolume()
                                * playbackRuntimeStateManager.replayGainMultiplierForTrack(currentTrack()));
        final long startedAtMs = System.currentTimeMillis();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (player == null) {
                    playbackTransitionStateManager.setFadeOutAdvancing(false);
                    return;
                }
                if (!isPlaying()) {
                    playbackTransitionStateManager.setFadeOutAdvancing(false);
                    if (playbackRuntimeStateManager != null) {
                        playbackRuntimeStateManager.applyAppVolume();
                    }
                    return;
                }
                long elapsedMs = System.currentTimeMillis() - startedAtMs;
                if (elapsedMs >= CROSSFADE_FADE_OUT_MS) {
                    skipToNextImmediately();
                    playbackTransitionStateManager.setFadeOutAdvancing(false);
                    if (playbackRuntimeStateManager != null) {
                        playbackRuntimeStateManager.applyAppVolume();
                    }
                    return;
                }
                float fraction = 1.0f - Math.max(0f, Math.min(1.0f, elapsedMs / (float) CROSSFADE_FADE_OUT_MS));
                try {
                    player.setVolume(playbackRuntimeStateManager == null
                            ? baseVolume * fraction
                            : playbackRuntimeStateManager.normalizeAppVolume(baseVolume * fraction));
                } catch (IllegalStateException ignored) {
                    playbackTransitionStateManager.setFadeOutAdvancing(false);
                    return;
                }
                mainHandler.postDelayed(this, CROSSFADE_FADE_STEP_MS);
            }
        });
        return true;
    }

    public void skipToPrevious() {
        if (queue.isEmpty()) {
            return;
        }
        if (positionMs() > 3000L) {
            seekTo(0L);
            return;
        }
        if (playbackQueueManager != null && playbackQueueManager.skipToPrevious()) {
            onMirroredQueueReused(true);
        }
    }

    public List<Track> queueSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
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
                        StreamingDataPathMetadata.quality(track.dataPath)
                );
                playbackTaskScheduler.schedule(
                        PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                        () -> mainHandler.post(() -> prepareCurrent(recovery.getPlayWhenReady()))
                );
            }
        }
    }

    public void removeTracksById(Set<Long> trackIds) {
        if (playbackQueueManager != null) {
            playbackQueueManager.removeTracksById(trackIds);
        }
    }

    public void precacheTrack(Track track) {
        if (playbackPrecacheManager != null) {
            playbackPrecacheManager.precacheTrack(track);
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

    private void clearQueueState() {
        queue.clear();
        setCurrentIndex(-1);
    }

    public void toggleCurrentFavorite() {
        Track track = currentTrack();
        if (track == null || repository == null) {
            return;
        }
        repository.setFavorite(track, !repository.isFavorite(track.id));
        publishState();
    }

    public void restoreLastPlayback(boolean playWhenRestored) {
        if (playbackQueueManager != null) {
            playbackQueueManager.restorePlaybackQueue();
        } else {
            restorePlaybackQueue();
        }
        if (queue.isEmpty()) {
            publishState();
            return;
        }
        createPlayerIfNeeded();
        if (currentTrack() == null) {
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
        Track track = currentTrack();
        long duration = track == null ? 0L : Math.max(track.durationMs, durationMs());
        boolean deferVisualGeneration = shouldDeferPlaybackVisualization();
        PlaybackWaveformSnapshot waveform = waveformSnapshotFor(track, duration, deferVisualGeneration);
        PlaybackSpectrumSnapshot spectrum = spectrumSnapshotFor(track, duration, deferVisualGeneration);
        return new PlaybackStateSnapshot(
                track,
                currentIndex(),
                queue.size(),
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
        Track restoredTrack = streamingPlaybackHeaderStore.restoredTrackFor(track);
        if (restoredTrack != null) {
            replaceCurrentQueueTrack(restoredTrack);
            track = restoredTrack;
        }
        if (Uri.EMPTY.equals(track.contentUri)) {
            playbackRuntimeStateManager.setPreparing(false);
            playbackRuntimeStateManager.setErrorMessage(isStreamingPlaceholder(track)
                    ? "Streaming track is not resolved yet. Tap the track again to play."
                    : "Unable to open this track.");
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
        if (queue.isEmpty()) {
            return;
        }
        Track track = currentTrack();
        if (track == null) {
            return;
        }
        if (seekExistingMirroredQueue(playWhenReady, startPositionMs)) {
            return;
        }
        List<Track> mirroredQueueTracks = playbackQueueManager == null
                ? Collections.emptyList()
                : playbackQueueManager.mirroredQueueTracksForPreparation();
        if (mirroredQueueTracks == null || mirroredQueueTracks.isEmpty()) {
            prepareSingleTrack(track, playWhenReady, startPositionMs);
            return;
        }
        ArrayList<MediaSource> mediaSources = new ArrayList<>();
        for (Track queueTrack : mirroredQueueTracks) {
            mediaSources.add(mediaSourceFactory(queueTrack).createMediaSource(mediaItemForTrack(queueTrack)));
        }
        playbackRuntimeStateManager.setPreparing(true);
        createPlayerIfNeeded();
        playbackTransitionStateManager.setLastMarkedTrack(null);
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        applyPlaybackParametersToPlayer();
        player.clearMediaItems();
        player.setMediaSources(mediaSources, safeCurrentIndex(), Math.max(0L, startPositionMs));
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(true);
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            if (startPositionMs > 0L) {
                clearRestoredPosition();
            }
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
        streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        player.stop();
        player.clearMediaItems();
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
        applyPlaybackParametersToPlayer();
        player.setMediaSource(mediaSourceFactory(track).createMediaSource(mediaItemForTrack(track)));
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (playbackWarmupCoordinator != null) {
                playbackWarmupCoordinator.warmup(track);
            }
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs);
                clearRestoredPosition();
            }
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

    private boolean isStreamingPlaceholder(Track track) {
        return track != null
                && track.dataPath != null
                && track.dataPath.startsWith("streaming:");
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
                this::releaseAudioCache
        );
        player = null;
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
        playbackRuntimeStateManager.setPreparing(false);
    }

    private void releaseAudioCache() {
        mediaSourceProvider.releaseAudioCache();
    }

    private void stopAndClear() {
        if (playbackSleepTimerManager != null) {
            playbackSleepTimerManager.cancel(false);
        }
        if (playbackPositionManager != null) {
            playbackPositionManager.clearPlaybackPosition();
        }
        if (playbackShutdownCoordinator != null) {
            playbackShutdownCoordinator.releasePlaybackResources();
        } else {
            releasePlayer();
        }
        playbackRuntimeStateManager.setPreparing(false);
        playbackRuntimeStateManager.setErrorMessage("");
        playbackTransitionStateManager.clear();
        clearQueueState();
        persistPlaybackQueue();
        savePlaybackResumeRequested(false);
        stopProgressUpdates();
        stopForeground(true);
        publishState();
        stopSelf();
    }

    private void playAfterCompletion() {
        if (queue.isEmpty()) {
            stopAndClear();
            return;
        }
        Track completed = currentTrack();
        if (completed != null) {
            saveTrackPlaybackPosition(completed, 0L);
        }
        int repeat = playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL;
        if (repeat == REPEAT_ONE) {
            clearRestoredPosition();
            prepareCurrent(true);
            return;
        }
        if (repeat == REPEAT_OFF && currentIndex() >= queue.size() - 1) {
            stopAtEndOfQueue();
            savePlaybackResumeRequested(false);
            return;
        }
        skipToNext();
    }

    private void stopAtEndOfQueue() {
        clearRestoredPosition();
        playbackRuntimeStateManager.setPreparing(false);
        playbackRuntimeStateManager.setErrorMessage("");
        playbackTransitionStateManager.setLastMarkedTrack(null);
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
        savePlaybackResumeRequested(false);
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

    private void applyPlaybackModeToPlayer() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyPlaybackModeToPlayer();
        }
    }

    private void applyPlaybackParametersToPlayer() {
        if (playbackRuntimeStateManager != null) {
            playbackRuntimeStateManager.applyPlaybackSpeed();
            playbackRuntimeStateManager.applyAppVolume();
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
        persistPlaybackPositionThrottled(true);
        setClampedCurrentIndex(completedIndex);
        Track completed = currentTrack();
        if (completed != null) {
            saveTrackPlaybackPosition(completed, 0L);
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
        applyPlaybackParametersToPlayer();
        applyPlaybackModeToPlayer();
        audioEffectManager.bind(player, audioEffectSettings);
        if (playbackSessionManager != null) {
            playbackSessionManager.bind();
        }
    }

    private void restorePlaybackQueue() {
        if (playbackQueueManager == null) {
            return;
        }
        playbackQueueManager.restorePlaybackQueue();
    }

    private int indexOfTrackOccurrence(Track target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < queue.size(); i++) {
            Track candidate = queue.get(i);
            if (candidate == target
                    || (candidate != null
                    && candidate.id == target.id
                    && safeEquals(candidate.dataPath, target.dataPath)
                    && safeEquals(candidate.contentUri, target.contentUri))) {
                return i;
            }
        }
        return Math.max(0, Math.min(currentIndex(), queue.size() - 1));
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private int safeCurrentIndex() {
        if (queue.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(currentIndex(), queue.size() - 1));
    }

    private void savePlaybackResumeRequested(boolean requested) {
        queueStore().saveResumeRequested(requested);
    }

    private void replaceCurrentQueueTrack(Track track) {
        if (track == null || currentIndex() < 0 || currentIndex() >= queue.size()) {
            return;
        }
        queue.set(currentIndex(), track);
        persistPlaybackQueue();
    }

    private void persistPlaybackQueue() {
        queueStore().save(new ArrayList<>(queue), currentIndex());
    }

    private void persistPlaybackPositionThrottled(boolean force) {
        if (playbackPositionManager != null) {
            playbackPositionManager.persistCurrentPosition(force);
        }
    }

    private void resetCurrentPlaybackPosition() {
        if (playbackPositionManager != null) {
            playbackPositionManager.resetCurrentPlaybackPosition();
        }
    }

    private long restoredPositionFor(Track track) {
        return playbackPositionManager == null ? 0L : playbackPositionManager.restoredPositionFor(track);
    }

    private void clearRestoredPosition() {
        if (playbackPositionManager != null) {
            playbackPositionManager.clearRestoredPosition();
        }
    }

    private int currentIndex() {
        return playbackQueueManager == null ? -1 : playbackQueueManager.currentIndex();
    }

    private void setCurrentIndex(int index) {
        if (playbackQueueManager != null) {
            playbackQueueManager.setCurrentIndex(index);
        }
    }

    private int clampedCurrentIndex() {
        return playbackQueueManager == null ? 0 : playbackQueueManager.clampCurrentIndex(queue.size());
    }

    private void setClampedCurrentIndex(int index) {
        if (playbackQueueManager != null) {
            playbackQueueManager.setClampedCurrentIndex(index, queue.size());
        }
    }

    private void saveTrackPlaybackPosition(Track track, long positionMs) {
        if (playbackPositionManager != null) {
            playbackPositionManager.saveTrackPosition(track, positionMs);
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
                || player.getMediaItemCount() != queue.size()) {
            return false;
        }
        for (int i = 0; i < queue.size(); i++) {
            Track track = queue.get(i);
            if (track == null || !PlaybackMediaSourceProvider.mediaItemMatchesTrackForReuse(
                    player.getMediaItemAt(i),
                    track.id,
                    track.contentUri,
                    cacheKeyForTrack(track))) {
                return false;
            }
        }
        return true;
    }

    private boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        List<Track> tracks = playbackMediaLibraryCallback == null
                ? Collections.emptyList()
                : playbackMediaLibraryCallback.resolveTracksForMediaItems(mediaItems);
        if (tracks.isEmpty()) {
            return false;
        }
        playQueue(tracks, startIndex, startPositionMs);
        return true;
    }

    private MediaItem mediaItemForTrack(Track track) {
        return mediaSourceProvider.mediaItemForTrack(track, this::mediaMetadataForPlaybackTrack);
    }

    private MediaMetadata mediaMetadataForPlaybackTrack(Track track) {
        return playbackNotificationManager == null
                ? new MediaMetadata.Builder().build()
                : playbackNotificationManager.mediaMetadataForTrack(track);
    }

    @OptIn(markerClass = UnstableApi.class)
    private DefaultMediaSourceFactory mediaSourceFactory(Track track) {
        return mediaSourceProvider.mediaSourceFactory(track);
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

    private boolean isHttpUri(Uri uri) {
        return mediaSourceProvider.isHttpUri(uri);
    }

    private String cacheKeyForTrack(Track track) {
        return mediaSourceProvider.cacheKeyForTrack(track);
    }

    private Map<String, String> headersForTrack(Track track) {
        return mediaSourceProvider.headersForTrack(track);
    }

    private boolean hasNotificationWorthyState() {
        return currentTrack() != null || !queue.isEmpty() || playbackRuntimeStateManager.preparing() || isPlaying();
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
        if (currentIndex() < 0 || currentIndex() >= queue.size()) {
            return null;
        }
        return queue.get(currentIndex());
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

