package app.yukine.data;

public final class LibraryExclusion {
    public final String sourceKey;
    public final String contentUri;
    public final String dataPath;
    public final long createdAt;

    public LibraryExclusion(String sourceKey, String contentUri, String dataPath, long createdAt) {
        this.sourceKey = sourceKey == null ? "" : sourceKey;
        this.contentUri = contentUri == null ? "" : contentUri;
        this.dataPath = dataPath == null ? "" : dataPath;
        this.createdAt = Math.max(0L, createdAt);
    }

    public String displayName() {
        String value = !dataPath.isEmpty() ? dataPath : contentUri;
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
    }
}
