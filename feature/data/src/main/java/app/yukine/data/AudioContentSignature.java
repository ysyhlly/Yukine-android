package app.yukine.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import app.yukine.model.Track;

/** Cheap content revision key used before any extractor, decoder or fingerprint work. */
final class AudioContentSignature {
    private AudioContentSignature() {
    }

    static String create(Context context, Track track) {
        if (track == null) return "";
        Uri uri = track.contentUri == null ? Uri.EMPTY : track.contentUri;
        FileFacts facts = fileFacts(uri, track.dataPath);
        if (context != null && ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            facts = contentFacts(context.getApplicationContext().getContentResolver(), uri, facts);
        }
        return create(
                uri.toString(),
                track.dataPath,
                track.durationMs,
                facts.length,
                facts.modifiedAt
        );
    }

    static String create(String uri, String dataPath, long durationMs, long length, long modifiedAt) {
        String raw = clean(uri) + '\n' + clean(dataPath) + '\n'
                + Math.max(0L, durationMs) + '\n' + length + '\n' + modifiedAt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static FileFacts fileFacts(Uri uri, String dataPath) {
        File file = null;
        if (uri != null && ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) file = new File(path);
        }
        if ((file == null || !file.isFile()) && dataPath != null && !dataPath.isEmpty()) {
            File candidate = new File(dataPath);
            if (candidate.isFile()) file = candidate;
        }
        return file == null || !file.isFile()
                ? new FileFacts(-1L, -1L)
                : new FileFacts(file.length(), file.lastModified());
    }

    private static FileFacts contentFacts(ContentResolver resolver, Uri uri, FileFacts fallback) {
        long length = fallback.length;
        long modifiedAt = fallback.modifiedAt;
        try (android.content.res.AssetFileDescriptor descriptor =
                     resolver.openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null && descriptor.getLength() >= 0L) {
                length = descriptor.getLength();
            }
        } catch (Exception ignored) {
            // Providers may deny descriptor metadata while still allowing the decoder to read.
        }
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                length = longValue(cursor, OpenableColumns.SIZE, length);
                modifiedAt = longValue(cursor, DocumentsContract.Document.COLUMN_LAST_MODIFIED, modifiedAt);
                modifiedAt = longValue(cursor, MediaStore.MediaColumns.DATE_MODIFIED, modifiedAt);
            }
        } catch (RuntimeException ignored) {
            // Revision metadata is an optimization; lack of it must not block parsing.
        }
        return new FileFacts(length, modifiedAt);
    }

    private static long longValue(Cursor cursor, String column, long fallback) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return fallback;
        try {
            return cursor.getLong(index);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class FileFacts {
        final long length;
        final long modifiedAt;

        FileFacts(long length, long modifiedAt) {
            this.length = length;
            this.modifiedAt = modifiedAt;
        }
    }
}
