package app.yukine.playback;

import androidx.media3.common.Player;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackQueueMirroredTransitionOwner {
    interface CurrentTrackVolumeApplier {
        void applyCurrentTrackVolume();
    }

    private final BiFunction<Integer, Boolean, PlaybackQueueManager.MirroredTransitionResult> applyMirroredTransitionIndex;
    private final BooleanSupplier prepareMirroredTransitionPlaybackState;
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
                (nextIndex, automaticAdvance) -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : playbackQueueManager.applyMirroredTransitionIndex(nextIndex, automaticAdvance);
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager == null) {
                        return false;
                    }
                    playbackQueueManager.prepareMirroredTransitionPlaybackState();
                    return true;
                },
                currentTrackVolumeApplier,
                playerMirrorsQueue,
                queueEmpty
        );
    }

    PlaybackQueueMirroredTransitionOwner(
            BiFunction<Integer, Boolean, PlaybackQueueManager.MirroredTransitionResult> applyMirroredTransitionIndex,
            BooleanSupplier prepareMirroredTransitionPlaybackState
    ) {
        this(applyMirroredTransitionIndex, prepareMirroredTransitionPlaybackState, null);
    }

    PlaybackQueueMirroredTransitionOwner(
            BiFunction<Integer, Boolean, PlaybackQueueManager.MirroredTransitionResult> applyMirroredTransitionIndex,
            BooleanSupplier prepareMirroredTransitionPlaybackState,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        this(applyMirroredTransitionIndex, prepareMirroredTransitionPlaybackState, currentTrackVolumeApplier, null, null);
    }

    PlaybackQueueMirroredTransitionOwner(
            BiFunction<Integer, Boolean, PlaybackQueueManager.MirroredTransitionResult> applyMirroredTransitionIndex,
            BooleanSupplier prepareMirroredTransitionPlaybackState,
            CurrentTrackVolumeApplier currentTrackVolumeApplier,
            BooleanSupplier playerMirrorsQueue,
            BooleanSupplier queueEmpty
    ) {
        this.applyMirroredTransitionIndex = applyMirroredTransitionIndex;
        this.prepareMirroredTransitionPlaybackState = prepareMirroredTransitionPlaybackState;
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
        return applyMirroredTransitionIndex == null
                ? null
                : applyMirroredTransitionIndex.apply(nextIndex, automaticAdvance);
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
        if (prepareMirroredTransitionPlaybackState != null
                && prepareMirroredTransitionPlaybackState.getAsBoolean()) {
            if (currentTrackVolumeApplier != null) {
                currentTrackVolumeApplier.applyCurrentTrackVolume();
            }
        }
    }
}
