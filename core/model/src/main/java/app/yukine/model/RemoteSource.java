package app.yukine.model;

public final class RemoteSource {
    public static final String TYPE_WEBDAV = "webdav";

    public final long id;
    public final String type;
    public final String name;
    public final String baseUrl;
    public final String username;
    public final String password;
    public final String rootPath;
    public final boolean allowInsecureTls;
    public final String lastStatus;
    public final long updatedAt;

    public RemoteSource(
            long id,
            String type,
            String name,
            String baseUrl,
            String username,
            String password,
            String rootPath,
            String lastStatus,
            long updatedAt
    ) {
        this(
                id, type, name, baseUrl, username, password, rootPath, false, lastStatus, updatedAt
        );
    }

    public RemoteSource(
            long id,
            String type,
            String name,
            String baseUrl,
            String username,
            String password,
            String rootPath,
            boolean allowInsecureTls,
            String lastStatus,
            long updatedAt
    ) {
        this.id = id;
        this.type = clean(type, TYPE_WEBDAV);
        this.name = clean(name, "WebDAV");
        this.baseUrl = clean(baseUrl, "");
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.rootPath = normalizeRoot(rootPath);
        this.allowInsecureTls = allowInsecureTls;
        this.lastStatus = lastStatus == null ? "" : lastStatus.trim();
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public boolean hasAuth() {
        return !username.isEmpty() && !password.isEmpty();
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String normalizeRoot(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }
}
