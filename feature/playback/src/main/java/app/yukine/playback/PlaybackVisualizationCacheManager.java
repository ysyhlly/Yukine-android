package app.yukine.playback;

import android.os.Handler;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaCacheOperations;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class PlaybackVisualizationCacheManager {
    private static final long VISUALIZATION_CACHE_BYTES = 64L * 1024L * 1024L;

    interface StateProvider {
        Handler mainHandler();
        Track currentTrack();
        void scheduleVisualizationCacheTask(Runnable task);
    }

    interface VisualizationCacheWriter {
        void cache() throws Exception;
        void cancel();
    }

    interface VisualizationCacheWriterFactory {
        VisualizationCacheWriter create(Track track, DataSpec dataSpec);
    }

    interface MediaCacheOperations {
        String cacheKeyForPrecache(Track track);
        boolean tracksShareResolvedUriForReuse(Track current, Track candidate);
        long cachedBytesInRange(String cacheKey, long position, long length);
        CacheDataSource cacheDataSourceForTrack(Track track);
    }

    private final StateProvider stateProvider;
    private final MediaCacheOperations mediaCacheOperations;
    private final VisualizationCacheWriterFactory cacheWriterFactory;
    private final Set<VisualizationCacheWriter> activeCacheWriters = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger cacheGeneration = new AtomicInteger();
    private volatile boolean released;

    static PlaybackVisualizationCacheManager fromMediaSourceProvider(
            StateProvider stateProvider,
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        return new PlaybackVisualizationCacheManager(
                stateProvider,
                mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider)
        );
    }

    private static MediaCacheOperations mediaCacheOperationsFromMediaSourceProvider(
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider);
        return new MediaCacheOperations() {
            @Override
            public String cacheKeyForPrecache(Track track) {
                return operations.cacheKeyForPrecache(track);
            }

            @Override
            public boolean tracksShareResolvedUriForReuse(Track current, Track candidate) {
                return operations.tracksShareResolvedUriForReuse(current, candidate);
            }

            @Override
            public long cachedBytesInRange(String cacheKey, long position, long length) {
                return operations.cachedBytesInRange(cacheKey, position, length);
            }

            @Override
            public CacheDataSource cacheDataSourceForTrack(Track track) {
                return operations.cacheDataSourceForTrack(track);
            }
        };
    }

    PlaybackVisualizationCacheManager(
            StateProvider stateProvider,
            MediaCacheOperations mediaCacheOperations
    ) {
        this(stateProvider, mediaCacheOperations, null);
    }

    PlaybackVisualizationCacheManager(
            StateProvider stateProvider,
            MediaCacheOperations mediaCacheOperations,
            VisualizationCacheWriterFactory cacheWriterFactory
    ) {
        this.stateProvider = stateProvider;
        this.mediaCacheOperations = mediaCacheOperations;
        this.cacheWriterFactory = cacheWriterFactory == null ? this::createCacheWriter : cacheWriterFactory;
    }

    void release() {
        if (released) {
            return;
        }
        released = true;
        cacheGeneration.incrementAndGet();
        synchronized (activeCacheWriters) {
            for (VisualizationCacheWriter writer : activeCacheWriters) {
                if (writer != null) {
                    writer.cancel();
                }
            }
            activeCacheWriters.clear();
        }
    }

    void scheduleVisualizationCache(Track track) {
        if (released) {
            return;
        }
        String cacheKey = cacheKeyForVisualization(track);
        if (cacheKey == null) {
            return;
        }
        int generation = cacheGeneration.get();
        final Track visualTrack = track;
        stateProvider.mainHandler().post(() -> {
            if (!isCurrentCacheGeneration(generation)) {
                return;
            }
            Track active = stateProvider.currentTrack();
            if (!tracksShareMediaIdentityForReuse(active, visualTrack)) {
                return;
            }
            stateProvider.scheduleVisualizationCacheTask(() -> cacheVisualizationWindow(visualTrack, generation));
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cacheVisualizationWindow(Track track, int generation) {
        if (!isCurrentCacheGeneration(generation)) {
            return;
        }
        String cacheKey = cacheKeyForVisualization(track);
        if (cacheKey == null) {
            return;
        }
        long cached = mediaCacheOperations.cachedBytesInRange(cacheKey, 0L, Long.MAX_VALUE);
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
            VisualizationCacheWriter writer = cacheWriterFactory.create(track, dataSpec);
            if (writer == null || !isCurrentCacheGeneration(generation)) {
                return;
            }
            activeCacheWriters.add(writer);
            try {
                if (isCurrentCacheGeneration(generation)) {
                    writer.cache();
                }
            } finally {
                activeCacheWriters.remove(writer);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isCurrentCacheGeneration(int generation) {
        return !released && cacheGeneration.get() == generation;
    }

    private String cacheKeyForVisualization(Track track) {
        if (mediaCacheOperations == null) {
            return null;
        }
        String cacheKey = mediaCacheOperations.cacheKeyForPrecache(track);
        return cacheKey == null || cacheKey.isEmpty() ? null : cacheKey;
    }

    private boolean tracksShareMediaIdentityForReuse(Track current, Track candidate) {
        return current != null
                && candidate != null
                && current.id == candidate.id
                && mediaCacheOperations != null
                && mediaCacheOperations.tracksShareResolvedUriForReuse(current, candidate);
    }

    private VisualizationCacheWriter createCacheWriter(Track track, DataSpec dataSpec) {
        CacheWriter writer = new CacheWriter(
                mediaCacheOperations.cacheDataSourceForTrack(track),
                dataSpec,
                new byte[16 * 1024],
                null
        );
        return new VisualizationCacheWriter() {
            @Override
            public void cache() throws Exception {
                writer.cache();
            }

            @Override
            public void cancel() {
                writer.cancel();
            }
        };
    }
}
