package app.yukine.playback;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local gate that keeps metadata enrichment out of playback and track preparation. */
public final class IdentityEnhancementPlaybackGate {
    private static final AtomicBoolean DEFER = new AtomicBoolean(false);

    private IdentityEnhancementPlaybackGate() {
    }

    public static void update(PlaybackStateSnapshot snapshot) {
        DEFER.set(snapshot != null && (snapshot.playing || snapshot.preparing));
    }

    public static boolean shouldDefer() {
        return DEFER.get();
    }

    public static void clear() {
        DEFER.set(false);
    }
}
