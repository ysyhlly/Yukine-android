package app.yukine.playback;

import android.net.Uri;

import androidx.media3.common.MediaItem;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;
import app.yukine.playback.manager.PlaybackQueueManager;

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
        Track track = track(1L);
        MediaItem mediaItem = MediaItem.fromUri("https://example.test/one.mp3");
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        PlaybackPrecacheStateOwner owner = new PlaybackPrecacheStateOwner(
                () -> {
                    events.add("queueState");
                    return new PlaybackQueueManager.QueueStateSnapshot(track, 0, 1);
                },
                () -> {
                    events.add("mediaItem");
                    return mediaItem;
                },
                () -> {
                    events.add("diagnostics");
                    return diagnostics;
                }
        );

        assertSame(track, owner.currentTrack());
        assertSame(mediaItem, owner.currentPlayerMediaItem());
        assertSame(diagnostics, owner.streamingDiagnostics());
        assertEquals(
                java.util.Arrays.asList(
                        "queueState",
                        "mediaItem",
                        "diagnostics"
                ),
                events
        );
    }

    @Test
    public void returnsNullCurrentTrackWhenQueueStateIsMissing() {
        PlaybackPrecacheStateOwner missingProviderOwner = new PlaybackPrecacheStateOwner(
                null,
                () -> null,
                PlaybackStreamingDiagnostics::new
        );
        PlaybackPrecacheStateOwner nullSnapshotOwner = new PlaybackPrecacheStateOwner(
                () -> null,
                () -> null,
                PlaybackStreamingDiagnostics::new
        );

        assertNull(missingProviderOwner.currentTrack());
        assertNull(nullSnapshotOwner.currentTrack());
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse("https://example.test/" + id + ".mp3"),
                "streaming:test:" + id
        );
    }
}
