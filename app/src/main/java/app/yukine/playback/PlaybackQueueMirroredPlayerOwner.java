package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

final class PlaybackQueueMirroredPlayerOwner implements PlaybackQueueManager.MirroredQueuePlayer {
    interface MirroredQueueMatcher {
        boolean matchesCurrentQueue();
    }

    interface MirrorStateProvider {
        boolean playerMirrorsQueue();
    }

    interface PlayerAvailability {
        boolean hasPlayer();
    }

    interface PlayerMediaItemCountProvider {
        int mediaItemCount();
    }

    interface PreparingStateController {
        void setPreparing(boolean preparing);
    }

    interface CurrentTrackProvider {
        Track currentTrack();
    }

    interface WaveformResetter {
        void resetWaveformIfTrackChanged(Track track);
    }

    interface PlaybackParameterApplier {
        void applyPlaybackModeAndParametersToPlayer();
    }

    interface PlayerSeeker {
        void seekTo(int index, long positionMs);
    }

    interface PlayWhenReadySetter {
        void setPlayWhenReady(boolean playWhenReady);
    }

    interface PlayerStarter {
        void play();
    }

    interface MirrorStateController {
        void setPlayerMirrorsQueue(boolean enabled);
    }

    interface FailureLogger {
        void logUnableToReuseMirroredQueue(IllegalStateException error);
    }

    private final MirroredQueueMatcher mirroredQueueMatcher;
    private final PlayerAvailability playerAvailability;
    private final PreparingStateController preparingStateController;
    private final CurrentTrackProvider currentTrackProvider;
    private final WaveformResetter waveformResetter;
    private final PlaybackParameterApplier playbackParameterApplier;
    private final PlayerSeeker playerSeeker;
    private final PlayWhenReadySetter playWhenReadySetter;
    private final PlayerStarter playerStarter;
    private final MirrorStateController mirrorStateController;
    private final FailureLogger failureLogger;

    static MirroredQueueMatcher fromPlaybackQueueManager(
            MirrorStateProvider mirrorStateProvider,
            PlayerAvailability playerAvailability,
            PlayerMediaItemCountProvider playerMediaItemCountProvider,
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

    static MirroredQueueMatcher mirroredQueueMatcher(
            MirrorStateProvider mirrorStateProvider,
            PlayerAvailability playerAvailability,
            PlayerMediaItemCountProvider playerMediaItemCountProvider,
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
            MirroredQueueMatcher mirroredQueueMatcher,
            PlayerAvailability playerAvailability,
            PreparingStateController preparingStateController,
            CurrentTrackProvider currentTrackProvider,
            WaveformResetter waveformResetter,
            PlaybackParameterApplier playbackParameterApplier,
            PlayerSeeker playerSeeker,
            PlayWhenReadySetter playWhenReadySetter,
            PlayerStarter playerStarter,
            MirrorStateController mirrorStateController,
            FailureLogger failureLogger
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
        return mirroredQueueMatcher.matchesCurrentQueue();
    }

    @Override
    public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
        if (!playerAvailability.hasPlayer()) {
            return false;
        }
        preparingStateController.setPreparing(false);
        try {
            Track track = currentTrackProvider.currentTrack();
            if (track != null) {
                waveformResetter.resetWaveformIfTrackChanged(track);
            }
            playbackParameterApplier.applyPlaybackModeAndParametersToPlayer();
            playerSeeker.seekTo(index, positionMs);
            playWhenReadySetter.setPlayWhenReady(playWhenReady);
            if (playWhenReady) {
                playerStarter.play();
            }
            return true;
        } catch (IllegalStateException error) {
            mirrorStateController.setPlayerMirrorsQueue(false);
            failureLogger.logUnableToReuseMirroredQueue(error);
            return false;
        }
    }

    private static final class PlaybackQueueManagerMatcher implements MirroredQueueMatcher {
        private final MirrorStateProvider mirrorStateProvider;
        private final PlayerAvailability playerAvailability;
        private final PlayerMediaItemCountProvider playerMediaItemCountProvider;
        private final Supplier<BiPredicate<Integer, PlaybackQueueManager.QueueTrackMatcher>> mirroredQueueMatcherSupplier;
        private final PlaybackQueueManager.QueueTrackMatcher queueTrackMatcher;

        private PlaybackQueueManagerMatcher(
                MirrorStateProvider mirrorStateProvider,
                PlayerAvailability playerAvailability,
                PlayerMediaItemCountProvider playerMediaItemCountProvider,
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
        public boolean matchesCurrentQueue() {
            if (mirrorStateProvider == null
                    || !mirrorStateProvider.playerMirrorsQueue()
                    || playerAvailability == null
                    || !playerAvailability.hasPlayer()
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
                        playerMediaItemCountProvider.mediaItemCount(),
                        queueTrackMatcher
                );
            } catch (IllegalStateException error) {
                return false;
            }
        }
    }
}
