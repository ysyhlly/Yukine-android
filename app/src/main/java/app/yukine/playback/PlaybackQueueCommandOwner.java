package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable statePublisher;
    private final Runnable stopAndClearCommand;

    PlaybackQueueCommandOwner(
            Consumer<Boolean> playbackPreparer,
            Runnable statePublisher,
            Runnable stopAndClearCommand
    ) {
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
        this.stopAndClearCommand = stopAndClearCommand;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        playbackPreparer.accept(playWhenReady);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }

    @Override
    public void stopAndClear() {
        stopAndClearCommand.run();
    }
}
