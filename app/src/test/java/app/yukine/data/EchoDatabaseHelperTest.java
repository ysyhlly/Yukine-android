package app.yukine.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import app.yukine.model.Playlist;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class EchoDatabaseHelperTest {
    private static final String PLAYLIST_DATABASE = "test-echo-playlists.db";
    private static final String MIGRATION_DATABASE = "test-echo-migration.db";
    private static final String TRANSACTION_DATABASE = "test-echo-transaction.db";
    private static final String CONCURRENT_DATABASE = "test-echo-concurrent.db";
    private static final String SETTINGS_DATABASE = "test-echo-settings.db";

    private EchoDatabaseHelper helper;

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        Context context = ApplicationProvider.getApplicationContext();
        deleteDatabase(context, PLAYLIST_DATABASE);
        deleteDatabase(context, MIGRATION_DATABASE);
        deleteDatabase(context, TRANSACTION_DATABASE);
        deleteDatabase(context, CONCURRENT_DATABASE);
        deleteDatabase(context, SETTINGS_DATABASE);
    }

    @Test
    public void systemMediaLyricsTitleSettingDefaultsOffAndPersists() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), SETTINGS_DATABASE);

        Assert.assertFalse(helper.loadSystemMediaLyricsTitleEnabled());

        helper.saveSystemMediaLyricsTitleEnabled(true);

        Assert.assertTrue(helper.loadSystemMediaLyricsTitleEnabled());
    }

    @Test
    public void deletePlaylistRemovesOrphanedStreamingPlaceholdersButKeepsSharedTracks() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), PLAYLIST_DATABASE);

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


    @Test
    public void deletePlaylistKeepsQueuedStreamingPlaceholderForPlaybackRestore() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), PLAYLIST_DATABASE);

        long playlistId = helper.createPlaylist("QueueStreamingOrphan");
        Track first = localTrack(111L, "QueueFirstKeep");
        Track queuedStreaming = streamingTrack(112L);
        Track current = localTrack(113L, "QueueCurrentKeep");

        helper.upsertTracks(Arrays.asList(first, queuedStreaming, current));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, queuedStreaming.id));
        helper.savePlaybackQueue(Arrays.asList(first, queuedStreaming, current), 2);

        Assert.assertTrue(helper.deletePlaylist(playlistId));

        List<Track> queue = helper.loadPlaybackQueueTracks();
        Assert.assertEquals(3, queue.size());
        Assert.assertEquals(first.id, queue.get(0).id);
        Assert.assertEquals(queuedStreaming.id, queue.get(1).id);
        Assert.assertEquals(current.id, queue.get(2).id);
        Assert.assertEquals(2, helper.loadPlaybackQueueIndex());
        Assert.assertTrue(containsTrackId(helper.loadTracks(), queuedStreaming.id));
        Assert.assertTrue(helper.loadPlaylistTracks(playlistId).isEmpty());
    }

    @Test
    public void upgradeFromLegacySchemaAddsCurrentTablesColumnsIndexesAndBackfillsHistory() {
        Context context = ApplicationProvider.getApplicationContext();
        createLegacyVersionOneDatabase(context, MIGRATION_DATABASE);

        helper = new EchoDatabaseHelper(context, MIGRATION_DATABASE);
        SQLiteDatabase database = helper.getWritableDatabase();

        Assert.assertTrue(tableExists(database, "play_events"));
        Assert.assertTrue(tableExists(database, "playlists"));
        Assert.assertTrue(tableExists(database, "settings"));
        Assert.assertTrue(tableExists(database, "remote_sources"));
        Assert.assertTrue(tableExists(database, "playback_queue"));
        Assert.assertTrue(tableExists(database, "streaming_track_matches"));
        Assert.assertTrue(columnExists(database, "tracks", "codec"));
        Assert.assertTrue(columnExists(database, "tracks", "replay_gain_album_db"));
        Assert.assertTrue(columnExists(database, "favorites", "created_at"));
        Assert.assertTrue(columnExists(database, "play_history", "play_count"));
        Assert.assertTrue(indexExists(database, "idx_tracks_data_path"));
        Assert.assertTrue(indexExists(database, "idx_tracks_album"));

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertEquals("Legacy Track", tracks.get(0).title);
        Assert.assertEquals("", tracks.get(0).codec);
        Assert.assertEquals(0, tracks.get(0).bitrateKbps);

        List<TrackPlayRecord> recentlyPlayed = helper.loadRecentlyPlayed(10);
        Assert.assertEquals(1, recentlyPlayed.size());
        Assert.assertEquals("Legacy Track", recentlyPlayed.get(0).track.title);
    }

    @Test
    public void upgradeFromPartialPlaybackQueueSchemaAddsAudioColumnsAndPreservesQueueRows() {
        Context context = ApplicationProvider.getApplicationContext();
        createPartialPlaybackQueueDatabase(context, MIGRATION_DATABASE);

        helper = new EchoDatabaseHelper(context, MIGRATION_DATABASE);
        SQLiteDatabase database = helper.getWritableDatabase();

        Assert.assertTrue(columnExists(database, "playback_queue", "codec"));
        Assert.assertTrue(columnExists(database, "playback_queue", "bitrate_kbps"));
        Assert.assertTrue(columnExists(database, "playback_queue", "sample_rate_hz"));
        Assert.assertTrue(columnExists(database, "playback_queue", "bits_per_sample"));
        Assert.assertTrue(columnExists(database, "playback_queue", "channel_count"));
        Assert.assertTrue(columnExists(database, "playback_queue", "replay_gain_track_db"));
        Assert.assertTrue(columnExists(database, "playback_queue", "replay_gain_album_db"));

        List<Track> queue = helper.loadPlaybackQueueTracks();
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(1_101L, queue.get(0).id);
        Assert.assertEquals("Partial Queue", queue.get(0).title);
        Assert.assertEquals("", queue.get(0).codec);
        Assert.assertEquals(0, queue.get(0).bitrateKbps);
        Assert.assertEquals(0, queue.get(0).sampleRateHz);
        Assert.assertEquals(0, queue.get(0).bitsPerSample);
        Assert.assertEquals(0, queue.get(0).channelCount);
        Assert.assertEquals(0.0, queue.get(0).replayGainTrackDb, 0.0);
        Assert.assertEquals(0.0, queue.get(0).replayGainAlbumDb, 0.0);
        Assert.assertEquals(0, helper.loadPlaybackQueueIndex());
    }

    @Test
    public void upsertTracksRollsBackWholeBatchWhenOneTrackFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);

        try {
            helper.upsertTracks(Arrays.asList(localTrack(1001L, "ShouldRollback"), null));
            Assert.fail("Expected null track to abort the transaction");
        } catch (NullPointerException expected) {
            // Baseline: failed batches must not leave partially inserted rows behind.
        }

        Assert.assertTrue(helper.loadTracks().isEmpty());
    }

    @Test
    public void replaceTracksRollsBackExistingLibraryWhenReplacementBatchFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track existing = localTrack(1_011L, "ExistingLibraryTrack");
        helper.upsertTracks(Collections.singletonList(existing));

        try {
            helper.replaceTracks(Arrays.asList(localTrack(1_012L, "HalfInsertedReplacement"), null));
            Assert.fail("Expected null replacement track to abort the transaction");
        } catch (NullPointerException expected) {
            // Baseline: failed library refresh must not erase the previously scanned local library.
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertTrue(containsTrackId(tracks, existing.id));
        Assert.assertFalse(containsTrackId(tracks, 1_012L));
    }

    @Test
    public void replaceTracksKeepsExistingLibraryWhenRefreshIsCancelledBeforeReplacement() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track existing = localTrack(1_013L, "ExistingLibraryTrack");
        helper.upsertTracks(Collections.singletonList(existing));

        Thread.currentThread().interrupt();
        try {
            helper.replaceTracks(Collections.singletonList(localTrack(1_014L, "CancelledReplacement")));
            Assert.fail("Expected interrupted replacement to cancel before deleting existing tracks");
        } catch (java.util.concurrent.CancellationException expected) {
            // Cancellation is cooperative and must preserve the previous atomic library snapshot.
        } finally {
            Thread.interrupted();
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertTrue(containsTrackId(tracks, existing.id));
        Assert.assertFalse(containsTrackId(tracks, 1_014L));
    }

    @Test
    public void loadTracksNeedingAudioSpecsLimitsAndExcludesRemoteTracks() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_021L, "NeedsSpecsFirst");
        Track second = localTrack(1_022L, "NeedsSpecsSecond");
        Track parsed = new Track(
                1_023L,
                "AlreadyParsed",
                "Artist",
                "Album",
                120_000L,
                Uri.parse("file:///music/1023.mp3"),
                "/music/1023.mp3",
                0L,
                null,
                "mp3",
                320,
                44_100,
                16,
                2
        );
        Track remote = streamingTrack(1_024L);
        helper.upsertTracks(Arrays.asList(first, second, parsed, remote));

        List<Track> firstBatch = helper.loadTracksNeedingAudioSpecs(1);
        List<Track> allCandidates = helper.loadTracksNeedingAudioSpecs(10);

        Assert.assertEquals(1, firstBatch.size());
        Assert.assertEquals(first.id, firstBatch.get(0).id);
        Assert.assertEquals(2, allCandidates.size());
        Assert.assertTrue(containsTrackId(allCandidates, first.id));
        Assert.assertTrue(containsTrackId(allCandidates, second.id));
        Assert.assertFalse(containsTrackId(allCandidates, parsed.id));
        Assert.assertFalse(containsTrackId(allCandidates, remote.id));
    }

    @Test
    public void mediaStoreGenerationPersistsOnlyValidTokens() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);

        Assert.assertEquals(-1L, helper.loadMediaStoreGeneration());
        helper.saveMediaStoreGeneration(42L);
        helper.saveMediaStoreGeneration(-1L);

        Assert.assertEquals(42L, helper.loadMediaStoreGeneration());
    }





    @Test
    public void saveRemoteSourceUpdateDeletesOldCachedTracksAndKeepsSource() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        long sourceId = helper.saveRemoteSource(remoteSource(-1L, "Original", "https://old.example.test"));
        Track oldRemote = remoteSourceTrack(1_961L, sourceId, "OldRemoteCache");
        Track unrelatedRemote = remoteSourceTrack(1_962L, sourceId + 1L, "OtherRemoteCache");
        Track local = localTrack(1_963L, "LocalKeep");
        helper.upsertTracks(Arrays.asList(oldRemote, unrelatedRemote, local));

        long savedId = helper.saveRemoteSource(remoteSource(sourceId, "Updated", "https://new.example.test/root"));

        Assert.assertEquals(sourceId, savedId);
        RemoteSource updated = helper.loadRemoteSource(sourceId);
        Assert.assertNotNull(updated);
        Assert.assertEquals("Updated", updated.name);
        Assert.assertEquals("https://new.example.test/root", updated.baseUrl);
        List<Track> tracks = helper.loadTracks();
        Assert.assertFalse(containsTrackId(tracks, oldRemote.id));
        Assert.assertTrue(containsTrackId(tracks, unrelatedRemote.id));
        Assert.assertTrue(containsTrackId(tracks, local.id));
    }

    @Test
    public void deleteStreamTracksRollsBackTrackDeleteWhenReferenceCleanupFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track stream = new Track(
                7_721L,
                "StreamDeleteRollback",
                "Artist",
                "Album",
                120_000L,
                Uri.parse("https://stream.example.com/delete.mp3"),
                "stream:https://stream.example.com/delete.mp3"
        );
        Track local = localTrack(7_722L, "StreamDeleteLocalKeep");
        helper.upsertTracks(Arrays.asList(stream, local));
        helper.markPlayed(stream.id);
        Assert.assertEquals(1, countRows(
                helper.getReadableDatabase(),
                "play_history",
                "track_id = ?",
                new String[]{String.valueOf(stream.id)}
        ));

        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("CREATE TRIGGER fail_stream_history_delete "
                + "BEFORE DELETE ON play_history "
                + "WHEN OLD.track_id = " + stream.id + " "
                + "BEGIN SELECT RAISE(ABORT, 'fail stream history delete'); END");
        try {
            helper.deleteStreamTracks();
            Assert.fail("Expected play_history delete trigger to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: stream track deletion and reference cleanup must be atomic.
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertTrue(containsTrackId(tracks, stream.id));
        Assert.assertTrue(containsTrackId(tracks, local.id));
        Assert.assertEquals(1, countRows(
                helper.getReadableDatabase(),
                "play_history",
                "track_id = ?",
                new String[]{String.valueOf(stream.id)}
        ));
    }

    @Test
    public void deleteRemoteSourceTracksRollsBackWhenReferenceCleanupFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        long sourceId = helper.saveRemoteSource(remoteSource(-1L, "CacheOnlyRollback", "https://dav-cache.example.com"));
        Track remote = remoteSourceTrack(7_731L, sourceId, "CacheOnlyRemote");
        Track local = localTrack(7_732L, "CacheOnlyLocalKeep");
        helper.upsertTracks(Arrays.asList(remote, local));
        helper.markPlayed(remote.id);
        Assert.assertEquals(1, countRows(
                helper.getReadableDatabase(),
                "play_history",
                "track_id = ?",
                new String[]{String.valueOf(remote.id)}
        ));

        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("CREATE TRIGGER fail_remote_history_delete "
                + "BEFORE DELETE ON play_history "
                + "WHEN OLD.track_id = " + remote.id + " "
                + "BEGIN SELECT RAISE(ABORT, 'fail remote history delete'); END");
        try {
            helper.deleteRemoteSourceTracks(sourceId);
            Assert.fail("Expected play_history delete trigger to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: WebDAV cache cleanup and reference cleanup must be atomic.
        }

        Assert.assertNotNull(helper.loadRemoteSource(sourceId));
        List<Track> tracks = helper.loadTracks();
        Assert.assertTrue(containsTrackId(tracks, remote.id));
        Assert.assertTrue(containsTrackId(tracks, local.id));
        Assert.assertEquals(1, countRows(
                helper.getReadableDatabase(),
                "play_history",
                "track_id = ?",
                new String[]{String.valueOf(remote.id)}
        ));
    }

    @Test
    public void deleteRemoteSourceRollsBackTrackDeleteWhenSourceDeleteFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        long sourceId = helper.saveRemoteSource(remoteSource(-1L, "DeleteRollback", "https://dav-delete.example.com"));
        Track remote = remoteSourceTrack(7_711L, sourceId, "DeleteRemoteCache");
        Track local = localTrack(7_712L, "DeleteLocalKeep");
        helper.upsertTracks(Arrays.asList(remote, local));

        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("CREATE TRIGGER fail_remote_source_delete "
                + "BEFORE DELETE ON remote_sources "
                + "WHEN OLD.id = " + sourceId + " "
                + "BEGIN SELECT RAISE(ABORT, 'fail remote source delete'); END");
        try {
            helper.deleteRemoteSource(sourceId);
            Assert.fail("Expected remote source delete trigger to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: source row delete and cached WebDAV track cleanup must be atomic.
        }

        Assert.assertNotNull(helper.loadRemoteSource(sourceId));
        List<Track> tracks = helper.loadTracks();
        Assert.assertTrue(containsTrackId(tracks, remote.id));
        Assert.assertTrue(containsTrackId(tracks, local.id));
    }

    @Test
    public void saveRemoteSourceRollsBackTrackDeleteWhenSourceUpdateFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        long sourceId = helper.saveRemoteSource(remoteSource(-1L, "Original", "https://old.example.test"));
        Track cachedRemoteTrack = remoteSourceTrack(1_951L, sourceId, "CachedRemoteTrack");
        helper.upsertTracks(Collections.singletonList(cachedRemoteTrack));

        helper.getWritableDatabase().execSQL("DROP TABLE remote_sources");
        try {
            helper.saveRemoteSource(remoteSource(sourceId, "Updated", "https://new.example.test"));
            Assert.fail("Expected missing remote_sources table to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: editing a WebDAV source must not delete cached tracks unless the source update also commits.
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertEquals(cachedRemoteTrack.id, tracks.get(0).id);
        Assert.assertEquals(cachedRemoteTrack.dataPath, tracks.get(0).dataPath);
    }

    @Test
    public void replaceRemoteSourceTracksRollsBackDeleteWhenReplacementBatchFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track existing = remoteSourceTrack(1_501L, 77L, "ExistingRemote");
        helper.upsertTracks(Collections.singletonList(existing));

        try {
            helper.replaceRemoteSourceTracks(77L, Arrays.asList(remoteSourceTrack(1_502L, 77L, "NewRemote"), null));
            Assert.fail("Expected null replacement track to abort the transaction");
        } catch (NullPointerException expected) {
            // Baseline: the source replacement transaction must restore the deleted old rows.
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertEquals(existing.id, tracks.get(0).id);
        Assert.assertEquals(existing.dataPath, tracks.get(0).dataPath);
    }




    @Test
    public void clearPlayHistoryRollsBackHistoryWhenEventDeleteFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_941L, "ClearHistoryRollbackA");
        Track second = localTrack(1_942L, "ClearHistoryRollbackB");
        helper.upsertTracks(Arrays.asList(first, second));
        helper.markPlayed(first.id);
        helper.markPlayed(second.id);

        helper.getWritableDatabase().execSQL("DROP TABLE play_events");
        try {
            helper.clearPlayHistory();
            Assert.fail("Expected missing play_events table to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: clearing play_history and play_events must be atomic.
        }

        List<TrackPlayRecord> recentlyPlayed = helper.loadRecentlyPlayed(10);
        Assert.assertEquals(2, recentlyPlayed.size());
        Assert.assertTrue(containsTrackIdFromRecords(recentlyPlayed, first.id));
        Assert.assertTrue(containsTrackIdFromRecords(recentlyPlayed, second.id));
    }

    @Test
    public void markPlayedRollsBackHistoryWhenPlayEventInsertFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track track = localTrack(1_701L, "HistoryStable");
        helper.upsertTracks(Collections.singletonList(track));
        helper.markPlayed(track.id);

        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("DROP TABLE play_events");
        try {
            helper.markPlayed(track.id);
            Assert.fail("Expected missing play_events table to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: failed event writes must not leave play_history half-updated.
        }

        List<TrackPlayRecord> recentlyPlayed = helper.loadRecentlyPlayed(10);
        Assert.assertEquals(1, recentlyPlayed.size());
        Assert.assertEquals(track.id, recentlyPlayed.get(0).track.id);
        Assert.assertEquals(1, recentlyPlayed.get(0).playCount);
    }


    @Test
    public void updateAudioSpecsRollsBackTrackUpdateWhenQueueMirrorFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track track = localTrack(1_981L, "AudioSpecRollback");
        helper.upsertTracks(Collections.singletonList(track));
        helper.savePlaybackQueue(Collections.singletonList(track), 0);

        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("CREATE TRIGGER fail_queue_codec_update "
                + "BEFORE UPDATE OF codec ON playback_queue "
                + "WHEN NEW.track_id = " + track.id + " "
                + "BEGIN SELECT RAISE(ABORT, 'fail queue codec update'); END");
        try {
            helper.updateAudioSpecs(Collections.singletonList(audioSpecTrack(track.id, "flac")));
            Assert.fail("Expected playback_queue mirror trigger to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: track audio specs and playback_queue mirror must be updated atomically.
        }

        Track stored = findTrack(helper.loadTracks(), track.id);
        Assert.assertNotNull(stored);
        Assert.assertEquals("", stored.codec);
        Assert.assertEquals(0, stored.bitrateKbps);
        Track queued = helper.loadPlaybackQueueTracks().get(0);
        Assert.assertEquals("", queued.codec);
        Assert.assertEquals(0, queued.bitrateKbps);
    }

    @Test
    public void replaceTrackAndMigrateReferencesMovesAllUserDataToReplacementTrack() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track oldTrack = localTrack(1_801L, "OldReferenced");
        Track replacement = localTrack(1_802L, "ReplacementReferenced");
        Track queueNeighbor = localTrack(1_803L, "QueueNeighbor");
        helper.upsertTracks(Arrays.asList(oldTrack, queueNeighbor));
        helper.setFavorite(oldTrack.id, true);
        helper.markPlayed(oldTrack.id);
        helper.markPlayed(oldTrack.id);
        long playlistId = helper.createPlaylist("Migration");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, oldTrack.id));
        helper.savePlaybackQueue(Arrays.asList(queueNeighbor, oldTrack), 1);
        helper.savePlaybackPosition(oldTrack.id, 42_000L);

        helper.replaceTrackAndMigrateReferences(oldTrack.id, replacement);

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(2, tracks.size());
        Assert.assertFalse(containsTrackId(tracks, oldTrack.id));
        Assert.assertTrue(containsTrackId(tracks, replacement.id));
        Assert.assertTrue(helper.isFavorite(replacement.id));
        Assert.assertFalse(helper.isFavorite(oldTrack.id));
        List<TrackPlayRecord> recentlyPlayed = helper.loadRecentlyPlayed(10);
        Assert.assertEquals(1, recentlyPlayed.size());
        Assert.assertEquals(replacement.id, recentlyPlayed.get(0).track.id);
        Assert.assertEquals(2, recentlyPlayed.get(0).playCount);
        List<Track> playlistTracks = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(1, playlistTracks.size());
        Assert.assertEquals(replacement.id, playlistTracks.get(0).id);
        List<Track> queue = helper.loadPlaybackQueueTracks();
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(queueNeighbor.id, queue.get(0).id);
        Assert.assertEquals(replacement.id, queue.get(1).id);
        Assert.assertEquals(1, helper.loadPlaybackQueueIndex());
        Assert.assertEquals(replacement.id, helper.loadPlaybackPositionTrackId());
        Assert.assertEquals(42_000L, helper.loadPlaybackPositionMs());
    }




    @Test
    public void addTrackToPlaylistMissingPlaylistReturnsFalseWithoutDanglingMembership() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track track = localTrack(1_923L, "AddRollback");
        helper.upsertTracks(Collections.singletonList(track));
        long playlistId = helper.createPlaylist("AddRollback");

        helper.getWritableDatabase().delete(
                "playlists",
                "id = ?",
                new String[]{String.valueOf(playlistId)}
        );

        Assert.assertFalse(helper.addTrackToPlaylist(playlistId, track.id));

        Assert.assertTrue(helper.loadPlaylistTracks(playlistId).isEmpty());
        Assert.assertEquals(0, countRows(
                helper.getReadableDatabase(),
                "playlist_tracks",
                "playlist_id = ? AND track_id = ?",
                new String[]{String.valueOf(playlistId), String.valueOf(track.id)}
        ));
    }

    @Test
    public void removeTrackFromMissingPlaylistLeavesMembershipUntouched() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_921L, "RemoveRollbackA");
        Track second = localTrack(1_922L, "RemoveRollbackB");
        helper.upsertTracks(Arrays.asList(first, second));
        long playlistId = helper.createPlaylist("RemoveRollback");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, first.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, second.id));

        helper.getWritableDatabase().delete(
                "playlists",
                "id = ?",
                new String[]{String.valueOf(playlistId)}
        );

        helper.removeTrackFromPlaylist(playlistId, first.id);

        List<Track> playlistTracks = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(2, playlistTracks.size());
        Assert.assertEquals(first.id, playlistTracks.get(0).id);
        Assert.assertEquals(second.id, playlistTracks.get(1).id);
    }

    @Test
    public void clearMissingPlaylistLeavesMembershipUntouched() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_931L, "ClearRollbackA");
        Track second = localTrack(1_932L, "ClearRollbackB");
        helper.upsertTracks(Arrays.asList(first, second));
        long playlistId = helper.createPlaylist("ClearRollback");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, first.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, second.id));

        helper.getWritableDatabase().delete(
                "playlists",
                "id = ?",
                new String[]{String.valueOf(playlistId)}
        );

        helper.clearPlaylistTracks(playlistId);

        List<Track> playlistTracks = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(2, playlistTracks.size());
        Assert.assertEquals(first.id, playlistTracks.get(0).id);
        Assert.assertEquals(second.id, playlistTracks.get(1).id);
    }

    @Test
    public void movePlaylistTrackAtSwapsByVisibleIndex() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_941L, "MoveAtA");
        Track second = localTrack(1_942L, "MoveAtB");
        Track third = localTrack(1_943L, "MoveAtC");
        helper.upsertTracks(Arrays.asList(first, second, third));
        long playlistId = helper.createPlaylist("MoveAt");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, first.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, second.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, third.id));

        Assert.assertTrue(helper.movePlaylistTrackAt(playlistId, 1, -1));
        List<Track> movedUp = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(second.id, movedUp.get(0).id);
        Assert.assertEquals(first.id, movedUp.get(1).id);
        Assert.assertEquals(third.id, movedUp.get(2).id);

        Assert.assertTrue(helper.movePlaylistTrackAt(playlistId, 1, 1));
        List<Track> movedDown = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(second.id, movedDown.get(0).id);
        Assert.assertEquals(third.id, movedDown.get(1).id);
        Assert.assertEquals(first.id, movedDown.get(2).id);

        Assert.assertFalse(helper.movePlaylistTrackAt(playlistId, 0, -1));
        Assert.assertFalse(helper.movePlaylistTrackAt(playlistId, 2, 1));
    }

    @Test
    public void movePlaylistTrackAtMissingPlaylistReturnsFalseWithoutReorder() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_944L, "MoveRollbackA");
        Track second = localTrack(1_945L, "MoveRollbackB");
        Track third = localTrack(1_946L, "MoveRollbackC");
        helper.upsertTracks(Arrays.asList(first, second, third));
        long playlistId = helper.createPlaylist("MoveRollback");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, first.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, second.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, third.id));

        helper.getWritableDatabase().delete(
                "playlists",
                "id = ?",
                new String[]{String.valueOf(playlistId)}
        );

        Assert.assertFalse(helper.movePlaylistTrackAt(playlistId, 1, -1));

        List<Track> playlistTracks = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(3, playlistTracks.size());
        Assert.assertEquals(first.id, playlistTracks.get(0).id);
        Assert.assertEquals(second.id, playlistTracks.get(1).id);
        Assert.assertEquals(third.id, playlistTracks.get(2).id);
    }

    @Test
    public void deleteMissingPlaylistDoesNotMutateDanglingPlaylistRows() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track dangling = localTrack(1_911L, "DanglingPlaylistTrack");
        long missingPlaylistId = 404_404L;
        helper.upsertTracks(Collections.singletonList(dangling));
        helper.getWritableDatabase().execSQL(
                "INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES (?, ?, ?, ?)",
                new Object[]{missingPlaylistId, dangling.id, 0, 1L}
        );
        Assert.assertEquals(1, helper.loadPlaylistTracks(missingPlaylistId).size());

        Assert.assertFalse(helper.deletePlaylist(missingPlaylistId));

        List<Track> danglingRows = helper.loadPlaylistTracks(missingPlaylistId);
        Assert.assertEquals(1, danglingRows.size());
        Assert.assertEquals(dangling.id, danglingRows.get(0).id);
        Assert.assertTrue(containsTrackId(helper.loadTracks(), dangling.id));
    }

    @Test
    public void deleteTrackRemovesReferencesEventsAndReconcilesPlaybackState() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_901L, "DeleteKeepBefore");
        Track deleted = localTrack(1_902L, "DeleteTarget");
        Track current = localTrack(1_903L, "DeleteKeepCurrent");
        helper.upsertTracks(Arrays.asList(first, deleted, current));
        helper.setFavorite(deleted.id, true);
        helper.markPlayed(deleted.id);
        helper.markPlayed(deleted.id);
        long playlistId = helper.createPlaylist("DeleteRefs");
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, first.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, deleted.id));
        Assert.assertTrue(helper.addTrackToPlaylist(playlistId, current.id));
        helper.savePlaybackQueue(Arrays.asList(first, deleted, current), 2);
        helper.savePlaybackPosition(deleted.id, 31_000L);

        Assert.assertEquals(1, helper.deleteTrack(deleted.id));

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(2, tracks.size());
        Assert.assertFalse(containsTrackId(tracks, deleted.id));
        Assert.assertFalse(helper.isFavorite(deleted.id));
        Assert.assertTrue(helper.loadRecentlyPlayed(10).isEmpty());
        Assert.assertEquals(0, countRows(
                helper.getReadableDatabase(),
                "play_events",
                "track_id = ?",
                new String[]{String.valueOf(deleted.id)}
        ));
        List<Track> playlistTracks = helper.loadPlaylistTracks(playlistId);
        Assert.assertEquals(2, playlistTracks.size());
        Assert.assertEquals(first.id, playlistTracks.get(0).id);
        Assert.assertEquals(current.id, playlistTracks.get(1).id);
        List<Track> queue = helper.loadPlaybackQueueTracks();
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(first.id, queue.get(0).id);
        Assert.assertEquals(current.id, queue.get(1).id);
        Assert.assertEquals(1, helper.loadPlaybackQueueIndex());
        Assert.assertEquals(-1L, helper.loadPlaybackPositionTrackId());
        Assert.assertEquals(0L, helper.loadPlaybackPositionMs());
    }


    @Test
    public void savePlaybackPositionRollsBackTrackIdWhenPositionWriteFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        helper.savePlaybackPosition(1_971L, 12_000L);

        SQLiteDatabase database = helper.getWritableDatabase();
        database.execSQL("CREATE TRIGGER fail_playback_position_ms "
                + "BEFORE INSERT ON settings "
                + "WHEN NEW.key = 'playback_position_ms' "
                + "BEGIN SELECT RAISE(ABORT, 'fail playback position ms'); END");
        try {
            helper.savePlaybackPosition(1_972L, 34_000L);
            Assert.fail("Expected playback position ms trigger to abort the transaction");
        } catch (android.database.sqlite.SQLiteException expected) {
            // Baseline: track id and position ms must be persisted atomically for cold-start restore.
        }

        Assert.assertEquals(1_971L, helper.loadPlaybackPositionTrackId());
        Assert.assertEquals(12_000L, helper.loadPlaybackPositionMs());
    }

    @Test
    public void savePlaybackQueueRollsBackOldQueueWhenReplacementBatchFails() {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), TRANSACTION_DATABASE);
        Track first = localTrack(1_601L, "QueueStableA");
        Track second = localTrack(1_602L, "QueueStableB");
        helper.upsertTracks(Arrays.asList(first, second));
        helper.savePlaybackQueue(Arrays.asList(first, second), 1);

        try {
            helper.savePlaybackQueue(Arrays.asList(localTrack(1_603L, "QueueBroken"), null), 0);
            Assert.fail("Expected null queue track to abort the transaction");
        } catch (NullPointerException expected) {
            // Baseline: failed queue replacement must not erase the cold-start restore queue.
        }

        List<Track> queue = helper.loadPlaybackQueueTracks();
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(first.id, queue.get(0).id);
        Assert.assertEquals(second.id, queue.get(1).id);
        Assert.assertEquals(1, helper.loadPlaybackQueueIndex());
    }

    @Test
    public void concurrentUpsertSameTrackIdKeepsSingleCompleteRow() throws Exception {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), CONCURRENT_DATABASE);
        int writers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        ArrayList<Future<?>> futures = new ArrayList<>();
        try {
            for (int writer = 0; writer < writers; writer++) {
                final int writerIndex = writer;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    helper.upsertTracks(Collections.singletonList(localTrack(3_000L, "ConcurrentShared" + writerIndex)));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(1, tracks.size());
        Assert.assertEquals(3_000L, tracks.get(0).id);
        Assert.assertTrue(tracks.get(0).title.startsWith("ConcurrentShared"));
        Assert.assertEquals("Artist", tracks.get(0).artist);
        Assert.assertEquals("Album", tracks.get(0).album);
    }

    @Test
    public void concurrentUpsertWritesKeepAllTracks() throws Exception {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), CONCURRENT_DATABASE);
        int writers = 6;
        int tracksPerWriter = 12;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        ArrayList<Future<?>> futures = new ArrayList<>();
        try {
            for (int writer = 0; writer < writers; writer++) {
                final int writerIndex = writer;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    for (int offset = 0; offset < tracksPerWriter; offset++) {
                        long id = 2_000L + writerIndex * tracksPerWriter + offset;
                        helper.upsertTracks(Collections.singletonList(localTrack(id, "Concurrent " + id)));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<Track> tracks = helper.loadTracks();
        Assert.assertEquals(writers * tracksPerWriter, tracks.size());
    }

    @Test
    public void concurrentPlaybackQueueWritesKeepOneCompleteQueueSnapshot() throws Exception {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), CONCURRENT_DATABASE);
        int writers = 6;
        int queueSize = 3;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        ArrayList<Future<?>> futures = new ArrayList<>();
        try {
            for (int writer = 0; writer < writers; writer++) {
                final int writerIndex = writer;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    long baseId = 4_000L + writerIndex * 10L;
                    helper.savePlaybackQueue(Arrays.asList(
                            localTrack(baseId, "QueueWriter" + writerIndex + "A"),
                            localTrack(baseId + 1L, "QueueWriter" + writerIndex + "B"),
                            localTrack(baseId + 2L, "QueueWriter" + writerIndex + "C")
                    ), writerIndex % queueSize);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<Track> queue = helper.loadPlaybackQueueTracks();
        Assert.assertEquals(queueSize, queue.size());
        long firstTrackId = queue.get(0).id;
        Assert.assertTrue(firstTrackId >= 4_000L);
        Assert.assertEquals(0L, (firstTrackId - 4_000L) % 10L);
        long writerIndex = (firstTrackId - 4_000L) / 10L;
        Assert.assertTrue(writerIndex >= 0L && writerIndex < writers);
        long expectedBaseId = 4_000L + writerIndex * 10L;
        Assert.assertEquals(expectedBaseId, queue.get(0).id);
        Assert.assertEquals(expectedBaseId + 1L, queue.get(1).id);
        Assert.assertEquals(expectedBaseId + 2L, queue.get(2).id);
        Assert.assertEquals((int) (writerIndex % queueSize), helper.loadPlaybackQueueIndex());
    }

    @Test
    public void concurrentPlaybackPositionWritesKeepTrackAndPositionPairAtomic() throws Exception {
        helper = new EchoDatabaseHelper(ApplicationProvider.getApplicationContext(), CONCURRENT_DATABASE);
        int writers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        ArrayList<Future<?>> futures = new ArrayList<>();
        try {
            for (int writer = 0; writer < writers; writer++) {
                final int writerIndex = writer;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    helper.savePlaybackPosition(5_000L + writerIndex, (writerIndex + 1L) * 1_000L);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        long trackId = helper.loadPlaybackPositionTrackId();
        long positionMs = helper.loadPlaybackPositionMs();
        Assert.assertTrue(trackId >= 5_000L && trackId < 5_000L + writers);
        long writerIndex = trackId - 5_000L;
        Assert.assertEquals((writerIndex + 1L) * 1_000L, positionMs);
    }

    private static void createLegacyVersionOneDatabase(Context context, String databaseName) {
        deleteDatabase(context, databaseName);
        File databaseFile = context.getDatabasePath(databaseName);
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            Assert.assertTrue(parent.exists() || parent.mkdirs());
        }
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        try {
            database.execSQL("CREATE TABLE tracks ("
                    + "id INTEGER PRIMARY KEY,"
                    + "title TEXT NOT NULL,"
                    + "artist TEXT NOT NULL,"
                    + "album TEXT NOT NULL,"
                    + "duration_ms INTEGER NOT NULL,"
                    + "content_uri TEXT NOT NULL,"
                    + "data_path TEXT NOT NULL,"
                    + "album_id INTEGER NOT NULL,"
                    + "album_art_uri TEXT NOT NULL"
                    + ")");
            database.execSQL("CREATE TABLE favorites (track_id INTEGER PRIMARY KEY)");
            database.execSQL("CREATE TABLE play_history (track_id INTEGER PRIMARY KEY, played_at INTEGER NOT NULL)");
            database.execSQL("INSERT INTO tracks "
                    + "(id, title, artist, album, duration_ms, content_uri, data_path, album_id, album_art_uri) "
                    + "VALUES (901, 'Legacy Track', 'Legacy Artist', 'Legacy Album', 123000, "
                    + "'file:///legacy-track.mp3', '/music/legacy-track.mp3', 90, '')");
            database.execSQL("INSERT INTO favorites (track_id) VALUES (901)");
            database.execSQL("INSERT INTO play_history (track_id, played_at) VALUES (901, 1700000000000)");
            database.setVersion(1);
        } finally {
            database.close();
        }
    }

    private static void createPartialPlaybackQueueDatabase(Context context, String databaseName) {
        deleteDatabase(context, databaseName);
        File databaseFile = context.getDatabasePath(databaseName);
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            Assert.assertTrue(parent.exists() || parent.mkdirs());
        }
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        try {
            database.execSQL("CREATE TABLE tracks ("
                    + "id INTEGER PRIMARY KEY,"
                    + "title TEXT NOT NULL,"
                    + "artist TEXT NOT NULL,"
                    + "album TEXT NOT NULL,"
                    + "duration_ms INTEGER NOT NULL,"
                    + "content_uri TEXT NOT NULL,"
                    + "data_path TEXT NOT NULL,"
                    + "album_id INTEGER NOT NULL,"
                    + "album_art_uri TEXT NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ")");
            database.execSQL("CREATE TABLE favorites (track_id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE play_history (track_id INTEGER PRIMARY KEY, played_at INTEGER NOT NULL, play_count INTEGER NOT NULL DEFAULT 1)");
            database.execSQL("CREATE TABLE playback_queue ("
                    + "position INTEGER PRIMARY KEY,"
                    + "track_id INTEGER NOT NULL,"
                    + "title TEXT NOT NULL DEFAULT '',"
                    + "artist TEXT NOT NULL DEFAULT '',"
                    + "album TEXT NOT NULL DEFAULT '',"
                    + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                    + "content_uri TEXT NOT NULL DEFAULT '',"
                    + "data_path TEXT NOT NULL DEFAULT '',"
                    + "album_id INTEGER NOT NULL DEFAULT 0,"
                    + "album_art_uri TEXT NOT NULL DEFAULT ''"
                    + ")");
            database.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            database.execSQL("INSERT INTO playback_queue "
                    + "(position, track_id, title, artist, album, duration_ms, content_uri, data_path, album_id, album_art_uri) "
                    + "VALUES (0, 1101, 'Partial Queue', 'Artist', 'Album', 90000, 'file:///partial.mp3', '/music/partial.mp3', 11, '')");
            database.execSQL("INSERT INTO settings (key, value) VALUES ('playback_queue_index', '0')");
            database.setVersion(10);
        } finally {
            database.close();
        }
    }


    private static int countRows(SQLiteDatabase database, String table, String whereClause, String[] whereArgs) {
        try (Cursor cursor = database.query(
                table,
                new String[]{"COUNT(*)"},
                whereClause,
                whereArgs,
                null,
                null,
                null
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static boolean tableExists(SQLiteDatabase database, String tableName) {
        try (Cursor cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{tableName}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static boolean indexExists(SQLiteDatabase database, String indexName) {
        try (Cursor cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
                new String[]{indexName}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static boolean columnExists(SQLiteDatabase database, String tableName, String columnName) {
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
        }
        return false;
    }



    private static Track findTrack(List<Track> tracks, long trackId) {
        for (Track track : tracks) {
            if (track.id == trackId) {
                return track;
            }
        }
        return null;
    }

    private static Track audioSpecTrack(long id, String codec) {
        return new Track(
                id,
                "Spec " + id,
                "Artist",
                "Album",
                120_000L,
                Uri.parse("file:///music/" + id + ".mp3"),
                "/music/" + id + ".mp3",
                0L,
                null,
                codec,
                960,
                48_000,
                24,
                2,
                -2.5f,
                -1.5f
        );
    }

    private static boolean containsTrackIdFromRecords(List<TrackPlayRecord> records, long trackId) {
        for (TrackPlayRecord record : records) {
            if (record.track.id == trackId) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTrackId(List<Track> tracks, long trackId) {
        for (Track track : tracks) {
            if (track.id == trackId) {
                return true;
            }
        }
        return false;
    }

    private static void deleteDatabase(Context context, String databaseName) {
        File dbFile = context.getDatabasePath(databaseName);
        if (dbFile != null) {
            dbFile.delete();
        }
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




    private static RemoteSource remoteSource(long id, String name, String baseUrl) {
        return new RemoteSource(
                id,
                RemoteSource.TYPE_WEBDAV,
                name,
                baseUrl,
                "user",
                "password",
                "music",
                "ready",
                1L
        );
    }

    private static Track remoteSourceTrack(long id, long sourceId, String title) {
        return new Track(
                id,
                title,
                "Remote Artist",
                "Remote Album",
                120_000L,
                Uri.parse("https://example.test/remote/" + id + ".mp3"),
                "webdav:" + sourceId + ":" + id
        );
    }

    private static Track localTrack(long id, String title) {
        return new Track(
                id,
                title,
                "Artist",
                "Album",
                120_000L,
                Uri.parse("file:///music/" + id + ".mp3"),
                "/music/" + id + ".mp3"
        );
    }
}
