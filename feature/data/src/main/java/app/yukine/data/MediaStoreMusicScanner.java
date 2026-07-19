package app.yukine.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;

public final class MediaStoreMusicScanner {
    private final Context context;

    public MediaStoreMusicScanner(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<Track> scan() {
        throwIfInterrupted();
        ArrayList<Track> tracks = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.ARTIST + " COLLATE NOCASE ASC, "
                + MediaStore.Audio.Media.ALBUM + " COLLATE NOCASE ASC, "
                + MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = resolver.query(collection, projection, selection, null, sortOrder)) {
            if (cursor == null) {
                return tracks;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);

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
                Uri uri = ContentUris.withAppendedId(collection, id);
                ExtendedMetadata extendedMetadata = readExtendedMetadata(
                        uri,
                        yearColumn >= 0 ? cursor.getInt(yearColumn) : 0
                );
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
                        extendedMetadata.albumArtist,
                        extendedMetadata.composer,
                        "",
                        extendedMetadata.year
                );
                // 音频规格和内嵌封面仍然懒加载。这里只对 MediaStore 未稳定暴露的身份字段做
                // 一次尽力读取；坏文件或无权限媒体会立即回退到 MediaStore 基础信息。
                tracks.add(track);
            }
        }
        return tracks;
    }

    private ExtendedMetadata readExtendedMetadata(Uri uri, int mediaStoreYear) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            return new ExtendedMetadata(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                    parseYear(
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                            mediaStoreYear
                    )
            );
        } catch (RuntimeException ignored) {
            return new ExtendedMetadata("", "", sanitizeYear(mediaStoreYear));
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // Best-effort metadata must never abort the library scan.
            }
        }
    }

    private static int parseYear(String value, int fallback) {
        if (value != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{4})")
                    .matcher(value);
            if (matcher.find()) {
                try {
                    return sanitizeYear(Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Fall through to the MediaStore year.
                }
            }
        }
        return sanitizeYear(fallback);
    }

    private static int sanitizeYear(int value) {
        return value >= 1000 && value <= 9999 ? value : 0;
    }

    private static final class ExtendedMetadata {
        final String albumArtist;
        final String composer;
        final int year;

        ExtendedMetadata(String albumArtist, String composer, int year) {
            this.albumArtist = albumArtist == null ? "" : albumArtist.trim();
            this.composer = composer == null ? "" : composer.trim();
            this.year = year;
        }
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

    private boolean isCallRecording(String title, String artist, String album, String dataPath) {
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

}
