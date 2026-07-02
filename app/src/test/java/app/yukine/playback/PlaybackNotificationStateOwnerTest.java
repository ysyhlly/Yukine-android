package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

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
                    events.add("queueState");
                    return new PlaybackQueueManager.QueueStateSnapshot(track, 0, 2);
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

        assertFalse(owner.isQueueEmpty());
        assertTrue(owner.isPlaying());
        assertFalse(owner.isPreparing());
        assertSame(track, owner.currentTrack());
        assertTrue(owner.isFavorite(track));
        assertNull(owner.playbackSessionPlatformToken());

        assertEquals(
                java.util.Arrays.asList(
                        "queueState",
                        "playing",
                        "preparing",
                        "queueState",
                        "favorite:7",
                        "token"
                ),
                events
        );
    }

    @Test
    public void playbackStateProviderFromPlaybackStateDelegatesPlaybackSuppliers() {
        List<String> events = new ArrayList<>();
        PlaybackNotificationStateOwner.PlaybackStateProvider provider =
                PlaybackNotificationStateOwner.playbackStateProviderFromPlaybackState(
                        () -> {
                            events.add("playing");
                            return true;
                        },
                        () -> {
                            events.add("preparing");
                            return false;
                        }
                );

        assertTrue(provider.isPlaying());
        assertFalse(provider.isPreparing());
        assertEquals(
                java.util.Arrays.asList(
                        "playing",
                        "preparing"
                ),
                events
        );
    }

    @Test
    public void playbackStateProviderFromPlaybackStateReturnsInactiveForMissingSuppliers() {
        PlaybackNotificationStateOwner.PlaybackStateProvider provider =
                PlaybackNotificationStateOwner.playbackStateProviderFromPlaybackState(null, null);

        assertFalse(provider.isPlaying());
        assertFalse(provider.isPreparing());
    }

    @Test
    public void returnsEmptyQueueStateWhenQueueSuppliersAreMissing() {
        PlaybackNotificationStateOwner owner = new PlaybackNotificationStateOwner(
                null,
                PlaybackNotificationStateOwner.playbackStateProviderFromPlaybackState(null, null),
                track -> false,
                () -> null
        );

        assertTrue(owner.isQueueEmpty());
        assertNull(owner.currentTrack());
    }
}
