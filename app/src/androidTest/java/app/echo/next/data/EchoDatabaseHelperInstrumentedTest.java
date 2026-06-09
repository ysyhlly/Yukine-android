package app.echo.next.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import app.echo.next.model.TrackPlayRecord;
import app.echo.next.model.Track;

@RunWith(AndroidJUnit4.class)
public final class EchoDatabaseHelperInstrumentedTest {
    private static final String DATABASE_NAME = "echo_playlist_order_test.db";

    private Context context;
    private EchoDatabaseHelper database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
        database = new EchoDatabaseHelper(context, DATABASE_NAME);
        database.getWritableDatabase();
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
    public void movePlaylistTrackAtSwapsAdjacentRows() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track(1001L, "OrderTrackA"));
        tracks.add(track(1002L, "OrderTrackB"));
        database.upsertTracks(tracks);

        long playlistId = database.createPlaylist("OrderList");
        assertTrue(playlistId > 0L);
        assertTrue(database.addTrackToPlaylist(playlistId, 1001L));
        assertTrue(database.addTrackToPlaylist(playlistId, 1002L));
        assertOrder(playlistId, "OrderTrackA", "OrderTrackB");

        assertTrue(database.movePlaylistTrackAt(playlistId, 0, 1));
        assertOrder(playlistId, "OrderTrackB", "OrderTrackA");

        assertTrue(database.movePlaylistTrackAt(playlistId, 1, -1));
        assertOrder(playlistId, "OrderTrackA", "OrderTrackB");

        assertFalse(database.movePlaylistTrackAt(playlistId, 0, -1));
        assertFalse(database.movePlaylistTrackAt(playlistId, 1, 1));
        assertOrder(playlistId, "OrderTrackA", "OrderTrackB");
    }

    @Test
    public void appVolumeSettingPersists() {
        assertEquals(1.0f, database.loadAppVolume(), 0.001f);

        database.saveAppVolume(0.7f);

        assertEquals(0.7f, database.loadAppVolume(), 0.001f);
    }

    @Test
    public void lyricsOffsetSettingPersists() {
        assertEquals(0L, database.loadLyricsOffsetMs());

        database.saveLyricsOffsetMs(500L);

        assertEquals(500L, database.loadLyricsOffsetMs());
    }

    @Test
    public void playbackModeSettingsPersist() {
        assertFalse(database.loadShuffleEnabled());
        assertEquals(0, database.loadRepeatMode());

        database.saveShuffleEnabled(true);
        database.saveRepeatMode(2);

        assertTrue(database.loadShuffleEnabled());
        assertEquals(2, database.loadRepeatMode());
    }

    @Test
    public void replaceTracksKeepsDocumentStreamAndWebDavTracks() {
        ArrayList<Track> initial = new ArrayList<>();
        initial.add(track(2001L, "ScannedBefore", "file:/music/before.mp3"));
        initial.add(track(2002L, "DocumentTrack", "document:content://doc/1"));
        initial.add(track(2003L, "StreamTrack", "stream:https://example.com/a.mp3"));
        initial.add(track(2004L, "WebDavTrack", "webdav:7:/music/a.flac"));
        database.upsertTracks(initial);

        ArrayList<Track> replacement = new ArrayList<>();
        replacement.add(track(2005L, "ScannedAfter", "file:/music/after.mp3"));
        database.replaceTracks(replacement);

        List<Track> tracks = database.loadTracks();
        assertFalse(containsTitle(tracks, "ScannedBefore"));
        assertTrue(containsTitle(tracks, "ScannedAfter"));
        assertTrue(containsTitle(tracks, "DocumentTrack"));
        assertTrue(containsTitle(tracks, "StreamTrack"));
        assertTrue(containsTitle(tracks, "WebDavTrack"));
    }

    @Test
    public void playbackQueuePersistsOrderAndCurrentIndex() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track(3001L, "QueueA"));
        tracks.add(track(3002L, "QueueB"));
        database.upsertTracks(tracks);

        database.savePlaybackQueue(tracks, 1);

        List<Track> queue = database.loadPlaybackQueueTracks();
        assertEquals(2, queue.size());
        assertEquals("QueueA", queue.get(0).title);
        assertEquals("QueueB", queue.get(1).title);
        assertEquals(1, database.loadPlaybackQueueIndex());
    }

    @Test
    public void playbackQueuePersistsStreamingTrackWithoutLibraryRow() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(new Track(
                3101L,
                "StreamingQueueA",
                "StreamingArtist",
                "StreamingAlbum",
                123000L,
                Uri.parse("https://example.com/audio/stream-a.mp3"),
                "streaming:netease:3101",
                -1L,
                Uri.parse("https://example.com/cover-a.jpg")
        ));

        database.savePlaybackQueue(tracks, 0);

        List<Track> queue = database.loadPlaybackQueueTracks();
        assertEquals(1, queue.size());
        assertEquals("StreamingQueueA", queue.get(0).title);
        assertEquals("StreamingArtist", queue.get(0).artist);
        assertEquals("streaming:netease:3101", queue.get(0).dataPath);
        assertEquals("https://example.com/audio/stream-a.mp3", queue.get(0).contentUri.toString());
        assertEquals("https://example.com/cover-a.jpg", queue.get(0).albumArtUriString());
    }

    @Test
    public void playbackPositionPersistsTrackAndPosition() {
        assertEquals(-1L, database.loadPlaybackPositionTrackId());
        assertEquals(0L, database.loadPlaybackPositionMs());

        database.savePlaybackPosition(3002L, 45000L);

        assertEquals(3002L, database.loadPlaybackPositionTrackId());
        assertEquals(45000L, database.loadPlaybackPositionMs());

        database.savePlaybackPosition(-1L, -100L);

        assertEquals(-1L, database.loadPlaybackPositionTrackId());
        assertEquals(0L, database.loadPlaybackPositionMs());
    }

    @Test
    public void deleteTrackRemovesPlaybackQueueEntry() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track(4001L, "QueueDeleteA"));
        tracks.add(track(4002L, "QueueDeleteB"));
        database.upsertTracks(tracks);
        database.savePlaybackQueue(tracks, 0);
        database.savePlaybackPosition(4001L, 12000L);

        database.deleteTrack(4001L);

        List<Track> queue = database.loadPlaybackQueueTracks();
        assertEquals(1, queue.size());
        assertEquals("QueueDeleteB", queue.get(0).title);
        assertEquals(0, database.loadPlaybackQueueIndex());
        assertEquals(-1L, database.loadPlaybackPositionTrackId());
        assertEquals(0L, database.loadPlaybackPositionMs());
    }

    @Test
    public void deleteTrackBeforeCurrentShiftsPlaybackQueueIndex() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track(4101L, "QueueShiftA"));
        tracks.add(track(4102L, "QueueShiftB"));
        tracks.add(track(4103L, "QueueShiftC"));
        database.upsertTracks(tracks);
        database.savePlaybackQueue(tracks, 2);
        database.savePlaybackPosition(4103L, 9000L);

        database.deleteTrack(4101L);

        List<Track> queue = database.loadPlaybackQueueTracks();
        assertEquals(2, queue.size());
        assertEquals("QueueShiftB", queue.get(0).title);
        assertEquals("QueueShiftC", queue.get(1).title);
        assertEquals(1, database.loadPlaybackQueueIndex());
        assertEquals(4103L, database.loadPlaybackPositionTrackId());
        assertEquals(9000L, database.loadPlaybackPositionMs());
    }

    @Test
    public void clearPlayHistoryRemovesRecentAndMostPlayedRecords() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track(5001L, "HistoryA"));
        tracks.add(track(5002L, "HistoryB"));
        database.upsertTracks(tracks);
        database.markPlayed(5001L);
        database.markPlayed(5002L);
        database.markPlayed(5002L);
        assertEquals(2, database.loadRecentlyPlayed(10).size());
        assertEquals(2, database.loadMostPlayed(10).size());

        int removed = database.clearPlayHistory();

        assertEquals(2, removed);
        assertEquals(0, database.loadRecentlyPlayed(10).size());
        assertEquals(0, database.loadMostPlayed(10).size());
    }

    @Test
    public void loadPlayedSinceCountsOnlyEventsInsideWindow() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track(5101L, "HistoryWindowA"));
        database.upsertTracks(tracks);
        SQLiteDatabase rawDatabase = database.getWritableDatabase();
        rawDatabase.delete("play_events", null, null);
        long now = System.currentTimeMillis();
        insertPlayEvent(rawDatabase, 5101L, now - 100000L);
        insertPlayEvent(rawDatabase, 5101L, now - 1000L);

        List<TrackPlayRecord> records = database.loadPlayedSince(now - 5000L, 10);

        assertEquals(1, records.size());
        assertEquals("HistoryWindowA", records.get(0).track.title);
        assertEquals(1, records.get(0).playCount);
    }

    @Test
    public void replaceRemoteSourceTracksRemovesOnlyThatSourceTracks() {
        ArrayList<Track> initial = new ArrayList<>();
        initial.add(track(6001L, "OldWebDavA", "webdav:11:https://example.com/old-a.flac"));
        initial.add(track(6002L, "OldWebDavB", "webdav:11:https://example.com/old-b.flac"));
        initial.add(track(6003L, "OtherWebDav", "webdav:12:https://example.com/other.flac"));
        initial.add(track(6004L, "StreamStillHere", "stream:https://example.com/live.mp3"));
        database.upsertTracks(initial);

        ArrayList<Track> replacement = new ArrayList<>();
        replacement.add(track(6005L, "NewWebDav", "webdav:11:https://example.com/new.flac"));

        int removed = database.replaceRemoteSourceTracks(11L, replacement);

        List<Track> tracks = database.loadTracks();
        assertEquals(2, removed);
        assertFalse(containsTitle(tracks, "OldWebDavA"));
        assertFalse(containsTitle(tracks, "OldWebDavB"));
        assertTrue(containsTitle(tracks, "NewWebDav"));
        assertTrue(containsTitle(tracks, "OtherWebDav"));
        assertTrue(containsTitle(tracks, "StreamStillHere"));
    }

    @Test
    public void loadRemoteSourceTracksReturnsOnlyRequestedWebDavSource() {
        ArrayList<Track> initial = new ArrayList<>();
        initial.add(track(7001L, "WebDavA", "webdav:21:https://example.com/a.flac"));
        initial.add(track(7002L, "WebDavB", "webdav:21:https://example.com/b.flac"));
        initial.add(track(7003L, "OtherWebDav", "webdav:22:https://example.com/other.flac"));
        initial.add(track(7004L, "StreamTrack", "stream:https://example.com/live.mp3"));
        database.upsertTracks(initial);

        List<Track> tracks = database.loadRemoteSourceTracks(21L);

        assertEquals(2, tracks.size());
        assertTrue(containsTitle(tracks, "WebDavA"));
        assertTrue(containsTitle(tracks, "WebDavB"));
        assertFalse(containsTitle(tracks, "OtherWebDav"));
        assertFalse(containsTitle(tracks, "StreamTrack"));
    }

    private Track track(long id, String title) {
        return track(id, title, "test:" + id);
    }

    private Track track(long id, String title, String dataPath) {
        return new Track(
                id,
                title,
                "测试艺人",
                "测试专辑",
                1000L,
                Uri.parse("content://test/" + id),
                dataPath,
                -1L,
                null
        );
    }

    private boolean containsTitle(List<Track> tracks, String title) {
        for (Track track : tracks) {
            if (title.equals(track.title)) {
                return true;
            }
        }
        return false;
    }

    private void insertPlayEvent(SQLiteDatabase rawDatabase, long trackId, long playedAt) {
        ContentValues values = new ContentValues();
        values.put("track_id", trackId);
        values.put("played_at", playedAt);
        rawDatabase.insert("play_events", null, values);
    }

    private void assertOrder(long playlistId, String firstTitle, String secondTitle) {
        List<Track> tracks = database.loadPlaylistTracks(playlistId);
        assertEquals(2, tracks.size());
        assertEquals(firstTitle, tracks.get(0).title);
        assertEquals(secondTitle, tracks.get(1).title);
    }
}
