package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class PlaybackQueueMirroredTransitionOwner {
    static final class Transition {
        private final boolean stopAfterAutomaticAdvance;
        private final Track currentTrack;

        private Transition(
                boolean stopAfterAutomaticAdvance,
                Track currentTrack
        ) {
            this.stopAfterAutomaticAdvance = stopAfterAutomaticAdvance;
            this.currentTrack = currentTrack;
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
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
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
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot = queueStateSnapshot();
        int currentIndex = queueStateSnapshot.getCurrentIndex();
        int queueSize = queueStateSnapshot.getQueueSize();
        if (nextIndex < 0 || nextIndex >= queueSize || nextIndex == currentIndex) {
            return null;
        }
        boolean stopAfterAutomaticAdvance =
                playbackQueueManager.applyMirroredTransitionIndex(
                        nextIndex,
                        isAutomaticMediaItemAdvance(reason)
                );
        Track currentTrack = null;
        if (!stopAfterAutomaticAdvance) {
            currentTrack = queueStateSnapshot().getCurrentTrack();
            if (currentTrackVolumeApplier != null) {
                currentTrackVolumeApplier.run();
            }
        }
        return new Transition(
                stopAfterAutomaticAdvance,
                currentTrack
        );
    }

    static boolean isAutomaticMediaItemAdvance(int reason) {
        return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return playbackQueueManager.queueStateSnapshot();
    }
}
