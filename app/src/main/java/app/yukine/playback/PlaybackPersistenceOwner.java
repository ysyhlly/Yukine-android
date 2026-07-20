package app.yukine.playback;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueStore;

/**
 * Playback-scoped persistence boundary. Room work is serialized off the main thread while the
 * service consumes small in-memory snapshots synchronously.
 */
@Singleton
final class PlaybackPersistenceOwner {
    private static final String TAG = "PlaybackPersistence";
    private static final long SHUTDOWN_FLUSH_TIMEOUT_SECONDS = 3L;

    private final MusicLibraryRepository repository;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<Runnable> readyCallbacks = new ArrayList<>();
    private final ArrayList<Runnable> pendingCacheMutations = new ArrayList<>();

    private volatile boolean loading;
    private volatile boolean loaded;
    private volatile PlaybackQueueState queueState = new PlaybackQueueState(Collections.emptyList(), -1);
    private volatile boolean resumeRequested;
    private volatile boolean playbackRestoreEnabled = true;
    private volatile long positionTrackId = -1L;
    private volatile long positionMs;
    private volatile boolean shuffleEnabled;
    private volatile int repeatMode = PlaybackRepeatMode.REPEAT_ALL;
    private volatile AudioEffectSettings audioEffectSettings = AudioEffectSettings.DEFAULT;
    private volatile boolean replayGainEnabled;
    private volatile boolean bitPerfectEnabled;
    private volatile boolean usbExclusiveEnabled;
    private volatile boolean audioExclusiveEnabled = false;
    private volatile float playbackSpeed = 1.0f;
    private volatile float appVolume = 1.0f;
    private volatile boolean statusBarLyricsEnabled;
    private volatile boolean systemMediaLyricsTitleEnabled;
    private volatile Set<Long> favoriteIds = Collections.emptySet();

    @Inject
    PlaybackPersistenceOwner(MusicLibraryRepository repository) {
        this.repository = repository;
    }

    void initialize(Handler mainHandler, Runnable callback) {
        boolean startLoad = false;
        synchronized (this) {
            if (callback != null) {
                readyCallbacks.add(() -> post(mainHandler, callback));
            }
            if (!loading) {
                loading = true;
                loaded = false;
                startLoad = true;
            }
        }
        if (startLoad) {
            databaseExecutor.execute(this::loadSnapshot);
        }
    }

    Executor databaseExecutor() {
        return databaseExecutor;
    }

    PlaybackQueueStore queueStore() {
        return new PlaybackQueueStore() {
            @Override
            public PlaybackQueueState load() {
                return queueState;
            }

            @Override
            public void save(List<Track> tracks, int currentIndex) {
                List<Track> snapshot = tracks == null
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(new ArrayList<>(tracks));
                updateCache(() -> queueState = new PlaybackQueueState(snapshot, currentIndex));
                execute(() -> repository.savePlaybackQueue(snapshot, currentIndex));
            }

            @Override
            public boolean loadResumeRequested() {
                return resumeRequested;
            }

            @Override
            public void saveResumeRequested(boolean requested) {
                updateCache(() -> resumeRequested = requested);
                execute(() -> repository.savePlaybackResumeRequested(requested));
            }

            @Override
            public boolean loadPlaybackRestoreEnabled() {
                return playbackRestoreEnabled;
            }

            @Override
            public void savePlaybackRestoreEnabled(boolean enabled) {
                updateCache(() -> playbackRestoreEnabled = enabled);
                execute(() -> repository.savePlaybackRestoreEnabled(enabled));
            }

            @Override
            public long loadPlaybackPositionTrackId() {
                return positionTrackId;
            }

            @Override
            public long loadPlaybackPositionMs() {
                return positionMs;
            }

            @Override
            public void savePlaybackPosition(long trackId, long nextPositionMs) {
                updateCache(() -> {
                    positionTrackId = trackId;
                    positionMs = nextPositionMs;
                });
                execute(() -> repository.savePlaybackPosition(trackId, nextPositionMs));
            }
        };
    }

    PlaybackAudioEffectSettingsStore.AudioEffectSettingsPersistence audioEffectSettings() {
        return new PlaybackAudioEffectSettingsStore.AudioEffectSettingsPersistence() {
            @Override
            public AudioEffectSettings loadAudioEffectSettings() {
                return audioEffectSettings;
            }

            @Override
            public void saveAudioEffectSettings(AudioEffectSettings settings) {
                AudioEffectSettings normalized = settings == null ? AudioEffectSettings.DEFAULT : settings;
                updateCache(() -> audioEffectSettings = normalized);
                execute(() -> repository.saveAudioEffectSettings(normalized));
            }
        };
    }

