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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;
import app.yukine.playback.manager.PlaybackQueueManager;

final class PlaybackNotificationArtworkManager implements PlaybackNotificationArtworkSource {
    interface NotificationBridge {
        void refreshPlaybackSession();
        void updateMediaNotification();
    }

    interface ArtworkLoader {
        Bitmap decode(Uri uri);
    }

    interface ArtworkEncoder {
        byte[] encode(Bitmap bitmap);
    }

    private static final int NOTIFICATION_ARTWORK_TARGET_PX = 512;
    private static final int NOTIFICATION_ARTWORK_CACHE_ENTRIES = 8;

    private final Context context;
    private final Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateProvider;
    private final NotificationBridge notificationBridge;
    private final Executor artworkExecutor;
    private final ArtworkLoader artworkLoader;
    private final ArtworkEncoder artworkEncoder;
    private final LruCache<String, Bitmap> artworkCache = new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final LruCache<String, byte[]> artworkDataCache = new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final Set<String> artworkMisses = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger artworkGeneration = new AtomicInteger();
    private volatile boolean released;

    PlaybackNotificationArtworkManager(
            Context context,
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateProvider,
            NotificationBridge notificationBridge
    ) {
        this(
                context,
                queueStateProvider,
                notificationBridge,
                command -> context.getMainExecutor().execute(command),
                null,
                null
        );
    }

    PlaybackNotificationArtworkManager(
            Context context,
            Supplier<PlaybackQueueManager.QueueStateSnapshot> queueStateProvider,
            NotificationBridge notificationBridge,
            Executor artworkExecutor,
            ArtworkLoader artworkLoader,
            ArtworkEncoder artworkEncoder
    ) {
        this.context = context;
        this.queueStateProvider = queueStateProvider;
        this.notificationBridge = notificationBridge;
        this.artworkExecutor = artworkExecutor;
        this.artworkLoader = artworkLoader == null ? this::decodeNotificationArtwork : artworkLoader;
        this.artworkEncoder = artworkEncoder == null ? this::encodeMetadataArtwork : artworkEncoder;
    }

    void release() {
        if (released) {
            return;
        }
        released = true;
        artworkGeneration.incrementAndGet();
        artworkCache.evictAll();
        artworkDataCache.evictAll();
        artworkMisses.clear();
    }

    @Override
    public Bitmap notificationArtworkFor(Track track) {
        if (released || track == null || track.albumArtUri == null) {
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

    @Override
    public byte[] notificationArtworkDataFor(Track track) {
        if (released || track == null) {
            return null;
        }
        return artworkDataCache.get(notificationArtworkKey(track));
    }

    private void loadNotificationArtworkAsync(Track track, String key) {
        int generation = artworkGeneration.get();
        artworkExecutor.execute(() -> {
            if (!isCurrentArtworkGeneration(generation)) {
                return;
            }
            Bitmap bitmap = artworkLoader.decode(track.albumArtUri);
            if (bitmap == null || !isCurrentArtworkGeneration(generation)) {
                return;
            }
            artworkCache.put(key, bitmap);
            byte[] artworkData = artworkEncoder.encode(bitmap);
            if (artworkData != null) {
                artworkDataCache.put(key, artworkData);
            }
            if (!isCurrentArtworkGeneration(generation)) {
                return;
            }
            Track current = currentTrack();
            if (current == null || !key.equals(notificationArtworkKey(current))) {
                return;
            }
            notificationBridge.refreshPlaybackSession();
            notificationBridge.updateMediaNotification();
        });
    }

    private boolean isCurrentArtworkGeneration(int generation) {
        return !released && artworkGeneration.get() == generation;
    }

    private Track currentTrack() {
        if (queueStateProvider == null) {
            return null;
        }
        PlaybackQueueManager.QueueStateSnapshot snapshot = queueStateProvider.get();
        return snapshot == null ? null : snapshot.getCurrentTrack();
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
