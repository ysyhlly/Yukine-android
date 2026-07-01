package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackCurrentTrackReplacementOwnerTest {
    @Test
    public void delegatesCurrentReplacementAndSchedulesRecovery() {
        List<String> events = new ArrayList<>();
        Track replacement = track(7L);
        PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                new PlaybackQueueManager.CurrentTrackReplacementRecovery(replacement, 4200L, true);
        FakeReplacementAction action = new FakeReplacementAction(events, recovery);
        PlaybackCurrentTrackReplacementOwner owner = new PlaybackCurrentTrackReplacementOwner(
                action::replaceCurrentTrackAndResume,
                recorded -> events.add("record:" + recorded.getRestoredPositionMs()),
                playWhenReady -> events.add("schedule:" + playWhenReady)
        );

        owner.replaceCurrentTrackAndResume(replacement, 1800L);

        assertSame(replacement, action.replacement);
        assertEquals(1800L, action.positionMs);
        assertEquals(
                Arrays.asList(
                        "replace:7@1800",
                        "record:4200",
                        "schedule:true"
                ),
                events
        );
    }

    @Test
    public void skipsRecoveryWorkWhenReplacementDoesNotNeedRecovery() {
        List<String> events = new ArrayList<>();
        FakeReplacementAction action = new FakeReplacementAction(events, null);
        PlaybackCurrentTrackReplacementOwner owner = new PlaybackCurrentTrackReplacementOwner(
                action::replaceCurrentTrackAndResume,
                recorded -> events.add("record"),
                playWhenReady -> events.add("schedule")
        );

        owner.replaceCurrentTrackAndResume(track(8L), 2200L);

        assertEquals(Collections.singletonList("replace:8@2200"), events);
    }

    @Test
    public void ignoresMissingDependencies() {
        List<String> events = new ArrayList<>();
        Track replacement = track(9L);
        PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                new PlaybackQueueManager.CurrentTrackReplacementRecovery(replacement, 3300L, false);
        PlaybackCurrentTrackReplacementOwner missingAction = new PlaybackCurrentTrackReplacementOwner(
                null,
                recorded -> events.add("record"),
                playWhenReady -> events.add("schedule")
        );
        FakeReplacementAction action = new FakeReplacementAction(events, recovery);
        PlaybackCurrentTrackReplacementOwner missingRecoveryHandlers = new PlaybackCurrentTrackReplacementOwner(
                action::replaceCurrentTrackAndResume,
                null,
                null
        );

        missingAction.replaceCurrentTrackAndResume(replacement, 0L);
        missingRecoveryHandlers.replaceCurrentTrackAndResume(replacement, 0L);

        assertEquals(Collections.singletonList("replace:9@0"), events);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierSkipsReplacement() {
        List<String> events = new ArrayList<>();
        PlaybackCurrentTrackReplacementOwner owner =
                PlaybackCurrentTrackReplacementOwner.fromPlaybackQueueManager(
                        null,
                        recorded -> events.add("record"),
                        playWhenReady -> events.add("schedule")
                );

        owner.replaceCurrentTrackAndResume(track(10L), 1200L);

        assertEquals(Collections.emptyList(), events);
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakeReplacementAction {
        private final List<String> events;
        private final PlaybackQueueManager.CurrentTrackReplacementRecovery recovery;
        private Track replacement;
        private long positionMs;

        private FakeReplacementAction(
                List<String> events,
                PlaybackQueueManager.CurrentTrackReplacementRecovery recovery
        ) {
            this.events = events;
            this.recovery = recovery;
        }

        public PlaybackQueueManager.CurrentTrackReplacementRecovery replaceCurrentTrackAndResume(
                Track replacement,
                long positionMs
        ) {
            this.replacement = replacement;
            this.positionMs = positionMs;
            events.add("replace:" + replacement.id + "@" + positionMs);
            return recovery;
        }
    }
}
