package app.yukine.data;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.yukine.model.Playlist;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;

/**
 * CRUD 基线测试：覆盖 tracks / playlists / favorites / play_history / play_events /
 * playback_queue / streaming_track_matches / settings 的写入-读回路径。与
 * {@link EchoDatabaseHelperMigrationTest}（schema/迁移）互补——这里只验证行为契约，
 * 不改生产代码。本文件失败说明某条 CRUD 路径的写入或读回语义被破坏。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class EchoDatabaseHelperCrudTest {
    private static final String DB = "test-echo-crud.db";

    private EchoDatabaseHelper helper;

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
            helper = null;
        }
        Context context = ApplicationProvider.getApplicationContext();
        File dbFile = context.getDatabasePath(DB);
        if (dbFile != null) {
            dbFile.delete();
        }
    }

    private void newHelper() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), DB);
    }

    private static Track track(long id, String title) {
        return new Track(id, title, "Artist", "Album", 120_000L, Uri.EMPTY, "/path/" + id);
    }

    private static Track firstWithId(List<Track> tracks, long id) {
        for (Track t : tracks) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }

    @Test
    public void upsertTracksInsertsNewAndUpdatesExisting() {
        newHelper();
        helper.upsertTracks(Arrays.asList(track(1L, "A"), track(2L, "B")));
        Assert.assertEquals(2, helper.loadTracks().size());

        helper.upsertTracks(Collections.singletonList(track(1L, "A2")));
        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals("同 id 不应产生重复行", 2, tracks.size());
        Track updated = firstWithId(tracks, 1L);
        Assert.assertNotNull(updated);
        Assert.assertEquals("A2", updated.title);
    }

    @Test
    public void replaceTracksClearsAndInserts() {
        newHelper();
        helper.upsertTracks(Arrays.asList(track(1L, "A"), track(2L, "B")));
        helper.replaceTracks(Collections.singletonList(track(3L, "C")));
        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertEquals(3L, tracks.get(0).id);
    }

    @Test
    public void createPlaylistReturnsUniqueIdsForDistinctNames() {
        newHelper();
        long a = helper.createPlaylist("A");
        long b = helper.createPlaylist("B");
        Assert.assertNotEquals("playlist id 必须唯一", a, b);
        Assert.assertEquals(2, helper.loadPlaylists().size());
    }

    @Test
    public void createPlaylistIgnoresDuplicateName() {
        newHelper();
        long first = helper.createPlaylist("Dup");
        Assert.assertTrue("首次创建应返回有效 id", first > 0);
        helper.createPlaylist("Dup"); // name UNIQUE + CONFLICT_IGNORE 应跳过重复插入
        Assert.assertEquals("不应新增重复名称行", 1, helper.loadPlaylists().size());
    }

    @Test
    public void addAndRemoveTrackFromPlaylist() {
        newHelper();
        helper.upsertTracks(Collections.singletonList(track(1L, "A")));
        long pl = helper.createPlaylist("PL");
        Assert.assertTrue(helper.addTrackToPlaylist(pl, 1L));
        Assert.assertEquals(1, helper.loadPlaylistTracks(pl).size());
        helper.removeTrackFromPlaylist(pl, 1L);
        Assert.assertEquals(0, helper.loadPlaylistTracks(pl).size());
    }

    @Test
    public void renamePlaylistUpdatesName() {
        newHelper();
        long pl = helper.createPlaylist("Old");
        Assert.assertTrue(helper.renamePlaylist(pl, "New"));
        List<Playlist> playlists = helper.loadPlaylists();
        Assert.assertEquals(1, playlists.size());
        Assert.assertEquals("New", playlists.get(0).name);
    }

    @Test
    public void setFavoriteTogglesAndLoadsIds() {
        newHelper();
        helper.upsertTracks(Collections.singletonList(track(1L, "A")));
        helper.setFavorite(1L, true);
        Assert.assertTrue(helper.isFavorite(1L));
        Assert.assertTrue(helper.loadFavoriteIds().contains(1L));
        helper.setFavorite(1L, false);
        Assert.assertFalse(helper.isFavorite(1L));
        Assert.assertFalse(helper.loadFavoriteIds().contains(1L));
    }

    @Test
    public void markPlayedRecordsHistoryAndMostPlayed() {
        newHelper();
        helper.upsertTracks(Collections.singletonList(track(1L, "A")));
        helper.markPlayed(1L);

        List<TrackPlayRecord> recent = helper.loadRecentlyPlayed(10);
        Assert.assertEquals(1, recent.size());
        Assert.assertEquals(1L, recent.get(0).track.id);

        List<TrackPlayRecord> most = helper.loadMostPlayed(10);
        Assert.assertEquals(1, most.size());
        Assert.assertEquals(1, most.get(0).playCount);
    }

    @Test
    public void clearPlayHistoryRemovesRecords() {
        newHelper();
        helper.upsertTracks(Collections.singletonList(track(1L, "A")));
        helper.markPlayed(1L);
        Assert.assertEquals(1, helper.loadRecentlyPlayed(10).size());
        helper.clearPlayHistory();
        Assert.assertEquals(0, helper.loadRecentlyPlayed(10).size());
    }

    @Test
    public void saveAndLoadPlaybackQueue() {
        newHelper();
        helper.upsertTracks(Arrays.asList(track(1L, "A"), track(2L, "B")));
        helper.savePlaybackQueue(Arrays.asList(track(1L, "A"), track(2L, "B")), 1);
        Assert.assertEquals(2, helper.loadPlaybackQueueTracks().size());
        Assert.assertEquals(1, helper.loadPlaybackQueueIndex());
    }

    @Test
    public void saveAndLoadStreamingTrackMatch() {
        newHelper();
        helper.saveStreamingTrackMatch("local-key-1", "netease", "netease-track-42", track(1L, "A"));
        Assert.assertEquals("netease-track-42", helper.loadStreamingTrackMatch("local-key-1", "netease"));
    }

    @Test
    public void settingsRoundTripPersistsValues() {
        newHelper();
        helper.saveThemeMode("dark");
        Assert.assertEquals("dark", helper.loadThemeMode());
        helper.savePlaybackSpeed(1.5f);
        Assert.assertEquals(1.5f, helper.loadPlaybackSpeed(), 0.001f);
        helper.saveRepeatMode(2);
        Assert.assertEquals(2, helper.loadRepeatMode());
        helper.saveOnlineLyricsEnabled(true);
        Assert.assertTrue(helper.loadOnlineLyricsEnabled());
    }
}
