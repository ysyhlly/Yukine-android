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

public class PlaybackNotificationStateOwnerTest {
    @Test
    public void delegatesNotificationStateToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = new Track(7L, "Track", "Artist", "Album", 1000L, Uri.EMPTY, "file:7");
        PlaybackNotificationStateOwner owner = new PlaybackNotificationStateOwner(
                () -> {
                    events.add("queueEmpty");
                    return true;
                },
                new PlaybackNotificationStateOwner.PlaybackStateProvider() {
                    @Override
                    public boolean isPlaying() {
                        events.add("playing");
                        return true;
                    }

                    @Override
                    public boolean isPreparing() {
                        events.add("preparing");
                        return false;
                    }

                    @Override
                    public Track currentTrack() {
                        events.add("track");
                        return track;
                    }
                },
                favoriteTrack -> {
                    events.add("favorite:" + (favoriteTrack == null ? -1L : favoriteTrack.id));
                    return favoriteTrack == track;
                },
                () -> {
                    events.add("token");
                    return null;
                }
        );

        assertTrue(owner.isQueueEmpty());
        assertTrue(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertSame(track, owner.currentTrack());
        assertTrue(owner.isFavorite(track));
        assertNull(owner.playbackSessionPlatformToken());

        assertEquals(
                java.util.Arrays.asList(
                        "queueEmpty",
                        "playing",
                        "preparing",
                        "track",
                        "favorite:7",
                        "token"
                ),
                events
        );
    }
}
