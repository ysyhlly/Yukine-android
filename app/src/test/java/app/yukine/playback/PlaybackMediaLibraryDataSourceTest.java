package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import app.yukine.model.Playlist;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;

public final class PlaybackMediaLibraryDataSourceTest {
    @Test
    public void delegatesLibraryLoadsAndMediaItemCreationOutsideService() {
        Track cached = track(1L);
        Track playlistTrack = track(2L);
        Playlist playlist = new Playlist(7L, "Road", 1, 0L, 0L);
        FakeLibrarySource librarySource = new FakeLibrarySource(cached, playlist, playlistTrack);
        FakeMediaItemFactory mediaItemFactory = new FakeMediaItemFactory();
        PlaybackMediaLibraryDataSource dataSource = new PlaybackMediaLibraryDataSource(
                "Yukine",
                librarySource,
                mediaItemFactory,
                track -> new MediaMetadata.Builder().setTitle("Meta " + track.id).build()
        );

        assertEquals("Yukine", dataSource.appName());
        assertEquals(Collections.singletonList(cached), dataSource.loadCachedTracks());
        assertEquals(Collections.singletonList(playlist), dataSource.loadPlaylists());
        assertEquals(Collections.emptyList(), dataSource.loadRecentlyPlayed(50));
        assertEquals(50, librarySource.lastRecentLimit);
        assertEquals(Collections.singletonList(playlistTrack), dataSource.loadPlaylistTracks(7L));
        assertEquals(7L, librarySource.lastPlaylistId);

        MediaItem item = dataSource.mediaItemForTrack(cached);

        assertEquals(cached, mediaItemFactory.lastTrack);
        assertEquals("media:1", item.mediaId);
        assertEquals("Meta 1", item.mediaMetadata.title.toString());
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                180000L,
                Uri.parse("file:///music/" + id + ".flac"),
                "local:" + id
        );
    }

    private static final class FakeLibrarySource implements PlaybackMediaLibraryDataSource.LibrarySource {
        private final Track cachedTrack;
        private final Playlist playlist;
        private final Track playlistTrack;
        private int lastRecentLimit;
        private long lastPlaylistId;

        FakeLibrarySource(Track cachedTrack, Playlist playlist, Track playlistTrack) {
            this.cachedTrack = cachedTrack;
            this.playlist = playlist;
            this.playlistTrack = playlistTrack;
        }

        @Override
        public List<Track> loadCachedTracks() {
            return Collections.singletonList(cachedTrack);
        }

        @Override
        public List<Playlist> loadPlaylists() {
            return Collections.singletonList(playlist);
        }

        @Override
        public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
            lastRecentLimit = limit;
            return Collections.emptyList();
        }

        @Override
        public List<Track> loadPlaylistTracks(long playlistId) {
            lastPlaylistId = playlistId;
            return Collections.singletonList(playlistTrack);
        }
    }

    private static final class FakeMediaItemFactory implements PlaybackMediaLibraryDataSource.MediaItemFactory {
        private Track lastTrack;

        @Override
        public MediaItem mediaItemForTrack(
                Track track,
                Function<Track, MediaMetadata> metadataProvider
        ) {
            lastTrack = track;
            return new MediaItem.Builder()
                    .setMediaId("media:" + track.id)
                    .setMediaMetadata(metadataProvider.apply(track))
                    .build();
        }
    }
}
