package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import androidx.media3.common.Player;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PlaybackBufferedProgressOwnerTest {
    @Test
    public void returnsBufferedProgressClampedToDuration() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> fakePlayer(2500L)
        );
        PlaybackBufferedProgressOwner overBuffered = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> fakePlayer(5000L)
        );

        assertEquals(0.5f, owner.bufferedProgress(5000L), 0.001f);
        assertEquals(1.0f, overBuffered.bufferedProgress(3000L), 0.001f);
    }

    @Test
    public void usesCurrentPositionWhenItIsAheadOfBufferedPosition() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 3000L,
                () -> fakePlayer(1200L)
        );

        assertEquals(0.75f, owner.bufferedProgress(4000L), 0.001f);
    }

    @Test
    public void returnsZeroWhenDurationOrPlayerIsMissing() {
        PlaybackBufferedProgressOwner missingPlayer = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                null
        );
        PlaybackBufferedProgressOwner nullPlayer = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                () -> null
        );

        assertEquals(0f, missingPlayer.bufferedProgress(4000L), 0.001f);
        assertEquals(0f, nullPlayer.bufferedProgress(4000L), 0.001f);
        assertEquals(0f, nullPlayer.bufferedProgress(0L), 0.001f);
    }

    @Test
    public void returnsZeroWhenPlayerStateCannotBeRead() {
        PlaybackBufferedProgressOwner owner = new PlaybackBufferedProgressOwner(
                () -> 1000L,
                PlaybackBufferedProgressOwnerTest::throwingPlayer
        );

        assertEquals(0f, owner.bufferedProgress(4000L), 0.001f);
    }

    private static Player fakePlayer(long bufferedPositionMs) {
        return playerProxy((proxy, method, args) -> {
            if ("getBufferedPosition".equals(method.getName())) {
                return bufferedPositionMs;
            }
            return defaultValue(method);
        });
    }

    private static Player throwingPlayer() {
        return playerProxy((proxy, method, args) -> {
            if ("getBufferedPosition".equals(method.getName())) {
                throw new IllegalStateException("player released");
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