    PlaybackModeSettingsStore.ModeSettings modeSettings() {
        return new PlaybackModeSettingsStore.ModeSettings() {
            @Override
            public boolean loadShuffleEnabled() {
                return shuffleEnabled;
            }

            @Override
            public int loadRepeatMode() {
                return repeatMode;
            }

            @Override
            public void saveShuffleEnabled(boolean enabled) {
                updateCache(() -> shuffleEnabled = enabled);
                execute(() -> repository.saveShuffleEnabled(enabled));
            }

            @Override
            public void saveRepeatMode(int mode) {
                updateCache(() -> repeatMode = mode);
                execute(() -> repository.saveRepeatMode(mode));
            }
        };
    }

    PlaybackRuntimeSettingsStore.RuntimeSettings runtimeSettings() {
        return new PlaybackRuntimeSettingsStore.RuntimeSettings() {
            @Override
            public boolean loadReplayGainEnabled() {
                return replayGainEnabled;
            }

            @Override
            public float loadPlaybackSpeed() {
                return playbackSpeed;
            }

            @Override
            public float loadAppVolume() {
                return appVolume;
            }

            @Override
            public boolean loadBitPerfectEnabled() {
                return bitPerfectEnabled;
            }
        };
    }

    PlaybackLyricsSettingsStore.LyricsSettings lyricsSettings() {
        return new PlaybackLyricsSettingsStore.LyricsSettings() {
            @Override
            public boolean loadStatusBarLyricsEnabled() {
                return statusBarLyricsEnabled;
            }

            @Override
            public boolean loadSystemMediaLyricsTitleEnabled() {
                return systemMediaLyricsTitleEnabled;
            }
        };
    }

    void markPlayed(long trackId) {
        execute(() -> repository.markPlayed(trackId));
    }

    void updatePlaybackSpeed(float speed) {
        updateCache(() -> playbackSpeed = speed);
    }

    void updateAppVolume(float volume) {
        updateCache(() -> appVolume = volume);
    }

    void updateReplayGainEnabled(boolean enabled) {
        updateCache(() -> replayGainEnabled = enabled);
    }

    void updateBitPerfectEnabled(boolean enabled) {
        updateCache(() -> bitPerfectEnabled = enabled);
        execute(() -> repository.saveBitPerfectEnabled(enabled));
    }

    void updateUsbExclusiveEnabled(boolean enabled) {
        updateCache(() -> usbExclusiveEnabled = enabled);
        execute(() -> repository.saveUsbExclusiveEnabled(enabled));
    }

    boolean usbExclusiveEnabled() {
        return usbExclusiveEnabled;
    }

    boolean audioExclusiveEnabled() {
        return audioExclusiveEnabled;
    }

    void updateAudioExclusiveEnabled(boolean enabled) {
        updateCache(() -> audioExclusiveEnabled = enabled);
        execute(() -> repository.saveAudioExclusiveEnabled(enabled));
    }

    void updateStatusBarLyricsEnabled(boolean enabled) {
        updateCache(() -> statusBarLyricsEnabled = enabled);
    }

    void updateSystemMediaLyricsTitleEnabled(boolean enabled) {
        updateCache(() -> systemMediaLyricsTitleEnabled = enabled);
    }

    boolean isFavorite(Track track) {
        return track != null && favoriteIds.contains(track.id);
    }

    boolean toggleFavorite(Track track) {
        if (track == null) {
            return false;
        }
        final boolean favorite;
        synchronized (this) {
            if (!loaded) {
                pendingCacheMutations.add(() -> {
                    boolean deferredFavorite = !favoriteIds.contains(track.id);
                    updateFavoriteCache(track.id, deferredFavorite);
                    execute(() -> repository.setFavorite(track, deferredFavorite));
                });
                return true;
            }
            favorite = !favoriteIds.contains(track.id);
            updateFavoriteCache(track.id, favorite);
        }
        execute(() -> repository.setFavorite(track, favorite));
        return true;
    }

