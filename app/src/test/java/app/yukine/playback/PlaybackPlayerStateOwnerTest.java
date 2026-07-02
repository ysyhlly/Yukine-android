package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;
import androidx.media3.common.Player;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PlaybackPlayerStateOwnerTest {
    @Test
    public void returnsNormalizedPlayerStateWhenPlayerIsReadable() {
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(true, 2500L, 9000L, 5000L)
        );

        assertEquals(true, owner.isPlaying());
        assertEquals(2500L, owner.positionMs());
        assertEquals(9000L, owner.durationMs());
        assertEquals(5000L, owner.bufferedPositionMs());
    }

    @Test
    public void clampsNegativePositionAndUnsetDuration() {
        PlaybackPlayerStateOwner owner = new PlaybackPlayerStateOwner(
                () -> fakePlayer(false, -100L, C.TIME_UNSET, -500L)
        );

        assertEquals(false, owner.isPlaying());
        assertEquals(0L, owner.positionMs());
        assertEquals(0L, owner.durationMs());
        assertEquals(0L, owner.bufferedPositionMs());
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

    private static void assertEmptyState(PlaybackPlayerStateOwner owner) {
        assertEquals(false, owner.isPlaying());
        assertEquals(0L, owner.positionMs());
        assertEquals(0L, owner.durationMs());
        assertEquals(0L, owner.bufferedPositionMs());
    }

    private static Player fakePlayer(
            boolean playing,
            long positionMs,
            long durationMs,
            long bufferedPositionMs
    ) {
        return playerProxy((proxy, method, args) -> {
            switch (method.getName()) {
                case "isPlaying":
                    return playing;
                case "getCurrentPosition":
                    return positionMs;
                case "getDuration":
                    return durationMs;
                case "getBufferedPosition":
                    return bufferedPositionMs;
                default:
                    return defaultValue(method);
            }
        });
    }

    private static Player throwingPlayer() {
        return playerProxy((proxy, method, args) -> {
            if ("isPlaying".equals(method.getName())
                    || "getCurrentPosition".equals(method.getName())
                    || "getDuration".equals(method.getName())
                    || "getBufferedPosition".equals(method.getName())) {
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
}
