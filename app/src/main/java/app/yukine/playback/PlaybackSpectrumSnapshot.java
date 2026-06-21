package app.yukine.playback;

public final class PlaybackSpectrumSnapshot {
    public final float[] bands;
    public final int generatedFrames;
    public final int bandCount;
    public final float cachedProgress;

    public PlaybackSpectrumSnapshot(float[] bands, int generatedFrames, int bandCount, float cachedProgress) {
        this.bands = bands == null ? new float[0] : bands.clone();
        this.bandCount = Math.max(0, bandCount);
        int maxFrames = this.bandCount <= 0 ? 0 : this.bands.length / this.bandCount;
        this.generatedFrames = Math.max(0, Math.min(generatedFrames, maxFrames));
        this.cachedProgress = Math.max(0.0f, Math.min(cachedProgress, 1.0f));
    }

    public boolean hasBands() {
        return bandCount > 0 && generatedFrames > 0 && bands.length >= bandCount;
    }

    public static PlaybackSpectrumSnapshot empty() {
        return new PlaybackSpectrumSnapshot(new float[0], 0, 0, 0.0f);
    }
}
