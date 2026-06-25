package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;

import org.junit.Test;

public class EchoPlaybackServiceTest {
    @Test
    public void appListRepeatDoesNotLoopSinglePlayerItem() {
        assertEquals(
                Player.REPEAT_MODE_OFF,
                EchoPlaybackService.media3RepeatModeForAppRepeatMode(EchoPlaybackService.REPEAT_ALL)
        );
    }

    @Test
    public void appRepeatOffDoesNotLoopSinglePlayerItem() {
        assertEquals(
                Player.REPEAT_MODE_OFF,
                EchoPlaybackService.media3RepeatModeForAppRepeatMode(EchoPlaybackService.REPEAT_OFF)
        );
    }

    @Test
    public void appRepeatOneLoopsSinglePlayerItem() {
        assertEquals(
                Player.REPEAT_MODE_ONE,
                EchoPlaybackService.media3RepeatModeForAppRepeatMode(EchoPlaybackService.REPEAT_ONE)
        );
    }

    @Test
    public void automaticMediaItemTransitionIsDetectedForRepeatOffStop() {
        assertEquals(
                true,
                EchoPlaybackService.isAutomaticMediaItemAdvance(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        );
        assertEquals(
                false,
                EchoPlaybackService.isAutomaticMediaItemAdvance(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        );
        assertEquals(
                false,
                EchoPlaybackService.isAutomaticMediaItemAdvance(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        );
    }

    @Test
    public void streamingCacheKeyIncludesResolvedUrl() {
        assertNotEquals(
                EchoPlaybackService.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/a.flac"),
                EchoPlaybackService.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/b.flac")
        );
    }

    @Test
    public void mirroredQueueReuseRequiresResolvedUriToMatch() {
        assertFalse(EchoPlaybackService.mediaItemIdentityMatchesForReuse(
                "42",
                "https://audio.example/first.flac",
                "streaming:netease:42|url=https://audio.example/first.flac",
                42L,
                "https://audio.example/second.flac",
                "streaming:netease:42|url=https://audio.example/second.flac"
        ));
    }

    @Test
    public void mirroredQueueReuseAllowsSameResolvedMediaItem() {
        assertTrue(EchoPlaybackService.mediaItemIdentityMatchesForReuse(
                "42",
                "https://audio.example/current.flac",
                "streaming:netease:42|url=https://audio.example/current.flac",
                42L,
                "https://audio.example/current.flac",
                "streaming:netease:42|url=https://audio.example/current.flac"
        ));
    }

    @Test
    public void contentRangeTotalBytesAreParsedForSegmentedPrecacheProbe() {
        assertEquals(1234567L, EchoPlaybackService.totalBytesFromContentRange("bytes 524288-524288/1234567"));
        assertEquals(-1L, EchoPlaybackService.totalBytesFromContentRange("bytes 0-0/*"));
        assertEquals(-1L, EchoPlaybackService.totalBytesFromContentRange(""));
    }

    @Test
    public void segmentedPrecachePlanUsesProbedContentLengthWhenMetadataIsMissing() {
        java.util.List<EchoPlaybackService.PrecacheSegment> segments =
                EchoPlaybackService.planPrecacheSegments(
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
        assertTrue(EchoPlaybackService.planPrecacheSegments(
                512L,
                256L,
                1536L,
                500L
        ).isEmpty());
    }

    @Test
    public void segmentedPrecachePlanStartsAfterLeadingCacheAndUsesFixedChunks() {
        java.util.List<EchoPlaybackService.PrecacheSegment> segments =
                EchoPlaybackService.planPrecacheSegments(
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
        assertEquals(512L, EchoPlaybackService.segmentedPrecacheStart(512L, 0L));
        assertEquals(2048L, EchoPlaybackService.segmentedPrecacheStart(512L, 2048L));
        assertEquals(512L, EchoPlaybackService.segmentedPrecacheStart(512L, -1L));
    }

    @Test
    public void segmentedPrecachePlanCanStartAfterCurrentBufferedRange() {
        java.util.List<EchoPlaybackService.PrecacheSegment> segments =
                EchoPlaybackService.planPrecacheSegments(
                        EchoPlaybackService.segmentedPrecacheStart(512L, 2048L),
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
