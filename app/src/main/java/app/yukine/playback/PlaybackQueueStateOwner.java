package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackQueueStateOwner {
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier;

    PlaybackQueueStateOwner() {
        this((Supplier<PlaybackQueueManager.QueueStateSnapshot>) null);
    }

    PlaybackQueueStateOwner(Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier) {
        this.queueStateSnapshotSupplier = queueStateSnapshotSupplier;
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshotSupplier == null
                ? null
                : queueStateSnapshotSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    boolean isQueueEmpty() {
        return queueStateSnapshot().isQueueEmpty();
    }

    boolean hasMultipleTracks() {
        return queueStateSnapshot().getHasMultipleTracks();
    }

    boolean isAtEndOfQueue() {
        return queueStateSnapshot().isAtEndOfQueue();
    }
}
