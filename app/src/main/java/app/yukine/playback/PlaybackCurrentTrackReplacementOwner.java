package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackCurrentTrackReplacementOwner {
    interface CurrentTrackReplacementOperations {
        PlaybackQueueManager.CurrentTrackReplacementRecovery replaceCurrentTrackAndResume(
                Track replacement,
                long positionMs
        );
    }

    interface RecoveryDiagnosticsRecorder {
        void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery);
    }

    interface RecoveryScheduler {
        void scheduleCurrentPlaybackRecovery(boolean playWhenReady);
    }

    private final Supplier<CurrentTrackReplacementOperations> currentTrackReplacementOperationsSupplier;
    private final RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder;
    private final RecoveryScheduler recoveryScheduler;

    PlaybackCurrentTrackReplacementOwner(
            Supplier<CurrentTrackReplacementOperations> currentTrackReplacementOperationsSupplier,
            RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder,
            RecoveryScheduler recoveryScheduler
    ) {
        this.currentTrackReplacementOperationsSupplier = currentTrackReplacementOperationsSupplier;
        this.recoveryDiagnosticsRecorder = recoveryDiagnosticsRecorder;
        this.recoveryScheduler = recoveryScheduler;
    }

    static PlaybackCurrentTrackReplacementOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder,
            RecoveryScheduler recoveryScheduler
    ) {
        return new PlaybackCurrentTrackReplacementOwner(
                () -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : new PlaybackQueueManagerOperations(playbackQueueManager);
                },
                recoveryDiagnosticsRecorder,
                recoveryScheduler
        );
    }

    void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        CurrentTrackReplacementOperations operations = currentTrackReplacementOperations();
        if (operations == null) {
            return;
        }
        PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                operations.replaceCurrentTrackAndResume(replacement, positionMs);
        if (recovery == null) {
            return;
        }
        if (recoveryDiagnosticsRecorder != null) {
            recoveryDiagnosticsRecorder.record(recovery);
        }
        if (recoveryScheduler != null) {
            recoveryScheduler.scheduleCurrentPlaybackRecovery(recovery.getPlayWhenReady());
        }
    }

    private CurrentTrackReplacementOperations currentTrackReplacementOperations() {
        return currentTrackReplacementOperationsSupplier == null
                ? null
                : currentTrackReplacementOperationsSupplier.get();
    }

    private static final class PlaybackQueueManagerOperations implements CurrentTrackReplacementOperations {
        private final PlaybackQueueManager playbackQueueManager;

        private PlaybackQueueManagerOperations(PlaybackQueueManager playbackQueueManager) {
            this.playbackQueueManager = playbackQueueManager;
        }

        @Override
        public PlaybackQueueManager.CurrentTrackReplacementRecovery replaceCurrentTrackAndResume(
                Track replacement,
                long positionMs
        ) {
            return playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
        }
    }
}
