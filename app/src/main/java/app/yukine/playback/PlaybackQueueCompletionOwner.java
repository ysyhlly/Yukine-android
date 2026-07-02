package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueCompletionOwner {
    interface CompletionBoundary {
        void stopAndClear();

        void prepareCurrent(boolean playWhenReady);

        void stopAtEndOfQueue();

        void skipToNext();

        void prepareStopAndClearFallbackState();
    }

    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final CompletionBoundary completionBoundary;

    PlaybackQueueCompletionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            CompletionBoundary completionBoundary
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.completionBoundary = completionBoundary;
    }

    void playAfterCompletion() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.PlaybackCompletionAction completionAction = playbackQueueManager == null
                ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                : playbackQueueManager.preparePlaybackCompletionAction();
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            stopAndClear();
            return;
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

    void prepareStopAndClearPlaybackState() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager == null) {
            prepareStopAndClearFallbackState();
            return;
        }
        playbackQueueManager.prepareStopAndClearPlaybackState();
    }

    boolean prepareStopAtEndOfQueue() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager == null) {
            return false;
        }
        playbackQueueManager.prepareStopAtEndOfQueue();
        return true;
    }

    void prepareStopAfterAutomaticAdvance(int completedIndex) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
        }
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

    private void prepareStopAndClearFallbackState() {
        if (completionBoundary != null) {
            completionBoundary.prepareStopAndClearFallbackState();
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
