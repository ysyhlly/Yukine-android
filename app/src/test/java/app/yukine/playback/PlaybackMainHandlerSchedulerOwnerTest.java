package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackMainHandlerSchedulerOwnerTest {
    @Test
    public void delegatesMainHandlerSchedulingActions() {
        FakeCallbacks callbacks = new FakeCallbacks();
        PlaybackMainHandlerSchedulerOwner owner = new PlaybackMainHandlerSchedulerOwner(callbacks);
        Runnable first = () -> {
        };
        Runnable delayed = () -> {
        };
        Runnable removed = () -> {
        };

        owner.post(first);
        owner.postDelayed(delayed, 123L);
        owner.removeCallbacks(removed);
        owner.clearCallbacks();

        assertEquals(Arrays.asList("post", "postDelayed:123", "remove", "clear"), callbacks.calls);
        assertSame(first, callbacks.posted);
        assertSame(delayed, callbacks.delayed);
        assertSame(removed, callbacks.removed);
        assertNull(callbacks.clearToken);
    }

    @Test
    public void ignoresMissingMainHandlerCallbacks() {
        PlaybackMainHandlerSchedulerOwner owner = new PlaybackMainHandlerSchedulerOwner(
                (PlaybackMainHandlerSchedulerOwner.MainHandlerCallbacks) null
        );

        owner.post(() -> {
        });
        owner.postDelayed(() -> {
        }, 1L);
        owner.removeCallbacks(() -> {
        });
        owner.clearCallbacks();
    }

    private static final class FakeCallbacks implements PlaybackMainHandlerSchedulerOwner.MainHandlerCallbacks {
        private final List<String> calls = new ArrayList<>();
        private Runnable posted;
        private Runnable delayed;
        private Runnable removed;
        private Object clearToken = new Object();

        @Override
        public void post(Runnable runnable) {
            calls.add("post");
            posted = runnable;
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            calls.add("postDelayed:" + delayMs);
            delayed = runnable;
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            calls.add("remove");
            removed = runnable;
        }

        @Override
        public void removeCallbacksAndMessages(Object token) {
            calls.add("clear");
            clearToken = token;
        }
    }
}
