package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;

final class PlaybackQueueMirroredTransitionOwner {
    static final class Transition {
        private final int completedIndex;
        private final boolean stopAfterAutomaticAdvance;
        private final Track currentTrack;

        private Transition(
                int completedIndex,
                boolean stopAfterAutomaticAdvance,
                Track currentTrack
        ) {
            this.completedIndex = completedIndex;
            this.stopAfterAutomaticAdvance = stopAfterAutomaticAdvance;
            this.currentTrack = currentTrack;
        }

        int completedIndex() {
            return completedIndex;
        }

        boolean stopAfterAutomaticAdvance() {
            return stopAfterAutomaticAdvance;
        }

        Track currentTrack() {
            return currentTrack;
        }
    }

    private final PlaybackQueueManager playbackQueueManager;
    private final Runnable currentTrackVolumeApplier;
    private final BooleanSupplier playerMirrorsQueue;

    PlaybackQueueMirroredTransitionOwner(
            PlaybackQueueManager playbackQueueManager,
            Runnable currentTrackVolumeApplier,
            BooleanSupplier playerMirrorsQueue
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
        this.playerMirrorsQueue = playerMirrorsQueue;
    }

    boolean canApplyMirroredTransition() {
        boolean mirrorsQueue = playerMirrorsQueue == null || playerMirrorsQueue.getAsBoolean();
        return mirrorsQueue && !queueStateSnapshot().isQueueEmpty();
    }

    Transition applyMirroredTransitionReason(
            int nextIndex,
            int reason
    ) {
        if (playbackQueueManager == null) {
            return null;
        }
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot = queueStateSnapshot();
        int completedIndex = queueStateSnapshot.getCurrentIndex();
        Boolean stopAfterAutomaticAdvance =
                playbackQueueManager.applyMirroredTransitionIndex(
                        nextIndex,
                        isAutomaticMediaItemAdvance(reason)
                );
        if (stopAfterAutomaticAdvance == null) {
            return null;
        }
        Track currentTrack = null;
        if (!stopAfterAutomaticAdvance) {
            currentTrack = queueStateSnapshot().getCurrentTrack();
            if (currentTrackVolumeApplier != null) {
                currentTrackVolumeApplier.run();
            }
        }
        return new Transition(
                completedIndex,
                stopAfterAutomaticAdvance,
                currentTrack
        );
    }

    static boolean isAutomaticMediaItemAdvance(int reason) {
        return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
