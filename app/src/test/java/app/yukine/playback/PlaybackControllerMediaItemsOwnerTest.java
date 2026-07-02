package app.yukine.playback;

import android.net.Uri;

import androidx.media3.common.MediaItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaLibraryCallback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackControllerMediaItemsOwnerTest {
    @Test
    public void playsResolvedControllerQueue() {
        List<String> events = new ArrayList<>();
        List<MediaItem> mediaItems = Collections.singletonList(MediaItem.fromUri("content://track/1"));
        List<Track> tracks = Arrays.asList(track(1L), track(2L));
        PlaybackControllerMediaItemsOwner owner = new PlaybackControllerMediaItemsOwner(
                (requestedMediaItems, startIndex, startPositionMs) -> {
                    assertSame(mediaItems, requestedMediaItems);
                    events.add("resolve:" + startIndex + ":" + startPositionMs);
                    return new PlaybackMediaLibraryCallback.ControllerQueue(tracks, 1, 2500L);
                },
                (queueTracks, startIndex, startPositionMs) -> {
                    assertSame(tracks, queueTracks);
                    events.add("playQueue:" + queueTracks.size() + ":" + startIndex + ":" + startPositionMs);
                }
        );

        assertTrue(owner.setControllerMediaItems(mediaItems, 3, 4000L));

        org.junit.Assert.assertEquals(
                Arrays.asList(
                        "resolve:3:4000",
                        "playQueue:2:1:2500"
                ),
                events
        );
    }

    @Test
    public void returnsFalseWhenControllerQueueCannotResolve() {
        List<String> events = new ArrayList<>();
        PlaybackControllerMediaItemsOwner owner = new PlaybackControllerMediaItemsOwner(
                (mediaItems, startIndex, startPositionMs) -> {
                    events.add("resolve");
                    return null;
                },
                (tracks, startIndex, startPositionMs) -> events.add("playQueue")
        );

        assertFalse(owner.setControllerMediaItems(Collections.singletonList(MediaItem.EMPTY), 0, 0L));

        org.junit.Assert.assertEquals(Collections.singletonList("resolve"), events);
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }
}
