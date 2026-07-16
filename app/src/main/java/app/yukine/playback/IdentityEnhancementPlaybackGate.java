package app.yukine.playback;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local gate that gives the visible UI and playback priority over identity maintenance. */
public final class IdentityEnhancementPlaybackGate {
    private static final AtomicBoolean PLAYBACK_ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean APP_VISIBLE = new AtomicBoolean(false);

    private IdentityEnhancementPlaybackGate() {
    }

    public static void update(PlaybackStateSnapshot snapshot) {
        PLAYBACK_ACTIVE.set(snapshot != null && (snapshot.playing || snapshot.preparing));
    }

    public static void setAppVisible(boolean visible) {
        APP_VISIBLE.set(visible);
    }

    public static boolean shouldDefer() {
        return PLAYBACK_ACTIVE.get() || APP_VISIBLE.get();
    }

    public static void clear() {
        PLAYBACK_ACTIVE.set(false);
        APP_VISIBLE.set(false);
    }
}
