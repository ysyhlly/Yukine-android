package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class PlaybackCurrentTrackReplacementOwnerTest {
    @Test
    public void delegatesCurrentReplacementAndSchedulesRecovery() {
        List<String> events = new ArrayList<>();
        PlaybackQueueManager queueManager = queueManagerWithCurrent(track(1L, "original"));
        PlaybackCurrentTrackReplacementOwner owner =
                new PlaybackCurrentTrackReplacementOwner(
                        queueManager,
                        (track, restoredPositionMs) -> events.add("record:" + restoredPositionMs),
                        () -> events.add("schedule")
                );

        owner.replaceCurrentTrackAndResume(track(7L, "replacement"), 1800L);

        assertEquals(
                Arrays.asList(
                        "record:0",
                        "schedule"
                ),
                events
        );
        assertEquals(7L, queueManager.queueStateSnapshot().getCurrentTrack().id);
    }

    @Test
    public void skipsRecoveryWorkWhenReplacementDoesNotNeedRecovery() {
        List<String> events = new ArrayList<>();
        Track current = track(8L, "same");
        PlaybackQueueManager queueManager = queueManagerWithCurrent(current);
        PlaybackCurrentTrackReplacementOwner owner =
                new PlaybackCurrentTrackReplacementOwner(
                        queueManager,
                        (track, restoredPositionMs) -> events.add("record"),
                        () -> events.add("schedule")
                );

        owner.replaceCurrentTrackAndResume(track(8L, "same"), 2200L);

        assertEquals(Collections.emptyList(), events);
        assertEquals(8L, queueManager.queueStateSnapshot().getCurrentTrack().id);
    }

    @Test
    public void ignoresMissingDependencies() {
        List<String> events = new ArrayList<>();
        PlaybackQueueManager queueManager = queueManagerWithCurrent(track(9L, "original"));
        PlaybackCurrentTrackReplacementOwner missingRecoveryHandlers =
                new PlaybackCurrentTrackReplacementOwner(
                        queueManager,
                        null,
                        null
                );

        missingRecoveryHandlers.replaceCurrentTrackAndResume(track(10L, "replacement"), 0L);

        assertEquals(Collections.emptyList(), events);
        assertEquals(10L, queueManager.queueStateSnapshot().getCurrentTrack().id);
    }

    @Test
    public void missingPlaybackQueueManagerSkipsReplacement() {
        List<String> events = new ArrayList<>();
        PlaybackCurrentTrackReplacementOwner missingManager =
                new PlaybackCurrentTrackReplacementOwner(
                        null,
                        (track, restoredPositionMs) -> events.add("record"),
                        () -> events.add("schedule")
                );

        missingManager.replaceCurrentTrackAndResume(track(11L, "replacement"), 1200L);

        assertEquals(Collections.emptyList(), events);
    }

    private static PlaybackQueueManager queueManagerWithCurrent(Track current) {
        PlaybackQueueManager queueManager = new PlaybackQueueManager(
                new FakeQueueStore(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null
        );
        queueManager.playQueue(Collections.singletonList(current), 0, 0L);
        return queueManager;
    }

    private static Track track(long id, String dataPathSuffix) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                null,
                "streaming:netease:" + dataPathSuffix
        );
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private List<Track> savedTracks = Collections.emptyList();
        private int savedIndex = -1;

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

    private static final class NoopQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
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
