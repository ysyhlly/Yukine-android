package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import java.util.Objects;

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
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.playbackQueueStore = playbackQueueStore;
        this.createPlayerIfNeeded = createPlayerIfNeeded;
        this.queuePlaybackActions = queuePlaybackActions;
    }

    void restoreLastPlayback(boolean playWhenRestored) {
        playbackQueueManager.restorePlaybackQueue();
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot = queueStateSnapshot();
        if (queueStateSnapshot.getQueueSize() <= 0) {
            publishState();
            return;
        }
        createPlayerIfNeeded();
        if (queueStateSnapshot.getCurrentTrack() == null) {
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
        return playbackQueueManager.queueStateSnapshot();
    }

}
