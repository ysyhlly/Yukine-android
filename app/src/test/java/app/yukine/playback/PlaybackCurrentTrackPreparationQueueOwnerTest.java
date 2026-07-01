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
    public void delegatesQueuePreparationToSuppliedActions() {
        List<String> events = new ArrayList<>();
        Track[] lastReplacedTrack = new Track[1];
        PlaybackCurrentTrackPreparationQueueOwner owner = new PlaybackCurrentTrackPreparationQueueOwner(
                track -> {
                    events.add("replace:" + track.id);
                    lastReplacedTrack[0] = track;
                },
                track -> {
                    events.add("position:" + track.id);
                    return 3200L;
                },
                () -> {
                    events.add("queuePreparation");
                    return new PlaybackQueueManager.QueuePreparation(
                            lastReplacedTrack[0],
                            2,
                            Collections.singletonList(lastReplacedTrack[0])
                    );
                },
                startPositionMs -> events.add("consume:" + startPositionMs)
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
    public void ignoresMissingQueueActions() {
        PlaybackCurrentTrackPreparationQueueOwner owner = new PlaybackCurrentTrackPreparationQueueOwner(
                null,
                null,
                null,
                null
        );

        owner.replaceCurrentQueueTrack(track(8L));

        assertEquals(0L, owner.restoredPositionFor(track(8L)));
        assertEquals(null, owner.queuePreparationForNewPlayer().getCurrentTrack());
        owner.consumeRestoredPositionAfterPrepare(5100L);
    }

    @Test
    public void missingPlaybackQueueManagerSupplierSkipsQueueActions() {
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
}
