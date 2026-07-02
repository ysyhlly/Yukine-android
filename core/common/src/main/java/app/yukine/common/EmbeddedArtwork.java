package app.yukine.common;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;

import java.io.IOException;
import java.util.Arrays;

public final class EmbeddedArtwork {
    private static final String SCHEME = "echo-embedded-artwork";
    private static final String AUTHORITY = "track";
    private static final String SOURCE_QUERY = "source";
    private static final int MAX_CACHED_ARTWORK_BYTES = 8 * 1024 * 1024;
    private static final Object CACHE_LOCK = new Object();
    private static final LruCache<String, byte[]> EMBEDDED_PICTURE_CACHE =
            new LruCache<String, byte[]>(MAX_CACHED_ARTWORK_BYTES) {
                @Override
                protected int sizeOf(String key, byte[] value) {
                    return value == null ? 0 : value.length;
                }
            };

    private EmbeddedArtwork() {
    }

    public static Uri uriIfEmbeddedPicture(Context context, Uri audioUri) {
        return hasEmbeddedPicture(context, audioUri) ? uriFor(audioUri) : null;
    }

    public static Uri uriFor(Uri audioUri) {
        if (audioUri == null || Uri.EMPTY.equals(audioUri)) {
            return null;
        }
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(AUTHORITY)
                .appendQueryParameter(SOURCE_QUERY, audioUri.toString())
                .build();
    }

    public static boolean isEmbeddedArtworkUri(Uri uri) {
        return uri != null && SCHEME.equals(uri.getScheme()) && AUTHORITY.equals(uri.getAuthority());
    }

    public static byte[] read(Context context, Uri artworkUri) {
        Uri audioUri = sourceUri(artworkUri);
        if (audioUri == null) {
            return null;
        }
        byte[] cached = cachedEmbeddedPicture(audioUri);
        if (cached != null && cached.length > 0) {
            return cached;
        }
        byte[] picture = readEmbeddedPicture(context, audioUri);
        cacheEmbeddedPicture(audioUri, picture);
        return picture;
    }

    static Uri sourceUri(Uri artworkUri) {
        if (!isEmbeddedArtworkUri(artworkUri)) {
            return null;
        }
        String value = artworkUri.getQueryParameter(SOURCE_QUERY);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Uri.parse(value);
    }

    private static boolean hasEmbeddedPicture(Context context, Uri audioUri) {
        byte[] picture = readEmbeddedPicture(context, audioUri);
        cacheEmbeddedPicture(audioUri, picture);
        return picture != null && picture.length > 0;
    }

    private static byte[] readEmbeddedPicture(Context context, Uri audioUri) {
        if (context == null || audioUri == null || Uri.EMPTY.equals(audioUri)) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context.getApplicationContext(), audioUri);
            return retriever.getEmbeddedPicture();
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // Some platform codecs throw during release.
            }
        }
    }

    static int cachedArtworkBytesForTesting(Uri audioUri) {
        String key = cacheKey(audioUri);
        if (key == null) {
            return 0;
        }
        synchronized (CACHE_LOCK) {
            byte[] cached = EMBEDDED_PICTURE_CACHE.get(key);
            return cached == null ? 0 : cached.length;
        }
    }

    static void clearCacheForTesting() {
        synchronized (CACHE_LOCK) {
            EMBEDDED_PICTURE_CACHE.evictAll();
        }
    }

    static void cacheEmbeddedPictureForTesting(Uri audioUri, byte[] picture) {
        cacheEmbeddedPicture(audioUri, picture);
    }

    private static byte[] cachedEmbeddedPicture(Uri audioUri) {
        String key = cacheKey(audioUri);
        if (key == null) {
            return null;
        }
        synchronized (CACHE_LOCK) {
            byte[] cached = EMBEDDED_PICTURE_CACHE.get(key);
            return cached == null ? null : Arrays.copyOf(cached, cached.length);
        }
    }

    private static void cacheEmbeddedPicture(Uri audioUri, byte[] picture) {
        String key = cacheKey(audioUri);
        if (key == null || picture == null || picture.length == 0 || picture.length > MAX_CACHED_ARTWORK_BYTES) {
            return;
        }
        synchronized (CACHE_LOCK) {
            EMBEDDED_PICTURE_CACHE.put(key, Arrays.copyOf(picture, picture.length));
        }
    }

    private static String cacheKey(Uri audioUri) {
        if (audioUri == null || Uri.EMPTY.equals(audioUri)) {
            return null;
        }
        return audioUri.toString();
    }
}
