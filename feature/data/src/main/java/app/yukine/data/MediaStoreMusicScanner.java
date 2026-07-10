package app.yukine.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;

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
                Track track = new Track(
                        id,
                        title,
                        artist,
                        album,
                        cursor.getLong(durationColumn),
                        uri,
                        dataPath,
                        albumId,
                        EmbeddedArtwork.uriFor(uri)
                );
                // 扫描只入库基础信息。音频规格和内嵌封面都要懒加载；同步打开每个媒体文件会让
                // 大曲库、坏文件或部分模拟器 MediaStore 卡在扫描循环里。
                tracks.add(track);
            }
        }
        return tracks;
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
