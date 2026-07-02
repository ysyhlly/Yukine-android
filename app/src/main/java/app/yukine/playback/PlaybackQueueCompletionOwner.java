package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueCompletionOwner {
    interface CompletionBoundary {
        void stopAndClear();

        void stopAtEndOfQueue();

        void skipToNext();

        void prepareStopAndClearFallbackState();

        void prepareStopAtEndFallbackState();
    }

    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions;
    private final CompletionBoundary completionBoundary;

    PlaybackQueueCompletionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions,
            CompletionBoundary completionBoundary
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.queuePlaybackActions = queuePlaybackActions;
        this.completionBoundary = completionBoundary;
    }

    void playAfterCompletion() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.PlaybackCompletionAction completionAction = playbackQueueManager == null
                ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                : playbackQueueManager.preparePlaybackCompletionAction();
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            if (completionBoundary != null) {
                completionBoundary.stopAndClear();
            }
            return;
        }
        switch (completionAction) {
            case REPEAT_CURRENT:
                prepareCurrent(true);
                break;
            case STOP_AT_END:
                if (completionBoundary != null) {
                    completionBoundary.stopAtEndOfQueue();
                }
                break;
            case ADVANCE_TO_NEXT:
                if (completionBoundary != null) {
                    completionBoundary.skipToNext();
                }
                break;
            default:
                if (completionBoundary != null) {
                    completionBoundary.stopAndClear();
                }
                break;
        }
    }

    void prepareStopAndClearPlaybackState() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager == null) {
            if (completionBoundary != null) {
                completionBoundary.prepareStopAndClearFallbackState();
            }
            return;
        }
        playbackQueueManager.prepareStopAndClearPlaybackState();
    }

    void prepareStopAtEndOfQueue() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager == null) {
            if (completionBoundary != null) {
                completionBoundary.prepareStopAtEndFallbackState();
            }
            return;
        }
        playbackQueueManager.prepareStopAtEndOfQueue();
    }

    void prepareStopAfterAutomaticAdvance(int completedIndex) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
        }
    }

    private void prepareCurrent(boolean playWhenReady) {
        if (queuePlaybackActions != null) {
            queuePlaybackActions.prepareCurrent(playWhenReady);
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
