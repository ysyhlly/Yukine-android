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

import java.io.File;
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
import app.yukine.data.EmbeddedArtwork;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackAudioEffectManager;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;
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
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.ContentMetadata;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
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
    public static final String ACTION_PLAY = "app.yukine.action.PLAY";
    public static final String ACTION_PAUSE = "app.yukine.action.PAUSE";
    public static final String ACTION_PREVIOUS = "app.yukine.action.PREVIOUS";
    public static final String ACTION_NEXT = "app.yukine.action.NEXT";
    public static final String ACTION_STOP = "app.yukine.action.STOP";
    public static final String ACTION_TOGGLE_FAVORITE = "app.yukine.action.TOGGLE_FAVORITE";
    public static final String ACTION_RESTORE = "app.yukine.action.RESTORE";
    public static final String ACTION_RESTORE_AND_PLAY = "app.yukine.action.RESTORE_AND_PLAY";

    public static final int REPEAT_ALL = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_OFF = 2;

    private static final String CHANNEL_ID = "echo_next_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "EchoPlaybackService";
    private static final String EMPTY_NOTIFICATION_TITLE = "Yukine";
    private static final String EMPTY_NOTIFICATION_TEXT = "Ready to resume playback";
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";
    private static final int NOTIFICATION_ACCENT = 0xFFEBC9A6;
    private static final long CROSSFADE_FADE_OUT_MS = 700L;
    private static final long CROSSFADE_FADE_STEP_MS = 70L;
    private static final long VISUALIZATION_CACHE_BYTES = 64L * 1024L * 1024L;
    // Buffering policy tuned for music streaming. Keep a generous read-ahead window (so a brief
    // network dip doesn't stall mid-song) but start and recover quickly so the user feels little
    // latency. Start playback after ~2.5s of buffer instead of 5s, and resume after a stall once
    // ~5s is buffered instead of 15s; the previous values made every hiccup feel like a long hang.
    private static final int STREAMING_MIN_BUFFER_MS = 90000;
    private static final int STREAMING_MAX_BUFFER_MS = 600000;
    private static final int STREAMING_BUFFER_FOR_PLAYBACK_MS = 2500;
    private static final int STREAMING_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3500;
    private static final int STREAMING_BACK_BUFFER_MS = 60000;
    private static final long AUDIO_CACHE_MAX_BYTES = 1024L * 1024L * 1024L;
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
    private SimpleCache audioCache;
    @Inject
    MusicLibraryRepository repository;
    @Inject
    StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    private AudioEffectSettings audioEffectSettings = AudioEffectSettings.DEFAULT;
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

                    @Override
                    public int currentIndex() {
                        return currentIndex();
                    }

                    @Override
                    public int repeatMode() {
                        return playbackRuntimeStateManager != null ? playbackRuntimeStateManager.repeatMode() : REPEAT_ALL;
                    }

                    @Override
                    public boolean shuffleEnabled() {
                        return playbackRuntimeStateManager != null && playbackRuntimeStateManager.shuffleEnabled();
                    }

                    @Override
                    public boolean isPlaying() {
                        return EchoPlaybackService.this.isPlaying();
                    }

                    @Override
                    public boolean preparing() {
                        return playbackRuntimeStateManager.preparing();
                    }

                    @Override
                    public void clearRestoredPosition() {
                        if (playbackPositionManager != null) {
                            playbackPositionManager.clearRestoredPosition();
                        }
                    }

                    @Override
                    public void resetCurrentPlaybackPosition() {
                        if (playbackPositionManager != null) {
                            playbackPositionManager.resetCurrentPlaybackPosition();
                        }
                    }

                    @Override
                    public void savePlaybackResumeRequested(boolean requested) {
                        EchoPlaybackService.this.savePlaybackResumeRequested(requested);
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

                    @Override
                    public boolean seekMirroredQueueToCurrentIndex(boolean playWhenReady) {
                        return EchoPlaybackService.this.seekMirroredQueueToCurrentIndex(playWhenReady);
                    }

                    @Override
                    public long playbackPositionMs() {
                        return EchoPlaybackService.this.positionMs();
                    }

                    @Override
                    public Track restoredTrackFor(Track track) {
                        return streamingPlaybackHeaderStore.restoredTrackFor(track);
                    }

                    @Override
                    public void restoreForDataPath(String dataPath) {
                        streamingPlaybackHeaderStore.restoreForDataPath(dataPath);
                    }

                    @Override
                    public boolean isRestorableQueueTrack(Track track) {
                        return EchoPlaybackService.this.isRestorableQueueTrack(track);
                    }

                    @Override
                    public void setRestoredPosition(long trackId, long positionMs, boolean explicit) {
                        if (playbackPositionManager != null) {
                            playbackPositionManager.setRestoredPosition(trackId, positionMs, explicit);
                        }
                    }

                    @Override
                    public void setCurrentIndex(int index) {
                        EchoPlaybackService.this.setCurrentIndex(index);
                    }

                    @Override
                    public void setErrorMessage(String message) {
                        playbackRuntimeStateManager.setErrorMessage(message);
                    }

                    @Override
                    public void setPreparing(boolean preparing) {
                        playbackRuntimeStateManager.setPreparing(preparing);
                    }

                    @Override
                    public void setLastMarkedTrack(Track track) {
                        playbackTransitionStateManager.setLastMarkedTrack(track);
                    }

                    @Override
                    public long setExplicitRestoredPosition(Track track, long positionMs) {
                        return playbackPositionManager == null
                                ? 0L
                                : playbackPositionManager.setExplicitRestoredPosition(track, positionMs);
                    }

                    @Override
                    public void recordStreamingRecovery(Track track, long restoredPositionMs) {
                        streamingDiagnostics.recordRecovery(track, restoredPositionMs, qualityFromDataPath(track.dataPath));
                    }

                    @Override
                    public void schedulePrepareCurrent(boolean playWhenReady) {
                        playbackTaskScheduler.schedule(
                                PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                                () -> mainHandler.post(() -> prepareCurrent(playWhenReady))
                        );
                    }

                    @Override
                    public boolean mirroredQueueMatchesCurrentPlayer() {
                        return EchoPlaybackService.this.mirroredQueueMatchesCurrentPlayer();
                    }

                    @Override
                    public void resetWaveformIfTrackChanged(Track track) {
                        EchoPlaybackService.this.resetWaveformIfTrackChanged(track);
                    }

                    @Override
                    public void applyPlaybackParametersToPlayer() {
                        EchoPlaybackService.this.applyPlaybackParametersToPlayer();
                    }

                    @Override
                    public void applyPlaybackModeToPlayer() {
                        EchoPlaybackService.this.applyPlaybackModeToPlayer();
                    }

                    @Override
                    public boolean seekMirroredQueueTo(int index, long positionMs, boolean playWhenReady) {
                        if (player == null) {
                            return false;
                        }
                        try {
                            player.seekTo(index, positionMs);
                            player.setPlayWhenReady(playWhenReady);
                            if (playWhenReady) {
                                player.play();
                            }
                            return true;
                        } catch (IllegalStateException error) {
                            Log.w(TAG, "Unable to reuse mirrored queue", error);
                            return false;
                        }
                    }

                    @Override
                    public void setPlayerMirrorsQueue(boolean enabled) {
                        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(enabled);
                    }

                    @Override
                    public void acquireWifiLockIfStreaming() {
                        if (playbackWifiLockManager != null) {
                            playbackWifiLockManager.acquireIfStreaming();
                        }
                    }

                    @Override
                    public void startProgressUpdates() {
                        EchoPlaybackService.this.startProgressUpdates();
                    }
                }
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
                    public String cacheKeyForTrack(Track track) {
                        return EchoPlaybackService.this.cacheKeyForTrack(track);
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
                        return EchoPlaybackService.this.cacheDataSourceForTrack(track);
                    }

                    @Override
                    public String mediaCacheKeyForTrack(Track track) {
                        return EchoPlaybackService.this.mediaCacheKeyForTrack(track);
                    }

                    @Override
                    public long continuousCachedBytes(String cacheKey) {
                        return EchoPlaybackService.this.continuousCachedBytes(cacheKey);
                    }

                    @Override
                    public float bufferedProgress(long durationMs) {
                        return EchoPlaybackService.this.bufferedProgress(durationMs);
                    }

                    @Override
                    public long contentLengthForCacheKey(String cacheKey) {
                        return EchoPlaybackService.this.contentLengthForCacheKey(cacheKey);
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
                        return EchoPlaybackService.this.continuousCachedBytes(cacheKey);
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
                                return EchoPlaybackService.this.cacheDataSourceForTrack(track);
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
            public Map<String, String> headersForTrack(Track track) {
                return EchoPlaybackService.this.headersForTrack(track);
            }

            @Override
            public SimpleCache audioCache() {
                return EchoPlaybackService.this.audioCache();
            }

            @Override
            public CacheDataSource cacheDataSourceForTrack(Track track) {
                return EchoPlaybackService.this.cacheDataSourceForTrack(track);
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
                    return mediaItemMatchesTrackForReuse(mediaItem, track.id, track.contentUri, cacheKey);
                } catch (IllegalStateException ignored) {
                    return false;
                }
            }

            @Override
            public long contentLengthForCacheKey(String cacheKey) {
                return EchoPlaybackService.this.contentLengthForCacheKey(cacheKey);
            }

            @Override
            public PlaybackStreamingDiagnostics streamingDiagnostics() {
                return streamingDiagnostics;
            }

            @Override
            public Handler mainHandler() {
                return mainHandler;
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
        return new ForwardingPlayer(player) {
            @Override
            public void play() {
                EchoPlaybackService.this.play();
            }

            @Override
            public void pause() {
                EchoPlaybackService.this.pause();
            }

            @Override
            public void seekTo(long positionMs) {
                EchoPlaybackService.this.seekTo(positionMs);
            }

            @Override
            public boolean isCommandAvailable(int command) {
                if (isAppQueueNavigationCommand(command) || command == Player.COMMAND_SET_REPEAT_MODE) {
                    return true;
                }
                return super.isCommandAvailable(command);
            }

            @Override
            public Player.Commands getAvailableCommands() {
                return new Player.Commands.Builder()
                        .addAll(super.getAvailableCommands())
                        .addAll(
                                Player.COMMAND_SEEK_TO_PREVIOUS,
                                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                                Player.COMMAND_SEEK_TO_NEXT,
                                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                                Player.COMMAND_SET_REPEAT_MODE
                        )
                        .build();
            }

            @Override
            public void seekTo(int mediaItemIndex, long positionMs) {
                EchoPlaybackService.this.seekTo(positionMs);
            }

            @Override
            public void seekToPrevious() {
                skipToPrevious();
            }

            @Override
            public void seekToPreviousMediaItem() {
                skipToPrevious();
            }

            @Override
            public void previous() {
                skipToPrevious();
            }

            @Override
            public void seekToNext() {
                skipToNext();
            }

            @Override
            public void seekToNextMediaItem() {
                skipToNext();
            }

            @Override
            public void next() {
                skipToNext();
            }

            @Override
            public void setRepeatMode(int repeatMode) {
                EchoPlaybackService.this.setRepeatMode(appRepeatModeForMedia3RepeatMode(repeatMode));
            }

            @Override
            public void stop() {
                stopAndClear();
            }

            @Override
            public void setPlayWhenReady(boolean playWhenReady) {
                if (playWhenReady) {
                    EchoPlaybackService.this.play();
                } else {
                    EchoPlaybackService.this.pause();
                }
            }

            @Override
            public MediaMetadata getMediaMetadata() {
                Track track = currentTrack();
                return track == null || playbackNotificationManager == null
                        ? super.getMediaMetadata()
                        : playbackNotificationManager.mediaMetadataForTrack(track);
            }

            @Override
            public void setMediaItem(MediaItem mediaItem) {
                if (!setControllerMediaItems(Collections.singletonList(mediaItem), 0, C.TIME_UNSET)) {
                    super.setMediaItem(mediaItem);
                }
            }

            @Override
            public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
                if (!setControllerMediaItems(Collections.singletonList(mediaItem), 0, startPositionMs)) {
                    super.setMediaItem(mediaItem, startPositionMs);
                }
            }

            @Override
            public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
                long startPositionMs = resetPosition ? 0L : C.TIME_UNSET;
                if (!setControllerMediaItems(Collections.singletonList(mediaItem), 0, startPositionMs)) {
                    super.setMediaItem(mediaItem, resetPosition);
                }
            }

            @Override
            public void setMediaItems(List<MediaItem> mediaItems) {
                if (!setControllerMediaItems(mediaItems, 0, C.TIME_UNSET)) {
                    super.setMediaItems(mediaItems);
                }
            }

            @Override
            public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
                long startPositionMs = resetPosition ? 0L : C.TIME_UNSET;
                if (!setControllerMediaItems(mediaItems, 0, startPositionMs)) {
                    super.setMediaItems(mediaItems, resetPosition);
                }
            }

            @Override
            public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
                if (!setControllerMediaItems(mediaItems, startIndex, startPositionMs)) {
                    super.setMediaItems(mediaItems, startIndex, startPositionMs);
                }
            }
        };
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
        return ACTION_PLAY.equals(action)
                || ACTION_PAUSE.equals(action)
                || ACTION_PREVIOUS.equals(action)
                || ACTION_NEXT.equals(action)
                || ACTION_TOGGLE_FAVORITE.equals(action)
                || ACTION_RESTORE.equals(action)
                || ACTION_RESTORE_AND_PLAY.equals(action)
                || ACTION_STOP.equals(action);
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
        mainHandler.removeCallbacksAndMessages(null);
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
        if (playbackQueueManager != null) {
            playbackQueueManager.skipToNextImmediately();
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
        if (playbackQueueManager != null) {
            playbackQueueManager.skipToPrevious();
        }
    }

    public List<Track> queueSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    private boolean seekMirroredQueueToCurrentIndex(boolean playWhenReady) {
        if (!playbackQueueRuntimeStateManager.playerMirrorsQueue() || player == null || player.getMediaItemCount() != queue.size()) {
            return false;
        }
        int targetIndex = safeCurrentIndex();
        Track track = currentTrack();
        playbackRuntimeStateManager.setErrorMessage("");
        playbackTransitionStateManager.setLastMarkedTrack(null);
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        if (track != null) {
            resetWaveformIfTrackChanged(track);
            streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        }
        try {
            if (playbackRuntimeStateManager != null) {
                playbackRuntimeStateManager.applyAppVolume();
            }
            player.seekTo(targetIndex, 0L);
            if (playWhenReady) {
                player.play();
                savePlaybackResumeRequested(true);
            }
            publishState();
            startProgressUpdates();
            return true;
        } catch (IllegalStateException error) {
            Log.w(TAG, "Unable to seek mirrored queue", error);
            playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
            return false;
        }
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
            playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
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
        refreshPlaybackSession();
        publishPlaybackNotification(true);
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
        if (canMirrorQueueToPlayer()) {
            prepareMirroredQueue(playWhenReady, startPositionMs);
        } else {
            prepareSingleTrack(track, playWhenReady, startPositionMs);
        }
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

    private boolean canMirrorQueueToPlayer() {
        if (queue.isEmpty() || currentIndex() < 0 || currentIndex() >= queue.size()) {
            return false;
        }
        for (Track track : queue) {
            if (track == null || Uri.EMPTY.equals(track.contentUri)) {
                return false;
            }
        }
        return true;
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
        if (player == null) {
            releasePlaybackSession();
            audioEffectManager.release();
            releaseAudioCache();
            return;
        }
        releasePlaybackSession();
        audioEffectManager.release();
        try {
            player.removeListener(playerListener);
            player.stop();
        } catch (IllegalStateException ignored) {
            // Player is already unusable.
        }
        player.release();
        player = null;
        playbackQueueRuntimeStateManager.setPlayerMirrorsQueue(false);
        playbackRuntimeStateManager.setPreparing(false);
        releaseAudioCache();
    }

    private void releaseAudioCache() {
        if (audioCache == null) {
            return;
        }
        try {
            audioCache.release();
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to release audio cache", error);
        }
        audioCache = null;
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
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(
                    Context context,
                    boolean enableFloatOutput,
                    boolean enableAudioTrackPlaybackParams
            ) {
                return new DefaultAudioSink.Builder(context)
                        .setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{realtimeBassAudioProcessor})
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build();
            }
        };
        player = new ExoPlayer.Builder(this, renderersFactory)
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                                STREAMING_MIN_BUFFER_MS,
                                STREAMING_MAX_BUFFER_MS,
                                STREAMING_BUFFER_FOR_PLAYBACK_MS,
                                STREAMING_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .setBackBuffer(STREAMING_BACK_BUFFER_MS, true)
                        .build())
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
                .setHandleAudioBecomingNoisy(true)
                .build();
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

    private boolean isRestorableQueueTrack(Track track) {
        if (track == null || track.id < 0L) {
            return false;
        }
        if (track.dataPath == null || track.dataPath.trim().isEmpty()) {
            return false;
        }
        if (track.contentUri == null || Uri.EMPTY.equals(track.contentUri)) {
            return isStreamingPlaceholder(track);
        }
        String scheme = track.contentUri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            String path = track.contentUri.getPath();
            return path != null && new File(path).exists();
        }
        if (scheme == null || scheme.trim().isEmpty()) {
            return !track.contentUri.toString().trim().isEmpty();
        }
        return true;
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
        return playbackQueueRuntimeStateManager.currentIndex();
    }

    private void setCurrentIndex(int index) {
        playbackQueueRuntimeStateManager.setCurrentIndex(index);
    }

    private int clampedCurrentIndex() {
        return playbackQueueRuntimeStateManager.clampCurrentIndex(queue.size());
    }

    private void setClampedCurrentIndex(int index) {
        playbackQueueRuntimeStateManager.setClampedCurrentIndex(index, queue.size());
    }

    private void saveTrackPlaybackPosition(Track track, long positionMs) {
        if (playbackPositionManager != null) {
            playbackPositionManager.saveTrackPosition(track, positionMs);
        }
    }

    private boolean seekExistingMirroredQueue(boolean playWhenReady, long startPositionMs) {
        return playbackQueueManager != null
                && playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
    }

    private boolean mirroredQueueMatchesCurrentPlayer() {
        if (!playbackQueueRuntimeStateManager.playerMirrorsQueue()
                || player == null
                || player.getMediaItemCount() != queue.size()) {
            return false;
        }
        for (int i = 0; i < queue.size(); i++) {
            Track track = queue.get(i);
            if (track == null || !mediaItemMatchesTrackForReuse(
                    player.getMediaItemAt(i),
                    track.id,
                    track.contentUri,
                    cacheKeyForTrack(track))) {
                return false;
            }
        }
        return true;
    }

    static boolean mediaItemMatchesTrackForReuse(MediaItem mediaItem, long trackId, Uri contentUri, String cacheKey) {
        if (mediaItem == null || mediaItem.localConfiguration == null) {
            return false;
        }
        String mediaUri = mediaItem.localConfiguration.uri == null
                ? null
                : mediaItem.localConfiguration.uri.toString();
        String trackUri = contentUri == null ? null : contentUri.toString();
        return mediaItemIdentityMatchesForReuse(
                mediaItem.mediaId,
                mediaUri,
                mediaItem.localConfiguration.customCacheKey,
                trackId,
                trackUri,
                cacheKey
        );
    }

    static boolean mediaItemIdentityMatchesForReuse(
            String mediaId,
            String mediaUri,
            String mediaCacheKey,
            long trackId,
            String trackUri,
            String trackCacheKey
    ) {
        if (!String.valueOf(trackId).equals(mediaId)) {
            return false;
        }
        return stringEquals(mediaUri, trackUri)
                && cacheKeyMatchesForReuse(mediaCacheKey, trackCacheKey);
    }

    private static boolean cacheKeyMatchesForReuse(String left, String right) {
        if (left == null || right == null) {
            return true;
        }
        return left.equals(right);
    }

    private static boolean stringEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
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
        return playbackMediaLibraryCallback == null
                ? new MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(String.valueOf(track.id))
                .setCustomCacheKey(cacheKeyForTrack(track))
                .setMediaMetadata(playbackNotificationManager == null
                        ? new MediaMetadata.Builder().build()
                        : playbackNotificationManager.mediaMetadataForTrack(track))
                .build()
                : playbackMediaLibraryCallback.mediaItemForPlaybackTrack(track);
    }

    @OptIn(markerClass = UnstableApi.class)
    private DefaultMediaSourceFactory mediaSourceFactory(Track track) {
        Map<String, String> headers = headersForTrack(track);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }
        DefaultDataSource.Factory upstreamFactory = new DefaultDataSource.Factory(this, httpFactory);
        if (!isHttpUri(track.contentUri)) {
            return new DefaultMediaSourceFactory(upstreamFactory);
        }
        CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(audioCache())
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        return new DefaultMediaSourceFactory(cacheFactory);
    }

    private CacheDataSource cacheDataSourceForTrack(Track track) {
        Map<String, String> headers = headersForTrack(track);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }
        DefaultDataSource upstream = new DefaultDataSource(this, httpFactory.createDataSource());
        return new CacheDataSource(
                audioCache(),
                upstream,
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
        );
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

    private void maybeGenerateSpectrum(Track track, long durationMs, float cachedProgress, boolean allowQuickStart) {
        if (playbackVisualizationAnalyzer != null) {
            playbackVisualizationAnalyzer.spectrumSnapshot(track, durationMs, false);
        }
    }

    private void maybeGenerateStreamingWaveform(Track track, long durationMs, float cachedProgress) {
        if (playbackVisualizationAnalyzer != null) {
            playbackVisualizationAnalyzer.waveformSnapshot(track, durationMs, false);
        }
    }

    private long continuousCachedBytes(String cacheKey) {
        try {
            long length = audioCache().getCachedLength(cacheKey, 0L, Long.MAX_VALUE);
            if (length > 0L) {
                return length;
            }
        } catch (RuntimeException ignored) {
        }
        return 0L;
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

    private float visualizationCachedProgress(Track track, long durationMs) {
        if (track == null || durationMs <= 0L) {
            return 0.0f;
        }
        String cacheKey = mediaCacheKeyForTrack(track);
        long cachedBytes = continuousCachedBytes(cacheKey);
        long contentLength = contentLengthForCacheKey(cacheKey);
        float byteProgress = contentLength > 0L && cachedBytes > 0L
                ? Math.max(0.0f, Math.min(1.0f, cachedBytes / (float) contentLength))
                : 0.0f;
        return Math.max(bufferedProgress(durationMs), byteProgress);
    }

    private long contentLengthForCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isEmpty()) {
            return -1L;
        }
        try {
            return ContentMetadata.getContentLength(audioCache().getContentMetadata(cacheKey));
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private String waveformKey(Track track) {
        if (track == null) {
            return "";
        }
        return track.id + "|" + (track.contentUri == null ? "" : track.contentUri.toString()) + "|" + track.dataPath;
    }

    private String mediaCacheKeyForTrack(Track track) {
        String cacheKey = cacheKeyForTrack(track);
        if (cacheKey != null && !cacheKey.isEmpty()) {
            return cacheKey;
        }
        return track == null || track.contentUri == null ? "" : track.contentUri.toString();
    }

    private SimpleCache audioCache() {
        if (audioCache == null) {
            File cacheDir = new File(getCacheDir(), "streaming-audio-cache");
            try {
                audioCache = new SimpleCache(
                        cacheDir,
                        new LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES)
                );
            } catch (RuntimeException error) {
                // If SimpleCache is left in a bad state after process death, clear and rebuild it.
                Log.w(TAG, "Audio cache corrupted; clearing and rebuilding", error);
                deleteRecursively(cacheDir);
                audioCache = new SimpleCache(
                        cacheDir,
                        new LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES)
                );
            }
        }
        return audioCache;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private boolean isHttpUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private String cacheKeyForTrack(Track track) {
        return mediaCacheKey(track);
    }

    static String mediaCacheKey(Track track) {
        if (track == null || track.dataPath == null || track.dataPath.isEmpty()) {
            return null;
        }
        String uri = track.contentUri == null ? "" : track.contentUri.toString();
        return mediaCacheKey(track.dataPath, uri);
    }

    static String mediaCacheKey(String dataPath, String uri) {
        if (dataPath == null || dataPath.isEmpty()) {
            return null;
        }
        if (dataPath.startsWith("streaming:")) {
            return uri == null || uri.isEmpty() ? dataPath : dataPath + "|url=" + uri;
        }
        if (dataPath.startsWith("webdav:")) {
            return dataPath;
        }
        return null;
    }

    private Map<String, String> headersForTrack(Track track) {
        HashMap<String, String> headers = new HashMap<>();
        headers.putAll(streamingPlaybackHeaderStore.forDataPath(track.dataPath));
        if (!track.dataPath.startsWith("webdav:")) {
            return headers;
        }
        long sourceId = webDavSourceId(track.dataPath);
        if (sourceId <= 0L) {
            return headers;
        }
        RemoteSource source = repository.loadRemoteSource(sourceId);
        if (source == null || !source.hasAuth()) {
            return headers;
        }
        String auth = source.username + ":" + source.password;
        String encoded = Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        headers.put("Authorization", "Basic " + encoded);
        return headers;
    }

    private long webDavSourceId(String dataPath) {
        String[] parts = dataPath.split(":", 3);
        if (parts.length < 3) {
            return -1L;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private String qualityFromDataPath(String dataPath) {
        if (dataPath == null || dataPath.isEmpty()) {
            return "";
        }
        int start = dataPath.indexOf("quality=");
        if (start < 0) {
            return "";
        }
        start += "quality=".length();
        int end = dataPath.indexOf(':', start);
        if (end < 0) {
            end = dataPath.indexOf('|', start);
        }
        if (end < 0) {
            end = dataPath.length();
        }
        return dataPath.substring(start, end);
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

