package app.yukine.playback;

import static app.yukine.playback.PlaybackRepeatMode.REPEAT_ALL;
import static app.yukine.playback.PlaybackRepeatMode.REPEAT_OFF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;
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

public class PlaybackQueueMirroredTransitionOwnerTest {
    @Test
    public void delegatesApplyAndPreparationToPlaybackQueueManager() {
        List<String> events = new ArrayList<>();
        FakeQueueStore store = new FakeQueueStore(events);
        RecordingStreamingRestoreProvider streamingRestoreProvider =
                new RecordingStreamingRestoreProvider(events);
        PlaybackQueueManager queueManager = queueManager(
                store,
                streamingRestoreProvider,
                REPEAT_OFF
        );
        queueManager.playQueue(
                Arrays.asList(track(1L), track(2L), track(3L), track(4L)),
                1,
                0L
        );
        store.clearRecords();
        events.clear();
        PlaybackQueueMirroredTransitionOwner owner =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> queueManager,
                        () -> events.add("applyVolume"),
                        null
                );

        PlaybackQueueManager.MirroredTransitionResult result =
                owner.applyMirroredTransitionReason(
                        3,
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                );

        assertEquals(1, result.getCompletedIndex());
        assertEquals(false, result.getStopAfterAutomaticAdvance());
        assertEquals(3, queueManager.queueStateSnapshot().getCurrentIndex());
        assertEquals(
                Arrays.asList(
                        "position:4:0",
                        "restore:streaming:netease:4",
                        "save:3",
                        "applyVolume"
                ),
                events
        );
    }

    @Test
    public void preparesBeforeApplyingCurrentTrackVolume() {
        List<String> events = new ArrayList<>();
        FakeQueueStore store = new FakeQueueStore(events);
        RecordingStreamingRestoreProvider streamingRestoreProvider =
                new RecordingStreamingRestoreProvider(events);
        PlaybackQueueManager queueManager = queueManager(store, streamingRestoreProvider, REPEAT_ALL);
        queueManager.playQueue(Arrays.asList(track(8L), track(9L)), 0, 0L);
        store.clearRecords();
        events.clear();
        PlaybackQueueMirroredTransitionOwner owner =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> queueManager,
                        () -> events.add("applyVolume"),
                        null
                );

        PlaybackQueueManager.MirroredTransitionResult result =
                owner.applyMirroredTransitionReason(
                        1,
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                );

        assertEquals(false, result.getStopAfterAutomaticAdvance());
        assertEquals(
                Arrays.asList(
                        "position:9:0",
                        "restore:streaming:netease:9",
                        "save:1",
                        "applyVolume"
                ),
                events
        );
    }

    @Test
    public void returnsNullAndIgnoresVolumeWhenOperationsAreMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredTransitionOwner missingManagerProvider =
                new PlaybackQueueMirroredTransitionOwner(
                        null,
                        () -> events.add("applyVolume"),
                        null
                );
        PlaybackQueueMirroredTransitionOwner missingManager =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> null,
                        () -> events.add("applyVolume"),
                        null
                );

        assertNull(missingManagerProvider.applyMirroredTransitionReason(
                2,
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        ));
        assertNull(missingManager.applyMirroredTransitionReason(
                3,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        ));

        assertEquals(Collections.emptyList(), events);
    }

    @Test
    public void mediaItemTransitionReasonIsConvertedInsideOwner() {
        PlaybackQueueManager queueManager = queueManager(
                new FakeQueueStore(new ArrayList<>()),
                new RecordingStreamingRestoreProvider(new ArrayList<>()),
                REPEAT_OFF
        );
        queueManager.playQueue(Arrays.asList(track(1L), track(2L), track(3L)), 0, 0L);
        PlaybackQueueMirroredTransitionOwner owner =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> queueManager,
                        null,
                        null
                );

        PlaybackQueueManager.MirroredTransitionResult automaticResult =
                owner.applyMirroredTransitionReason(2, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
        PlaybackQueueManager.MirroredTransitionResult seekResult =
                owner.applyMirroredTransitionReason(1, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
        PlaybackQueueManager.MirroredTransitionResult playlistChangedResult =
                owner.applyMirroredTransitionReason(2, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

        assertEquals(0, automaticResult.getCompletedIndex());
        assertEquals(true, automaticResult.getStopAfterAutomaticAdvance());
        assertEquals(0, seekResult.getCompletedIndex());
        assertEquals(false, seekResult.getStopAfterAutomaticAdvance());
        assertEquals(1, playlistChangedResult.getCompletedIndex());
        assertEquals(false, playlistChangedResult.getStopAfterAutomaticAdvance());
        assertEquals(2, queueManager.queueStateSnapshot().getCurrentIndex());
    }

    @Test
    public void canApplyMirroredTransitionRequiresMirroredNonEmptyQueue() {
        PlaybackQueueManager readyManager = queueManager(
                new FakeQueueStore(new ArrayList<>()),
                new RecordingStreamingRestoreProvider(new ArrayList<>()),
                REPEAT_OFF
        );
        readyManager.playQueue(Collections.singletonList(track(1L)), 0, 0L);
        PlaybackQueueManager emptyManager = queueManager(
                new FakeQueueStore(new ArrayList<>()),
                new RecordingStreamingRestoreProvider(new ArrayList<>()),
                REPEAT_OFF
        );
        PlaybackQueueMirroredTransitionOwner missingStateOwner =
                new PlaybackQueueMirroredTransitionOwner(null, null, null);
        PlaybackQueueMirroredTransitionOwner readyOwner =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> readyManager,
                        null,
                        () -> true
                );
        PlaybackQueueMirroredTransitionOwner notMirroredOwner =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> readyManager,
                        null,
                        () -> false
                );
        PlaybackQueueMirroredTransitionOwner emptyQueueOwner =
                new PlaybackQueueMirroredTransitionOwner(
                        () -> emptyManager,
                        null,
                        () -> true
                );

        assertFalse(missingStateOwner.canApplyMirroredTransition());
        assertTrue(readyOwner.canApplyMirroredTransition());
        assertFalse(notMirroredOwner.canApplyMirroredTransition());
        assertFalse(emptyQueueOwner.canApplyMirroredTransition());
    }

    @Test
    public void automaticMediaItemTransitionIsDetectedForRepeatOffStop() {
        assertEquals(
                true,
                PlaybackQueueMirroredTransitionOwner.isAutomaticMediaItemAdvance(
                        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                )
        );
        assertEquals(
                false,
                PlaybackQueueMirroredTransitionOwner.isAutomaticMediaItemAdvance(
                        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                )
        );
        assertEquals(
                false,
                PlaybackQueueMirroredTransitionOwner.isAutomaticMediaItemAdvance(
                        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                )
        );
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            RecordingStreamingRestoreProvider streamingRestoreProvider,
            int repeatMode
    ) {
        PlaybackRuntimeStateManager runtimeStateManager = new PlaybackRuntimeStateManager(
                new PlaybackRuntimeStateManager.StateProvider() {
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
                }
        );
        runtimeStateManager.setRepeatMode(repeatMode);
        return new PlaybackQueueManager(
                store,
                new NoopQueuePlaybackActions(),
                null,
                streamingRestoreProvider,
                new NoopMirroredQueuePlayer(),
                runtimeStateManager,
                null
        );
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
        private final List<String> events;
        private List<Track> savedTracks = Collections.emptyList();
        private int savedIndex = -1;

        private FakeQueueStore(List<String> events) {
            this.events = events;
        }

        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(savedTracks, savedIndex);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
            savedTracks = tracks == null ? Collections.emptyList() : new ArrayList<>(tracks);
            savedIndex = currentIndex;
            events.add("save:" + currentIndex);
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
            events.add("position:" + trackId + ":" + positionMs);
        }

        private void clearRecords() {
            events.clear();
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

    private static final class RecordingStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        private final List<String> events;

        private RecordingStreamingRestoreProvider(List<String> events) {
            this.events = events;
        }

        @Override
        public Track restoredTrackFor(Track track) {
            return track;
        }

        @Override
        public void restoreForDataPath(String dataPath) {
            events.add("restore:" + dataPath);
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
