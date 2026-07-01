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
        FakeWarmupAction precacheAction = new FakeWarmupAction();
        FakeWarmupAction visualizationAction = new FakeWarmupAction();
        PlaybackWarmupActionsOwner owner = new PlaybackWarmupActionsOwner(
                precacheAction::accept,
                visualizationAction::accept
        );

        owner.precacheTrack(track);
        owner.scheduleVisualizationCache(track);

        assertEquals(1, precacheAction.calls);
        assertSame(track, precacheAction.lastTrack);
        assertEquals(1, visualizationAction.calls);
        assertSame(track, visualizationAction.lastTrack);
    }

    @Test
    public void ignoresMissingWarmupOperations() {
        PlaybackWarmupActionsOwner nullProvidersOwner = new PlaybackWarmupActionsOwner(null, null);

        nullProvidersOwner.precacheTrack(track());
        nullProvidersOwner.scheduleVisualizationCache(track());
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

    private static final class FakeWarmupAction {
        private int calls;
        private Track lastTrack;

        private void accept(Track track) {
            calls++;
            lastTrack = track;
        }
    }
}
