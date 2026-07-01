package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueuePersistenceOwner
        implements PlaybackShutdownLifecycleResourcesOwner.PlaybackQueueLifecycleStore {
    interface QueuePersistenceOperations {
        void persistQueueState();

        void savePlaybackResumeRequested(boolean requested);

        void persistCurrentPlaybackPosition(boolean force);
    }

    private final Supplier<QueuePersistenceOperations> queuePersistenceOperationsSupplier;

    static PlaybackQueuePersistenceOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueuePersistenceOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                }
        );
    }

    PlaybackQueuePersistenceOwner(
            Supplier<QueuePersistenceOperations> queuePersistenceOperationsSupplier
    ) {
        this.queuePersistenceOperationsSupplier = queuePersistenceOperationsSupplier;
    }

    @Override
    public void persistQueueState() {
        QueuePersistenceOperations queuePersistenceOperations = queuePersistenceOperations();
        if (queuePersistenceOperations != null) {
            queuePersistenceOperations.persistQueueState();
        }
    }

    @Override
    public void savePlaybackResumeRequested(boolean requested) {
        QueuePersistenceOperations queuePersistenceOperations = queuePersistenceOperations();
        if (queuePersistenceOperations != null) {
            queuePersistenceOperations.savePlaybackResumeRequested(requested);
        }
    }

    void requestPlaybackResume() {
        savePlaybackResumeRequested(true);
    }

    void clearPlaybackResumeRequest() {
        savePlaybackResumeRequested(false);
    }

    void persistCurrentPlaybackPosition(boolean force) {
        QueuePersistenceOperations queuePersistenceOperations = queuePersistenceOperations();
        if (queuePersistenceOperations != null) {
            queuePersistenceOperations.persistCurrentPlaybackPosition(force);
        }
    }

    private QueuePersistenceOperations queuePersistenceOperations() {
        return queuePersistenceOperationsSupplier == null
                ? null
                : queuePersistenceOperationsSupplier.get();
    }

    private static final class PlaybackQueueManagerOperations implements QueuePersistenceOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public void persistQueueState() {
            playbackQueueManager.persistQueueState();
        }

        @Override
        public void savePlaybackResumeRequested(boolean requested) {
            playbackQueueManager.savePlaybackResumeRequested(requested);
        }

        @Override
        public void persistCurrentPlaybackPosition(boolean force) {
            playbackQueueManager.persistCurrentPlaybackPosition(force);
        }
    }
}
