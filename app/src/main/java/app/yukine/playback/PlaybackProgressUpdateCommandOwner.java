package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

import java.util.function.Consumer;

final class PlaybackProgressUpdateCommandOwner implements PlaybackProgressUpdateManager.Actions {
    private final Runnable statePublisher;
    private final Consumer<Boolean> positionPersister;

    PlaybackProgressUpdateCommandOwner(
            Runnable statePublisher,
            Consumer<Boolean> positionPersister
    ) {
        this.statePublisher = statePublisher;
        this.positionPersister = positionPersister;
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }

    @Override
    public void persistPlaybackPosition() {
        positionPersister.accept(false);
    }
}
