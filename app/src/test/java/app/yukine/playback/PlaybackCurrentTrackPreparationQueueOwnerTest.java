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

public class PlaybackCurrentTrackPreparationQueueOwnerTest {
    @Test
    public void delegatesQueuePreparationToCurrentQueueOperations() {
        List<String> events = new ArrayList<>();
        FakeQueueOperations operations = new FakeQueueOperations(events, 3200L);
        PlaybackCurrentTrackPreparationQueueOwner owner = new PlaybackCurrentTrackPreparationQueueOwner(
                () -> operations
        );
        Track track = track(7L);

        owner.replaceCurrentQueueTrack(track);
        long positionMs = owner.restoredPositionFor(track);
        PlaybackQueueManager.QueuePreparation queuePreparation = owner.queuePreparationForNewPlayer();
        owner.consumeRestoredPositionAfterPrepare(4300L);

        assertEquals(3200L, positionMs);
        assertEquals(track.id, queuePreparation.getCurrentTrack().id);
        assertEquals(2, queuePreparation.getStartIndex());
        assertEquals(1, queuePreparation.getMirroredQueueTracks().size());
        assertEquals(track.id, queuePreparation.getMirroredQueueTracks().get(0).id);
        assertEquals(
                Arrays.asList(
                        "replace:7",
                        "position:7",
                        "queuePreparation",
                        "consume:4300"
                ),
                events
        );
    }

    @Test
    public void ignoresMissingQueueOperations() {
        PlaybackCurrentTrackPreparationQueueOwner owner = new PlaybackCurrentTrackPreparationQueueOwner(
                () -> null
        );

        owner.replaceCurrentQueueTrack(track(8L));

        assertEquals(0L, owner.restoredPositionFor(track(8L)));
        assertEquals(null, owner.queuePreparationForNewPlayer().getCurrentTrack());
        owner.consumeRestoredPositionAfterPrepare(5100L);
    }

    @Test
    public void ignoresMissingQueueOperationsSupplier() {
        PlaybackCurrentTrackPreparationQueueOwner owner = new PlaybackCurrentTrackPreparationQueueOwner(
                null
        );

        owner.replaceCurrentQueueTrack(track(9L));

        assertEquals(0L, owner.restoredPositionFor(track(9L)));
        assertEquals(null, owner.queuePreparationForNewPlayer().getCurrentTrack());
        owner.consumeRestoredPositionAfterPrepare(6100L);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierSkipsQueueOperations() {
        PlaybackCurrentTrackPreparationQueueOwner owner =
                PlaybackCurrentTrackPreparationQueueOwner.fromPlaybackQueueManager(null);

        owner.replaceCurrentQueueTrack(track(10L));

        assertEquals(0L, owner.restoredPositionFor(track(10L)));
        assertEquals(null, owner.queuePreparationForNewPlayer().getCurrentTrack());
        owner.consumeRestoredPositionAfterPrepare(7100L);
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "streaming:netease:" + id);
    }

    private static final class FakeQueueOperations implements PlaybackCurrentTrackPreparationQueueOwner.QueueOperations {
        private final List<String> events;
        private final long positionMs;
        private Track lastReplacedTrack;

        private FakeQueueOperations(List<String> events, long positionMs) {
            this.events = events;
            this.positionMs = positionMs;
        }

        @Override
        public void replaceCurrentQueueTrack(Track track) {
            events.add("replace:" + track.id);
            lastReplacedTrack = track;
        }

        @Override
        public long restoredPositionFor(Track track) {
            events.add("position:" + track.id);
            return positionMs;
        }

        @Override
        public PlaybackQueueManager.QueuePreparation queuePreparationForNewPlayer() {
            events.add("queuePreparation");
            return new PlaybackQueueManager.QueuePreparation(
                    lastReplacedTrack,
                    2,
                    Collections.singletonList(lastReplacedTrack)
            );
        }

        @Override
        public void consumeRestoredPositionAfterPrepare(long startPositionMs) {
            events.add("consume:" + startPositionMs);
        }
    }
}
