package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;

final class PlaybackQueueNavigationOwner {
    private final PlaybackQueueManager playbackQueueManager;
    private final Consumer<Boolean> mirroredQueueReuseHandler;

    PlaybackQueueNavigationOwner(
            PlaybackQueueManager playbackQueueManager,
            Consumer<Boolean> mirroredQueueReuseHandler
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.mirroredQueueReuseHandler = mirroredQueueReuseHandler;
    }

    void playFirstQueuedTrack() {
        if (playbackQueueManager != null) {
            playbackQueueManager.playFirstQueuedTrack();
        }
    }

    void skipToNextImmediately() {
        if (playbackQueueManager != null && playbackQueueManager.skipToNextImmediately()) {
            notifyMirroredQueueReused(true);
        }
    }

    void skipToPrevious() {
        if (playbackQueueManager != null && playbackQueueManager.skipToPrevious()) {
            notifyMirroredQueueReused(true);
        }
    }

    boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
        boolean reused = playbackQueueManager != null
                && playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
        if (reused) {
            notifyMirroredQueueReused(playWhenReady);
        }
        return reused;
    }

    private void notifyMirroredQueueReused(boolean playWhenReady) {
        if (mirroredQueueReuseHandler != null) {
            mirroredQueueReuseHandler.accept(playWhenReady);
        }
    }
}
