package app.echo.next.data;

import android.net.Uri;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import app.echo.next.model.Track;

public final class M3uPlaylistParser {
    private M3uPlaylistParser() {
    }

    public static List<Track> parse(String playlistText, String playlistUrl) {
        return parse(playlistText, playlistUrl, false);
    }

    public static List<Track> parse(String playlistText, String playlistUrl, boolean allowLocalEntries) {
        if (playlistText == null || playlistText.trim().isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Track> tracks = new ArrayList<>();
        HashSet<String> seenUrls = new HashSet<>();
        HashSet<String> seenLocalEntries = new HashSet<>();
        boolean hlsPlaylist = false;
        String cleanPlaylistUrl = normalizeStreamUrl(playlistUrl);
        String pendingTitle = "";
        String[] lines = playlistText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = stripBom(line.trim());
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isHlsTag(trimmed)) {
                hlsPlaylist = true;
                continue;
            }
            if (isExtInfTag(trimmed)) {
                pendingTitle = extInfTitle(trimmed);
            } else if (!trimmed.startsWith("#")) {
                String streamUrl = normalizePlaylistEntry(cleanPlaylistUrl, trimmed);
                if (!hlsPlaylist && isHttpUrl(streamUrl) && seenUrls.add(streamUrl)) {
                    tracks.add(streamTrack(streamTitle(pendingTitle, streamUrl), streamUrl));
                } else if (!hlsPlaylist && allowLocalEntries && !isHttpUrl(trimmed) && seenLocalEntries.add(trimmed)) {
                    tracks.add(localReferenceTrack(streamTitle(pendingTitle, trimmed), trimmed));
                }
                pendingTitle = "";
            }
        }
        if (hlsPlaylist && isHttpUrl(cleanPlaylistUrl)) {
            tracks.clear();
            tracks.add(streamTrack(streamTitle("", cleanPlaylistUrl), cleanPlaylistUrl));
        }
        return tracks;
    }

    public static String playlistName(String playlistText, String fallback) {
        if (playlistText != null) {
            String[] lines = playlistText.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = stripBom(line.trim());
                if (isPlaylistTag(trimmed)) {
                    String name = trimmed.substring("#PLAYLIST:".length()).trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
        }
        return fallback == null || fallback.trim().isEmpty() ? "导入的播放列表" : fallback.trim();
    }

    static Track streamTrack(String title, String cleanUrl) {
        return new Track(
                stableNegativeId("stream:" + cleanUrl),
                title,
                "流媒体",
                "网络",
                0L,
                Uri.parse(cleanUrl),
                "stream:" + cleanUrl,
                0L,
                null
        );
    }

    static Track localReferenceTrack(String title, String location) {
        String cleanLocation = location == null ? "" : location.trim();
        return new Track(
                stableNegativeId("playlist-local:" + cleanLocation),
                title,
                "本地引用",
                "播放列表",
                0L,
                Uri.parse(cleanLocation),
                cleanLocation,
                0L,
                null
        );
    }

    private static String extInfTitle(String line) {
        int comma = line.lastIndexOf(',');
        if (comma < 0 || comma >= line.length() - 1) {
            return "";
        }
        return line.substring(comma + 1).trim();
    }

    private static boolean isHlsTag(String line) {
        return line != null && line.toUpperCase(Locale.ROOT).startsWith("#EXT-X-");
    }

    private static boolean isExtInfTag(String line) {
        return line != null && line.toUpperCase(Locale.ROOT).startsWith("#EXTINF");
    }

    private static boolean isPlaylistTag(String line) {
        return line != null && line.toUpperCase(Locale.ROOT).startsWith("#PLAYLIST:");
    }

    private static String stripBom(String value) {
        if (value == null || value.isEmpty() || value.charAt(0) != '\uFEFF') {
            return value;
        }
        return value.substring(1).trim();
    }

    private static String streamTitle(String title, String url) {
        if (title != null && !title.trim().isEmpty()) {
            return title.trim();
        }
        Uri uri = Uri.parse(url);
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment == null || lastSegment.trim().isEmpty()) {
            return "网络流媒体";
        }
        return lastSegment.trim();
    }

    static String normalizeStreamUrl(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.trim();
        if (!isHttpUrl(clean)) {
            return clean;
        }
        try {
            URL url = new URL(clean);
            String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            String userInfo = url.getUserInfo();
            int port = url.getPort();
            String file = url.getFile();
            String ref = url.getRef();
            StringBuilder normalized = new StringBuilder();
            normalized.append(protocol).append("://");
            if (userInfo != null && !userInfo.isEmpty()) {
                normalized.append(userInfo).append('@');
            }
            normalized.append(host);
            if (port >= 0 && !isDefaultPort(protocol, port)) {
                normalized.append(':').append(port);
            }
            normalized.append(file == null || file.isEmpty() ? "/" : file);
            if (ref != null && !ref.isEmpty()) {
                normalized.append('#').append(ref);
            }
            return normalized.toString();
        } catch (Exception ignored) {
            return clean;
        }
    }

    private static String normalizePlaylistEntry(String playlistUrl, String entry) {
        if (isHttpUrl(entry)) {
            return normalizeStreamUrl(entry);
        }
        try {
            return normalizeStreamUrl(new URL(new URL(playlistUrl), entry).toString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isDefaultPort(String protocol, int port) {
        return ("http".equals(protocol) && port == 80)
                || ("https".equals(protocol) && port == 443);
    }

    private static long stableNegativeId(String value) {
        long hash = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            hash = 31L * hash + value.charAt(i);
        }
        return -Math.abs(hash);
    }
}
