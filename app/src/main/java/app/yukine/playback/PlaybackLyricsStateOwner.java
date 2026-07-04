package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackLyricsManager;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaybackLyricsStateOwner implements PlaybackLyricsManager.StateProvider {
    private final BooleanSupplier appVisibilitySupplier;
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier;
    private final BooleanSupplier playingStateProvider;
    private final BooleanSupplier preparingStateProvider;

    PlaybackLyricsStateOwner(
            BooleanSupplier appVisibilitySupplier,
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier,
            BooleanSupplier playingStateProvider,
            BooleanSupplier preparingStateProvider
    ) {
        this.appVisibilitySupplier = appVisibilitySupplier;
        this.queueStateSnapshotSupplier = queueStateSnapshotSupplier;
        this.playingStateProvider = playingStateProvider;
        this.preparingStateProvider = preparingStateProvider;
    }

    @Override
    public boolean isAppVisible() {
        return appVisibilitySupplier.getAsBoolean();
    }

    @Override
    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    @Override
    public boolean isPlaying() {
        return playingStateProvider != null && playingStateProvider.getAsBoolean();
    }

    @Override
    public boolean isPreparing() {
        return preparingStateProvider != null && preparingStateProvider.getAsBoolean();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshotSupplier == null
                ? null
                : queueStateSnapshotSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }
}
