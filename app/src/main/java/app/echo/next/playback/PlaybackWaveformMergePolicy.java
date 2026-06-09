package app.echo.next.playback;

final class PlaybackWaveformMergePolicy {
    private PlaybackWaveformMergePolicy() {
    }

    static PlaybackWaveformSnapshot merge(
            PlaybackWaveformSnapshot current,
            PlaybackWaveformSnapshot generated,
            float cachedProgress
    ) {
        PlaybackWaveformSnapshot safeCurrent = current == null
                ? PlaybackWaveformSnapshot.empty()
                : current;
        float mergedCachedProgress = Math.max(safeCurrent.cachedProgress, cachedProgress);
        if (generated != null) {
            mergedCachedProgress = Math.max(mergedCachedProgress, generated.cachedProgress);
        }
        if (generated != null
                && generated.hasBars()
                && generated.generatedBars >= safeCurrent.generatedBars) {
            return new PlaybackWaveformSnapshot(
                    generated.bars,
                    generated.generatedBars,
                    mergedCachedProgress
            );
        }
        if (mergedCachedProgress > safeCurrent.cachedProgress) {
            return new PlaybackWaveformSnapshot(
                    safeCurrent.bars,
                    safeCurrent.generatedBars,
                    mergedCachedProgress
            );
        }
        return safeCurrent;
    }
}
