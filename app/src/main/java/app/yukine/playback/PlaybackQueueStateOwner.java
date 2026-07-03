package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

final class PlaybackQueueStateOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;

    PlaybackQueueStateOwner() {
        this((Supplier<PlaybackQueueManager>) null);
    }

    PlaybackQueueStateOwner(PlaybackQueueManager playbackQueueManager) {
        this(() -> playbackQueueManager);
    }

    PlaybackQueueStateOwner(Supplier<PlaybackQueueManager> playbackQueueManagerSupplier) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    int currentIndex() {
        return queueStateSnapshot().getCurrentIndex();
    }

    int queueSize() {
        return queueStateSnapshot().getQueueSize();
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

    List<Track> queueSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        List<Track> snapshot = playbackQueueManager == null ? null : playbackQueueManager.queueSnapshot();
        return snapshot == null ? Collections.emptyList() : snapshot;
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
