package app.yukine.playback;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.List;
import java.util.function.Function;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.model.Playlist;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;
import app.yukine.playback.manager.PlaybackMediaLibraryCallback;
import app.yukine.playback.manager.PlaybackMediaSourceProvider;

@OptIn(markerClass = UnstableApi.class)
final class PlaybackMediaLibraryDataSource implements PlaybackMediaLibraryCallback.DataSource {
    interface LibrarySource {
        List<Track> loadCachedTracks();
        List<Playlist> loadPlaylists();
        List<TrackPlayRecord> loadRecentlyPlayed(int limit);
        List<Track> loadPlaylistTracks(long playlistId);
    }

    interface MediaItemFactory {
        MediaItem mediaItemForTrack(Track track, Function<Track, MediaMetadata> metadataProvider);
    }

    private final String appName;
    private final LibrarySource librarySource;
    private final MediaItemFactory mediaItemFactory;
    private final Function<Track, MediaMetadata> metadataProvider;

    PlaybackMediaLibraryDataSource(
            String appName,
            LibrarySource librarySource,
            MediaItemFactory mediaItemFactory,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        this.appName = appName;
        this.librarySource = librarySource;
        this.mediaItemFactory = mediaItemFactory;
        this.metadataProvider = metadataProvider;
    }

    static PlaybackMediaLibraryDataSource fromRepository(
            String appName,
            MusicLibraryRepository repository,
            PlaybackMediaSourceProvider mediaSourceProvider,
            Function<Track, MediaMetadata> metadataProvider
    ) {
        return new PlaybackMediaLibraryDataSource(
                appName,
                new RepositoryLibrarySource(repository),
                new PlaybackMediaItemFactory(mediaSourceProvider),
                metadataProvider
        );
    }

    @Override
    public String appName() {
        return appName;
    }

    @Override
    public List<Track> loadCachedTracks() {
        return librarySource.loadCachedTracks();
    }

    @Override
    public List<Playlist> loadPlaylists() {
        return librarySource.loadPlaylists();
    }

    @Override
    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
        return librarySource.loadRecentlyPlayed(limit);
    }

    @Override
    public List<Track> loadPlaylistTracks(long playlistId) {
        return librarySource.loadPlaylistTracks(playlistId);
    }

    @Override
    public MediaItem mediaItemForTrack(Track track) {
        return mediaItemFactory.mediaItemForTrack(track, metadataProvider);
    }

    private static final class RepositoryLibrarySource implements LibrarySource {
        private final MusicLibraryRepository repository;

        RepositoryLibrarySource(MusicLibraryRepository repository) {
            this.repository = repository;
        }

        @Override
        public List<Track> loadCachedTracks() {
            return repository.loadCachedTracks();
        }

        @Override
        public List<Playlist> loadPlaylists() {
            return repository.loadPlaylists();
        }

        @Override
        public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
            return repository.loadRecentlyPlayed(limit);
        }

        @Override
        public List<Track> loadPlaylistTracks(long playlistId) {
            return repository.loadPlaylistTracks(playlistId);
        }
    }

    private static final class PlaybackMediaItemFactory implements MediaItemFactory {
        private final PlaybackMediaSourceProvider mediaSourceProvider;

        PlaybackMediaItemFactory(PlaybackMediaSourceProvider mediaSourceProvider) {
            this.mediaSourceProvider = mediaSourceProvider;
        }

        @Override
        public MediaItem mediaItemForTrack(
                Track track,
                Function<Track, MediaMetadata> metadataProvider
        ) {
            return mediaSourceProvider.mediaItemForTrack(track, metadataProvider::apply);
        }
    }
}
