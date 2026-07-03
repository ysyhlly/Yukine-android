package app.yukine.playback;

import androidx.media3.common.MediaItem;

import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlaybackPrecacheStateOwnerTest {
    @Test
    public void delegatesPrecacheStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        MediaItem mediaItem = MediaItem.fromUri("https://example.test/one.mp3");
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackPrecacheStateOwner owner = new PlaybackPrecacheStateOwner(
                () -> {
                    events.add("mediaItem");
                    return mediaItem;
                },
                diagnostics
        );

        assertSame(mediaItem, owner.currentPlayerMediaItem());
        assertSame(diagnostics, owner.streamingDiagnostics());
        assertEquals(
                java.util.Collections.singletonList("mediaItem"),
                events
        );
    }

    @Test
    public void returnsNullCurrentPlayerMediaItemWhenSupplierIsMissing() {
        PlaybackPrecacheStateOwner owner = new PlaybackPrecacheStateOwner(
                null,
                new PlaybackStreamingDiagnostics()
        );

        assertNull(owner.currentPlayerMediaItem());
    }

}
