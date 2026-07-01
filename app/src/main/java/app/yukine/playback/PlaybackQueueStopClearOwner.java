package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueStopClearOwner {
    private final Supplier<Runnable> prepareStopAndClearPlaybackStateSupplier;

    PlaybackQueueStopClearOwner(Supplier<Runnable> prepareStopAndClearPlaybackStateSupplier) {
        this.prepareStopAndClearPlaybackStateSupplier = prepareStopAndClearPlaybackStateSupplier;
    }

    static PlaybackQueueStopClearOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueStopClearOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : playbackQueueManager::prepareStopAndClearPlaybackState;
                }
        );
    }

    boolean prepareStopAndClearPlaybackState() {
        Runnable prepareStopAndClearPlaybackState = prepareStopAndClearPlaybackStateAction();
        if (prepareStopAndClearPlaybackState == null) {
            return false;
        }
        prepareStopAndClearPlaybackState.run();
        return true;
    }

    private Runnable prepareStopAndClearPlaybackStateAction() {
        return prepareStopAndClearPlaybackStateSupplier == null
                ? null
                : prepareStopAndClearPlaybackStateSupplier.get();
    }
}
