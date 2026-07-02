package app.yukine.playback;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueMutationOwner implements PlaybackControllerMediaItemsOwner.QueuePlayer {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;

    PlaybackQueueMutationOwner(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
    }

    @Override
    public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);
        }
    }

    void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.appendToQueue(tracks);
        }
    }

    void removeTracksById(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.removeTracksById(trackIds);
        }
    }

    void retainTracksById(Set<Long> trackIdsToKeep) {
        if (trackIdsToKeep == null || trackIdsToKeep.isEmpty()) {
            return;
        }
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.retainTracksById(trackIdsToKeep);
        }
    }

    void clearQueue() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.clearQueue();
        }
    }

    void moveQueueTrack(int fromIndex, int toIndex) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.moveQueueTrack(fromIndex, toIndex);
        }
    }

    void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager != null) {
            playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement);
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
