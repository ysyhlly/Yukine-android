package app.yukine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.yukine.model.RemoteSource;
import app.yukine.model.Track;

final class NetworkLibrary {
    private static final String STREAM_PREFIX = "stream:";
    private static final String WEBDAV_PREFIX = "webdav:";

    private NetworkLibrary() {
    }

    static String remoteSourceSubtitle(RemoteSource source, List<Track> tracks) {
        return remoteSourceSubtitle(source, tracks, AppLanguage.MODE_ENGLISH);
    }

    static String remoteSourceSubtitle(RemoteSource source, List<Track> tracks, String languageMode) {
        String type = safe(source.type).toUpperCase(Locale.ROOT);
        String location = joinUrlPath(source.baseUrl, source.rootPath);
        if (RemoteSource.TYPE_WEBDAV.equalsIgnoreCase(source.type)) {
            return type + " - " + AppLanguage.text(languageMode, "synced") + " "
                    + webDavTracksForSource(tracks, source.id).size() + " "
                    + AppLanguage.text(languageMode, "tracks") + " - " + location;
        }
        return type + " - " + location;
    }

    static int streamTrackCount(List<Track> tracks) {
        return streamTracks(tracks).size();
    }

    static ArrayList<Track> streamTracks(List<Track> tracks) {
        ArrayList<Track> streams = new ArrayList<>();
        for (Track track : tracks) {
            if (track.dataPath.startsWith(STREAM_PREFIX)) {
                streams.add(track);
            }
        }
        return streams;
    }

    static int webDavSourceCount(List<RemoteSource> sources) {
        int count = 0;
        for (RemoteSource source : sources) {
            if (RemoteSource.TYPE_WEBDAV.equalsIgnoreCase(source.type)) {
                count++;
            }
        }
        return count;
    }

    static ArrayList<Track> webDavTracks(List<Track> tracks) {
        ArrayList<Track> webDavTracks = new ArrayList<>();
        for (Track track : tracks) {
            if (track.dataPath.startsWith(WEBDAV_PREFIX)) {
                webDavTracks.add(track);
            }
        }
        return webDavTracks;
    }

    static ArrayList<Track> webDavTracksForSource(List<Track> tracks, long sourceId) {
        ArrayList<Track> sourceTracks = new ArrayList<>();
        String prefix = WEBDAV_PREFIX + sourceId + ":";
        for (Track track : tracks) {
            if (track.dataPath.startsWith(prefix)) {
                sourceTracks.add(track);
            }
        }
        return sourceTracks;
    }

    static ArrayList<String> streamTrackDetails(List<Track> tracks) {
        ArrayList<String> details = new ArrayList<>();
        for (Track track : tracks) {
            details.add(track.dataPath.startsWith(STREAM_PREFIX)
                    ? track.dataPath.substring(STREAM_PREFIX.length())
                    : track.contentUri.toString());
        }
        return details;
    }

    static ArrayList<String> webDavTrackDetails(List<Track> tracks, List<RemoteSource> sources) {
        return webDavTrackDetails(tracks, sources, AppLanguage.MODE_ENGLISH);
    }

    static ArrayList<String> webDavTrackDetails(List<Track> tracks, List<RemoteSource> sources, String languageMode) {
        ArrayList<String> details = new ArrayList<>();
        for (Track track : tracks) {
            details.add(webDavTrackDetail(track, sources, languageMode));
        }
        return details;
    }

    static RemoteSource selectedRemoteSource(List<RemoteSource> sources, long selectedRemoteSourceId) {
        for (RemoteSource source : sources) {
            if (source.id == selectedRemoteSourceId) {
                return source;
            }
        }
        return null;
    }

    static String remoteSourceName(List<RemoteSource> sources, long sourceId) {
        return remoteSourceName(sources, sourceId, AppLanguage.MODE_ENGLISH);
    }

    static String remoteSourceName(List<RemoteSource> sources, long sourceId, String languageMode) {
        for (RemoteSource source : sources) {
            if (source.id == sourceId) {
                return source.name;
            }
        }
        return AppLanguage.text(languageMode, "remote.source.default");
    }

    private static String webDavTrackDetail(Track track, List<RemoteSource> sources, String languageMode) {
        String dataPath = track.dataPath == null ? "" : track.dataPath;
        if (!dataPath.startsWith(WEBDAV_PREFIX)) {
            return dataPath;
        }
        String remainder = dataPath.substring(WEBDAV_PREFIX.length());
        int separator = remainder.indexOf(':');
        if (separator <= 0) {
            return remainder;
        }
        long sourceId;
        try {
            sourceId = Long.parseLong(remainder.substring(0, separator));
        } catch (NumberFormatException ignored) {
            return remainder;
        }
        String path = remainder.substring(separator + 1);
        return remoteSourceName(sources, sourceId, languageMode) + " - " + path;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String joinUrlPath(String baseUrl, String path) {
        String base = trimRight(safe(baseUrl), "/");
        String cleanPath = trimLeft(safe(path), "/");
        if (cleanPath.isEmpty()) {
            return base;
        }
        return base + "/" + cleanPath;
    }

    private static String trimRight(String value, String suffix) {
        String current = value.trim();
        while (current.endsWith(suffix)) {
            current = current.substring(0, current.length() - suffix.length());
        }
        return current;
    }

    private static String trimLeft(String value, String prefix) {
        String current = value.trim();
        while (current.startsWith(prefix)) {
            current = current.substring(prefix.length());
        }
        return current;
    }
}
