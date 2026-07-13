package app.yukine.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentity;

public final class DocumentMusicImporter {
    private static final int MAX_TREE_DEPTH = 12;

    private final Context context;
    private final AudioSpecParser audioSpecParser;
    private final PortableAudioMetadataReader portableMetadataReader;

    public DocumentMusicImporter(Context context) {
        this.context = context.getApplicationContext();
        this.audioSpecParser = new AudioSpecParser(this.context);
        this.portableMetadataReader = new PortableAudioMetadataReader(this.context);
    }

    public List<Track> importAudioUris(List<Uri> uris) {
        ArrayList<Track> tracks = new ArrayList<>();
        for (Uri uri : uris) {
            Track track = trackFromUri(uri, displayName(uri), mimeType(uri));
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    public List<Track> importAudioTree(Uri treeUri) {
        ArrayList<Track> tracks = new ArrayList<>();
        String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        collectTreeTracks(treeUri, rootDocumentId, 0, tracks);
        return tracks;
    }

    private void collectTreeTracks(Uri treeUri, String documentId, int depth, List<Track> tracks) {
        if (depth > MAX_TREE_DEPTH) {
            return;
        }
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String childId = cursor.getString(idColumn);
                String name = cursor.getString(nameColumn);
                String mimeType = cursor.getString(mimeColumn);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    collectTreeTracks(treeUri, childId, depth + 1, tracks);
                    continue;
                }
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                Track track = trackFromUri(documentUri, name, mimeType);
                if (track != null) {
                    tracks.add(track);
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Some providers expose partial trees or revoke individual children.
        }
    }

    private Track trackFromUri(Uri uri, String displayName, String mimeType) {
        if (uri == null || !isLikelyAudio(displayName, mimeType)) {
            return null;
        }
        String title = stripExtension(displayName);
        String artist = "未知艺人";
        String album = "导入音频";
        long durationMs = 0L;
        byte[] embeddedArtwork = null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String platformTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String platformArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String platformAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            PortableAudioMetadataReader.Metadata portable = portableMetadataReader.read(uri, displayName);
            title = firstText(platformTitle, firstText(portable.title, title));
            artist = firstText(platformArtist, firstText(portable.artist, artist));
            album = firstText(platformAlbum, firstText(portable.album, album));
            embeddedArtwork = retriever.getEmbeddedPicture();
            if ((embeddedArtwork == null || embeddedArtwork.length == 0) && portable.artwork != null) {
                embeddedArtwork = portable.artwork;
            }
            durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (RuntimeException ignored) {
            // The file can still be playable even when metadata extraction fails.
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // Release can throw on some platform codecs.
            }
        }

        Track track = new Track(
                stableDocumentId(uri),
                title,
                artist,
                album,
                durationMs,
                uri,
                "document:" + uri.toString(),
                0L,
                EmbeddedArtwork.uriFor(uri, embeddedArtwork)
        );
        return audioSpecParser.enrich(track);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Fall back to the final URI path segment.
        }
        String segment = uri.getLastPathSegment();
        return segment == null || segment.trim().isEmpty() ? "导入音频" : segment;
    }

    private String mimeType(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try {
            return resolver.getType(uri);
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private boolean isLikelyAudio(String displayName, String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return true;
        }
        if (displayName == null) {
            return false;
        }
        String lower = displayName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3")
                || lower.endsWith(".flac")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".wav")
                || lower.endsWith(".ogg")
                || lower.endsWith(".opus")
                || lower.endsWith(".amr")
                || lower.endsWith(".mid")
                || lower.endsWith(".midi")
                || lower.endsWith(".wma");
    }

    private String stripExtension(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "导入音频";
        }
        String trimmed = value.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot > 0 ? trimmed.substring(0, dot) : trimmed;
    }

    private String firstText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long stableDocumentId(Uri uri) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().getBytes());
        } catch (NoSuchAlgorithmException error) {
            long hash = uri.toString().hashCode();
            return hash == 0L ? -1L : -Math.abs(hash);
        }
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[i] & 0xffL);
        }
        value = value & Long.MAX_VALUE;
        return TrackIdentity.stableNegative(-value);
    }
}
