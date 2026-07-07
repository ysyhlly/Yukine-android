package app.yukine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.PlaybackQueueState;
import app.yukine.model.PlaylistImportResult;
import app.yukine.model.StreamImportResult;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;
import app.yukine.common.StreamingDataPathParser;

@RunWith(AndroidJUnit4.class)
public final class MusicLibraryRepositoryInstrumentedTest {
    private static final String DATABASE_NAME = "echo_next.db";

    private Context context;
    private MusicLibraryRepository repository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
        repository = new MusicLibraryRepository(context, new FakeStreamingDataPathParser());
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.deleteDatabase(DATABASE_NAME);
        }
    }

    @Test
    public void importM3uTextReportsAddedAndDuplicateStreams() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,电台一\n"
                + "https://example.com/live/a.mp3\n"
                + "#EXTINF:-1,电台二\n"
                + "https://example.com/live/b.mp3\n";

        StreamImportResult first = repository.importM3uTextWithResult(playlist);
        StreamImportResult second = repository.importM3uTextWithResult(playlist);

        assertEquals(2, first.candidateCount);
        assertEquals(2, first.addedCount);
        assertEquals(0, first.duplicateCount);
        assertEquals(2, second.candidateCount);
        assertEquals(0, second.addedCount);
        assertEquals(2, second.duplicateCount);
        assertEquals(2, repository.loadCachedTracks().size());
    }

    @Test
    public void importM3uTextDeduplicatesMixedCaseStreamUrls() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,电台一\n"
                + "HTTPS://EXAMPLE.com:443/live/a.mp3\n"
                + "#EXTINF:-1,重复电台\n"
                + "https://example.com/live/a.mp3\n";

        StreamImportResult result = repository.importM3uTextWithResult(playlist);

        assertEquals(1, result.candidateCount);
        assertEquals(1, result.addedCount);
        assertEquals(0, result.duplicateCount);
        assertEquals(1, repository.loadCachedTracks().size());
        assertEquals("stream:https://example.com/live/a.mp3", repository.loadCachedTracks().get(0).dataPath);
    }

    @Test
    public void addStreamUrlUpdatesExistingNormalizedUrl() {
        Track first = repository.addStreamUrl("旧名称", "HTTPS://EXAMPLE.com:443/live/a.mp3");
        Track second = repository.addStreamUrl("新名称", "https://example.com/live/a.mp3");

        assertTrue(repository.streamUrlExists("https://example.com/live/a.mp3"));
        assertEquals(first.id, second.id);
        assertEquals("stream:https://example.com/live/a.mp3", second.dataPath);

        List<Track> tracks = repository.loadCachedTracks();
        assertEquals(1, tracks.size());
        assertEquals("新名称", tracks.get(0).title);
        assertEquals("stream:https://example.com/live/a.mp3", tracks.get(0).dataPath);
    }

    @Test
    public void updateStreamUrlMigratesFavoritesHistoryPlaylistsAndQueue() {
        Track oldTrack = repository.addStreamUrl("旧电台", "https://example.com/live/old.mp3");
        long playlistId = repository.createPlaylist("流媒体歌单");
        repository.setFavorite(oldTrack.id, true);
        repository.markPlayed(oldTrack.id);
        repository.markPlayed(oldTrack.id);
        repository.addTrackToPlaylist(playlistId, oldTrack.id);
        ArrayList<Track> queue = new ArrayList<>();
        queue.add(oldTrack);
        repository.savePlaybackQueue(queue, 0);
        repository.savePlaybackPosition(oldTrack.id, 12000L);

        Track updated = repository.updateStreamUrl(oldTrack.id, "新电台", "https://example.com/live/new.mp3");

        assertTrue(updated.id != oldTrack.id);
        assertEquals("新电台", updated.title);
        assertEquals("stream:https://example.com/live/new.mp3", updated.dataPath);
        assertTrue(repository.isFavorite(updated.id));
        assertEquals(updated.id, repository.loadPlaybackPositionTrackId());
        assertEquals(12000L, repository.loadPlaybackPositionMs());

        List<TrackPlayRecord> recent = repository.loadRecentlyPlayed(10);
        assertEquals(1, recent.size());
        assertEquals(updated.id, recent.get(0).track.id);
        assertEquals(2, recent.get(0).playCount);

        List<Track> playlistTracks = repository.loadPlaylistTracks(playlistId);
        assertEquals(1, playlistTracks.size());
        assertEquals(updated.id, playlistTracks.get(0).id);

        PlaybackQueueState queueState = repository.loadPlaybackQueue();
        assertEquals(1, queueState.tracks.size());
        assertEquals(updated.id, queueState.tracks.get(0).id);
        assertEquals(0, queueState.currentIndex);
        assertEquals(1, repository.loadCachedTracks().size());
    }

    @Test
    public void updateStreamUrlMergesReferencesIntoExistingTargetStream() {
        Track oldTrack = repository.addStreamUrl("旧电台", "https://example.com/live/old.mp3");
        Track targetTrack = repository.addStreamUrl("目标电台", "https://example.com/live/target.mp3");
        long playlistId = repository.createPlaylist("合并歌单");
        repository.setFavorite(oldTrack.id, true);
        repository.markPlayed(oldTrack.id);
        repository.markPlayed(oldTrack.id);
        repository.markPlayed(targetTrack.id);
        repository.addTrackToPlaylist(playlistId, oldTrack.id);
        repository.addTrackToPlaylist(playlistId, targetTrack.id);
        ArrayList<Track> queue = new ArrayList<>();
        queue.add(targetTrack);
        queue.add(oldTrack);
        repository.savePlaybackQueue(queue, 1);
        repository.savePlaybackPosition(oldTrack.id, 34000L);

        Track updated = repository.updateStreamUrl(oldTrack.id, "合并电台", "https://example.com/live/target.mp3");

        assertEquals(targetTrack.id, updated.id);
        assertEquals("stream:https://example.com/live/target.mp3", updated.dataPath);
        assertTrue(repository.isFavorite(targetTrack.id));
        assertEquals(targetTrack.id, repository.loadPlaybackPositionTrackId());
        assertEquals(34000L, repository.loadPlaybackPositionMs());

        List<TrackPlayRecord> recent = repository.loadRecentlyPlayed(10);
        assertEquals(1, recent.size());
        assertEquals(targetTrack.id, recent.get(0).track.id);
        assertEquals(3, recent.get(0).playCount);

        List<Track> playlistTracks = repository.loadPlaylistTracks(playlistId);
        assertEquals(1, playlistTracks.size());
        assertEquals(targetTrack.id, playlistTracks.get(0).id);

        PlaybackQueueState queueState = repository.loadPlaybackQueue();
        assertEquals(1, queueState.tracks.size());
        assertEquals(targetTrack.id, queueState.tracks.get(0).id);
        assertEquals(0, queueState.currentIndex);

        List<Track> tracks = repository.loadCachedTracks();
        assertEquals(1, tracks.size());
        assertEquals(targetTrack.id, tracks.get(0).id);
        assertEquals("合并电台", tracks.get(0).title);
    }

    @Test
    public void updateStreamUrlMergeKeepsUnselectedQueueIndex() {
        Track oldTrack = repository.addStreamUrl("Old Stream", "https://example.com/live/old-index.mp3");
        Track targetTrack = repository.addStreamUrl("Target Stream", "https://example.com/live/target-index.mp3");
        ArrayList<Track> queue = new ArrayList<>();
        queue.add(oldTrack);
        queue.add(targetTrack);
        repository.savePlaybackQueue(queue, -1);

        Track updated = repository.updateStreamUrl(oldTrack.id, "Merged Stream", "https://example.com/live/target-index.mp3");

        assertEquals(targetTrack.id, updated.id);
        PlaybackQueueState queueState = repository.loadPlaybackQueue();
        assertEquals(1, queueState.tracks.size());
        assertEquals(targetTrack.id, queueState.tracks.get(0).id);
        assertEquals(-1, queueState.currentIndex);
    }

    @Test
    public void importM3uTextAsPlaylistMergesLocalAndStreamTracks() {
        EchoDatabaseHelper database = new EchoDatabaseHelper(context);
        ArrayList<Track> localTracks = new ArrayList<>();
        localTracks.add(new Track(
                9001L,
                "本地曲目",
                "本地艺人",
                "本地专辑",
                1000L,
                Uri.parse("content://test/local-track"),
                "/storage/emulated/0/Music/local-track.mp3",
                0L,
                null
        ));
        database.upsertTracks(localTracks);
        database.close();

        String playlist = "#EXTM3U\n"
                + "#PLAYLIST:导入歌单\n"
                + "#EXTINF:1,本地曲目\n"
                + "content://test/local-track\n"
                + "#EXTINF:-1,网络曲目\n"
                + "https://example.com/live/imported.mp3\n";

        PlaylistImportResult first = repository.importM3uTextAsPlaylist(playlist, "Fallback");
        PlaylistImportResult second = repository.importM3uTextAsPlaylist(playlist, "Fallback");

        assertEquals("导入歌单", first.playlistName);
        assertTrue(first.playlistId > 0L);
        assertEquals(2, first.candidateCount);
        assertEquals(1, first.streamAddedCount);
        assertEquals(2, first.playlistAddedCount);
        assertEquals(0, first.duplicateCount);
        assertEquals(first.playlistId, second.playlistId);
        assertEquals(0, second.streamAddedCount);
        assertEquals(0, second.playlistAddedCount);
        assertEquals(2, second.duplicateCount);

        List<Track> playlistTracks = repository.loadPlaylistTracks(first.playlistId);
        assertEquals(2, playlistTracks.size());
        assertEquals("本地曲目", playlistTracks.get(0).title);
        assertEquals("网络曲目", playlistTracks.get(1).title);
        assertEquals(2, repository.loadCachedTracks().size());
    }


    private static final class FakeStreamingDataPathParser implements StreamingDataPathParser {
        @Override
        public boolean isStreamingTrack(String dataPath) {
            return dataPath != null && dataPath.startsWith("streaming:");
        }

        @Override
        public String providerName(String dataPath) {
            return null;
        }

        @Override
        public String providerTrackId(String dataPath) {
            return "";
        }
    }
}
