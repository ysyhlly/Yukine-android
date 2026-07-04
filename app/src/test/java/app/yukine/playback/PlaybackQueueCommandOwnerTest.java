package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueCommandOwnerTest {
    @Test
    public void delegatesQueueActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(7L);
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                () -> queueSnapshot(track),
                (currentTrack, playWhenReady) -> events.add("prepare:" + currentTrack.id + ":" + playWhenReady),
                () -> events.add("publish")
        );

        owner.prepareCurrent(true);
        owner.publishState();

        assertEquals(
                java.util.Arrays.asList(
                        "prepare:7:true",
                        "publish"
                ),
                events
        );
        assertFalse(owner.runIfCurrentTrackMissing(() -> events.add("fallback")));
        assertTrue(owner.prepareCurrentOrRunFallback(false, () -> events.add("fallback")));
        assertEquals(
                java.util.Arrays.asList(
                        "prepare:7:true",
                        "publish",
                        "prepare:7:false"
                ),
                events
        );
    }

    @Test
    public void ignoresPrepareWhenCurrentTrackIsMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                PlaybackQueueManager.QueueStateSnapshot::empty,
                (currentTrack, playWhenReady) -> events.add("prepare"),
                () -> events.add("publish")
        );

        owner.prepareCurrent(true);
        owner.publishState();

        assertEquals(Collections.singletonList("publish"), events);
        assertTrue(owner.runIfCurrentTrackMissing(() -> events.add("fallback")));
        assertFalse(owner.prepareCurrentOrRunFallback(false, () -> events.add("fallback")));
        assertEquals(java.util.Arrays.asList("publish", "fallback", "fallback"), events);
    }

    private static PlaybackQueueManager.QueueStateSnapshot queueSnapshot(Track currentTrack) {
        return new PlaybackQueueManager.QueueStateSnapshot(currentTrack, 0, 1);
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, null, "file:" + id);
    }
}
