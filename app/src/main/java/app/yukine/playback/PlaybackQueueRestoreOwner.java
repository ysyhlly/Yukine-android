package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

final class PlaybackQueueRestoreOwner {
    private final PlaybackQueueManager playbackQueueManager;
    private final PlaybackQueueStore playbackQueueStore;
    private final Runnable createPlayerIfNeeded;
    private final PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions;

    PlaybackQueueRestoreOwner(
            PlaybackQueueManager playbackQueueManager,
            PlaybackQueueStore playbackQueueStore,
            Runnable createPlayerIfNeeded,
            PlaybackQueueManager.QueuePlaybackActions queuePlaybackActions
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.playbackQueueStore = playbackQueueStore;
        this.createPlayerIfNeeded = createPlayerIfNeeded;
        this.queuePlaybackActions = queuePlaybackActions;
    }

    void restoreLastPlayback(boolean playWhenRestored) {
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
        if (playbackQueueManager != null) {
            playbackQueueManager.restorePlaybackQueue();
        }
    }

    void setPlaybackRestoreEnabled(boolean enabled) {
        if (playbackQueueStore != null) {
            playbackQueueStore.savePlaybackRestoreEnabled(enabled);
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

}
