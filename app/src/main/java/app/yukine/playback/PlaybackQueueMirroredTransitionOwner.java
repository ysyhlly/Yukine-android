package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackQueueMirroredTransitionOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Runnable currentTrackVolumeApplier;
    private final BooleanSupplier playerMirrorsQueue;
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier;

    PlaybackQueueMirroredTransitionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Runnable currentTrackVolumeApplier,
            BooleanSupplier playerMirrorsQueue,
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSupplier
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
        this.playerMirrorsQueue = playerMirrorsQueue;
        this.queueStateSupplier = queueStateSupplier;
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
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
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

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSupplier == null
                ? null
                : queueStateSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

}
