package app.yukine.playback;

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

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return fromPlaybackQueueManager(playbackQueueManagerSupplier, null);
    }

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
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
                currentTrackVolumeApplier
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
        this.applyMirroredTransitionIndex = applyMirroredTransitionIndex;
        this.prepareMirroredTransitionPlaybackState = prepareMirroredTransitionPlaybackState;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
    }

    PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
            int nextIndex,
            boolean automaticAdvance
    ) {
        return applyMirroredTransitionIndex == null
                ? null
                : applyMirroredTransitionIndex.apply(nextIndex, automaticAdvance);
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
