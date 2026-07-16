package app.yukine.data.room;

import java.util.Locale;

/** Deterministic provider identity for one persisted track row; never performs network work. */
public final class TrackSourceIdentity {
    public final String provider;
    public final String providerTrackId;

    private TrackSourceIdentity(String provider, String providerTrackId) {
        this.provider = provider;
        this.providerTrackId = providerTrackId;
    }

    public static TrackSourceIdentity from(long trackId, String contentUri, String dataPath) {
        String path = clean(dataPath);
        String uri = clean(contentUri);
        if (path.regionMatches(true, 0, "streaming:", 0, "streaming:".length())) {
            String remainder = path.substring("streaming:".length());
            int separator = remainder.indexOf(':');
            if (separator > 0 && separator + 1 < remainder.length()) {
                String provider = remainder.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String providerTrackId = stripQuery(remainder.substring(separator + 1));
                if (!provider.isEmpty() && !providerTrackId.isEmpty()) {
                    return new TrackSourceIdentity(provider, providerTrackId);
                }
            }
            return new TrackSourceIdentity("streaming", path);
        }
        if (path.regionMatches(true, 0, "webdav:", 0, "webdav:".length())) {
            return new TrackSourceIdentity("webdav", path);
        }
        if (path.regionMatches(true, 0, "document:", 0, "document:".length())) {
            return new TrackSourceIdentity("document", uri.isEmpty() ? path : uri);
        }
        if (path.regionMatches(true, 0, "stream:", 0, "stream:".length())) {
            return new TrackSourceIdentity("stream", path);
        }
        String localId = path.isEmpty() ? uri : path;
        if (localId.isEmpty()) {
            localId = "track:" + trackId;
        }
        return new TrackSourceIdentity("local", localId);
    }

    private static String stripQuery(String value) {
        String clean = clean(value);
        int query = clean.indexOf('?');
        return query < 0 ? clean : clean.substring(0, query).trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
