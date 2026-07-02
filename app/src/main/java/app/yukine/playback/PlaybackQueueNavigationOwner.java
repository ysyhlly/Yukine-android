package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackQueueNavigationOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Consumer<Boolean> mirroredQueueReuseHandler;

    PlaybackQueueNavigationOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Consumer<Boolean> mirroredQueueReuseHandler
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.mirroredQueueReuseHandler = mirroredQueueReuseHandler;
    }

    void playFirstQueuedTrack() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.playFirstQueuedTrack();
        }
    }

    void skipToNextImmediately() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null && playbackQueueManager.skipToNextImmediately()) {
            notifyMirroredQueueReused(true);
        }
    }

    void skipToPrevious() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null && playbackQueueManager.skipToPrevious()) {
            notifyMirroredQueueReused(true);
        }
    }

    boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        boolean reused = playbackQueueManager != null
                && playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
        if (reused) {
            notifyMirroredQueueReused(playWhenReady);
        }
        return reused;
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null
                ? null
                : playbackQueueManagerSupplier.get();
    }

    private void notifyMirroredQueueReused(boolean playWhenReady) {
        if (mirroredQueueReuseHandler != null) {
            mirroredQueueReuseHandler.accept(playWhenReady);
        }
    }
}
