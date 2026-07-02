package app.yukine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaybackQueueState {
    public final List<Track> tracks;
    public final int currentIndex;

    public PlaybackQueueState(List<Track> tracks, int currentIndex) {
        ArrayList<Track> copy = tracks == null ? new ArrayList<Track>() : new ArrayList<>(tracks);
        this.tracks = Collections.unmodifiableList(copy);
        this.currentIndex = currentIndex < 0 || copy.isEmpty() ? -1 : Math.min(currentIndex, copy.size() - 1);
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }
}
