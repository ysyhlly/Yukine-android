package app.yukine.playback;

public interface PlaybackStateListener {
    void onPlaybackStateChanged(PlaybackStateSnapshot snapshot);

    default void onPlaybackBuffering(PlaybackStateSnapshot snapshot) {
    }
}
