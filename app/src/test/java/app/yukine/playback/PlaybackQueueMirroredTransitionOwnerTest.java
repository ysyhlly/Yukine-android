package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import androidx.media3.common.Player;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaybackQueueMirroredTransitionOwnerTest {
    @Test
    public void delegatesApplyAndPrepareToCurrentOperations() {
        List<String> events = new ArrayList<>();
        FakeMirroredTransitionActions actions = new FakeMirroredTransitionActions(
                events,
                new PlaybackQueueManager.MirroredTransitionResult(3, true)
        );
        PlaybackQueueMirroredTransitionOwner owner = new PlaybackQueueMirroredTransitionOwner(
                actions::applyMirroredTransitionIndex,
                actions::prepareMirroredTransitionPlaybackState,
                () -> events.add("applyVolume")
        );

        PlaybackQueueManager.MirroredTransitionResult result =
                owner.applyMirroredTransitionIndex(5, true);
        owner.prepareMirroredTransitionPlaybackState();

        assertEquals(3, result.getCompletedIndex());
        assertEquals(true, result.getStopAfterAutomaticAdvance());
        assertEquals(
                java.util.Arrays.asList(
                        "apply:5:true",
                        "prepare",
                        "applyVolume"
                ),
                events
        );
    }

    @Test
    public void preparesBeforeApplyingCurrentTrackVolume() {
        List<String> events = new ArrayList<>();
        FakeMirroredTransitionActions actions = new FakeMirroredTransitionActions(events, null);
        PlaybackQueueMirroredTransitionOwner owner = new PlaybackQueueMirroredTransitionOwner(
                actions::applyMirroredTransitionIndex,
                actions::prepareMirroredTransitionPlaybackState,
                () -> events.add("applyVolume")
        );

        owner.prepareMirroredTransitionPlaybackState();

        assertEquals(java.util.Arrays.asList("prepare", "applyVolume"), events);
    }

    @Test
    public void returnsNullAndIgnoresPrepareWhenOperationsAreMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredTransitionOwner owner =
                new PlaybackQueueMirroredTransitionOwner(null, null, () -> events.add("applyVolume"));

        assertNull(owner.applyMirroredTransitionIndex(2, false));
        owner.prepareMirroredTransitionPlaybackState();
        assertEquals(java.util.Collections.emptyList(), events);
    }

    @Test
    public void mediaItemTransitionReasonIsConvertedInsideOwner() {
        List<String> events = new ArrayList<>();
        FakeMirroredTransitionActions actions = new FakeMirroredTransitionActions(events, null);
        PlaybackQueueMirroredTransitionOwner owner = new PlaybackQueueMirroredTransitionOwner(
                actions::applyMirroredTransitionIndex,
                actions::prepareMirroredTransitionPlaybackState
        );

        owner.applyMirroredTransitionReason(5, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
        owner.applyMirroredTransitionReason(6, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
        owner.applyMirroredTransitionReason(7, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

        assertEquals(
                java.util.Arrays.asList(
                        "apply:5:true",
                        "apply:6:false",
                        "apply:7:false"
                ),
                events
        );
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

    @Test
    public void missingPlaybackQueueManagerSupplierReturnsNullAndIgnoresPrepare() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredTransitionOwner owner =
                PlaybackQueueMirroredTransitionOwner.fromPlaybackQueueManager(
                        null,
                        () -> events.add("applyVolume")
                );

        assertNull(owner.applyMirroredTransitionIndex(4, true));
        owner.prepareMirroredTransitionPlaybackState();
        assertEquals(java.util.Collections.emptyList(), events);
    }

    private static final class FakeMirroredTransitionActions {
        private final List<String> events;
        private final PlaybackQueueManager.MirroredTransitionResult result;

        private FakeMirroredTransitionActions(
                List<String> events,
                PlaybackQueueManager.MirroredTransitionResult result
        ) {
            this.events = events;
            this.result = result;
        }

        public PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
                int nextIndex,
                boolean automaticAdvance
        ) {
            events.add("apply:" + nextIndex + ":" + automaticAdvance);
            return result;
        }

        public boolean prepareMirroredTransitionPlaybackState() {
            events.add("prepare");
            return true;
        }
    }
}
