package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

final class PlaybackProgressUpdateCommandOwner implements PlaybackProgressUpdateManager.Actions {
    interface StatePublisher {
        void publishState();
    }

    interface PositionPersister {
        void persistPlaybackPosition(boolean force);
    }

    interface ProgressUpdateManagerProvider {
        PlaybackProgressUpdateManager playbackProgressUpdateManager();
    }

    private final StatePublisher statePublisher;
    private final PositionPersister positionPersister;
    private final ProgressUpdateManagerProvider progressUpdateManagerProvider;

    PlaybackProgressUpdateCommandOwner(
            StatePublisher statePublisher,
            PositionPersister positionPersister
    ) {
        this(statePublisher, positionPersister, null);
    }

    PlaybackProgressUpdateCommandOwner(
            StatePublisher statePublisher,
            PositionPersister positionPersister,
            ProgressUpdateManagerProvider progressUpdateManagerProvider
    ) {
        this.statePublisher = statePublisher;
        this.positionPersister = positionPersister;
        this.progressUpdateManagerProvider = progressUpdateManagerProvider;
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }

    @Override
    public void persistPlaybackPosition() {
        positionPersister.persistPlaybackPosition(false);
    }

    void startProgressUpdates() {
        PlaybackProgressUpdateManager manager = progressUpdateManager();
        if (manager != null) {
            manager.startIfNeeded();
        }
    }

    void stopProgressUpdates() {
        PlaybackProgressUpdateManager manager = progressUpdateManager();
        if (manager != null) {
            manager.stop();
        }
    }

    private PlaybackProgressUpdateManager progressUpdateManager() {
        return progressUpdateManagerProvider == null
                ? null
                : progressUpdateManagerProvider.playbackProgressUpdateManager();
    }
}
