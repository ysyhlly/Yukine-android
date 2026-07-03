package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final BiConsumer<Track, Boolean> playbackPreparer;
    private final Runnable statePublisher;

    PlaybackQueueCommandOwner(
            PlaybackQueueManager playbackQueueManager,
            BiConsumer<Track, Boolean> playbackPreparer,
            Runnable statePublisher
    ) {
        this(() -> playbackQueueManager, playbackPreparer, statePublisher);
    }

    PlaybackQueueCommandOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            BiConsumer<Track, Boolean> playbackPreparer,
            Runnable statePublisher
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        prepareCurrentIfAvailable(playWhenReady);
    }

    boolean prepareCurrentOrRunFallback(boolean playWhenReady, Runnable fallbackAction) {
        if (prepareCurrentIfAvailable(playWhenReady)) {
            return true;
        }
        if (fallbackAction != null) {
            fallbackAction.run();
        }
        return false;
    }

    private boolean prepareCurrentIfAvailable(boolean playWhenReady) {
        Track track = currentTrack();
        if (track == null || playbackPreparer == null) {
            return false;
        }
        playbackPreparer.accept(track, playWhenReady);
        return true;
    }

    boolean runIfCurrentTrackMissing(Runnable missingCurrentTrackAction) {
        boolean missingCurrentTrack = currentTrack() == null;
        if (missingCurrentTrack && missingCurrentTrackAction != null) {
            missingCurrentTrackAction.run();
        }
        return missingCurrentTrack;
    }

    private Track currentTrack() {
        return queueStateSnapshot().getCurrentTrack();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        PlaybackQueueManager.QueueStateSnapshot snapshot = playbackQueueManager == null
                ? null
                : playbackQueueManager.queueStateSnapshot();
        return snapshot == null ? PlaybackQueueManager.QueueStateSnapshot.empty() : snapshot;
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }
}
