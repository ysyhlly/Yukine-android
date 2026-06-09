package app.echo.next.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StreamImportResult {
    public final List<Track> tracks;
    public final int candidateCount;
    public final int addedCount;
    public final int duplicateCount;

    public StreamImportResult(List<Track> tracks, int candidateCount, int addedCount, int duplicateCount) {
        ArrayList<Track> copy = tracks == null ? new ArrayList<Track>() : new ArrayList<>(tracks);
        this.tracks = Collections.unmodifiableList(copy);
        this.candidateCount = Math.max(0, candidateCount);
        this.addedCount = Math.max(0, addedCount);
        this.duplicateCount = Math.max(0, duplicateCount);
    }

    public boolean isEmpty() {
        return candidateCount == 0;
    }
}
