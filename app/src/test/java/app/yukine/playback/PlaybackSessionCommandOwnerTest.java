package app.yukine.playback;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackSessionCommandOwnerTest {
    @Test
    public void delegatesMediaSessionCommandsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        Track track = track(3L);
        MediaMetadata metadata = new MediaMetadata.Builder().setTitle("Session title").build();
        PlaybackSessionCommandOwner owner = new PlaybackSessionCommandOwner(
                new FakePlaybackCommands(events),
                positionMs -> events.add("seek:" + positionMs),
                repeatMode -> events.add("repeat:" + repeatMode),
                (mediaItems, startIndex, startPositionMs) -> {
                    events.add("controllerItems:" + mediaItems.size() + ":" + startIndex + ":" + startPositionMs);
                    return true;
                },
                new PlaybackSessionCommandOwner.StateProvider() {
                    @Override
                    public Track currentTrack() {
                        return track;
                    }

                    @Override
                    public long positionMs() {
                        return 4200L;
                    }

                    @Override
                    public long sessionPositionMs() {
                        return 4000L;
                    }

                    @Override
                    public long durationMs() {
                        return 9000L;
                    }
                },
                requestedTrack -> {
                    events.add("metadata:" + requestedTrack.id);
                    return metadata;
                }
        );

        owner.play();
        owner.pause();
        owner.seekTo(1200L);
        owner.skipToPrevious();
        owner.skipToNext();
        owner.setRepeatMode(2);
        owner.stopAndClear();
        assertTrue(owner.setControllerMediaItems(Collections.singletonList(MediaItem.fromUri("content://track/3")), 1, 3000L));
        assertSame(track, owner.currentTrack());
        assertSame(metadata, owner.mediaMetadataForTrack(track));
        assertEquals(4200L, owner.positionMs());
        assertEquals(4000L, owner.sessionPositionMs());
        assertEquals(9000L, owner.durationMs());

        assertEquals(
                java.util.Arrays.asList(
                        "play",
                        "pause",
                        "seek:1200",
                        "previous",
                        "next",
                        "repeat:2",
                        "stopAndClear",
                        "controllerItems:1:1:3000",
                        "metadata:3"
                ),
                events
        );
    }

    private static Track track(long id) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, Uri.EMPTY, "file:" + id);
    }

    private static final class FakePlaybackCommands implements PlaybackNotificationCommandOwner.PlaybackCommands {
        private final List<String> events;

        FakePlaybackCommands(List<String> events) {
            this.events = events;
        }

        @Override
        public void play() {
            events.add("play");
        }

        @Override
        public void pause() {
            events.add("pause");
        }

        @Override
        public void skipToPrevious() {
            events.add("previous");
        }

        @Override
        public void skipToNext() {
            events.add("next");
        }

        @Override
        public void toggleCurrentFavorite() {
            events.add("favorite");
        }

        @Override
        public void restoreLastPlayback(boolean playWhenReady) {
            events.add("restore:" + playWhenReady);
        }

        @Override
        public void stopAndClear() {
            events.add("stopAndClear");
        }
    }
}
