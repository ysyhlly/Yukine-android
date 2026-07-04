package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueCompletionOwner {
    private final PlaybackQueueManager playbackQueueManager;
    private final Runnable stopAndClearAction;
    private final Runnable stopAtEndOfQueueAction;
    private final Runnable skipToNextAction;

    PlaybackQueueCompletionOwner(
            PlaybackQueueManager playbackQueueManager,
            Runnable stopAndClearAction,
            Runnable stopAtEndOfQueueAction,
            Runnable skipToNextAction
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.stopAndClearAction = stopAndClearAction;
        this.stopAtEndOfQueueAction = stopAtEndOfQueueAction;
        this.skipToNextAction = skipToNextAction;
    }

    void playAfterCompletion() {
        PlaybackQueueManager.PlaybackCompletionAction completionAction = playbackQueueManager == null
                ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                : playbackQueueManager.preparePlaybackCompletionAction();
        if (completionAction == null) {
            return;
        }
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            stopAndClearPlayback();
            return;
        }
        switch (completionAction) {
            case STOP_AT_END:
                stopAtEndOfQueue();
                break;
            case ADVANCE_TO_NEXT:
                run(skipToNextAction);
                break;
            default:
                stopAndClearPlayback();
                break;
        }
    }

    void stopAndClearPlayback() {
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAndClearPlaybackState();
        }
        run(stopAndClearAction);
    }

    void stopAtEndOfQueue() {
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAtEndOfQueue();
        }
        run(stopAtEndOfQueueAction);
    }

    void stopAfterAutomaticAdvance(int completedIndex) {
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
        }
        run(stopAtEndOfQueueAction);
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
