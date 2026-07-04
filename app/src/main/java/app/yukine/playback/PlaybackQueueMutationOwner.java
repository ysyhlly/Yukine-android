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
        this.stopAndClearAction = stopAndClearAction;
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
        boolean hadQueue = !queueStateSnapshot().isQueueEmpty();
        playbackQueueManager.removeTracksById(trackIds);
        if (hadQueue && queueStateSnapshot().isQueueEmpty()) {
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
        boolean hadQueue = !queueStateSnapshot().isQueueEmpty();
        playbackQueueManager.removeTracksById(trackIdsToRemove);
        if (hadQueue && queueStateSnapshot().isQueueEmpty()) {
            stopAndClear();
        }
    }

    void clearQueue() {
        if (!queueStateSnapshot().isQueueEmpty()) {
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
        if (stopAndClearAction != null) {
            stopAndClearAction.run();
        }
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }
}
