package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackVisualizationCacheStateOwnerTest {
    @Test
    public void delegatesVisualizationCacheStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(41L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:41");
        Runnable task = () -> events.add("task");
        PlaybackVisualizationCacheStateOwner owner = new PlaybackVisualizationCacheStateOwner(
                () -> {
                    events.add("handler");
                    return null;
                },
                () -> {
                    events.add("track");
                    return track;
                },
                scheduled -> {
                    events.add("schedule");
                    assertSame(task, scheduled);
                }
        );

        assertNull(owner.mainHandler());
        assertSame(track, owner.currentTrack());
        owner.scheduleVisualizationCacheTask(task);
        assertEquals(
                java.util.Arrays.asList(
                        "handler",
                        "track",
                        "schedule"
                ),
                events
        );
    }
}
