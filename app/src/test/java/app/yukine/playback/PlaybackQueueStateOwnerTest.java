package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import java.util.Arrays;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import org.junit.Test;

public class PlaybackQueueStateOwnerTest {
    @Test
    public void delegatesQueueStateSnapshotToQueueOperations() {
        Track track = track(12L);
        PlaybackQueueManager.QueueStateSnapshot snapshot =
                new PlaybackQueueManager.QueueStateSnapshot(track, 0, 1, false, true, false, true);
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner(
                () -> snapshot
        );

        assertSame(snapshot, owner.queueStateSnapshot());
        assertEquals(false, owner.isQueueEmpty());
        assertSame(track, owner.currentTrack());
    }

    @Test
    public void returnsEmptyQueueStateWhenProviderOperationsOrSnapshotAreMissing() {
        PlaybackQueueStateOwner missingSupplier = new PlaybackQueueStateOwner(null);
        PlaybackQueueStateOwner missingSnapshot = new PlaybackQueueStateOwner(() -> null);

        assertEmpty(missingSupplier.queueStateSnapshot());
        assertEmpty(missingSnapshot.queueStateSnapshot());
        assertTrue(missingSupplier.isQueueEmpty());
        assertTrue(missingSnapshot.isQueueEmpty());
        assertSame(null, missingSupplier.currentTrack());
        assertSame(null, missingSnapshot.currentTrack());
    }

    @Test
    public void failedTrackPolicyUsesQueueStateSnapshot() {
        Track failed = track(7L);
        PlaybackQueueStateOwner missingQueue = new PlaybackQueueStateOwner(null);
        PlaybackQueueStateOwner singleTrack = new PlaybackQueueStateOwner(
                () -> new PlaybackQueueManager.QueueStateSnapshot(failed, 0, 1, false, true, false, true)
        );
        PlaybackQueueStateOwner multipleTracks = new PlaybackQueueStateOwner(
                () -> new PlaybackQueueManager.QueueStateSnapshot(failed, 0, 2, false, true, true, false)
        );

        assertFalse(missingQueue.canSkipFailedTrack(failed));
        assertFalse(multipleTracks.canSkipFailedTrack(null));
        assertFalse(multipleTracks.canSkipFailedTrack(track(-1L)));
        assertFalse(singleTrack.canSkipFailedTrack(failed));
        assertTrue(multipleTracks.canSkipFailedTrack(failed));
    }

    @Test
    public void delegatesQueueSnapshotToQueueOperations() {
        List<Track> queue = Arrays.asList(track(1L), track(2L));
        List<Track> upcoming = Arrays.asList(track(3L), track(4L));
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner(
                PlaybackQueueManager.QueueStateSnapshot::empty,
                () -> queue,
                maxCount -> upcoming.subList(0, Math.min(maxCount, upcoming.size()))
        );

        assertEquals(queue, owner.queueSnapshot());
        assertEquals(upcoming.subList(0, 1), owner.upcomingTracksForPrecache(1));
    }

    @Test
    public void returnsEmptyQueueSnapshotWhenProviderOperationsOrSnapshotAreMissing() {
        PlaybackQueueStateOwner missingSupplier = new PlaybackQueueStateOwner(null);
        PlaybackQueueStateOwner missingOperations = new PlaybackQueueStateOwner(
                PlaybackQueueManager.QueueStateSnapshot::empty,
                null,
                null
        );
        PlaybackQueueStateOwner missingSnapshot = new PlaybackQueueStateOwner(
                PlaybackQueueManager.QueueStateSnapshot::empty,
                () -> null,
                maxCount -> null
        );

        assertTrue(missingSupplier.queueSnapshot().isEmpty());
        assertTrue(missingOperations.queueSnapshot().isEmpty());
        assertTrue(missingSnapshot.queueSnapshot().isEmpty());
        assertTrue(missingSupplier.upcomingTracksForPrecache(3).isEmpty());
        assertTrue(missingOperations.upcomingTracksForPrecache(3).isEmpty());
        assertTrue(missingSnapshot.upcomingTracksForPrecache(3).isEmpty());
    }

    private static void assertEmpty(PlaybackQueueManager.QueueStateSnapshot snapshot) {
        assertSame(null, snapshot.getCurrentTrack());
        assertEquals(-1, snapshot.getCurrentIndex());
        assertEquals(0, snapshot.getQueueSize());
        assertEquals(true, snapshot.isQueueEmpty());
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }
}
