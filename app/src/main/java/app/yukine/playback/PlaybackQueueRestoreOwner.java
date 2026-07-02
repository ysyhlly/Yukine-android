package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueRestoreOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Runnable createPlayerIfNeeded;
    private final PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions;

    PlaybackQueueRestoreOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Runnable createPlayerIfNeeded,
            PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.createPlayerIfNeeded = createPlayerIfNeeded;
        this.queuePlaybackActions = queuePlaybackActions;
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
        if (queuePlaybackActions != null) {
            queuePlaybackActions.prepareCurrent(playWhenReady);
        }
    }

    private void publishState() {
        if (queuePlaybackActions != null) {
            queuePlaybackActions.publishState();
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
