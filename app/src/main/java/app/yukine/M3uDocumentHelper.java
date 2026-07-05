package app.yukine;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import android.util.Log;

import app.yukine.data.M3uPlaylistExporter;
import app.yukine.model.PlaylistImportResult;
import app.yukine.model.StreamImportResult;
import app.yukine.model.Track;

final class M3uDocumentHelper {
    private static final String TAG = "M3uDocumentHelper";
    static final String[] MIME_TYPES = new String[]{
            "audio/x-mpegurl",
            "audio/mpegurl",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "text/plain",
            "application/octet-stream",
            "*/*"
    };

    private M3uDocumentHelper() {
    }

    static void configureReadIntent(Intent intent) {
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    }

    static String exportFileName(String playlistName) {
        return M3uPlaylistExporter.safeExportFileName(playlistName) + ".m3u8";
    }

    static String buildPlaylistText(String playlistName, List<Track> tracks) {
        return M3uPlaylistExporter.buildM3u8Playlist(playlistName, tracks);
    }

    static ReadResult readText(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) {
            return ReadResult.failed();
        }
        StringBuilder text = new StringBuilder();
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                return ReadResult.failed();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read M3U text from " + uri, e);
            return ReadResult.failed();
        }
        return ReadResult.success(text.toString());
    }

    static boolean writeText(ContentResolver resolver, Uri uri, String text) {
        if (resolver == null || uri == null) {
            return false;
        }
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) {
                return false;
            }
            output.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            output.flush();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to write M3U text to " + uri, e);
            return false;
        }
    }

    static String playlistFallbackName(Uri uri) {
        if (uri == null) {
            return "Imported playlist";
        }
        String segment = uri.getLastPathSegment();
        if (segment == null || segment.trim().isEmpty()) {
            return "Imported playlist";
        }
        String clean = segment.trim();
        int slash = clean.lastIndexOf('/');
        if (slash >= 0 && slash < clean.length() - 1) {
            clean = clean.substring(slash + 1);
        }
        int colon = clean.lastIndexOf(':');
        if (colon >= 0 && colon < clean.length() - 1) {
            clean = clean.substring(colon + 1);
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m3u8")) {
            clean = clean.substring(0, clean.length() - 5);
        } else if (lower.endsWith(".m3u")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = M3uPlaylistExporter.cleanInlineText(clean);
        return clean.isEmpty() ? "Imported playlist" : clean;
    }

    static String localImportStatus(ReadResult playlistRead, StreamImportResult result) {
        if (playlistRead == null || !playlistRead.success) {
            return "Local M3U import failed";
        }
        if (result == null || result.isEmpty()) {
            return "No streams imported";
        }
        return streamImportStatus("Imported streams", result);
    }

    static String playlistImportStatus(ReadResult playlistRead, PlaylistImportResult result) {
        if (playlistRead == null || !playlistRead.success) {
            return "Playlist import failed";
        }
        if (result == null || result.isEmpty()) {
            return "No playlist entries imported";
        }
        return "Imported playlist " + result.playlistName
                + ", playlist tracks " + result.playlistAddedCount
                + ", streams " + result.streamAddedCount
                + ", skipped " + result.duplicateCount;
    }

    static String streamImportStatus(String prefix, StreamImportResult result) {
        if (result == null || result.isEmpty()) {
            return prefix + ": none";
        }
        String status = prefix + ": added " + result.addedCount + ", skipped " + result.duplicateCount;
        if (result.candidateCount != result.addedCount + result.duplicateCount) {
            status += ", parsed " + result.candidateCount;
        }
        return status;
    }

    static final class ReadResult {
        final boolean success;
        final String text;

        private ReadResult(boolean success, String text) {
            this.success = success;
            this.text = text == null ? "" : text;
        }

        static ReadResult success(String text) {
            return new ReadResult(true, text);
        }

        static ReadResult failed() {
            return new ReadResult(false, "");
        }
    }
}
