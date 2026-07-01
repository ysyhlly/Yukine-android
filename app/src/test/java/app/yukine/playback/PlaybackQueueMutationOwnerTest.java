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
        FakeQueueMutationActions actions = new FakeQueueMutationActions();
        PlaybackQueueMutationOwner owner = owner(actions);
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

        assertSame(tracks, actions.playedTracks);
        assertEquals(1, actions.startIndex);
        assertEquals(2500L, actions.startPositionMs);
        assertSame(tracks, actions.appendedTracks);
        assertSame(trackIds, actions.removedTrackIds);
        assertSame(retainedTrackIds, actions.retainedTrackIds);
        assertEquals(1, actions.clearCalls);
        assertEquals(3, actions.moveFromIndex);
        assertEquals(1, actions.moveToIndex);
        assertSame(replacement, actions.replacement);
        assertEquals(6L, actions.oldTrackId);
        assertSame(replacementById, actions.replacementById);
    }

    @Test
    public void ignoresMissingOrEmptyQueueMutationInputs() {
        FakeQueueMutationActions actions = new FakeQueueMutationActions();
        PlaybackQueueMutationOwner missingActions = new PlaybackQueueMutationOwner(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        PlaybackQueueMutationOwner owner = owner(actions);

        missingActions.playQueue(Collections.singletonList(track(3L)), 0, 0L);
        missingActions.appendToQueue(Collections.singletonList(track(4L)));
        missingActions.clearQueue();
        missingActions.moveQueueTrack(1, 2);
        missingActions.replaceQueuedTrack(track(9L));
        missingActions.replaceQueuedTrackById(9L, track(10L));
        owner.playQueue(null, 0, 0L);
        owner.playQueue(Collections.emptyList(), 0, 0L);
        owner.appendToQueue(null);
        owner.appendToQueue(Collections.emptyList());
        owner.removeTracksById(null);
        owner.removeTracksById(Collections.emptySet());
        owner.retainTracksById(null);
        owner.retainTracksById(Collections.emptySet());

        assertEquals(0, actions.playCalls);
        assertEquals(0, actions.appendCalls);
        assertEquals(0, actions.removeCalls);
        assertEquals(0, actions.retainCalls);
        assertEquals(0, actions.clearCalls);
        assertEquals(0, actions.moveCalls);
        assertEquals(0, actions.replaceCalls);
        assertEquals(0, actions.replaceByIdCalls);
    }

    @Test
    public void usesUnsetPositionForTwoArgumentPlayQueue() {
        FakeQueueMutationActions actions = new FakeQueueMutationActions();
        PlaybackQueueMutationOwner owner = owner(actions);
        List<Track> tracks = Collections.singletonList(track(5L));

        owner.playQueue(tracks, 2);

        assertSame(tracks, actions.playedTracks);
        assertEquals(2, actions.startIndex);
        assertEquals(androidx.media3.common.C.TIME_UNSET, actions.startPositionMs);
    }

    private static PlaybackQueueMutationOwner owner(FakeQueueMutationActions actions) {
        return new PlaybackQueueMutationOwner(
                actions::playQueue,
                actions::appendToQueue,
                actions::removeTracksById,
                actions::retainTracksById,
                actions::clearQueue,
                actions::moveQueueTrack,
                actions::replaceQueuedTrack,
                actions::replaceQueuedTrackById
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakeQueueMutationActions {
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

        public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
            playCalls++;
            playedTracks = tracks;
            this.startIndex = startIndex;
            this.startPositionMs = startPositionMs;
        }

        public void appendToQueue(List<Track> tracks) {
            appendCalls++;
            appendedTracks = tracks;
        }

        public void removeTracksById(Set<Long> trackIds) {
            removeCalls++;
            removedTrackIds = trackIds;
        }

        public void retainTracksById(Set<Long> trackIdsToKeep) {
            retainCalls++;
            retainedTrackIds = trackIdsToKeep;
        }

        public void clearQueue() {
            clearCalls++;
        }

        public void moveQueueTrack(int fromIndex, int toIndex) {
            moveCalls++;
            moveFromIndex = fromIndex;
            moveToIndex = toIndex;
        }

        public void replaceQueuedTrack(Track replacement) {
            replaceCalls++;
            this.replacement = replacement;
        }

        public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
            replaceByIdCalls++;
            this.oldTrackId = oldTrackId;
            replacementById = replacement;
        }
    }
}
