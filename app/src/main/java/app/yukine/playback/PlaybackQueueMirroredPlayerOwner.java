package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.List;
import java.util.function.Supplier;

final class PlaybackQueueMirroredPlayerOwner implements PlaybackQueueManager.MirroredQueuePlayer {
    private final BooleanSupplier mirroredQueueMatcher;
    private final BooleanSupplier playerAvailability;
    private final Consumer<Boolean> preparingStateController;
    private final PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider;
    private final Consumer<Track> waveformResetter;
    private final Runnable playbackParameterApplier;
    private final BiConsumer<Integer, Long> playerSeeker;
    private final Consumer<Boolean> playWhenReadySetter;
    private final Runnable playerStarter;
    private final Consumer<Boolean> mirrorStateController;
    private final Consumer<IllegalStateException> failureLogger;

    static BooleanSupplier mirroredQueueMatcher(
            BooleanSupplier mirrorStateProvider,
            BooleanSupplier playerAvailability,
            IntSupplier playerMediaItemCountProvider,
            Supplier<List<Track>> queueSnapshotProvider,
            BiPredicate<Integer, Track> queueTrackMatcher
    ) {
        return new MirroredQueueSnapshotMatcher(
                mirrorStateProvider,
                playerAvailability,
                playerMediaItemCountProvider,
                queueSnapshotProvider,
                queueTrackMatcher
        );
    }

    PlaybackQueueMirroredPlayerOwner(
            BooleanSupplier mirroredQueueMatcher,
            BooleanSupplier playerAvailability,
            Consumer<Boolean> preparingStateController,
            PlaybackStateSnapshotOwner.QueueStateProvider queueStateProvider,
            Consumer<Track> waveformResetter,
            Runnable playbackParameterApplier,
            BiConsumer<Integer, Long> playerSeeker,
            Consumer<Boolean> playWhenReadySetter,
            Runnable playerStarter,
            Consumer<Boolean> mirrorStateController,
            Consumer<IllegalStateException> failureLogger
    ) {
        this.mirroredQueueMatcher = mirroredQueueMatcher;
        this.playerAvailability = playerAvailability;
        this.preparingStateController = preparingStateController;
        this.queueStateProvider = queueStateProvider;
        this.waveformResetter = waveformResetter;
        this.playbackParameterApplier = playbackParameterApplier;
        this.playerSeeker = playerSeeker;
        this.playWhenReadySetter = playWhenReadySetter;
        this.playerStarter = playerStarter;
        this.mirrorStateController = mirrorStateController;
        this.failureLogger = failureLogger;
    }

    @Override
    public boolean matchesCurrentQueue() {
        return mirroredQueueMatcher.getAsBoolean();
    }

    @Override
    public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
        if (!playerAvailability.getAsBoolean()) {
            return false;
        }
        preparingStateController.accept(false);
        try {
            Track track = currentTrack();
            if (track != null) {
                waveformResetter.accept(track);
            }
            playbackParameterApplier.run();
            playerSeeker.accept(index, positionMs);
            playWhenReadySetter.accept(playWhenReady);
            if (playWhenReady) {
                playerStarter.run();
            }
            return true;
        } catch (IllegalStateException error) {
            mirrorStateController.accept(false);
            failureLogger.accept(error);
            return false;
        }
    }

    private Track currentTrack() {
        if (queueStateProvider == null) {
            return null;
        }
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.queueStateSnapshot();
        return snapshot == null ? null : snapshot.getCurrentTrack();
    }

    private static final class MirroredQueueSnapshotMatcher implements BooleanSupplier {
        private final BooleanSupplier mirrorStateProvider;
        private final BooleanSupplier playerAvailability;
        private final IntSupplier playerMediaItemCountProvider;
        private final Supplier<List<Track>> queueSnapshotProvider;
        private final BiPredicate<Integer, Track> queueTrackMatcher;

        private MirroredQueueSnapshotMatcher(
                BooleanSupplier mirrorStateProvider,
                BooleanSupplier playerAvailability,
                IntSupplier playerMediaItemCountProvider,
                Supplier<List<Track>> queueSnapshotProvider,
                BiPredicate<Integer, Track> queueTrackMatcher
        ) {
            this.mirrorStateProvider = mirrorStateProvider;
            this.playerAvailability = playerAvailability;
            this.playerMediaItemCountProvider = playerMediaItemCountProvider;
            this.queueSnapshotProvider = queueSnapshotProvider;
            this.queueTrackMatcher = queueTrackMatcher;
        }

        @Override
        public boolean getAsBoolean() {
            if (mirrorStateProvider == null
                    || !mirrorStateProvider.getAsBoolean()
                    || playerAvailability == null
                    || !playerAvailability.getAsBoolean()
                    || playerMediaItemCountProvider == null
                    || queueSnapshotProvider == null
                    || queueTrackMatcher == null) {
                return false;
            }
            try {
                List<Track> queueSnapshot = queueSnapshotProvider.get();
                if (queueSnapshot == null || playerMediaItemCountProvider.getAsInt() != queueSnapshot.size()) {
                    return false;
                }
                for (int i = 0; i < queueSnapshot.size(); i++) {
                    Track track = queueSnapshot.get(i);
                    if (track == null || !queueTrackMatcher.test(i, track)) {
                        return false;
                    }
                }
                return true;
            } catch (IllegalStateException error) {
                return false;
            }
        }
    }
}
