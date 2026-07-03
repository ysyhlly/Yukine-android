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
        Track track = queueStateOwner == null ? null
                : queueStateOwner.queueStateSnapshot().getCurrentTrack();
        if (track == null || playbackPreparer == null) {
            return false;
        }
        playbackPreparer.accept(track, playWhenReady);
        return true;
    }

    boolean runIfCurrentTrackMissing(Runnable missingCurrentTrackAction) {
        boolean missingCurrentTrack = queueStateOwner == null
                || queueStateOwner.queueStateSnapshot().getCurrentTrack() == null;
        if (missingCurrentTrack && missingCurrentTrackAction != null) {
            missingCurrentTrackAction.run();
        }
        return missingCurrentTrack;
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }
}
