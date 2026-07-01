package app.yukine.playback;

import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;

final class PlaybackCurrentTrackPreparationOwner {
    interface RestoredTrackProvider {
        Track restoredTrackForPreparation(Track track);
    }

    interface UnplayableMessageProvider {
        String unplayableMessageForTrack(Track track);
    }

    interface MediaSourceResolver {
        MediaSource mediaSourceForTrack(Track track);
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
        private final MediaSourceResolver mediaSourceResolver;

        private PreparedTrack(
                Track track,
                long startPositionMs,
                boolean playable,
                MediaSourceResolver mediaSourceResolver
        ) {
            this.track = track;
            this.startPositionMs = startPositionMs;
            this.playable = playable;
            this.mediaSourceResolver = mediaSourceResolver;
        }

        static PreparedTrack playable(
                Track track,
                long startPositionMs,
                MediaSourceResolver mediaSourceResolver
        ) {
            return new PreparedTrack(track, Math.max(0L, startPositionMs), true, mediaSourceResolver);
        }

        static PreparedTrack unplayable(Track track) {
            return new PreparedTrack(track, 0L, false, null);
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

        MediaSource mediaSource() {
            if (mediaSourceResolver == null) {
                return null;
            }
            return mediaSourceResolver.mediaSourceForTrack(track);
        }
    }

    private final RestoredTrackProvider restoredTrackProvider;
    private final UnplayableMessageProvider unplayableMessageProvider;
    private final MediaSourceResolver mediaSourceResolver;
    private final QueuePreparationController queuePreparationController;
    private final RuntimeStateController runtimeStateController;
    private final StatePublisher statePublisher;
    private final RefusalLogger refusalLogger;

    PlaybackCurrentTrackPreparationOwner(
            RestoredTrackProvider restoredTrackProvider,
            UnplayableMessageProvider unplayableMessageProvider,
            MediaSourceResolver mediaSourceResolver,
            QueuePreparationController queuePreparationController,
            RuntimeStateController runtimeStateController,
            StatePublisher statePublisher,
            RefusalLogger refusalLogger
    ) {
        this.restoredTrackProvider = restoredTrackProvider;
        this.unplayableMessageProvider = unplayableMessageProvider;
        this.mediaSourceResolver = mediaSourceResolver;
        this.queuePreparationController = queuePreparationController;
        this.runtimeStateController = runtimeStateController;
        this.statePublisher = statePublisher;
        this.refusalLogger = refusalLogger;
    }

    PreparedTrack prepareCurrentTrack(Track track) {
        Track restoredTrack = restoredTrackProvider == null
                ? null
                : restoredTrackProvider.restoredTrackForPreparation(track);
        if (restoredTrack != null) {
            queuePreparationController.replaceCurrentQueueTrack(restoredTrack);
        }
        Track preparedTrack = restoredTrack == null ? track : restoredTrack;
        String unplayableMessage = unplayableMessageProvider == null
                ? null
                : unplayableMessageProvider.unplayableMessageForTrack(preparedTrack);
        if (unplayableMessage != null) {
            runtimeStateController.setPreparing(false);
            runtimeStateController.setErrorMessage(unplayableMessage);
            refusalLogger.logRefusingToPrepareEmptyUri(preparedTrack);
            statePublisher.publishState();
            return PreparedTrack.unplayable(preparedTrack);
        }
        return PreparedTrack.playable(
                preparedTrack,
                queuePreparationController.restoredPositionFor(preparedTrack),
                mediaSourceResolver
        );
    }
}
