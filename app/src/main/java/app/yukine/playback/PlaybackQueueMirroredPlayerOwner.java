package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class PlaybackQueueMirroredPlayerOwner implements PlaybackQueueManager.MirroredQueuePlayer {
    private final BooleanSupplier mirroredQueueMatcher;
    private final BooleanSupplier playerAvailability;
    private final Consumer<Boolean> preparingStateController;
    private PlaybackQueueManager playbackQueueManager;
    private final Consumer<Track> waveformResetter;
    private final Runnable playbackParameterApplier;
    private final BiConsumer<Integer, Long> playerSeeker;
    private final Consumer<Boolean> playWhenReadySetter;
    private final Runnable playerStarter;
    private final Consumer<Boolean> mirrorStateController;
    private final Consumer<IllegalStateException> failureLogger;

    static BooleanSupplier mirroredQueueMatcher(
            BooleanSupplier mirrorStateProvider,
            IntSupplier playerMediaItemCountProvider,
            Supplier<List<Track>> queueSnapshotProvider,
            BiPredicate<Integer, Track> queueTrackMatcher
    ) {
        return new MirroredQueueSnapshotMatcher(
                Objects.requireNonNull(mirrorStateProvider, "mirrorStateProvider"),
                Objects.requireNonNull(playerMediaItemCountProvider, "playerMediaItemCountProvider"),
                Objects.requireNonNull(queueSnapshotProvider, "queueSnapshotProvider"),
                Objects.requireNonNull(queueTrackMatcher, "queueTrackMatcher")
        );
    }

    PlaybackQueueMirroredPlayerOwner(
            BooleanSupplier mirrorStateProvider,
            IntSupplier playerMediaItemCountProvider,
            BiPredicate<Integer, Track> queueTrackMatcher,
            BooleanSupplier playerAvailability,
            Consumer<Boolean> preparingStateController,
            Consumer<Track> waveformResetter,
            Runnable playbackParameterApplier,
            BiConsumer<Integer, Long> playerSeeker,
            Consumer<Boolean> playWhenReadySetter,
            Runnable playerStarter,
            Consumer<Boolean> mirrorStateController,
            Consumer<IllegalStateException> failureLogger
    ) {
        this.mirroredQueueMatcher = mirroredQueueMatcher(
                Objects.requireNonNull(mirrorStateProvider, "mirrorStateProvider"),
                Objects.requireNonNull(playerMediaItemCountProvider, "playerMediaItemCountProvider"),
                this::queueSnapshot,
                Objects.requireNonNull(queueTrackMatcher, "queueTrackMatcher")
        );
        this.playerAvailability = Objects.requireNonNull(playerAvailability, "playerAvailability");
        this.preparingStateController = Objects.requireNonNull(preparingStateController, "preparingStateController");
        this.waveformResetter = Objects.requireNonNull(waveformResetter, "waveformResetter");
        this.playbackParameterApplier = Objects.requireNonNull(playbackParameterApplier, "playbackParameterApplier");
        this.playerSeeker = Objects.requireNonNull(playerSeeker, "playerSeeker");
        this.playWhenReadySetter = Objects.requireNonNull(playWhenReadySetter, "playWhenReadySetter");
        this.playerStarter = Objects.requireNonNull(playerStarter, "playerStarter");
        this.mirrorStateController = Objects.requireNonNull(mirrorStateController, "mirrorStateController");
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
    }

    PlaybackQueueMirroredPlayerOwner(
            BooleanSupplier mirroredQueueMatcher,
            BooleanSupplier playerAvailability,
            Consumer<Boolean> preparingStateController,
            Consumer<Track> waveformResetter,
            Runnable playbackParameterApplier,
            BiConsumer<Integer, Long> playerSeeker,
            Consumer<Boolean> playWhenReadySetter,
            Runnable playerStarter,
            Consumer<Boolean> mirrorStateController,
            Consumer<IllegalStateException> failureLogger
    ) {
        this.mirroredQueueMatcher = Objects.requireNonNull(mirroredQueueMatcher, "mirroredQueueMatcher");
        this.playerAvailability = Objects.requireNonNull(playerAvailability, "playerAvailability");
        this.preparingStateController = Objects.requireNonNull(preparingStateController, "preparingStateController");
        this.waveformResetter = Objects.requireNonNull(waveformResetter, "waveformResetter");
        this.playbackParameterApplier = Objects.requireNonNull(playbackParameterApplier, "playbackParameterApplier");
        this.playerSeeker = Objects.requireNonNull(playerSeeker, "playerSeeker");
        this.playWhenReadySetter = Objects.requireNonNull(playWhenReadySetter, "playWhenReadySetter");
        this.playerStarter = Objects.requireNonNull(playerStarter, "playerStarter");
        this.mirrorStateController = Objects.requireNonNull(mirrorStateController, "mirrorStateController");
        this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
    }

    void bindPlaybackQueueManager(PlaybackQueueManager playbackQueueManager) {
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
    }

    @Override
    public boolean matchesCurrentQueue() {
        return hasPlayer() && mirroredQueueMatcher.getAsBoolean();
    }

    @Override
    public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
        if (!hasPlayer()) {
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
        return queueStateSnapshot().getCurrentTrack();
    }

    private PlaybackQueueManager.QueueStateSnapshot queueStateSnapshot() {
        return Objects.requireNonNull(playbackQueueManager, "playbackQueueManager").queueStateSnapshot();
    }

    private boolean hasPlayer() {
        return playerAvailability.getAsBoolean();
    }

    private List<Track> queueSnapshot() {
        return Objects.requireNonNull(playbackQueueManager, "playbackQueueManager").queueSnapshot();
    }

    private static final class MirroredQueueSnapshotMatcher implements BooleanSupplier {
        private final BooleanSupplier mirrorStateProvider;
        private final IntSupplier playerMediaItemCountProvider;
        private final Supplier<List<Track>> queueSnapshotProvider;
        private final BiPredicate<Integer, Track> queueTrackMatcher;

        private MirroredQueueSnapshotMatcher(
                BooleanSupplier mirrorStateProvider,
                IntSupplier playerMediaItemCountProvider,
                Supplier<List<Track>> queueSnapshotProvider,
                BiPredicate<Integer, Track> queueTrackMatcher
        ) {
            this.mirrorStateProvider = mirrorStateProvider;
            this.playerMediaItemCountProvider = playerMediaItemCountProvider;
            this.queueSnapshotProvider = queueSnapshotProvider;
            this.queueTrackMatcher = queueTrackMatcher;
        }

        @Override
        public boolean getAsBoolean() {
            if (!mirrorStateProvider.getAsBoolean()) {
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
