package app.echo.next.playback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StreamingWaveformGeneratorTest {
    @Test
    public void generatedBarsStopAtFirstUncachedGap() {
        assertEquals(3, StreamingWaveformGenerator.continuousGeneratedBars(
                new int[]{4, 6, 2, 0, 9, 7},
                6
        ));
    }

    @Test
    public void generatedBarsTreatSilentDecodedBucketsAsGenerated() {
        assertEquals(4, StreamingWaveformGenerator.continuousGeneratedBars(
                new int[]{1, 1, 1, 1, 0},
                5
        ));
    }

    @Test
    public void generatedBarsRespectRequestedLimit() {
        assertEquals(2, StreamingWaveformGenerator.continuousGeneratedBars(
                new int[]{3, 3, 3, 3},
                2
        ));
    }

    @Test
    public void generatedBarsReturnZeroWhenThePrefixIsMissing() {
        assertEquals(0, StreamingWaveformGenerator.continuousGeneratedBars(
                new int[]{0, 5, 5, 5},
                4
        ));
    }
}
