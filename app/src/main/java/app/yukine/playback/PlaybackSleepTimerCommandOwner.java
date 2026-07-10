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
    private final Runnable pausePlaybackAction;

    PlaybackSleepTimerCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            StatePublisher statePublisher
    ) {
        this(playbackCommands, statePublisher, null, null);
    }

    PlaybackSleepTimerCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            StatePublisher statePublisher,
            SleepTimerManagerProvider sleepTimerManagerProvider
    ) {
        this(playbackCommands, statePublisher, sleepTimerManagerProvider, null);
    }

    PlaybackSleepTimerCommandOwner(
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            StatePublisher statePublisher,
            SleepTimerManagerProvider sleepTimerManagerProvider,
            Runnable pausePlaybackAction
    ) {
        this.playbackCommands = playbackCommands;
        this.statePublisher = statePublisher;
        this.sleepTimerManagerProvider = sleepTimerManagerProvider;
        this.pausePlaybackAction = pausePlaybackAction;
    }

    @Override
    public void pausePlayback() {
        if (pausePlaybackAction != null) {
            pausePlaybackAction.run();
            return;
        }
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
