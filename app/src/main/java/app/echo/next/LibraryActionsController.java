package app.echo.next;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.echo.next.data.MusicLibraryRepository;
import app.echo.next.model.Playlist;
import app.echo.next.model.PlaylistImportResult;
import app.echo.next.model.RemoteSource;
import app.echo.next.model.StreamImportResult;
import app.echo.next.model.Track;
import app.echo.next.model.TrackPlayRecord;

final class LibraryActionsController {
    private static final int PLAY_HISTORY_RECAP_LIMIT = 2000;
    private static final long PLAY_HISTORY_RECAP_WINDOW_MS = 4L * 7L * 24L * 60L * 60L * 1000L;

    interface Listener {
        void onLibraryLoaded(List<Track> tracks, Set<Long> favorites, String status);

        void onLibraryLoadFailed(String status);

        void onStreamM3uImported(List<Track> tracks, Set<Long> favorites, String status);

        void onPlaylistImported(long playlistId, List<Track> tracks, Set<Long> favorites, String status);

        void onPlaylistExported(boolean exported);

        void onCollectionsLoaded(CollectionsSnapshot snapshot);

        void onPlayHistoryCleared(int removed);

        void onFavoriteSaved();

        void onAudioSpecsParsed(int updatedCount, List<Track> tracks, Set<Long> favorites);
    }

    static final class CollectionsSnapshot {
        final long selectedPlaylistId;
        final Set<Long> favoriteIds;
        final List<Track> favoriteTracks;
        final List<TrackPlayRecord> recentRecords;
        final List<TrackPlayRecord> mostPlayedRecords;
        final List<Playlist> playlists;
        final List<RemoteSource> remoteSources;
        final List<Track> selectedPlaylistTracks;

        CollectionsSnapshot(
                long selectedPlaylistId,
                Set<Long> favoriteIds,
                List<Track> favoriteTracks,
                List<TrackPlayRecord> recentRecords,
                List<TrackPlayRecord> mostPlayedRecords,
                List<Playlist> playlists,
                List<RemoteSource> remoteSources,
                List<Track> selectedPlaylistTracks
        ) {
            this.selectedPlaylistId = selectedPlaylistId;
            this.favoriteIds = favoriteIds;
            this.favoriteTracks = favoriteTracks;
            this.recentRecords = recentRecords;
            this.mostPlayedRecords = mostPlayedRecords;
            this.playlists = playlists;
            this.remoteSources = remoteSources;
            this.selectedPlaylistTracks = selectedPlaylistTracks;
        }
    }

    private final MusicLibraryRepository repository;
    private final MainExecutors executors;
    private final Handler mainHandler;
    private final ContentResolver contentResolver;
    private final Listener listener;
    private boolean audioSpecParsingRunning;

    LibraryActionsController(
            MusicLibraryRepository repository,
            MainExecutors executors,
            Handler mainHandler,
            ContentResolver contentResolver,
            Listener listener
    ) {
        this.repository = repository;
        this.executors = executors;
        this.mainHandler = mainHandler;
        this.contentResolver = contentResolver;
        this.listener = listener;
    }

