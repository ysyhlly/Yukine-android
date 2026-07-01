package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueMirroredTransitionOwner {
    interface MirroredTransitionOperations {
        PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
                int nextIndex,
                boolean automaticAdvance
        );

        void prepareMirroredTransitionPlaybackState();
    }

    interface CurrentTrackVolumeApplier {
        void applyCurrentTrackVolume();
    }

    private final Supplier<MirroredTransitionOperations> mirroredTransitionOperationsSupplier;
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
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                },
                currentTrackVolumeApplier
        );
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<MirroredTransitionOperations> mirroredTransitionOperationsSupplier
    ) {
        this(mirroredTransitionOperationsSupplier, null);
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<MirroredTransitionOperations> mirroredTransitionOperationsSupplier,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        this.mirroredTransitionOperationsSupplier = mirroredTransitionOperationsSupplier;
        this.currentTrackVolumeApplier = currentTrackVolumeApplier;
    }

    PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
            int nextIndex,
            boolean automaticAdvance
    ) {
        MirroredTransitionOperations operations = mirroredTransitionOperations();
        return operations == null
                ? null
                : operations.applyMirroredTransitionIndex(nextIndex, automaticAdvance);
    }

    void prepareMirroredTransitionPlaybackState() {
        MirroredTransitionOperations operations = mirroredTransitionOperations();
        if (operations != null) {
            operations.prepareMirroredTransitionPlaybackState();
            if (currentTrackVolumeApplier != null) {
                currentTrackVolumeApplier.applyCurrentTrackVolume();
            }
        }
    }

    private MirroredTransitionOperations mirroredTransitionOperations() {
        return mirroredTransitionOperationsSupplier == null
                ? null
                : mirroredTransitionOperationsSupplier.get();
    }

    private static final class PlaybackQueueManagerOperations implements MirroredTransitionOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public PlaybackQueueManager.MirroredTransitionResult applyMirroredTransitionIndex(
                int nextIndex,
                boolean automaticAdvance
        ) {
            return playbackQueueManager.applyMirroredTransitionIndex(nextIndex, automaticAdvance);
        }

        @Override
        public void prepareMirroredTransitionPlaybackState() {
            playbackQueueManager.prepareMirroredTransitionPlaybackState();
        }
    }
}
