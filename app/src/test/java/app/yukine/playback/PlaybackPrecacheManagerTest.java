package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Track;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class PlaybackPrecacheManagerTest {
    @Test
    public void releaseCancelsDelayedPrecacheCallbacksOwnedByManager() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
        stateProvider.currentTrack = track(1L, "https://example.test/one.mp3");

        manager.precacheTrack(stateProvider.currentTrack);
        manager.release();

        assertEquals(2, scheduler.removedCallbacks);
        assertEquals(0, scheduler.pendingCallbacks.size());
    }

    @Test
    public void replacingCurrentPrecacheCancelsPreviousDelayedCallbacks() {
        FakeStateProvider stateProvider = new FakeStateProvider();
        FakeCallbackScheduler scheduler = new FakeCallbackScheduler();
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, mediaSourceProvider(), scheduler);
        Track current = track(1L, "https://example.test/shared.mp3");
        Track candidate = track(2L, "https://example.test/shared.mp3");

        stateProvider.currentTrack = current;
        manager.precacheTrack(candidate);
        manager.release();

        assertEquals(2, scheduler.removedCallbacks);
        assertEquals(0, scheduler.pendingCallbacks.size());
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
}
