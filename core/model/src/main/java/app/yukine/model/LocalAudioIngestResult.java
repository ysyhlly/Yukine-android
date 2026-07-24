package app.yukine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocalAudioIngestResult {
    private final List<Track> tracks;
    private final LocalAudioImportSummary summary;
    private final boolean complete;
    private final String failureReason;

    public LocalAudioIngestResult(List<Track> tracks, LocalAudioImportSummary summary) {
        this(tracks, summary, true, "");
    }

    public LocalAudioIngestResult(
            List<Track> tracks,
            LocalAudioImportSummary summary,
            boolean complete,
            String failureReason
    ) {
        this.tracks = Collections.unmodifiableList(
                new ArrayList<>(tracks == null ? Collections.emptyList() : tracks)
        );
        this.summary = summary == null ? LocalAudioImportSummary.EMPTY : summary;
        this.complete = complete;
        this.failureReason = failureReason == null ? "" : failureReason.trim();
    }

    public List<Track> tracks() {
        return tracks;
    }

    public LocalAudioImportSummary summary() {
        return summary;
    }

    public boolean complete() {
        return complete;
    }

    public String failureReason() {
        return failureReason;
    }

    public static LocalAudioIngestResult empty() {
        return new LocalAudioIngestResult(Collections.emptyList(), LocalAudioImportSummary.EMPTY);
    }

    public static LocalAudioIngestResult incomplete(
            List<Track> tracks,
            LocalAudioImportSummary summary,
            String failureReason
    ) {
        return new LocalAudioIngestResult(tracks, summary, false, failureReason);
    }
}
