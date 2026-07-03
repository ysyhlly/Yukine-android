package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

final class PlaybackQueuePersistenceOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
    private final PlaybackQueueManager playbackQueueManager;
    private final PlaybackQueueStore queueStore;

    PlaybackQueuePersistenceOwner(
            PlaybackQueueManager playbackQueueManager,
            PlaybackQueueStore queueStore
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.queueStore = queueStore;
    }

    @Override
    public void persistQueueState() {
        if (playbackQueueManager != null) {
            playbackQueueManager.persistQueueState();
        }
    }

    @Override
    public void savePlaybackResumeRequested(boolean requested) {
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
}
