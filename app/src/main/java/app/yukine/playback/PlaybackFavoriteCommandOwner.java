package app.yukine.playback;

import app.yukine.ToggleFavoriteUseCase;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackFavoriteCommandOwner {
    private PlaybackFavoriteCommandOwner() {
    }

    static void toggleCurrentFavorite(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier,
            ToggleFavoriteUseCase toggleFavoriteUseCase,
            Runnable statePublisher
    ) {
        Track track = currentTrack(queueStateSnapshotSupplier);
        if (toggleFavoriteUseCase != null && toggleFavoriteUseCase.toggle(track)) {
            if (statePublisher != null) {
                statePublisher.run();
            }
        }
    }

    private static Track currentTrack(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier
    ) {
        return queueStateSnapshot(queueStateSnapshotSupplier).getCurrentTrack();
    }

    private static PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier
    ) {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshotSupplier == null
                ? null
                : queueStateSnapshotSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
