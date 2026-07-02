package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

final class PlaybackQueueStateOwner implements
        PlaybackStateSnapshotOwner.QueueStateProvider,
        Supplier<PlaybackQueueManager.QueueStateSnapshot> {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;

    PlaybackQueueStateOwner(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
    }

    @Override
    public PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    @Override
    public PlaybackQueueManager.QueueStateSnapshot get() {
        return queueStateSnapshot();
    }

    List<Track> queueSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        List<Track> snapshot = playbackQueueManager == null ? null : playbackQueueManager.queueSnapshot();
        return snapshot == null ? Collections.emptyList() : snapshot;
    }

    List<Track> upcomingTracksForPrecache(int maxCount) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        List<Track> tracks = playbackQueueManager == null
                ? null
                : playbackQueueManager.upcomingTracksForPrecache(maxCount);
        return tracks == null ? Collections.emptyList() : tracks;
    }

    boolean isQueueEmpty() {
        return queueStateSnapshot().isQueueEmpty();
    }

    Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
