package app.yukine.playback;

import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.List;
import java.util.function.Function;
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

    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Function<List<Track>, List<MediaSource>> mediaSourcesForTracks;

    static PlaybackCurrentTrackPreparationQueueOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks
    ) {
        return new PlaybackCurrentTrackPreparationQueueOwner(
                playbackQueueManagerSupplier,
                mediaSourcesForTracks
        );
    }

    static PlaybackCurrentTrackPreparationQueueOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            PlaybackMediaSourceProvider mediaSourceProvider,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        return fromPlaybackQueueManager(
                playbackQueueManagerSupplier,
                tracks -> mediaSourceProvider == null
                        ? null
                        : mediaSourceProvider.mediaSourcesForTracks(
                                tracks,
                                metadataProvider == null ? null : metadataProvider::apply
                        )
        );
    }

    PlaybackCurrentTrackPreparationQueueOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.mediaSourcesForTracks = mediaSourcesForTracks;
    }

    @Override
    public void replaceCurrentQueueTrack(Track track) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.replaceCurrentQueueTrack(track);
        }
    }

    @Override
    public long restoredPositionFor(Track track) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        return playbackQueueManager == null ? 0L : playbackQueueManager.restoredPositionFor(track);
    }

    PreparedQueue queuePreparationForNewPlayer() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.QueuePreparation queuePreparation = playbackQueueManager == null
                ? PlaybackQueueManager.QueuePreparation.empty()
                : playbackQueueManager.queuePreparationForNewPlayer();
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
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
