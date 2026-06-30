package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    interface PlaybackStateProvider {
        boolean isPlaying();
    }

    interface PlaybackPreparer {
        void prepareCurrent(boolean playWhenReady);
    }

    interface StatePublisher {
        void publishState();
    }

    private final PlaybackStateProvider playbackStateProvider;
    private final PlaybackPreparer playbackPreparer;
    private final StatePublisher statePublisher;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;

    PlaybackQueueCommandOwner(
            PlaybackStateProvider playbackStateProvider,
            PlaybackPreparer playbackPreparer,
            StatePublisher statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands
    ) {
        this.playbackStateProvider = playbackStateProvider;
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
        this.playbackCommands = playbackCommands;
    }

    @Override
    public boolean isPlaying() {
        return playbackStateProvider.isPlaying();
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
