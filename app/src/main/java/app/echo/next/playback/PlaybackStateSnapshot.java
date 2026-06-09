package app.echo.next.playback;

import app.echo.next.model.Track;

public final class PlaybackStateSnapshot {
    public final Track currentTrack;
    public final int currentIndex;
    public final int queueSize;
    public final long positionMs;
    public final long durationMs;
    public final boolean playing;
    public final boolean preparing;
    public final String errorMessage;
    public final boolean shuffleEnabled;
    public final int repeatMode;
    public final float playbackSpeed;
    public final float appVolume;
    public final long sleepTimerRemainingMs;
    public final PlaybackWaveformSnapshot waveform;

    public PlaybackStateSnapshot(
            Track currentTrack,
            int currentIndex,
            int queueSize,
            long positionMs,
            long durationMs,
            boolean playing,
            boolean preparing,
            String errorMessage,
            boolean shuffleEnabled,
            int repeatMode,
            float playbackSpeed,
            float appVolume,
            long sleepTimerRemainingMs,
            PlaybackWaveformSnapshot waveform
    ) {
        this.currentTrack = currentTrack;
        this.currentIndex = currentIndex;
        this.queueSize = Math.max(queueSize, 0);
        this.positionMs = Math.max(positionMs, 0L);
        this.durationMs = Math.max(durationMs, 0L);
        this.playing = playing;
        this.preparing = preparing;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.shuffleEnabled = shuffleEnabled;
        this.repeatMode = repeatMode;
        this.playbackSpeed = playbackSpeed <= 0f ? 1.0f : playbackSpeed;
        this.appVolume = Math.max(0.0f, Math.min(appVolume, 1.0f));
        this.sleepTimerRemainingMs = Math.max(sleepTimerRemainingMs, 0L);
        this.waveform = waveform == null ? PlaybackWaveformSnapshot.empty() : waveform;
    }

    public PlaybackStateSnapshot(
            Track currentTrack,
            int currentIndex,
            int queueSize,
            long positionMs,
            long durationMs,
            boolean playing,
            boolean preparing,
            String errorMessage,
            boolean shuffleEnabled,
            int repeatMode,
            float playbackSpeed,
            float appVolume,
            long sleepTimerRemainingMs
    ) {
        this(
                currentTrack,
                currentIndex,
                queueSize,
                positionMs,
                durationMs,
                playing,
                preparing,
                errorMessage,
                shuffleEnabled,
                repeatMode,
                playbackSpeed,
                appVolume,
                sleepTimerRemainingMs,
                PlaybackWaveformSnapshot.empty()
        );
    }

    public boolean hasTrack() {
        return currentTrack != null;
    }

    public static PlaybackStateSnapshot empty() {
        return new PlaybackStateSnapshot(null, -1, 0, 0L, 0L, false, false, "", false, 0, 1.0f, 1.0f, 0L);
    }
}
