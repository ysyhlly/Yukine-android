package app.yukine.playback;

import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

import java.util.function.Consumer;
import java.util.function.Function;

final class PlaybackCurrentTrackPreparationOwner {
    interface QueuePreparationController {
        void replaceCurrentQueueTrack(Track track);
    }

    interface RuntimeStateController {
        void setPreparing(boolean preparing);
        void setErrorMessage(String message);
    }

    static final class PreparedTrack {
        private final Track track;
        private final long startPositionMs;
        private final boolean playable;
        private final Function<Track, MediaSource> mediaSourceResolver;

        private PreparedTrack(
                Track track,
                long startPositionMs,
                boolean playable,
                Function<Track, MediaSource> mediaSourceResolver
        ) {
            this.track = track;
            this.startPositionMs = startPositionMs;
            this.playable = playable;
            this.mediaSourceResolver = mediaSourceResolver;
        }

        static PreparedTrack playable(
                Track track,
                long startPositionMs,
                Function<Track, MediaSource> mediaSourceResolver
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
            return mediaSourceResolver.apply(track);
        }
    }

    private final Function<Track, PlaybackMediaSourceProvider.PlaybackPreparation> playbackPreparationProvider;
    private final Function<Track, MediaSource> mediaSourceResolver;
    private final QueuePreparationController queuePreparationController;
    private final Function<Track, Long> restoredPositionProvider;
    private final RuntimeStateController runtimeStateController;
    private final Runnable statePublisher;
    private final Consumer<Track> refusalLogger;

    static PlaybackCurrentTrackPreparationOwner fromMediaSourceProvider(
            PlaybackMediaSourceProvider mediaSourceProvider,
            Function<Track, MediaMetadata> metadataProvider,
            QueuePreparationController queuePreparationController,
            Function<Track, Long> restoredPositionProvider,
            RuntimeStateController runtimeStateController,
            Runnable statePublisher,
            Consumer<Track> refusalLogger
    ) {
        return new PlaybackCurrentTrackPreparationOwner(
                track -> mediaSourceProvider == null
                        ? null
                        : mediaSourceProvider.prepareTrackForPlayback(track),
                track -> mediaSourceProvider == null
                        ? null
                        : mediaSourceProvider.mediaSourceForTrack(
                                track,
                                metadataProvider == null ? null : metadataProvider::apply
                        ),
                queuePreparationController,
                restoredPositionProvider,
                runtimeStateController,
                statePublisher,
                refusalLogger
        );
    }

    PlaybackCurrentTrackPreparationOwner(
            Function<Track, PlaybackMediaSourceProvider.PlaybackPreparation> playbackPreparationProvider,
            Function<Track, MediaSource> mediaSourceResolver,
            QueuePreparationController queuePreparationController,
            Function<Track, Long> restoredPositionProvider,
            RuntimeStateController runtimeStateController,
            Runnable statePublisher,
            Consumer<Track> refusalLogger
    ) {
        this.playbackPreparationProvider = playbackPreparationProvider;
        this.mediaSourceResolver = mediaSourceResolver;
        this.queuePreparationController = queuePreparationController;
        this.restoredPositionProvider = restoredPositionProvider;
        this.runtimeStateController = runtimeStateController;
        this.statePublisher = statePublisher;
        this.refusalLogger = refusalLogger;
    }

    PreparedTrack prepareCurrentTrack(Track track) {
        PlaybackMediaSourceProvider.PlaybackPreparation preparation = playbackPreparationProvider == null
                ? null
                : playbackPreparationProvider.apply(track);
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
            refusalLogger.accept(preparedTrack);
            statePublisher.run();
            return PreparedTrack.unplayable(preparedTrack);
        }
        return PreparedTrack.playable(
                preparedTrack,
                restoredPositionFor(preparedTrack),
                mediaSourceResolver
        );
    }

    private long restoredPositionFor(Track track) {
        if (restoredPositionProvider == null) {
            return 0L;
        }
        Long positionMs = restoredPositionProvider.apply(track);
        return positionMs == null ? 0L : positionMs;
    }
}
