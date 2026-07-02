package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.function.Supplier;

final class PlaybackQueuePersistenceOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Supplier<PlaybackQueueStore> queueStoreSupplier;

    PlaybackQueuePersistenceOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Supplier<PlaybackQueueStore> queueStoreSupplier
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.queueStoreSupplier = queueStoreSupplier;
    }

    @Override
    public void persistQueueState() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.persistQueueState();
        }
    }

    @Override
    public void savePlaybackResumeRequested(boolean requested) {
        PlaybackQueueStore queueStore = queueStore();
        if (queueStore != null) {
            queueStore.saveResumeRequested(requested);
        }
    }

    void requestPlaybackResume() {
        savePlaybackResumeRequested(true);
    }

    void clearPlaybackResumeRequest() {
        savePlaybackResumeRequested(false);
    }

    void persistCurrentPlaybackPosition(boolean force) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.persistCurrentPlaybackPosition(force);
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }

    private PlaybackQueueStore queueStore() {
        return queueStoreSupplier == null ? null : queueStoreSupplier.get();
    }
}
