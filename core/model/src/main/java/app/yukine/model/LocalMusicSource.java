package app.yukine.model;

import java.util.Locale;

/** Persisted local document source exposed to onboarding and settings. */
public final class LocalMusicSource {
    public static final String TYPE_FOLDER = "FOLDER";
    public static final String TYPE_FILE = "FILE";
    public static final String TYPE_LEGACY = "LEGACY";

    public static final String STATUS_READY = "READY";
    public static final String STATUS_SCANNING = "SCANNING";
    public static final String STATUS_ACCESS_LOST = "ACCESS_LOST";
    public static final String STATUS_FAILED = "FAILED";

    private final String sourceId;
    private final String type;
    private final String rootUri;
    private final String displayName;
    private final String status;
    private final int trackCount;
    private final long addedAt;
    private final long lastScanAt;
    private final long updatedAt;

    public LocalMusicSource(
            String sourceId,
            String type,
            String rootUri,
            String displayName,
            String status,
            int trackCount,
            long addedAt,
            long lastScanAt,
            long updatedAt
    ) {
        this.sourceId = safe(sourceId);
        this.type = safe(type).toUpperCase(Locale.ROOT);
        this.rootUri = safe(rootUri);
        this.displayName = safe(displayName);
        this.status = safe(status).toUpperCase(Locale.ROOT);
        this.trackCount = Math.max(0, trackCount);
        this.addedAt = Math.max(0L, addedAt);
        this.lastScanAt = Math.max(0L, lastScanAt);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public String sourceId() {
        return sourceId;
    }

    public String type() {
        return type;
    }

    public String rootUri() {
        return rootUri;
    }

    public String displayName() {
        return displayName;
    }

    public String status() {
        return status;
    }

    public int trackCount() {
        return trackCount;
    }

    public long addedAt() {
        return addedAt;
    }

    public long lastScanAt() {
        return lastScanAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public boolean needsAuthorization() {
        return STATUS_ACCESS_LOST.equals(status);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
