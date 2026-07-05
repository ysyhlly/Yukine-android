package app.yukine.playback;

import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class PlaybackCurrentTrackPreparationQueueOwner {
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
    private final Function<Track, Track> queueTrackForPreparation;
    private final Function<List<Track>, List<MediaSource>> mediaSourcesForTracks;

    PlaybackCurrentTrackPreparationQueueOwner(
            PlaybackQueueManager playbackQueueManager,
            PlaybackMediaSourceProvider mediaSourceProvider,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        this(
                playbackQueueManager,
                queueTrackForPreparation(mediaSourceProvider),
                mediaSourcesForTracks(mediaSourceProvider, metadataProvider)
        );
    }

    PlaybackCurrentTrackPreparationQueueOwner(
            PlaybackQueueManager playbackQueueManager,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks
    ) {
        this(playbackQueueManager, null, mediaSourcesForTracks);
    }

    PlaybackCurrentTrackPreparationQueueOwner(
            PlaybackQueueManager playbackQueueManager,
            Function<Track, Track> queueTrackForPreparation,
            Function<List<Track>, List<MediaSource>> mediaSourcesForTracks
    ) {
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.queueTrackForPreparation = queueTrackForPreparation;
        this.mediaSourcesForTracks = Objects.requireNonNull(
                mediaSourcesForTracks,
                "mediaSourcesForTracks"
        );
    }

    PreparedQueue queuePreparationForNewPlayer() {
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot = queueStateSnapshot();
        Track currentTrack = queueStateSnapshot.getCurrentTrack();
        if (currentTrack == null) {
            return PreparedQueue.empty();
        }
        List<Track> mirroredQueueTracks = mirroredQueueTracksForPreparation(queueStateSnapshot);
        List<MediaSource> mirroredQueueMediaSources =
                mirroredQueueTracks == null || mirroredQueueTracks.isEmpty()
                        ? null
                        : mediaSourcesForTracks.apply(mirroredQueueTracks);
        return new PreparedQueue(
                currentTrack,
                queueStateSnapshot.getCurrentIndex(),
                mirroredQueueMediaSources
        );
    }

    private List<Track> mirroredQueueTracksForPreparation(
            PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot
    ) {
        List<Track> queue = queueSnapshot();
        if (queue.isEmpty() || queueStateSnapshot.getCurrentTrack() == null) {
            return null;
        }
        List<Track> tracks = new ArrayList<>(queue.size());
        for (Track track : queue) {
            Track preparedTrack = preparedQueueTrack(track);
            if (preparedTrack == null) {
                return null;
            }
            tracks.add(preparedTrack);
        }
        return tracks;
    }

    private Track preparedQueueTrack(Track track) {
        if (track == null || track.contentUri == null || track.contentUri.toString().isEmpty()) {
            return null;
        }
        if (!PlaybackMediaSourceProvider.isRestorableQueueTrack(track)) {
            return null;
        }
        if (queueTrackForPreparation == null) {
            return track;
        }
        Track preparedTrack = queueTrackForPreparation.apply(track);
        return preparedTrack == null ? track : preparedTrack;
    }

    private List<Track> queueSnapshot() {
        return playbackQueueManager.queueSnapshot();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }

    private static Function<Track, Track> queueTrackForPreparation(
            PlaybackMediaSourceProvider mediaSourceProvider
    ) {
        PlaybackMediaSourceProvider mediaSourceOwner =
                Objects.requireNonNull(mediaSourceProvider, "mediaSourceProvider");
        return mediaSourceOwner::restoredTrackForPreparation;
    }

    private static Function<List<Track>, List<MediaSource>> mediaSourcesForTracks(
            PlaybackMediaSourceProvider mediaSourceProvider,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        PlaybackMediaSourceProvider mediaSourceOwner =
                Objects.requireNonNull(mediaSourceProvider, "mediaSourceProvider");
        return tracks -> mediaSourceOwner.mediaSourcesForTracks(
                tracks,
                metadataProvider == null ? null : metadataProvider::apply
        );
    }
}
