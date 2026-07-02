package app.yukine.playback;

import app.yukine.playback.manager.PlaybackSleepTimerManager;

import java.util.function.Supplier;

final class PlaybackSleepTimerCommandOwner implements PlaybackSleepTimerManager.Actions {
    private final Runnable pauseCommand;
    private final Runnable statePublisher;
    private final Supplier<PlaybackSleepTimerManager> sleepTimerManagerProvider;

    PlaybackSleepTimerCommandOwner(
            Runnable pauseCommand,
            Runnable statePublisher
    ) {
        this(pauseCommand, statePublisher, null);
    }

    PlaybackSleepTimerCommandOwner(
            Runnable pauseCommand,
            Runnable statePublisher,
            Supplier<PlaybackSleepTimerManager> sleepTimerManagerProvider
    ) {
        this.pauseCommand = pauseCommand;
        this.statePublisher = statePublisher;
        this.sleepTimerManagerProvider = sleepTimerManagerProvider;
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
        return sleepTimerManagerProvider == null
                ? null
                : sleepTimerManagerProvider.get();
    }
}
