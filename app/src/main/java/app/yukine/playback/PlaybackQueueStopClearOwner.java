package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueStopClearOwner {
    interface QueueStopClearOperations {
        void prepareStopAndClearPlaybackState();
    }

    private final Supplier<QueueStopClearOperations> queueStopClearOperationsSupplier;

    PlaybackQueueStopClearOwner(Supplier<QueueStopClearOperations> queueStopClearOperationsSupplier) {
        this.queueStopClearOperationsSupplier = queueStopClearOperationsSupplier;
    }

    static PlaybackQueueStopClearOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueStopClearOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : playbackQueueManager::prepareStopAndClearPlaybackState;
                }
        );
    }

    boolean prepareStopAndClearPlaybackState() {
        QueueStopClearOperations operations = queueStopClearOperations();
        if (operations == null) {
            return false;
        }
        operations.prepareStopAndClearPlaybackState();
        return true;
    }

    private QueueStopClearOperations queueStopClearOperations() {
        return queueStopClearOperationsSupplier == null
                ? null
                : queueStopClearOperationsSupplier.get();
    }
}
