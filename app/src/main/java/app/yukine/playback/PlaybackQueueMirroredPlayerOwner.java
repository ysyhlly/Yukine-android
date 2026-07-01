package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

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
    private final Supplier<Track> currentTrackProvider;
    private final Consumer<Track> waveformResetter;
    private final Runnable playbackParameterApplier;
    private final BiConsumer<Integer, Long> playerSeeker;
    private final Consumer<Boolean> playWhenReadySetter;
    private final Runnable playerStarter;
    private final Consumer<Boolean> mirrorStateController;
    private final Consumer<IllegalStateException> failureLogger;

    static BooleanSupplier fromPlaybackQueueManager(
            BooleanSupplier mirrorStateProvider,
            BooleanSupplier playerAvailability,
            IntSupplier playerMediaItemCountProvider,
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher
    ) {
        return mirroredQueueMatcher(
                mirrorStateProvider,
                playerAvailability,
                playerMediaItemCountProvider,
                () -> {
                    PlaybackQueueManager playbackQueueManager =
                            playbackQueueManagerSupplier == null
                                    ? null
                                    : playbackQueueManagerSupplier.get();
                    return playbackQueueManager != null
                            ? playbackQueueManager::matchesMirroredQueue
                            : null;
                },
                queueTrackMatcher
        );
    }

    static BooleanSupplier mirroredQueueMatcher(
            BooleanSupplier mirrorStateProvider,
            BooleanSupplier playerAvailability,
            IntSupplier playerMediaItemCountProvider,
            Supplier<BiPredicate<Integer, PlaybackQueueManager.QueueTrackMatcher>> mirroredQueueMatcherSupplier,
            PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher
    ) {
        return new PlaybackQueueManagerMatcher(
                mirrorStateProvider,
                playerAvailability,
                playerMediaItemCountProvider,
                mirroredQueueMatcherSupplier,
                queueTrackMatcher
        );
    }

    PlaybackQueueMirroredPlayerOwner(
            BooleanSupplier mirroredQueueMatcher,
            BooleanSupplier playerAvailability,
            Consumer<Boolean> preparingStateController,
            Supplier<Track> currentTrackProvider,
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
        this.currentTrackProvider = currentTrackProvider;
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
            Track track = currentTrackProvider.get();
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

    private static final class PlaybackQueueManagerMatcher implements BooleanSupplier {
        private final BooleanSupplier mirrorStateProvider;
        private final BooleanSupplier playerAvailability;
        private final IntSupplier playerMediaItemCountProvider;
        private final Supplier<BiPredicate<Integer, PlaybackQueueManager.QueueTrackMatcher>> mirroredQueueMatcherSupplier;
        private final PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher;

        private PlaybackQueueManagerMatcher(
                BooleanSupplier mirrorStateProvider,
                BooleanSupplier playerAvailability,
                IntSupplier playerMediaItemCountProvider,
                Supplier<BiPredicate<Integer, PlaybackQueueManager.QueueTrackMatcher>> mirroredQueueMatcherSupplier,
                PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher
        ) {
            this.mirrorStateProvider = mirrorStateProvider;
            this.playerAvailability = playerAvailability;
            this.playerMediaItemCountProvider = playerMediaItemCountProvider;
            this.mirroredQueueMatcherSupplier = mirroredQueueMatcherSupplier;
            this.queueTrackMatcher = queueTrackMatcher;
        }

        @Override
        public boolean getAsBoolean() {
            if (mirrorStateProvider == null
                    || !mirrorStateProvider.getAsBoolean()
                    || playerAvailability == null
                    || !playerAvailability.getAsBoolean()
                    || playerMediaItemCountProvider == null
                    || mirroredQueueMatcherSupplier == null
                    || queueTrackMatcher == null) {
                return false;
            }
            BiPredicate<Integer, PlaybackQueueManager.QueueTrackMatcher> mirroredQueueMatcher =
                    mirroredQueueMatcherSupplier.get();
            if (mirroredQueueMatcher == null) {
                return false;
            }
            try {
                return mirroredQueueMatcher.test(
                        playerMediaItemCountProvider.getAsInt(),
                        queueTrackMatcher
                );
            } catch (IllegalStateException error) {
                return false;
            }
        }
    }
}
