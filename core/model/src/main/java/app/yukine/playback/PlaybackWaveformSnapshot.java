package app.yukine.playback;

public final class PlaybackWaveformSnapshot {
    public final float[] bars;
    public final int generatedBars;
    public final float cachedProgress;

    public PlaybackWaveformSnapshot(float[] bars, int generatedBars, float cachedProgress) {
        this.bars = bars == null ? new float[0] : bars.clone();
        this.generatedBars = Math.max(0, Math.min(generatedBars, this.bars.length));
        this.cachedProgress = Math.max(0.0f, Math.min(cachedProgress, 1.0f));
    }

    public boolean hasBars() {
        return bars.length > 0 && generatedBars > 0;
    }

    public static PlaybackWaveformSnapshot empty() {
        return new PlaybackWaveformSnapshot(new float[0], 0, 0.0f);
    }
}
