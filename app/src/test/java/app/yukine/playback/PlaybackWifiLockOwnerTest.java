package app.yukine.playback;

import android.net.wifi.WifiManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackWifiLockOwnerTest {
    @Test
    public void lowLatencyModeIsOnlyUsedFromAndroidQ() {
        assertEquals(WifiManager.WIFI_MODE_FULL, PlaybackWifiLockOwner.preferredModeForSdk(28));
        assertEquals(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                PlaybackWifiLockOwner.preferredModeForSdk(29)
        );
    }

    @Test
    public void delegatesWifiLockLifecycleActions() {
        FakeWifiLockOperations operations = new FakeWifiLockOperations();
        PlaybackWifiLockOwner owner = new PlaybackWifiLockOwner(operations);

        assertFalse(owner.isHeld());

        owner.acquire();

        assertTrue(owner.isHeld());
        assertEquals(1, operations.acquireCalls);

        owner.release();

        assertFalse(owner.isHeld());
        assertEquals(1, operations.releaseCalls);
    }

    @Test
    public void ignoresMissingWifiLock() {
        PlaybackWifiLockOwner owner = new PlaybackWifiLockOwner(null);

        assertFalse(owner.isHeld());
        owner.acquire();
        owner.release();

        assertFalse(owner.isHeld());
    }

    private static final class FakeWifiLockOperations
            implements PlaybackWifiLockOwner.WifiLockOperations {
        private boolean held;
        private int acquireCalls;
        private int releaseCalls;

        @Override
        public boolean isHeld() {
            return held;
        }

        @Override
        public void acquire() {
            acquireCalls++;
            held = true;
        }

        @Override
        public void release() {
            releaseCalls++;
            held = false;
        }
    }
}
