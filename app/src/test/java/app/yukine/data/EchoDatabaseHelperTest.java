package app.yukine.data;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import app.yukine.model.Playlist;
import app.yukine.model.Track;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class EchoDatabaseHelperTest {
    private EchoDatabaseHelper helper;

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        Context context = ApplicationProvider.getApplicationContext();
        File dbFile = context.getDatabasePath("test-echo-playlists.db");
        if (dbFile != null) {
            dbFile.delete();
        }
    }

    @Test
    public void deletePlaylistRemovesOrphanedStreamingPlaceholdersButKeepsSharedTracks() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), "test-echo-playlists.db");

        long playlistA = helper.createPlaylist("A");
        long playlistB = helper.createPlaylist("B");
        Track shared = streamingTrack(101L);
        Track orphan = streamingTrack(102L);

        helper.upsertTracks(java.util.Arrays.asList(shared, orphan));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistA, shared.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistA, orphan.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistB, shared.id));

        Assert.assertTrue(helper.deletePlaylist(playlistA));

        List<Track> remainingTracks = helper.loadTracks();
        List<Playlist> remainingPlaylists = helper.loadPlaylists();

        Assert.assertEquals(1, remainingPlaylists.size());
        Assert.assertEquals(playlistB, remainingPlaylists.get(0).id);
        Assert.assertEquals(1, remainingTracks.size());
        Assert.assertEquals(shared.id, remainingTracks.get(0).id);
        Assert.assertEquals(1, helper.loadPlaylistTracks(playlistB).size());
        Assert.assertEquals(shared.id, helper.loadPlaylistTracks(playlistB).get(0).id);
    }

    private static Track streamingTrack(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                120_000L,
                Uri.EMPTY,
                "streaming:netease:" + id
        );
    }
}
