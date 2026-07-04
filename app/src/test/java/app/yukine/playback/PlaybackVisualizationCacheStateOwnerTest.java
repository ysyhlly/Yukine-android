package app.yukine.playback;

import android.os.Handler;

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
        Track track = new Track(41L, "Track", "Artist", "Album", 1000L, null, "file:41");
        Runnable task = () -> events.add("task");
        Handler handler = new Handler();
        PlaybackVisualizationCacheStateOwner owner = new PlaybackVisualizationCacheStateOwner(
                handler,
                () -> track,
                scheduled -> {
                    events.add("schedule");
                    assertSame(task, scheduled);
                }
        );

        assertSame(handler, owner.mainHandler());
        assertSame(track, owner.currentTrack());
        owner.scheduleVisualizationCacheTask(task);
        assertEquals(
                java.util.Collections.singletonList("schedule"),
                events
        );
    }

    @Test
    public void returnsNullCurrentTrackWhenSupplierIsMissing() {
        PlaybackVisualizationCacheStateOwner missingProviderOwner = new PlaybackVisualizationCacheStateOwner(
                null,
                null,
                task -> {
                }
        );
        PlaybackVisualizationCacheStateOwner nullTrackOwner = new PlaybackVisualizationCacheStateOwner(
                null,
                () -> null,
                task -> {
                }
        );

        assertNull(missingProviderOwner.currentTrack());
        assertNull(nullTrackOwner.currentTrack());
    }
}
