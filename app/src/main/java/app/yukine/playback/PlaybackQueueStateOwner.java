package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

final class PlaybackQueueStateOwner implements
        PlaybackNotificationStateOwner.QueueStateProvider,
        PlaybackStateSnapshotOwner.QueueStateProvider,
        PlaybackCrossfadeStateOwner.QueueStateProvider,
        PlaybackErrorRecoveryCommandOwner.FailedTrackPolicy {
    interface QueueStateOperations {
        PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot();
    }

    interface QueueSnapshotOperations {
        List<Track> queueSnapshot();
    }

    interface UpcomingTracksOperations {
        List<Track> upcomingTracksForPrecache(int maxCount);
    }

    private final Supplier<QueueStateOperations> queueStateOperationsSupplier;
    private final Supplier<QueueSnapshotOperations> queueSnapshotOperationsSupplier;
    private final Supplier<UpcomingTracksOperations> upcomingTracksOperationsSupplier;

    PlaybackQueueStateOwner(Supplier<QueueStateOperations> queueStateOperationsSupplier) {
        this(queueStateOperationsSupplier, null, null);
    }

    PlaybackQueueStateOwner(
            Supplier<QueueStateOperations> queueStateOperationsSupplier,
            Supplier<QueueSnapshotOperations> queueSnapshotOperationsSupplier,
            Supplier<UpcomingTracksOperations> upcomingTracksOperationsSupplier
    ) {
        this.queueStateOperationsSupplier = queueStateOperationsSupplier;
        this.queueSnapshotOperationsSupplier = queueSnapshotOperationsSupplier;
        this.upcomingTracksOperationsSupplier = upcomingTracksOperationsSupplier;
    }

    static PlaybackQueueStateOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueStateOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : playbackQueueManager::queueStateSnapshot;
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : playbackQueueManager::queueSnapshot;
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : playbackQueueManager::upcomingTracksForPrecache;
                }
        );
    }

    @Override
    public boolean isQueueEmpty() {
        return queueStateSnapshot().isQueueEmpty();
    }

    @Override
    public PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        QueueStateOperations operations = queueStateOperationsSupplier == null
                ? null
                : queueStateOperationsSupplier.get();
        PlaybackQueueManager.QueueStateSnapshot snapshot = operations == null
                ? null
                : operations.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    List<Track> queueSnapshot() {
        QueueSnapshotOperations operations = queueSnapshotOperationsSupplier == null
                ? null
                : queueSnapshotOperationsSupplier.get();
        List<Track> snapshot = operations == null ? null : operations.queueSnapshot();
        return snapshot == null ? Collections.emptyList() : snapshot;
    }

    List<Track> upcomingTracksForPrecache(int maxCount) {
        UpcomingTracksOperations operations = upcomingTracksOperationsSupplier == null
                ? null
                : upcomingTracksOperationsSupplier.get();
        List<Track> tracks = operations == null ? null : operations.upcomingTracksForPrecache(maxCount);
        return tracks == null ? Collections.emptyList() : tracks;
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        return failed != null && failed.id != -1L && queueStateSnapshot().getHasMultipleTracks();
    }
}
