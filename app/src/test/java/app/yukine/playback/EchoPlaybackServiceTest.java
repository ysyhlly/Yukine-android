package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EchoPlaybackServiceTest {
    @Test
    public void segmentedPrecachePlanUsesProbedContentLengthWhenMetadataIsMissing() {
        java.util.List<PlaybackPrecacheManager.PrecacheSegment> segments =
                PlaybackPrecacheManager.planPrecacheSegments(
                        512L,
                        256L,
                        1536L,
                        1200L
                );

        assertEquals(3, segments.size());
        assertEquals(512L, segments.get(0).start);
        assertEquals(256L, segments.get(0).length);
        assertEquals(1024L, segments.get(2).start);
        assertEquals(176L, segments.get(2).length);
    }

    @Test
    public void segmentedPrecachePlanSkipsAudioAlreadyCoveredByLeadingCache() {
        assertTrue(PlaybackPrecacheManager.planPrecacheSegments(
                512L,
                256L,
                1536L,
                500L
        ).isEmpty());
    }

    @Test
    public void segmentedPrecachePlanStartsAfterLeadingCacheAndUsesFixedChunks() {
        java.util.List<PlaybackPrecacheManager.PrecacheSegment> segments =
                PlaybackPrecacheManager.planPrecacheSegments(
                        512L * 1024L,
                        1024L * 1024L,
                        5L * 1024L * 1024L,
                        10L * 1024L * 1024L
                );

        assertEquals(5, segments.size());
        assertEquals(512L * 1024L, segments.get(0).start);
        assertEquals(1024L * 1024L, segments.get(0).length);
        assertEquals(4L * 1024L * 1024L + 512L * 1024L, segments.get(4).start);
        assertEquals(512L * 1024L, segments.get(4).length);
    }

    @Test
    public void segmentedPrecacheStartSkipsLeadingRangeWhenPlayerOrCacheAlreadyFilledIt() {
        assertEquals(512L, PlaybackPrecacheManager.segmentedPrecacheStart(512L, 0L));
        assertEquals(2048L, PlaybackPrecacheManager.segmentedPrecacheStart(512L, 2048L));
        assertEquals(512L, PlaybackPrecacheManager.segmentedPrecacheStart(512L, -1L));
    }

    @Test
    public void segmentedPrecachePlanCanStartAfterCurrentBufferedRange() {
        java.util.List<PlaybackPrecacheManager.PrecacheSegment> segments =
                PlaybackPrecacheManager.planPrecacheSegments(
                        PlaybackPrecacheManager.segmentedPrecacheStart(512L, 2048L),
                        512L,
                        4096L,
                        4096L
                );

        assertEquals(4, segments.size());
        assertEquals(2048L, segments.get(0).start);
        assertEquals(512L, segments.get(0).length);
        assertEquals(3584L, segments.get(3).start);
    }
}
