package app.yukine.playback;

import androidx.media3.common.C;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueMutationOwner implements PlaybackControllerMediaItemsOwner.QueuePlayer {
    interface QueueMutationOperations {
        void playQueue(List<Track> tracks, int startIndex, long startPositionMs);

        void appendToQueue(List<Track> tracks);

        void removeTracksById(Set<Long> trackIds);

        void retainTracksById(Set<Long> trackIdsToKeep);

        void clearQueue();

        void moveQueueTrack(int fromIndex, int toIndex);

        void replaceQueuedTrack(Track replacement);

        void replaceQueuedTrackById(long oldTrackId, Track replacement);
    }

    private final Supplier<QueueMutationOperations> queueMutationOperationsSupplier;

    PlaybackQueueMutationOwner(Supplier<QueueMutationOperations> queueMutationOperationsSupplier) {
        this.queueMutationOperationsSupplier = queueMutationOperationsSupplier;
    }

    static PlaybackQueueMutationOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueMutationOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : new PlaybackQueueManagerOperations(playbackQueueManager);
                }
        );
    }

    void playQueue(List<Track> tracks, int startIndex) {
        playQueue(tracks, startIndex, C.TIME_UNSET);
    }

    @Override
    public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.playQueue(tracks, startIndex, startPositionMs);
        }
    }

    void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.appendToQueue(tracks);
        }
    }

    void removeTracksById(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.removeTracksById(trackIds);
        }
    }

    void retainTracksById(Set<Long> trackIdsToKeep) {
        if (trackIdsToKeep == null || trackIdsToKeep.isEmpty()) {
            return;
        }
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.retainTracksById(trackIdsToKeep);
        }
    }

    void clearQueue() {
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.clearQueue();
        }
    }

    void moveQueueTrack(int fromIndex, int toIndex) {
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.moveQueueTrack(fromIndex, toIndex);
        }
    }

    void replaceQueuedTrack(Track replacement) {
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.replaceQueuedTrack(replacement);
        }
    }

    void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        QueueMutationOperations operations = queueMutationOperations();
        if (operations != null) {
            operations.replaceQueuedTrackById(oldTrackId, replacement);
        }
    }

    private QueueMutationOperations queueMutationOperations() {
        return queueMutationOperationsSupplier == null ? null : queueMutationOperationsSupplier.get();
    }

    private static final class PlaybackQueueManagerOperations implements QueueMutationOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
            playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);
        }

        @Override
        public void appendToQueue(List<Track> tracks) {
            playbackQueueManager.appendToQueue(tracks);
        }

        @Override
        public void removeTracksById(Set<Long> trackIds) {
            playbackQueueManager.removeTracksById(trackIds);
        }

        @Override
        public void retainTracksById(Set<Long> trackIdsToKeep) {
            playbackQueueManager.retainTracksById(trackIdsToKeep);
        }

        @Override
        public void clearQueue() {
            playbackQueueManager.clearQueue();
        }

        @Override
        public void moveQueueTrack(int fromIndex, int toIndex) {
            playbackQueueManager.moveQueueTrack(fromIndex, toIndex);
        }

        @Override
        public void replaceQueuedTrack(Track replacement) {
            playbackQueueManager.replaceQueuedTrack(replacement);
        }

        @Override
        public void replaceQueuedTrackById(long oldTrackId, Track replacement) {
            playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement);
        }
    }
}
