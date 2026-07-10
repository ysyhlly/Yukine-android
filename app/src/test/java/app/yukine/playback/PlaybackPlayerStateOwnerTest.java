package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;
import androidx.media3.common.Player;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.LongSupplier;

public class PlaybackPlayerStateOwnerTest {
    @Test
    public void returnsNormalizedPlayerStateWhenPlayerIsReadable() {
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(true, 2500L, 9000L)
        );

        assertEquals(true, owner.isPlaying());
        assertEquals(2500L, owner.positionMs());
        assertEquals(9000L, owner.durationMs());
    }

    @Test
    public void clampsNegativePositionAndUnsetDuration() {
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(false, -100L, C.TIME_UNSET)
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
                PlaybackPlayerStateOwnerTest::throwingPlayer
        );

        assertEmptyState(missingProvider);
        assertEmptyState(missingPlayer);
        assertEmptyState(throwing);
    }

    @Test
    public void estimatesPlayingPositionWhenPlayerPositionStopsAdvancing() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(true, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        assertEquals(0L, owner.positionMs());
        clock.nowMs = 2_250L;
        assertEquals(1_250L, owner.positionMs());
        clock.nowMs = 3_000L;
        assertEquals(2_000L, owner.positionMs());
    }

    @Test
    public void resetPositionEstimateStartsAgainFromPlayerPosition() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(true, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        owner.positionMs();
        clock.nowMs = 3_000L;
        assertEquals(2_000L, owner.positionMs());

        owner.resetPositionEstimate();
        state.positionMs = 0L;
        clock.nowMs = 4_000L;
        assertEquals(0L, owner.positionMs());
    }

    @Test
    public void resetPositionEstimateDropsOldPausedPositionBeforeReplacingTheSource() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(false, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        owner.setPositionEstimate(4_200L);
        assertEquals(4_200L, owner.positionMs());

        owner.resetPositionEstimate();

        assertEquals(0L, owner.positionMs());
    }

    @Test
    public void pendingMediaItemTransitionDoesNotExposeOldTrackProgress() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(false, 4_200L, 9_000L);
        state.mediaItemIndex = 0;
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        assertEquals(4_200L, owner.positionMs());

        owner.beginMediaItemPositionTransition(1, 0L);

        // Queue state already points at song 2, while ExoPlayer still reports song 1.
        assertEquals(0L, owner.positionMs());
        assertEquals(0L, owner.sessionPositionMs());

        state.mediaItemIndex = 1;
        state.positionMs = 0L;
        assertEquals(0L, owner.positionMs());
    }

    @Test
    public void keepsEstimatedPositionWhenPausedPlayerReportsZero() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(true, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        owner.positionMs();
        clock.nowMs = 3_000L;
        assertEquals(2_000L, owner.positionMs());

        state.playing = false;
        state.positionMs = 0L;
        clock.nowMs = 3_500L;
        assertEquals(2_000L, owner.positionMs());
        clock.nowMs = 5_000L;
        assertEquals(2_000L, owner.positionMs());
    }

    @Test
    public void resumesProgressFromPausedEstimateWhenPlayerStillReportsZero() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(true, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        owner.positionMs();
        clock.nowMs = 3_000L;
        assertEquals(2_000L, owner.positionMs());
        state.playing = false;
        clock.nowMs = 3_500L;
        assertEquals(2_000L, owner.positionMs());

        state.playing = true;
        state.positionMs = 0L;
        clock.nowMs = 4_000L;
        assertEquals(2_000L, owner.positionMs());
        clock.nowMs = 4_750L;
        assertEquals(2_750L, owner.positionMs());
    }

    @Test
    public void explicitPositionEstimateSeedsSeekTargetWhenPlayerReportsZero() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(false, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        owner.setPositionEstimate(4_200L);

        assertEquals(4_200L, owner.positionMs());
        state.playing = true;
        clock.nowMs = 1_500L;
        assertEquals(4_200L, owner.positionMs());
        clock.nowMs = 2_000L;
        assertEquals(4_700L, owner.positionMs());
    }

    @Test
    public void sessionPositionReadsKnownEstimateWithoutAdvancingIt() {
        MutableClock clock = new MutableClock(1_000L);
        MutablePlayerState state = new MutablePlayerState(true, 0L, 9_000L);
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(state),
                clock
        );

        assertEquals(0L, owner.positionMs());
        clock.nowMs = 2_500L;

        assertEquals(0L, owner.sessionPositionMs());
        assertEquals(1_500L, owner.positionMs());
        clock.nowMs = 3_000L;
        assertEquals(1_500L, owner.sessionPositionMs());
    }

    private static void assertEmptyState(PlaybackPlayerStateOwner owner) {
        assertEquals(false, owner.isPlaying());
        assertEquals(0L, owner.positionMs());
        assertEquals(0L, owner.durationMs());
    }

    private static Player fakePlayer(boolean playing, long positionMs, long durationMs) {
        return fakePlayer(new MutablePlayerState(playing, positionMs, durationMs));
    }

    private static Player fakePlayer(MutablePlayerState state) {
        return playerProxy((proxy, method, args) -> {
            switch (method.getName()) {
                case "isPlaying":
                    return state.playing;
                case "getCurrentPosition":
                    return state.positionMs;
                case "getCurrentMediaItemIndex":
                    return state.mediaItemIndex;
                case "getDuration":
                    return state.durationMs;
                default:
                    return defaultValue(method);
            }
        });
    }

    private static Player throwingPlayer() {
        return playerProxy((proxy, method, args) -> {
            if ("isPlaying".equals(method.getName())
                    || "getCurrentPosition".equals(method.getName())
                    || "getDuration".equals(method.getName())) {
                throw new IllegalStateException("released");
            }
            return defaultValue(method);
        });
    }

    private static Player playerProxy(InvocationHandler handler) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                handler
        );
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Double.TYPE) {
            return 0.0d;
        }
        return null;
    }

    private static final class MutableClock implements LongSupplier {
        long nowMs;

        MutableClock(long nowMs) {
            this.nowMs = nowMs;
        }

        @Override
        public long getAsLong() {
            return nowMs;
        }
    }

    private static final class MutablePlayerState {
        boolean playing;
        long positionMs;
        long durationMs;
        int mediaItemIndex;

        MutablePlayerState(boolean playing, long positionMs, long durationMs) {
            this.playing = playing;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.mediaItemIndex = 0;
        }
    }
}
