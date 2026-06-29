package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import app.yukine.model.Track;

import org.junit.Test;
import org.junit.runner.RunWith;
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, null, scheduler);
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
        PlaybackPrecacheManager manager = new PlaybackPrecacheManager(stateProvider, null, scheduler);
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

    private static Track track(long id, String uri) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse(uri),
                "streaming:test:" + id
        );
    }

    private static final class FakeStateProvider implements PlaybackPrecacheManager.StateProvider {
        private final PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        private Track currentTrack;

        @Override
        public List<Track> queueSnapshot() {
            return Collections.emptyList();
        }

        @Override
        public int currentIndex() {
            return 0;
        }

        @Override
        public int repeatMode() {
            return EchoPlaybackService.REPEAT_OFF;
        }

        @Override
        public Track currentTrack() {
            return currentTrack;
        }

        @Override
        public boolean isHttpUri(Uri uri) {
            return true;
        }

        @Override
        public String cacheKeyForTrack(Track track) {
            return track == null ? "" : "cache:" + track.id;
        }

        @Override
        public boolean currentPlayerLoadsCacheKey(Track track, String cacheKey) {
            return true;
        }

        @Override
        public PlaybackStreamingDiagnostics streamingDiagnostics() {
            return diagnostics;
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
    }
}
