package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;

import org.junit.Test;

public class PlaybackPlayerStateOwnerTest {
    @Test
    public void returnsNormalizedPlayerStateWhenPlayerIsReadable() {
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> new FakePlayerStateOperations(true, 2500L, 9000L)
        );

        assertEquals(true, owner.isPlaying());
        assertEquals(2500L, owner.positionMs());
        assertEquals(9000L, owner.durationMs());
    }

    @Test
    public void clampsNegativePositionAndUnsetDuration() {
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> new FakePlayerStateOperations(false, -100L, C.TIME_UNSET)
        );

        assertEquals(false, owner.isPlaying());
        assertEquals(0L, owner.positionMs());
        assertEquals(0L, owner.durationMs());
    }

    @Test
    public void returnsEmptyStateWhenPlayerIsMissingOrReleased() {
        PlaybackPlayerStateOwner missingProvider = new PlaybackPlayerStateOwner(null);
        PlaybackPlayerStateOwner missingPlayer = new PlaybackPlayerStateOwner(() -> null);
        PlaybackPlayerStateOwner throwing = new PlaybackPlayerStateOwner(
                () -> new ThrowingPlayerStateOperations()
        );

        assertEmptyState(missingProvider);
        assertEmptyState(missingPlayer);
        assertEmptyState(throwing);
    }

    private static void assertEmptyState(PlaybackPlayerStateOwner owner) {
        assertEquals(false, owner.isPlaying());
        assertEquals(0L, owner.positionMs());
        assertEquals(0L, owner.durationMs());
    }

    private static class FakePlayerStateOperations
            implements PlaybackPlayerStateOwner.PlayerStateOperations {
        private final boolean playing;
        private final long positionMs;
        private final long durationMs;

        private FakePlayerStateOperations(boolean playing, long positionMs, long durationMs) {
            this.playing = playing;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public long currentPositionMs() {
            return positionMs;
        }

        @Override
        public long durationMs() {
            return durationMs;
        }
    }

    private static final class ThrowingPlayerStateOperations extends FakePlayerStateOperations {
        private ThrowingPlayerStateOperations() {
            super(true, 1000L, 5000L);
        }

        @Override
        public boolean isPlaying() {
            throw new IllegalStateException("released");
        }

        @Override
        public long currentPositionMs() {
            throw new IllegalStateException("released");
        }

        @Override
        public long durationMs() {
            throw new IllegalStateException("released");
        }
    }
}
