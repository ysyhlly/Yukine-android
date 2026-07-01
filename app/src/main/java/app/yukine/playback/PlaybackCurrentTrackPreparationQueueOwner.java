package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackCurrentTrackPreparationQueueOwner
        implements PlaybackCurrentTrackPreparationOwner.QueuePreparationController {
    interface QueueOperations {
        void replaceCurrentQueueTrack(Track track);

        long restoredPositionFor(Track track);

        PlaybackQueueManager.QueuePreparation queuePreparationForNewPlayer();

        void consumeRestoredPositionAfterPrepare(long startPositionMs);
    }

    private final Supplier<QueueOperations> queueOperationsProvider;

    static PlaybackCurrentTrackPreparationQueueOwner fromPlaybackQueueManagerProvider(
            Supplier<PlaybackQueueManager> playbackQueueManagerProvider
    ) {
        return new PlaybackCurrentTrackPreparationQueueOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerProvider == null
                                    ? null
                                    : playbackQueueManagerProvider.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                }
        );
    }

    PlaybackCurrentTrackPreparationQueueOwner(Supplier<QueueOperations> queueOperationsProvider) {
        this.queueOperationsProvider = queueOperationsProvider;
    }

    @Override
    public void replaceCurrentQueueTrack(Track track) {
        QueueOperations queueOperations = queueOperations();
        if (queueOperations != null) {
            queueOperations.replaceCurrentQueueTrack(track);
        }
    }

    @Override
    public long restoredPositionFor(Track track) {
        QueueOperations queueOperations = queueOperations();
        return queueOperations == null ? 0L : queueOperations.restoredPositionFor(track);
    }

    PlaybackQueueManager.QueuePreparation queuePreparationForNewPlayer() {
        QueueOperations queueOperations = queueOperations();
        return queueOperations == null
                ? PlaybackQueueManager.QueuePreparation.empty()
                : queueOperations.queuePreparationForNewPlayer();
    }

    void consumeRestoredPositionAfterPrepare(long startPositionMs) {
        QueueOperations queueOperations = queueOperations();
        if (queueOperations != null) {
            queueOperations.consumeRestoredPositionAfterPrepare(startPositionMs);
        }
    }

    private QueueOperations queueOperations() {
        return queueOperationsProvider == null ? null : queueOperationsProvider.get();
    }

    private static final class PlaybackQueueManagerOperations implements QueueOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public void replaceCurrentQueueTrack(Track track) {
            playbackQueueManager.replaceCurrentQueueTrack(track);
        }

        @Override
        public long restoredPositionFor(Track track) {
            return playbackQueueManager.restoredPositionFor(track);
        }

        @Override
        public PlaybackQueueManager.QueuePreparation queuePreparationForNewPlayer() {
            return playbackQueueManager.queuePreparationForNewPlayer();
        }

        @Override
        public void consumeRestoredPositionAfterPrepare(long startPositionMs) {
            playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);
        }
    }
}
