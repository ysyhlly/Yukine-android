package app.yukine.data;

import java.util.ArrayList;
import java.util.List;

import app.yukine.data.room.HistoryDao;
import app.yukine.data.room.MusicIdentityDao;
import app.yukine.data.room.TrackSourceMappingEntity;
import app.yukine.data.room.YukineDatabase;
import app.yukine.data.room.TrackEntityMapper;
import app.yukine.data.room.TrackPlayRecordRow;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;

/** Playback history and event-window owner. */
public final class HistoryRepository {
    private final HistoryDao dao;
    private final MusicIdentityDao identityDao;

    public HistoryRepository(HistoryDao dao) {
        this.dao = dao;
        this.identityDao = null;
    }

    public HistoryRepository(YukineDatabase database) {
        this.dao = database.historyDao();
        this.identityDao = database.musicIdentityDao();
    }

    public void markPlayed(long trackId) {
        long now = System.currentTimeMillis();
        TrackSourceMappingEntity source = identityDao == null ? null : identityDao.sourceForLocalTrack(trackId);
        if (source == null || source.getSourceId() == null) {
            dao.recordPlay(trackId, now);
        } else {
            dao.recordCanonicalPlay(trackId, source.getRecordingId(), source.getSourceId(), now);
        }
    }

    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
        int safeLimit = Math.max(limit, 1);
        List<TrackPlayRecordRow> canonical = dao.canonicalRecentlyPlayed(safeLimit);
        return records(canonical.isEmpty() ? dao.recentlyPlayed(safeLimit) : canonical);
    }

    public List<TrackPlayRecord> loadMostPlayed(int limit) {
        int safeLimit = Math.max(limit, 1);
        List<TrackPlayRecordRow> canonical = dao.canonicalMostPlayed(safeLimit);
        return records(canonical.isEmpty() ? dao.mostPlayed(safeLimit) : canonical);
    }

    public List<TrackPlayRecord> loadPlayedSince(long startMs, int limit) {
        long safeStart = Math.max(0L, startMs);
        int safeLimit = Math.max(limit, 1);
        List<TrackPlayRecordRow> canonical = dao.canonicalPlayedSince(safeStart, safeLimit);
        return records(canonical.isEmpty() ? dao.playedSince(safeStart, safeLimit) : canonical);
    }

    public int clear() {
        return dao.clearHistory();
    }

    private static List<TrackPlayRecord> records(List<TrackPlayRecordRow> rows) {
        ArrayList<TrackPlayRecord> records = new ArrayList<>(rows.size());
        for (TrackPlayRecordRow row : rows) {
            Track track = TrackEntityMapper.track(row.getTrack());
            if (track != null) {
                records.add(new TrackPlayRecord(track, row.getPlayedAt(), row.getPlayCount()));
            }
        }
        return records;
    }
}
