package app.yukine.playback;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Playlist;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
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

        Track anotherTrack = track(3L);
        assertEquals(
                "Meta 3",
                mediaItemFactory.lastMetadataProvider.apply(anotherTrack).title.toString()
        );
    }

    @Test
    public void fromRepositoryMediaItemsUsePlaybackMediaSourceProviderRules() {
        Context context = RuntimeEnvironment.getApplication();
        MusicLibraryRepository repository = new MusicLibraryRepository(context, new FakeStreamingDataPathParser());
        PlaybackMediaSourceProvider mediaSourceProvider = new PlaybackMediaSourceProvider(
                context,
                repository,
                new FakeStreamingPlaybackHeaderStore()
        );
        PlaybackMediaLibraryDataSource dataSource = PlaybackMediaLibraryDataSource.fromRepository(
                "Yukine",
                repository,
                mediaSourceProvider,
                track -> new MediaMetadata.Builder().setTitle("Meta " + track.id).build()
        );
        Track streaming = new Track(
                42L,
                "Stream",
                "Artist",
                "Album",
                180000L,
                Uri.parse("https://audio.example/current.flac"),
                "streaming:netease:42"
        );

        MediaItem item = dataSource.mediaItemForTrack(streaming);

        assertEquals("42", item.mediaId);
        assertEquals(streaming.contentUri, item.localConfiguration.uri);
        assertEquals(
                "streaming:netease:42|url=https://audio.example/current.flac",
                item.localConfiguration.customCacheKey
        );
        assertEquals("Meta 42", item.mediaMetadata.title.toString());
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
        private Function<Track, MediaMetadata> lastMetadataProvider;

        @Override
        public MediaItem mediaItemForTrack(
                Track track,
                Function<Track, MediaMetadata> metadataProvider
        ) {
            lastTrack = track;
            lastMetadataProvider = metadataProvider;
            return new MediaItem.Builder()
                    .setMediaId("media:" + track.id)
                    .setMediaMetadata(metadataProvider.apply(track))
                    .build();
        }
    }

    private static final class FakeStreamingDataPathParser implements StreamingDataPathParser {
        @Override
        public boolean isStreamingTrack(String dataPath) {
            return dataPath != null && dataPath.startsWith("streaming:");
        }

        @Override
        public String providerName(String dataPath) {
            if (dataPath == null || !dataPath.startsWith("streaming:")) {
                return null;
            }
            return dataPath.substring("streaming:".length()).split(":")[0];
        }

        @Override
        public String providerTrackId(String dataPath) {
            if (dataPath == null) {
                return "";
            }
            int index = dataPath.lastIndexOf(':');
            return index < 0 ? dataPath : dataPath.substring(index + 1);
        }
    }

    private static final class FakeStreamingPlaybackHeaderStore implements StreamingPlaybackHeaderStore {
        @Override
        public void register(String dataPath, java.util.Map<String, String> headers) {
        }

        @Override
        public java.util.Map<String, String> forDataPath(String dataPath) {
            return Collections.emptyMap();
        }

        @Override
        public boolean restoreForDataPath(String dataPath) {
            return false;
        }

        @Override
        public Track restoredTrackFor(Track track) {
            return null;
        }
    }
}
