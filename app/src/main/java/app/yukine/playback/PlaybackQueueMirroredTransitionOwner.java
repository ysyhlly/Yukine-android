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

    private final Supplier<MirroredTransitionOperations> mirroredTransitionOperationsProvider;
    private final CurrentTrackVolumeApplier currentTrackVolumeApplier;

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManagerProvider(
            Supplier<PlaybackQueueManager> playbackQueueManagerProvider
    ) {
        return fromPlaybackQueueManagerProvider(playbackQueueManagerProvider, null);
    }

    static PlaybackQueueMirroredTransitionOwner fromPlaybackQueueManagerProvider(
            Supplier<PlaybackQueueManager> playbackQueueManagerProvider,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        return new PlaybackQueueMirroredTransitionOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerProvider == null
                                    ? null
                                    : playbackQueueManagerProvider.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                },
                currentTrackVolumeApplier
        );
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<MirroredTransitionOperations> mirroredTransitionOperationsProvider
    ) {
        this(mirroredTransitionOperationsProvider, null);
    }

    PlaybackQueueMirroredTransitionOwner(
            Supplier<MirroredTransitionOperations> mirroredTransitionOperationsProvider,
            CurrentTrackVolumeApplier currentTrackVolumeApplier
    ) {
        this.mirroredTransitionOperationsProvider = mirroredTransitionOperationsProvider;
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
        return mirroredTransitionOperationsProvider == null
                ? null
                : mirroredTransitionOperationsProvider.get();
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
