package app.yukine.playback;

import app.yukine.model.Track;

public final class PlaybackStateSnapshot {
    public final Track currentTrack;
    public final int currentIndex;
    public final int queueSize;
    public final long queueRevision;
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
    public final PlaybackSpectrumSnapshot spectrum;
    public final float realtimeBeat;
    public final boolean bitPerfectActive;
    public final int outputSampleRateHz;
    public final String bitPerfectFallbackReason;
    public final boolean audioExclusiveActive;
    public final AudioOutputSnapshot audioOutput;

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
            PlaybackWaveformSnapshot waveform,
            PlaybackSpectrumSnapshot spectrum,
            float realtimeBeat,
            long queueRevision,
            boolean bitPerfectActive,
            int outputSampleRateHz,
            String bitPerfectFallbackReason,
            boolean audioExclusiveActive,
            AudioOutputSnapshot audioOutput
    ) {
        this.currentTrack = currentTrack;
        this.currentIndex = currentIndex;
        this.queueSize = Math.max(queueSize, 0);
        this.queueRevision = Math.max(queueRevision, 0L);
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
        this.spectrum = spectrum == null ? PlaybackSpectrumSnapshot.empty() : spectrum;
        this.realtimeBeat = Math.max(0f, Math.min(realtimeBeat, 1f));
        this.audioOutput = audioOutput == null ? AudioOutputSnapshot.idle() : audioOutput;
        boolean hasTypedAudioState = this.audioOutput.phase != AudioOutputPhase.IDLE;
        this.bitPerfectActive = bitPerfectActive
                && (!hasTypedAudioState || this.audioOutput.phase == AudioOutputPhase.ACTIVE);
        this.outputSampleRateHz = hasTypedAudioState && this.audioOutput.sampleRateHz > 0
                ? this.audioOutput.sampleRateHz
                : Math.max(outputSampleRateHz, 0);
        this.bitPerfectFallbackReason = hasTypedAudioState
                && this.audioOutput.fallbackReason != AudioFallbackReason.NONE
                ? this.audioOutput.fallbackReason.name()
                : bitPerfectFallbackReason;
        this.audioExclusiveActive = audioExclusiveActive;
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
            long sleepTimerRemainingMs,
            PlaybackWaveformSnapshot waveform,
            PlaybackSpectrumSnapshot spectrum,
            float realtimeBeat,
            long queueRevision,
            boolean bitPerfectActive,
            int outputSampleRateHz,
            String bitPerfectFallbackReason,
            boolean audioExclusiveActive
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
                waveform,
                spectrum,
                realtimeBeat,
                queueRevision,
                bitPerfectActive,
                outputSampleRateHz,
                bitPerfectFallbackReason,
                audioExclusiveActive,
                AudioOutputSnapshot.idle()
        );
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
            long sleepTimerRemainingMs,
            PlaybackWaveformSnapshot waveform,
            PlaybackSpectrumSnapshot spectrum,
            float realtimeBeat
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
                waveform,
                spectrum,
                realtimeBeat,
                0L,
                false,
                0,
                null,
                false
        );
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
            long sleepTimerRemainingMs,
            PlaybackWaveformSnapshot waveform,
            PlaybackSpectrumSnapshot spectrum
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
                waveform,
                spectrum,
                0f,
                0L,
                false,
                0,
                null,
                false
        );
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
            long sleepTimerRemainingMs,
            PlaybackWaveformSnapshot waveform
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
                waveform,
                PlaybackSpectrumSnapshot.empty(),
                0f,
                0L,
                false,
                0,
                null,
                false
        );
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
            long sleepTimerRemainingMs,
            long queueRevision
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
                PlaybackWaveformSnapshot.empty(),
                PlaybackSpectrumSnapshot.empty(),
                0f,
                queueRevision,
                false,
                0,
                null,
                false
        );
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
                0L
        );
    }

    public boolean hasTrack() {
        return currentTrack != null;
    }

    public static PlaybackStateSnapshot empty() {
        return new PlaybackStateSnapshot(null, -1, 0, 0L, 0L, false, false, "", false, 0, 1.0f, 1.0f, 0L);
    }
}