    void loadLibrary(final boolean allowCachedFirst, final boolean canScan) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                if (allowCachedFirst) {
                    final List<Track> cached = repository.loadCachedTracks();
                    final Set<Long> cachedFavorites = repository.loadFavoriteIds();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onLibraryLoaded(cached, cachedFavorites, "Library updated");
                        }
                    });
                }
                if (!canScan) {
                    return;
                }
                try {
                    final List<Track> fresh = repository.refreshFromDevice();
                    final Set<Long> freshFavorites = repository.loadFavoriteIds();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onLibraryLoaded(fresh, freshFavorites, "Library updated");
                        }
                    });
                } catch (SecurityException error) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onLibraryLoadFailed("Status");
                        }
                    });
                }
            }
        });
    }

    void importAudioUris(final List<Uri> uris) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                repository.importAudioUris(uris);
                final List<Track> cached = repository.loadCachedTracks();
                final Set<Long> favorites = repository.loadFavoriteIds();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLibraryLoaded(cached, favorites, "Library updated");
                    }
                });
            }
        });
    }

    void importAudioTree(final Uri treeUri) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                repository.importAudioTree(treeUri);
                final List<Track> cached = repository.loadCachedTracks();
                final Set<Long> favorites = repository.loadFavoriteIds();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLibraryLoaded(cached, favorites, "Library updated");
                    }
                });
            }
        });
    }

    void parseMissingAudioSpecs() {
        executors.io(new Runnable() {
            @Override
            public void run() {
                synchronized (LibraryActionsController.this) {
                    if (audioSpecParsingRunning) {
                        return;
                    }
                    audioSpecParsingRunning = true;
                }
                try {
                    final int updatedCount = repository.parseMissingAudioSpecs();
                    if (updatedCount <= 0) {
                        return;
                    }
                    final List<Track> cached = repository.loadCachedTracks();
                    final Set<Long> favorites = repository.loadFavoriteIds();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onAudioSpecsParsed(updatedCount, cached, favorites);
                        }
                    });
                } finally {
                    synchronized (LibraryActionsController.this) {
                        audioSpecParsingRunning = false;
                    }
                }
            }
        });
    }

    void importStreamM3u(final Uri playlistUri) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                final M3uDocumentHelper.ReadResult playlistRead = M3uDocumentHelper.readText(contentResolver, playlistUri);
                final StreamImportResult result = playlistRead.success
                        ? repository.importM3uTextWithResult(playlistRead.text)
                        : null;
                final List<Track> cached = repository.loadCachedTracks();
                final Set<Long> favorites = repository.loadFavoriteIds();
                final String status = M3uDocumentHelper.localImportStatus(playlistRead, result);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStreamM3uImported(cached, favorites, status);
                    }
                });
            }
        });
    }

    void importPlaylistM3u(final Uri playlistUri) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                final M3uDocumentHelper.ReadResult playlistRead = M3uDocumentHelper.readText(contentResolver, playlistUri);
                final PlaylistImportResult result = playlistRead.success
                        ? repository.importM3uTextAsPlaylist(
                        playlistRead.text,
                        M3uDocumentHelper.playlistFallbackName(playlistUri)
                )
                        : null;
                final List<Track> cached = repository.loadCachedTracks();
                final Set<Long> favorites = repository.loadFavoriteIds();
                final long playlistId = result != null && result.playlistId >= 0L ? result.playlistId : -1L;
                final String status = M3uDocumentHelper.playlistImportStatus(playlistRead, result);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onPlaylistImported(playlistId, cached, favorites, status);
                    }
                });
            }
        });
    }

    void exportPlaylist(final Uri exportUri, final long playlistId, final String playlistName) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                final List<Track> tracks = repository.loadPlaylistTracks(playlistId);
                final boolean exported = M3uDocumentHelper.writeText(
                        contentResolver,
                        exportUri,
                        M3uDocumentHelper.buildPlaylistText(playlistName, tracks)
                );
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onPlaylistExported(exported);
                    }
                });
            }
        });
    }

    void loadCollections(final long selectedPlaylistId) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                long playlistId = selectedPlaylistId;
                if (playlistId < 0L) {
                    playlistId = repository.ensureDefaultPlaylist();
                }
                final List<Playlist> loadedPlaylists = repository.loadPlaylists();
                boolean selectedExists = false;
                for (Playlist playlist : loadedPlaylists) {
                    if (playlist.id == playlistId) {
                        selectedExists = true;
                        break;
                    }
                }
                final long loadedPlaylistId;
                if (selectedExists) {
                    loadedPlaylistId = playlistId;
                } else if (!loadedPlaylists.isEmpty()) {
                    loadedPlaylistId = loadedPlaylists.get(0).id;
                } else {
                    loadedPlaylistId = -1L;
                }

                final Set<Long> loadedFavoriteIds = repository.loadFavoriteIds();
                final List<Track> loadedFavoriteTracks = repository.loadFavoriteTracks();
                List<TrackPlayRecord> recentRecords = repository.loadPlayedSince(
                        System.currentTimeMillis() - PLAY_HISTORY_RECAP_WINDOW_MS,
                        PLAY_HISTORY_RECAP_LIMIT
                );
                if (recentRecords.isEmpty()) {
                    recentRecords = repository.loadRecentlyPlayed(PLAY_HISTORY_RECAP_LIMIT);
                }
                final List<TrackPlayRecord> loadedRecentRecords = recentRecords;
                final List<TrackPlayRecord> loadedMostPlayedRecords = repository.loadMostPlayed(PLAY_HISTORY_RECAP_LIMIT);
                final List<RemoteSource> loadedRemoteSources = repository.loadRemoteSources();
                final List<Track> loadedPlaylistTracks = loadedPlaylistId < 0L
                        ? new ArrayList<Track>()
                        : repository.loadPlaylistTracks(loadedPlaylistId);
                final CollectionsSnapshot snapshot = new CollectionsSnapshot(
                        loadedPlaylistId,
                        loadedFavoriteIds,
                        loadedFavoriteTracks,
                        loadedRecentRecords,
                        loadedMostPlayedRecords,
                        loadedPlaylists,
                        loadedRemoteSources,
                        loadedPlaylistTracks
                );
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onCollectionsLoaded(snapshot);
                    }
                });
            }
        });
    }

    void clearPlayHistory() {
        executors.io(new Runnable() {
            @Override
            public void run() {
                final int removed = repository.clearPlayHistory();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onPlayHistoryCleared(removed);
                    }
                });
            }
        });
    }

    void setFavorite(final long trackId, final boolean favorite) {
        executors.io(new Runnable() {
            @Override
            public void run() {
                repository.setFavorite(trackId, favorite);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFavoriteSaved();
                    }
                });
            }
        });
    }
}
