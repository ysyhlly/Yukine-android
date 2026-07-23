package app.yukine.playback;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.together.TogetherPlayerPort;

/**
 * Narrow Media3 boundary used by junto. Remote commands always enter through the playback
 * runtime's application-looper methods; protocol and session state remain in feature:together.
 */
final class TogetherMedia3PlayerAdapter implements TogetherPlayerPort {
    private final PlaybackServiceRuntime runtime;

    TogetherMedia3PlayerAdapter(PlaybackServiceRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void play() {
        runtime.play();
    }

    @Override
    public void pause() {
        runtime.pause();
    }

    @Override
    public void seekTo(long positionMs) {
        runtime.seekTo(positionMs);
    }

    @Override
    public void setSpeed(float speed) {
        runtime.setPlaybackSpeed(speed);
    }

    @Override
    public void skipToQueueIndex(int index) {
        runtime.seekToTogetherQueueIndex(index);
    }

    @Override
    public long currentPositionMs() {
        PlaybackStateSnapshot snapshot = runtime.snapshot();
        return snapshot == null ? 0L : snapshot.positionMs;
    }

    @Override
    public int currentQueueIndex() {
        PlaybackStateSnapshot snapshot = runtime.snapshot();
        return snapshot == null ? 0 : snapshot.currentIndex;
    }

    @Override
    public void setRoomPlaybackConstraints(boolean enabled) {
        runtime.setTogetherRoomActive(enabled);
    }

    @Override
    public void replaceQueueWithStreamUrls(List<String> urls) {
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            tracks.add(new Track(
                    -9_000_000L - i,
                    "Together " + (i + 1),
                    "",
                    "junto",
                    0L,
                    Uri.parse(url),
                    url
            ));
        }
        runtime.replaceTogetherQueue(tracks);
    }
}
