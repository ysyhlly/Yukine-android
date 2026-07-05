package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class PlaybackBufferedProgressOwnerTest {
    @Test
    public void returnsBufferedProgressClampedToDuration() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> 2500L
        );
        PlaybackBufferedProgressOwner overBuffered = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> 5000L
        );

        assertEquals(0.5f, owner.bufferedProgress(5000L), 0.001f);
        assertEquals(1.0f, overBuffered.bufferedProgress(3000L), 0.001f);
    }

    @Test
    public void usesCurrentPositionWhenItIsAheadOfBufferedPosition() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 3000L,
                () -> 1200L
        );

        assertEquals(0.75f, owner.bufferedProgress(4000L), 0.001f);
    }

    @Test
    public void returnsZeroWhenDurationIsMissing() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> 2500L
        );

        assertEquals(0f, owner.bufferedProgress(0L), 0.001f);
    }

    @Test
    public void constructorRequiresPlaybackPositionProvider() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new PlaybackBufferedProgressOwner(
                        null,
                        () -> 2500L
                )
        );

        assertEquals("playbackPositionProvider", error.getMessage());
    }

    @Test
    public void constructorRequiresBufferedPositionProvider() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new PlaybackBufferedProgressOwner(
                        () -> 1000L,
                        null
                )
        );

        assertEquals("bufferedPositionProvider", error.getMessage());
    }

    @Test
    public void returnsZeroWhenBufferedPositionCannotBeRead() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> {
                    throw new IllegalStateException("player released");
                }
        );

        assertEquals(0f, owner.bufferedProgress(4000L), 0.001f);
    }
}
