package app.yukine.playback;

import android.app.PendingIntent;
import android.app.Notification;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import app.yukine.MainActivity;
import app.yukine.R;
import app.yukine.data.EmbeddedArtwork;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackAudioEffectManager;
import app.yukine.playback.manager.PlaybackLyricsManager;
import app.yukine.playback.manager.PlaybackMediaLibraryCallback;
import app.yukine.playback.manager.PlaybackNotificationManager;
import app.yukine.playback.manager.PlaybackNotificationChannelOwner;
import app.yukine.playback.manager.PlaybackSessionManager;
import app.yukine.model.Playlist;
import app.yukine.model.PlaybackQueueState;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;
import app.yukine.streaming.StreamingPlaybackHeaderStore;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
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
    private static final long PLAYBACK_POSITION_SAVE_INTERVAL_MS = 5000L;
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
    private final Set<PlaybackStateListener> listeners = new CopyOnWriteArraySet<>();
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
    private boolean playerMirrorsQueue;
    private final PlaybackAudioEffectManager audioEffectManager =
            new PlaybackAudioEffectManager(TAG);
    private PlaybackSessionManager playbackSessionManager;
    private PlaybackQueueStore queueStore;
    private PlaybackLyricsManager playbackLyricsManager;
    private PlaybackNotificationManager playbackNotificationManager;
    private PlaybackVisualizationAnalyzer playbackVisualizationAnalyzer;
    private PlaybackVisualizationCacheManager playbackVisualizationCacheManager;
    private PlaybackNotificationArtworkManager playbackNotificationArtworkManager;
    private PlaybackPrecacheManager playbackPrecacheManager;
    private SimpleCache audioCache;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    @Inject
    MusicLibraryRepository repository;
    @Inject
    StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    private int currentIndex = -1;
    private boolean preparing;
    private boolean shuffleEnabled;
    private int repeatMode = REPEAT_ALL;
    private float playbackSpeed = 1.0f;
    private float appVolume = 1.0f;
    private AudioEffectSettings audioEffectSettings = AudioEffectSettings.DEFAULT;
    private boolean concurrentPlaybackEnabled = false;
    private boolean statusBarLyricsEnabled = true;
    private boolean playbackRestoreEnabled = true;
    private boolean replayGainEnabled = true;
    private long sleepTimerEndsAtMs;
    private long restoredPositionTrackId = -1L;
    private long restoredPositionMs;
    private boolean restoredPositionExplicit;
    private long lastSavedPositionTrackId = -1L;
    private long lastSavedPositionMs;
    private long lastPositionSaveAtMs;
    private long lastErrorTrackId = -1L;
    private long lastNotificationUpdateAtMs;
    private String errorMessage = "";
    private Track lastMarkedTrack;
    private boolean noisyReceiverRegistered;
    private boolean fadeOutAdvancing;
    private volatile boolean appVisible;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "" : intent.getAction();
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action) && isPlaying()) {
                pause();
            }
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            publishState();
            persistPlaybackPositionThrottled(false);
            if (isPlaying() || preparing) {
                mainHandler.postDelayed(this, 1000L);
            }
        }
    };

    private final Runnable sleepTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (sleepTimerEndsAtMs <= 0L) {
                return;
            }
            long remainingMs = sleepTimerRemainingMs();
            if (remainingMs <= 0L) {
                sleepTimerEndsAtMs = 0L;
                pause();
                return;
            }
            publishState();
            mainHandler.postDelayed(this, Math.min(remainingMs, 60000L));
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (player == null) {
                return;
            }
            if (playbackState == Player.STATE_READY) {
                preparing = false;
                errorMessage = "";
                lastErrorTrackId = -1L;
                Track track = currentTrack();
                if (player.getPlayWhenReady() && track != null && (lastMarkedTrack == null || lastMarkedTrack.id != track.id)) {
                    repository.markPlayed(track.id);
                    lastMarkedTrack = track;
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
            if (!playerMirrorsQueue || player == null || queue.isEmpty()) {
                return;
            }
            int nextIndex = player.getCurrentMediaItemIndex();
            if (nextIndex < 0 || nextIndex >= queue.size() || nextIndex == currentIndex) {
                return;
            }
            if (repeatMode == REPEAT_OFF && isAutomaticMediaItemAdvance(reason)) {
                stopAfterAutomaticAdvance(currentIndex);
                return;
            }
            persistPlaybackPositionThrottled(true);
            currentIndex = nextIndex;
            Track track = currentTrack();
            errorMessage = "";
            lastMarkedTrack = null;
            clearRestoredPosition();
            resetCurrentPlaybackPosition();
            if (track != null) {
                resetWaveformIfTrackChanged(track);
                streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
            }
            applyAppVolume();
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
                acquireWifiLockIfStreaming();
            } else {
                releaseWifiLock();
            }
            publishState();
            startProgressUpdates();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            preparing = false;
            Log.w(TAG, "Playback failed for " + debugTrack(currentTrack()), error);
            // A transient streaming/network hiccup must not leave playback silently stuck.
            // Retry the current track once; if it fails again, skip to the next track so
            // playback keeps moving instead of "mysteriously pausing".
            Track failed = currentTrack();
            long failedId = failed == null ? -1L : failed.id;
            boolean isStreaming = failed != null && isHttpUri(failed.contentUri);
            if (isStreaming && failedId != -1L && failedId != lastErrorTrackId) {
                lastErrorTrackId = failedId;
                Log.w(TAG, "Retrying streaming track after error: " + debugTrack(failed));
                mainHandler.postDelayed(() -> {
                    if (currentTrack() != null && currentTrack().id == failedId) {
                        prepareCurrent(true);
                    }
                }, 1500L);
                return;
            }
            if (failedId != -1L && queue.size() > 1) {
                lastErrorTrackId = -1L;
                Log.w(TAG, "Skipping unplayable track: " + debugTrack(failed));
                errorMessage = "";
                skipToNext();
                return;
            }
            errorMessage = "Unable to play this track.";
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
        shuffleEnabled = repository.loadShuffleEnabled();
        repeatMode = repository.loadRepeatMode();
        audioEffectSettings = repository.loadAudioEffectSettings();
        statusBarLyricsEnabled = repository.loadStatusBarLyricsEnabled();
        playbackRestoreEnabled = repository.loadPlaybackRestoreEnabled();
        replayGainEnabled = repository.loadReplayGainEnabled();
        new PlaybackNotificationChannelOwner(this).createNotificationChannel();
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
                return preparing;
            }

            @Override
            public void notifyMediaNotification(boolean force) {
                if (playbackNotificationManager != null) {
                    playbackNotificationManager.updateMediaNotification(force);
                }
            }

        });
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
                        return preparing;
                    }

                    @Override
                    public Track currentTrack() {
                        return EchoPlaybackService.this.currentTrack();
                    }

                    @Override
                    public android.media.session.MediaSession.Token playbackSessionPlatformToken() {
                        return playbackSessionManager == null ? null : playbackSessionManager.session().getPlatformToken();
                    }
                },
                new PlaybackNotificationManager.LyricsTextProvider() {
                    @Override
                    public String currentNotificationLyric(Track track) {
                        return playbackLyricsManager == null ? "" : playbackLyricsManager.currentNotificationLyric(track);
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
        PlaybackMediaLibraryCallback mediaLibraryCallback = new PlaybackMediaLibraryCallback(
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
                mediaLibraryCallback,
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

                    @Override
                    public boolean samePlaybackUri(Track first, Track second) {
                        return EchoPlaybackService.this.samePlaybackUri(first, second);
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
                    public void refreshNotificationArtwork() {
                        if (playbackSessionManager != null) {
                            playbackSessionManager.refreshPlayer();
                        }
                    }

                    @Override
                    public void updateMediaNotification(boolean force) {
                        if (playbackNotificationManager != null) {
                            playbackNotificationManager.updateMediaNotification(force);
                        }
                    }
                }
        );
        playbackPrecacheManager = new PlaybackPrecacheManager(new PlaybackPrecacheManager.StateProvider() {
            @Override
            public List<Track> queueSnapshot() {
                return new ArrayList<>(queue);
            }

            @Override
            public int currentIndex() {
                return currentIndex;
            }

            @Override
            public int repeatMode() {
                return repeatMode;
            }

            @Override
            public Track currentTrack() {
                return EchoPlaybackService.this.currentTrack();
            }

            @Override
            public boolean samePlaybackUri(Track first, Track second) {
                return EchoPlaybackService.this.samePlaybackUri(first, second);
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
            playbackNotificationManager.updateMediaNotification(true);
        }
        playbackLyricsManager.bind();
        registerNoisyReceiver();
        android.net.wifi.WifiManager wifiManager =
                (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "echo:playback");
        }
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
            playbackNotificationManager.updateMediaNotification(true);
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
            playbackNotificationManager.updateMediaNotification(true);
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
        savePlaybackResumeRequested(isPlaying() || preparing);
        if (hasNotificationWorthyState()) {
            playbackNotificationManager.updateMediaNotification(true);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        persistPlaybackPositionThrottled(true);
        if (playbackLyricsManager != null) {
            playbackLyricsManager.release();
        }
        mainHandler.removeCallbacksAndMessages(null);
        unregisterNoisyReceiver();
        playbackTaskScheduler.shutdownNow();
        visualizationTaskScheduler.shutdownNow();
        if (playbackNotificationArtworkManager != null) {
            playbackNotificationArtworkManager.release();
        }
        if (playbackPrecacheManager != null) {
            playbackPrecacheManager.release();
        }
        releaseWifiLock();
        releasePlayer();
        super.onDestroy();
    }

    public void registerListener(PlaybackStateListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.onPlaybackStateChanged(snapshot());
    }

    public void unregisterListener(PlaybackStateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void setAppVisible(boolean visible) {
        appVisible = visible;
        if (visible && hasNotificationWorthyState()) {
            playbackNotificationManager.updateMediaNotification(true);
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
        queue.clear();
        queue.addAll(tracks);
        currentIndex = Math.max(0, Math.min(startIndex, queue.size() - 1));
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        if (startPositionMs >= 0L) {
            Track track = currentTrack();
            if (track != null) {
                restoredPositionTrackId = track.id;
                restoredPositionMs = startPositionMs;
                restoredPositionExplicit = true;
            }
        }
        savePlaybackResumeRequested(true);
        prepareCurrent(true);
    }

    private void queueTracks(List<Track> tracks, int startIndex) {
        queue.clear();
        queue.addAll(tracks);
        currentIndex = Math.max(0, Math.min(startIndex, queue.size() - 1));
    }

    public void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        boolean wasEmpty = queue.isEmpty();
        queue.addAll(tracks);
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            currentIndex = 0;
        }
        persistPlaybackQueue();
        if (wasEmpty) {
            errorMessage = "";
            lastMarkedTrack = null;
            clearRestoredPosition();
            resetCurrentPlaybackPosition();
            savePlaybackResumeRequested(true);
            prepareCurrent(true);
            return;
        }
        publishState();
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
        if (preparing) {
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
        acquireWifiLockIfStreaming();
        publishState();
        startProgressUpdates();
    }

    private void playFirstQueuedTrack() {
        if (queue.isEmpty()) {
            return;
        }
        currentIndex = 0;
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        prepareCurrent(true);
    }

    public void pause() {
        fadeOutAdvancing = false;
        if (player != null && isPlaying()) {
            player.pause();
        }
        savePlaybackResumeRequested(false);
        releaseWifiLock();
        persistPlaybackPositionThrottled(true);
        publishState();
    }

    public void seekTo(long positionMs) {
        if (player == null || preparing) {
            return;
        }
        try {
            player.seekTo(Math.max(0L, positionMs));
            persistPlaybackPositionThrottled(true);
            publishState();
        } catch (IllegalStateException ignored) {
            errorMessage = "Playback is not ready.";
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
        if (shuffleEnabled && queue.size() > 1) {
            int nextIndex = currentIndex;
            while (nextIndex == currentIndex) {
                nextIndex = random.nextInt(queue.size());
            }
            currentIndex = nextIndex;
            return;
        }
        if (currentIndex >= queue.size() - 1) {
            if (repeatMode == REPEAT_OFF) {
                persistPlaybackQueue();
                publishState();
                return;
            }
            currentIndex = 0;
            return;
        }
        currentIndex += 1;
    }

    private void skipToNextImmediately() {
        persistPlaybackPositionThrottled(true);
        advanceQueueIndexToNext();
        if (seekMirroredQueueToCurrentIndex(true)) {
            return;
        }
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        savePlaybackResumeRequested(true);
        prepareCurrent(true);
    }

    private boolean startFadeOutThenNext() {
        if (fadeOutAdvancing || player == null || !isPlaying() || queue.size() < 2) {
            return false;
        }
        if (repeatMode == REPEAT_OFF && currentIndex >= queue.size() - 1) {
            return false;
        }
        fadeOutAdvancing = true;
        final float baseVolume = normalizeAppVolume(appVolume * replayGainMultiplier(currentTrack()));
        final long startedAtMs = System.currentTimeMillis();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (player == null) {
                    fadeOutAdvancing = false;
                    return;
                }
                if (!isPlaying()) {
                    fadeOutAdvancing = false;
                    applyAppVolume();
                    return;
                }
                long elapsedMs = System.currentTimeMillis() - startedAtMs;
                if (elapsedMs >= CROSSFADE_FADE_OUT_MS) {
                    skipToNextImmediately();
                    fadeOutAdvancing = false;
                    applyAppVolume();
                    return;
                }
                float fraction = 1.0f - Math.max(0f, Math.min(1.0f, elapsedMs / (float) CROSSFADE_FADE_OUT_MS));
                try {
                    player.setVolume(normalizeAppVolume(baseVolume * fraction));
                } catch (IllegalStateException ignored) {
                    fadeOutAdvancing = false;
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
        persistPlaybackPositionThrottled(true);
        currentIndex = currentIndex <= 0 ? queue.size() - 1 : currentIndex - 1;
        if (seekMirroredQueueToCurrentIndex(true)) {
            return;
        }
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        savePlaybackResumeRequested(true);
        prepareCurrent(true);
    }

    public List<Track> queueSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    private boolean seekMirroredQueueToCurrentIndex(boolean playWhenReady) {
        if (!playerMirrorsQueue || player == null || player.getMediaItemCount() != queue.size()) {
            return false;
        }
        int targetIndex = safeCurrentIndex();
        Track track = currentTrack();
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        if (track != null) {
            resetWaveformIfTrackChanged(track);
            streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        }
        try {
            applyAppVolume();
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
            playerMirrorsQueue = false;
            return false;
        }
    }

    public void moveQueueTrack(int fromIndex, int toIndex) {
        if (queue.isEmpty() || fromIndex == toIndex || fromIndex < 0 || fromIndex >= queue.size()) {
            return;
        }
        int targetIndex = Math.max(0, Math.min(toIndex, queue.size() - 1));
        Track current = currentTrack();
        Track moved = queue.remove(fromIndex);
        queue.add(targetIndex, moved);
        if (current != null) {
            currentIndex = indexOfTrackOccurrence(current);
        } else {
            currentIndex = Math.max(0, Math.min(currentIndex, queue.size() - 1));
        }
        persistPlaybackQueue();
        persistPlaybackPositionThrottled(true);
        publishState();
    }

    public PlaybackStreamingDiagnostics.Snapshot streamingDiagnostics() {
        return streamingDiagnostics.snapshot();
    }

    public void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        if (replacement == null || currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        Track current = currentTrack();
        long resumePositionMs = Math.max(Math.max(0L, positionMs), positionMs());
        if (samePlaybackUri(current, replacement)) {
            queue.set(currentIndex, replacement);
            errorMessage = "";
            persistPlaybackQueue();
            persistPlaybackPositionThrottled(true);
            publishState();
            return;
        }
        queue.set(currentIndex, replacement);
        errorMessage = "";
        lastMarkedTrack = null;
        restoredPositionTrackId = replacement.id;
        restoredPositionMs = clampPlaybackPosition(replacement, resumePositionMs);
        restoredPositionExplicit = true;
        streamingDiagnostics.recordRecovery(replacement, restoredPositionMs, qualityFromDataPath(replacement.dataPath));
        persistPlaybackQueue();
        playbackTaskScheduler.schedule(
                PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                () -> mainHandler.post(() -> prepareCurrent(true))
        );
    }

    private boolean samePlaybackUri(Track first, Track second) {
        if (first == null || second == null || first.contentUri == null || second.contentUri == null) {
            return false;
        }
        return first.contentUri.equals(second.contentUri);
    }

    private void scheduleVisualizationCache(Track track) {
        if (playbackVisualizationCacheManager == null) {
            return;
        }
        playbackVisualizationCacheManager.scheduleVisualizationCache(track);
    }

    public void precacheTrack(Track track) {
        if (playbackPrecacheManager != null) {
            playbackPrecacheManager.precacheTrack(track);
        }
    }

    public void removeTracksById(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty() || queue.isEmpty()) {
            return;
        }
        Track current = currentTrack();
        boolean removedCurrent = current != null && trackIds.contains(current.id);
        int removedBeforeCurrent = 0;
        for (int i = 0; i < queue.size(); i++) {
            Track track = queue.get(i);
            if (i < currentIndex && trackIds.contains(track.id)) {
                removedBeforeCurrent++;
            }
        }
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (trackIds.contains(queue.get(i).id)) {
                queue.remove(i);
            }
        }
        if (queue.isEmpty()) {
            stopAndClear();
            return;
        }
        if (currentIndex >= 0) {
            currentIndex = Math.max(0, currentIndex - removedBeforeCurrent);
        }
        if (currentIndex >= queue.size()) {
            currentIndex = queue.size() - 1;
        }
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        if (removedCurrent) {
            persistPlaybackQueue();
            resetCurrentPlaybackPosition();
            prepareCurrent(false);
        } else {
            persistPlaybackQueue();
            persistPlaybackPositionThrottled(true);
            publishState();
        }
    }

    public void retainTracksById(Set<Long> trackIdsToKeep) {
        if (trackIdsToKeep == null || queue.isEmpty()) {
            return;
        }
        HashSet<Long> trackIdsToRemove = new HashSet<>();
        for (Track track : queue) {
            if (!trackIdsToKeep.contains(track.id)) {
                trackIdsToRemove.add(track.id);
            }
        }
        removeTracksById(trackIdsToRemove);
    }

    public void clearQueue() {
        if (queue.isEmpty()) {
            return;
        }
        stopAndClear();
    }

    private void clearQueueState() {
        queue.clear();
        currentIndex = -1;
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
        if (!playbackRestoreEnabled) {
            publishState();
            return;
        }
        createPlayerIfNeeded();
        if (queue.isEmpty()) {
            restorePlaybackQueue();
        }
        if (currentTrack() == null) {
            publishState();
            return;
        }
        boolean shouldPlay = playWhenRestored || (repository != null && repository.loadPlaybackResumeRequested());
        prepareCurrent(shouldPlay);
    }

    public void replaceQueuedTrack(Track replacement) {
        if (replacement == null || queue.isEmpty()) {
            return;
        }
        boolean replaced = false;
        boolean replacedCurrent = false;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id == replacement.id) {
                if (i == currentIndex) {
                    replacedCurrent = true;
                }
                queue.set(i, replacement);
                replaced = true;
            }
        }
        if (replaced) {
            errorMessage = "";
            lastMarkedTrack = null;
            clearRestoredPosition();
            persistPlaybackQueue();
            if (replacedCurrent) {
                resetCurrentPlaybackPosition();
                prepareCurrent(isPlaying() || player == null || player.getMediaItemCount() == 0);
                return;
            }
            publishState();
            playbackNotificationManager.updateMediaNotification(true);
        }
    }

    public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        if (replacement == null || queue.isEmpty()) {
            return;
        }
        if (oldTrackId == replacement.id) {
            replaceQueuedTrack(replacement);
            return;
        }
        boolean targetAlreadyQueued = false;
        for (Track track : queue) {
            if (track.id == replacement.id) {
                targetAlreadyQueued = true;
                break;
            }
        }
        if (targetAlreadyQueued) {
            replaceAndCollapseQueuedTrack(oldTrackId, replacement);
            return;
        }
        boolean replaced = false;
        boolean replacedCurrent = false;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id == oldTrackId) {
                queue.set(i, replacement);
                replaced = true;
                if (i == currentIndex) {
                    replacedCurrent = true;
                }
            }
        }
        if (!replaced) {
            replaceQueuedTrack(replacement);
            return;
        }
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        if (replacedCurrent) {
            resetCurrentPlaybackPosition();
            prepareCurrent(isPlaying());
        } else {
            persistPlaybackPositionThrottled(true);
            publishState();
        }
    }

    private void replaceAndCollapseQueuedTrack(long oldTrackId, Track replacement) {
        int preferredIndex = -1;
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            long currentId = queue.get(currentIndex).id;
            if (currentId == oldTrackId || currentId == replacement.id) {
                preferredIndex = currentIndex;
            }
        }
        if (preferredIndex < 0) {
            for (int i = 0; i < queue.size(); i++) {
                long trackId = queue.get(i).id;
                if (trackId == oldTrackId || trackId == replacement.id) {
                    preferredIndex = i;
                    break;
                }
            }
        }
        if (preferredIndex < 0) {
            return;
        }

        boolean replaced = false;
        boolean currentWasOldTrack = false;
        ArrayList<Track> collapsedQueue = new ArrayList<>();
        int collapsedCurrentIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            Track track = queue.get(i);
            boolean isOldTrack = track.id == oldTrackId;
            boolean isReplacementTrack = track.id == replacement.id;
            if (isOldTrack) {
                replaced = true;
                if (i == currentIndex) {
                    currentWasOldTrack = true;
                }
            }
            if (isOldTrack || isReplacementTrack) {
                if (i == preferredIndex) {
                    collapsedCurrentIndex = collapsedQueue.size();
                    collapsedQueue.add(replacement);
                }
                continue;
            }
            if (i == currentIndex) {
                collapsedCurrentIndex = collapsedQueue.size();
            }
            collapsedQueue.add(track);
        }
        if (!replaced) {
            return;
        }
        queue.clear();
        queue.addAll(collapsedQueue);
        if (queue.isEmpty()) {
            stopAndClear();
            return;
        }
        if (collapsedCurrentIndex >= 0) {
            currentIndex = collapsedCurrentIndex;
        } else if (currentIndex >= 0) {
            currentIndex = Math.min(currentIndex, queue.size() - 1);
        } else {
            currentIndex = -1;
        }
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        if (currentWasOldTrack) {
            resetCurrentPlaybackPosition();
            prepareCurrent(isPlaying());
        } else {
            persistPlaybackPositionThrottled(true);
            publishState();
            playbackNotificationManager.updateMediaNotification(true);
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
                currentIndex,
                queue.size(),
                positionMs(),
                duration,
                isPlaying(),
                preparing,
                errorMessage,
                shuffleEnabled,
                repeatMode,
                playbackSpeed,
                appVolume,
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
        shuffleEnabled = enabled;
        repository.saveShuffleEnabled(shuffleEnabled);
        applyPlaybackModeToPlayer();
        publishState();
    }

    public void setRepeatMode(int mode) {
        if (mode != REPEAT_ALL && mode != REPEAT_ONE && mode != REPEAT_OFF) {
            mode = REPEAT_ALL;
        }
        repeatMode = mode;
        repository.saveRepeatMode(repeatMode);
        applyPlaybackModeToPlayer();
        publishState();
    }

    public void cycleRepeatMode() {
        if (repeatMode == REPEAT_ALL) {
            repeatMode = REPEAT_ONE;
        } else if (repeatMode == REPEAT_ONE) {
            repeatMode = REPEAT_OFF;
        } else {
            repeatMode = REPEAT_ALL;
        }
        repository.saveRepeatMode(repeatMode);
        applyPlaybackModeToPlayer();
        publishState();
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed = normalizePlaybackSpeed(speed);
        applyPlaybackSpeed();
        publishState();
    }

    public float playbackSpeed() {
        return playbackSpeed;
    }

    public void setAppVolume(float volume) {
        appVolume = normalizeAppVolume(volume);
        applyAppVolume();
        publishState();
    }

    public float appVolume() {
        return appVolume;
    }

    public void setConcurrentPlaybackEnabled(boolean enabled) {
        concurrentPlaybackEnabled = enabled;
        applyAudioFocusHandling();
    }

    public boolean concurrentPlaybackEnabled() {
        return concurrentPlaybackEnabled;
    }

    public void setStatusBarLyricsEnabled(boolean enabled) {
        if (playbackLyricsManager != null) {
            playbackLyricsManager.setStatusBarLyricsEnabled(enabled);
        }
        statusBarLyricsEnabled = enabled;
        if (playbackSessionManager != null) {
            playbackSessionManager.refreshPlayer();
        }
        if (playbackNotificationManager != null) {
            playbackNotificationManager.updateMediaNotification(true);
        }
    }

    public void setPlaybackRestoreEnabled(boolean enabled) {
        playbackRestoreEnabled = enabled;
    }

    public void setReplayGainEnabled(boolean enabled) {
        replayGainEnabled = enabled;
        applyAppVolume();
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
            queueStore = new PlaybackQueueStore(repository);
        }
        return queueStore;
    }

    public void startSleepTimerMinutes(int minutes) {
        if (minutes <= 0) {
            cancelSleepTimer();
            return;
        }
        long durationMs = Math.min(minutes, 240) * 60000L;
        sleepTimerEndsAtMs = System.currentTimeMillis() + durationMs;
        scheduleSleepTimer();
        publishState();
    }

    public void cancelSleepTimer() {
        cancelSleepTimerInternal(true);
    }

    public long sleepTimerRemainingMs() {
        if (sleepTimerEndsAtMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, sleepTimerEndsAtMs - System.currentTimeMillis());
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
            preparing = false;
            errorMessage = isStreamingPlaceholder(track)
                    ? "Streaming track is not resolved yet. Tap the track again to play."
                    : "Unable to open this track.";
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
        ArrayList<MediaSource> mediaSources = new ArrayList<>();
        for (Track queueTrack : queue) {
            if (queueTrack == null || Uri.EMPTY.equals(queueTrack.contentUri)) {
                prepareSingleTrack(track, playWhenReady, startPositionMs);
                return;
            }
            streamingPlaybackHeaderStore.restoreForDataPath(queueTrack.dataPath);
            mediaSources.add(mediaSourceFactory(queueTrack).createMediaSource(mediaItemForTrack(queueTrack)));
        }
        preparing = true;
        createPlayerIfNeeded();
        lastMarkedTrack = null;
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        applyPlaybackSpeed();
        applyAppVolume();
        player.clearMediaItems();
        player.setMediaSources(mediaSources, safeCurrentIndex(), Math.max(0L, startPositionMs));
        playerMirrorsQueue = true;
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            precacheTrack(track);
            scheduleVisualizationCache(track);
            if (startPositionMs > 0L) {
                clearRestoredPosition();
            }
            publishState();
            if (playbackNotificationManager != null) {
                playbackNotificationManager.updateMediaNotification(true);
            }
        } catch (IllegalStateException error) {
            preparing = false;
            Log.w(TAG, "Unable to prepare mirrored queue for " + debugTrack(track), error);
            errorMessage = "Unable to open this track.";
            releasePlayer();
            publishState();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareSingleTrack(Track track, final boolean playWhenReady, final long startPositionMs) {
        preparing = true;
        createPlayerIfNeeded();
        lastMarkedTrack = null;
        resetWaveformIfTrackChanged(track);
        postponePlaybackVisualizationWarmup();
        streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        player.stop();
        player.clearMediaItems();
        playerMirrorsQueue = false;
        applyPlaybackSpeed();
        applyAppVolume();
        player.setMediaSource(mediaSourceFactory(track).createMediaSource(mediaItemForTrack(track)));
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            precacheTrack(track);
            scheduleVisualizationCache(track);
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs);
                clearRestoredPosition();
            }
            publishState();
            if (playbackNotificationManager != null) {
                playbackNotificationManager.updateMediaNotification(true);
            }
        } catch (IllegalStateException error) {
            preparing = false;
            Log.w(TAG, "Unable to prepare player for " + debugTrack(track), error);
            errorMessage = "Unable to open this track.";
            releasePlayer();
            publishState();
        }
    }

    private boolean canMirrorQueueToPlayer() {
        if (queue.isEmpty() || currentIndex < 0 || currentIndex >= queue.size()) {
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
            if (playbackSessionManager != null) {
                playbackSessionManager.release();
            }
            audioEffectManager.release();
            releaseAudioCache();
            return;
        }
        if (playbackSessionManager != null) {
            playbackSessionManager.release();
        }
        audioEffectManager.release();
        try {
            player.removeListener(playerListener);
            player.stop();
        } catch (IllegalStateException ignored) {
            // Player is already unusable.
        }
        player.release();
        player = null;
        playerMirrorsQueue = false;
        preparing = false;
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
        cancelSleepTimerInternal(false);
        clearRestoredPosition();
        queueStore().savePlaybackPosition(-1L, 0L);
        lastSavedPositionTrackId = -1L;
        lastSavedPositionMs = 0L;
        releasePlayer();
        preparing = false;
        errorMessage = "";
        lastMarkedTrack = null;
        fadeOutAdvancing = false;
        clearQueueState();
        persistPlaybackQueue();
        savePlaybackResumeRequested(false);
        mainHandler.removeCallbacks(progressRunnable);
        releaseWifiLock();
        if (playbackLyricsManager != null) {
            playbackLyricsManager.release();
        }
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
            queueStore().savePlaybackPosition(completed.id, 0L);
        }
        if (repeatMode == REPEAT_ONE) {
            clearRestoredPosition();
            prepareCurrent(true);
            return;
        }
        if (repeatMode == REPEAT_OFF && currentIndex >= queue.size() - 1) {
            stopAtEndOfQueue();
            savePlaybackResumeRequested(false);
            return;
        }
        skipToNext();
    }

    private void stopAtEndOfQueue() {
        clearRestoredPosition();
        preparing = false;
        errorMessage = "";
        lastMarkedTrack = null;
        mainHandler.removeCallbacks(progressRunnable);
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
        mainHandler.removeCallbacks(progressRunnable);
        if (isPlaying() || preparing) {
            mainHandler.postDelayed(progressRunnable, 1000L);
        }
    }

    private void scheduleSleepTimer() {
        mainHandler.removeCallbacks(sleepTimerRunnable);
        long remainingMs = sleepTimerRemainingMs();
        if (remainingMs > 0L) {
            mainHandler.postDelayed(sleepTimerRunnable, Math.min(remainingMs, 60000L));
        }
    }

    private void cancelSleepTimerInternal(boolean publish) {
        sleepTimerEndsAtMs = 0L;
        mainHandler.removeCallbacks(sleepTimerRunnable);
        if (publish) {
            publishState();
        }
    }

    private void publishState() {
        PlaybackStateSnapshot snapshot = snapshot();
        if (playbackLyricsManager != null) {
            playbackLyricsManager.syncFloatingLyricsPlaybackState(snapshot);
        }
        applyPlaybackModeToPlayer();
        if (playbackNotificationManager != null) {
            playbackNotificationManager.updateMediaNotification(false);
        }
        Track track = snapshot == null ? null : snapshot.currentTrack;
        Bitmap artwork = track == null || playbackNotificationArtworkManager == null
                ? null
                : playbackNotificationArtworkManager.notificationArtworkFor(track);
        EchoPlaybackWidgetProvider.update(this, snapshot, artwork);
        for (PlaybackStateListener listener : listeners) {
            listener.onPlaybackStateChanged(snapshot);
        }
    }

    private void publishBufferingState() {
        PlaybackStateSnapshot snapshot = snapshot();
        streamingDiagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        for (PlaybackStateListener listener : listeners) {
            listener.onPlaybackBuffering(snapshot);
        }
    }

    private void applyPlaybackModeToPlayer() {
        if (player == null) {
            return;
        }
        player.setShuffleModeEnabled(shuffleEnabled);
        player.setRepeatMode(media3RepeatModeForAppRepeatMode(repeatMode, playerMirrorsQueue));
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
        currentIndex = Math.max(0, Math.min(completedIndex, queue.size() - 1));
        Track completed = currentTrack();
        if (completed != null) {
            queueStore().savePlaybackPosition(completed.id, 0L);
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
        applyAudioFocusHandling();
        player.addListener(playerListener);
        applyPlaybackSpeed();
        applyAppVolume();
        applyPlaybackModeToPlayer();
        audioEffectManager.bind(player, audioEffectSettings);
        if (playbackSessionManager != null) {
            playbackSessionManager.bind();
        }
    }

    private void restorePlaybackQueue() {
        if (!playbackRestoreEnabled) {
            return;
        }
        PlaybackQueueState savedQueue = queueStore().load();
        if (savedQueue.isEmpty()) {
            return;
        }
        queue.clear();
        for (Track track : savedQueue.tracks) {
            if (!isRestorableQueueTrack(track)) {
                continue;
            }
            Track restoredTrack = streamingPlaybackHeaderStore.restoredTrackFor(track);
            Track queueTrack = restoredTrack == null ? track : restoredTrack;
            queue.add(queueTrack);
            streamingPlaybackHeaderStore.restoreForDataPath(queueTrack.dataPath);
        }
        if (queue.isEmpty()) {
            currentIndex = -1;
            return;
        }
        currentIndex = Math.max(0, Math.min(savedQueue.currentIndex, queue.size() - 1));
        restoredPositionTrackId = queueStore().loadPlaybackPositionTrackId();
        restoredPositionMs = queueStore().loadPlaybackPositionMs();
        errorMessage = "";
        lastMarkedTrack = null;
        persistPlaybackQueue();
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
        return Math.max(0, Math.min(currentIndex, queue.size() - 1));
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private int safeCurrentIndex() {
        if (queue.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(currentIndex, queue.size() - 1));
    }

    private void savePlaybackResumeRequested(boolean requested) {
        queueStore().saveResumeRequested(requested);
    }

    private void replaceCurrentQueueTrack(Track track) {
        if (track == null || currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        queue.set(currentIndex, track);
        persistPlaybackQueue();
    }

    private void persistPlaybackQueue() {
        queueStore().save(new ArrayList<>(queue), currentIndex);
    }

    private void persistPlaybackPositionThrottled(boolean force) {
        Track track = currentTrack();
        if (track == null) {
            return;
        }
        long position = positionMs();
        long now = System.currentTimeMillis();
        if (!force
                && track.id == lastSavedPositionTrackId
                && Math.abs(position - lastSavedPositionMs) < PLAYBACK_POSITION_SAVE_INTERVAL_MS
                && now - lastPositionSaveAtMs < PLAYBACK_POSITION_SAVE_INTERVAL_MS) {
            return;
        }
        queueStore().savePlaybackPosition(track.id, clampPlaybackPosition(track, position));
        lastSavedPositionTrackId = track.id;
        lastSavedPositionMs = position;
        lastPositionSaveAtMs = now;
    }

    private void resetCurrentPlaybackPosition() {
        Track track = currentTrack();
        if (track == null) {
            return;
        }
        queueStore().savePlaybackPosition(track.id, 0L);
        lastSavedPositionTrackId = track.id;
        lastSavedPositionMs = 0L;
        lastPositionSaveAtMs = System.currentTimeMillis();
    }

    private long restoredPositionFor(Track track) {
        if (track == null || track.id != restoredPositionTrackId) {
            return 0L;
        }
        if (track.dataPath != null && track.dataPath.startsWith("streaming:") && !restoredPositionExplicit) {
            return 0L;
        }
        return clampPlaybackPosition(track, restoredPositionMs);
    }

    private long clampPlaybackPosition(Track track, long positionMs) {
        long position = Math.max(0L, positionMs);
        long duration = track == null ? 0L : track.durationMs;
        if (duration <= 0L) {
            return position;
        }
        long latestResumePosition = Math.max(0L, duration - 2000L);
        return Math.min(position, latestResumePosition);
    }

    private void clearRestoredPosition() {
        restoredPositionTrackId = -1L;
        restoredPositionMs = 0L;
        restoredPositionExplicit = false;
    }

    private void registerNoisyReceiver() {
        if (noisyReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, filter);
        }
        noisyReceiverRegistered = true;
    }

    private void unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(noisyReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was already unregistered by the system.
        }
        noisyReceiverRegistered = false;
    }

    private void applyPlaybackSpeed() {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
        }
    }

    private void applyAppVolume() {
        if (player != null) {
            player.setVolume(normalizeAppVolume(appVolume * replayGainMultiplier(currentTrack())));
        }
    }

    private boolean seekExistingMirroredQueue(boolean playWhenReady, long startPositionMs) {
        if (!playerMirrorsQueue || player == null || player.getMediaItemCount() != queue.size()) {
            return false;
        }
        if (!mirroredQueueMatchesCurrentPlayer()) {
            return false;
        }
        int targetIndex = safeCurrentIndex();
        Track track = currentTrack();
        if (track == null) {
            return false;
        }
        try {
            preparing = false;
            errorMessage = "";
            lastMarkedTrack = null;
            resetWaveformIfTrackChanged(track);
            streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
            applyPlaybackSpeed();
            applyAppVolume();
            player.seekTo(targetIndex, Math.max(0L, startPositionMs));
            player.setPlayWhenReady(playWhenReady);
            if (playWhenReady) {
                player.play();
                savePlaybackResumeRequested(true);
                acquireWifiLockIfStreaming();
            }
            if (startPositionMs > 0L) {
                clearRestoredPosition();
            }
            publishState();
            startProgressUpdates();
            return true;
        } catch (IllegalStateException error) {
            Log.w(TAG, "Unable to reuse mirrored queue", error);
            playerMirrorsQueue = false;
            return false;
        }
    }

    private boolean mirroredQueueMatchesCurrentPlayer() {
        if (player == null || player.getMediaItemCount() != queue.size()) {
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

    private float replayGainMultiplier(Track track) {
        if (!replayGainEnabled || track == null) {
            return 1.0f;
        }
        float gainDb = Math.abs(track.replayGainTrackDb) > 0.001f
                ? track.replayGainTrackDb
                : track.replayGainAlbumDb;
        if (Math.abs(gainDb) <= 0.001f) {
            return 1.0f;
        }
        return (float) Math.pow(10.0, gainDb / 20.0);
    }

    private void applyAudioFocusHandling() {
        if (player != null) {
            // When concurrent playback is enabled we do NOT request audio focus, so Yukine
            // mixes with other media apps instead of pausing them (and won't be auto-paused
            // by them). When disabled, ExoPlayer manages exclusive audio focus as usual.
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), !concurrentPlaybackEnabled);
        }
    }

    private float normalizePlaybackSpeed(float speed) {
        if (speed < 0.5f) {
            return 0.5f;
        }
        if (speed > 2.0f) {
            return 2.0f;
        }
        return Math.round(speed * 100.0f) / 100.0f;
    }

    private float normalizeAppVolume(float volume) {
        if (volume < 0.0f) {
            return 0.0f;
        }
        if (volume > 1.0f) {
            return 1.0f;
        }
        return Math.round(volume * 100.0f) / 100.0f;
    }

    private boolean setControllerMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        List<Track> tracks = tracksForMediaItems(mediaItems);
        if (tracks.isEmpty()) {
            return false;
        }
        playQueue(tracks, startIndex, startPositionMs);
        return true;
    }

    private List<Track> tracksForMediaItems(List<MediaItem> mediaItems) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Track> tracksById = tracksById(repository.loadCachedTracks());
        ArrayList<Track> tracks = new ArrayList<>();
        for (MediaItem mediaItem : mediaItems) {
            Track track = trackForMediaItem(mediaItem, tracksById);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private Track trackForMediaItem(MediaItem mediaItem, Map<Long, Track> tracksById) {
        if (mediaItem == null) {
            return null;
        }
        long trackId = trackIdFromAutoMediaId(mediaItem.mediaId);
        if (trackId < 0L) {
            trackId = parseLong(mediaItem.mediaId, -1L);
        }
        return trackId < 0L ? null : tracksById.get(trackId);
    }

    private Map<Long, Track> tracksById(List<Track> tracks) {
        HashMap<Long, Track> byId = new HashMap<>();
        if (tracks == null) {
            return byId;
        }
        for (Track track : tracks) {
            if (track != null) {
                byId.put(track.id, track);
            }
        }
        return byId;
    }

    private long trackIdFromAutoMediaId(String mediaId) {
        if (mediaId == null || !mediaId.startsWith(AUTO_TRACK_PREFIX)) {
            return -1L;
        }
        return parseLong(mediaId.substring(AUTO_TRACK_PREFIX.length()), -1L);
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private MediaItem mediaItemForTrack(Track track) {
        return new MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(String.valueOf(track.id))
                .setCustomCacheKey(cacheKeyForTrack(track))
                .setMediaMetadata(playbackNotificationManager == null
                        ? new MediaMetadata.Builder().build()
                        : playbackNotificationManager.mediaMetadataForTrack(track))
                .build();
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

    private void acquireWifiLockIfStreaming() {
        Track track = currentTrack();
        if (track != null && isHttpUri(track.contentUri) && wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
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
        return currentTrack() != null || !queue.isEmpty() || preparing || isPlaying();
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
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            return null;
        }
        return queue.get(currentIndex);
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

