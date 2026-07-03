package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;

final class PlaybackQueueMirroredTransitionOwner {
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
        boolean emptyQueue = queueStateSnapshot().isQueueEmpty();
        return mirrorsQueue && !emptyQueue;
    }

    PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionReason(
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
        if (result != null
                && !result.getStopAfterAutomaticAdvance()
                && currentTrackVolumeApplier != null) {
            currentTrackVolumeApplier.run();
        }
        return result;
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
