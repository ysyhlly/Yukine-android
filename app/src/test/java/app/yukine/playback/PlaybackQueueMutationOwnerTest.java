package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.net.Uri;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.yukine.model.Track;
import org.junit.Test;

public class PlaybackQueueMutationOwnerTest {
    @Test
    public void delegatesQueueMutationsToQueueOperations() {
        FakeQueueMutationOperations operations = new FakeQueueMutationOperations();
        PlaybackQueueMutationOwner owner = new PlaybackQueueMutationOwner(() -> operations);
        List<Track> tracks = Arrays.asList(track(1L), track(2L));

        owner.playQueue(tracks, 1, 2500L);
        owner.appendToQueue(tracks);
        Set<Long> trackIds = new HashSet<>(Arrays.asList(1L, 2L));
        Set<Long> retainedTrackIds = new HashSet<>(Collections.singletonList(2L));
        owner.removeTracksById(trackIds);
        owner.retainTracksById(retainedTrackIds);
        owner.clearQueue();
        Track replacement = track(7L);
        Track replacementById = track(8L);
        owner.moveQueueTrack(3, 1);
        owner.replaceQueuedTrack(replacement);
        owner.replaceQueuedTrackById(6L, replacementById);

        assertSame(tracks, operations.playedTracks);
        assertEquals(1, operations.startIndex);
        assertEquals(2500L, operations.startPositionMs);
        assertSame(tracks, operations.appendedTracks);
        assertSame(trackIds, operations.removedTrackIds);
        assertSame(retainedTrackIds, operations.retainedTrackIds);
        assertEquals(1, operations.clearCalls);
        assertEquals(3, operations.moveFromIndex);
        assertEquals(1, operations.moveToIndex);
        assertSame(replacement, operations.replacement);
        assertEquals(6L, operations.oldTrackId);
        assertSame(replacementById, operations.replacementById);
    }

    @Test
    public void ignoresMissingOrEmptyQueueMutationInputs() {
        FakeQueueMutationOperations operations = new FakeQueueMutationOperations();
        PlaybackQueueMutationOwner missingSupplier = new PlaybackQueueMutationOwner(null);
        PlaybackQueueMutationOwner missingOperations = new PlaybackQueueMutationOwner(() -> null);
        PlaybackQueueMutationOwner owner = new PlaybackQueueMutationOwner(() -> operations);

        missingSupplier.playQueue(Collections.singletonList(track(3L)), 0, 0L);
        missingOperations.appendToQueue(Collections.singletonList(track(4L)));
        missingSupplier.clearQueue();
        missingOperations.clearQueue();
        missingSupplier.moveQueueTrack(1, 2);
        missingOperations.replaceQueuedTrack(track(9L));
        missingSupplier.replaceQueuedTrackById(9L, track(10L));
        owner.playQueue(null, 0, 0L);
        owner.playQueue(Collections.emptyList(), 0, 0L);
        owner.appendToQueue(null);
        owner.appendToQueue(Collections.emptyList());
        owner.removeTracksById(null);
        owner.removeTracksById(Collections.emptySet());
        owner.retainTracksById(null);
        owner.retainTracksById(Collections.emptySet());

        assertEquals(0, operations.playCalls);
        assertEquals(0, operations.appendCalls);
        assertEquals(0, operations.removeCalls);
        assertEquals(0, operations.retainCalls);
        assertEquals(0, operations.clearCalls);
        assertEquals(0, operations.moveCalls);
        assertEquals(0, operations.replaceCalls);
        assertEquals(0, operations.replaceByIdCalls);
    }

    @Test
    public void usesUnsetPositionForTwoArgumentPlayQueue() {
        FakeQueueMutationOperations operations = new FakeQueueMutationOperations();
        PlaybackQueueMutationOwner owner = new PlaybackQueueMutationOwner(() -> operations);
        List<Track> tracks = Collections.singletonList(track(5L));

        owner.playQueue(tracks, 2);

        assertSame(tracks, operations.playedTracks);
        assertEquals(2, operations.startIndex);
        assertEquals(androidx.media3.common.C.TIME_UNSET, operations.startPositionMs);
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakeQueueMutationOperations
            implements PlaybackQueueMutationOwner.QueueMutationOperations {
        private int playCalls;
        private int appendCalls;
        private int removeCalls;
        private int retainCalls;
        private int clearCalls;
        private int moveCalls;
        private int replaceCalls;
        private int replaceByIdCalls;
        private List<Track> playedTracks;
        private int startIndex;
        private long startPositionMs;
        private List<Track> appendedTracks;
        private Set<Long> removedTrackIds;
        private Set<Long> retainedTrackIds;
        private int moveFromIndex;
        private int moveToIndex;
        private Track replacement;
        private long oldTrackId;
        private Track replacementById;

        @Override
        public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
            playCalls++;
            playedTracks = tracks;
            this.startIndex = startIndex;
            this.startPositionMs = startPositionMs;
        }

        @Override
        public void appendToQueue(List<Track> tracks) {
            appendCalls++;
            appendedTracks = tracks;
        }

        @Override
        public void removeTracksById(Set<Long> trackIds) {
            removeCalls++;
            removedTrackIds = trackIds;
        }

        @Override
        public void retainTracksById(Set<Long> trackIdsToKeep) {
            retainCalls++;
            retainedTrackIds = trackIdsToKeep;
        }

        @Override
        public void clearQueue() {
            clearCalls++;
        }

        @Override
        public void moveQueueTrack(int fromIndex, int toIndex) {
            moveCalls++;
            moveFromIndex = fromIndex;
            moveToIndex = toIndex;
        }

        @Override
        public void replaceQueuedTrack(Track replacement) {
            replaceCalls++;
            this.replacement = replacement;
        }

        @Override
        public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
            replaceByIdCalls++;
            this.oldTrackId = oldTrackId;
            replacementById = replacement;
        }
    }
}
