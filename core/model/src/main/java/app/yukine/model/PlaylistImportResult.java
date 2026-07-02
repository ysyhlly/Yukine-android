package app.yukine.model;

public final class PlaylistImportResult {
    public final long playlistId;
    public final String playlistName;
    public final int candidateCount;
    public final int streamAddedCount;
    public final int playlistAddedCount;
    public final int duplicateCount;

    public PlaylistImportResult(
            long playlistId,
            String playlistName,
            int candidateCount,
            int streamAddedCount,
            int playlistAddedCount,
            int duplicateCount
    ) {
        this.playlistId = playlistId;
        this.playlistName = playlistName == null ? "" : playlistName;
        this.candidateCount = Math.max(0, candidateCount);
        this.streamAddedCount = Math.max(0, streamAddedCount);
        this.playlistAddedCount = Math.max(0, playlistAddedCount);
        this.duplicateCount = Math.max(0, duplicateCount);
    }

    public boolean isEmpty() {
        return candidateCount == 0;
    }
}
