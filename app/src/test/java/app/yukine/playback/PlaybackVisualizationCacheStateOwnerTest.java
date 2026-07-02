package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

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
                    events.add("queueState");
                    return new PlaybackQueueManager.QueueStateSnapshot(track, 0, 1);
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
                        "queueState",
                        "schedule"
                ),
                events
        );
    }

    @Test
    public void returnsNullCurrentTrackWhenQueueStateIsMissing() {
        PlaybackVisualizationCacheStateOwner missingProviderOwner = new PlaybackVisualizationCacheStateOwner(
                () -> null,
                null,
                task -> {
                }
        );
        PlaybackVisualizationCacheStateOwner nullSnapshotOwner = new PlaybackVisualizationCacheStateOwner(
                () -> null,
                () -> null,
                task -> {
                }
        );

        assertNull(missingProviderOwner.currentTrack());
        assertNull(nullSnapshotOwner.currentTrack());
    }
}
