package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

final class PlaybackCurrentTrackPreparationOwner {
    interface PreparationProvider {
        PlaybackMediaSourceProvider.PlaybackPreparation prepareTrackForPlayback(Track track);
    }

    interface QueuePreparationController {
        void replaceCurrentQueueTrack(Track track);
        long restoredPositionFor(Track track);
    }

    interface RuntimeStateController {
        void setPreparing(boolean preparing);
        void setErrorMessage(String message);
    }

    interface StatePublisher {
        void publishState();
    }

    interface RefusalLogger {
        void logRefusingToPrepareEmptyUri(Track track);
    }

    static final class PreparedTrack {
        private final Track track;
        private final long startPositionMs;
        private final boolean playable;

        private PreparedTrack(Track track, long startPositionMs, boolean playable) {
            this.track = track;
            this.startPositionMs = startPositionMs;
            this.playable = playable;
        }

        static PreparedTrack playable(Track track, long startPositionMs) {
            return new PreparedTrack(track, Math.max(0L, startPositionMs), true);
        }

        static PreparedTrack unplayable(Track track) {
            return new PreparedTrack(track, 0L, false);
        }

        Track track() {
            return track;
        }

        long startPositionMs() {
            return startPositionMs;
        }

        boolean playable() {
            return playable;
        }
    }

    private final PreparationProvider preparationProvider;
    private final QueuePreparationController queuePreparationController;
    private final RuntimeStateController runtimeStateController;
    private final StatePublisher statePublisher;
    private final RefusalLogger refusalLogger;

    PlaybackCurrentTrackPreparationOwner(
            PreparationProvider preparationProvider,
            QueuePreparationController queuePreparationController,
            RuntimeStateController runtimeStateController,
            StatePublisher statePublisher,
            RefusalLogger refusalLogger
    ) {
        this.preparationProvider = preparationProvider;
        this.queuePreparationController = queuePreparationController;
        this.runtimeStateController = runtimeStateController;
        this.statePublisher = statePublisher;
        this.refusalLogger = refusalLogger;
    }

    PreparedTrack prepareCurrentTrack(Track track) {
        PlaybackMediaSourceProvider.PlaybackPreparation preparation =
                preparationProvider.prepareTrackForPlayback(track);
        Track restoredTrack = preparation.getRestoredTrack();
        if (restoredTrack != null) {
            queuePreparationController.replaceCurrentQueueTrack(restoredTrack);
        }
        Track preparedTrack = preparation.getTrack();
        if (!preparation.getPlayable()) {
            runtimeStateController.setPreparing(false);
            runtimeStateController.setErrorMessage(preparation.getUnplayableMessage());
            refusalLogger.logRefusingToPrepareEmptyUri(preparedTrack);
            statePublisher.publishState();
            return PreparedTrack.unplayable(preparedTrack);
        }
        return PreparedTrack.playable(
                preparedTrack,
                queuePreparationController.restoredPositionFor(preparedTrack)
        );
    }
}
