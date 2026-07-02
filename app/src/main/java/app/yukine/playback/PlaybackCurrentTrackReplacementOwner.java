package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Supplier;

final class PlaybackCurrentTrackReplacementOwner {
    interface RecoveryDiagnosticsRecorder {
        void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery);
    }

    interface RecoveryScheduler {
        void scheduleCurrentPlaybackRecovery(boolean playWhenReady);
    }

    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder;
    private final RecoveryScheduler recoveryScheduler;

    PlaybackCurrentTrackReplacementOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder,
            RecoveryScheduler recoveryScheduler
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.recoveryDiagnosticsRecorder = recoveryDiagnosticsRecorder;
        this.recoveryScheduler = recoveryScheduler;
    }

    static PlaybackCurrentTrackReplacementOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder,
            RecoveryScheduler recoveryScheduler
    ) {
        return new PlaybackCurrentTrackReplacementOwner(
                playbackQueueManagerSupplier,
                recoveryDiagnosticsRecorder,
                recoveryScheduler
        );
    }

    void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        PlaybackQueueManager playbackQueueManager = playbackQueueManager();
        if (playbackQueueManager == null) {
            return;
        }
        PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
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

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
