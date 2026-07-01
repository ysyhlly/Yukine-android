package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.cache.CacheDataSource;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
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
import java.util.function.IntFunction;

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
                (IntFunction<List<Track>>) null,
                PlaybackPrecacheManager.mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider()),
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
    public void audioCacheReleaseActionFromSupplierDelegatesThroughManagerOnce() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        FakeAudioCacheReleaseAction audioCacheReleaseAction = new FakeAudioCacheReleaseAction();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(
                stateProvider,
                (IntFunction<List<Track>>) null,
                PlaybackPrecacheManager.mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider()),
                scheduler,
                audioCacheReleaseAction::releaseAudioCache
        );
        Runnable releaseAction =
                PlaybackPrecacheManager.audioCacheReleaseActionFromPrecacheManagerSupplier(() -> manager);

        releaseAction.run();
        releaseAction.run();

        assertEquals(1, audioCacheReleaseAction.releaseCalls);
    }

    @Test
    public void audioCacheReleaseActionFromSupplierIgnoresMissingManager() {
        Runnable releaseAction =
                PlaybackPrecacheManager.audioCacheReleaseActionFromPrecacheManagerSupplier(() -> null);

        releaseAction.run();
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
                (IntFunction<List<Track>>) null,
                mediaCacheOperations,
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
        FakeUpcomingTracksProvider upcomingTracksProvider = new FakeUpcomingTracksProvider();
        PlaybackPrecacheManager manager = precacheManager(stateProvider, upcomingTracksProvider, scheduler);
        Track current = track(1L, "https://example.test/current.mp3");
        upcomingTracksProvider.tracks.add(track(2L, "https://example.test/upcoming.mp3"));

        stateProvider.currentTrack = current;
        manager.precacheTrack(current);
        scheduler.runNext();
        scheduler.runNext();
        manager.release();

        assertEquals(PlaybackPrecacheManager.SEGMENTED_PRECACHE_CONCURRENCY, upcomingTracksProvider.lastMaxCount);
        assertEquals(1, upcomingTracksProvider.calls);
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
        return precacheManager(stateProvider, null, scheduler);
    }

    private static PlaybackPrecacheManager precacheManager(
            FakeStateProvider stateProvider,
            IntFunction<List<Track>> upcomingTracksProvider,
            FakeCallbackScheduler scheduler
    ) {
        PlaybackMediaSourceProvider mediaSourceProvider = mediaSourceProvider();
        return new PlaybackPrecacheManager(
                stateProvider,
                upcomingTracksProvider,
                PlaybackPrecacheManager.mediaCacheOperationsFromMediaSourceProvider(mediaSourceProvider),
                scheduler,
                PlaybackPrecacheManager.audioCacheReleaseActionFromMediaSourceProvider(mediaSourceProvider)
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
        Context context = RuntimeEnvironment.getApplication();
        return new PlaybackMediaSourceProvider(
                context,
                new MusicLibraryRepository(context, new FakeStreamingDataPathParser()),
                new FakeStreamingPlaybackHeaderStore()
        );
    }

    private static final class FakeStateProvider implements PlaybackPrecacheManager.StateProvider {
        private final PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        private Track currentTrack;
        private MediaItem currentPlayerMediaItem;
        private int currentTrackCalls;

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
        @Override
        public void register(String dataPath, java.util.Map<String, String> headers) {
        }

        @Override
        public java.util.Map<String, String> forDataPath(String dataPath) {
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

    private static final class FakeMediaCacheOperations implements PlaybackPrecacheManager.MediaCacheOperations {
        private long contentLength = -1L;
        private long cachedBytes;
        private int contentLengthCalls;
        private int cachedBytesInRangeCalls;

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
            return cachedBytes;
        }

        @Override
        public CacheDataSource cacheDataSourceForTrack(Track track) {
            return null;
        }

        @Override
        public boolean mediaItemMatchesTrackForReuse(MediaItem mediaItem, Track track) {
            return true;
        }
    }

    private static final class FakeUpcomingTracksProvider implements IntFunction<List<Track>> {
        private final List<Track> tracks = new ArrayList<>();
        private int calls;
        private int lastMaxCount;

        @Override
        public List<Track> apply(int maxCount) {
            calls++;
            lastMaxCount = maxCount;
            return tracks;
        }
    }
}
