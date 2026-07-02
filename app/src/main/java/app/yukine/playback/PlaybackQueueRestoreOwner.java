package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackQueueRestoreOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Runnable createPlayerIfNeeded;
    private final Consumer<Boolean> prepareCurrent;
    private final Runnable statePublisher;

    PlaybackQueueRestoreOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Runnable createPlayerIfNeeded,
            Consumer<Boolean> prepareCurrent,
            Runnable statePublisher
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
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
                playbackQueueManagerSupplier,
                createPlayerIfNeeded,
                prepareCurrent,
                statePublisher
        );
    }

    void restoreLastPlayback(boolean playWhenRestored) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.RestorePlaybackResult restoreResult = playbackQueueManager == null
                ? PlaybackQueueManager.RestorePlaybackResult.empty()
                : playbackQueueManager.restoreLastPlayback(playWhenRestored);
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
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.restorePlaybackQueue();
        }
    }

    void setPlaybackRestoreEnabled(boolean enabled) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.setPlaybackRestoreEnabled(enabled);
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

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
