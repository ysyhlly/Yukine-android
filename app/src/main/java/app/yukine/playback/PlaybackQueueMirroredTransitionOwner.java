package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackQueueMirroredTransitionOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Runnable currentTrackVolumeApplier;
    private final BooleanSupplier playerMirrorsQueue;
    private final BooleanSupplier queueEmptySupplier;

    PlaybackQueueMirroredTransitionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Runnable currentTrackVolumeApplier,
            BooleanSupplier playerMirrorsQueue,
            BooleanSupplier queueEmptySupplier
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
        this.playerMirrorsQueue = playerMirrorsQueue;
        this.queueEmptySupplier = queueEmptySupplier;
    }

    boolean canApplyMirroredTransition() {
        boolean mirrorsQueue = playerMirrorsQueue == null || playerMirrorsQueue.getAsBoolean();
        boolean emptyQueue = queueEmptySupplier == null || queueEmptySupplier.getAsBoolean();
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

}
