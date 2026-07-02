package app.yukine.model;

public final class TrackPlayRecord {
    public final Track track;
    public final long playedAt;
    public final int playCount;

    public TrackPlayRecord(Track track, long playedAt, int playCount) {
        this.track = track;
        this.playedAt = Math.max(playedAt, 0L);
        this.playCount = Math.max(playCount, 0);
    }
}
