package app.yukine.playback;

import java.util.List;
import java.util.Set;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackQueueMutationOwner implements PlaybackControllerMediaItemsOwner.QueuePlayer {
    private final PlaybackQueueManager playbackQueueManager;
    private final Runnable stopAndClearAction;

    PlaybackQueueMutationOwner(
            PlaybackQueueManager playbackQueueManager,
            Runnable stopAndClearAction
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.stopAndClearAction = stopAndClearAction;
    }

    @Override
    public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        if (playbackQueueManager != null) {
            playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);
        }
    }

    void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        if (playbackQueueManager != null) {
            playbackQueueManager.appendToQueue(tracks);
        }
    }

    void removeTracksById(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        if (playbackQueueManager != null && playbackQueueManager.removeTracksById(trackIds)) {
            stopAndClear();
        }
    }

    void retainTracksById(Set<Long> trackIdsToKeep) {
        if (trackIdsToKeep == null || trackIdsToKeep.isEmpty()) {
            return;
        }
        if (playbackQueueManager != null && playbackQueueManager.retainTracksById(trackIdsToKeep)) {
            stopAndClear();
        }
    }

    void clearQueue() {
        if (playbackQueueManager != null && !playbackQueueManager.queueStateSnapshot().isQueueEmpty()) {
            stopAndClear();
        }
    }

    void moveQueueTrack(int fromIndex, int toIndex) {
        if (playbackQueueManager != null) {
            playbackQueueManager.moveQueueTrack(fromIndex, toIndex);
        }
    }

    void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        if (playbackQueueManager != null
                && replacement != null
                && playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement)) {
            stopAndClear();
        }
    }

    private void stopAndClear() {
        if (stopAndClearAction != null) {
            stopAndClearAction.run();
        }
    }
}
