package app.yukine.playback;

import android.net.FakeUri;
import android.net.Uri;

import androidx.media3.exoplayer.source.MediaSource;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.playback.manager.PlaybackPositionManager;
import app.yukine.playback.manager.PlaybackQueueManager;
import app.yukine.playback.manager.PlaybackQueueStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PlaybackCurrentTrackPreparationQueueOwnerTest {
    @Test
    public void queuePreparationReadsQueueStateAfterCurrentReplacement() {
        FakeQueueStore store = new FakeQueueStore();
        PlaybackQueueManager[] queueManagerRef = new PlaybackQueueManager[1];
        PlaybackPositionManager positionManager = new PlaybackPositionManager(
                store,
                new PlaybackPositionManager.StateProvider() {
                    @Override
                    public Track currentTrack() {
                        return queueManagerRef[0] == null
                                ? null
                                : queueManagerRef[0].queueStateSnapshot().getCurrentTrack();
                    }

                    @Override
                    public long positionMs() {
                        return 0L;
                    }
                }
        );
        PlaybackQueueManager queueManager = queueManager(store, positionManager);
        queueManagerRef[0] = queueManager;
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> Collections.singletonList(null)
                );
        Track currentTrack = track(7L);
        Track replacement = track(7L);
        Track nextTrack = track(8L);
        queueManager.playQueue(Arrays.asList(currentTrack, nextTrack), 0, 3200L);

        queueManager.replaceCurrentQueueTrack(replacement);
        long positionMs = positionManager.restoredPositionFor(replacement);
        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertEquals(3200L, positionMs);
        assertSame(replacement, queuePreparation.currentTrack());
        assertEquals(0, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
        assertEquals(3200L, positionManager.restoredPositionFor(replacement));
        assertEquals(1, store.savePlaybackPositionCalls);
    }

    @Test
    public void constructorRequiresQueueManager() {
        try {
            new PlaybackCurrentTrackPreparationQueueOwner(
                    null,
                    tracks -> {
                        throw new AssertionError("should not be called");
                    }
            );
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError("Expected NullPointerException");
    }

    @Test
    public void queuePreparationMapsMirroredQueueThroughMediaSourceBoundary() {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        Track first = playableTrack(21L);
        Track second = playableTrack(22L);
        queueManager.playQueue(Arrays.asList(first, second), 1, -1L);
        List<List<Track>> requestedTracks = new ArrayList<>();
        List<MediaSource> resolvedSources = Collections.emptyList();
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> {
                            requestedTracks.add(new ArrayList<>(tracks));
                            return resolvedSources;
                        }
                );

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertSame(second, queuePreparation.currentTrack());
        assertEquals(1, queuePreparation.startIndex());
        assertSame(resolvedSources, queuePreparation.mirroredQueueMediaSources());
        assertEquals(1, requestedTracks.size());
        assertEquals(Arrays.asList(first, second), requestedTracks.get(0));
    }

    @Test
    public void queuePreparationUsesPreparedQueueTracksBeforeResolvingMediaSources() {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        Track first = playableTrack(61L);
        Track second = playableTrack(62L);
        Track restoredSecond = playableTrack(620L);
        List<String> preparedTrackDataPaths = new ArrayList<>();
        List<List<Track>> requestedTracks = new ArrayList<>();
        queueManager.playQueue(Arrays.asList(first, second), 0, -1L);
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        track -> {
                            preparedTrackDataPaths.add(track.dataPath);
                            return track.id == second.id ? restoredSecond : track;
                        },
                        tracks -> {
                            requestedTracks.add(new ArrayList<>(tracks));
                            return Collections.emptyList();
                        }
                );

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertSame(first, queuePreparation.currentTrack());
        assertEquals(0, queuePreparation.startIndex());
        assertEquals(Arrays.asList("/music/61", "/music/62"), preparedTrackDataPaths);
        assertEquals(1, requestedTracks.size());
        assertEquals(Arrays.asList(61L, 620L), trackIds(requestedTracks.get(0)));
    }

    @Test
    public void queuePreparationSkipsMirroredSourcesWhenAnyQueuedTrackLacksPlayableUri() {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        Track current = playableTrack(31L);
        Track missingUri = trackWithoutPlayableUri(32L);
        int[] mediaSourceRequests = new int[] {0};
        queueManager.playQueue(Arrays.asList(current, missingUri), 0, -1L);
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> {
                            mediaSourceRequests[0]++;
                            return Collections.singletonList(null);
                        }
                );

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertSame(current, queuePreparation.currentTrack());
        assertEquals(0, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
        assertEquals(0, mediaSourceRequests[0]);
    }

    @Test
    public void queuePreparationSkipsMirroredSourcesWhenQueuedTrackIsNotRestorable() {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        Track current = playableTrack(71L);
        Track missingFile = new Track(
                72L,
                "Track 72",
                "Artist",
                "Album",
                10000L,
                Uri.parse("file:///definitely/missing-72.flac"),
                "/definitely/missing-72.flac"
        );
        int[] mediaSourceRequests = new int[] {0};
        queueManager.playQueue(Arrays.asList(current, missingFile), 0, -1L);
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> {
                            mediaSourceRequests[0]++;
                            return Collections.singletonList(null);
                        }
                );

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertSame(current, queuePreparation.currentTrack());
        assertEquals(0, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
        assertEquals(0, mediaSourceRequests[0]);
    }

    @Test
    public void queuePreparationPreservesCurrentTrackWhenMediaSourceBoundaryReturnsNull() {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        Track first = playableTrack(41L);
        Track second = playableTrack(42L);
        List<List<Track>> requestedTracks = new ArrayList<>();
        queueManager.playQueue(Arrays.asList(first, second), 1, -1L);
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> {
                            requestedTracks.add(new ArrayList<>(tracks));
                            return null;
                        }
                );

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertSame(second, queuePreparation.currentTrack());
        assertEquals(1, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
        assertEquals(1, requestedTracks.size());
        assertEquals(Arrays.asList(first, second), requestedTracks.get(0));
    }

    @Test
    public void queuePreparationPreservesCurrentTrackWhenMediaSourceBoundaryIsMissing() {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        Track first = playableTrack(51L);
        Track second = playableTrack(52L);
        queueManager.playQueue(Arrays.asList(first, second), 1, -1L);
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(queueManager, null);

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertSame(second, queuePreparation.currentTrack());
        assertEquals(1, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
    }

    @Test
    public void queuePreparationIsEmptyWithoutCurrentTrack() throws Exception {
        PlaybackQueueManager queueManager = queueManager(new FakeQueueStore(), null);
        queueManager.playQueue(Collections.singletonList(playableTrack(81L)), 0, -1L);
        setRawCurrentIndex(queueManager, 3);
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager,
                        tracks -> Collections.singletonList(null)
                );

        PlaybackCurrentTrackPreparationQueueOwner.PreparedQueue queuePreparation =
                owner.queuePreparationForNewPlayer();

        assertEquals(null, queuePreparation.currentTrack());
        assertEquals(0, queuePreparation.startIndex());
        assertEquals(null, queuePreparation.mirroredQueueMediaSources());
    }

    @Test
    public void mediaSourceProviderConstructorFallsBackWhenProviderIsMissing() {
        PlaybackCurrentTrackPreparationQueueOwner owner =
                new PlaybackCurrentTrackPreparationQueueOwner(
                        queueManager(new FakeQueueStore(), null),
                        (PlaybackMediaSourceProvider) null,
                        track -> null
                );

        assertEquals(null, owner.queuePreparationForNewPlayer().mirroredQueueMediaSources());
    }

    private static PlaybackQueueManager queueManager(
            FakeQueueStore store,
            PlaybackPositionManager positionManager
    ) {
        return new PlaybackQueueManager(
                store,
                new ArrayList<>(),
                new NoopQueuePlaybackActions(),
                positionManager,
                new NoopStreamingRestoreProvider(),
                new NoopMirroredQueuePlayer(),
                null,
                null,
                new Random(1L)
        );
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                10000L,
                Uri.parse("content://test/" + id),
                "/music/" + id
        );
    }

    private static Track playableTrack(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                10000L,
                new FakeUri("content://test/" + id),
                "/music/" + id
        );
    }

    private static Track trackWithoutPlayableUri(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                10000L,
                Uri.EMPTY,
                "/music/" + id
        );
    }

    private static List<Long> trackIds(List<Track> tracks) {
        List<Long> ids = new ArrayList<>();
        for (Track track : tracks) {
            ids.add(track.id);
        }
        return ids;
    }

    private static void setRawCurrentIndex(PlaybackQueueManager queueManager, int currentIndex)
            throws Exception {
        Field field = PlaybackQueueManager.class.getDeclaredField("currentIndex");
        field.setAccessible(true);
        field.setInt(queueManager, currentIndex);
    }

    private static final class FakeQueueStore implements PlaybackQueueStore {
        private int savePlaybackPositionCalls;

        @Override
        public PlaybackQueueState load() {
            return new PlaybackQueueState(Collections.emptyList(), -1);
        }

        @Override
        public void save(List<Track> tracks, int currentIndex) {
        }

        @Override
        public boolean loadResumeRequested() {
            return false;
        }

        @Override
        public void saveResumeRequested(boolean requested) {
        }

        @Override
        public boolean loadPlaybackRestoreEnabled() {
            return true;
        }

        @Override
        public void savePlaybackRestoreEnabled(boolean enabled) {
        }

        @Override
        public long loadPlaybackPositionTrackId() {
            return -1L;
        }

        @Override
        public long loadPlaybackPositionMs() {
            return 0L;
        }

        @Override
        public void savePlaybackPosition(long trackId, long positionMs) {
            savePlaybackPositionCalls++;
        }
    }

    private static final class NoopQueuePlaybackActions
            implements PlaybackQueueManager.QueuePlaybackActions {
        @Override
        public void prepareCurrent(boolean playWhenReady) {
        }

        @Override
        public void publishState() {
        }
    }

    private static final class NoopStreamingRestoreProvider
            implements PlaybackQueueManager.StreamingRestoreProvider {
        @Override
        public Track restoreTrackForPlayback(Track track) {
            return track;
        }
    }

    private static final class NoopMirroredQueuePlayer
            implements PlaybackQueueManager.MirroredQueuePlayer {
        @Override
        public boolean matchesCurrentQueue() {
            return false;
        }

        @Override
        public boolean seekTo(int index, long positionMs, boolean playWhenReady) {
            return false;
        }
    }
}
