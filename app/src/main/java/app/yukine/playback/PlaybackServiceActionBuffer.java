package app.yukine.playback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.playback.service.PlaybackServiceActions;

/** Holds playback Intents until the persisted queue and settings are ready. */
final class PlaybackServiceActionBuffer {
    private final ArrayDeque<String> pendingActions = new ArrayDeque<>();
    private boolean ready;

    List<String> accept(String action) {
        if (!PlaybackServiceActions.isPlaybackServiceAction(action)) {
            return Collections.emptyList();
        }
        if (!ready && !PlaybackServiceActions.STOP.equals(action)) {
            pendingActions.addLast(action);
            return Collections.emptyList();
        }
        if (PlaybackServiceActions.STOP.equals(action)) {
            pendingActions.clear();
        }
        return Collections.singletonList(action);
    }

    boolean requiresBootstrapForeground(String action) {
        return !ready
                && PlaybackServiceActions.isPlaybackServiceAction(action)
                && !PlaybackServiceActions.STOP.equals(action);
    }

    List<String> markReadyAndDrain() {
        ready = true;
        ArrayList<String> actions = new ArrayList<>(pendingActions);
        pendingActions.clear();
        return actions;
    }

    boolean isReady() {
        return ready;
    }

    void reset() {
        ready = false;
        pendingActions.clear();
    }
}
