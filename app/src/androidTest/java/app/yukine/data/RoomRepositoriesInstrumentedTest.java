package app.yukine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import app.yukine.data.room.YukineDatabase;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;

@RunWith(AndroidJUnit4.class)
public final class RoomRepositoriesInstrumentedTest {
    private static final String DATABASE_NAME = "room_repositories_instrumented_test.db";

    private Context context;
    private YukineDatabase database;
    private LibraryRepository library;
    private PlaybackPersistenceRepository playback;
    private SettingsRepository settings;
    private HistoryRepository history;
    private PlaylistRepository playlists;
    private RemoteSourceRepository remoteSources;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
        database = YukineDatabase.open(context, DATABASE_NAME);
        library = new LibraryRepository(database);
        playback = new PlaybackPersistenceRepository(database);
        settings = new SettingsRepository(database.settingsDao());
        history = new HistoryRepository(database.historyDao());
        playlists = new PlaylistRepository(database);
        remoteSources = new RemoteSourceRepository(database, library);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
        if (context != null) {
            context.deleteDatabase(DATABASE_NAME);
        }
    }

    @Test
    public void queueSettingsHistoryAndDeleteReferencesPersistTogether() {
        Track first = track(1L, "/music/first.flac");
        Track deleted = track(2L, "/music/deleted.flac");
        Track last = track(3L, "/music/last.flac");
        library.upsertTracks(Arrays.asList(first, deleted, last));
        library.setFavorite(deleted.id, true);
        history.markPlayed(deleted.id);
        long playlistId = playlists.create("Device");
        assertTrue(playlists.addTrack(playlistId, deleted.id));
        playback.saveQueue(Arrays.asList(first, deleted, last), 2);
        playback.savePosition(deleted.id, 12_345L);
        settings.saveAppVolume(0.65f);
        settings.saveLyricsOffsetMs(-700L);

        assertEquals(1, library.deleteTrack(deleted.id));

        assertNull(database.libraryDao().loadTrack(deleted.id));
        assertFalse(library.isFavorite(deleted.id));
        assertTrue(history.loadRecentlyPlayed(10).isEmpty());
        assertTrue(playlists.loadTracks(playlistId).isEmpty());
        assertEquals(Arrays.asList(first.id, last.id), ids(playback.loadQueue()));
        assertEquals(1, playback.loadQueueIndex());
        assertEquals(-1L, playback.loadPositionTrackId());
        assertEquals(0.65f, settings.loadAppVolume(), 0.0001f);
        assertEquals(-700L, settings.loadLyricsOffsetMs());
    }

    @Test
    public void scanReplacementPreservesDocumentStreamAndWebDavRows() {
        Track local = track(10L, "/music/local.flac");
        Track document = track(11L, "document:tree/file");
        Track stream = track(12L, "stream:https://example.test/live");
        Track webdav = track(13L, "webdav:8:/music/remote.flac");
        Track replacement = track(14L, "/music/replacement.flac");
        library.upsertTracks(Arrays.asList(local, document, stream, webdav));

        library.replaceScanManagedTracks(List.of(replacement));

        assertEquals(
                new java.util.HashSet<>(Arrays.asList(11L, 12L, 13L, 14L)),
                new java.util.HashSet<>(ids(library.loadTracks()))
        );
    }

    @Test
    public void remoteSourceEncryptsPasswordAtRestAndOwnsOnlyItsCachedTracks() {
        RemoteSource source = new RemoteSource(
                0L,
                RemoteSource.TYPE_WEBDAV,
                "Test DAV",
                "https://dav.example.com",
                "alice",
                "dav-secret",
                "/music",
                "",
                0L
        );
        long sourceId = remoteSources.save(source);
        Track cached = track(20L, "webdav:" + sourceId + ":/cached.flac");
        remoteSources.replaceTracks(sourceId, List.of(cached));

        String rawPassword = database.remoteSourceDao().loadSource(sourceId).getPassword();
        RemoteSource loaded = remoteSources.loadSource(sourceId);

        assertTrue(sourceId > 0L);
        assertNotNull(rawPassword);
        assertNotEquals("dav-secret", rawPassword);
        assertNotNull(loaded);
        assertEquals("dav-secret", loaded.password);
        assertEquals(List.of(cached.id), ids(remoteSources.loadTracks(sourceId)));

        remoteSources.delete(sourceId);
        assertNull(remoteSources.loadSource(sourceId));
        assertTrue(remoteSources.loadTracks(sourceId).isEmpty());
    }

    private static Track track(long id, String dataPath) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                120_000L,
                Uri.parse("content://media/" + id),
                dataPath,
                id,
                null
        );
    }

    private static List<Long> ids(List<Track> tracks) {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        for (Track track : tracks) {
            ids.add(track.id);
        }
        return ids;
    }
}
