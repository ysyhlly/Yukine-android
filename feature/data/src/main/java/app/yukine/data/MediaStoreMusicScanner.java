package app.yukine.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.LocalAudioDecision;
import app.yukine.model.LocalAudioFormatPolicy;
import app.yukine.model.LocalAudioImportSummary;
import app.yukine.model.LocalAudioIngestResult;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;

public final class MediaStoreMusicScanner {
    private final Context context;
    private final LocalAudioCandidateProbe candidateProbe;

    public MediaStoreMusicScanner(Context context) {
        this(context, new LocalAudioCandidateProbe(context));
    }

    MediaStoreMusicScanner(Context context, LocalAudioCandidateProbe candidateProbe) {
        this.context = context.getApplicationContext();
        this.candidateProbe = candidateProbe;
    }

    public LocalAudioIngestResult scan() {
        throwIfInterrupted();
        ArrayList<Track> tracks = new ArrayList<>();
        LocalAudioImportSummary.Builder summary = new LocalAudioImportSummary.Builder();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        boolean hasExtendedColumns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        String[] projection = hasExtendedColumns
                ? new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.ALBUM_ARTIST,
                        MediaStore.Audio.Media.COMPOSER
                }
                : new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.ALBUM_ID
                };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.ARTIST + " COLLATE NOCASE ASC, "
                + MediaStore.Audio.Media.ALBUM + " COLLATE NOCASE ASC, "
                + MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = resolver.query(collection, projection, selection, null, sortOrder)) {
            if (cursor == null) {
                return LocalAudioIngestResult.empty();
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
            int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int albumArtistColumn = hasExtendedColumns
                    ? cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) : -1;
            int composerColumn = hasExtendedColumns
                    ? cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER) : -1;

            while (cursor.moveToNext()) {
                throwIfInterrupted();
                long id = cursor.getLong(idColumn);
                long albumId = albumIdColumn >= 0 ? cursor.getLong(albumIdColumn) : 0L;
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                String album = cursor.getString(albumColumn);
                String dataPath = cursor.getString(dataColumn);
                if (isCallRecording(title, artist, album, dataPath)) {
                    continue;
                }
                if (isThirdPartyCache(dataPath)) {
                    summary.record(LocalAudioFormatPolicy.classify(dataPath, "audio/unknown"));
                    continue;
                }
                Uri uri = ContentUris.withAppendedId(collection, id);
                String displayName = displayNameColumn >= 0
                        ? cursor.getString(displayNameColumn) : dataPath;
                String mimeType = mimeTypeColumn >= 0
                        ? cursor.getString(mimeTypeColumn) : "";
                LocalAudioDecision decision =
                        candidateProbe.probeForMediaStore(uri, displayName, mimeType);
                summary.record(decision);
                if (!decision.shouldImport()) {
                    continue;
                }
                String albumArtist = albumArtistColumn >= 0
                        ? nullToEmpty(cursor.getString(albumArtistColumn)) : "";
                String composer = composerColumn >= 0
                        ? nullToEmpty(cursor.getString(composerColumn)) : "";
                int year = sanitizeYear(yearColumn >= 0 ? cursor.getInt(yearColumn) : 0);
                Track track = new Track(
                        id,
                        title,
                        artist,
                        album,
                        cursor.getLong(durationColumn),
                        uri,
                        dataPath,
                        albumId,
                        EmbeddedArtwork.uriFor(uri),
                        "",
                        0,
                        0,
                        0,
                        0,
                        0.0f,
                        0.0f,
                        TrackIdentityTags.EMPTY,
                        albumArtist,
                        composer,
                        "",
                        year
                );
                tracks.add(track);
            }
        }
        return new LocalAudioIngestResult(tracks, summary.build());
    }

    private static int sanitizeYear(int value) {
        return value >= 1000 && value <= 9999 ? value : 0;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("MediaStore scan cancelled");
        }
    }

    /**
     * Android 11+ increments this token whenever the external MediaStore content changes. Older
     * versions and providers that deny the query fall back to a full scan.
     */
    public long generation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return -1L;
        }
        try {
            return MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL);
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    // Package-private for unit testing.
    static boolean isCallRecording(String title, String artist, String album, String dataPath) {
        String text = ((title == null ? "" : title) + " "
                + (artist == null ? "" : artist) + " "
                + (album == null ? "" : album) + " "
                + (dataPath == null ? "" : dataPath)).toLowerCase(Locale.ROOT);
        return text.contains("call recordings")
                || text.contains("/callrecord")
                || text.contains("/call_record")
                || text.contains("/record/call")
                || text.contains("/recordings/call")
                || text.contains("通话录音");
    }

    /**
     * Filters known DRM/encrypted cache files from third-party music apps (Kugou, QQ Music, etc.)
     * that MediaStore may incorrectly tag as IS_MUSIC.
     */
    // Package-private for unit testing.
    static boolean isThirdPartyCache(String dataPath) {
        if (dataPath == null || dataPath.isEmpty()) return false;
        String lower = dataPath.toLowerCase(Locale.ROOT);
        if (LocalAudioFormatPolicy.isEncryptedExtension(lower)) {
            return true;
        }
        // Files inside known cache directories with non-standard audio extensions
        boolean inCacheDir = lower.contains("/kugou/") || lower.contains("/kugoumusic/")
                || lower.contains("/qqmusic/") || lower.contains("/tencent/")
                || lower.contains("/netease/cloudmusic/cache");
        if (inCacheDir) {
            return !LocalAudioFormatPolicy.isRecognizedAudioExtension(lower);
        }
        return false;
    }

}
