package app.yukine.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackRealtimeVisualizationOwnerTest {
    @Test
    public void returnsRealtimeBeatAndBandsWhenPlaying() {
        float[] bands = new float[]{0.1f, 0.4f, 0.9f};
        FakeRealtimeDataProvider dataProvider = new FakeRealtimeDataProvider(0.7f, bands);
        PlaybackRealtimeVisualizationOwner owner = new PlaybackRealtimeVisualizationOwner(
                () -> true,
                dataProvider
        );

        assertEquals(0.7f, owner.beat(), 0.001f);
        assertArrayEquals(bands, owner.bands(), 0.001f);
        assertEquals(1, dataProvider.beatCalls);
        assertEquals(1, dataProvider.bandCalls);
    }

    @Test
    public void returnsEmptyRealtimeDataWhenPausedOrDependenciesAreMissing() {
        FakeRealtimeDataProvider dataProvider = new FakeRealtimeDataProvider(1.0f, new float[]{1.0f});
        PlaybackRealtimeVisualizationOwner paused = new PlaybackRealtimeVisualizationOwner(
                () -> false,
                dataProvider
        );
        PlaybackRealtimeVisualizationOwner missingPlaybackState = new PlaybackRealtimeVisualizationOwner(
                null,
                dataProvider
        );
        PlaybackRealtimeVisualizationOwner missingData = new PlaybackRealtimeVisualizationOwner(
                () -> true,
                null
        );

        assertEquals(0f, paused.beat(), 0.001f);
        assertEquals(0, paused.bands().length);
        assertEquals(0f, missingPlaybackState.beat(), 0.001f);
        assertEquals(0, missingPlaybackState.bands().length);
        assertEquals(0f, missingData.beat(), 0.001f);
        assertEquals(0, missingData.bands().length);
        assertEquals(0, dataProvider.beatCalls);
        assertEquals(0, dataProvider.bandCalls);
    }

    @Test
    public void returnsEmptyBandsWhenProviderReturnsNull() {
        PlaybackRealtimeVisualizationOwner owner = new PlaybackRealtimeVisualizationOwner(
                () -> true,
                new FakeRealtimeDataProvider(0.2f, null)
        );

        assertEquals(0, owner.bands().length);
    }

    @Test
    public void factoryWrapsRealtimeBassDetectorBehindPlaybackGate() {
        PlaybackRealtimeVisualizationOwner paused = PlaybackRealtimeVisualizationOwner.fromRealtimeBassDetector(
                () -> false,
                new RealtimeBassDetector()
        );
        PlaybackRealtimeVisualizationOwner playing = PlaybackRealtimeVisualizationOwner.fromRealtimeBassDetector(
                () -> true,
                new RealtimeBassDetector()
        );
        PlaybackRealtimeVisualizationOwner missingDetector = PlaybackRealtimeVisualizationOwner.fromRealtimeBassDetector(
                () -> true,
                null
        );

        assertEquals(0f, paused.beat(), 0.001f);
        assertEquals(0, paused.bands().length);
        assertEquals(0f, playing.beat(), 0.001f);
        assertEquals(RealtimeBassDetector.REALTIME_BAND_COUNT, playing.bands().length);
        assertEquals(0f, missingDetector.beat(), 0.001f);
        assertEquals(0, missingDetector.bands().length);
    }

    private static final class FakeRealtimeDataProvider
            implements PlaybackRealtimeVisualizationOwner.RealtimeDataProvider {
        private final float beat;
        private final float[] bands;
        private int beatCalls;
        private int bandCalls;

        private FakeRealtimeDataProvider(float beat, float[] bands) {
            this.beat = beat;
            this.bands = bands;
        }

        @Override
        public float beat() {
            beatCalls++;
            return beat;
        }

        @Override
        public float[] bands() {
            bandCalls++;
            return bands;
        }
    }
}
