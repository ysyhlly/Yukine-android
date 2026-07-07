package app.yukine.playback;

import android.os.Handler;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheWriter;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

@OptIn(markerClass = UnstableApi.class)
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

    private final StateProvider stateProvider;
    private final PlaybackMediaSourceProvider mediaSourceProvider;
    private final VisualizationCacheWriterFactory cacheWriterFactory;
    private final Set<VisualizationCacheWriter> activeCacheWriters = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger cacheGeneration = new AtomicInteger();
    private volatile boolean released;

    PlaybackVisualizationCacheManager(
            StateProvider stateProvider,
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        this(stateProvider, mediaSourceProvider, null);
    }

    PlaybackVisualizationCacheManager(
            StateProvider stateProvider,
            PlaybackMediaSourceProvider mediaSourceProvider,
            VisualizationCacheWriterFactory cacheWriterFactory
    ) {
        this.stateProvider = stateProvider;
        this.mediaSourceProvider = mediaSourceProvider;
        this.cacheWriterFactory = cacheWriterFactory == null ? this::createCacheWriter : cacheWriterFactory;
    }

    static Consumer<Track> scheduleVisualizationCacheActionFromSupplier(
            Supplier<PlaybackVisualizationCacheManager> supplier
    ) {
        return track -> {
            PlaybackVisualizationCacheManager manager = supplier == null ? null : supplier.get();
            if (manager != null) {
                manager.scheduleVisualizationCache(track);
            }
        };
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
        if (released || !mediaSourceProvider.isHttpTrack(track)) {
            return;
        }
        int generation = cacheGeneration.get();
        final Track visualTrack = track;
        stateProvider.mainHandler().post(() -> {
            if (!isCurrentCacheGeneration(generation)) {
                return;
            }
            Track active = stateProvider.currentTrack();
            if (!mediaSourceProvider.tracksShareMediaIdentityForReuse(active, visualTrack)) {
                return;
            }
            stateProvider.scheduleVisualizationCacheTask(() -> cacheVisualizationWindow(visualTrack, generation));
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cacheVisualizationWindow(Track track, int generation) {
        if (!isCurrentCacheGeneration(generation) || !mediaSourceProvider.isHttpTrack(track)) {
            return;
        }
        String cacheKey = mediaSourceProvider.cacheKeyForTrack(track);
        if (cacheKey == null || cacheKey.isEmpty()) {
            return;
        }
        long cached = mediaSourceProvider.continuousCachedBytes(cacheKey);
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

    private VisualizationCacheWriter createCacheWriter(Track track, DataSpec dataSpec) {
        CacheWriter writer = new CacheWriter(
                mediaSourceProvider.cacheDataSourceForTrack(track),
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
