package app.yukine.playback;

import android.net.Uri;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaybackCurrentTrackPreparationOwnerTest {
    @Test
    public void replacesRestoredTrackAndReturnsRestoredPosition() {
        List<String> events = new ArrayList<>();
        Track requested = track(1L, Uri.EMPTY);
        Track restored = track(1L, Uri.parse("https://audio.example/restored.flac"));
        PlaybackCurrentTrackPreparationOwner owner = new PlaybackCurrentTrackPreparationOwner(
                track -> {
                    events.add("restore:" + track.id);
                    return preparation(restored, restored, true, null);
                },
                track -> {
                    events.add("source:" + track.id);
                    return null;
                },
                new FakeQueuePreparationController(events, 4500L),
                new FakeRuntimeStateController(events),
                () -> events.add("publish"),
                track -> events.add("refuse:" + track.id)
        );

        PlaybackCurrentTrackPreparationOwner.PreparedTrack prepared = owner.prepareCurrentTrack(requested);

        assertTrue(prepared.playable());
        assertSame(restored, prepared.track());
        assertEquals(4500L, prepared.startPositionMs());
        assertEquals(
                Arrays.asList(
                        "restore:1",
                        "replace:1",
                        "position:1"
                ),
                events
        );
        assertNull(prepared.mediaSource());
        assertEquals(
                Arrays.asList(
                        "restore:1",
                        "replace:1",
                        "position:1",
                        "source:1"
                ),
                events
        );
    }

    @Test
    public void unplayableTrackUpdatesRuntimeStateAndPublishes() {
        List<String> events = new ArrayList<>();
        Track unresolved = track(2L, Uri.EMPTY);
        PlaybackCurrentTrackPreparationOwner owner = new PlaybackCurrentTrackPreparationOwner(
                track -> preparation(
                        unresolved,
                        null,
                        false,
                        "Streaming track is not resolved yet. Tap the track again to play."
                ),
                track -> {
                    events.add("source:" + track.id);
                    return null;
                },
                new FakeQueuePreparationController(events, 4500L),
                new FakeRuntimeStateController(events),
                () -> events.add("publish"),
                track -> events.add("refuse:" + track.id)
        );

        PlaybackCurrentTrackPreparationOwner.PreparedTrack prepared = owner.prepareCurrentTrack(unresolved);

        assertFalse(prepared.playable());
        assertSame(unresolved, prepared.track());
        assertEquals(0L, prepared.startPositionMs());
        assertNull(prepared.mediaSource());
        assertEquals(
                Arrays.asList(
                        "preparing:false",
                        "error:Streaming track is not resolved yet. Tap the track again to play.",
                        "refuse:2",
                        "publish"
                ),
                events
        );
    }

    @Test
    public void restoredPositionIsClampedToZero() {
        Track track = track(3L, Uri.parse("file:///music/local.flac"));
        PlaybackCurrentTrackPreparationOwner owner = new PlaybackCurrentTrackPreparationOwner(
                requested -> preparation(requested, null, true, null),
                requested -> null,
                new FakeQueuePreparationController(new ArrayList<>(), -1L),
                new FakeRuntimeStateController(new ArrayList<>()),
                () -> {
                },
                ignored -> {
                }
        );

        assertEquals(0L, owner.prepareCurrentTrack(track).startPositionMs());
    }

    private static PlaybackMediaSourceProvider.PlaybackPreparation preparation(
            Track track,
            Track restoredTrack,
            boolean playable,
            String unplayableMessage
    ) {
        return new PlaybackMediaSourceProvider.PlaybackPreparation(
                track,
                restoredTrack,
                playable,
                unplayableMessage
        );
    }

    private static Track track(long id, Uri uri) {
        return new Track(id, "Track " + id, "Artist", "Album", 1000L, uri, "streaming:netease:" + id);
    }

    private static final class FakeQueuePreparationController
            implements PlaybackCurrentTrackPreparationOwner.QueuePreparationController {
        private final List<String> events;
        private final long restoredPositionMs;

        FakeQueuePreparationController(List<String> events, long restoredPositionMs) {
            this.events = events;
            this.restoredPositionMs = restoredPositionMs;
        }

        @Override
        public void replaceCurrentQueueTrack(Track track) {
            events.add("replace:" + track.id);
        }

        @Override
        public long restoredPositionFor(Track track) {
            events.add("position:" + track.id);
            return restoredPositionMs;
        }
    }

    private static final class FakeRuntimeStateController
            implements PlaybackCurrentTrackPreparationOwner.RuntimeStateController {
        private final List<String> events;

        FakeRuntimeStateController(List<String> events) {
            this.events = events;
        }

        @Override
        public void setPreparing(boolean preparing) {
            events.add("preparing:" + preparing);
        }

        @Override
        public void setErrorMessage(String message) {
            events.add("error:" + message);
        }
    }
}
