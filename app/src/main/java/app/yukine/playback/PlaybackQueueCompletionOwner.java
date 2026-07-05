package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;

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
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.stopAndClearAction = Objects.requireNonNull(stopAndClearAction, "stopAndClearAction");
        this.stopAtEndOfQueueAction = Objects.requireNonNull(
                stopAtEndOfQueueAction,
                "stopAtEndOfQueueAction"
        );
        this.skipToNextAction = Objects.requireNonNull(skipToNextAction, "skipToNextAction");
    }

    void playAfterCompletion() {
        PlaybackQueueManager.PlaybackCompletionAction completionAction =
                playbackQueueManager.preparePlaybackCompletionAction();
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.REPEAT_CURRENT) {
            return;
        }
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            stopAndClearPlayback();
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
                stopAndClearPlayback();
                break;
        }
    }

    void stopAndClearPlayback() {
        playbackQueueManager.prepareStopAndClearPlaybackState();
        run(stopAndClearAction);
    }

    private static void run(Runnable action) {
        action.run();
    }
}
