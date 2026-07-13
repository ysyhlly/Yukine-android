package app.yukine.data;

import java.util.ArrayList;
import java.util.List;

import app.yukine.data.room.HistoryDao;
import app.yukine.data.room.TrackEntityMapper;
import app.yukine.data.room.TrackPlayRecordRow;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;

/** Playback history and event-window owner. */
public final class HistoryRepository {
    private final HistoryDao dao;

    public HistoryRepository(HistoryDao dao) {
        this.dao = dao;
    }

    public void markPlayed(long trackId) {
        dao.recordPlay(trackId, System.currentTimeMillis());
    }

    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
        return records(dao.recentlyPlayed(Math.max(limit, 1)));
    }

    public List<TrackPlayRecord> loadMostPlayed(int limit) {
        return records(dao.mostPlayed(Math.max(limit, 1)));
    }

    public List<TrackPlayRecord> loadPlayedSince(long startMs, int limit) {
        return records(dao.playedSince(Math.max(0L, startMs), Math.max(limit, 1)));
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
