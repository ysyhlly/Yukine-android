package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiConsumer;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    private final PlaybackQueueStateOwner queueStateOwner;
    private final BiConsumer<Track, Boolean> playbackPreparer;
    private final Runnable statePublisher;

    PlaybackQueueCommandOwner(
            PlaybackQueueStateOwner queueStateOwner,
            BiConsumer<Track, Boolean> playbackPreparer,
            Runnable statePublisher
    ) {
        this.queueStateOwner = queueStateOwner;
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        Track track = queueStateOwner == null ? null : queueStateOwner.currentTrack();
        if (track == null || playbackPreparer == null) {
            return;
        }
        playbackPreparer.accept(track, playWhenReady);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }
}
