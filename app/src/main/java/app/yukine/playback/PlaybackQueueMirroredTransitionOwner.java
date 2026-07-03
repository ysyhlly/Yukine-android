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
    private final PlaybackQueueStateOwner queueStateOwner;
    private final Runnable currentTrackVolumeApplier;
    private final BooleanSupplier playerMirrorsQueue;

    PlaybackQueueMirroredTransitionOwner(
            PlaybackQueueManager playbackQueueManager,
            PlaybackQueueStateOwner queueStateOwner,
            Runnable currentTrackVolumeApplier,
            BooleanSupplier playerMirrorsQueue
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.queueStateOwner = queueStateOwner;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
        this.playerMirrorsQueue = playerMirrorsQueue;
    }

    boolean canApplyMirroredTransition() {
        boolean mirrorsQueue = playerMirrorsQueue == null || playerMirrorsQueue.getAsBoolean();
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null
                ? PlaybackQueueManager.QueueStateSnapshot.empty()
                : queueStateOwner.queueStateSnapshot();
        boolean queueEmpty = snapshot.isQueueEmpty();
        return mirrorsQueue && !queueEmpty;
    }

    Transition applyMirroredTransitionReason(
            int nextIndex,
            int reason
    ) {
        if (playbackQueueManager == null) {
            return null;
        }
        PlaybackQueueManager.MirroredTransitionResult result =
                playbackQueueManager.applyMirroredTransitionIndex(
                        nextIndex,
                        isAutomaticMediaItemAdvance(reason)
                );
        if (result == null) {
            return null;
        }
        Track currentTrack = null;
        if (!result.getStopAfterAutomaticAdvance()) {
            PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateOwner == null
                    ? PlaybackQueueManager.QueueStateSnapshot.empty()
                    : queueStateOwner.queueStateSnapshot();
            currentTrack = snapshot.getCurrentTrack();
            if (currentTrackVolumeApplier != null) {
                currentTrackVolumeApplier.run();
            }
        }
        return new Transition(
                result.getCompletedIndex(),
                result.getStopAfterAutomaticAdvance(),
                currentTrack
        );
    }

    static boolean isAutomaticMediaItemAdvance(int reason) {
        return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
    }

}
