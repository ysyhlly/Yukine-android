package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.net.Uri;

import app.yukine.model.Track;
import org.junit.Test;

public class PlaybackWarmupActionsOwnerTest {
    @Test
    public void delegatesPrecacheAndVisualizationWarmup() {
        Track track = track();
        FakePrecacheOperations precacheOperations = new FakePrecacheOperations();
        FakeVisualizationCacheOperations visualizationOperations =
                new FakeVisualizationCacheOperations();
        PlaybackWarmupActionsOwner owner = new PlaybackWarmupActionsOwner(
                () -> precacheOperations,
                () -> visualizationOperations
        );

        owner.precacheTrack(track);
        owner.scheduleVisualizationCache(track);

        assertEquals(1, precacheOperations.calls);
        assertSame(track, precacheOperations.lastTrack);
        assertEquals(1, visualizationOperations.calls);
        assertSame(track, visualizationOperations.lastTrack);
    }

    @Test
    public void ignoresMissingWarmupOperations() {
        PlaybackWarmupActionsOwner nullProvidersOwner = new PlaybackWarmupActionsOwner(null, null);
        PlaybackWarmupActionsOwner missingOperationsOwner = new PlaybackWarmupActionsOwner(
                () -> null,
                () -> null
        );

        nullProvidersOwner.precacheTrack(track());
        nullProvidersOwner.scheduleVisualizationCache(track());
        missingOperationsOwner.precacheTrack(track());
        missingOperationsOwner.scheduleVisualizationCache(track());
    }

    private static Track track() {
        return new Track(
                11L,
                "Track",
                "Artist",
                "Album",
                1000L,
                Uri.parse("https://example.test/track.mp3"),
                "streaming:test:11"
        );
    }

    private static final class FakePrecacheOperations
            implements PlaybackWarmupActionsOwner.PrecacheOperations {
        private int calls;
        private Track lastTrack;

        @Override
        public void precacheTrack(Track track) {
            calls++;
            lastTrack = track;
        }
    }

    private static final class FakeVisualizationCacheOperations
            implements PlaybackWarmupActionsOwner.VisualizationCacheOperations {
        private int calls;
        private Track lastTrack;

        @Override
        public void scheduleVisualizationCache(Track track) {
            calls++;
            lastTrack = track;
        }
    }
}
