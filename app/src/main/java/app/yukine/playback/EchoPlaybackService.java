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
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import app.yukine.model.PlaybackQueueState;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
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
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import android.util.Base64;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public final class EchoPlaybackService extends MediaSessionService {
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
    private static final long PLAYBACK_POSITION_SAVE_INTERVAL_MS = 5000L;
    private static final int PRECACHE_BYTES = 512 * 1024;
    // Buffering policy tuned for music streaming. Keep a generous read-ahead window (so a brief
    // network dip doesn't stall mid-song) but start and recover quickly so the user feels little
    // latency. Start playback after ~2.5s of buffer instead of 5s, and resume after a stall once
    // ~5s is buffered instead of 15s — the previous values made every hiccup feel like a long hang.
    private static final int STREAMING_MIN_BUFFER_MS = 60000;
    private static final int STREAMING_MAX_BUFFER_MS = 600000;
    private static final int STREAMING_BUFFER_FOR_PLAYBACK_MS = 2500;
    private static final int STREAMING_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000;
    private static final int STREAMING_BACK_BUFFER_MS = 30000;
    private static final long AUDIO_CACHE_MAX_BYTES = 1024L * 1024L * 1024L;
    private static final float WAVEFORM_PROGRESS_STEP = 0.015f;
    private static final int WAVEFORM_BAR_COUNT = 96;
    private static final int NOTIFICATION_ARTWORK_TARGET_PX = 512;
    private static final int NOTIFICATION_ARTWORK_CACHE_ENTRIES = 8;
    private final LocalBinder binder = new LocalBinder();
    private final CopyOnWriteArrayList<Track> queue = new CopyOnWriteArrayList<>();
    private final Set<PlaybackStateListener> listeners = new CopyOnWriteArraySet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final PlaybackTaskScheduler playbackTaskScheduler = new PlaybackTaskScheduler("EchoPlaybackScheduler");
    private final PlaybackStreamingDiagnostics streamingDiagnostics = new PlaybackStreamingDiagnostics();
    private final LruCache<String, Bitmap> notificationArtworkCache =
            new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final LruCache<String, byte[]> mediaMetadataArtworkCache =
            new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final Set<String> notificationArtworkMisses = Collections.synchronizedSet(new HashSet<>());

    private ExoPlayer player;
    private Player sessionPlayer;
    private MediaSession mediaSession;
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
    private boolean concurrentPlaybackEnabled = false;
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
    private String errorMessage = "";
    private Track lastMarkedTrack;
    private boolean noisyReceiverRegistered;

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
            errorMessage = "无法播放这首歌曲。";
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
        createNotificationChannel();
        createPlayerIfNeeded();
        restorePlaybackQueue();
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
                if (isAppQueueNavigationCommand(command)) {
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
                                Player.COMMAND_SEEK_TO_PREVIOUS_WINDOW,
                                Player.COMMAND_SEEK_TO_NEXT,
                                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                                Player.COMMAND_SEEK_TO_NEXT_WINDOW
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
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && MediaSessionService.SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return binder;
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent == null ? "" : intent.getAction();
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
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        persistPlaybackPositionThrottled(true);
        mainHandler.removeCallbacksAndMessages(null);
        unregisterNoisyReceiver();
        playbackTaskScheduler.shutdownNow();
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

    public void playQueue(List<Track> tracks, int startIndex) {
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
            errorMessage = "播放尚未就绪。";
            publishState();
        }
    }

    public void skipToNext() {
        if (queue.isEmpty()) {
            return;
        }
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
        errorMessage = "";
        lastMarkedTrack = null;
        clearRestoredPosition();
        persistPlaybackQueue();
        resetCurrentPlaybackPosition();
        savePlaybackResumeRequested(true);
        prepareCurrent(true);
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
        queue.set(currentIndex, replacement);
        errorMessage = "";
        lastMarkedTrack = null;
        restoredPositionTrackId = replacement.id;
        restoredPositionMs = Math.max(0L, positionMs);
        restoredPositionExplicit = true;
        streamingDiagnostics.recordRecovery(replacement, positionMs, qualityFromDataPath(replacement.dataPath));
        persistPlaybackQueue();
        playbackTaskScheduler.schedule(
                PlaybackTaskScheduler.Priority.CURRENT_PLAYBACK_RECOVERY,
                () -> mainHandler.post(() -> prepareCurrent(true))
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
                () -> precacheWithMediaCache(precacheTrack)
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
            updateMediaNotification();
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
            updateMediaNotification();
        }
    }

    public PlaybackStateSnapshot snapshot() {
        Track track = currentTrack();
        long duration = track == null ? 0L : Math.max(track.durationMs, durationMs());
        PlaybackWaveformSnapshot waveform = waveformSnapshotFor(track, duration);
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
                waveform
        );
    }

    public void setShuffleEnabled(boolean enabled) {
        shuffleEnabled = enabled;
        repository.saveShuffleEnabled(shuffleEnabled);
        publishState();
    }

    public void setRepeatMode(int mode) {
        if (mode != REPEAT_ALL && mode != REPEAT_ONE && mode != REPEAT_OFF) {
            mode = REPEAT_ALL;
        }
        repeatMode = mode;
        repository.saveRepeatMode(repeatMode);
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
                    ? "流媒体歌曲尚未解析，请重新点击歌曲播放。"
                    : "无法打开这首歌曲。";
            Log.w(TAG, "Refusing to prepare empty uri for " + debugTrack(track));
            publishState();
            return;
        }
        final long startPositionMs = restoredPositionFor(track);
        preparing = true;
        createPlayerIfNeeded();
        lastMarkedTrack = null;
        resetWaveformIfTrackChanged(track);
        streamingPlaybackHeaderStore.restoreForDataPath(track.dataPath);
        player.stop();
        player.clearMediaItems();
        applyPlaybackSpeed();
        player.setMediaSource(mediaSourceFactory(track).createMediaSource(mediaItemForTrack(track)));
        player.setPlayWhenReady(playWhenReady);
        try {
            player.prepare();
            if (startPositionMs > 0L) {
                player.seekTo(startPositionMs);
                clearRestoredPosition();
            }
            publishState();
            updateMediaNotification();
        } catch (IllegalStateException error) {
            preparing = false;
            Log.w(TAG, "Unable to prepare player for " + debugTrack(track), error);
            errorMessage = "无法打开这首歌曲。";
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
        if (player == null) {
            releaseMediaSession();
            releaseAudioCache();
            return;
        }
        releaseMediaSession();
        try {
            player.removeListener(playerListener);
            player.stop();
        } catch (IllegalStateException ignored) {
            // Player is already unusable.
        }
        player.release();
        player = null;
        sessionPlayer = null;
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
        queue.clear();
        currentIndex = -1;
        persistPlaybackQueue();
        savePlaybackResumeRequested(false);
        mainHandler.removeCallbacks(progressRunnable);
        releaseWifiLock();
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
        updateMediaSessionState(snapshot);
        updateMediaNotification();
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

    private void updateMediaSessionState(PlaybackStateSnapshot snapshot) {
        if (player == null) {
            return;
        }
        player.setShuffleModeEnabled(snapshot.shuffleEnabled);
        player.setRepeatMode(media3RepeatModeForAppRepeatMode(snapshot.repeatMode));
    }

    static int media3RepeatModeForAppRepeatMode(int appRepeatMode) {
        // The player only holds the current track; app queue logic handles list repeat and shuffle.
        return appRepeatMode == REPEAT_ONE ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
    }

    static boolean isAppQueueNavigationCommand(int command) {
        return command == Player.COMMAND_SEEK_TO_PREVIOUS
                || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                || command == Player.COMMAND_SEEK_TO_PREVIOUS_WINDOW
                || command == Player.COMMAND_SEEK_TO_NEXT
                || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                || command == Player.COMMAND_SEEK_TO_NEXT_WINDOW;
    }

    private void createPlayerIfNeeded() {
        if (player != null) {
            return;
        }
        player = new ExoPlayer.Builder(this)
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
        bindMediaSessionPlayer();
    }

    private void restorePlaybackQueue() {
        PlaybackQueueState savedQueue = repository.loadPlaybackQueue();
        if (savedQueue.isEmpty()) {
            return;
        }
        queue.clear();
        for (Track track : savedQueue.tracks) {
            if (track == null) {
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
            player.setVolume(appVolume);
        }
    }

    private void applyAudioFocusHandling() {
        if (player != null) {
            // When concurrent playback is enabled we do NOT request audio focus, so ECHO
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

    @OptIn(markerClass = UnstableApi.class)
    private void bindMediaSessionPlayer() {
        sessionPlayer = createSessionPlayer();
        if (mediaSession == null) {
            mediaSession = new MediaSession.Builder(this, sessionPlayer)
                    .setId("echo_next_playback")
                    .setSessionActivity(activityPendingIntent())
                    .build();
        } else {
            mediaSession.setPlayer(sessionPlayer);
        }
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
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setDurationMs(track.durationMs > 0L ? track.durationMs : null)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC);
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
        float cachedProgress = bufferedProgress(durationMs);
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
        playbackTaskScheduler.schedule(PlaybackTaskScheduler.Priority.CURRENT_WAVEFORM, () -> {
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
            audioCache = new SimpleCache(
                    cacheDir,
                    new LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES)
            );
        }
        return audioCache;
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

    private void updateMediaNotification() {
        Track track = currentTrack();
        if (track == null) {
            return;
        }
        startPlaybackForeground(playbackNotification(track));
    }

    private Notification playbackNotification(Track track) {
        boolean playing = isPlaying() || preparing;
        boolean isFavorite = repository != null && repository.isFavorite(track.id);
        String favoriteTitle = isFavorite ? "已收藏" : "收藏";
        int favoriteIcon = isFavorite ? R.drawable.ic_notif_favorite_filled : R.drawable.ic_notif_favorite_outline;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_echo)
                .setContentTitle(track.title)
                .setContentText(track.subtitle())
                .setContentIntent(activityPendingIntent())
                .setShowWhen(false)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_notif_previous, "上一首", serviceActionPendingIntent(ACTION_PREVIOUS, 1))
                .addAction(
                        playing ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play,
                        playing ? "暂停" : "播放",
                        serviceActionPendingIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 2)
                )
                .addAction(R.drawable.ic_notif_next, "下一首", serviceActionPendingIntent(ACTION_NEXT, 3))
                .addAction(favoriteIcon, favoriteTitle, serviceActionPendingIntent(ACTION_TOGGLE_FAVORITE, 4));
        Bitmap artwork = notificationArtworkFor(track);
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
        }
        return builder.build();
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
        playbackTaskScheduler.schedule(PlaybackTaskScheduler.Priority.NEXT_TRACK_PRECACHE, () -> {
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
                updateMediaNotification();
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
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 ECHO-NEXT-Android");
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

    private void startPlaybackForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
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
                "播放",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Yukine 播放控制");
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
