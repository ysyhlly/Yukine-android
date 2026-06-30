package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;
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

    private static PlaybackPlayHistoryRecorder recorder(FakeHistorySink historySink) {
        return new PlaybackPlayHistoryRecorder(historySink, new PlaybackTransitionStateManager());
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
}
