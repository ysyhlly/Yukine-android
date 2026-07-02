package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackCurrentTrackReplacementOwner {
    private final Supplier<PlaybackQueueManager> playbackQueueManagerSupplier;
    private final Consumer<PlaybackQueueManager.CurrentTrackReplacementRecovery> recoveryDiagnosticsRecorder;
    private final Consumer<Boolean> recoveryScheduler;

    PlaybackCurrentTrackReplacementOwner(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            Consumer<PlaybackQueueManager.CurrentTrackReplacementRecovery> recoveryDiagnosticsRecorder,
            Consumer<Boolean> recoveryScheduler
    ) {
        this.playbackQueueManagerSupplier = playbackQueueManagerSupplier;
        this.recoveryDiagnosticsRecorder = recoveryDiagnosticsRecorder;
        this.recoveryScheduler = recoveryScheduler;
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
            recoveryDiagnosticsRecorder.accept(recovery);
        }
        if (recoveryScheduler != null) {
            recoveryScheduler.accept(recovery.getPlayWhenReady());
        }
    }

    private PlaybackQueueManager playbackQueueManager() {
        return playbackQueueManagerSupplier == null ? null : playbackQueueManagerSupplier.get();
    }
}
