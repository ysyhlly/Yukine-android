package app.yukine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocalAudioIngestResult {
    private final List<Track> tracks;
    private final LocalAudioImportSummary summary;

    public LocalAudioIngestResult(List<Track> tracks, LocalAudioImportSummary summary) {
        this.tracks = Collections.unmodifiableList(
                new ArrayList<>(tracks == null ? Collections.emptyList() : tracks)
        );
        this.summary = summary == null ? LocalAudioImportSummary.EMPTY : summary;
    }

    public List<Track> tracks() {
        return tracks;
    }

    public LocalAudioImportSummary summary() {
        return summary;
    }

    public static LocalAudioIngestResult empty() {
        return new LocalAudioIngestResult(Collections.emptyList(), LocalAudioImportSummary.EMPTY);
    }
}
