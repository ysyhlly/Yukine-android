package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;
import app.yukine.playback.manager.PlaybackTransitionStateManager;

public final class PlaybackPlayHistoryRecorderTest {
    @Test
    public void recordsTrackWhenPlaybackStarts() {
        FakeHistorySink historySink = new FakeHistorySink();
        PlaybackPlayHistoryRecorder recorder = recorder(historySink);

        recorder.recordIfPlaybackStarted(true, track(1L));

        assertEquals(list(1L), historySink.markedTrackIds);
    }

    @Test
    public void ignoresPausedOrMissingTrack() {
        FakeHistorySink historySink = new FakeHistorySink();
        PlaybackPlayHistoryRecorder recorder = recorder(historySink);

        recorder.recordIfPlaybackStarted(false, track(1L));
        recorder.recordIfPlaybackStarted(true, null);

        assertEquals(list(), historySink.markedTrackIds);
    }

    @Test
    public void doesNotRecordSameTrackTwiceUntilTrackChanges() {
        FakeHistorySink historySink = new FakeHistorySink();
        PlaybackPlayHistoryRecorder recorder = recorder(historySink);

        recorder.recordIfPlaybackStarted(true, track(1L));
        recorder.recordIfPlaybackStarted(true, track(1L));
        recorder.recordIfPlaybackStarted(true, track(2L));

        assertEquals(list(1L, 2L), historySink.markedTrackIds);
    }

    @Test
    public void recordIfPlaybackStartedActionUsesRecorderAndLatestState() {
        FakeHistorySink historySink = new FakeHistorySink();
        AtomicBoolean playWhenReady = new AtomicBoolean(false);
        PlaybackQueueManager queueManager = playbackQueueManager();
        queueManager.playQueue(Collections.singletonList(track(1L)), 0, -1L);
        PlaybackPlayHistoryRecorder recorder = recorder(historySink);
        Runnable action = PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(
                recorder,
                playWhenReady::get,
                queueStateOwner(queueManager)
        );

        action.run();
        playWhenReady.set(true);
        action.run();
        queueManager.playQueue(Collections.singletonList(track(2L)), 0, -1L);
        action.run();

        assertEquals(list(1L, 2L), historySink.markedTrackIds);
    }

    @Test
    public void recordIfPlaybackStartedActionIgnoresMissingRecorder() {
        AtomicBoolean playWhenReady = new AtomicBoolean(true);
        PlaybackQueueManager queueManager = playbackQueueManager();
        queueManager.playQueue(Collections.singletonList(track(1L)), 0, -1L);
        Runnable action = PlaybackPlayHistoryRecorder.recordIfPlaybackStartedAction(
                null,
                playWhenReady::get,
                queueStateOwner(queueManager)
        );

        action.run();
    }

    private static PlaybackPlayHistoryRecorder recorder(FakeHistorySink historySink) {
        return new PlaybackPlayHistoryRecorder(historySink, new PlaybackTransitionStateManager());
    }

    private static PlaybackQueueStateOwner queueStateOwner(PlaybackQueueManager queueManager) {
        return new PlaybackQueueStateOwner(() -> queueManager);
    }

    private static PlaybackQueueManager playbackQueueManager() {
        return new PlaybackQueueManager(
                new FakeQueueStore(),
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                null,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                new PlaybackTransitionStateManager(),
                new Random(1L)
        );
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse("file:///music/" + id + ".flac"),
                "local:" + id
        );
    }

    private static List<Long> list(long... ids) {
        ArrayList<Long> values = new ArrayList<>();
        for (long id : ids) {
            values.add(id);
        }
        return values;
    }

    private static final class FakeHistorySink implements PlaybackPlayHistoryRecorder.HistorySink {
        private final List<Long> markedTrackIds = new ArrayList<>();

        @Override
        public void markPlayed(long trackId) {
            markedTrackIds.add(trackId);
        }
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
