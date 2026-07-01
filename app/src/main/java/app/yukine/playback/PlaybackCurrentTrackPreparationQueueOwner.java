package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class PlaybackCurrentTrackPreparationQueueOwner
        implements PlaybackCurrentTrackPreparationOwner.QueuePreparationController {
    private final Consumer<Track> replaceCurrentQueueTrack;
    private final Function<Track, Long> restoredPositionFor;
    private final Supplier<PlaybackQueueManager.QueuePreparation> queuePreparationForNewPlayer;
    private final LongConsumer consumeRestoredPositionAfterPrepare;

    static PlaybackCurrentTrackPreparationQueueOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier
    ) {
        return new PlaybackCurrentTrackPreparationQueueOwner(
                track -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.replaceCurrentQueueTrack(track);
                    }
                },
                track -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null ? 0L : playbackQueueManager.restoredPositionFor(track);
                },
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? PlaybackQueueManager.QueuePreparation.empty()
                            : playbackQueueManager.queuePreparationForNewPlayer();
                },
                startPositionMs -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    if (playbackQueueManager != null) {
                        playbackQueueManager.consumeRestoredPositionAfterPrepare(startPositionMs);
                    }
                }
        );
    }

    PlaybackCurrentTrackPreparationQueueOwner(
            Consumer<Track> replaceCurrentQueueTrack,
            Function<Track, Long> restoredPositionFor,
            Supplier<PlaybackQueueManager.QueuePreparation> queuePreparationForNewPlayer,
            LongConsumer consumeRestoredPositionAfterPrepare
    ) {
        this.replaceCurrentQueueTrack = replaceCurrentQueueTrack;
        this.restoredPositionFor = restoredPositionFor;
        this.queuePreparationForNewPlayer = queuePreparationForNewPlayer;
        this.consumeRestoredPositionAfterPrepare = consumeRestoredPositionAfterPrepare;
    }

    @Override
    public void replaceCurrentQueueTrack(Track track) {
        if (replaceCurrentQueueTrack != null) {
            replaceCurrentQueueTrack.accept(track);
        }
    }

    @Override
    public long restoredPositionFor(Track track) {
        Long positionMs = restoredPositionFor == null ? null : restoredPositionFor.apply(track);
        return positionMs == null ? 0L : positionMs;
    }

    PlaybackQueueManager.QueuePreparation queuePreparationForNewPlayer() {
        return queuePreparationForNewPlayer == null
                ? PlaybackQueueManager.QueuePreparation.empty()
                : queuePreparationForNewPlayer.get();
    }

    void consumeRestoredPositionAfterPrepare(long startPositionMs) {
        if (consumeRestoredPositionAfterPrepare != null) {
            consumeRestoredPositionAfterPrepare.accept(startPositionMs);
        }
    }
}
