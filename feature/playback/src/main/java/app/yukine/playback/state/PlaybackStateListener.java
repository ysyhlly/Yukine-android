package app.yukine.playback.state;

import app.yukine.playback.PlaybackStateSnapshot;

public interface PlaybackStateListener {
    void onPlaybackStateChanged(PlaybackStateSnapshot snapshot);

    default void onPlaybackBuffering(PlaybackStateSnapshot snapshot) {
    }
}
