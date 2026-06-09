package app.echo.next.model;

public final class Playlist {
    public final long id;
    public final String name;
    public final int trackCount;
    public final long createdAt;
    public final long updatedAt;

    public Playlist(long id, String name, int trackCount, long createdAt, long updatedAt) {
        this.id = id;
        this.name = clean(name);
        this.trackCount = Math.max(trackCount, 0);
        this.createdAt = Math.max(createdAt, 0L);
        this.updatedAt = Math.max(updatedAt, 0L);
    }

    private static String clean(String value) {
        if (value == null) {
            return "未命名播放列表";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "未命名播放列表" : trimmed;
    }
}
