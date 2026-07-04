package app.yukine.playback;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.stopAndClearAction = Objects.requireNonNull(stopAndClearAction, "stopAndClearAction");
    }

    @Override
    public void playQueue(List<Track> tracks, int startIndex, long startPositionMs) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        playbackQueueManager.playQueue(tracks, startIndex, startPositionMs);
    }

    void appendToQueue(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        playbackQueueManager.appendToQueue(tracks);
    }

    void removeTracksById(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        boolean hadQueue = hasQueue(queueStateSnapshot());
        playbackQueueManager.removeTracksById(trackIds);
        if (hadQueue && !hasQueue(queueStateSnapshot())) {
            stopAndClear();
        }
    }

    void retainTracksById(Set<Long> trackIdsToKeep) {
        if (trackIdsToKeep == null || trackIdsToKeep.isEmpty()) {
            return;
        }
        Set<Long> trackIdsToRemove = new HashSet<>();
        for (Track track : playbackQueueManager.queueSnapshot()) {
            if (!trackIdsToKeep.contains(track.id)) {
                trackIdsToRemove.add(track.id);
            }
        }
        if (trackIdsToRemove.isEmpty()) {
            return;
        }
        boolean hadQueue = hasQueue(queueStateSnapshot());
        playbackQueueManager.removeTracksById(trackIdsToRemove);
        if (hadQueue && !hasQueue(queueStateSnapshot())) {
            stopAndClear();
        }
    }

    void clearQueue() {
        if (hasQueue(queueStateSnapshot())) {
            stopAndClear();
        }
    }

    void moveQueueTrack(int fromIndex, int toIndex) {
        playbackQueueManager.moveQueueTrack(fromIndex, toIndex);
    }

    void replaceQueuedTrackById(long oldTrackId, Track replacement) {
        if (replacement != null) {
            playbackQueueManager.replaceQueuedTrackById(oldTrackId, replacement);
        }
    }

    private void stopAndClear() {
        stopAndClearAction.run();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }

    private static boolean hasQueue(PlaybackQueueManager.QueueStateSnapshot snapshot) {
        return snapshot.getQueueSize() > 0;
    }
}
