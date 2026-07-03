package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueCompletionOwner {
    interface CompletionBoundary {
        void stopAndClear();

        void stopAtEndOfQueue();

        void skipToNext();
    }

    private final PlaybackQueueManager playbackQueueManager;
    private final CompletionBoundary completionBoundary;
    private final PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions;

    PlaybackQueueCompletionOwner(
            PlaybackQueueManager playbackQueueManager,
            CompletionBoundary completionBoundary,
            PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.completionBoundary = completionBoundary;
        this.queuePlaybackActions = queuePlaybackActions;
    }

    void playAfterCompletion() {
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
                if (queuePlaybackActions != null) {
                    queuePlaybackActions.prepareCurrent(true);
                }
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
        if (playbackQueueManager == null) {
            return;
        }
        playbackQueueManager.prepareStopAndClearPlaybackState();
    }

    void prepareStopAtEndOfQueue() {
        if (playbackQueueManager == null) {
            return;
        }
        playbackQueueManager.prepareStopAtEndOfQueue();
    }

    void prepareStopAfterAutomaticAdvance(int completedIndex) {
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
        }
    }
}
