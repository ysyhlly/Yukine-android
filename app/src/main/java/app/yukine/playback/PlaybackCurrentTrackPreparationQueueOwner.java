package app.yukine.playback;

import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.List;
import java.util.function.Function;

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

    private final PlaybackQueueManager playbackQueueManager;
    private final Function<List<Track>, List<MediaSource>> mediaSourcesForTracks;

    static PlaybackCurrentTrackPreparationQueueOwner fromMediaSourceProvider(
            PlaybackQueueManager playbackQueueManager,
            PlaybackMediaSourceProvider mediaSourceProvider,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        return new PlaybackCurrentTrackPreparationQueueOwner(
                playbackQueueManager,
                tracks -> mediaSourceProvider == null
                        ? null
                        : mediaSourceProvider.mediaSourcesForTracks(
                                tracks,
                                metadataProvider == null ? null : metadataProvider::apply
                        )
        );
    }

    PlaybackCurrentTrackPreparationQueueOwner(
            PlaybackQueueManager playbackQueueManager,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.mediaSourcesForTracks = mediaSourcesForTracks;
    }

    @Override
    public void replaceCurrentQueueTrack(Track track) {
        if (playbackQueueManager != null) {
            playbackQueueManager.replaceCurrentQueueTrack(track);
        }
    }

    @Override
    public long restoredPositionFor(Track track) {
        return playbackQueueManager == null ? 0L : playbackQueueManager.restoredPositionFor(track);
    }

    PreparedQueue queuePreparationForNewPlayer() {
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
        if (playbackQueueManager != null) {
            playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);
        }
    }
}
