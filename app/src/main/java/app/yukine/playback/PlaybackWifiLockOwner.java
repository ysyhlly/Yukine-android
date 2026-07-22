package app.yukine.playback;

import android.net.wifi.WifiManager;
import android.os.Build;

import app.yukine.playback.manager.PlaybackWifiLockManager;

final class PlaybackWifiLockOwner implements PlaybackWifiLockManager.Lock {
    interface WifiLockOperations {
        boolean isHeld();

        void acquire();

        void release();
    }

    private final WifiLockOperations wifiLockOperations;

    static PlaybackWifiLockOwner fromWifiLock(WifiManager.WifiLock wifiLock) {
        return new PlaybackWifiLockOwner(
                wifiLock == null ? null : new AndroidWifiLockOperations(wifiLock)
        );
    }

    static int preferredModeForSdk(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.Q
                ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                : WifiManager.WIFI_MODE_FULL;
    }

    PlaybackWifiLockOwner(WifiLockOperations wifiLockOperations) {
        this.wifiLockOperations = wifiLockOperations;
    }

    @Override
    public boolean isHeld() {
        return wifiLockOperations != null && wifiLockOperations.isHeld();
    }

    @Override
    public void acquire() {
        if (wifiLockOperations != null) {
            wifiLockOperations.acquire();
        }
    }

    @Override
    public void release() {
        if (wifiLockOperations != null) {
            wifiLockOperations.release();
        }
    }

    private static final class AndroidWifiLockOperations implements WifiLockOperations {
        private final WifiManager.WifiLock wifiLock;

        private AndroidWifiLockOperations(WifiManager.WifiLock wifiLock) {
            this.wifiLock = wifiLock;
        }

        @Override
        public boolean isHeld() {
            return wifiLock.isHeld();
        }

        @Override
        public void acquire() {
            wifiLock.acquire();
        }

        @Override
        public void release() {
            wifiLock.release();
        }
    }
}
