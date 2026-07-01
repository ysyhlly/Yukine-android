package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackPositionManager;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import org.junit.Test;

public class PlaybackQueueMutationOwnerTest {
    @Test
    public void delegatesQueueMutationsToPlaybackQueueManager() {
        FakeQueuePlaybackActions actions = new FakeQueuePlaybackActions();
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), actions, null);
        PlaybackQueueMutationOwner owner = owner(queueManager);
        List<Track> tracks = Arrays.asList(track(1L), track(2L), track(3L));

        owner.playQueue(tracks, 1, 2500L);
        assertTrackIds(Arrays.asList(1L, 2L, 3L), queueManager.queueSnapshot());
        assertEquals(2L, queueManager.queueStateSnapshot().getCurrentTrack().id);

        owner.appendToQueue(Collections.singletonList(track(4L)));
        assertTrackIds(Arrays.asList(1L, 2L, 3L, 4L), queueManager.queueSnapshot());

        owner.moveQueueTrack(3, 0);
        assertTrackIds(Arrays.asList(4L, 1L, 2L, 3L), queueManager.queueSnapshot());
        assertEquals(2L, queueManager.queueStateSnapshot().getCurrentTrack().id);

        Track replacement = track(1L);
        owner.replaceQueuedTrack(replacement);
        assertSame(replacement, queueManager.queueSnapshot().get(1));

        Track replacementById = track(9L);
        owner.replaceQueuedTrackById(4L, replacementById);
        assertTrackIds(Arrays.asList(9L, 1L, 2L, 3L), queueManager.queueSnapshot());
        assertSame(replacementById, queueManager.queueSnapshot().get(0));

        owner.removeTracksById(new HashSet<>(Collections.singletonList(1L)));
        assertTrackIds(Arrays.asList(9L, 2L, 3L), queueManager.queueSnapshot());
        assertEquals(2L, queueManager.queueStateSnapshot().getCurrentTrack().id);

        owner.retainTracksById(new HashSet<>(Collections.singletonList(2L)));
        assertTrackIds(Collections.singletonList(2L), queueManager.queueSnapshot());
        assertEquals(2L, queueManager.queueStateSnapshot().getCurrentTrack().id);

        owner.clearQueue();
        assertEquals(1, actions.stopAndClearCalls);
    }

    @Test
    public void ignoresMissingManagerOrEmptyQueueMutationInputs() {
        FakeQueuePlaybackActions actions = new FakeQueuePlaybackActions();
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), actions, null);
        PlaybackQueueMutationOwner missingManager = PlaybackQueueMutationOwner.fromPlaybackQueueManager(null);
        PlaybackQueueMutationOwner owner = owner(queueManager);

        missingManager.playQueue(Collections.singletonList(track(3L)), 0, 0L);
        missingManager.appendToQueue(Collections.singletonList(track(4L)));
        missingManager.removeTracksById(Collections.singleton(4L));
        missingManager.retainTracksById(Collections.singleton(4L));
        missingManager.clearQueue();
        missingManager.moveQueueTrack(1, 2);
        missingManager.replaceQueuedTrack(track(9L));
        missingManager.replaceQueuedTrackById(9L, track(10L));
        owner.playQueue(null, 0, 0L);
        owner.playQueue(Collections.emptyList(), 0, 0L);
        owner.appendToQueue(null);
        owner.appendToQueue(Collections.emptyList());
        owner.removeTracksById(null);
        owner.removeTracksById(Collections.emptySet());
        owner.retainTracksById(null);
        owner.retainTracksById(Collections.emptySet());

        assertEquals(0, queueManager.queueSnapshot().size());
        assertEquals(0, actions.prepareCurrentCalls);
        assertEquals(0, actions.publishStateCalls);
        assertEquals(0, actions.stopAndClearCalls);
    }

    @Test
    public void twoArgumentPlayQueueUsesUnsetStartPosition() {
        FakeQueueStore store = new FakeQueueStore();
        Track track = track(5L, 10_000L);
        PlaybackQueueManager[] queueManagerRef = new PlaybackQueueManager[1];
        PlaybackPositionManager positionManager = new PlaybackPositionManager(
                store,
                new PlaybackPositionManager.StateProvider() {
                    @Override
                    public Track currentTrack() {
                        return queueManagerRef[0] == null
                                ? null
                                : queueManagerRef[0].queueStateSnapshot().getCurrentTrack();
                    }

                    @Override
                    public long positionMs() {
                        return 0L;
                    }
                }
        );
        PlaybackQueueManager queueManager =
                queueManager(store, new FakeQueuePlaybackActions(), positionManager);
        queueManagerRef[0] = queueManager;
        PlaybackQueueMutationOwner owner = owner(queueManager);

        owner.playQueue(Collections.singletonList(track), 0);

        assertTrackIds(Collections.singletonList(5L), queueManager.queueSnapshot());
        assertEquals(0L, positionManager.restoredPositionFor(track));
    }

    private static PlaybackQueueMutationOwner owner(PlaybackQueueManager queueManager) {
        return PlaybackQueueMutationOwner.fromPlaybackQueueManager(() -> queueManager);
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            FakeQueuePlaybackActions actions,
            PlaybackPositionManager positionManager
    ) {
        return new PlaybackQueueManager(
                store,
                new ArrayList<>(),
                actions,
                positionManager,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null,
                new Random(1L)
        );
    }

    private static Track track(long id) {
        return track(id, 1000L);
    }

    private static Track track(long id, long durationMs) {
        return new Track(id, "Track " + id, "Artist", "Album", durationMs, Uri.EMPTY, "file:" + id);
    }

    private static void assertTrackIds(List<Long> expectedIds, List<Track> tracks) {
        List<Long> actualIds = new ArrayList<>();
        for (Track track : tracks) {
            actualIds.add(track.id);
        }
        assertEquals(expectedIds, actualIds);
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(Collections.emptyList(), -1);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
        }

        @Override
        public boolean loadPlaybackRestoreEnabled() {
            return true;
        }

        @Override
        public void savePlaybackRestoreEnabled(boolean enabled) {
        }

        @Override
        public long loadPlaybackPositionTrackId() {
            return -1L;
        }

        @Override
        public long loadPlaybackPositionMs() {
            return 0L;
        }

        @Override
        public void savePlaybackPosition(long trackId, long positionMs) {
        }
    }

    private static final class FakeQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        private int prepareCurrentCalls;
        private int publishStateCalls;
        private int stopAndClearCalls;

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            prepareCurrentCalls++;
        }

        @Override
        public void publishState() {
            publishStateCalls++;
        }

        @Override
        public void stopAndClear() {
            stopAndClearCalls++;
        }
    }

    private static final class NoopStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoredTrackFor(Track track) {
            return track;
        }

        @Override
        public void restoreForDataPath(String dataPath) {
        }
    }

    private static final class NoopMirroredQueuePlayer
            implements PlaybackQueueManager.MirroredQueuePlayer {
        @Override
        public boolean matchesCurrentQueue() {
            return false;
        }

        @Override
        public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
            return false;
        }
    }
}
