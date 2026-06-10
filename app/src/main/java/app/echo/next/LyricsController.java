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

    interface LanguageProvider {
        String languageMode();
    }

    private final MainExecutors executors;
    private final Handler mainHandler;
    private final LyricsRepository repository;
    private final LanguageProvider languageProvider;
    private final Listener listener;
    private final ArrayList<LyricsLine> lines = new ArrayList<>();

    private long trackId = -1L;
    private long requestToken;
    private boolean onlineEnabled;
    private long offsetMs;
    private StatusKind statusKind = StatusKind.NOT_LOADED;
    private int loadedLineCount;

    LyricsController(
            MainExecutors executors,
            Handler mainHandler,
            boolean onlineEnabled,
            long offsetMs,
            LanguageProvider languageProvider,
            Listener listener
    ) {
        this.executors = executors;
        this.mainHandler = mainHandler;
        this.repository = new LyricsRepository();
        this.languageProvider = languageProvider;
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
        return statusText();
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
        load(track, "");
    }

    void load(final Track track, final String neteaseProviderTrackId) {
        final long token = ++requestToken;
        trackId = track == null ? -1L : track.id;
        lines.clear();
        loadedLineCount = 0;
        statusKind = track == null
                ? StatusKind.NO_TRACK
                : (onlineEnabled || (neteaseProviderTrackId != null && !neteaseProviderTrackId.trim().isEmpty())
                        ? StatusKind.LOADING
                        : StatusKind.LOADING_LOCAL);
        notifyChanged();
        if (track == null) {
            return;
        }

        final long requestedTrackId = track.id;
        final boolean requestOnline = onlineEnabled;
        executors.lyrics(new Runnable() {
            @Override
            public void run() {
                final List<LyricsLine> loadedLines = repository.loadForTrack(
                        track,
                        requestOnline,
                        neteaseProviderTrackId
                );
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != requestToken || trackId != requestedTrackId) {
                            return;
                        }
                        lines.clear();
                        lines.addAll(loadedLines);
                        loadedLineCount = loadedLines.size();
                        statusKind = loadedLines.isEmpty()
                                ? (requestOnline ? StatusKind.NOT_FOUND : StatusKind.LOCAL_NOT_FOUND)
                                : StatusKind.LOADED;
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

    private String statusText() {
        String languageMode = languageProvider == null ? AppLanguage.MODE_SYSTEM : languageProvider.languageMode();
        switch (statusKind) {
            case NO_TRACK:
                return AppLanguage.text(languageMode, "no.track.selected");
            case LOADING:
                return AppLanguage.text(languageMode, "loading.lyrics");
            case LOADING_LOCAL:
                return AppLanguage.text(languageMode, "loading.local.lyrics");
            case NOT_FOUND:
                return AppLanguage.text(languageMode, "no.lyrics.found");
            case LOCAL_NOT_FOUND:
                return AppLanguage.text(languageMode, "no.local.lyrics.found");
            case LOADED:
                return AppLanguage.text(languageMode, "loaded.lyrics.prefix")
                        + loadedLineCount
                        + AppLanguage.text(languageMode, "loaded.lyrics.suffix");
            case NOT_LOADED:
            default:
                return AppLanguage.text(languageMode, "lyrics.not.loaded");
        }
    }

    private enum StatusKind {
        NOT_LOADED,
        NO_TRACK,
        LOADING,
        LOADING_LOCAL,
        NOT_FOUND,
        LOCAL_NOT_FOUND,
        LOADED
    }
}
