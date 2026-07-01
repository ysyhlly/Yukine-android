package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueCompletionOwner {
    interface QueueCompletionOperations {
        PlaybackQueueManager.PlaybackCompletionAction playbackCompletionAction();

        void preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction action);

        void prepareStopAtEndOfQueue();

        void prepareStopAfterAutomaticAdvance(int completedIndex);
    }

    interface CompletionBoundary {
        void stopAndClear();

        void prepareCurrent(boolean playWhenReady);

        void stopAtEndOfQueue();

        void skipToNext();
    }

    private final Supplier<QueueCompletionOperations> queueCompletionOperationsProvider;
    private final CompletionBoundary completionBoundary;

    PlaybackQueueCompletionOwner(
            Supplier<QueueCompletionOperations> queueCompletionOperationsProvider,
            CompletionBoundary completionBoundary
    ) {
        this.queueCompletionOperationsProvider = queueCompletionOperationsProvider;
        this.completionBoundary = completionBoundary;
    }

    static PlaybackQueueCompletionOwner fromPlaybackQueueManagerProvider(
            Supplier<PlaybackQueueManager> playbackQueueManagerProvider,
            CompletionBoundary completionBoundary
    ) {
        return new PlaybackQueueCompletionOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerProvider == null
                            ? null
                            : playbackQueueManagerProvider.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                },
                completionBoundary
        );
    }

    void playAfterCompletion() {
        QueueCompletionOperations operations = queueCompletionOperations();
        PlaybackQueueManager.PlaybackCompletionAction completionAction = operations == null
                ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                : operations.playbackCompletionAction();
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            stopAndClear();
            return;
        }
        if (operations != null) {
            operations.preparePlaybackCompletion(completionAction);
        }
        switch (completionAction) {
            case REPEAT_CURRENT:
                prepareCurrent(true);
                break;
            case STOP_AT_END:
                stopAtEndOfQueue();
                break;
            case ADVANCE_TO_NEXT:
                skipToNext();
                break;
            default:
                stopAndClear();
                break;
        }
    }

    boolean prepareStopAtEndOfQueue() {
        QueueCompletionOperations operations = queueCompletionOperations();
        if (operations == null) {
            return false;
        }
        operations.prepareStopAtEndOfQueue();
        return true;
    }

    void prepareStopAfterAutomaticAdvance(int completedIndex) {
        QueueCompletionOperations operations = queueCompletionOperations();
        if (operations != null) {
            operations.prepareStopAfterAutomaticAdvance(completedIndex);
        }
    }

    private QueueCompletionOperations queueCompletionOperations() {
        return queueCompletionOperationsProvider == null
                ? null
                : queueCompletionOperationsProvider.get();
    }

    private void stopAndClear() {
        if (completionBoundary != null) {
            completionBoundary.stopAndClear();
        }
    }

    private void prepareCurrent(boolean playWhenReady) {
        if (completionBoundary != null) {
            completionBoundary.prepareCurrent(playWhenReady);
        }
    }

    private void stopAtEndOfQueue() {
        if (completionBoundary != null) {
            completionBoundary.stopAtEndOfQueue();
        }
    }

    private void skipToNext() {
        if (completionBoundary != null) {
            completionBoundary.skipToNext();
        }
    }

    private static final class PlaybackQueueManagerOperations implements QueueCompletionOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public PlaybackQueueManager.PlaybackCompletionAction playbackCompletionAction() {
            return playbackQueueManager.playbackCompletionAction();
        }

        @Override
        public void preparePlaybackCompletion(PlaybackQueueManager.PlaybackCompletionAction action) {
            playbackQueueManager.preparePlaybackCompletion(action);
        }

        @Override
        public void prepareStopAtEndOfQueue() {
            playbackQueueManager.prepareStopAtEndOfQueue();
        }

        @Override
        public void prepareStopAfterAutomaticAdvance(int completedIndex) {
            playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
        }
    }
}
