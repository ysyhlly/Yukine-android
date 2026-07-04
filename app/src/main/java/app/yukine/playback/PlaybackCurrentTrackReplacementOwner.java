package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

import java.util.Objects;
import java.util.function.BiConsumer;

final class PlaybackCurrentTrackReplacementOwner {
    private final PlaybackQueueManager playbackQueueManager;
    private final BiConsumer<Track, Long> recoveryDiagnosticsRecorder;
    private final Runnable recoveryScheduler;

    PlaybackCurrentTrackReplacementOwner(
            PlaybackQueueManager playbackQueueManager,
            BiConsumer<Track, Long> recoveryDiagnosticsRecorder,
            Runnable recoveryScheduler
    ) {
        this.playbackQueueManager = Objects.requireNonNull(playbackQueueManager, "playbackQueueManager");
        this.recoveryDiagnosticsRecorder = recoveryDiagnosticsRecorder;
        this.recoveryScheduler = recoveryScheduler;
    }

    void replaceCurrentTrackAndResume(Track replacement, long positionMs) {
        Long restoredPositionMs =
                playbackQueueManager.replaceCurrentTrackAndResume(replacement, positionMs);
        if (restoredPositionMs == null) {
            return;
        }
        if (recoveryDiagnosticsRecorder != null) {
            recoveryDiagnosticsRecorder.accept(replacement, restoredPositionMs);
        }
        if (recoveryScheduler != null) {
            recoveryScheduler.run();
        }
    }
}
