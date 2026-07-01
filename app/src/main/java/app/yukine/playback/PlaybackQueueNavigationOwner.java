package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackQueueNavigationOwner {
    interface MirroredQueueReuseHandler {
        void onMirroredQueueReused(boolean playWhenReady);
    }

    private final Runnable playFirstQueuedTrack;
    private final BooleanSupplier skipToNextImmediately;
    private final BooleanSupplier skipToPrevious;
    private final BiPredicate<Boolean, Long> reuseMirroredQueueIfAvailable;
    private final MirroredQueueReuseHandler mirroredQueueReuseHandler;

    PlaybackQueueNavigationOwner(
            Runnable playFirstQueuedTrack,
            BooleanSupplier skipToNextImmediately,
            BooleanSupplier skipToPrevious,
            BiPredicate<Boolean, Long> reuseMirroredQueueIfAvailable,
            MirroredQueueReuseHandler mirroredQueueReuseHandler
    ) {
        this.playFirstQueuedTrack = playFirstQueuedTrack;
        this.skipToNextImmediately = skipToNextImmediately;
        this.skipToPrevious = skipToPrevious;
        this.reuseMirroredQueueIfAvailable = reuseMirroredQueueIfAvailable;
        this.mirroredQueueReuseHandler = mirroredQueueReuseHandler;
    }

    static PlaybackQueueNavigationOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            MirroredQueueReuseHandler mirroredQueueReuseHandler
    ) {
        return new PlaybackQueueNavigationOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    if (playbackQueueManager != null) {
                        playbackQueueManager.playFirstQueuedTrack();
                    }
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    return playbackQueueManager != null
                            && playbackQueueManager.skipToNextImmediately();
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    return playbackQueueManager != null
                            && playbackQueueManager.skipToPrevious();
                },
                (playWhenReady, startPositionMs) -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManager(playbackQueueManagerSupplier);
                    return playbackQueueManager != null
                            && playbackQueueManager.reuseMirroredQueueIfAvailable(playWhenReady, startPositionMs);
                },
                mirroredQueueReuseHandler
        );
    }

    void playFirstQueuedTrack() {
        if (playFirstQueuedTrack != null) {
            playFirstQueuedTrack.run();
        }
    }

    void skipToNextImmediately() {
        if (skipToNextImmediately != null && skipToNextImmediately.getAsBoolean()) {
            notifyMirroredQueueReused(true);
        }
    }

    void skipToPrevious() {
        if (skipToPrevious != null && skipToPrevious.getAsBoolean()) {
            notifyMirroredQueueReused(true);
        }
    }

    boolean reuseMirroredQueueIfAvailable(boolean playWhenReady, long startPositionMs) {
        boolean reused = reuseMirroredQueueIfAvailable != null
                && reuseMirroredQueueIfAvailable.test(playWhenReady, startPositionMs);
        if (reused) {
            notifyMirroredQueueReused(playWhenReady);
        }
        return reused;
    }

    private static PlaybackQueueManager playbackQueueManager(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier) {
        return playbackQueueManagerSupplier == null
                ? null
                : playbackQueueManagerSupplier.get();
    }

    private void notifyMirroredQueueReused(boolean playWhenReady) {
        if (mirroredQueueReuseHandler != null) {
            mirroredQueueReuseHandler.onMirroredQueueReused(playWhenReady);
        }
    }
}
