package app.yukine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.yukine.data.room.LibraryDao;
import app.yukine.data.room.PlaybackPersistenceDao;
import app.yukine.data.room.PlaybackQueueEntity;
import app.yukine.data.room.SettingEntity;
import app.yukine.data.room.SettingsDao;
import app.yukine.data.room.TrackEntity;
import app.yukine.data.room.TrackEntityMapper;
import app.yukine.data.room.YukineDatabase;
import app.yukine.model.Track;

/** Atomic queue, current-index and playback-position persistence. */
public final class PlaybackPersistenceRepository {
    private static final int SQLITE_IN_BATCH_SIZE = 500;
    private static final String QUEUE_INDEX = "playback_queue_index";
    private static final String POSITION_TRACK_ID = "playback_position_track_id";
    private static final String POSITION_MS = "playback_position_ms";

    private final YukineDatabase database;
    private final PlaybackPersistenceDao playbackDao;
    private final SettingsDao settingsDao;
    private final LibraryDao libraryDao;

    public PlaybackPersistenceRepository(YukineDatabase database) {
        this.database = database;
        playbackDao = database.playbackPersistenceDao();
        settingsDao = database.settingsDao();
        libraryDao = database.libraryDao();
    }

    public List<Track> loadQueue() {
        List<PlaybackQueueEntity> rows = playbackDao.loadQueue();
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Long> ids = new ArrayList<>(rows.size());
        for (PlaybackQueueEntity row : rows) {
            ids.add(row.getTrackId());
        }
        Map<Long, TrackEntity> fallbacks = new HashMap<>();
        for (int start = 0; start < ids.size(); start += SQLITE_IN_BATCH_SIZE) {
            int end = Math.min(ids.size(), start + SQLITE_IN_BATCH_SIZE);
            for (TrackEntity track : libraryDao.loadTracksByIds(ids.subList(start, end))) {
                if (track.getId() != null) {
                    fallbacks.put(track.getId(), track);
                }
            }
        }
        ArrayList<Track> tracks = new ArrayList<>(rows.size());
        for (PlaybackQueueEntity row : rows) {
            Track track = TrackEntityMapper.queueTrack(row, fallbacks.get(row.getTrackId()));
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    public void saveQueue(List<Track> tracks, int currentIndex) {
        ArrayList<PlaybackQueueEntity> rows = new ArrayList<>();
        if (tracks != null) {
            for (int index = 0; index < tracks.size(); index++) {
                rows.add(TrackEntityMapper.queueEntity(tracks.get(index), index));
            }
        }
        database.runInTransaction(() -> {
            playbackDao.replaceQueue(rows);
            settingsDao.put(new SettingEntity(QUEUE_INDEX, String.valueOf(currentIndex)));
        });
    }

    public int loadQueueIndex() {
        try {
            String value = settingsDao.value(QUEUE_INDEX);
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public long loadPositionTrackId() {
        try {
            String value = settingsDao.value(POSITION_TRACK_ID);
            return value == null ? -1L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    public long loadPositionMs() {
        try {
            String value = settingsDao.value(POSITION_MS);
            return value == null ? 0L : Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public void savePosition(long trackId, long positionMs) {
        database.runInTransaction(() -> settingsDao.putAll(List.of(
                new SettingEntity(POSITION_TRACK_ID, String.valueOf(trackId)),
                new SettingEntity(POSITION_MS, String.valueOf(Math.max(0L, positionMs)))
        )));
    }
}
