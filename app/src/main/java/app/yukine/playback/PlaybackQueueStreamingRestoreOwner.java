package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Function;

final class PlaybackQueueStreamingRestoreOwner implements PlaybackQueueManager.StreamingRestoreProvider {
    private final Function<Track, Track> restoredTrackForPreparation;
    private final Consumer<String> restoreHeadersForDataPath;

    PlaybackQueueStreamingRestoreOwner(
            Function<Track, Track> restoredTrackForPreparation,
            Consumer<String> restoreHeadersForDataPath
    ) {
        this.restoredTrackForPreparation = restoredTrackForPreparation;
        this.restoreHeadersForDataPath = restoreHeadersForDataPath;
    }

    @Override
    public Track restoredTrackFor(Track track) {
        return restoredTrackForPreparation == null ? null : restoredTrackForPreparation.apply(track);
    }

    @Override
    public void restoreForDataPath(String dataPath) {
        if (restoreHeadersForDataPath != null) {
            restoreHeadersForDataPath.accept(dataPath);
        }
    }
}
