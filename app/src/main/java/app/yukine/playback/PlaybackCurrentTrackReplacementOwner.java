package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.function.BiFunction;
import java.util.function.Supplier;

final class PlaybackCurrentTrackReplacementOwner {
    interface RecoveryDiagnosticsRecorder {
        void record(PlaybackQueueManager.CurrentTrackReplacementRecovery recovery);
    }

    interface RecoveryScheduler {
        void scheduleCurrentPlaybackRecovery(boolean playWhenReady);
    }

    private final BiFunction<Track, Long, PlaybackQueueManager.CurrentTrackReplacementRecovery> replaceCurrentTrackAndResume;
    private final RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder;
    private final RecoveryScheduler recoveryScheduler;

    PlaybackCurrentTrackReplacementOwner(
            BiFunction<Track, Long, PlaybackQueueManager.CurrentTrackReplacementRecovery> replaceCurrentTrackAndResume,
            RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder,
            RecoveryScheduler recoveryScheduler
    ) {
        this.replaceCurrentTrackAndResume = replaceCurrentTrackAndResume;
        this.recoveryDiagnosticsRecorder = recoveryDiagnosticsRecorder;
        this.recoveryScheduler = recoveryScheduler;
    }

    static PlaybackCurrentTrackReplacementOwner fromPlaybackQueueManager(
            Supplier<PlaybackQueueManager> playbackQueueManagerSupplier,
            RecoveryDiagnosticsRecorder recoveryDiagnosticsRecorder,
            RecoveryScheduler recoveryScheduler
    ) {
        return new PlaybackCurrentTrackReplacementOwner(
                (replacement, positionMs) -> {
                    PlaybackQueueManager playbackQueueManager = playbackQueueManagerSupplier == null
                            ? null
                            : playbackQueueManagerSupplier.get();
                    return playbackQueueManager == null
                            ? null
                            : playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
                },
                recoveryDiagnosticsRecorder,
                recoveryScheduler
        );
    }

    void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        if (replaceCurrentTrackAndResume == null) {
            return;
        }
        PlaybackQueueManager.CurrentTrackReplacementRecovery recovery =
                replaceCurrentTrackAndResume.apply(replacement, positionMs);
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
}
