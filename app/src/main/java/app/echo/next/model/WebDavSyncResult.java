package app.echo.next.model;

import java.util.Collections;
import java.util.List;

public final class WebDavSyncResult {
    public final List<Track> tracks;
    public final int addedCount;
    public final int removedCount;
    public final int keptCount;

    public WebDavSyncResult(List<Track> tracks, int addedCount, int removedCount, int keptCount) {
        this.tracks = tracks == null ? Collections.<Track>emptyList() : tracks;
        this.addedCount = Math.max(0, addedCount);
        this.removedCount = Math.max(0, removedCount);
        this.keptCount = Math.max(0, keptCount);
    }

    public int trackCount() {
        return tracks.size();
    }

    public String summary() {
        return "added " + addedCount + ", removed " + removedCount + ", kept " + keptCount;
    }
}
