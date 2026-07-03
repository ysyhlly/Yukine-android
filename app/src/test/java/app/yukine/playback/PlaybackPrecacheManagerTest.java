package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackMediaCacheOperations;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class PlaybackPrecacheManagerTest {
    @Test
    public void releaseCancelsDelayedPrecacheCallbacksOwnedByManager() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        stateProvider.currentTrack = track(1L, "https://example.test/one.mp3");

        manager.precacheTrack(stateProvider.currentTrack);
        manager.release();

        assertEquals(2, scheduler.removedCallbacks);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void releaseIsIdempotentAfterCallbacksAreCancelled() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        stateProvider.currentTrack = track(1L, "https://example.test/one.mp3");

        manager.precacheTrack(stateProvider.currentTrack);
        manager.release();
        manager.release();

        assertEquals(2, scheduler.removedCallbacks);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void releaseAudioCacheIsIdempotentAcrossExplicitAndManagerRelease() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeAudioCacheReleaseAction audioCacheReleaseAction = new FakeAudioCacheReleaseAction();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                PlaybackMediaCacheOperations.fromMediaSourceProvider(mediaSourceProvider()),
                scheduler,
                audioCacheReleaseAction::releaseAudioCache
        );

        manager.releaseAudioCache();
        manager.release();
        manager.releaseAudioCache();
        manager.release();

        assertEquals(1, audioCacheReleaseAction.releaseCalls);
    }

    @Test
    public void cacheRangeDataSpecUsesTrackUriCacheKeyAndFragmentationFlag() {
        Track track = track(42L, "https://example.test/audio.flac");

        DataSpec dataSpec = PlaybackPrecacheManager.cacheRangeDataSpec(
                track,
                "streaming:provider:42|url=https://example.test/audio.flac",
                -128L,
                4096L
        );

        assertEquals(track.contentUri, dataSpec.uri);
        assertEquals(0L, dataSpec.position);
        assertEquals(4096L, dataSpec.length);
        assertEquals("streaming:provider:42|url=https://example.test/audio.flac", dataSpec.key);
        assertEquals(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION, dataSpec.flags);
    }

    @Test
    public void cacheRangeDataSpecRejectsUncacheableInputs() {
        Track track = track(43L, "https://example.test/audio.flac");

        assertEquals(null, PlaybackPrecacheManager.cacheRangeDataSpec(null, "key", 0L, 1L));
        assertEquals(null, PlaybackPrecacheManager.cacheRangeDataSpec(track, "", 0L, 1L));
        assertEquals(null, PlaybackPrecacheManager.cacheRangeDataSpec(track, "key", 0L, 0L));
    }

    @Test
    public void replacingCurrentPrecacheCancelsPreviousDelayedCallbacks() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track first = track(1L, "https://example.test/one.mp3");
        Track second = track(2L, "https://example.test/two.mp3");

        stateProvider.currentTrack = first;
        manager.precacheTrack(first);
        stateProvider.currentTrack = second;
        manager.precacheTrack(second);
        manager.release();

        assertEquals(4, scheduler.removedCallbacks);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void releasePreventsLatePrecacheDiagnosticsAndCallbacks() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track track = track(1L, "https://example.test/one.mp3");

        manager.release();
        stateProvider.currentTrack = track;
        manager.precacheTrack(track);

        assertEquals(0, stateProvider.diagnostics.snapshot().precacheAttempts);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void releasePreventsAlreadyDequeuedDelayedCallbackFromReadingState() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track track = track(1L, "https://example.test/one.mp3");

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        Runnable delayedCallback = scheduler.takeNext();
        stateProvider.currentTrackCalls = 0;
        manager.release();
        delayedCallback.run();

        assertEquals(0, stateProvider.currentTrackCalls);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void releasePreventsAlreadySubmittedExecutorTaskFromReadingCacheState() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        CapturingPlaybackCacheExecutor executor = new CapturingPlaybackCacheExecutor();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                mediaCacheOperations,
                null,
                scheduler,
                new FakeAudioCacheReleaseAction()::releaseAudioCache,
                executor
        );
        Track track = track(1L, "https://example.test/one.mp3");

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        int cacheKeyCallsBeforeRelease = mediaCacheOperations.cacheKeyForPrecacheCalls;
        manager.release();
        executor.runSubmitted(0);

        assertEquals(cacheKeyCallsBeforeRelease, mediaCacheOperations.cacheKeyForPrecacheCalls);
        assertEquals(0, stateProvider.diagnostics.snapshot().precacheSuccesses);
    }

    @Test
    public void nonHttpTrackDoesNotQueuePrecacheWork() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track track = track(1L, "content://media/audio/1");

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);

        assertEquals(0, stateProvider.diagnostics.snapshot().precacheAttempts);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void httpTrackWithoutCacheKeyDoesNotQueuePrecacheWork() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track track = track(1L, "https://example.test/no-cache-key.mp3", "");

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);

        assertEquals(0, stateProvider.diagnostics.snapshot().precacheAttempts);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void providerBackedMediaCacheOperationsOwnCacheKeyAndHeaders() {
        Map<String, String> headers = Collections.singletonMap("Cookie", "token=abc");
        PlaybackMediaCacheOperations operations =
                PlaybackMediaCacheOperations.fromMediaSourceProvider(
                        mediaSourceProvider(new FakeStreamingPlaybackHeaderStore(headers))
                );
        Track streaming = track(42L, "https://example.test/audio.flac", "streaming:test:42");
        Track local = track(7L, "content://media/audio/7", "/music/local.flac");

        assertEquals(
                "streaming:test:42|url=https://example.test/audio.flac",
                operations.cacheKeyForPrecache(streaming)
        );
        assertEquals(headers, operations.headersForTrack(streaming));
        assertEquals(null, operations.cacheKeyForPrecache(local));
    }

    @Test
    public void nullMediaSourceProviderKeepsPrecacheFactoryAsSafeNoop() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = PlaybackPrecacheManager.fromMediaSourceProvider(
                stateProvider,
                null,
                scheduler
        );
        Track track = track(42L, "https://example.test/audio.flac", "streaming:test:42");

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        manager.releaseAudioCache();
        manager.release();

        assertEquals(0, stateProvider.diagnostics.snapshot().precacheAttempts);
        assertEquals(0, scheduler.pendingCallbacks.size());
        assertEquals(0, scheduler.removedCallbacks);
    }

    @Test
    public void matchingCurrentPlayerMediaItemLetsPlayerFillLeadingRange() throws Exception {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track track = track(1L, "https://example.test/one.mp3");

        stateProvider.currentTrack = track;
        stateProvider.currentPlayerMediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null);
        manager.precacheTrack(track);
        scheduler.runNext();
        assertEventuallyPrecacheComplete(stateProvider, 1, 0L);
        manager.release();
    }

    @Test
    public void fullyCachedCurrentLeadingRangeCompletesWithoutOpeningCacheDataSource() throws Exception {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        CapturingPlaybackCacheExecutor executor = new CapturingPlaybackCacheExecutor();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                mediaCacheOperations,
                (mediaItem, matchedTrack) -> mediaCacheOperations.mediaItemMatchesForReuse,
                scheduler,
                new FakeAudioCacheReleaseAction()::releaseAudioCache,
                executor
        );
        Track track = track(1L, "https://example.test/cached.mp3");
        mediaCacheOperations.mediaItemMatchesForReuse = false;
        mediaCacheOperations.cachedBytes = PlaybackPrecacheManager.PRECACHE_BYTES * 2L;

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        executor.runSubmitted(0);
        manager.release();

        assertEquals(PlaybackPrecacheManager.PRECACHE_BYTES, stateProvider.diagnostics.snapshot().recentEvents.get(0).bytes);
        assertEquals(1, stateProvider.diagnostics.snapshot().precacheSuccesses);
        assertEquals(0, mediaCacheOperations.cacheDataSourceForTrackCalls);
    }

    @Test
    public void cacheStateReadFailureFallsBackToManagerOwnedCacheAttempt() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        CapturingPlaybackCacheExecutor executor = new CapturingPlaybackCacheExecutor();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                mediaCacheOperations,
                (mediaItem, matchedTrack) -> mediaCacheOperations.mediaItemMatchesForReuse,
                scheduler,
                new FakeAudioCacheReleaseAction()::releaseAudioCache,
                executor
        );
        Track track = track(1L, "https://example.test/cache-state-fails.mp3");
        mediaCacheOperations.mediaItemMatchesForReuse = false;
        mediaCacheOperations.throwOnCachedBytesRead = true;

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        executor.runSubmitted(0);
        manager.release();

        assertEquals(1, mediaCacheOperations.cachedBytesInRangeCalls);
        assertEquals(1, mediaCacheOperations.cacheDataSourceForTrackCalls);
        assertEquals(track, mediaCacheOperations.lastCacheDataSourceTrack);
        assertEquals(1, stateProvider.diagnostics.snapshot().precacheFailures);
    }

    @Test
    public void failedCurrentLeadingRangeCleansActiveRangeForRetry() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        CapturingPlaybackCacheExecutor executor = new CapturingPlaybackCacheExecutor();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                mediaCacheOperations,
                (mediaItem, matchedTrack) -> mediaCacheOperations.mediaItemMatchesForReuse,
                scheduler,
                new FakeAudioCacheReleaseAction()::releaseAudioCache,
                executor
        );
        Track track = track(1L, "https://example.test/fails.mp3");
        mediaCacheOperations.mediaItemMatchesForReuse = false;

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        executor.runSubmitted(0);
        manager.precacheTrack(track);
        executor.runSubmitted(1);
        manager.release();

        assertEquals(2, mediaCacheOperations.cacheDataSourceForTrackCalls);
        assertEquals(track, mediaCacheOperations.lastCacheDataSourceTrack);
        assertEquals(2, stateProvider.diagnostics.snapshot().precacheFailures);
    }

    @Test
    public void resolvedUriMatchUsesCurrentTrackPrecachePath() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track current = track(1L, "https://example.test/shared.mp3");
        Track candidate = track(2L, "https://example.test/shared.mp3");

        stateProvider.currentTrack = current;
        manager.precacheTrack(candidate);
        manager.release();

        assertEquals(2, scheduler.removedCallbacks);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void repeatedCurrentPrecacheUsesManagerOwnedCacheStateToAvoidRescheduling() throws Exception {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                mediaCacheOperations,
                (mediaItem, matchedTrack) -> mediaCacheOperations.mediaItemMatchesForReuse,
                scheduler,
                new FakeAudioCacheReleaseAction()::releaseAudioCache
        );
        Track track = track(1L, "https://example.test/current.mp3");
        mediaCacheOperations.contentLength = 1024L;
        mediaCacheOperations.cachedBytes = 1024L;

        stateProvider.currentTrack = track;
        manager.precacheTrack(track);
        assertEventuallyPrecacheComplete(stateProvider, 1, 0L);
        int pendingCallbacks = scheduler.pendingCallbacks.size();
        int precacheAttempts = stateProvider.diagnostics.snapshot().precacheAttempts;
        int contentLengthCalls = mediaCacheOperations.contentLengthCalls;
        int cachedBytesInRangeCalls = mediaCacheOperations.cachedBytesInRangeCalls;
        manager.precacheTrack(track);
        manager.release();

        assertEquals(pendingCallbacks, scheduler.pendingCallbacks.size() + scheduler.removedCallbacks);
        assertEquals(precacheAttempts, stateProvider.diagnostics.snapshot().precacheAttempts);
        assertEquals(contentLengthCalls + 1, mediaCacheOperations.contentLengthCalls);
        assertEquals(cachedBytesInRangeCalls + 1, mediaCacheOperations.cachedBytesInRangeCalls);
    }

    @Test
    public void upcomingPrecacheReadsTracksThroughNarrowProvider() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, scheduler);
        Track current = track(1L, "https://example.test/current.mp3");
        stateProvider.upcomingTracks.add(track(2L, "https://example.test/upcoming.mp3"));

        stateProvider.currentTrack = current;
        manager.precacheTrack(current);
        scheduler.runNext();
        scheduler.runNext();
        manager.release();

        assertEquals(PlaybackPrecacheManager.SEGMENTED_PRECACHE_CONCURRENCY, stateProvider.lastUpcomingMaxCount);
        assertEquals(1, stateProvider.upcomingTracksCalls);
    }

    @Test
    public void upcomingPrecacheSkipsTracksWithoutCachePolicyKey() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeMediaCacheOperations mediaCacheOperations = new FakeMediaCacheOperations();
        CapturingPlaybackCacheExecutor executor = new CapturingPlaybackCacheExecutor();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                mediaCacheOperations,
                (mediaItem, matchedTrack) -> mediaCacheOperations.mediaItemMatchesForReuse,
                scheduler,
                new FakeAudioCacheReleaseAction()::releaseAudioCache,
                executor
        );
        Track current = track(1L, "https://example.test/current.mp3");
        Track localUpcoming = track(2L, "content://media/audio/2", "/music/local.flac");
        Track streamingUpcoming = track(3L, "https://example.test/upcoming.mp3");

        stateProvider.currentTrack = current;
        stateProvider.upcomingTracks.add(localUpcoming);
        stateProvider.upcomingTracks.add(streamingUpcoming);
        manager.precacheTrack(current);
        scheduler.runNext();
        scheduler.runNext();
        executor.runSubmitted(2);

        assertEquals(1, stateProvider.upcomingTracksCalls);
        assertEquals(3, executor.submittedTaskCount());
        assertEquals(1, mediaCacheOperations.cacheDataSourceForTrackCalls);
        assertEquals(streamingUpcoming, mediaCacheOperations.lastCacheDataSourceTrack);
        manager.release();
    }

    private static Track track(long id, String uri) {
        return track(id, uri, "streaming:test:" + id);
    }

    private static Track track(long id, String uri, String dataPath) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse(uri),
                dataPath
        );
    }

    private static PlaybackPrecacheManager precacheManager(
            FakeStateProvider stateProvider,
            FakeCallbackScheduler scheduler
    ) {
        PlaybackMediaSourceProvider mediaSourceProvider = mediaSourceProvider();
        return PlaybackPrecacheManager.fromMediaSourceProvider(
                stateProvider,
                mediaSourceProvider,
                scheduler
        );
    }

    private static void assertEventuallyPrecacheComplete(
            FakeStateProvider stateProvider,
            int expectedSuccesses,
            long expectedBytes
    ) throws Exception {
        PlaybackStreamingDiagnostics.Snapshot snapshot = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            snapshot = stateProvider.diagnostics.snapshot();
            if (snapshot.precacheSuccesses == expectedSuccesses) {
                assertEquals(expectedBytes, snapshot.recentEvents.get(0).bytes);
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expectedSuccesses, snapshot == null ? 0 : snapshot.precacheSuccesses);
    }

    private static PlaybackMediaSourceProvider mediaSourceProvider() {
        return mediaSourceProvider(new FakeStreamingPlaybackHeaderStore());
    }

    private static PlaybackMediaSourceProvider mediaSourceProvider(
            StreamingPlaybackHeaderStore headerStore
    ) {
        Context context = RuntimeEnvironment.getApplication();
        return new PlaybackMediaSourceProvider(
                context,
                new MusicLibraryRepository(context, new FakeStreamingDataPathParser()),
                headerStore
        );
    }

    private static final class FakeStateProvider implements PlaybackPrecacheManager.StateProvider {
        private final PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        private final List<Track> upcomingTracks = new ArrayList<>();
        private Track currentTrack;
        private MediaItem currentPlayerMediaItem;
        private int currentTrackCalls;
        private int upcomingTracksCalls;
        private int lastUpcomingMaxCount;

        @Override
        public Track currentTrack() {
            currentTrackCalls++;
            return currentTrack;
        }

        @Override
        public MediaItem currentPlayerMediaItem() {
            if (currentPlayerMediaItem != null) {
                return currentPlayerMediaItem;
            }
            return currentTrack == null
                    ? null
                    : PlaybackMediaSourceProvider.playbackMediaItemForTrack(currentTrack, null);
        }

        @Override
        public List<Track> upcomingTracksForPrecache(int maxCount) {
            upcomingTracksCalls++;
            lastUpcomingMaxCount = maxCount;
            return upcomingTracks;
        }

        @Override
        public PlaybackStreamingDiagnostics streamingDiagnostics() {
            return diagnostics;
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
        private final Map<String, String> headers;

        private FakeStreamingPlaybackHeaderStore() {
            this(Collections.emptyMap());
        }

        private FakeStreamingPlaybackHeaderStore(Map<String, String> headers) {
            this.headers = headers;
        }

        @Override
        public void register(String dataPath, java.util.Map<String, String> headers) {
        }

        @Override
        public java.util.Map<String, String> forDataPath(String dataPath) {
            return headers;
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

    private static final class FakeCallbackScheduler implements PlaybackPrecacheManager.CallbackScheduler {
        private final List<Runnable> pendingCallbacks = new ArrayList<>();
        private int removedCallbacks;

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            pendingCallbacks.add(runnable);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            if (pendingCallbacks.remove(runnable)) {
                removedCallbacks++;
            }
        }

        private void runNext() {
            Runnable runnable = takeNext();
            runnable.run();
        }

        private Runnable takeNext() {
            return pendingCallbacks.remove(0);
        }
    }

    private static final class FakeAudioCacheReleaseAction {
        private int releaseCalls;

        public void releaseAudioCache() {
            releaseCalls++;
        }
    }

    private static final class FakeMediaCacheOperations implements PlaybackMediaCacheOperations {
        private long contentLength = -1L;
        private long cachedBytes;
        private boolean mediaItemMatchesForReuse = true;
        private boolean throwOnCachedBytesRead;
        private int cacheKeyForPrecacheCalls;
        private int contentLengthCalls;
        private int cachedBytesInRangeCalls;
        private int cacheDataSourceForTrackCalls;
        private Track lastCacheDataSourceTrack;

        @Override
        public boolean tracksShareResolvedUriForReuse(Track current, Track candidate) {
            return current != null
                    && candidate != null
                    && current.contentUri != null
                    && current.contentUri.equals(candidate.contentUri);
        }

        @Override
        public long contentLengthForCacheKey(String cacheKey) {
            contentLengthCalls++;
            return contentLength;
        }

        @Override
        public String cacheKeyForPrecache(Track track) {
            cacheKeyForPrecacheCalls++;
            String scheme = track == null || track.contentUri == null ? null : track.contentUri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            if (track == null || track.contentUri == null || track.dataPath == null || track.dataPath.isEmpty()) {
                return null;
            }
            return track.dataPath + "|url=" + track.contentUri;
        }

        @Override
        public Map<String, String> headersForTrack(Track track) {
            return Collections.emptyMap();
        }

        @Override
        public long cachedBytesInRange(String cacheKey, long position, long length) {
            cachedBytesInRangeCalls++;
            if (throwOnCachedBytesRead) {
                throw new IllegalStateException("cache state unavailable");
            }
            return cachedBytes;
        }

        @Override
        public CacheDataSource cacheDataSourceForTrack(Track track) {
            cacheDataSourceForTrackCalls++;
            lastCacheDataSourceTrack = track;
            return null;
        }

    }

    private static final class CapturingPlaybackCacheExecutor extends ThreadPoolExecutor {
        private final List<Runnable> submittedTasks = new ArrayList<>();
        private boolean shutdown;

        CapturingPlaybackCacheExecutor() {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        public void execute(Runnable command) {
            submittedTasks.add(command);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            getQueue().clear();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        private void runSubmitted(int index) {
            submittedTasks.get(index).run();
        }

        private int submittedTaskCount() {
            return submittedTasks.size();
        }
    }

}
