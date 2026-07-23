package app.yukine.playback;

/** Applies Android background-start rules without coupling boot handling to the playback service. */
final class PlaybackBootRestorePolicy {
    private static final int ANDROID_15_API = 35;

    private PlaybackBootRestorePolicy() {
    }

    static boolean canStartMediaPlaybackService(int deviceSdk, int targetSdk) {
        return deviceSdk < ANDROID_15_API || targetSdk < ANDROID_15_API;
    }
}
