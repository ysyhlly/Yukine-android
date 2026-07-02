package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

final class PlaybackQueueStateOwner implements
        PlaybackStateSnapshotOwner.QueueStateProvider {
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

    Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    boolean hasMultipleTracks() {
        return queueStateSnapshot().getHasMultipleTracks();
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
