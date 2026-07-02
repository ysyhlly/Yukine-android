package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackQueueMirroredTransitionOwner {
    interface CurrentTrackVolumeApplier {
        void applyCurrentTrackVolume();
    }

    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final CurrentTrackVolumeApplier currentTrackVolumeApplier;
    private final BooleanSupplier playerMirrorsQueue;
    private final BooleanSupplier queueEmpty;

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return fromPlaybackQueueManager(playbackQueueManagerSupplier, null);
    }

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        return fromPlaybackQueueManager(playbackQueueManagerSupplier, null, null, currentTrackVolumeApplier);
    }

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            BooleanSupplier playerMirrorsQueue,
            BooleanSupplier queueEmpty,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        return new PlaybackQueueMirroredTransitionOwner(
                playbackQueueManagerSupplier,
                currentTrackVolumeApplier,
                playerMirrorsQueue,
                queueEmpty
        );
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        this(playbackQueueManagerSupplier, null);
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        this(playbackQueueManagerSupplier, currentTrackVolumeApplier, null, null);
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            CurrentTrackVolumeApplier currentTrackVolumeApplier,
            BooleanSupplier playerMirrorsQueue,
            BooleanSupplier queueEmpty
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
        this.playerMirrorsQueue = playerMirrorsQueue;
        this.queueEmpty = queueEmpty;
    }

    boolean canApplyMirroredTransition() {
        boolean mirrorsQueue = playerMirrorsQueue == null || playerMirrorsQueue.getAsBoolean();
        boolean emptyQueue = queueEmpty != null && queueEmpty.getAsBoolean();
        return mirrorsQueue && !emptyQueue;
    }

    PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
            int nextIndex,
            boolean automaticAdvance
    ) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        return playbackQueueManager == null
                ? null
                : playbackQueueManager.applyMirroredTransitionIndex(nextIndex, automaticAdvance);
    }

    PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionReason(
            int nextIndex,
            int reason
    ) {
        return applyMirroredTransitionIndex(nextIndex, isAutomaticMediaItemAdvance(reason));
    }

    static boolean isAutomaticMediaItemAdvance(int reason) {
        return reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
    }

    void prepareMirroredTransitionPlaybackState() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager == null) {
            return;
        }
        playbackQueueManager.prepareMirroredTransitionPlaybackState();
        if (currentTrackVolumeApplier != null) {
            currentTrackVolumeApplier.applyCurrentTrackVolume();
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
