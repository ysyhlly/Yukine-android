package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.model.Track;

import java.util.List;
import java.util.function.Consumer;

final class PlaybackQueueCommandOwner implements PlaybackQueueManager.QueuePlaybackActions {
    private final Consumer<Boolean> playbackPreparer;
    private final Runnable statePublisher;
    private final PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands;
    private final QueuePersistence queuePersistence;

    PlaybackQueueCommandOwner(
            Consumer<Boolean> playbackPreparer,
            Runnable statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands
    ) {
        this(playbackPreparer, statePublisher, playbackCommands, null);
    }

    PlaybackQueueCommandOwner(
            Consumer<Boolean> playbackPreparer,
            Runnable statePublisher,
            PlaybackNotificationCommandOwner.PlaybackCommands playbackCommands,
            QueuePersistence queuePersistence
    ) {
        this.playbackPreparer = playbackPreparer;
        this.statePublisher = statePublisher;
        this.playbackCommands = playbackCommands;
        this.queuePersistence = queuePersistence;
    }

    @Override
    public void prepareCurrent(boolean playWhenReady) {
        playbackPreparer.accept(playWhenReady);
    }

    @Override
    public void publishState() {
        statePublisher.run();
    }

    @Override
    public void stopAndClear() {
        playbackCommands.stopAndClear();
    }

    @Override
    public boolean persistQueueAsync(List<Track> tracks, int currentIndex) {
        return queuePersistence != null && queuePersistence.persist(tracks, currentIndex);
    }

    interface QueuePersistence {
        boolean persist(List<Track> tracks, int currentIndex);
    }
}
