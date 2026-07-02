package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class PlaybackQueueRestoreOwner {
    private final Runnable restorePlaybackQueue;
    private final Function<Boolean, PlaybackQueueManager.RestorePlaybackResult> restoreLastPlayback;
    private final Consumer<Boolean> setPlaybackRestoreEnabled;
    private final Runnable createPlayerIfNeeded;
    private final Consumer<Boolean> prepareCurrent;
    private final Runnable statePublisher;

    PlaybackQueueRestoreOwner(
            Runnable restorePlaybackQueue,
            Function<Boolean, PlaybackQueueManager.RestorePlaybackResult> restoreLastPlayback,
            Consumer<Boolean> setPlaybackRestoreEnabled,
            Runnable createPlayerIfNeeded,
            Consumer<Boolean> prepareCurrent,
            Runnable statePublisher
    ) {
        this.restorePlaybackQueue = restorePlaybackQueue;
        this.restoreLastPlayback = restoreLastPlayback;
        this.setPlaybackRestoreEnabled = setPlaybackRestoreEnabled;
        this.createPlayerIfNeeded = createPlayerIfNeeded;
        this.prepareCurrent = prepareCurrent;
        this.statePublisher = statePublisher;
    }

    static PlaybackQueueRestoreOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Runnable createPlayerIfNeeded,
            Consumer<Boolean> prepareCurrent,
            Runnable statePublisher
    ) {
        return new PlaybackQueueRestoreOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.restorePlaybackQueue();
                    }
                },
                playWhenRestored -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    return playbackQueueManager == null
                            ? null
                            : playbackQueueManager.restoreLastPlayback(playWhenRestored);
                },
                enabled -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.setPlaybackRestoreEnabled(enabled);
                    }
                },
                createPlayerIfNeeded,
                prepareCurrent,
                statePublisher
        );
    }

    private static PlaybackQueueManager playbackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }

    void restoreLastPlayback(boolean playWhenRestored) {
        PlaybackQueueManager.RestorePlaybackResult restoreResult = restoreLastPlayback == null
                ? PlaybackQueueManager.RestorePlaybackResult.empty()
                : restoreLastPlayback.apply(playWhenRestored);
        if (restoreResult == null) {
            restoreResult = PlaybackQueueManager.RestorePlaybackResult.empty();
        }
        if (restoreResult.getShouldCreatePlayer()) {
            createPlayerIfNeeded();
        }
        if (!restoreResult.getShouldPrepare()) {
            publishState();
            return;
        }
        prepareCurrent(restoreResult.getPlayWhenReady());
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

    private void createPlayerIfNeeded() {
        if (createPlayerIfNeeded != null) {
            createPlayerIfNeeded.run();
        }
    }

    private void prepareCurrent(boolean playWhenReady) {
        if (prepareCurrent != null) {
            prepareCurrent.accept(playWhenReady);
        }
    }

    private void publishState() {
        if (statePublisher != null) {
            statePublisher.run();
        }
    }
}
