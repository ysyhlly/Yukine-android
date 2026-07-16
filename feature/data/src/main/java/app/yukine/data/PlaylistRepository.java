package app.yukine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.yukine.data.room.LibraryDao;
import app.yukine.data.room.PlaylistDao;
import app.yukine.data.room.PlaylistEntity;
import app.yukine.data.room.PlaylistRow;
import app.yukine.data.room.PlaylistTrackEntity;
import app.yukine.data.room.PlaylistRecordingItemEntity;
import app.yukine.data.room.MusicIdentityDao;
import app.yukine.data.room.TrackSourceMappingEntity;
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
    private final OfflineMusicIdentityStore musicIdentityStore;
    private final MusicIdentityDao identityDao;

    public PlaylistRepository(YukineDatabase database) {
        this.database = database;
        dao = database.playlistDao();
        libraryDao = database.libraryDao();
        musicIdentityStore = new OfflineMusicIdentityStore(database.musicIdentityDao());
        identityDao = database.musicIdentityDao();
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
            dao.deletePlaylistRecordingItems(playlistId);
            dao.deletePlaylistTracks(playlistId);
            if (dao.deletePlaylist(playlistId) > 0) {
                if (!orphaned.isEmpty()) {
                    libraryDao.deleteTracksByIds(orphaned);
                    musicIdentityStore.pruneMissingTracks();
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
            TrackSourceMappingEntity source = identityDao.sourceForLocalTrack(trackId);
            if (source != null) {
                long canonicalInserted = dao.insertPlaylistRecordingItem(new PlaylistRecordingItemEntity(
                        playlistId,
                        source.getRecordingId(),
                        trackId,
                        dao.nextRecordingSortKey(playlistId),
                        now
                ));
                if (canonicalInserted == -1L) {
                    return;
                }
            }
            long inserted = dao.insertPlaylistTrack(new PlaylistTrackEntity(
                    playlistId,
                    trackId,
                    dao.nextPosition(playlistId),
                    now
            ));
            if (inserted != -1L || source != null) {
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
            TrackSourceMappingEntity source = identityDao.sourceForLocalTrack(trackId);
            int canonicalRemoved = source == null
                    ? 0
                    : dao.removePlaylistRecordingItem(playlistId, source.getRecordingId());
            int legacyRemoved = canonicalRemoved > 0
                    ? dao.removeLegacyPlaylistRecordingSources(playlistId, source.getRecordingId())
                    : dao.removePlaylistTrack(playlistId, trackId);
            if (legacyRemoved > 0 || canonicalRemoved > 0) {
                dao.touchPlaylist(playlistId, System.currentTimeMillis());
                removed.set(true);
            }
        });
        return removed.get();
    }

    public void clearTracks(long playlistId) {
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) > 0) {
                dao.deletePlaylistRecordingItems(playlistId);
                dao.deletePlaylistTracks(playlistId);
                dao.touchPlaylist(playlistId, System.currentTimeMillis());
            }
        });
    }

    /**
     * Replaces a complete playlist in one transaction. Canonical identity is resolved in bounded
     * bulk queries so a large remote playlist does not open one transaction and run one identity
     * lookup per song.
     */
    public int replaceTracks(long playlistId, List<Long> trackIds) {
        if (trackIds == null) {
            return 0;
        }
        AtomicInteger replaced = new AtomicInteger(0);
        database.runInTransaction(() -> {
            if (dao.playlistExists(playlistId) == 0) {
                return;
            }
            long now = System.currentTimeMillis();
            Map<Long, TrackSourceMappingEntity> sourcesByTrackId = new HashMap<>();
            ArrayList<Long> queryIds = new ArrayList<>();
            Set<Long> seenQueryIds = new HashSet<>();
            for (Long trackId : trackIds) {
                if (trackId != null && seenQueryIds.add(trackId)) {
                    queryIds.add(trackId);
                }
            }
            for (int start = 0; start < queryIds.size(); start += IDENTITY_QUERY_CHUNK_SIZE) {
                int end = Math.min(start + IDENTITY_QUERY_CHUNK_SIZE, queryIds.size());
                for (TrackSourceMappingEntity source : identityDao.sourcesForLocalTracks(
                        queryIds.subList(start, end))) {
                    Long localTrackId = source.getLocalTrackId();
                    if (localTrackId != null) {
                        sourcesByTrackId.put(localTrackId, source);
                    }
                }
            }

            ArrayList<PlaylistTrackEntity> legacyRows = new ArrayList<>();
            ArrayList<PlaylistRecordingItemEntity> canonicalRows = new ArrayList<>();
            Set<Long> acceptedTrackIds = new HashSet<>();
            Set<Long> acceptedRecordingIds = new HashSet<>();
            int position = 0;
            for (Long trackId : trackIds) {
                if (trackId == null || !acceptedTrackIds.add(trackId)) {
                    continue;
                }
                TrackSourceMappingEntity source = sourcesByTrackId.get(trackId);
                if (source != null && !acceptedRecordingIds.add(source.getRecordingId())) {
                    continue;
                }
                legacyRows.add(new PlaylistTrackEntity(playlistId, trackId, position, now));
                if (source != null) {
                    canonicalRows.add(new PlaylistRecordingItemEntity(
                            playlistId,
                            source.getRecordingId(),
                            trackId,
                            (position + 1L) * PLAYLIST_SORT_KEY_GAP,
                            now
                    ));
                }
                position++;
            }

            dao.deletePlaylistRecordingItems(playlistId);
            dao.replacePlaylistTracks(playlistId, legacyRows);
            if (!canonicalRows.isEmpty()) {
                dao.insertPlaylistRecordingItems(canonicalRows);
            }
            dao.touchPlaylist(playlistId, now);
            replaced.set(position);
        });
        return replaced.get();
    }

    public boolean moveTrack(long playlistId, long trackId, int direction) {
        if (direction == 0) {
            return false;
        }
        TrackSourceMappingEntity source = identityDao.sourceForLocalTrack(trackId);
        List<PlaylistRecordingItemEntity> canonicalRows = dao.playlistRecordingRows(playlistId);
        if (source != null && !canonicalRows.isEmpty()) {
            int canonicalIndex = -1;
            for (int i = 0; i < canonicalRows.size(); i++) {
                if (canonicalRows.get(i).getRecordingId() == source.getRecordingId()) {
                    canonicalIndex = i;
                    break;
                }
            }
            return moveAt(playlistId, canonicalIndex, direction);
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
            List<PlaylistRecordingItemEntity> canonicalRows = dao.playlistRecordingRows(playlistId);
            if (!canonicalRows.isEmpty()) {
                if (trackIndex >= canonicalRows.size() || neighborIndex >= canonicalRows.size()) {
                    return;
                }
                PlaylistRecordingItemEntity current = canonicalRows.get(trackIndex);
                PlaylistRecordingItemEntity neighbor = canonicalRows.get(neighborIndex);
                dao.updateRecordingSortKey(playlistId, current.getRecordingId(), neighbor.getSortKey());
                dao.updateRecordingSortKey(playlistId, neighbor.getRecordingId(), current.getSortKey());
                dao.touchPlaylist(playlistId, System.currentTimeMillis());
                moved.set(true);
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
        List<TrackEntity> rows = dao.playlistRecordingTracks(playlistId);
        if (rows.isEmpty()) {
            rows = dao.playlistTracks(playlistId);
        }
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

    private static final int IDENTITY_QUERY_CHUNK_SIZE = 900;
    private static final long PLAYLIST_SORT_KEY_GAP = 1024L;

    private static String cleanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "未命名播放列表";
        }
        return name.trim();
    }
}
