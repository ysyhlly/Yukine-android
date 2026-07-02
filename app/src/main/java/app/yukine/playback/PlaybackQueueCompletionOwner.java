package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueCompletionOwner {
    interface CompletionBoundary {
        void stopAndClear();

        void stopAtEndOfQueue();

        void skipToNext();

        void repeatCurrent();
    }

    private final PlaybackQueueManager playbackQueueManager;
    private final CompletionBoundary completionBoundary;

    PlaybackQueueCompletionOwner(
            PlaybackQueueManager playbackQueueManager,
            CompletionBoundary completionBoundary
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.completionBoundary = completionBoundary;
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
                if (completionBoundary != null) {
                    completionBoundary.repeatCurrent();
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
