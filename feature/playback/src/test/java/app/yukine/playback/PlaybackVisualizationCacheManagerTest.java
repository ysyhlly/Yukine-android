package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaCacheOperations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@UnstableApi
@RunWith(RobolectricTestRunner.class)
public final class PlaybackVisualizationCacheManagerTest {
    @Test
    public void releaseBeforeMainCallbackSkipsSchedulingCacheTask() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(1L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(track);
        manager.release();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void releaseBeforeScheduledTaskSkipsCacheWriterCreation() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(2L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();
        manager.release();
        stateProvider.scheduledTasks.get(0).run();

        assertEquals(1, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void releasePreventsFutureVisualizationCacheScheduling() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(8L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        PlaybackVisualizationCacheManager manager =
                manager(stateProvider, mediaCacheOperations, writerFactory);

        manager.release();
        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, mediaCacheOperations.cacheKeyForPrecacheCalls);
        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void releaseDuringCacheCancelsActiveWriter() throws Exception {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(3L);
        stateProvider.currentTrack = track;
        final PlaybackVisualizationCacheManager[] holder = new PlaybackVisualizationCacheManager[1];
        FakeCacheWriter writer = new FakeCacheWriter(() -> holder[0].release());
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory(writer);
        holder[0] = manager(stateProvider, writerFactory);

        holder[0].scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();
        stateProvider.scheduledTasks.get(0).run();

        assertEquals(1, writer.cacheCalls);
        assertEquals(1, writer.cancelCalls);
    }

    @Test
    public void releaseIsIdempotentAfterQueuedCacheInvalidation() throws Exception {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(9L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(track);
        int generationBeforeRelease = cacheGeneration(manager);
        manager.release();
        int generationAfterFirstRelease = cacheGeneration(manager);
        manager.release();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(generationBeforeRelease + 1, generationAfterFirstRelease);
        assertEquals(generationAfterFirstRelease, cacheGeneration(manager));
        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void currentTrackSchedulesAndCachesVisualizationWindow() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(4L);
        stateProvider.currentTrack = track;
        FakeCacheWriter writer = new FakeCacheWriter(null);
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory(writer);
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();
        stateProvider.scheduledTasks.get(0).run();

        assertEquals(1, stateProvider.scheduledTasks.size());
        assertEquals(1, writerFactory.createCalls);
        assertSame(track, writerFactory.lastTrack);
        assertEquals(0L, writerFactory.lastDataSpec.position);
        assertEquals(64L * 1024L * 1024L, writerFactory.lastDataSpec.length);
        assertEquals(1, writer.cacheCalls);
        assertEquals(0, writer.cancelCalls);
    }

    @Test
    public void partiallyCachedVisualizationWindowResumesFromCachedBytes() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(10L);
        stateProvider.currentTrack = track;
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        long cachedBytes = 128L * 1024L;
        mediaCacheOperations.cachedBytes = cachedBytes;
        FakeCacheWriter writer = new FakeCacheWriter(null);
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory(writer);
        PlaybackVisualizationCacheManager manager =
                manager(stateProvider, mediaCacheOperations, writerFactory);

        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();
        stateProvider.scheduledTasks.get(0).run();

        assertEquals(1, mediaCacheOperations.cachedBytesInRangeCalls);
        assertSame(track, writerFactory.lastTrack);
        assertEquals(cachedBytes, writerFactory.lastDataSpec.position);
        assertEquals(64L * 1024L * 1024L - cachedBytes, writerFactory.lastDataSpec.length);
        assertEquals(1, writer.cacheCalls);
    }

    @Test
    public void fullyCachedVisualizationWindowSkipsCacheWriterCreation() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(11L);
        stateProvider.currentTrack = track;
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        mediaCacheOperations.cachedBytes = 64L * 1024L * 1024L;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager =
                manager(stateProvider, mediaCacheOperations, writerFactory);

        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();
        stateProvider.scheduledTasks.get(0).run();

        assertEquals(1, stateProvider.scheduledTasks.size());
        assertEquals(1, mediaCacheOperations.cachedBytesInRangeCalls);
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void nonHttpTrackDoesNotScheduleVisualizationCache() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(7L, "content://media/audio/7");
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void sameResolvedUriWithDifferentTrackIdDoesNotScheduleVisualizationCache() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track visualTrack = track(5L, "https://example.com/shared.mp3");
        stateProvider.currentTrack = track(6L, "https://example.com/shared.mp3");
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(visualTrack);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    private static PlaybackVisualizationCacheManager manager(
            FakeStateProvider stateProvider,
            PlaybackVisualizationCacheManager.VisualizationCacheWriterFactory writerFactory
    ) {
        return manager(stateProvider, new FakeMediaCacheOperations(), writerFactory);
    }

    private static PlaybackVisualizationCacheManager manager(
            FakeStateProvider stateProvider,
            FakeMediaCacheOperations mediaCacheOperations,
            PlaybackVisualizationCacheManager.VisualizationCacheWriterFactory writerFactory
    ) {
        return new PlaybackVisualizationCacheManager(
                stateProvider,
                mediaCacheOperations,
                writerFactory
        );
    }

    private static int cacheGeneration(PlaybackVisualizationCacheManager manager) throws Exception {
        Field field = PlaybackVisualizationCacheManager.class.getDeclaredField("cacheGeneration");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(manager)).get();
    }

    private static Track track(long id) {
        return track(id, "https://example.com/audio-" + id + ".mp3");
    }

    private static Track track(long id, String uri) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180_000L,
                Uri.parse(uri),
                "streaming:test:" + id
        );
    }

