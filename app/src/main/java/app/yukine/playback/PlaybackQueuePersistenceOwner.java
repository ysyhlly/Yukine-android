package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueuePersistenceOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;

    PlaybackQueuePersistenceOwner(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
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
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.savePlaybackResumeRequested(requested);
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
}
