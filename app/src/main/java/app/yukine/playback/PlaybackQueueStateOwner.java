package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

final class PlaybackQueueStateOwner implements
        PlaybackStateSnapshotOwner.QueueStateProvider,
        PlaybackCrossfadeStateOwner.QueueStateProvider,
        PlaybackErrorRecoveryCommandOwner.FailedTrackPolicy {
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier;
    private final Supplier<List<Track>> queueSnapshotSupplier;
    private final IntFunction<List<Track>> upcomingTracksSupplier;

    PlaybackQueueStateOwner(Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier) {
        this(queueStateSnapshotSupplier, null, null);
    }

    PlaybackQueueStateOwner(
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateSnapshotSupplier,
            Supplier<List<Track>> queueSnapshotSupplier,
            IntFunction<List<Track>> upcomingTracksSupplier
    ) {
        this.queueStateSnapshotSupplier = queueStateSnapshotSupplier;
        this.queueSnapshotSupplier = queueSnapshotSupplier;
        this.upcomingTracksSupplier = upcomingTracksSupplier;
    }

    static PlaybackQueueStateOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackQueueStateOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : playbackQueueManager.queueStateSnapshot();
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : playbackQueueManager.queueSnapshot();
                },
                maxCount -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? null : playbackQueueManager.upcomingTracksForPrecache(maxCount);
                }
        );
    }

    public boolean isQueueEmpty() {
        return queueStateSnapshot().isQueueEmpty();
    }

    @Override
    public PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateSnapshotSupplier == null
                ? null
                : queueStateSnapshotSupplier.get();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    public Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    List<Track> queueSnapshot() {
        List<Track> snapshot = queueSnapshotSupplier == null ? null : queueSnapshotSupplier.get();
        return snapshot == null ? Collections.emptyList() : snapshot;
    }

    List<Track> upcomingTracksForPrecache(int maxCount) {
        List<Track> tracks = upcomingTracksSupplier == null ? null : upcomingTracksSupplier.apply(maxCount);
        return tracks == null ? Collections.emptyList() : tracks;
    }

    @Override
    public boolean canSkipFailedTrack(Track failed) {
        return failed != null && failed.id != -1L && queueStateSnapshot().getHasMultipleTracks();
    }
}
