package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackProgressUpdateCommandOwner implements PlaybackProgressUpdateManager.Actions {
    private final Runnable statePublisher;
    private final Consumer<Boolean> positionPersister;
    private final Supplier<PlaybackProgressUpdateManager> progressUpdateManagerProvider;

    PlaybackProgressUpdateCommandOwner(
            Runnable statePublisher,
            Consumer<Boolean> positionPersister
    ) {
        this(statePublisher, positionPersister, null);
    }

    PlaybackProgressUpdateCommandOwner(
            Runnable statePublisher,
            Consumer<Boolean> positionPersister,
            Supplier<PlaybackProgressUpdateManager> progressUpdateManagerProvider
    ) {
        this.statePublisher = statePublisher;
        this.positionPersister = positionPersister;
        this.progressUpdateManagerProvider = progressUpdateManagerProvider;
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }

    @Override
    public void persistPlaybackPosition() {
        positionPersister.accept(false);
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
                : progressUpdateManagerProvider.get();
    }
}
