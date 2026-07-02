package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.playback.manager.PlaybackQueueManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackCrossfadeStateOwnerTest {
    @Test
    public void delegatesCrossfadeStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackCrossfadeStateOwner owner = new PlaybackCrossfadeStateOwner(
                () -> {
                    events.add("fadeOut");
                    return false;
                },
                () -> {
                    events.add("player");
                    return true;
                },
                () -> {
                    events.add("playing");
                    return true;
                },
                () -> {
                    events.add("repeat");
                    return 2;
                },
                () -> {
                    events.add("queue");
                    return queueStateSnapshot(1, 3);
                },
                () -> {
                    events.add("volume");
                    return 0.75f;
                }
        );

        assertFalse(owner.fadeOutAdvancing());
        assertTrue(owner.playerAvailable());
        assertTrue(owner.isPlaying());
        assertTrue(owner.canCrossfadeAdvance());
        assertEquals(0.75f, owner.baseVolume(), 0.001f);
        assertEquals(
                java.util.Arrays.asList(
                        "fadeOut",
                        "player",
                        "playing",
                        "queue",
                        "repeat",
                        "volume"
                ),
                events
        );
    }

    @Test
    public void crossfadeAdvancePolicyUsesQueueSnapshotAndRepeatMode() {
        PlaybackCrossfadeStateOwner missingQueue = owner(null, PlaybackRepeatMode.REPEAT_ALL);
        PlaybackCrossfadeStateOwner singleTrack = owner(queueStateSnapshot(0, 1), PlaybackRepeatMode.REPEAT_ALL);
        PlaybackCrossfadeStateOwner repeatOffBeforeEnd = owner(
                queueStateSnapshot(0, 2),
                PlaybackRepeatMode.REPEAT_OFF
        );
        PlaybackCrossfadeStateOwner repeatOffAtEnd = owner(
                queueStateSnapshot(1, 2),
                PlaybackRepeatMode.REPEAT_OFF
        );
        PlaybackCrossfadeStateOwner repeatAllAtEnd = owner(
                queueStateSnapshot(1, 2),
                PlaybackRepeatMode.REPEAT_ALL
        );

        assertFalse(missingQueue.canCrossfadeAdvance());
        assertFalse(singleTrack.canCrossfadeAdvance());
        assertTrue(repeatOffBeforeEnd.canCrossfadeAdvance());
        assertFalse(repeatOffAtEnd.canCrossfadeAdvance());
        assertTrue(repeatAllAtEnd.canCrossfadeAdvance());
    }

    private static PlaybackCrossfadeStateOwner owner(
            PlaybackQueueManager.QueueStateSnapshot snapshot,
            int repeatMode
    ) {
        return new PlaybackCrossfadeStateOwner(
                () -> false,
                () -> true,
                () -> true,
                () -> repeatMode,
                snapshot == null ? null : () -> snapshot,
                () -> 1.0f
        );
    }

    private static PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot(
            int currentIndex,
            int queueSize
    ) {
        return new PlaybackQueueManager.QueueStateSnapshot(
                null,
                currentIndex,
                queueSize
        );
    }
}
