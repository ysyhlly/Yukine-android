package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.media3.common.Player;

import org.junit.Test;

public class EchoPlaybackServiceTest {
    @Test
    public void appListRepeatLoopsMedia3Playlist() {
        assertEquals(
                Player.REPEAT_MODE_ALL,
                EchoPlaybackService.media3RepeatModeForAppRepeatMode(EchoPlaybackService.REPEAT_ALL)
        );
    }

    @Test
    public void appListRepeatDoesNotLoopSingleMedia3ItemWhenQueueIsNotMirrored() {
        assertEquals(
                Player.REPEAT_MODE_OFF,
                EchoPlaybackService.media3RepeatModeForAppRepeatMode(EchoPlaybackService.REPEAT_ALL, false)
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
    public void media3RepeatModesMapBackToAppRepeatModes() {
        assertEquals(
                EchoPlaybackService.REPEAT_ALL,
                EchoPlaybackService.appRepeatModeForMedia3RepeatMode(Player.REPEAT_MODE_ALL)
        );
        assertEquals(
                EchoPlaybackService.REPEAT_ONE,
                EchoPlaybackService.appRepeatModeForMedia3RepeatMode(Player.REPEAT_MODE_ONE)
        );
        assertEquals(
                EchoPlaybackService.REPEAT_OFF,
                EchoPlaybackService.appRepeatModeForMedia3RepeatMode(Player.REPEAT_MODE_OFF)
        );
    }

    @Test
    public void streamingCacheKeyIncludesResolvedUrl() {
        assertNotEquals(
                EchoPlaybackService.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/a.flac"),
                EchoPlaybackService.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/b.flac")
        );
    }
}
