package app.yukine.data;

import android.net.Uri;

import java.util.List;

import app.yukine.model.Track;

public final class M3uPlaylistExporter {
    private M3uPlaylistExporter() {
    }

    public static String buildM3u8Playlist(String playlistName, List<Track> tracks) {
        StringBuilder text = new StringBuilder();
        text.append("#EXTM3U\n");
        text.append("#PLAYLIST:").append(cleanInlineText(playlistName)).append('\n');
        if (tracks == null) {
            return text.toString();
        }
        for (Track track : tracks) {
            if (track == null) {
                continue;
            }
            long durationSeconds = track.durationMs > 0L ? track.durationMs / 1000L : -1L;
            text.append("#EXTINF:")
                    .append(durationSeconds)
                    .append(',')
                    .append(cleanInlineText(track.artist))
                    .append(" - ")
                    .append(cleanInlineText(track.title))
                    .append('\n');
            text.append(exportLocationForTrack(track)).append('\n');
        }
        return text.toString();
    }

    public static String safeExportFileName(String value) {
        String clean = cleanInlineText(value).replaceAll("[\\\\/:*?\"<>|]", "_");
        return clean.isEmpty() ? "ECHO playlist" : clean;
    }

    public static String cleanInlineText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    static String exportLocationForTrack(Track track) {
        if (track == null) {
            return "";
        }
        if (track.dataPath.startsWith("stream:")) {
            return track.dataPath.substring("stream:".length());
        }
        if (track.dataPath.startsWith("webdav:")) {
            String[] parts = track.dataPath.split(":", 3);
            if (parts.length == 3 && !parts[2].isEmpty()) {
                return parts[2];
            }
        }
        if (!track.dataPath.isEmpty() && !track.dataPath.startsWith("document:")) {
            return track.dataPath;
        }
        if (track.contentUri != null && !Uri.EMPTY.equals(track.contentUri)) {
            return track.contentUri.toString();
        }
        return track.dataPath;
    }
}
