package app.echo.next;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

import app.echo.next.data.LyricsRepository;
import app.echo.next.model.LyricsLine;
import app.echo.next.model.Track;

final class LyricsController {
    interface Listener {
        void onLyricsChanged();
    }

    private final MainExecutors executors;
    private final Handler mainHandler;
    private final LyricsRepository repository;
    private final Listener listener;
    private final ArrayList<LyricsLine> lines = new ArrayList<>();

    private long trackId = -1L;
    private long requestToken;
    private boolean onlineEnabled;
    private long offsetMs;
    private String status = "Lyrics not loaded";

    LyricsController(
            MainExecutors executors,
            Handler mainHandler,
            boolean onlineEnabled,
            long offsetMs,
            Listener listener
    ) {
        this.executors = executors;
        this.mainHandler = mainHandler;
        this.repository = new LyricsRepository();
        this.onlineEnabled = onlineEnabled;
        this.offsetMs = offsetMs;
        this.listener = listener;
    }

    long trackId() {
        return trackId;
    }

    List<LyricsLine> lines() {
        return lines;
    }

    String status() {
        return status;
    }

    boolean onlineEnabled() {
        return onlineEnabled;
    }

    void setOnlineEnabled(boolean enabled) {
        onlineEnabled = enabled;
    }

    long offsetMs() {
        return offsetMs;
    }

    void setOffsetMs(long nextOffsetMs) {
        offsetMs = nextOffsetMs;
    }

    void reload(Track track) {
        trackId = -1L;
        lines.clear();
        load(track);
    }

    void load(final Track track) {
        final long token = ++requestToken;
        trackId = track == null ? -1L : track.id;
        lines.clear();
        status = track == null
                ? "No track selected"
                : (onlineEnabled ? "Loading lyrics" : "Loading local lyrics");
        notifyChanged();
        if (track == null) {
            return;
        }

        final long requestedTrackId = track.id;
        final boolean requestOnline = onlineEnabled;
        executors.lyrics(new Runnable() {
            @Override
            public void run() {
                final List<LyricsLine> loadedLines = repository.loadForTrack(track, requestOnline);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != requestToken || trackId != requestedTrackId) {
                            return;
                        }
                        lines.clear();
                        lines.addAll(loadedLines);
                        status = loadedLines.isEmpty()
                                ? (requestOnline ? "No lyrics found" : "No local lyrics found")
                                : "Loaded lyrics: " + loadedLines.size() + " lines";
                        notifyChanged();
                    }
                });
            }
        });
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onLyricsChanged();
        }
    }
}
