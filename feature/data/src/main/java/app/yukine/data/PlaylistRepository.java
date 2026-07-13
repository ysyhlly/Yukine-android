package app.yukine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import app.yukine.data.room.LibraryDao;
import app.yukine.data.room.PlaylistDao;
import app.yukine.data.room.PlaylistEntity;
import app.yukine.data.room.PlaylistRow;
import app.yukine.data.room.PlaylistTrackEntity;
import app.yukine.data.room.TrackEntity;
import app.yukine.data.room.TrackEntityMapper;
import app.yukine.data.room.YukineDatabase;
import app.yukine.model.Playlist;
import app.yukine.model.Track;

/** Playlist order and placeholder-lifecycle owner. */
public final class PlaylistRepository {
    private final YukineDatabase database;
    private final PlaylistDao dao;
    private final LibraryDao libraryDao;

    public PlaylistRepository(YukineDatabase database) {
        this.database = database;
        dao = database.playlistDao();
        libraryDao = database.libraryDao();
    }

    public long create(String name) {
        long now = System.currentTimeMillis();
        return dao.insertPlaylist(new PlaylistEntity(null, cleanName(name), now, now));
    }

    public boolean rename(long playlistId, String name) {
        return dao.renamePlaylist(playlistId, cleanName(name), System.currentTimeMillis()) > 0;
    }

    public boolean delete(long playlistId) {
        AtomicBoolean deleted = new AtomicBoolean(false);
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) == 0) {
                return;
            }
            List<Long> orphaned = dao.orphanedPlaceholderTrackIds(playlistId);
            dao.deletePlaylistTracks(playlistId);
            if (dao.deletePlaylist(playlistId) > 0) {
                if (!orphaned.isEmpty()) {
                    libraryDao.deleteTracksByIds(orphaned);
                }
                deleted.set(true);
            }
        });
        return deleted.get();
    }

    public List<Playlist> loadPlaylists() {
        ArrayList<Playlist> playlists = new ArrayList<>();
        for (PlaylistRow row : dao.loadPlaylistRows()) {
            playlists.add(new Playlist(
                    row.getId(),
                    row.getName(),
                    row.getTrackCount(),
                    row.getCreatedAt(),
                    row.getUpdatedAt()
            ));
        }
        return playlists;
    }

    public boolean addTrack(long playlistId, long trackId) {
        AtomicBoolean added = new AtomicBoolean(false);
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) == 0) {
                return;
            }
            long now = System.currentTimeMillis();
            long inserted = dao.insertPlaylistTrack(new PlaylistTrackEntity(
                    playlistId,
                    trackId,
                    dao.nextPosition(playlistId),
                    now
            ));
            if (inserted != -1L) {
                dao.touchPlaylist(playlistId, now);
                added.set(true);
            }
        });
        return added.get();
    }

    public boolean removeTrack(long playlistId, long trackId) {
        AtomicBoolean removed = new AtomicBoolean(false);
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) == 0) {
                return;
            }
            if (dao.removePlaylistTrack(playlistId, trackId) > 0) {
                dao.touchPlaylist(playlistId, System.currentTimeMillis());
                removed.set(true);
            }
        });
        return removed.get();
    }

    public void clearTracks(long playlistId) {
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) > 0) {
                dao.deletePlaylistTracks(playlistId);
                dao.touchPlaylist(playlistId, System.currentTimeMillis());
            }
        });
    }

    public boolean moveTrack(long playlistId, long trackId, int direction) {
        if (direction == 0) {
            return false;
        }
        List<PlaylistTrackEntity> rows = dao.playlistTrackRows(playlistId);
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getTrackId() == trackId) {
                index = i;
                break;
            }
        }
        return moveAt(playlistId, index, direction);
    }

    public boolean moveAt(long playlistId, int trackIndex, int direction) {
        int neighborIndex = trackIndex + direction;
        if (trackIndex < 0 || neighborIndex < 0 || direction == 0) {
            return false;
        }
        AtomicBoolean moved = new AtomicBoolean(false);
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) == 0) {
                return;
            }
            List<PlaylistTrackEntity> rows = dao.playlistTrackRows(playlistId);
            if (trackIndex >= rows.size() || neighborIndex >= rows.size()) {
                return;
            }
            PlaylistTrackEntity current = rows.get(trackIndex);
            PlaylistTrackEntity neighbor = rows.get(neighborIndex);
            dao.updateTrackPosition(playlistId, current.getTrackId(), neighbor.getPosition());
            dao.updateTrackPosition(playlistId, neighbor.getTrackId(), current.getPosition());
            dao.touchPlaylist(playlistId, System.currentTimeMillis());
            moved.set(true);
        });
        return moved.get();
    }

    public List<Track> loadTracks(long playlistId) {
        List<TrackEntity> rows = dao.playlistTracks(playlistId);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Track> tracks = new ArrayList<>(rows.size());
        for (TrackEntity row : rows) {
            Track track = TrackEntityMapper.track(row);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "未命名播放列表";
        }
        return name.trim();
    }
}
