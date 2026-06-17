package app.yukine.playback;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Track;

public final class PlaybackStreamingDiagnostics {
    private static final String TAG = "PlaybackDiagnostics";
    private static final int MAX_EVENTS = 24;

    private final ArrayDeque<Event> recentEvents = new ArrayDeque<>();
    private int bufferingEvents;
    private int recoveryEvents;
    private int precacheAttempts;
    private int precacheSuccesses;
    private int precacheFailures;

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                bufferingEvents,
                recoveryEvents,
                precacheAttempts,
                precacheSuccesses,
                precacheFailures,
                new ArrayList<>(recentEvents)
        );
    }

    public void recordBuffering(Track track, long positionMs) {
        synchronized (this) {
            bufferingEvents++;
            addLocked(Event.buffering(track, positionMs));
        }
        logDebug("BUFFERING track=" + trackKey(track) + " host=" + host(track) + " positionMs=" + positionMs);
    }

    public void recordRecovery(Track track, long positionMs, String quality) {
        synchronized (this) {
            recoveryEvents++;
            addLocked(Event.recovery(track, positionMs, quality));
        }
        logDebug("RECOVERY track=" + trackKey(track) + " quality=" + quality + " positionMs=" + positionMs);
    }

    public void recordPrecacheQueued(Track track) {
        synchronized (this) {
            precacheAttempts++;
            addLocked(Event.precacheQueued(track));
        }
        logDebug("PRECACHE_QUEUED track=" + trackKey(track) + " host=" + host(track));
    }

    public void recordPrecacheComplete(Track track, long bytesCached) {
        synchronized (this) {
            precacheSuccesses++;
            addLocked(Event.precacheComplete(track, bytesCached));
        }
        logDebug("PRECACHE_OK track=" + trackKey(track) + " bytes=" + bytesCached);
    }

    public void recordPrecacheFailed(Track track, Throwable error) {
        synchronized (this) {
            precacheFailures++;
            addLocked(Event.precacheFailed(track, message(error)));
        }
        logWarning("PRECACHE_FAILED track=" + trackKey(track), error);
    }

    private void addLocked(Event event) {
        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.removeLast();
        }
    }

    private static String trackKey(Track track) {
        if (track == null) {
            return "<null>";
        }
        if (track.dataPath != null && !track.dataPath.isEmpty()) {
            return track.dataPath;
        }
        return String.valueOf(track.id);
    }

    private static String host(Track track) {
        if (track == null || track.contentUri == null) {
            return "";
        }
        String host = track.contentUri.getHost();
        return host == null ? "" : host;
    }

    private static String message(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private static void logDebug(String message) {
        try {
            Log.d(TAG, message);
        } catch (RuntimeException ignored) {
            // Local JVM tests do not provide android.util.Log.
        }
    }

    private static void logWarning(String message, Throwable error) {
        try {
            Log.w(TAG, message, error);
        } catch (RuntimeException ignored) {
            // Local JVM tests do not provide android.util.Log.
        }
    }

    public static final class Snapshot {
        public final int bufferingEvents;
        public final int recoveryEvents;
        public final int precacheAttempts;
        public final int precacheSuccesses;
        public final int precacheFailures;
        public final List<Event> recentEvents;

        private Snapshot(
                int bufferingEvents,
                int recoveryEvents,
                int precacheAttempts,
                int precacheSuccesses,
                int precacheFailures,
                List<Event> recentEvents
        ) {
            this.bufferingEvents = bufferingEvents;
            this.recoveryEvents = recoveryEvents;
            this.precacheAttempts = precacheAttempts;
            this.precacheSuccesses = precacheSuccesses;
            this.precacheFailures = precacheFailures;
            this.recentEvents = Collections.unmodifiableList(recentEvents);
        }
    }

    public static final class Event {
        public final String type;
        public final String trackKey;
        public final String host;
        public final long positionMs;
        public final long bytes;
        public final String quality;
        public final String message;
        public final long timestampMs;

        private Event(
                String type,
                Track track,
                long positionMs,
                long bytes,
                String quality,
                String message
        ) {
            this.type = type;
            this.trackKey = trackKey(track);
            this.host = host(track);
            this.positionMs = Math.max(0L, positionMs);
            this.bytes = Math.max(0L, bytes);
            this.quality = quality == null ? "" : quality;
            this.message = message == null ? "" : message;
            this.timestampMs = System.currentTimeMillis();
        }

        private static Event buffering(Track track, long positionMs) {
            return new Event("buffering", track, positionMs, 0L, "", "");
        }

        private static Event recovery(Track track, long positionMs, String quality) {
            return new Event("recovery", track, positionMs, 0L, quality, "");
        }

        private static Event precacheQueued(Track track) {
            return new Event("precache_queued", track, 0L, 0L, "", "");
        }

        private static Event precacheComplete(Track track, long bytes) {
            return new Event("precache_complete", track, 0L, bytes, "", "");
        }

        private static Event precacheFailed(Track track, String message) {
            return new Event("precache_failed", track, 0L, 0L, "", message);
        }
    }
}
