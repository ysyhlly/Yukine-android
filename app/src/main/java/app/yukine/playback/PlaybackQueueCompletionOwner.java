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
            run(stopAndClearAction);
            return;
        }
        switch (completionAction) {
            case STOP_AT_END:
                run(stopAtEndOfQueueAction);
                break;
            case ADVANCE_TO_NEXT:
                run(skipToNextAction);
                break;
            default:
                run(stopAndClearAction);
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

    void stopAfterAutomaticAdvance(int completedIndex) {
        prepareStopAfterAutomaticAdvance(completedIndex);
        run(stopAtEndOfQueueAction);
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
