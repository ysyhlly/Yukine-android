package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueStreamingRestoreOwner implements PlaybackQueueManager.StreamingRestoreProvider {
    interface StreamingRestoreResolver {
        Track restoredTrackForPreparation(Track track);

        void restoreHeadersForDataPath(String dataPath);
    }

    private final StreamingRestoreResolver streamingRestoreResolver;

    PlaybackQueueStreamingRestoreOwner(StreamingRestoreResolver streamingRestoreResolver) {
        this.streamingRestoreResolver = streamingRestoreResolver;
    }

    @Override
    public Track restoredTrackFor(Track track) {
        return streamingRestoreResolver.restoredTrackForPreparation(track);
    }

    @Override
    public void restoreForDataPath(String dataPath) {
        streamingRestoreResolver.restoreHeadersForDataPath(dataPath);
    }
}
