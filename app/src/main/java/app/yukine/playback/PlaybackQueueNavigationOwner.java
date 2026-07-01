package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueNavigationOwner {
    interface QueueNavigationOperations {
        void playFirstQueuedTrack();

        boolean skipToNextImmediately();

        boolean skipToPrevious();

        boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs);

    }

    interface MirroredQueueReuseHandler {
        void onMirroredQueueReused(boolean playWhenReady);
    }

    private final Supplier<QueueNavigationOperations> queueNavigationOperationsSupplier;
    private final MirroredQueueReuseHandler mirroredQueueReuseHandler;

    PlaybackQueueNavigationOwner(
            Supplier<QueueNavigationOperations> queueNavigationOperationsSupplier,
            MirroredQueueReuseHandler mirroredQueueReuseHandler
    ) {
        this.queueNavigationOperationsSupplier = queueNavigationOperationsSupplier;
        this.mirroredQueueReuseHandler = mirroredQueueReuseHandler;
    }

    static PlaybackQueueNavigationOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            MirroredQueueReuseHandler mirroredQueueReuseHandler
    ) {
        return new PlaybackQueueNavigationOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                },
                mirroredQueueReuseHandler
        );
    }

    void playFirstQueuedTrack() {
        QueueNavigationOperations operations = queueNavigationOperations();
        if (operations != null) {
            operations.playFirstQueuedTrack();
        }
    }

    void skipToNextImmediately() {
        QueueNavigationOperations operations = queueNavigationOperations();
        if (operations != null && operations.skipToNextImmediately()) {
            notifyMirroredQueueReused(true);
        }
    }

    void skipToPrevious() {
        QueueNavigationOperations operations = queueNavigationOperations();
        if (operations != null && operations.skipToPrevious()) {
            notifyMirroredQueueReused(true);
        }
    }

    boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
        QueueNavigationOperations operations = queueNavigationOperations();
        boolean reused = operations != null
                && operations.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
        if (reused) {
            notifyMirroredQueueReused(playWhenReady);
        }
        return reused;
    }

    private QueueNavigationOperations queueNavigationOperations() {
        return queueNavigationOperationsSupplier == null
                ? null
                : queueNavigationOperationsSupplier.get();
    }

    private void notifyMirroredQueueReused(boolean playWhenReady) {
        if (mirroredQueueReuseHandler != null) {
            mirroredQueueReuseHandler.onMirroredQueueReused(playWhenReady);
        }
    }

    private static final class PlaybackQueueManagerOperations implements QueueNavigationOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public void playFirstQueuedTrack() {
            playbackQueueManager.playFirstQueuedTrack();
        }

        @Override
        public boolean skipToNextImmediately() {
            return playbackQueueManager.skipToNextImmediately();
        }

        @Override
        public boolean skipToPrevious() {
            return playbackQueueManager.skipToPrevious();
        }

        @Override
        public boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
            return playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
        }

    }
}
