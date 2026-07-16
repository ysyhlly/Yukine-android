package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import app.yukine.model.Track;

public class PlaybackSourceHealthFeedbackOwnerTest {
    @Test
    public void deduplicatesPauseResumeButRecordsTheSourceAgainAfterAnotherTrack() {
        List<Long> recorded = new ArrayList<>();
        PlaybackSourceHealthFeedbackOwner owner = new PlaybackSourceHealthFeedbackOwner(
                Runnable::run,
                track -> recorded.add(track.id)
        );
        Track first = track(1L, "streaming:netease:first");
        Track second = track(2L, "streaming:qqmusic:second");

        owner.recordFirstAudioOutput(first);
        owner.recordFirstAudioOutput(first);
        owner.recordFirstAudioOutput(second);
        owner.recordFirstAudioOutput(first);

        assertEquals(List.of(1L, 2L, 1L), recorded);
    }

    private static Track track(long id, String dataPath) {
        return new Track(id, "Track", "Artist", "Album", 0L, Uri.EMPTY, dataPath);
    }
}
