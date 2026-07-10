package app.yukine.playback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackQueueCommandOwnerTest {
    @Test
    public void delegatesQueueActionsToPlaybackOwners() {
        List<String> events = new ArrayList<>();
        PlaybackQueueCommandOwner owner = new PlaybackQueueCommandOwner(
                playWhenReady -> events.add("prepare:" + playWhenReady),
                () -> events.add("publish"),
                new FakePlaybackCommands(events),
                (tracks, currentIndex) -> {
                    events.add("persist:" + tracks.size() + ":" + currentIndex);
                    return true;
                }
        );

        owner.prepareCurrent(true);
        owner.publishState();
        owner.stopAndClear();
        boolean persisted = owner.persistQueueAsync(
                java.util.Collections.singletonList(track(1L)),
                0
        );

        assertTrue(persisted);
        assertEquals(
                java.util.Arrays.asList(
                        "prepare:true",
                        "publish",
                        "stopAndClear",
                        "persist:1:0"
                ),
                events
        );
    }

    @Test
    public void conflatingQueuePersistenceKeepsOnlyLatestPendingSnapshot() {
        java.util.ArrayDeque<Runnable> scheduled = new java.util.ArrayDeque<>();
        List<String> saves = new ArrayList<>();
        PlaybackQueueCommandOwner.QueuePersistence persistence =
                PlaybackQueueCommandOwner.conflatingQueuePersistence(
                        scheduled::offerLast,
                        (tracks, currentIndex) ->
                                saves.add(tracks.get(0).id + ":" + currentIndex)
                );

        assertTrue(persistence.persist(java.util.Collections.singletonList(track(1L)), 0));
        assertTrue(persistence.persist(java.util.Collections.singletonList(track(2L)), 1));
        assertTrue(persistence.persist(java.util.Collections.singletonList(track(3L)), 2));

        assertEquals(1, scheduled.size());
        scheduled.removeFirst().run();

        assertEquals(java.util.Collections.singletonList("3:2"), saves);
        assertTrue(scheduled.isEmpty());
    }

    @Test
    public void conflatingQueuePersistenceYieldsBeforeSavingUpdatesArrivingDuringWrite() {
        java.util.ArrayDeque<Runnable> scheduled = new java.util.ArrayDeque<>();
        List<String> saves = new ArrayList<>();
        PlaybackQueueCommandOwner.QueuePersistence[] persistence =
                new PlaybackQueueCommandOwner.QueuePersistence[1];
        persistence[0] = PlaybackQueueCommandOwner.conflatingQueuePersistence(
                scheduled::offerLast,
                (tracks, currentIndex) -> {
                    saves.add(tracks.get(0).id + ":" + currentIndex);
                    if (currentIndex == 0) {
                        persistence[0].persist(java.util.Collections.singletonList(track(2L)), 1);
                        persistence[0].persist(java.util.Collections.singletonList(track(3L)), 2);
                    }
                }
        );

        assertTrue(persistence[0].persist(java.util.Collections.singletonList(track(1L)), 0));
        scheduled.removeFirst().run();

        assertEquals(java.util.Collections.singletonList("1:0"), saves);
        assertEquals(1, scheduled.size());

        scheduled.removeFirst().run();

        assertEquals(java.util.Arrays.asList("1:0", "3:2"), saves);
        assertTrue(scheduled.isEmpty());
    }

    @Test
    public void conflatingQueuePersistenceFallsBackWhenSchedulerRejectsWork() {
        PlaybackQueueCommandOwner.QueuePersistence persistence =
                PlaybackQueueCommandOwner.conflatingQueuePersistence(
                        command -> false,
                        (tracks, currentIndex) -> {
                            throw new AssertionError("Rejected work must not run");
                        }
                );

        assertFalse(persistence.persist(java.util.Collections.singletonList(track(1L)), 0));
    }

    @Test
    public void conflatingQueuePersistenceReschedulesNewerSnapshotAfterSaveFailure() {
        java.util.ArrayDeque<Runnable> scheduled = new java.util.ArrayDeque<>();
        List<String> saves = new ArrayList<>();
        PlaybackQueueCommandOwner.QueuePersistence[] persistence =
                new PlaybackQueueCommandOwner.QueuePersistence[1];
        persistence[0] = PlaybackQueueCommandOwner.conflatingQueuePersistence(
                scheduled::offerLast,
                (tracks, currentIndex) -> {
                    if (currentIndex == 0) {
                        persistence[0].persist(java.util.Collections.singletonList(track(2L)), 1);
                        throw new IllegalStateException("simulated write failure");
                    }
                    saves.add(tracks.get(0).id + ":" + currentIndex);
                }
        );

        assertTrue(persistence[0].persist(java.util.Collections.singletonList(track(1L)), 0));
        try {
            scheduled.removeFirst().run();
            throw new AssertionError("Expected simulated write failure");
        } catch (IllegalStateException expected) {
            assertEquals("simulated write failure", expected.getMessage());
        }

        assertEquals(1, scheduled.size());
        scheduled.removeFirst().run();

        assertEquals(java.util.Collections.singletonList("2:1"), saves);
        assertTrue(scheduled.isEmpty());
    }

    private static app.yukine.model.Track track(long id) {
        return new app.yukine.model.Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                android.net.Uri.EMPTY,
                "file:" + id
        );
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
