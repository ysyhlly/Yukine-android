package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackLyricsStateOwnerTest {
    @Test
    public void delegatesLyricsStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(13L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:13");
        PlaybackLyricsStateOwner owner = new PlaybackLyricsStateOwner(
                () -> {
                    events.add("visible");
                    return true;
                },
                new PlaybackLyricsStateOwner.PlaybackStateProvider() {
                    @Override
                    public Track currentTrack() {
                        events.add("track");
                        return track;
                    }

                    @Override
                    public boolean isPlaying() {
                        events.add("playing");
                        return false;
                    }

                    @Override
                    public boolean isPreparing() {
                        events.add("preparing");
                        return true;
                    }
                }
        );

        assertTrue(owner.isAppVisible());
        assertSame(track, owner.currentTrack());
        assertFalse(owner.isPlaying());
        assertTrue(owner.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "visible",
                        "track",
                        "playing",
                        "preparing"
                ),
                events
        );
    }

    @Test
    public void playbackStateProviderFromPlaybackStateDelegatesPlaybackSuppliers() {
        List<String> events = new ArrayList<>();
        Track track = new Track(14L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:14");
        PlaybackLyricsStateOwner.PlaybackStateProvider provider =
                PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(
                        () -> {
                            events.add("track");
                            return track;
                        },
                        () -> {
                            events.add("playing");
                            return false;
                        },
                        () -> {
                            events.add("preparing");
                            return true;
                        }
                );

        assertSame(track, provider.currentTrack());
        assertFalse(provider.isPlaying());
        assertTrue(provider.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "track",
                        "playing",
                        "preparing"
                ),
                events
        );
    }

    @Test
    public void playbackStateProviderFromPlaybackStateReturnsInactiveForMissingSuppliers() {
        PlaybackLyricsStateOwner.PlaybackStateProvider provider =
                PlaybackLyricsStateOwner.playbackStateProviderFromPlaybackState(null, null, null);

        assertNull(provider.currentTrack());
        assertFalse(provider.isPlaying());
        assertFalse(provider.isPreparing());
    }
}
