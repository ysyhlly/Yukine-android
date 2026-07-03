package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;

final class PlaybackCurrentTrackReplacementOwner {
    private final PlaybackQueueManager playbackQueueManager;
    private final Consumer<PlaybackQueueManager.CurrentTrackReplacementRecovery> recoveryDiagnosticsRecorder;
    private final Consumer<Boolean> recoveryScheduler;

    PlaybackCurrentTrackReplacementOwner(
            PlaybackQueueManager playbackQueueManager,
            Consumer<PlaybackQueueManager.CurrentTrackReplacementRecovery> recoveryDiagnosticsRecorder,
            Consumer<Boolean> recoveryScheduler
    ) {
        this.playbackQueueManager = playbackQueueManager;
        this.recoveryDiagnosticsRecorder = recoveryDiagnosticsRecorder;
        this.recoveryScheduler = recoveryScheduler;
    }

    void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        if (playbackQueueManager == null) {
            return;
        }
        PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
        if (recovery == null) {
            return;
        }
        if (recoveryDiagnosticsRecorder != null) {
            recoveryDiagnosticsRecorder.accept(recovery);
        }
        if (recoveryScheduler != null) {
            recoveryScheduler.accept(recovery.getPlayWhenReady());
        }
    }
}
