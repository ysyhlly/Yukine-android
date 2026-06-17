package app.yukine.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackWaveformMergePolicyTest {
    @Test
    public void shorterGeneratedWaveformDoesNotReplaceExistingBars() {
        PlaybackWaveformSnapshot current = new PlaybackWaveformSnapshot(
                new float[] {0.1f, 0.5f, 0.9f, 0.4f},
                4,
                0.4f
        );
        PlaybackWaveformSnapshot shorter = new PlaybackWaveformSnapshot(
                new float[] {0.8f, 0.2f},
                2,
                0.7f
        );

        PlaybackWaveformSnapshot merged = PlaybackWaveformMergePolicy.merge(current, shorter, 0.7f);

        assertArrayEquals(new float[] {0.1f, 0.5f, 0.9f, 0.4f}, merged.bars, 0.0001f);
        assertEquals(4, merged.generatedBars);
        assertEquals(0.7f, merged.cachedProgress, 0.0001f);
    }

    @Test
    public void longerGeneratedWaveformReplacesExistingBarsWithoutCachedRegression() {
        PlaybackWaveformSnapshot current = new PlaybackWaveformSnapshot(
                new float[] {0.1f, 0.3f},
                2,
                0.8f
        );
        PlaybackWaveformSnapshot longer = new PlaybackWaveformSnapshot(
                new float[] {0.2f, 0.4f, 0.6f},
                3,
                0.5f
        );

        PlaybackWaveformSnapshot merged = PlaybackWaveformMergePolicy.merge(current, longer, 0.5f);

        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f}, merged.bars, 0.0001f);
        assertEquals(3, merged.generatedBars);
        assertEquals(0.8f, merged.cachedProgress, 0.0001f);
    }

    @Test
    public void emptyGeneratedWaveformOnlyAdvancesCachedProgress() {
        PlaybackWaveformSnapshot current = new PlaybackWaveformSnapshot(
                new float[] {0.25f, 0.75f},
                2,
                0.2f
        );

        PlaybackWaveformSnapshot merged = PlaybackWaveformMergePolicy.merge(
                current,
                PlaybackWaveformSnapshot.empty(),
                0.6f
        );

        assertArrayEquals(new float[] {0.25f, 0.75f}, merged.bars, 0.0001f);
        assertEquals(2, merged.generatedBars);
        assertEquals(0.6f, merged.cachedProgress, 0.0001f);
    }
}
