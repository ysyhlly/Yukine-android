package app.yukine.playback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;

final class PlaybackNotificationArtworkManager {
    interface StateProvider {
        Track currentTrack();
        void refreshPlaybackSession();
        void updateMediaNotification();
    }

    private static final int NOTIFICATION_ARTWORK_TARGET_PX = 512;
    private static final int NOTIFICATION_ARTWORK_CACHE_ENTRIES = 8;

    private final Context context;
    private final StateProvider stateProvider;
    private final LruCache<String, Bitmap> artworkCache = new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final LruCache<String, byte[]> artworkDataCache = new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final Set<String> artworkMisses = Collections.synchronizedSet(new HashSet<>());

    PlaybackNotificationArtworkManager(Context context, StateProvider stateProvider) {
        this.context = context;
        this.stateProvider = stateProvider;
    }

    void release() {
        artworkCache.evictAll();
        artworkDataCache.evictAll();
        artworkMisses.clear();
    }

    Bitmap notificationArtworkFor(Track track) {
        if (track == null || track.albumArtUri == null) {
            return null;
        }
        String key = notificationArtworkKey(track);
        Bitmap cached = artworkCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!artworkMisses.contains(key)) {
            artworkMisses.add(key);
            loadNotificationArtworkAsync(track, key);
        }
        return null;
    }

    byte[] notificationArtworkDataFor(Track track) {
        if (track == null) {
            return null;
        }
        return artworkDataCache.get(notificationArtworkKey(track));
    }

    private void loadNotificationArtworkAsync(Track track, String key) {
        context.getMainExecutor().execute(() -> {
            Bitmap bitmap = decodeNotificationArtwork(track.albumArtUri);
            if (bitmap == null) {
                return;
            }
            artworkCache.put(key, bitmap);
            byte[] artworkData = encodeMetadataArtwork(bitmap);
            if (artworkData != null) {
                artworkDataCache.put(key, artworkData);
            }
            Track current = stateProvider.currentTrack();
            if (current == null || !key.equals(notificationArtworkKey(current))) {
                return;
            }
            stateProvider.refreshPlaybackSession();
            stateProvider.updateMediaNotification();
        });
    }

    private String notificationArtworkKey(Track track) {
        if (track == null || track.albumArtUri == null) {
            return "";
        }
        return track.id + "|" + track.albumArtUri;
    }

    private Bitmap decodeNotificationArtwork(Uri uri) {
        if (uri == null) {
            return null;
        }
        if (EmbeddedArtwork.isEmbeddedArtworkUri(uri)) {
            byte[] bytes = EmbeddedArtwork.read(context, uri);
            return decodeNotificationArtworkBytes(bytes);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = openNotificationArtworkStream(uri)) {
            if (input == null) {
                return null;
            }
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight, NOTIFICATION_ARTWORK_TARGET_PX);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = openNotificationArtworkStream(uri)) {
            if (input == null) {
                return null;
            }
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private Bitmap decodeNotificationArtworkBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight, NOTIFICATION_ARTWORK_TARGET_PX);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private byte[] encodeMetadataArtwork(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                return null;
            }
            return output.toByteArray();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private InputStream openNotificationArtworkStream(Uri uri) throws IOException {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return context.getContentResolver().openInputStream(uri);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android");
        connection.setRequestProperty("Referer", "https://music.163.com/");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            return null;
        }
        return connection.getInputStream();
    }

    private int artworkSampleSize(int width, int height, int targetPx) {
        int sample = 1;
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        while (halfWidth / sample >= targetPx && halfHeight / sample >= targetPx) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }
}
