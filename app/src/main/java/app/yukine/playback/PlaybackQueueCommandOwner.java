package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    interface PlaybackPreparer {
        void prepareCurrent(boolean playWhenReady);
    }

    interface StatePublisher {
        void publishState();
    }

    private final PlaybackPreparer playbackPreparer;
    private final StatePublisher statePublisher;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;

    PlaybackQueueCommandOwner(
            PlaybackPreparer playbackPreparer,
            StatePublisher statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands
    ) {
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
        this.playbackCommands = playbackCommands;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        playbackPreparer.prepareCurrent(playWhenReady);
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }

    @Override
    public void stopAndClear() {
        playbackCommands.stopAndClear();
    }
}
