package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueStopClearOwner {
    interface QueueStopClearOperations {
        void prepareStopAndClearPlaybackState();
    }

    private final Supplier<QueueStopClearOperations> queueStopClearOperationsProvider;

    PlaybackQueueStopClearOwner(Supplier<QueueStopClearOperations> queueStopClearOperationsProvider) {
        this.queueStopClearOperationsProvider = queueStopClearOperationsProvider;
    }

    static PlaybackQueueStopClearOwner fromPlaybackQueueManagerProvider(
            Supplier<PlaybackQueueManager> playbackQueueManagerProvider
    ) {
        return new PlaybackQueueStopClearOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerProvider == null
                            ? null
                            : playbackQueueManagerProvider.get();
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
        return queueStopClearOperationsProvider == null
                ? null
                : queueStopClearOperationsProvider.get();
    }
}
