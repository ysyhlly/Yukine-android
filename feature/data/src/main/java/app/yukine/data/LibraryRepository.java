package app.yukine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import app.yukine.data.room.FavoriteEntity;
import app.yukine.data.room.HistoryDao;
import app.yukine.data.room.LibraryDao;
import app.yukine.data.room.LibraryExclusionEntity;
import app.yukine.data.room.PlayHistoryEntity;
import app.yukine.data.room.PlaybackPersistenceDao;
import app.yukine.data.room.PlaylistDao;
import app.yukine.data.room.PlaylistTrackEntity;
import app.yukine.data.room.SettingEntity;
import app.yukine.data.room.SettingsDao;
import app.yukine.data.room.StreamingTrackMatchDao;
import app.yukine.data.room.StreamingTrackMatchEntity;
import app.yukine.data.room.TrackEntity;
import app.yukine.data.room.TrackEntityMapper;
import app.yukine.data.room.YukineDatabase;
import app.yukine.model.Track;

/**
 * Owns the local library and every cross-table invariant caused by replacing or deleting tracks.
 * Scan, WebDAV and streaming callers share this path so queue/index/position reconciliation cannot
 * drift between import sources.
 */
public final class LibraryRepository {
    private static final int SQLITE_IN_BATCH_SIZE = 500;
    private static final long LONG_UNPLAYED_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String QUEUE_INDEX = "playback_queue_index";
    private static final String POSITION_TRACK_ID = "playback_position_track_id";
    private static final String POSITION_MS = "playback_position_ms";

    private final YukineDatabase database;
    private final LibraryDao libraryDao;
    private final HistoryDao historyDao;
    private final PlaylistDao playlistDao;
    private final PlaybackPersistenceDao playbackDao;
    private final SettingsDao settingsDao;
    private final StreamingTrackMatchDao streamingMatchDao;
    private final PlaybackPersistenceRepository playbackPersistence;

    public LibraryRepository(YukineDatabase database) {
        this.database = database;
        libraryDao = database.libraryDao();
        historyDao = database.historyDao();
        playlistDao = database.playlistDao();
        playbackDao = database.playbackPersistenceDao();
        settingsDao = database.settingsDao();
        streamingMatchDao = database.streamingTrackMatchDao();
        playbackPersistence = new PlaybackPersistenceRepository(database);
    }

    public List<Track> loadTracks() {
        return tracks(libraryDao.loadTracks());
    }

    public List<Track> loadRecentlyAdded(int limit) {
        return limit <= 0 ? Collections.emptyList() : tracks(libraryDao.loadRecentlyAdded(limit));
    }

    public List<Track> loadLongUnplayed(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        long playedAfter = System.currentTimeMillis() - LONG_UNPLAYED_WINDOW_MS;
        return tracks(libraryDao.loadLongUnplayed(playedAfter, limit));
    }

    public List<Track> loadTracksNeedingAudioSpecs(int limit) {
        return limit <= 0
                ? Collections.emptyList()
                : tracks(libraryDao.loadTracksNeedingAudioSpecs(limit));
    }

    public List<Track> loadTracksByDataPathPattern(String pattern) {
        return tracks(libraryDao.loadTracksByDataPathPattern(pattern));
    }

    public void upsertTracks(List<Track> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        database.runInTransaction(() -> {
            Set<String> exclusions = new HashSet<>(libraryDao.loadExclusionKeys());
            libraryDao.upsertTracks(trackEntities(values, exclusions, System.currentTimeMillis()));
        });
    }

    public void replaceScanManagedTracks(List<Track> values) {
        throwIfInterrupted();
        database.runInTransaction(() -> {
            throwIfInterrupted();
            LinkedHashSet<Long> removedIds = new LinkedHashSet<>(libraryDao.loadScanManagedTrackIds());
            List<Track> queueBefore = playbackPersistence.loadQueue();
            int queueIndexBefore = playbackPersistence.loadQueueIndex();
            Set<String> exclusions = new HashSet<>(libraryDao.loadExclusionKeys());
            ArrayList<TrackEntity> replacements = trackEntities(
                    values == null ? Collections.emptyList() : values,
                    exclusions,
                    System.currentTimeMillis()
            );
            deleteTrackRows(new ArrayList<>(removedIds));
            if (!replacements.isEmpty()) {
                libraryDao.upsertTracks(replacements);
                for (TrackEntity replacement : replacements) {
                    if (replacement.getId() != null) {
                        removedIds.remove(replacement.getId());
                    }
                }
            }
            cleanupDeletedTrackReferences(
                    new ArrayList<>(removedIds),
                    queueBefore,
                    queueIndexBefore
            );
        });
    }