    private static final class FakeStateProvider implements PlaybackVisualizationCacheManager.StateProvider {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final List<Runnable> scheduledTasks = new ArrayList<>();
        private Track currentTrack;

        @Override
        public Handler mainHandler() {
            return handler;
        }

        @Override
        public Track currentTrack() {
            return currentTrack;
        }

        @Override
        public void scheduleVisualizationCacheTask(Runnable task) {
            scheduledTasks.add(task);
        }
    }

    private static final class FakeCacheWriterFactory implements PlaybackVisualizationCacheManager.VisualizationCacheWriterFactory {
        private final FakeCacheWriter writer;
        private int createCalls;
        private Track lastTrack;
        private DataSpec lastDataSpec;

        private FakeCacheWriterFactory() {
            this(new FakeCacheWriter(null));
        }

        private FakeCacheWriterFactory(FakeCacheWriter writer) {
            this.writer = writer;
        }

        @Override
        public PlaybackVisualizationCacheManager.VisualizationCacheWriter create(Track track, DataSpec dataSpec) {
            createCalls++;
            lastTrack = track;
            lastDataSpec = dataSpec;
            return writer;
        }
    }

    private static final class FakeCacheWriter implements PlaybackVisualizationCacheManager.VisualizationCacheWriter {
        private final Runnable onCache;
        private int cacheCalls;
        private int cancelCalls;

        private FakeCacheWriter(Runnable onCache) {
            this.onCache = onCache;
        }

        @Override
        public void cache() throws Exception {
            cacheCalls++;
            if (onCache != null) {
                onCache.run();
            }
        }

        @Override
        public void cancel() {
            cancelCalls++;
        }
    }

    private static final class FakeMediaCacheOperations
            implements PlaybackMediaCacheOperations {
        private int cacheKeyForPrecacheCalls;
        private int cachedBytesInRangeCalls;
        private long cachedBytes;

        @Override
        public boolean tracksShareResolvedUriForReuse(Track current, Track candidate) {
            return current != null
                    && candidate != null
                    && current.contentUri != null
                    && current.contentUri.equals(candidate.contentUri);
        }

        @Override
        public String cacheKeyForPrecache(Track track) {
            cacheKeyForPrecacheCalls++;
            String scheme = track == null || track.contentUri == null ? null : track.contentUri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            if (track.dataPath == null || track.dataPath.isEmpty()) {
                return null;
            }
            return track.dataPath + "|url=" + track.contentUri;
        }

        @Override
        public long cachedBytesInRange(String cacheKey, long position, long length) {
            cachedBytesInRangeCalls++;
            return cachedBytes;
        }

        @Override
        public CacheDataSource cacheDataSourceForTrack(Track track) {
            return null;
        }

        @Override
        public long contentLengthForCacheKey(String cacheKey) {
            return -1L;
        }

        @Override
        public Map<String, String> headersForTrack(Track track) {
            return Collections.emptyMap();
        }

    }
}
