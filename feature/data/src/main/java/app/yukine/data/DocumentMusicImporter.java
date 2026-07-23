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

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.LocalAudioDecision;
import app.yukine.model.LocalAudioImportSummary;
import app.yukine.model.LocalAudioIngestResult;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;
import app.yukine.model.TrackIdentity;

public final class DocumentMusicImporter {
    private static final int MAX_TREE_DEPTH = 12;

    private final Context context;
    private final AudioSpecParser audioSpecParser;
    private final PortableAudioMetadataReader portableMetadataReader;
    private final LocalAudioCandidateProbe candidateProbe;

    public DocumentMusicImporter(Context context) {
        this(context, new LocalAudioCandidateProbe(context));
    }

    DocumentMusicImporter(Context context, LocalAudioCandidateProbe candidateProbe) {
        this.context = context.getApplicationContext();
        this.audioSpecParser = new AudioSpecParser(this.context);
        this.portableMetadataReader = new PortableAudioMetadataReader(this.context);
        this.candidateProbe = candidateProbe;
    }

    public LocalAudioIngestResult importAudioUris(List<Uri> uris) {
        ArrayList<Track> tracks = new ArrayList<>();
        LocalAudioImportSummary.Builder summary = new LocalAudioImportSummary.Builder();
        for (Uri uri : uris) {
            String name = displayName(uri);
            appendCandidate(uri, name, mimeType(uri), tracks, summary);
        }
        return new LocalAudioIngestResult(tracks, summary.build());
    }

    public LocalAudioIngestResult importAudioTree(Uri treeUri) {
        ArrayList<Track> tracks = new ArrayList<>();
        LocalAudioImportSummary.Builder summary = new LocalAudioImportSummary.Builder();
        String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        collectTreeTracks(treeUri, rootDocumentId, 0, tracks, summary);
        return new LocalAudioIngestResult(tracks, summary.build());
    }

    private void collectTreeTracks(
            Uri treeUri,
            String documentId,
            int depth,
            List<Track> tracks,
            LocalAudioImportSummary.Builder summary
    ) {
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
                    collectTreeTracks(treeUri, childId, depth + 1, tracks, summary);
                    continue;
                }
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                appendCandidate(documentUri, name, mimeType, tracks, summary);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Some providers expose partial trees or revoke individual children.
        }
    }

    private void appendCandidate(
            Uri uri,
            String displayName,
            String mimeType,
            List<Track> tracks,
            LocalAudioImportSummary.Builder summary
    ) {
        LocalAudioDecision decision = candidateProbe.probe(uri, displayName, mimeType);
        summary.record(decision);
        if (decision.shouldImport()) {
            tracks.add(trackFromUri(uri, displayName, decision));
        }
    }

    private Track trackFromUri(
            Uri uri,
            String displayName,
            LocalAudioDecision formatDecision
    ) {
        String title = stripExtension(displayName);
        String artist = "未知艺人";
        String album = "导入音频";
        String albumArtist = "";
        String composer = "";
        String releaseType = "";
        String genre = "";
        int year = 0;
        int discNumber = 0;
        int trackNumber = 0;
        int bpm = 0;
        String lyrics = "";
        long durationMs = 0L;
        byte[] embeddedArtwork = null;
        TrackIdentityTags identityTags = TrackIdentityTags.EMPTY;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String platformTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String platformArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String platformAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String platformAlbumArtist =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            String platformComposer =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
            int platformYear = parseYear(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            );
            PortableAudioMetadataReader.Metadata portable = portableMetadataReader.read(uri, displayName);
            identityTags = portable.identityTags;
            title = firstText(platformTitle, firstText(portable.title, title));
            artist = firstText(platformArtist, firstText(portable.artist, artist));
            album = firstText(platformAlbum, firstText(portable.album, album));
            albumArtist = firstText(platformAlbumArtist, portable.albumArtist);
            composer = firstText(platformComposer, portable.composer);
            releaseType = portable.releaseType;
            year = platformYear > 0 ? platformYear : portable.year;
            genre = firstText(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    portable.genre
            );
            discNumber = parseIntSafe(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            );
            if (discNumber == 0) discNumber = portable.discNumber;
            trackNumber = parseIntSafe(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            );
            if (trackNumber == 0) trackNumber = portable.trackNumber;
            bpm = portable.bpm;
            lyrics = portable.lyrics;
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
                EmbeddedArtwork.uriFor(uri, embeddedArtwork),
                formatDecision.format().canonicalCodec(),
                0,
                0,
                0,
                0,
                0.0f,
                0.0f,
                identityTags,
                albumArtist,
                composer,
                releaseType,
                year,
                0L,
                genre,
                discNumber,
                trackNumber,
                bpm,
                lyrics
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

    private int parseYear(String value) {
        long year = parseLong(value);
        return year >= 1000L && year <= 9999L ? (int) year : 0;
    }

    private int parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
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