    private void updateFavoriteCache(long trackId, boolean favorite) {
        HashSet<Long> updated = new HashSet<>(favoriteIds);
        if (favorite) updated.add(trackId); else updated.remove(trackId);
        favoriteIds = Collections.unmodifiableSet(updated);
    }

    private void loadSnapshot() {
        try {
            PlaybackQueueState loadedQueue = repository.loadPlaybackQueue();
            boolean loadedResume = repository.loadPlaybackResumeRequested();
            boolean loadedRestore = repository.loadPlaybackRestoreEnabled();
            long loadedPositionTrackId = repository.loadPlaybackPositionTrackId();
            long loadedPositionMs = repository.loadPlaybackPositionMs();
            boolean loadedShuffle = repository.loadShuffleEnabled();
            int loadedRepeat = repository.loadRepeatMode();
            AudioEffectSettings loadedAudioEffects = repository.loadAudioEffectSettings();
            boolean loadedReplayGain = repository.loadReplayGainEnabled();
            boolean loadedBitPerfect = repository.loadBitPerfectEnabled();
            boolean loadedUsbExclusive = repository.loadUsbExclusiveEnabled();
            boolean loadedAudioExclusive = repository.loadAudioExclusiveEnabled();
            float loadedSpeed = repository.loadPlaybackSpeed();
            float loadedVolume = repository.loadAppVolume();
            boolean loadedStatusLyrics = repository.loadStatusBarLyricsEnabled();
            boolean loadedSystemLyrics = repository.loadSystemMediaLyricsTitleEnabled();
            Set<Long> loadedFavorites = repository.loadFavoriteIds();
            repository.loadRemoteSources();
            synchronized (this) {
                queueState = loadedQueue == null
                        ? new PlaybackQueueState(Collections.emptyList(), -1)
                        : loadedQueue;
                resumeRequested = loadedResume;
                playbackRestoreEnabled = loadedRestore;
                positionTrackId = loadedPositionTrackId;
                positionMs = loadedPositionMs;
                shuffleEnabled = loadedShuffle;
                repeatMode = loadedRepeat;
                audioEffectSettings = loadedAudioEffects == null
                        ? AudioEffectSettings.DEFAULT
                        : loadedAudioEffects;
                replayGainEnabled = loadedReplayGain;
                bitPerfectEnabled = loadedBitPerfect;
                usbExclusiveEnabled = loadedUsbExclusive;
                audioExclusiveEnabled = loadedAudioExclusive;
                playbackSpeed = loadedSpeed;
                appVolume = loadedVolume;
                statusBarLyricsEnabled = loadedStatusLyrics;
                systemMediaLyricsTitleEnabled = loadedSystemLyrics;
                favoriteIds = Collections.unmodifiableSet(new HashSet<>(loadedFavorites));
                for (Runnable mutation : pendingCacheMutations) mutation.run();
                pendingCacheMutations.clear();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to load playback persistence; keeping safe defaults", error);
        } finally {
            ArrayList<Runnable> callbacks;
            synchronized (this) {
                for (Runnable mutation : pendingCacheMutations) mutation.run();
                pendingCacheMutations.clear();
                loaded = true;
                loading = false;
                callbacks = new ArrayList<>(readyCallbacks);
                readyCallbacks.clear();
            }
            for (Runnable callback : callbacks) callback.run();
        }
    }

    private void updateCache(Runnable mutation) {
        synchronized (this) {
            if (loaded) mutation.run(); else pendingCacheMutations.add(mutation);
        }
    }

    private void execute(Runnable operation) {
        try {
            databaseExecutor.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException error) {
                    Log.w(TAG, "Playback persistence operation failed", error);
                }
            });
        } catch (RejectedExecutionException error) {
            Log.w(TAG, "Playback persistence executor rejected an operation", error);
        }
    }

    void flushPendingWrites() {
        final Future<?> barrier;
        try {
            barrier = databaseExecutor.submit(() -> { });
        } catch (RejectedExecutionException error) {
            Log.w(TAG, "Unable to schedule playback persistence flush", error);
            return;
        }
        try {
            barrier.get(SHUTDOWN_FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while flushing playback persistence", error);
        } catch (ExecutionException | TimeoutException error) {
            Log.w(TAG, "Unable to flush playback persistence before shutdown", error);
        }
    }

    private static void post(Handler handler, Runnable callback) {
        if (callback == null) return;
        if (handler == null) callback.run(); else handler.post(callback);
    }
}
