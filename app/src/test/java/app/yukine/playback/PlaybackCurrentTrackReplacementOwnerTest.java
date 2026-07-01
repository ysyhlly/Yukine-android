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
        FakeReplacementOperations operations = new FakeReplacementOperations(events, recovery);
        PlaybackCurrentTrackReplacementOwner owner = new PlaybackCurrentTrackReplacementOwner(
                () -> operations,
                recorded -> events.add("record:" + recorded.getRestoredPositionMs()),
                playWhenReady -> events.add("schedule:" + playWhenReady)
        );

        owner.replaceCurrentTrackAndResume(replacement, 1800L);

        assertSame(replacement, operations.replacement);
        assertEquals(1800L, operations.positionMs);
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
        PlaybackCurrentTrackReplacementOwner owner = new PlaybackCurrentTrackReplacementOwner(
                () -> new FakeReplacementOperations(events, null),
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
        PlaybackCurrentTrackReplacementOwner missingSupplier = new PlaybackCurrentTrackReplacementOwner(
                null,
                recorded -> events.add("record"),
                playWhenReady -> events.add("schedule")
        );
        PlaybackCurrentTrackReplacementOwner missingOperations = new PlaybackCurrentTrackReplacementOwner(
                () -> null,
                recorded -> events.add("record"),
                playWhenReady -> events.add("schedule")
        );
        PlaybackCurrentTrackReplacementOwner missingRecoveryHandlers = new PlaybackCurrentTrackReplacementOwner(
                () -> new FakeReplacementOperations(events, recovery),
                null,
                null
        );

        missingSupplier.replaceCurrentTrackAndResume(replacement, 0L);
        missingOperations.replaceCurrentTrackAndResume(replacement, 0L);
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

    private static final class FakeReplacementOperations
            implements PlaybackCurrentTrackReplacementOwner.CurrentTrackReplacementOperations {
        private final List<String> events;
        private final PlaybackQueueManager.CurrentTrackReplacementRecovery recovery;
        private Track replacement;
        private long positionMs;

        private FakeReplacementOperations(
                List<String> events,
                PlaybackQueueManager.CurrentTrackReplacementRecovery recovery
        ) {
            this.events = events;
            this.recovery = recovery;
        }

        @Override
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
