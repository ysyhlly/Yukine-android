package app.yukine.playback;

import app.yukine.playback.manager.PlaybackSleepTimerManager;

final class PlaybackSleepTimerCommandOwner implements PlaybackSleepTimerManager.Actions {
    interface StatePublisher {
        void publishState();
    }

    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final StatePublisher statePublisher;

    PlaybackSleepTimerCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            StatePublisher statePublisher
    ) {
        this.playbackCommands = playbackCommands;
        this.statePublisher = statePublisher;
    }

    @Override
    public void pausePlayback() {
        playbackCommands.pause();
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }
}
