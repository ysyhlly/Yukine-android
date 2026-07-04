package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.ExoPlayer;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class PlaybackQueueCompletionOwnerTest {
    @Test
    public void repeatCurrentCompletionIsPreparedByQueueManagerWithoutBoundaryAction() {
        FakeQueuePlaybackActions actions = new FakeQueuePlaybackActions();
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueueManager queueManager = queueManagerWithTracks(
                store,
                Collections.singletonList(track(1L)),
                0,
                REPEAT_ONE,
                runtimeStateManager(),
                actions
        );
        actions.clearRecords();
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = owner(queueManager, events);

        owner.playAfterCompletion();

        assertEquals(1, actions.prepareCurrentCalls);
        assertTrue(actions.lastPreparePlayWhenReady);
        assertTrue(events.isEmpty());
    }

    @Test
    public void routesStopAtEndCompletionThroughQueuePreparationAndBoundary() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager();
        runtimeStateManager.setPreparing(true);
        Track current = track(1L);
        List<String> events = new ArrayList<>();
        PlaybackQueueManager queueManager = queueManagerWithTracks(
                store,
                Collections.singletonList(current),
                0,
                REPEAT_OFF,
                runtimeStateManager
        );
        PlaybackQueueCompletionOwner owner = owner(queueManager, events);

        owner.playAfterCompletion();

        assertEquals(0, queueManager.queueStateSnapshot().getCurrentIndex());
        assertEquals(1L, queueManager.queueStateSnapshot().getCurrentTrack().id);
        assertEquals(Collections.singletonList(false), store.savedResumeRequestedValues);
        assertFalse(runtimeStateManager.preparing());
        assertEquals(Collections.singletonList("stopAtEnd"), events);
    }

    @Test
    public void routesAdvanceCompletionThroughQueuePreparationAndBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueManager queueManager = queueManagerWithTracks(
                new FakeQueueStore(),
                Arrays.asList(track(1L), track(2L)),
                0,
                REPEAT_OFF
        );
        PlaybackQueueCompletionOwner owner = owner(queueManager, events);

        owner.playAfterCompletion();

        assertEquals(Collections.singletonList("skipNext"), events);
    }

    @Test
    public void stopAndClearCompletionDoesNotPrepareQueueCompletion() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = owner(
                queueManager(new FakeQueueStore(), runtimeStateManager()),
                events
        );

        owner.playAfterCompletion();

        assertEquals(Collections.singletonList("stopAndClear"), events);
    }

    @Test
    public void requiresQueueManager() {
        try {
            owner(null, new ArrayList<>());
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError("Expected constructor to require a queue manager");
    }

    @Test
    public void stopAndClearPlaybackPreparesQueueBeforeBoundary() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackRuntimeStateManager runtimeStateManager = runtimeStateManager();
        runtimeStateManager.setPreparing(true);
        PlaybackQueueManager queueManager = queueManagerWithTracks(
                store,
                Arrays.asList(track(5L), track(6L)),
                1,
                REPEAT_ALL,
                runtimeStateManager
        );
        List<String> events = new ArrayList<>();

        owner(queueManager, events).stopAndClearPlayback();

        assertTrue(queueManager.queueStateSnapshot().isQueueEmpty());
        assertEquals(-1, queueManager.queueStateSnapshot().getCurrentIndex());
        assertTrue(store.savedTracks.isEmpty());
        assertEquals(-1, store.savedIndex);
        assertEquals(Collections.singletonList(false), store.savedResumeRequestedValues);
        assertFalse(runtimeStateManager.preparing());
        assertEquals(Collections.singletonList("stopAndClear"), events);
    }

    @Test
    public void missingBoundaryDoesNotCrash() {
        PlaybackQueueCompletionOwner owner = owner(
                queueManagerWithTracks(
                        new FakeQueueStore(),
                        Arrays.asList(track(1L), track(2L)),
                        0,
                        REPEAT_OFF
                ),
                null
        );

        owner.playAfterCompletion();
    }

    private static PlaybackQueueCompletionOwner owner(
            PlaybackQueueManager queueManager,
            List<String> events
    ) {
        return new PlaybackQueueCompletionOwner(
                queueManager,
                action(events, "stopAndClear"),
                action(events, "stopAtEnd"),
                action(events, "skipNext")
        );
    }

    private static Runnable action(List<String> events, String event) {
        return events == null ? null : () -> events.add(event);
    }

    private static PlaybackQueueManager queueManagerWithTracks(
            FakeQueueStore store,
            List<Track> tracks,
            int currentIndex,
            int repeatMode
    ) {
        return queueManagerWithTracks(store, tracks, currentIndex, repeatMode, runtimeStateManager());
    }

    private static PlaybackQueueManager queueManagerWithTracks(
            FakeQueueStore store,
            List<Track> tracks,
            int currentIndex,
            int repeatMode,
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return queueManagerWithTracks(
                store,
                tracks,
                currentIndex,
                repeatMode,
                runtimeStateManager,
                new NoopQueuePlaybackActions()
        );
    }

    private static PlaybackQueueManager queueManagerWithTracks(
            FakeQueueStore store,
            List<Track> tracks,
            int currentIndex,
            int repeatMode,
            PlaybackRuntimeStateManager runtimeStateManager,
            PlaybackQueueManager.QueuePlaybackActions actions
    ) {
        runtimeStateManager.setRepeatMode(repeatMode);
        PlaybackQueueManager queueManager = queueManager(store, runtimeStateManager, actions);
        queueManager.playQueue(tracks, currentIndex, 0L);
        store.clearRecords();
        return queueManager;
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return queueManager(store, runtimeStateManager, new NoopQueuePlaybackActions());
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            PlaybackRuntimeStateManager runtimeStateManager,
            PlaybackQueueManager.QueuePlaybackActions actions
    ) {
        return new PlaybackQueueManager(
                store,
                actions,
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                runtimeStateManager,
                null
        );
    }

    private static PlaybackRuntimeStateManager runtimeStateManager() {
        return new PlaybackRuntimeStateManager(new PlaybackRuntimeStateManager.StateProvider() {
            @Override
            public ExoPlayer player() {
                return null;
            }

            @Override
            public boolean playerMirrorsQueue() {
                return false;
            }

            @Override
            public Track currentTrack() {
                return null;
            }
        });
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                null,
                "streaming:netease:" + id
        );
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private List<Track> savedTracks = Collections.emptyList();
        private int savedIndex = -1;
        private final List<Boolean> savedResumeRequestedValues = new ArrayList<>();
        private final List<String> savedPositions = new ArrayList<>();

        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(savedTracks, savedIndex);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
            savedTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
            savedIndex = currentIndex;
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
            savedResumeRequestedValues.add(requested);
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
            savedPositions.add(trackId + ":" + positionMs);
        }

        private void clearRecords() {
            savedResumeRequestedValues.clear();
            savedPositions.clear();
        }
    }

    private static final class NoopQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
        }
    }

    private static final class FakeQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        private int prepareCurrentCalls;
        private boolean lastPreparePlayWhenReady;

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            prepareCurrentCalls++;
            lastPreparePlayWhenReady = playWhenReady;
        }

        @Override
        public void publishState() {
        }

        private void clearRecords() {
            prepareCurrentCalls = 0;
            lastPreparePlayWhenReady = false;
        }
    }

    private static final class NoopStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
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
