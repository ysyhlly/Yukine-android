package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable statePublisher;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;

    PlaybackQueueCommandOwner(
            Consumer<Boolean> playbackPreparer,
            Runnable statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands
    ) {
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
        this.playbackCommands = playbackCommands;
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
        playbackCommands.stopAndClear();
    }
}
