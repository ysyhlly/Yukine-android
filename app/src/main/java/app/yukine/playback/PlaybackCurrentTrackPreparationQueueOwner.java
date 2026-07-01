package app.yukine.playback;

import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class PlaybackCurrentTrackPreparationQueueOwner
        implements PlaybackCurrentTrackPreparationOwner.QueuePreparationController {
    static final class PreparedQueue {
        private final Track currentTrack;
        private final int startIndex;
        private final List<MediaSource> mirroredQueueMediaSources;

        private PreparedQueue(
                Track currentTrack,
                int startIndex,
                List<MediaSource> mirroredQueueMediaSources
        ) {
            this.currentTrack = currentTrack;
            this.startIndex = startIndex;
            this.mirroredQueueMediaSources = mirroredQueueMediaSources;
        }

        static PreparedQueue empty() {
            return new PreparedQueue(null, 0, null);
        }

        Track currentTrack() {
            return currentTrack;
        }

        int startIndex() {
            return startIndex;
        }

        List<MediaSource> mirroredQueueMediaSources() {
            return mirroredQueueMediaSources;
        }
    }

    private final Consumer<Track> replaceCurrentQueueTrack;
    private final Function<Track, Long> restoredPositionFor;
    private final Supplier<PlaybackQueueManager.QueuePreparation> queuePreparationForNewPlayer;
    private final Function<List<Track>, List<MediaSource>> mediaSourcesForTracks;
    private final LongConsumer consumeRestoredPositionAfterPrepare;

    static PlaybackCurrentTrackPreparationQueueOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks
    ) {
        return new PlaybackCurrentTrackPreparationQueueOwner(
                track -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.replaceCurrentQueueTrack(track);
                    }
                },
                track -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? 0L : playbackQueueManager.restoredPositionFor(track);
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? PlaybackQueueManager.QueuePreparation.empty()
                            : playbackQueueManager.queuePreparationForNewPlayer();
                },
                mediaSourcesForTracks,
                startPositionMs -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);
                    }
                }
        );
    }

    PlaybackCurrentTrackPreparationQueueOwner(
            Consumer<Track> replaceCurrentQueueTrack,
            Function<Track, Long> restoredPositionFor,
            Supplier<PlaybackQueueManager.QueuePreparation> queuePreparationForNewPlayer,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks,
            LongConsumer consumeRestoredPositionAfterPrepare
    ) {
        this.replaceCurrentQueueTrack = replaceCurrentQueueTrack;
        this.restoredPositionFor = restoredPositionFor;
        this.queuePreparationForNewPlayer = queuePreparationForNewPlayer;
        this.mediaSourcesForTracks = mediaSourcesForTracks;
        this.consumeRestoredPositionAfterPrepare = consumeRestoredPositionAfterPrepare;
    }

    @Override
    public void replaceCurrentQueueTrack(Track track) {
        if (replaceCurrentQueueTrack != null) {
            replaceCurrentQueueTrack.accept(track);
        }
    }

    @Override
    public long restoredPositionFor(Track track) {
        Long positionMs = restoredPositionFor == null ? null : restoredPositionFor.apply(track);
        return positionMs == null ? 0L : positionMs;
    }

    PreparedQueue queuePreparationForNewPlayer() {
        PlaybackQueueManager.QueuePreparation queuePreparation = queuePreparationForNewPlayer == null
                ? PlaybackQueueManager.QueuePreparation.empty()
                : queuePreparationForNewPlayer.get();
        if (queuePreparation == null || queuePreparation.getCurrentTrack() == null) {
            return PreparedQueue.empty();
        }
        List<Track> mirroredQueueTracks = queuePreparation.getMirroredQueueTracks();
        List<MediaSource> mirroredQueueMediaSources =
                mirroredQueueTracks == null || mirroredQueueTracks.isEmpty() || mediaSourcesForTracks == null
                        ? null
                        : mediaSourcesForTracks.apply(mirroredQueueTracks);
        return new PreparedQueue(
                queuePreparation.getCurrentTrack(),
                queuePreparation.getStartIndex(),
                mirroredQueueMediaSources
        );
    }

    void consumeRestoredPositionAfterPrepare(long startPositionMs) {
        if (consumeRestoredPositionAfterPrepare != null) {
            consumeRestoredPositionAfterPrepare.accept(startPositionMs);
        }
    }
}
