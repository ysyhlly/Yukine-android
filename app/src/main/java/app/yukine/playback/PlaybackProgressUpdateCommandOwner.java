package app.yukine.playback;

import app.yukine.playback.manager.PlaybackProgressUpdateManager;

final class PlaybackProgressUpdateCommandOwner implements PlaybackProgressUpdateManager.Actions {
    interface StatePublisher {
        void publishState();
    }

    interface PositionPersister {
        void persistPlaybackPosition(boolean force);
    }

    private final StatePublisher statePublisher;
    private final PositionPersister positionPersister;

    PlaybackProgressUpdateCommandOwner(
            StatePublisher statePublisher,
            PositionPersister positionPersister
    ) {
        this.statePublisher = statePublisher;
        this.positionPersister = positionPersister;
    }

    @Override
    public void publishState() {
        statePublisher.publishState();
    }

    @Override
    public void persistPlaybackPosition() {
        positionPersister.persistPlaybackPosition(false);
    }
}
