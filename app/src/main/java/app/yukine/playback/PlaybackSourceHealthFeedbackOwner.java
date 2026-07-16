package app.yukine.playback;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import app.yukine.model.Track;

/** Moves playback-success persistence out of the player and audio callback threads. */
final class PlaybackSourceHealthFeedbackOwner {
    private final Executor executor;
    private final Consumer<Track> recorder;
    private String lastScheduledSourceKey = "";

    PlaybackSourceHealthFeedbackOwner(Executor executor, Consumer<Track> recorder) {
        this.executor = executor;
        this.recorder = recorder;
    }

    void recordFirstAudioOutput(Track track) {
        if (track == null || executor == null || recorder == null) {
            return;
        }
        String sourceKey = track.id + "\n" + (track.dataPath == null ? "" : track.dataPath);
        synchronized (this) {
            if (sourceKey.equals(lastScheduledSourceKey)) {
                return;
            }
            lastScheduledSourceKey = sourceKey;
        }
        executor.execute(() -> recorder.accept(track));
    }
}
