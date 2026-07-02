package app.yukine.playback;

import app.yukine.playback.manager.PlaybackSleepTimerManager;

final class PlaybackSleepTimerCommandOwner implements PlaybackSleepTimerManager.Actions {
    interface StatePublisher {
        void publishState();
    }

    interface SleepTimerManagerProvider {
        PlaybackSleepTimerManager playbackSleepTimerManager();
    }

    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final StatePublisher statePublisher;
    private final SleepTimerManagerProvider sleepTimerManagerProvider;

    PlaybackSleepTimerCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            StatePublisher statePublisher
    ) {
        this(playbackCommands, statePublisher, null);
    }

    PlaybackSleepTimerCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            StatePublisher statePublisher,
            SleepTimerManagerProvider sleepTimerManagerProvider
    ) {
        this.playbackCommands = playbackCommands;
        this.statePublisher = statePublisher;
        this.sleepTimerManagerProvider = sleepTimerManagerProvider;
    }

    @Override
    public void pausePlayback() {
        playbackCommands.pause();
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }

    void cancelSleepTimer(boolean publish) {
        PlaybackSleepTimerManager manager = sleepTimerManager();
        if (manager != null) {
            manager.cancel(publish);
        }
    }

    void startSleepTimerMinutes(int minutes) {
        PlaybackSleepTimerManager manager = sleepTimerManager();
        if (manager != null) {
            manager.startMinutes(minutes);
        }
    }

    long sleepTimerRemainingMs() {
        PlaybackSleepTimerManager manager = sleepTimerManager();
        return manager == null ? 0L : manager.remainingMs();
    }

    private PlaybackSleepTimerManager sleepTimerManager() {
        return sleepTimerManagerProvider == null
                ? null
                : sleepTimerManagerProvider.playbackSleepTimerManager();
    }
}
