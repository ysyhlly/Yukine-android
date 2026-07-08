package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

@UnstableApi
@RunWith(RobolectricTestRunner.class)
public final class PlaybackVisualizationCacheManagerTest {
    @Test
    public void releaseBeforeMainCallbackSkipsSchedulingCacheTask() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = localHttpTrack(1L);
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
        Track track = localHttpTrack(2L);
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
        Track track = localHttpTrack(8L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.release();
        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void releaseDuringCacheCancelsActiveWriter() throws Exception {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = localHttpTrack(3L);
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
        Track track = localHttpTrack(9L);
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
        Track track = localHttpTrack(4L);
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
        assertEquals(8L * 1024L * 1024L, writerFactory.lastDataSpec.length);
        assertEquals(1, writer.cacheCalls);
        assertEquals(0, writer.cancelCalls);
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
    public void streamingTrackDoesNotScheduleVisualizationCache() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = track(12L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);

        manager.scheduleVisualizationCache(track);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, stateProvider.scheduledTasks.size());
        assertEquals(0, writerFactory.createCalls);
    }

    @Test
    public void scheduleVisualizationCacheActionFromSupplierDelegatesThroughManager() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        Track track = localHttpTrack(10L);
        stateProvider.currentTrack = track;
        FakeCacheWriterFactory writerFactory = new FakeCacheWriterFactory();
        PlaybackVisualizationCacheManager manager = manager(stateProvider, writerFactory);
        Consumer<Track> action =
                PlaybackVisualizationCacheManager.scheduleVisualizationCacheActionFromSupplier(() -> manager);

        action.accept(track);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, stateProvider.scheduledTasks.size());
    }

    @Test
    public void scheduleVisualizationCacheActionFromSupplierIgnoresMissingManager() {
        Consumer<Track> action =
                PlaybackVisualizationCacheManager.scheduleVisualizationCacheActionFromSupplier(() -> null);

        action.accept(track(11L));
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
        return new PlaybackVisualizationCacheManager(
                stateProvider,
                mediaSourceProvider(),
                writerFactory
        );
    }

    private static int cacheGeneration(PlaybackVisualizationCacheManager manager) throws Exception {
        Field field = PlaybackVisualizationCacheManager.class.getDeclaredField("cacheGeneration");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(manager)).get();
    }

    private static PlaybackMediaSourceProvider mediaSourceProvider() {
        Context context = RuntimeEnvironment.getApplication();
        return new PlaybackMediaSourceProvider(
                context,
                new MusicLibraryRepository(context, new FakeStreamingDataPathParser()),
                new FakeStreamingPlaybackHeaderStore()
        );
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

    private static Track localHttpTrack(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180_000L,
                Uri.parse("https://example.com/audio-" + id + ".mp3"),
                "webdav:" + id
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

    private static final class FakeStreamingDataPathParser implements StreamingDataPathParser {
        @Override
        public boolean isStreamingTrack(String dataPath) {
            return dataPath != null && dataPath.startsWith("streaming:");
        }

        @Override
        public String providerName(String dataPath) {
            return "test";
        }

        @Override
        public String providerTrackId(String dataPath) {
            return dataPath == null ? "" : dataPath.substring(dataPath.lastIndexOf(':') + 1);
        }
    }

    private static final class FakeStreamingPlaybackHeaderStore implements StreamingPlaybackHeaderStore {
        @Override
        public void register(String dataPath, Map<String, String> headers) {
        }

        @Override
        public Map<String, String> forDataPath(String dataPath) {
            return Collections.emptyMap();
        }

        @Override
        public boolean restoreForDataPath(String dataPath) {
            return false;
        }

        @Override
        public Track restoredTrackFor(Track track) {
            return null;
        }
    }
}
