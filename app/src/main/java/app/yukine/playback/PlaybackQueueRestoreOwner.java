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
        if (playbackQueueManager != null) {
            playbackQueueManager.restorePlaybackQueue();
        }
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot = queueStateSnapshot();
        if (queueStateSnapshot.isQueueEmpty()) {
            publishState();
            return;
        }
        createPlayerIfNeeded();
        if (!queueStateSnapshot.getHasCurrentTrack()) {
            publishState();
            return;
        }
        prepareCurrent(playWhenRestored || loadResumeRequested());
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

    private boolean loadResumeRequested() {
        return playbackQueueStore != null && playbackQueueStore.loadResumeRequested();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

}
