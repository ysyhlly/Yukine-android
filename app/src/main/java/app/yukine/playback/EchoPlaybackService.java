package app.yukine.playback;

import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
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
import app.yukine.FloatingLyricsPublisher;
import app.yukine.FloatingLyricsState;
import app.yukine.LiveLyricsNotificationService;
import app.yukine.R;
import app.yukine.data.EmbeddedArtwork;
import app.yukine.data.MusicLibraryRepository;
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
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
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
    private static final String EXTRA_CURRENT_LYRIC = "app.yukine.extra.CURRENT_LYRIC";
    private static final String EXTRA_LYRIC_TRACK_TITLE = "app.yukine.extra.LYRIC_TRACK_TITLE";
    private static final String EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing";
    private static final int NOTIFICATION_ACCENT = 0xFFEBC9A6;
    private static final long PLAYBACK_POSITION_SAVE_INTERVAL_MS = 5000L;
    private static final long CROSSFADE_FADE_OUT_MS = 700L;
    private static final long CROSSFADE_FADE_STEP_MS = 70L;
    private static final int PRECACHE_BYTES = 512 * 1024;
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
    private static final float WAVEFORM_PROGRESS_STEP = 0.015f;
    private static final int WAVEFORM_BAR_COUNT = 96;
    private static final long SPECTRUM_QUICK_START_MS = 6_000L;
    private static final float SPECTRUM_PROGRESS_STEP = 0.008f;
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
    private final LruCache<String, Bitmap> notificationArtworkCache =
            new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final LruCache<String, byte[]> mediaMetadataArtworkCache =
            new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final Set<String> notificationArtworkMisses = Collections.synchronizedSet(new HashSet<>());

    private ExoPlayer player;
    private Player sessionPlayer;
    private MediaLibrarySession mediaSession;
    private boolean playerMirrorsQueue;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private LoudnessEnhancer loudnessEnhancer;
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
    private String lastPrecacheKey = "";
    private String waveformTrackKey = "";
    private String waveformGeneratingKey = "";
    private float waveformGeneratedProgress;
    private int waveformGeneratedBarCount;
    private PlaybackWaveformSnapshot waveformSnapshot = PlaybackWaveformSnapshot.empty();
    private String spectrumGeneratingKey = "";
    private float spectrumGeneratedProgress;
    private PlaybackSpectrumSnapshot spectrumSnapshot = PlaybackSpectrumSnapshot.empty();
    private String lastNotificationLyric = "";
    private long lastNotificationUpdateAtMs;
    private long lastLyricNotificationUpdateAtMs;
    private String errorMessage = "";
    private Track lastMarkedTrack;
    private boolean noisyReceiverRegistered;
    private boolean fadeOutAdvancing;
    private volatile boolean appVisible;

    private final FloatingLyricsPublisher.Listener floatingLyricsListener = state -> {
        String nextLyric = state == null ? "" : sanitizeNotificationLyric(state.getActiveLine());
        if (nextLyric.equals(lastNotificationLyric)) {
            return;
        }
        lastNotificationLyric = nextLyric;
        if (hasNotificationWorthyState()) {
            mainHandler.post(() -> {
                long now = System.currentTimeMillis();
                if (!appVisible && now - lastLyricNotificationUpdateAtMs < BACKGROUND_LYRIC_NOTIFICATION_MIN_INTERVAL_MS) {
                    return;
                }
                lastLyricNotificationUpdateAtMs = now;
                refreshMediaSessionMetadata();
                updateMediaNotification(false);
                updateLiveLyricsNotificationService(nextLyric);
            });
        }
    };

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
        createNotificationChannel();
        createPlayerIfNeeded();
        restorePlaybackQueue();
        if (hasNotificationWorthyState()) {
            updateMediaNotification(true);
        }
        FloatingLyricsPublisher.addListener(floatingLyricsListener);
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
                return track == null ? super.getMediaMetadata() : mediaMetadataForTrack(track);
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
        return mediaSession;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent == null ? "" : intent.getAction();
        boolean notificationRequested = isPlaybackServiceAction(action);
        if (notificationRequested) {
            updateMediaNotification(true);
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
            updateMediaNotification(true);
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
            updateMediaNotification(true);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        persistPlaybackPositionThrottled(true);
        FloatingLyricsPublisher.removeListener(floatingLyricsListener);
        LiveLyricsNotificationService.stop(this);
        mainHandler.removeCallbacksAndMessages(null);
        unregisterNoisyReceiver();
        playbackTaskScheduler.shutdownNow();
        visualizationTaskScheduler.shutdownNow();
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
            updateMediaNotification(true);
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

    private void skipToNextImmediately() {
        persistPlaybackPositionThrottled(true);
        if (shuffleEnabled && queue.size() > 1) {
            int nextIndex = currentIndex;
            while (nextIndex == currentIndex) {
                nextIndex = random.nextInt(queue.size());
            }
            currentIndex = nextIndex;
        } else if (currentIndex >= queue.size() - 1) {
            if (repeatMode == REPEAT_OFF) {
                persistPlaybackQueue();
                publishState();
                return;
            }
            currentIndex = 0;
        } else {
            currentIndex += 1;
        }
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
        if (track == null || !isHttpUri(track.contentUri)) {
            return;
        }
        final Track visualTrack = track;
        visualizationTaskScheduler.schedule(
                PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE,
                () -> cacheVisualizationWindow(visualTrack)
        );
    }

    public void precacheTrack(Track track) {
        if (track == null || !isHttpUri(track.contentUri)) {
            return;
        }
        String cacheKey = cacheKeyForTrack(track);
        String precacheKey = cacheKey == null || cacheKey.isEmpty()
                ? track.contentUri.toString()
                : cacheKey;
        if (precacheKey.equals(lastPrecacheKey)) {
            return;
        }
        lastPrecacheKey = precacheKey;
        final Track precacheTrack = track;
        streamingDiagnostics.recordPrecacheQueued(precacheTrack);
        playbackTaskScheduler.schedule(
                PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE,
                () -> {
                    precacheWithMediaCache(precacheTrack);
                    cacheVisualizationWindow(precacheTrack);
                }
        );
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
            updateMediaNotification(true);
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
            updateMediaNotification(true);
        }
    }

    public PlaybackStateSnapshot snapshot() {
        Track track = currentTrack();
        long duration = track == null ? 0L : Math.max(track.durationMs, durationMs());
        PlaybackWaveformSnapshot waveform = waveformSnapshotFor(track, duration);
        PlaybackSpectrumSnapshot spectrum = spectrumSnapshotFor(track, duration);
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
        return isPlaying() ? realtimeBassDetector.bands() : new float[0];
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
        if (statusBarLyricsEnabled == enabled) {
            return;
        }
        statusBarLyricsEnabled = enabled;
        lastNotificationLyric = "";
        refreshMediaSessionMetadata();
        updateMediaNotification(true);
        updateLiveLyricsNotificationService(notificationLyricText(currentTrack()));
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
        bindAudioEffects();
        publishState();
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
        streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        applyPlaybackSpeed();
        applyAppVolume();
        player.clearMediaItems();
        player.setMediaSources(mediaSources, safeCurrentIndex(), Math.max(0L, startPositionMs));
        playerMirrorsQueue = true;
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            scheduleVisualizationCache(track);
            if (startPositionMs > 0L) {
                clearRestoredPosition();
            }
            publishState();
            updateMediaNotification(true);
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
            scheduleVisualizationCache(track);
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs);
                clearRestoredPosition();
            }
            publishState();
            updateMediaNotification(true);
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
            releaseMediaSession();
            releaseAudioEffects();
            releaseAudioCache();
            return;
        }
        releaseMediaSession();
        releaseAudioEffects();
        try {
            player.removeListener(playerListener);
            player.stop();
        } catch (IllegalStateException ignored) {
            // Player is already unusable.
        }
        player.release();
        player = null;
        sessionPlayer = null;
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

    private void releaseMediaSession() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        sessionPlayer = null;
    }

    private void stopAndClear() {
        cancelSleepTimerInternal(false);
        clearRestoredPosition();
        if (repository != null) {
            repository.savePlaybackPosition(-1L, 0L);
        }
        lastSavedPositionTrackId = -1L;
        lastSavedPositionMs = 0L;
        releasePlayer();
        preparing = false;
        errorMessage = "";
        lastMarkedTrack = null;
        fadeOutAdvancing = false;
        queue.clear();
        currentIndex = -1;
        persistPlaybackQueue();
        savePlaybackResumeRequested(false);
        mainHandler.removeCallbacks(progressRunnable);
        releaseWifiLock();
        LiveLyricsNotificationService.stop(this);
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
        if (completed != null && repository != null) {
            repository.savePlaybackPosition(completed.id, 0L);
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
        syncFloatingLyricsPlaybackState(snapshot);
        updateMediaSessionState(snapshot);
        updateMediaNotification(false);
        updatePlaybackWidget(snapshot);
        for (PlaybackStateListener listener : listeners) {
            listener.onPlaybackStateChanged(snapshot);
        }
    }

    private void syncFloatingLyricsPlaybackState(PlaybackStateSnapshot snapshot) {
        if (snapshot == null || snapshot.currentTrack == null) {
            return;
        }
        FloatingLyricsState state = FloatingLyricsPublisher.snapshot();
        Track track = snapshot.currentTrack;
        String activeLine = state != null && floatingLyricsTrackMatches(state, track)
                ? state.getActiveLine()
                : "";
        FloatingLyricsPublisher.update(
                track.title,
                track.artist,
                track.albumArtUriString(),
                snapshot.playing || snapshot.preparing,
                activeLine
        );
    }

    private boolean floatingLyricsTrackMatches(FloatingLyricsState state, Track track) {
        if (state == null || track == null) {
            return false;
        }
        return track.title.equals(state.getTrackTitle())
                && track.artist.equals(state.getArtist());
    }

    private void updatePlaybackWidget(PlaybackStateSnapshot snapshot) {
        Track track = snapshot == null ? null : snapshot.currentTrack;
        Bitmap artwork = track == null ? null : notificationArtworkFor(track);
        EchoPlaybackWidgetProvider.update(this, snapshot, artwork);
    }

    private void publishBufferingState() {
        PlaybackStateSnapshot snapshot = snapshot();
        streamingDiagnostics.recordBuffering(snapshot.currentTrack, snapshot.positionMs);
        for (PlaybackStateListener listener : listeners) {
            listener.onPlaybackBuffering(snapshot);
        }
    }

    private void updateMediaSessionState(PlaybackStateSnapshot snapshot) {
        applyPlaybackModeToPlayer();
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
        if (completed != null && repository != null) {
            repository.savePlaybackPosition(completed.id, 0L);
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
        bindAudioEffects();
        bindMediaSessionPlayer();
    }

    private void restorePlaybackQueue() {
        if (!playbackRestoreEnabled) {
            return;
        }
        PlaybackQueueState savedQueue = repository.loadPlaybackQueue();
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
        restoredPositionTrackId = repository.loadPlaybackPositionTrackId();
        restoredPositionMs = repository.loadPlaybackPositionMs();
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
        if (repository != null) {
            repository.savePlaybackResumeRequested(requested);
        }
    }

    private void replaceCurrentQueueTrack(Track track) {
        if (track == null || currentIndex < 0 || currentIndex >= queue.size()) {
            return;
        }
        queue.set(currentIndex, track);
        persistPlaybackQueue();
    }

    private void persistPlaybackQueue() {
        if (repository != null) {
            repository.savePlaybackQueue(new ArrayList<>(queue), currentIndex);
        }
    }

    private void persistPlaybackPositionThrottled(boolean force) {
        if (repository == null) {
            return;
        }
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
        repository.savePlaybackPosition(track.id, clampPlaybackPosition(track, position));
        lastSavedPositionTrackId = track.id;
        lastSavedPositionMs = position;
        lastPositionSaveAtMs = now;
    }

    private void resetCurrentPlaybackPosition() {
        if (repository == null) {
            return;
        }
        Track track = currentTrack();
        if (track == null) {
            return;
        }
        repository.savePlaybackPosition(track.id, 0L);
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

    private void bindAudioEffects() {
        if (player == null) {
            return;
        }
        releaseAudioEffects();
        if (audioEffectSettings == null || !audioEffectSettings.enabled) {
            return;
        }
        int sessionId;
        try {
            sessionId = player.getAudioSessionId();
        } catch (IllegalStateException error) {
            Log.w(TAG, "Unable to read audio session for effects", error);
            return;
        }
        if (sessionId == 0) {
            return;
        }
        try {
            equalizer = new Equalizer(0, sessionId);
            applyEqualizerSettings();
        } catch (RuntimeException error) {
            Log.w(TAG, "Equalizer unavailable", error);
            equalizer = null;
        }
        try {
            bassBoost = new BassBoost(0, sessionId);
            bassBoost.setStrength(audioEffectSettings.bassBoostStrength);
            bassBoost.setEnabled(audioEffectSettings.bassBoostStrength > 0);
        } catch (RuntimeException error) {
            Log.w(TAG, "BassBoost unavailable", error);
            bassBoost = null;
        }
        try {
            virtualizer = new Virtualizer(0, sessionId);
            virtualizer.setStrength(audioEffectSettings.virtualizerStrength);
            virtualizer.setEnabled(audioEffectSettings.virtualizerStrength > 0);
        } catch (RuntimeException error) {
            Log.w(TAG, "Virtualizer unavailable", error);
            virtualizer = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                loudnessEnhancer = new LoudnessEnhancer(sessionId);
                loudnessEnhancer.setTargetGain(audioEffectSettings.loudnessGainMb);
                loudnessEnhancer.setEnabled(audioEffectSettings.loudnessGainMb != 0);
            } catch (RuntimeException error) {
                Log.w(TAG, "LoudnessEnhancer unavailable", error);
                loudnessEnhancer = null;
            }
        }
    }

    private void applyEqualizerSettings() {
        if (equalizer == null || audioEffectSettings == null) {
            return;
        }
        short presetCount = equalizer.getNumberOfPresets();
        if (audioEffectSettings.preset >= 0 && audioEffectSettings.preset < presetCount) {
            equalizer.usePreset((short) audioEffectSettings.preset);
        } else {
            short bands = equalizer.getNumberOfBands();
            short[] range = equalizer.getBandLevelRange();
            short min = range != null && range.length > 0 ? range[0] : -1500;
            short max = range != null && range.length > 1 ? range[1] : 1500;
            for (short band = 0; band < bands && band < audioEffectSettings.bandLevels.length; band++) {
                short level = audioEffectSettings.bandLevels[band];
                if (level < min) {
                    level = min;
                } else if (level > max) {
                    level = max;
                }
                equalizer.setBandLevel(band, level);
            }
        }
        equalizer.setEnabled(audioEffectSettings.enabled);
    }

    private void releaseAudioEffects() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (RuntimeException ignored) {
            }
            equalizer = null;
        }
        if (bassBoost != null) {
            try {
                bassBoost.release();
            } catch (RuntimeException ignored) {
            }
            bassBoost = null;
        }
        if (virtualizer != null) {
            try {
                virtualizer.release();
            } catch (RuntimeException ignored) {
            }
            virtualizer = null;
        }
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.release();
            } catch (RuntimeException ignored) {
            }
            loudnessEnhancer = null;
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

    @OptIn(markerClass = UnstableApi.class)
    private void bindMediaSessionPlayer() {
        sessionPlayer = createSessionPlayer();
        if (mediaSession == null) {
            mediaSession = new MediaLibrarySession.Builder(this, sessionPlayer, new EchoMediaLibraryCallback())
                    .setId("echo_next_playback")
                    .setSessionActivity(activityPendingIntent())
                    .build();
        } else {
            mediaSession.setPlayer(sessionPlayer);
        }
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

    private final class EchoMediaLibraryCallback implements MediaLibrarySession.Callback {
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                LibraryParams params
        ) {
            return Futures.immediateFuture(LibraryResult.ofItem(
                    browsableItem(AUTO_ROOT, getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                    params
            ));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String mediaId
        ) {
            MediaItem item = itemForAutoMediaId(mediaId);
            if (item == null) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
            }
            return Futures.immediateFuture(LibraryResult.ofItem(item, null));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String parentId,
                int page,
                int pageSize,
                LibraryParams params
        ) {
            List<MediaItem> children = childrenForAutoParent(parentId);
            if (children == null) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE, params));
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(pagedItems(children, page, pageSize), params));
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession session,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems
        ) {
            ArrayList<MediaItem> resolved = new ArrayList<>();
            for (Track track : tracksForMediaItems(mediaItems)) {
                resolved.add(mediaItemForTrack(track));
            }
            return Futures.immediateFuture(resolved);
        }

        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                MediaSession session,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems,
                int startIndex,
                long startPositionMs
        ) {
            ArrayList<MediaItem> resolved = new ArrayList<>();
            for (Track track : tracksForMediaItems(mediaItems)) {
                resolved.add(mediaItemForTrack(track));
            }
            if (resolved.isEmpty()) {
                resolved.addAll(mediaItems);
            }
            return Futures.immediateFuture(new MediaSession.MediaItemsWithStartPosition(
                    resolved,
                    Math.max(0, Math.min(startIndex, Math.max(resolved.size() - 1, 0))),
                    startPositionMs
            ));
        }
    }

    private MediaItem itemForAutoMediaId(String mediaId) {
        if (AUTO_ROOT.equals(mediaId)) {
            return browsableItem(AUTO_ROOT, getString(R.string.app_name), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
        }
        if (AUTO_ALL.equals(mediaId)) {
            return browsableItem(AUTO_ALL, "All songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
        }
        if (AUTO_RECENT.equals(mediaId)) {
            return browsableItem(AUTO_RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED);
        }
        if (AUTO_PLAYLISTS.equals(mediaId)) {
            return browsableItem(AUTO_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS);
        }
        if (AUTO_ARTISTS.equals(mediaId)) {
            return browsableItem(AUTO_ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS);
        }
        if (AUTO_ALBUMS.equals(mediaId)) {
            return browsableItem(AUTO_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS);
        }
        if (mediaId != null && mediaId.startsWith(AUTO_PLAYLIST_PREFIX)) {
            long playlistId = parseLong(mediaId.substring(AUTO_PLAYLIST_PREFIX.length()), -1L);
            for (Playlist playlist : repository.loadPlaylists()) {
                if (playlist.id == playlistId) {
                    return browsableItem(mediaId, playlist.name, MediaMetadata.MEDIA_TYPE_PLAYLIST);
                }
            }
            return null;
        }
        if (mediaId != null && mediaId.startsWith(AUTO_ARTIST_PREFIX)) {
            String artist = mediaId.substring(AUTO_ARTIST_PREFIX.length());
            return browsableItem(mediaId, artist, MediaMetadata.MEDIA_TYPE_ARTIST);
        }
        if (mediaId != null && mediaId.startsWith(AUTO_ALBUM_PREFIX)) {
            String album = mediaId.substring(AUTO_ALBUM_PREFIX.length());
            return browsableItem(mediaId, album, MediaMetadata.MEDIA_TYPE_ALBUM);
        }
        long trackId = trackIdFromAutoMediaId(mediaId);
        if (trackId >= 0L) {
            Track track = tracksById(repository.loadCachedTracks()).get(trackId);
            return track == null ? null : autoMediaItemForTrack(track);
        }
        return null;
    }

    private List<MediaItem> childrenForAutoParent(String parentId) {
        if (AUTO_ROOT.equals(parentId)) {
            ArrayList<MediaItem> roots = new ArrayList<>();
            roots.add(browsableItem(AUTO_ALL, "All songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED));
            roots.add(browsableItem(AUTO_RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED));
            roots.add(browsableItem(AUTO_PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS));
            roots.add(browsableItem(AUTO_ARTISTS, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS));
            roots.add(browsableItem(AUTO_ALBUMS, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS));
            return roots;
        }
        if (AUTO_ALL.equals(parentId)) {
            return autoItemsForTracks(repository.loadCachedTracks());
        }
        if (AUTO_RECENT.equals(parentId)) {
            ArrayList<Track> tracks = new ArrayList<>();
            for (TrackPlayRecord record : repository.loadRecentlyPlayed(100)) {
                if (record != null && record.track != null) {
                    tracks.add(record.track);
                }
            }
            return autoItemsForTracks(tracks);
        }
        if (AUTO_PLAYLISTS.equals(parentId)) {
            ArrayList<MediaItem> playlists = new ArrayList<>();
            for (Playlist playlist : repository.loadPlaylists()) {
                playlists.add(browsableItem(
                        AUTO_PLAYLIST_PREFIX + playlist.id,
                        playlist.name,
                        MediaMetadata.MEDIA_TYPE_PLAYLIST
                ));
            }
            return playlists;
        }
        if (parentId != null && parentId.startsWith(AUTO_PLAYLIST_PREFIX)) {
            long playlistId = parseLong(parentId.substring(AUTO_PLAYLIST_PREFIX.length()), -1L);
            return playlistId < 0L ? null : autoItemsForTracks(repository.loadPlaylistTracks(playlistId));
        }
        if (AUTO_ARTISTS.equals(parentId)) {
            return groupedAutoItems(AUTO_ARTIST_PREFIX, MediaMetadata.MEDIA_TYPE_ARTIST, true);
        }
        if (parentId != null && parentId.startsWith(AUTO_ARTIST_PREFIX)) {
            String artist = parentId.substring(AUTO_ARTIST_PREFIX.length());
            return autoItemsForTracks(filterTracksByArtist(repository.loadCachedTracks(), artist));
        }
        if (AUTO_ALBUMS.equals(parentId)) {
            return groupedAutoItems(AUTO_ALBUM_PREFIX, MediaMetadata.MEDIA_TYPE_ALBUM, false);
        }
        if (parentId != null && parentId.startsWith(AUTO_ALBUM_PREFIX)) {
            String album = parentId.substring(AUTO_ALBUM_PREFIX.length());
            return autoItemsForTracks(filterTracksByAlbum(repository.loadCachedTracks(), album));
        }
        return null;
    }

    private List<MediaItem> groupedAutoItems(String prefix, int mediaType, boolean groupByArtist) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Track track : repository.loadCachedTracks()) {
            String key = groupByArtist ? track.artist : track.album;
            counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
        }
        ArrayList<MediaItem> items = new ArrayList<>();
        for (String key : counts.keySet()) {
            items.add(browsableItem(prefix + key, key, mediaType));
        }
        return items;
    }

    private List<Track> filterTracksByArtist(List<Track> tracks, String artist) {
        ArrayList<Track> matches = new ArrayList<>();
        for (Track track : tracks) {
            if (track != null && track.artist.equals(artist)) {
                matches.add(track);
            }
        }
        return matches;
    }

    private List<Track> filterTracksByAlbum(List<Track> tracks, String album) {
        ArrayList<Track> matches = new ArrayList<>();
        for (Track track : tracks) {
            if (track != null && track.album.equals(album)) {
                matches.add(track);
            }
        }
        return matches;
    }

    private List<MediaItem> autoItemsForTracks(List<Track> tracks) {
        ArrayList<MediaItem> items = new ArrayList<>();
        if (tracks == null) {
            return items;
        }
        for (Track track : tracks) {
            if (track != null && !Uri.EMPTY.equals(track.contentUri)) {
                items.add(autoMediaItemForTrack(track));
            }
        }
        return items;
    }

    private MediaItem autoMediaItemForTrack(Track track) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .populate(mediaMetadataForTrack(track))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC);
        return mediaItemForTrack(track)
                .buildUpon()
                .setMediaId(AUTO_TRACK_PREFIX + track.id)
                .setMediaMetadata(metadata.build())
                .build();
    }

    private MediaItem browsableItem(String mediaId, String title, int mediaType) {
        return new MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(mediaType)
                        .build())
                .build();
    }

    private List<MediaItem> pagedItems(List<MediaItem> items, int page, int pageSize) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        if (page < 0 || pageSize <= 0) {
            return items;
        }
        int fromIndex = page * pageSize;
        if (fromIndex >= items.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(items.size(), fromIndex + pageSize);
        return items.subList(fromIndex, toIndex);
    }

    private MediaItem mediaItemForTrack(Track track) {
        return new MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(String.valueOf(track.id))
                .setCustomCacheKey(cacheKeyForTrack(track))
                .setMediaMetadata(mediaMetadataForTrack(track))
                .build();
    }

    private MediaMetadata mediaMetadataForTrack(Track track) {
        String lyricText = notificationLyricText(track);
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setDurationMs(track.durationMs > 0L ? track.durationMs : null)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC);
        if (!lyricText.isEmpty()) {
            Bundle extras = new Bundle();
            extras.putString(EXTRA_CURRENT_LYRIC, lyricText);
            extras.putString(EXTRA_LYRIC_TRACK_TITLE, track.title);
            metadata.setSubtitle(lyricText)
                    .setDescription(lyricText)
                    .setExtras(extras);
        }
        if (track.albumArtUri != null) {
            metadata.setArtworkUri(track.albumArtUri);
            byte[] artworkData = mediaMetadataArtworkCache.get(notificationArtworkKey(track));
            if (artworkData != null && artworkData.length > 0) {
                metadata.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
            }
        }
        return metadata.build();
    }

    private void refreshMediaSessionMetadata() {
        if (player == null || mediaSession == null) {
            return;
        }
        sessionPlayer = createSessionPlayer();
        mediaSession.setPlayer(sessionPlayer);
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

    private PlaybackWaveformSnapshot waveformSnapshotFor(Track track, long durationMs) {
        if (track == null || durationMs <= 0L || !isHttpUri(track.contentUri)) {
            return PlaybackWaveformSnapshot.empty();
        }
        resetWaveformIfTrackChanged(track);
        if (!appVisible) {
            return waveformSnapshot;
        }
        float cachedProgress = visualizationCachedProgress(track, durationMs);
        PlaybackWaveformSnapshot current = waveformSnapshot;
        if (cachedProgress > current.cachedProgress && !current.hasBars()) {
            current = new PlaybackWaveformSnapshot(current.bars, current.generatedBars, cachedProgress);
            waveformSnapshot = current;
        }
        maybeGenerateStreamingWaveform(track, durationMs, cachedProgress);
        return current;
    }

    private void resetWaveformIfTrackChanged(Track track) {
        String key = waveformKey(track);
        if (key.equals(waveformTrackKey)) {
            return;
        }
        waveformTrackKey = key;
        waveformGeneratingKey = "";
        waveformGeneratedProgress = 0.0f;
        waveformGeneratedBarCount = 0;
        waveformSnapshot = PlaybackWaveformSnapshot.empty();
        spectrumGeneratingKey = "";
        spectrumGeneratedProgress = 0.0f;
        spectrumSnapshot = PlaybackSpectrumSnapshot.empty();
        realtimeBassDetector.reset();
    }

    private PlaybackSpectrumSnapshot spectrumSnapshotFor(Track track, long durationMs) {
        if (track == null || durationMs <= 0L || track.contentUri == null || Uri.EMPTY.equals(track.contentUri)) {
            return PlaybackSpectrumSnapshot.empty();
        }
        resetWaveformIfTrackChanged(track);
        if (!appVisible) {
            return spectrumSnapshot;
        }
        float cachedProgress = isHttpUri(track.contentUri) ? visualizationCachedProgress(track, durationMs) : 1.0f;
        PlaybackSpectrumSnapshot current = spectrumSnapshot;
        if (cachedProgress > current.cachedProgress && !current.hasBands()) {
            current = new PlaybackSpectrumSnapshot(current.bands, current.generatedFrames, current.bandCount, cachedProgress);
            spectrumSnapshot = current;
        }
        maybeGenerateSpectrum(track, durationMs, cachedProgress, true);
        return current;
    }

    private void maybeGenerateSpectrum(Track track, long durationMs, float cachedProgress, boolean allowQuickStart) {
        if (track == null || track.contentUri == null || Uri.EMPTY.equals(track.contentUri) || cachedProgress <= 0.005f) {
            return;
        }
        float targetProgress = cachedProgress;
        boolean quickStart = false;
        if (allowQuickStart && !spectrumSnapshot.hasBands()) {
            float quickProgress = durationMs <= 0L ? cachedProgress : Math.min(cachedProgress, SPECTRUM_QUICK_START_MS / (float) durationMs);
            targetProgress = Math.max(0.006f, Math.min(cachedProgress, quickProgress));
            quickStart = targetProgress + SPECTRUM_PROGRESS_STEP < cachedProgress;
        }
        int targetGeneratedFrames = Math.max(1, Math.min(
                PlaybackSpectrumGenerator.FRAME_COUNT,
                (int) Math.ceil(PlaybackSpectrumGenerator.FRAME_COUNT * Math.min(1.0f, targetProgress))
        ));
        if (spectrumSnapshot.hasBands()
                && targetGeneratedFrames <= spectrumSnapshot.generatedFrames
                && targetProgress - spectrumGeneratedProgress < SPECTRUM_PROGRESS_STEP) {
            return;
        }
        final String taskKey = waveformKey(track) + "|spectrum|" + targetGeneratedFrames;
        if (taskKey.equals(spectrumGeneratingKey)) {
            return;
        }
        spectrumGeneratingKey = taskKey;
        final Track spectrumTrack = track;
        final long spectrumDurationMs = durationMs;
        final float spectrumCachedProgress = targetProgress;
        final float requestedCachedProgress = cachedProgress;
        final boolean spectrumQuickStart = quickStart;
        final boolean spectrumIsHttp = isHttpUri(track.contentUri);
        final String spectrumCacheKey = spectrumIsHttp ? mediaCacheKeyForTrack(track) : "";
        final long spectrumCachedBytes = spectrumIsHttp ? continuousCachedBytes(spectrumCacheKey) : 0L;
        if (spectrumIsHttp && (spectrumCacheKey == null || spectrumCacheKey.isEmpty() || spectrumCachedBytes <= 0L)) {
            spectrumGeneratingKey = "";
            return;
        }
        visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM, () -> {
            PlaybackSpectrumSnapshot generated;
            if (spectrumIsHttp) {
                DataSpec dataSpec = new DataSpec.Builder()
                        .setUri(spectrumTrack.contentUri)
                        .setPosition(0L)
                        .setLength(spectrumCachedBytes)
                        .setKey(spectrumCacheKey)
                        .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                        .build();
                generated = PlaybackSpectrumGenerator.extract(
                        cacheDataSourceForTrack(spectrumTrack),
                        dataSpec,
                        spectrumDurationMs,
                        spectrumCachedProgress
                );
            } else {
                generated = PlaybackSpectrumGenerator.extract(
                        EchoPlaybackService.this,
                        spectrumTrack.contentUri,
                        spectrumDurationMs,
                        spectrumCachedProgress
                );
            }
            mainHandler.post(() -> {
                if (!waveformKey(spectrumTrack).equals(waveformTrackKey) || !taskKey.equals(spectrumGeneratingKey)) {
                    return;
                }
                spectrumGeneratingKey = "";
                spectrumGeneratedProgress = Math.max(spectrumGeneratedProgress, spectrumCachedProgress);
                if (generated != null && generated.hasBands()
                        && generated.generatedFrames >= spectrumSnapshot.generatedFrames) {
                    spectrumSnapshot = generated;
                } else if (spectrumCachedProgress > spectrumSnapshot.cachedProgress) {
                    spectrumSnapshot = new PlaybackSpectrumSnapshot(
                            spectrumSnapshot.bands,
                            spectrumSnapshot.generatedFrames,
                            spectrumSnapshot.bandCount,
                            spectrumCachedProgress
                    );
                }
                publishState();
                if (spectrumQuickStart && requestedCachedProgress > spectrumCachedProgress + SPECTRUM_PROGRESS_STEP) {
                    maybeGenerateSpectrum(spectrumTrack, spectrumDurationMs, requestedCachedProgress, false);
                }
            });
        });
    }

    private void maybeGenerateStreamingWaveform(Track track, long durationMs, float cachedProgress) {
        String cacheKey = mediaCacheKeyForTrack(track);
        if (cacheKey == null || cacheKey.isEmpty() || cachedProgress <= 0.005f) {
            return;
        }
        int targetGeneratedBars = Math.max(1, Math.min(
                WAVEFORM_BAR_COUNT,
                (int) Math.ceil(WAVEFORM_BAR_COUNT * Math.min(1.0f, cachedProgress))
        ));
        int currentGeneratedBars = Math.max(waveformGeneratedBarCount, waveformSnapshot.generatedBars);
        if (targetGeneratedBars <= currentGeneratedBars && waveformSnapshot.hasBars()) {
            return;
        }
        if (cachedProgress - waveformGeneratedProgress < WAVEFORM_PROGRESS_STEP
                && targetGeneratedBars <= currentGeneratedBars + 1
                && waveformSnapshot.hasBars()) {
            return;
        }
        long cachedBytes = continuousCachedBytes(cacheKey);
        if (cachedBytes <= 0L) {
            return;
        }
        final String taskKey = waveformKey(track) + "|" + targetGeneratedBars;
        if (taskKey.equals(waveformGeneratingKey)) {
            return;
        }
        waveformGeneratingKey = taskKey;
        final Track waveformTrack = track;
        final long waveformDurationMs = durationMs;
        final float waveformCachedProgress = cachedProgress;
        final long waveformCachedBytes = cachedBytes;
        visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM, () -> {
            DataSpec dataSpec = new DataSpec.Builder()
                    .setUri(waveformTrack.contentUri)
                    .setPosition(0L)
                    .setLength(waveformCachedBytes)
                    .setKey(cacheKey)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build();
            PlaybackWaveformSnapshot generated = StreamingWaveformGenerator.extract(
                    EchoPlaybackService.this,
                    cacheDataSourceForTrack(waveformTrack),
                    dataSpec,
                    waveformDurationMs,
                    waveformCachedProgress,
                    cacheKey
            );
            mainHandler.post(() -> {
                if (!waveformKey(waveformTrack).equals(waveformTrackKey) || !taskKey.equals(waveformGeneratingKey)) {
                    return;
                }
                waveformGeneratingKey = "";
                waveformGeneratedProgress = waveformCachedProgress;
                waveformGeneratedBarCount = Math.max(waveformGeneratedBarCount, targetGeneratedBars);
                waveformSnapshot = PlaybackWaveformMergePolicy.merge(
                        waveformSnapshot,
                        generated,
                        waveformCachedProgress
                );
                publishState();
            });
        });
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

    @OptIn(markerClass = UnstableApi.class)
    private void precacheWithMediaCache(Track track) {
        if (track == null || !isHttpUri(track.contentUri)) {
            return;
        }
        try {
            final long[] bytesCached = {0L};
            DataSpec dataSpec = new DataSpec.Builder()
                    .setUri(track.contentUri)
                    .setPosition(0L)
                    .setLength(PRECACHE_BYTES)
                    .setKey(cacheKeyForTrack(track))
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build();
            CacheWriter writer = new CacheWriter(
                    cacheDataSourceForTrack(track),
                    dataSpec,
                    new byte[16 * 1024],
                    (requestLength, bytesCachedTotal, newBytesCached) -> bytesCached[0] = bytesCachedTotal
            );
            writer.cache();
            streamingDiagnostics.recordPrecacheComplete(track, bytesCached[0]);
        } catch (Exception error) {
            streamingDiagnostics.recordPrecacheFailed(track, error);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cacheVisualizationWindow(Track track) {
        if (track == null || !isHttpUri(track.contentUri)) {
            return;
        }
        String cacheKey = cacheKeyForTrack(track);
        if (cacheKey == null || cacheKey.isEmpty()) {
            return;
        }
        long cached = continuousCachedBytes(cacheKey);
        if (cached >= VISUALIZATION_CACHE_BYTES) {
            return;
        }
        try {
            DataSpec dataSpec = new DataSpec.Builder()
                    .setUri(track.contentUri)
                    .setPosition(cached)
                    .setLength(VISUALIZATION_CACHE_BYTES - cached)
                    .setKey(cacheKey)
                    .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                    .build();
            CacheWriter writer = new CacheWriter(
                    cacheDataSourceForTrack(track),
                    dataSpec,
                    new byte[16 * 1024],
                    null
            );
            writer.cache();
        } catch (Exception ignored) {
            // Visualization cache is best-effort. Playback must never wait for it.
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

    private void updateMediaNotification(boolean force) {
        Track track = currentTrack();
        if (track == null && !hasNotificationWorthyState()) {
            return;
        }
        long now = System.currentTimeMillis();
        long minInterval = appVisible
                ? FOREGROUND_NOTIFICATION_MIN_INTERVAL_MS
                : BACKGROUND_NOTIFICATION_MIN_INTERVAL_MS;
        if (!force && now - lastNotificationUpdateAtMs < minInterval) {
            return;
        }
        lastNotificationUpdateAtMs = now;
        try {
            startPlaybackForeground(playbackNotification(track));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to update playback notification", error);
        }
    }

    private boolean hasNotificationWorthyState() {
        return currentTrack() != null || !queue.isEmpty() || preparing || isPlaying();
    }

    private Notification playbackNotification(Track track) {
        boolean playing = isPlaying() || preparing;
        boolean hasTrack = track != null;
        boolean isFavorite = hasTrack && repository != null && repository.isFavorite(track.id);
        String lyricText = notificationLyricText(track);
        String contentText = !lyricText.isEmpty()
                ? lyricText
                : hasTrack ? track.subtitle() : EMPTY_NOTIFICATION_TEXT;
        String titleText = hasTrack ? track.title : EMPTY_NOTIFICATION_TITLE;
        String capsuleText = !lyricText.isEmpty()
                ? shortCriticalText(lyricText)
                : hasTrack ? shortCriticalText(track.title) : "Yukine";
        String favoriteTitle = isFavorite ? "Favorited" : "Favorite";
        int favoriteIcon = isFavorite ? R.drawable.ic_notif_favorite_filled : R.drawable.ic_notif_favorite_outline;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_echo)
                .setContentTitle(titleText)
                .setContentText(contentText)
                .setContentIntent(activityPendingIntent())
                .setTicker(contentText)
                .setSubText(hasTrack ? track.subtitle() : "Yukine")
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setColor(NOTIFICATION_ACCENT)
                .setShowWhen(false)
                .setOngoing(hasTrack || playing || preparing)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_notif_previous, "Previous", serviceActionPendingIntent(ACTION_PREVIOUS, 1))
                .addAction(
                        playing ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play,
                        playing ? "Pause" : "Play",
                        serviceActionPendingIntent(playing ? ACTION_PAUSE : ACTION_RESTORE_AND_PLAY, 2)
                )
                .addAction(R.drawable.ic_notif_next, "Next", serviceActionPendingIntent(ACTION_NEXT, 3))
                .addAction(favoriteIcon, favoriteTitle, serviceActionPendingIntent(ACTION_TOGGLE_FAVORITE, 4))
                .addAction(R.drawable.ic_notif_stop, "Stop", serviceActionPendingIntent(ACTION_STOP, 5));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        if (hasTrack) {
            Bundle extras = new Bundle();
            extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true);
            extras.putString("extra_title", titleText);
            extras.putString("extra_body", contentText);
            extras.putString("extra_short_text", capsuleText);
            extras.putBoolean("extra_show_close_action", false);
            extras.putBoolean("extra_show_notification_icon", true);
            extras.putString(Notification.EXTRA_TITLE, titleText);
            extras.putString(Notification.EXTRA_TEXT, contentText);
            extras.putString(Notification.EXTRA_BIG_TEXT, contentText);
            extras.putString(Notification.EXTRA_SUB_TEXT, track.subtitle());
            extras.putString(Notification.EXTRA_SUMMARY_TEXT, track.subtitle());
            extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, new CharSequence[]{contentText});
            extras.putString(EXTRA_CURRENT_LYRIC, lyricText.isEmpty() ? contentText : lyricText);
            extras.putString(EXTRA_LYRIC_TRACK_TITLE, track.title);
            builder.addExtras(extras);
        }
        Bitmap artwork = hasTrack ? notificationArtworkFor(track) : null;
        if (artwork != null) {
            builder.setLargeIcon(artwork);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Notification.MediaStyle style = new Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2);
            if (mediaSession != null) {
                style.setMediaSession(mediaSession.getPlatformToken());
            }
            builder.setStyle(style);
        } else if (!lyricText.isEmpty()) {
            builder.setStyle(new Notification.BigTextStyle().bigText(lyricText));
        }
        requestPromotedOngoing(builder, true);
        setShortCriticalText(builder, capsuleText);
        return builder.build();
    }

    private String shortCriticalText(String value) {
        String compact = sanitizeNotificationLyric(value).replace('\n', ' ');
        if (compact.isEmpty()) {
            return "Yukine";
        }
        if (compact.length() <= 9) {
            return "\u266A " + compact;
        }
        if (compact.length() <= 14) {
            return "\u266A " + compact.substring(0, 9);
        }
        return "\u266A " + compact.substring(0, 8) + "...";
    }

    private void requestPromotedOngoing(Notification.Builder builder, boolean requested) {
        try {
            Notification.Builder.class
                    .getMethod("setRequestPromotedOngoing", boolean.class)
                    .invoke(builder, requested);
        } catch (ReflectiveOperationException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private void setShortCriticalText(Notification.Builder builder, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            Notification.Builder.class
                    .getMethod("setShortCriticalText", String.class)
                    .invoke(builder, text);
        } catch (ReflectiveOperationException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private String notificationLyricText(Track track) {
        if (!statusBarLyricsEnabled) {
            return "";
        }
        if (track == null) {
            return "";
        }
        try {
            FloatingLyricsState state = FloatingLyricsPublisher.snapshot();
            if (state == null || !track.title.equals(state.getTrackTitle())) {
                return "";
            }
            String activeLine = state.getActiveLine();
            if (activeLine == null) {
                return "";
            }
            return sanitizeNotificationLyric(activeLine);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void updateLiveLyricsNotificationService(String lyricText) {
        if (!statusBarLyricsEnabled
                || currentTrack() == null
                || (!isPlaying() && !preparing)) {
            LiveLyricsNotificationService.stop(this);
            return;
        }
        try {
            LiveLyricsNotificationService.start(this);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to start live lyrics notification", error);
        }
    }

    private String sanitizeNotificationLyric(String value) {
        if (value == null) {
            return "";
        }
        String[] rawLines = value.replace('\r', '\n').split("\n");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            while (line.contains("  ")) {
                line = line.replace("  ", " ");
            }
            if (line.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
            count++;
            if (count >= 2) {
                break;
            }
        }
        String text = builder.toString();
        if (text.length() > 140) {
            return text.substring(0, 139) + "...";
        }
        return text;
    }

    private Bitmap notificationArtworkFor(Track track) {
        if (track == null || track.albumArtUri == null) {
            return null;
        }
        String key = notificationArtworkKey(track);
        Bitmap cached = notificationArtworkCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!notificationArtworkMisses.contains(key)) {
            notificationArtworkMisses.add(key);
            loadNotificationArtworkAsync(track, key);
        }
        return null;
    }

    private void loadNotificationArtworkAsync(Track track, String key) {
        visualizationTaskScheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, () -> {
            Bitmap bitmap = decodeNotificationArtwork(track.albumArtUri);
            if (bitmap == null) {
                return;
            }
            notificationArtworkCache.put(key, bitmap);
            byte[] artworkData = encodeMetadataArtwork(bitmap);
            if (artworkData != null) {
                mediaMetadataArtworkCache.put(key, artworkData);
            }
            mainHandler.post(() -> {
                Track current = currentTrack();
                if (current == null || !key.equals(notificationArtworkKey(current))) {
                    return;
                }
                refreshMediaSessionMetadata();
                updateMediaNotification(true);
            });
        });
    }

    private String notificationArtworkKey(Track track) {
        if (track == null || track.albumArtUri == null) {
            return "";
        }
        return track.id + "|" + track.albumArtUri;
    }

    private Bitmap decodeNotificationArtwork(Uri uri) {
        if (uri == null) {
            return null;
        }
        if (EmbeddedArtwork.isEmbeddedArtworkUri(uri)) {
            byte[] bytes = EmbeddedArtwork.read(this, uri);
            return decodeNotificationArtworkBytes(bytes);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = openNotificationArtworkStream(uri)) {
            if (input == null) {
                return null;
            }
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = artworkSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                NOTIFICATION_ARTWORK_TARGET_PX
        );
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = openNotificationArtworkStream(uri)) {
            if (input == null) {
                return null;
            }
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private Bitmap decodeNotificationArtworkBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = artworkSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                NOTIFICATION_ARTWORK_TARGET_PX
        );
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private byte[] encodeMetadataArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                return null;
            }
            return output.toByteArray();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private InputStream openNotificationArtworkStream(Uri uri) throws IOException {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return getContentResolver().openInputStream(uri);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android");
        connection.setRequestProperty("Referer", "https://music.163.com/");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            return null;
        }
        return connection.getInputStream();
    }

    private int artworkSampleSize(int width, int height, int targetPx) {
        int sample = 1;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        while (halfWidth / sample >= targetPx && halfHeight / sample >= targetPx) {
            sample *= 2;
        }
        return Math.max(1, sample);
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

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Yukine playback controls");
        notificationManager().createNotificationChannel(channel);
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

