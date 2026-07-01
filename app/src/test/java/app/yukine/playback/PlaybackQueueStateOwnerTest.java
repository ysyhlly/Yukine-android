package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import org.junit.Test;

public class PlaybackQueueStateOwnerTest {
    @Test
    public void delegatesQueueStateSnapshotToFallbackSupplier() {
        Track track = track(12L);
        PlaybackQueueManager.QueueStateSnapshot snapshot =
                new PlaybackQueueManager.QueueStateSnapshot(track, 0, 1, false, true, false, true);
        PlaybackQueueStateOwner owner = new PlaybackQueueStateOwner(
                () -> snapshot
        );

        assertSame(snapshot, owner.queueStateSnapshot());
        assertEquals(false, owner.queueStateSnapshot().isQueueEmpty());
    }

    @Test
    public void returnsEmptyQueueStateWhenFallbackSupplierOrSnapshotAreMissing() {
        PlaybackQueueStateOwner missingSupplier = new PlaybackQueueStateOwner(null);
        PlaybackQueueStateOwner missingSnapshot = new PlaybackQueueStateOwner(() -> null);

        assertEmpty(missingSupplier.queueStateSnapshot());
        assertEmpty(missingSnapshot.queueStateSnapshot());
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
    public void returnsEmptyQueueSnapshotWhenManagerIsMissing() {
        PlaybackQueueStateOwner missingSupplier = new PlaybackQueueStateOwner(null);
        PlaybackQueueStateOwner missingManagerProvider =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(null);
        PlaybackQueueStateOwner missingManager =
                PlaybackQueueStateOwner.fromPlaybackQueueManager(() -> null);

        assertTrue(missingSupplier.queueSnapshot().isEmpty());
        assertTrue(missingManagerProvider.queueSnapshot().isEmpty());
        assertTrue(missingManager.queueSnapshot().isEmpty());
        assertTrue(missingSupplier.upcomingTracksForPrecache(3).isEmpty());
        assertTrue(missingManagerProvider.upcomingTracksForPrecache(3).isEmpty());
        assertTrue(missingManager.upcomingTracksForPrecache(3).isEmpty());
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
