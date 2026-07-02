package app.yukine.playback;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackNoisyReceiverRegistrarOwnerTest {
    @Test
    public void registersReceiverWithoutExportFlagBeforeApi33() {
        FakeReceiverRegistry registry = new FakeReceiverRegistry();
        PlaybackNoisyReceiverRegistrarOwner owner = new PlaybackNoisyReceiverRegistrarOwner(registry, 32);
        BroadcastReceiver receiver = receiver();
        IntentFilter filter = new IntentFilter("test");

        owner.register(receiver, filter);

        assertEquals(Arrays.asList("register"), registry.calls);
        assertSame(receiver, registry.receiver);
        assertSame(filter, registry.filter);
    }

    @Test
    public void registersReceiverNotExportedOnApi33AndLater() {
        FakeReceiverRegistry registry = new FakeReceiverRegistry();
        PlaybackNoisyReceiverRegistrarOwner owner = new PlaybackNoisyReceiverRegistrarOwner(registry, 33);
        BroadcastReceiver receiver = receiver();
        IntentFilter filter = new IntentFilter("test");

        owner.register(receiver, filter);

        assertEquals(Arrays.asList("register-not-exported"), registry.calls);
        assertSame(receiver, registry.receiver);
        assertSame(filter, registry.filter);
    }

    @Test
    public void unregistersReceiverThroughRegistry() {
        FakeReceiverRegistry registry = new FakeReceiverRegistry();
        PlaybackNoisyReceiverRegistrarOwner owner = new PlaybackNoisyReceiverRegistrarOwner(registry, 33);
        BroadcastReceiver receiver = receiver();

        owner.unregister(receiver);

        assertEquals(Arrays.asList("unregister"), registry.calls);
        assertSame(receiver, registry.receiver);
    }

    private static BroadcastReceiver receiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
            }
        };
    }

    private static final class FakeReceiverRegistry implements PlaybackNoisyReceiverRegistrarOwner.ReceiverRegistry {
        private final List<String> calls = new ArrayList<>();
        private BroadcastReceiver receiver;
        private IntentFilter filter;

        @Override
        public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            calls.add("register");
            this.receiver = receiver;
            this.filter = filter;
        }

        @Override
        public void registerReceiverNotExported(BroadcastReceiver receiver, IntentFilter filter) {
            calls.add("register-not-exported");
            this.receiver = receiver;
            this.filter = filter;
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
            calls.add("unregister");
            this.receiver = receiver;
        }
    }
}
