package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueRestoreOwner {
    interface QueueRestoreOperations {
        PlaybackQueueManager.QueueStateSnapshot restorePlaybackQueue();

        PlaybackQueueManager.RestorePlaybackResult restoreLastPlayback(boolean playWhenRestored);

        void setPlaybackRestoreEnabled(boolean enabled);
    }

    interface RestorePlaybackBoundary {
        void createPlayerIfNeeded();

        void prepareCurrent(boolean playWhenReady);

        void publishState();
    }

    private final Supplier<QueueRestoreOperations> queueRestoreOperationsSupplier;
    private final RestorePlaybackBoundary restorePlaybackBoundary;

    PlaybackQueueRestoreOwner(
            Supplier<QueueRestoreOperations> queueRestoreOperationsSupplier,
            RestorePlaybackBoundary restorePlaybackBoundary
    ) {
        this.queueRestoreOperationsSupplier = queueRestoreOperationsSupplier;
        this.restorePlaybackBoundary = restorePlaybackBoundary;
    }

    static PlaybackQueueRestoreOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            RestorePlaybackBoundary restorePlaybackBoundary
    ) {
        return new PlaybackQueueRestoreOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                },
                restorePlaybackBoundary
        );
    }

    void restoreLastPlayback(boolean playWhenRestored) {
        QueueRestoreOperations operations = queueRestoreOperations();
        PlaybackQueueManager.RestorePlaybackResult restoreResult = operations == null
                ? PlaybackQueueManager.RestorePlaybackResult.empty()
                : operations.restoreLastPlayback(playWhenRestored);
        if (restoreResult == null) {
            restoreResult = PlaybackQueueManager.RestorePlaybackResult.empty();
        }
        if (restorePlaybackBoundary == null) {
            return;
        }
        if (restoreResult.getShouldCreatePlayer()) {
            restorePlaybackBoundary.createPlayerIfNeeded();
        }
        if (!restoreResult.getShouldPrepare()) {
            restorePlaybackBoundary.publishState();
            return;
        }
        restorePlaybackBoundary.prepareCurrent(restoreResult.getPlayWhenReady());
    }

    void restorePlaybackQueue() {
        QueueRestoreOperations operations = queueRestoreOperations();
        if (operations != null) {
            operations.restorePlaybackQueue();
        }
    }

    void setPlaybackRestoreEnabled(boolean enabled) {
        QueueRestoreOperations operations = queueRestoreOperations();
        if (operations != null) {
            operations.setPlaybackRestoreEnabled(enabled);
        }
    }

    private QueueRestoreOperations queueRestoreOperations() {
        return queueRestoreOperationsSupplier == null
                ? null
                : queueRestoreOperationsSupplier.get();
    }

    private static final class PlaybackQueueManagerOperations implements QueueRestoreOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public PlaybackQueueManager.QueueStateSnapshot restorePlaybackQueue() {
            return playbackQueueManager.restorePlaybackQueue();
        }

        @Override
        public PlaybackQueueManager.RestorePlaybackResult restoreLastPlayback(boolean playWhenRestored) {
            return playbackQueueManager.restoreLastPlayback(playWhenRestored);
        }

        @Override
        public void setPlaybackRestoreEnabled(boolean enabled) {
            playbackQueueManager.setPlaybackRestoreEnabled(enabled);
        }
    }
}
