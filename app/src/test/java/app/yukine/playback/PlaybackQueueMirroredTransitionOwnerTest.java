package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaybackQueueMirroredTransitionOwnerTest {
    @Test
    public void delegatesApplyAndPrepareToCurrentOperations() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredTransitionOwner owner = new PlaybackQueueMirroredTransitionOwner(
                () -> new PlaybackQueueMirroredTransitionOwner.MirroredTransitionOperations() {
                    @Override
                    public PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
                            int nextIndex,
                            boolean automaticAdvance
                    ) {
                        events.add("apply:" + nextIndex + ":" + automaticAdvance);
                        return new PlaybackQueueManager.MirroredTransitionResult(3, true);
                    }

                    @Override
                    public void prepareMirroredTransitionPlaybackState() {
                        events.add("prepare");
                    }
                },
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
        PlaybackQueueMirroredTransitionOwner owner = new PlaybackQueueMirroredTransitionOwner(
                () -> new PlaybackQueueMirroredTransitionOwner.MirroredTransitionOperations() {
                    @Override
                    public PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
                            int nextIndex,
                            boolean automaticAdvance
                    ) {
                        return null;
                    }

                    @Override
                    public void prepareMirroredTransitionPlaybackState() {
                        events.add("prepare");
                    }
                },
                () -> events.add("applyVolume")
        );

        owner.prepareMirroredTransitionPlaybackState();

        assertEquals(java.util.Arrays.asList("prepare", "applyVolume"), events);
    }

    @Test
    public void returnsNullAndIgnoresPrepareWhenOperationsAreMissing() {
        List<String> events = new ArrayList<>();
        PlaybackQueueMirroredTransitionOwner owner =
                new PlaybackQueueMirroredTransitionOwner(() -> null, () -> events.add("applyVolume"));

        assertNull(owner.applyMirroredTransitionIndex(2, false));
        owner.prepareMirroredTransitionPlaybackState();
        assertEquals(java.util.Collections.emptyList(), events);
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
}
