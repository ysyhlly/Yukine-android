package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackErrorRecoveryCommandOwnerTest {
    @Test
    public void delegatesErrorRecoveryActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(7L);
        PlaybackErrorRecoveryCommandOwner owner = new PlaybackErrorRecoveryCommandOwner(
                () -> new PlaybackQueueManager.QueueStateSnapshot(track, 0, 2),
                playWhenReady -> events.add("prepare:" + playWhenReady),
                () -> events.add("next"),
                message -> events.add("error:" + message),
                () -> events.add("publish"),
                (message, error) -> events.add("warn:" + message + ":" + error.getMessage())
        );

        assertSame(track, owner.currentTrack());
        assertTrue(owner.canSkipFailedTrack(track));
        assertEquals(false, owner.canSkipFailedTrack(null));
        assertEquals(false, owner.canSkipFailedTrack(track(-1L)));
        assertEquals("trackId=7, title=Track 7, dataPath=file:7, uri=null", owner.debugTrack(track));
        assertEquals("track=<null>", owner.debugTrack(null));
        owner.prepareCurrent(true);
        owner.skipToNext();
        owner.setErrorMessage("Unable");
        owner.publishState();
        owner.logWarning("Playback failed", new Exception("boom"));

        assertEquals(
                java.util.Arrays.asList(
                        "prepare:true",
                        "next",
                        "error:Unable",
                        "publish",
                        "warn:Playback failed:boom"
                ),
                events
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }
}