    public int replaceTracksByDataPathPattern(String pattern, List<Track> replacements) {
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> {
            List<Long> ids = libraryDao.loadTrackIdsByDataPathPattern(pattern);
            removed.set(deleteTrackIdsInCurrentTransaction(ids));
            if (replacements != null && !replacements.isEmpty()) {
                libraryDao.upsertTracks(trackEntities(
                        replacements,
                        Collections.emptySet(),
                        System.currentTimeMillis()
                ));
            }
        });
        return removed.get();
    }

    public int deleteTracksByDataPathPattern(String pattern) {
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> removed.set(deleteTrackIdsInCurrentTransaction(
                libraryDao.loadTrackIdsByDataPathPattern(pattern)
        )));
        return removed.get();
    }

    public int deleteTrack(long trackId) {
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> removed.set(deleteTrackIdsInCurrentTransaction(List.of(trackId))));
        return removed.get();
    }

    public int hideTracks(List<Track> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> {
            ArrayList<Long> ids = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Track track : values) {
                String sourceKey = librarySourceKey(track);
                if (sourceKey.isEmpty()) {
                    continue;
                }
                libraryDao.putExclusion(new LibraryExclusionEntity(
                        sourceKey,
                        track.contentUri == null ? "" : track.contentUri.toString(),
                        track.dataPath == null ? "" : track.dataPath,
                        now
                ));
                ids.add(track.id);
            }
            removed.set(deleteTrackIdsInCurrentTransaction(ids));
        });
        return removed.get();
    }

    public List<LibraryExclusion> loadExclusions() {
        ArrayList<LibraryExclusion> values = new ArrayList<>();
        for (LibraryExclusionEntity row : libraryDao.loadExclusions()) {
            values.add(new LibraryExclusion(
                    row.getSourceKey(),
                    row.getContentUri(),
                    row.getDataPath(),
                    row.getCreatedAt()
            ));
        }
        return values;
    }

    public boolean restoreExclusion(String sourceKey) {
        return sourceKey != null
                && !sourceKey.trim().isEmpty()
                && libraryDao.deleteExclusion(sourceKey.trim()) > 0;
    }

    public int restoreAllExclusions() {
        return libraryDao.deleteAllExclusions();
    }

    public int updateAudioSpecs(List<Track> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        AtomicInteger updated = new AtomicInteger();
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            for (Track track : values) {
                if (track == null || (!track.hasAudioSpec()
                        && Math.abs(track.replayGainTrackDb) <= 0.001f
                        && Math.abs(track.replayGainAlbumDb) <= 0.001f)) {
                    continue;
                }
                updated.addAndGet(libraryDao.updateAudioSpecs(
                        track.id,
                        track.codec,
                        track.bitrateKbps,
                        track.sampleRateHz,
                        track.bitsPerSample,
                        track.channelCount,
                        track.replayGainTrackDb,
                        track.replayGainAlbumDb,
                        now
                ));
                playbackDao.updateAudioSpecs(
                        track.id,
                        track.codec,
                        track.bitrateKbps,
                        track.sampleRateHz,
                        track.bitsPerSample,
                        track.channelCount,
                        track.replayGainTrackDb,
                        track.replayGainAlbumDb
                );
            }
        });
        return updated.get();
    }

    public boolean trackExistsByDataPath(String dataPath) {
        return dataPath != null && !dataPath.isEmpty() && libraryDao.trackCountByDataPath(dataPath) > 0;
    }

    public void setFavorite(long trackId, boolean favorite) {
        if (favorite) {
            libraryDao.putFavorite(new FavoriteEntity(trackId, System.currentTimeMillis()));
        } else {
            libraryDao.deleteFavorite(trackId);
        }
    }

    public boolean isFavorite(long trackId) {
        return libraryDao.favoriteCount(trackId) > 0;
    }

    public Set<Long> loadFavoriteIds() {
        return new HashSet<>(libraryDao.loadFavoriteIds());
    }

    public List<Track> loadFavoriteTracks() {
        return tracks(libraryDao.loadFavoriteTracks());
    }

    public String loadStreamingTrackMatch(String localKey, String provider) {
        if (localKey == null || localKey.isEmpty() || provider == null || provider.isEmpty()) {
            return "";
        }
        StreamingTrackMatchEntity match = streamingMatchDao.match(localKey, provider);
        return match == null ? "" : match.getProviderTrackId();
    }

    public void saveStreamingTrackMatch(
            String localKey,
            String provider,
            String providerTrackId,
            Track track
    ) {
        if (localKey == null || localKey.isEmpty()
                || provider == null || provider.isEmpty()
                || providerTrackId == null || providerTrackId.isEmpty()) {
            return;
        }
        streamingMatchDao.upsert(new StreamingTrackMatchEntity(
                localKey,
                provider,
                providerTrackId,
                track == null ? "" : track.title,
                track == null ? "" : track.artist,
                track == null ? "" : track.dataPath,
                System.currentTimeMillis()
        ));
    }

    public void replaceTrackAndMigrateReferences(long oldTrackId, Track replacement) {
        if (replacement == null) {
            return;
        }
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            libraryDao.upsertTracks(List.of(TrackEntityMapper.entity(replacement, now)));
            long newTrackId = replacement.id;
            if (oldTrackId == newTrackId) {
                return;
            }

            if (libraryDao.favoriteCount(oldTrackId) > 0) {
                libraryDao.putFavorite(new FavoriteEntity(newTrackId, now));
                libraryDao.deleteFavorite(oldTrackId);
            }
            migrateHistory(oldTrackId, newTrackId);
            historyDao.migrateEvents(oldTrackId, newTrackId);
            migratePlaylistReferences(oldTrackId, newTrackId, now);
            migrateQueueReferences(oldTrackId, newTrackId, replacement);
            if (playbackPersistence.loadPositionTrackId() == oldTrackId) {
                playbackPersistence.savePosition(newTrackId, playbackPersistence.loadPositionMs());
            }
            libraryDao.deleteTracksByIds(List.of(oldTrackId));
        });
    }

    static String librarySourceKey(Track track) {
        if (track == null) {
            return "";
        }
        String dataPath = track.dataPath == null ? "" : track.dataPath.trim();
        if (dataPath.startsWith("stream:")
                || dataPath.startsWith("streaming:")
                || dataPath.startsWith("webdav:")) {
            return "";
        }
        String uri = track.contentUri == null ? "" : track.contentUri.toString().trim();
        if (!uri.isEmpty()) {
            return "uri:" + uri;
        }
        return dataPath.isEmpty() ? "" : "path:" + dataPath;
    }

    private int deleteTrackIdsInCurrentTransaction(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Track> queueBefore = playbackPersistence.loadQueue();
        int queueIndexBefore = playbackPersistence.loadQueueIndex();
        int existing = 0;
        for (List<Long> batch : batches(ids)) {
            for (Long id : batch) {
                if (id != null && libraryDao.loadTrack(id) != null) {
                    existing++;
                }
            }
            libraryDao.deleteTracksByIds(batch);
        }
        cleanupDeletedTrackReferences(ids, queueBefore, queueIndexBefore);
        return existing;
    }

    private void cleanupDeletedTrackReferences(
            List<Long> ids,
            List<Track> queueBefore,
            int queueIndexBefore
    ) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        HashSet<Long> deleted = new HashSet<>(ids);
        for (List<Long> batch : batches(ids)) {
            libraryDao.deleteFavoritesByTrackIds(batch);
            historyDao.deleteHistory(batch);
            historyDao.deleteEvents(batch);
            playlistDao.deleteTrackReferences(batch);
            playbackDao.deleteTracks(batch);
        }

        ArrayList<Track> remainingQueue = new ArrayList<>();
        int removedBeforeCurrent = 0;
        for (int index = 0; index < queueBefore.size(); index++) {
            Track track = queueBefore.get(index);
            if (deleted.contains(track.id)) {
                if (queueIndexBefore >= 0 && index < queueIndexBefore) {
                    removedBeforeCurrent++;
                }
            } else {
                remainingQueue.add(track);
            }
        }
        int newIndex = -1;
        if (!remainingQueue.isEmpty() && queueIndexBefore >= 0) {
            newIndex = Math.min(
                    Math.max(queueIndexBefore - removedBeforeCurrent, 0),
                    remainingQueue.size() - 1
            );
        }
        playbackPersistence.saveQueue(remainingQueue, newIndex);
        if (deleted.contains(playbackPersistence.loadPositionTrackId())) {
            playbackPersistence.savePosition(-1L, 0L);
        }
    }

    private void migrateHistory(long oldTrackId, long newTrackId) {
        PlayHistoryEntity oldHistory = historyDao.history(oldTrackId);
        if (oldHistory == null) {
            return;
        }
        PlayHistoryEntity newHistory = historyDao.history(newTrackId);
        long playedAt = oldHistory.getPlayedAt();
        int playCount = oldHistory.getPlayCount();
        if (newHistory != null) {
            playedAt = Math.max(playedAt, newHistory.getPlayedAt());
            playCount += newHistory.getPlayCount();
        }
        historyDao.upsertHistory(new PlayHistoryEntity(newTrackId, playedAt, Math.max(playCount, 1)));
        historyDao.deleteHistory(oldTrackId);
    }

    private void migratePlaylistReferences(long oldTrackId, long newTrackId, long now) {
        for (PlaylistTrackEntity reference : playlistDao.playlistReferences(oldTrackId)) {
            playlistDao.insertPlaylistTrack(new PlaylistTrackEntity(
                    reference.getPlaylistId(),
                    newTrackId,
                    reference.getPosition(),
                    reference.getAddedAt()
            ));
            playlistDao.touchPlaylist(reference.getPlaylistId(), now);
        }
        playlistDao.deleteTrackReferences(oldTrackId);
    }

    private void migrateQueueReferences(long oldTrackId, long newTrackId, Track replacement) {
        List<Track> queue = playbackPersistence.loadQueue();
        int currentIndex = playbackPersistence.loadQueueIndex();
        boolean hasOld = false;
        boolean hasNew = false;
        for (Track track : queue) {
            hasOld |= track.id == oldTrackId;
            hasNew |= track.id == newTrackId;
        }
        if (!hasOld) {
            return;
        }
        if (!hasNew) {
            ArrayList<Track> migrated = new ArrayList<>(queue.size());
            for (Track track : queue) {
                migrated.add(track.id == oldTrackId ? replacement : track);
            }
            playbackPersistence.saveQueue(migrated, currentIndex);
            return;
        }

        int preferredIndex = -1;
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            long currentId = queue.get(currentIndex).id;
            if (currentId == oldTrackId || currentId == newTrackId) {
                preferredIndex = currentIndex;
            }
        }
        if (preferredIndex < 0) {
            for (int index = 0; index < queue.size(); index++) {
                long id = queue.get(index).id;
                if (id == oldTrackId || id == newTrackId) {
                    preferredIndex = index;
                    break;
                }
            }
        }

        ArrayList<Track> collapsed = new ArrayList<>();
        int newCurrentIndex = -1;
        boolean currentIsMerged = currentIndex >= 0
                && currentIndex < queue.size()
                && (queue.get(currentIndex).id == oldTrackId || queue.get(currentIndex).id == newTrackId);
        for (int index = 0; index < queue.size(); index++) {
            Track track = queue.get(index);
            boolean merged = track.id == oldTrackId || track.id == newTrackId;
            if (merged && index != preferredIndex) {
                continue;
            }
            if (merged) {
                track = replacement;
            }
            if (index == currentIndex || index == preferredIndex && currentIsMerged) {
                newCurrentIndex = collapsed.size();
            }
            collapsed.add(track);
        }
        if (newCurrentIndex < 0 && currentIndex >= 0 && !collapsed.isEmpty()) {
            newCurrentIndex = Math.min(Math.max(currentIndex, 0), collapsed.size() - 1);
        }
        playbackPersistence.saveQueue(collapsed, newCurrentIndex);
    }

    private static ArrayList<TrackEntity> trackEntities(
            List<Track> values,
            Set<String> exclusions,
            long updatedAt
    ) {
        ArrayList<TrackEntity> rows = new ArrayList<>();
        int index = 0;
        for (Track track : values) {
            if ((index++ & 63) == 0) {
                throwIfInterrupted();
            }
            if (exclusions.contains(librarySourceKey(track))) {
                continue;
            }
            rows.add(TrackEntityMapper.entity(track, updatedAt));
        }
        return rows;
    }

    private void deleteTrackRows(List<Long> ids) {
        for (List<Long> batch : batches(ids)) {
            libraryDao.deleteTracksByIds(batch);
        }
    }

    private static List<List<Long>> batches(List<Long> ids) {
        ArrayList<List<Long>> batches = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += SQLITE_IN_BATCH_SIZE) {
            batches.add(ids.subList(start, Math.min(ids.size(), start + SQLITE_IN_BATCH_SIZE)));
        }
        return batches;
    }

    private static List<Track> tracks(List<TrackEntity> rows) {
        ArrayList<Track> values = new ArrayList<>(rows.size());
        for (TrackEntity row : rows) {
            Track track = TrackEntityMapper.track(row);
            if (track != null) {
                values.add(track);
            }
        }
        return values;
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Library database replacement cancelled");
        }
    }
}
