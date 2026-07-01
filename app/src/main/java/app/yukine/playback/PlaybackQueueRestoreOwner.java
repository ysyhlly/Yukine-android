package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class PlaybackQueueRestoreOwner {
    interface RestorePlaybackBoundary {
        void createPlayerIfNeeded();

        void prepareCurrent(boolean playWhenReady);

        void publishState();
    }

    private final Runnable restorePlaybackQueue;
    private final Function<Boolean, PlaybackQueueManager.RestorePlaybackResult> restoreLastPlayback;
    private final Consumer<Boolean> setPlaybackRestoreEnabled;
    private final RestorePlaybackBoundary restorePlaybackBoundary;

    PlaybackQueueRestoreOwner(
            Runnable restorePlaybackQueue,
            Function<Boolean, PlaybackQueueManager.RestorePlaybackResult> restoreLastPlayback,
            Consumer<Boolean> setPlaybackRestoreEnabled,
            RestorePlaybackBoundary restorePlaybackBoundary
    ) {
        this.restorePlaybackQueue = restorePlaybackQueue;
        this.restoreLastPlayback = restoreLastPlayback;
        this.setPlaybackRestoreEnabled = setPlaybackRestoreEnabled;
        this.restorePlaybackBoundary = restorePlaybackBoundary;
    }

    static PlaybackQueueRestoreOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            RestorePlaybackBoundary restorePlaybackBoundary
    ) {
        return new PlaybackQueueRestoreOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.restorePlaybackQueue();
                    }
                },
                playWhenRestored -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : playbackQueueManager.restoreLastPlayback(playWhenRestored);
                },
                enabled -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.setPlaybackRestoreEnabled(enabled);
                    }
                },
                restorePlaybackBoundary
        );
    }

    void restoreLastPlayback(boolean playWhenRestored) {
        PlaybackQueueManager.RestorePlaybackResult restoreResult = restoreLastPlayback == null
                ? PlaybackQueueManager.RestorePlaybackResult.empty()
                : restoreLastPlayback.apply(playWhenRestored);
        if (restoreResult == null) {
            restoreResult = PlaybackQueueManager.RestorePlaybackResult.empty();
        }
        if (restorePlaybackBoundary == null) {
            return;
        }
        if (restoreResult.getShouldCreatePlayer()) {
            restorePlaybackBoundary.createPlayerIfNeeded();
        }
        if (!restoreResult.getShouldPrepare()) {
            restorePlaybackBoundary.publishState();
            return;
        }
        restorePlaybackBoundary.prepareCurrent(restoreResult.getPlayWhenReady());
    }

    void restorePlaybackQueue() {
        if (restorePlaybackQueue != null) {
            restorePlaybackQueue.run();
        }
    }

    void setPlaybackRestoreEnabled(boolean enabled) {
        if (setPlaybackRestoreEnabled != null) {
            setPlaybackRestoreEnabled.accept(enabled);
        }
    }
}
