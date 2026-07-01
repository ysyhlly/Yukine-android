package app.yukine.playback;

import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

final class PlaybackCurrentTrackPreparationOwner {
    interface PlaybackPreparationProvider {
        PlaybackMediaSourceProvider.PlaybackPreparation prepareTrackForPlayback(Track track);
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

    private final PlaybackPreparationProvider playbackPreparationProvider;
    private final MediaSourceResolver mediaSourceResolver;
    private final QueuePreparationController queuePreparationController;
    private final RuntimeStateController runtimeStateController;
    private final StatePublisher statePublisher;
    private final RefusalLogger refusalLogger;

    PlaybackCurrentTrackPreparationOwner(
            PlaybackPreparationProvider playbackPreparationProvider,
            MediaSourceResolver mediaSourceResolver,
            QueuePreparationController queuePreparationController,
            RuntimeStateController runtimeStateController,
            StatePublisher statePublisher,
            RefusalLogger refusalLogger
    ) {
        this.playbackPreparationProvider = playbackPreparationProvider;
        this.mediaSourceResolver = mediaSourceResolver;
        this.queuePreparationController = queuePreparationController;
        this.runtimeStateController = runtimeStateController;
        this.statePublisher = statePublisher;
        this.refusalLogger = refusalLogger;
    }

    PreparedTrack prepareCurrentTrack(Track track) {
        PlaybackMediaSourceProvider.PlaybackPreparation preparation = playbackPreparationProvider == null
                ? null
                : playbackPreparationProvider.prepareTrackForPlayback(track);
        Track restoredTrack = preparation == null ? null : preparation.getRestoredTrack();
        if (restoredTrack != null) {
            queuePreparationController.replaceCurrentQueueTrack(restoredTrack);
        }
        Track preparedTrack = preparation == null ? track : preparation.getTrack();
        if (preparedTrack == null) {
            preparedTrack = track;
        }
        if (preparation != null && !preparation.getPlayable()) {
            String unplayableMessage = preparation.getUnplayableMessage();
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
