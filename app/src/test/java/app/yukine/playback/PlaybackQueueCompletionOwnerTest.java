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
    public void routesRepeatCurrentCompletionThroughQueuePreparationAndBoundary() {
        List<String> events = new ArrayList<>();
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueueManager queueManager = queueManagerWithTracks(
                store,
                Collections.singletonList(track(1L)),
                0,
                REPEAT_ONE
        );
        PlaybackQueueCompletionOwner owner = owner(queueManager, new FakeCompletionBoundary(events));

        owner.playAfterCompletion();

        assertEquals(Collections.singletonList("prepareCurrent:true"), events);
    }

    @Test
    public void routesStopAtEndCompletionThroughQueuePreparationAndBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueManager queueManager = queueManagerWithTracks(
                new FakeQueueStore(),
                Collections.singletonList(track(1L)),
                0,
                REPEAT_OFF
        );
        PlaybackQueueCompletionOwner owner = owner(queueManager, new FakeCompletionBoundary(events));

        owner.playAfterCompletion();

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
        PlaybackQueueCompletionOwner owner = owner(queueManager, new FakeCompletionBoundary(events));

        owner.playAfterCompletion();

        assertEquals(Collections.singletonList("skipNext"), events);
    }

    @Test
    public void stopAndClearCompletionDoesNotPrepareQueueCompletion() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = owner(
                queueManager(new FakeQueueStore(), runtimeStateManager()),
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        assertEquals(Collections.singletonList("stopAndClear"), events);
    }

    @Test
    public void delegatesStopPreparationsToQueueManager() {
        FakeQueueStore stopStore = new FakeQueueStore();
        PlaybackRuntimeStateManager stopRuntime = runtimeStateManager();
        stopRuntime.setPreparing(true);
        stopRuntime.setErrorMessage("stale");
        PlaybackQueueManager stopManager = queueManagerWithTracks(
                stopStore,
                Collections.singletonList(track(1L)),
                0,
                REPEAT_ALL,
                stopRuntime
        );

        owner(stopManager, null).prepareStopAndClearPlaybackState();
        assertTrue(stopManager.queueStateSnapshot().isQueueEmpty());
        assertFalse(stopRuntime.preparing());
        assertEquals("", stopRuntime.errorMessage());
        assertEquals(Collections.singletonList(false), stopStore.savedResumeRequestedValues);

        FakeQueueStore endStore = new FakeQueueStore();
        PlaybackRuntimeStateManager endRuntime = runtimeStateManager();
        endRuntime.setPreparing(true);
        endRuntime.setErrorMessage("stale");
        PlaybackQueueManager endManager = queueManagerWithTracks(
                endStore,
                Collections.singletonList(track(2L)),
                0,
                REPEAT_OFF,
                endRuntime
        );

        assertTrue(owner(endManager, null).prepareStopAtEndOfQueue());
        assertFalse(endRuntime.preparing());
        assertEquals("", endRuntime.errorMessage());
        assertEquals(Collections.singletonList(false), endStore.savedResumeRequestedValues);

        FakeQueueStore advanceStore = new FakeQueueStore();
        PlaybackQueueManager advanceManager = queueManagerWithTracks(
                advanceStore,
                Arrays.asList(track(3L), track(4L)),
                1,
                REPEAT_ALL
        );

        owner(advanceManager, null).prepareStopAfterAutomaticAdvance(0);

        assertEquals(0, advanceManager.queueStateSnapshot().getCurrentIndex());
        assertEquals(Collections.singletonList("4:0"), advanceStore.savedPositions);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierUsesStopAndClearBoundary() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCompletionOwner owner = new PlaybackQueueCompletionOwner(
                null,
                new FakeCompletionBoundary(events)
        );

        owner.playAfterCompletion();

        owner.prepareStopAndClearPlaybackState();
        assertFalse(owner.prepareStopAtEndOfQueue());
        owner.prepareStopAfterAutomaticAdvance(7);
        assertEquals(Arrays.asList("stopAndClear", "prepareStopAndClearFallback"), events);
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
            PlaybackQueueCompletionOwner.CompletionBoundary boundary
    ) {
        return new PlaybackQueueCompletionOwner(() -> queueManager, boundary);
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
        runtimeStateManager.setRepeatMode(repeatMode);
        PlaybackQueueManager queueManager = queueManager(store, runtimeStateManager);
        queueManager.playQueue(tracks, currentIndex, 0L);
        store.clearRecords();
        return queueManager;
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return new PlaybackQueueManager(
                store,
                new NoopQueuePlaybackActions(),
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

        @Override
        public void stopAndClear() {
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

    private static final class FakeCompletionBoundary
            implements PlaybackQueueCompletionOwner.CompletionBoundary {
        private final List<String> events;

        private FakeCompletionBoundary(List<String> events) {
            this.events = events;
        }

        @Override
        public void stopAndClear() {
            events.add("stopAndClear");
        }

        @Override
        public void prepareCurrent(boolean playWhenReady) {
            events.add("prepareCurrent:" + playWhenReady);
        }

        @Override
        public void stopAtEndOfQueue() {
            events.add("stopAtEnd");
        }

        @Override
        public void skipToNext() {
            events.add("skipNext");
        }

        @Override
        public void prepareStopAndClearFallbackState() {
            events.add("prepareStopAndClearFallback");
        }
    }
}
