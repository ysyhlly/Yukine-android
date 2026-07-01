package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

final class PlaybackQueueCompletionOwner {
    interface CompletionBoundary {
        void stopAndClear();

        void prepareCurrent(boolean playWhenReady);

        void stopAtEndOfQueue();

        void skipToNext();
    }

    private final Supplier<PlaybackQueueManager.PlaybackCompletionAction> playbackCompletionAction;
    private final Consumer<PlaybackQueueManager.PlaybackCompletionAction> preparePlaybackCompletion;
    private final BooleanSupplier prepareStopAtEndOfQueue;
    private final IntConsumer prepareStopAfterAutomaticAdvance;
    private final CompletionBoundary completionBoundary;

    PlaybackQueueCompletionOwner(
            Supplier<PlaybackQueueManager.PlaybackCompletionAction> playbackCompletionAction,
            Consumer<PlaybackQueueManager.PlaybackCompletionAction> preparePlaybackCompletion,
            BooleanSupplier prepareStopAtEndOfQueue,
            IntConsumer prepareStopAfterAutomaticAdvance,
            CompletionBoundary completionBoundary
    ) {
        this.playbackCompletionAction = playbackCompletionAction;
        this.preparePlaybackCompletion = preparePlaybackCompletion;
        this.prepareStopAtEndOfQueue = prepareStopAtEndOfQueue;
        this.prepareStopAfterAutomaticAdvance = prepareStopAfterAutomaticAdvance;
        this.completionBoundary = completionBoundary;
    }

    static PlaybackQueueCompletionOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            CompletionBoundary completionBoundary
    ) {
        return new PlaybackQueueCompletionOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                            : playbackQueueManager.playbackCompletionAction();
                },
                action -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.preparePlaybackCompletion(action);
                    }
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager == null) {
                        return false;
                    }
                    playbackQueueManager.prepareStopAtEndOfQueue();
                    return true;
                },
                completedIndex -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.prepareStopAfterAutomaticAdvance(completedIndex);
                    }
                },
                completionBoundary
        );
    }

    void playAfterCompletion() {
        PlaybackQueueManager.PlaybackCompletionAction completionAction = playbackCompletionAction == null
                ? PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR
                : playbackCompletionAction.get();
        if (completionAction == PlaybackQueueManager.PlaybackCompletionAction.STOP_AND_CLEAR) {
            stopAndClear();
            return;
        }
        if (preparePlaybackCompletion != null) {
            preparePlaybackCompletion.accept(completionAction);
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
        return prepareStopAtEndOfQueue != null
                && prepareStopAtEndOfQueue.getAsBoolean();
    }

    void prepareStopAfterAutomaticAdvance(int completedIndex) {
        if (prepareStopAfterAutomaticAdvance != null) {
            prepareStopAfterAutomaticAdvance.accept(completedIndex);
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
}
