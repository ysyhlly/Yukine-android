package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackQueuePersistenceOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
    private final Runnable persistQueueState;
    private final Consumer<Boolean> savePlaybackResumeRequested;
    private final Consumer<Boolean> persistCurrentPlaybackPosition;

    static PlaybackQueuePersistenceOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueuePersistenceOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.persistQueueState();
                    }
                },
                requested -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.savePlaybackResumeRequested(requested);
                    }
                },
                force -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.persistCurrentPlaybackPosition(force);
                    }
                }
        );
    }

    PlaybackQueuePersistenceOwner(
            Runnable persistQueueState,
            Consumer<Boolean> savePlaybackResumeRequested,
            Consumer<Boolean> persistCurrentPlaybackPosition
    ) {
        this.persistQueueState = persistQueueState;
        this.savePlaybackResumeRequested = savePlaybackResumeRequested;
        this.persistCurrentPlaybackPosition = persistCurrentPlaybackPosition;
    }

    @Override
    public void persistQueueState() {
        if (persistQueueState != null) {
            persistQueueState.run();
        }
    }

    @Override
    public void savePlaybackResumeRequested(boolean requested) {
        if (savePlaybackResumeRequested != null) {
            savePlaybackResumeRequested.accept(requested);
        }
    }

    void requestPlaybackResume() {
        savePlaybackResumeRequested(true);
    }

    void clearPlaybackResumeRequest() {
        savePlaybackResumeRequested(false);
    }

    void persistCurrentPlaybackPosition(boolean force) {
        if (persistCurrentPlaybackPosition != null) {
            persistCurrentPlaybackPosition.accept(force);
        }
    }
}
