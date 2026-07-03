package app.yukine.playback;

import app.yukine.playback.manager.PlaybackSleepTimerManager;

final class PlaybackSleepTimerCommandOwner implements PlaybackSleepTimerManager.Actions {
    private final Runnable pauseCommand;
    private final Runnable statePublisher;
    private PlaybackSleepTimerManager sleepTimerManager;

    PlaybackSleepTimerCommandOwner(
            Runnable pauseCommand,
            Runnable statePublisher
    ) {
        this.pauseCommand = pauseCommand;
        this.statePublisher = statePublisher;
    }

    void bindPlaybackSleepTimerManager(PlaybackSleepTimerManager sleepTimerManager) {
        this.sleepTimerManager = sleepTimerManager;
    }

    @Override
    public void pausePlayback() {
        pauseCommand.run();
    }

    @Override
    public void publishState() {
        statePublisher.run();
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
        return sleepTimerManager;
    }
}
