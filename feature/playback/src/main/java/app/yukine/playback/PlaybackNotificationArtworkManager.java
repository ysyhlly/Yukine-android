package app.yukine.playback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Process;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;

final class PlaybackNotificationArtworkManager implements PlaybackNotificationArtworkSource {
    interface StateProvider {
        Track currentTrack();
    }

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
    private final StateProvider stateProvider;
    private final NotificationBridge notificationBridge;
    private final Executor artworkExecutor;
    private final Executor mainExecutor;
    private final ExecutorService ownedArtworkExecutor;
    private final ArtworkLoader artworkLoader;
    private final ArtworkEncoder artworkEncoder;
    private final LruCache<String, Bitmap> artworkCache = new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final LruCache<String, byte[]> artworkDataCache = new LruCache<>(NOTIFICATION_ARTWORK_CACHE_ENTRIES);
    private final Set<String> artworkMisses = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger artworkGeneration = new AtomicInteger();
    private volatile boolean released;

    PlaybackNotificationArtworkManager(
            Context context,
            StateProvider stateProvider,
            NotificationBridge notificationBridge
    ) {
        this(
                context,
                stateProvider,
                notificationBridge,
                newArtworkExecutor(),
                ContextCompat.getMainExecutor(context),
                null,
                null,
                true
        );
    }

    PlaybackNotificationArtworkManager(
            Context context,
            StateProvider stateProvider,
            NotificationBridge notificationBridge,
            Executor artworkExecutor,
            ArtworkLoader artworkLoader,
            ArtworkEncoder artworkEncoder
    ) {
        this(
                context,
                stateProvider,
                notificationBridge,
                artworkExecutor,
                Runnable::run,
                artworkLoader,
                artworkEncoder,
                false
        );
    }

    PlaybackNotificationArtworkManager(
            Context context,
            StateProvider stateProvider,
            NotificationBridge notificationBridge,
            Executor artworkExecutor,
            Executor mainExecutor,
            ArtworkLoader artworkLoader,
            ArtworkEncoder artworkEncoder
    ) {
        this(
                context,
                stateProvider,
                notificationBridge,
                artworkExecutor,
                mainExecutor,
                artworkLoader,
                artworkEncoder,
                false
        );
    }

    private PlaybackNotificationArtworkManager(
            Context context,
            StateProvider stateProvider,
            NotificationBridge notificationBridge,
            Executor artworkExecutor,
            Executor mainExecutor,
            ArtworkLoader artworkLoader,
            ArtworkEncoder artworkEncoder,
            boolean ownsArtworkExecutor
    ) {
        this.context = context.getApplicationContext();
        this.stateProvider = stateProvider;
        this.notificationBridge = notificationBridge;
        this.artworkExecutor = artworkExecutor;
        this.mainExecutor = mainExecutor;
        this.ownedArtworkExecutor = ownsArtworkExecutor && artworkExecutor instanceof ExecutorService
                ? (ExecutorService) artworkExecutor
                : null;
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
        if (ownedArtworkExecutor != null) {
            ownedArtworkExecutor.shutdownNow();
        }
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
        if (artworkMisses.add(key)) {
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
        try {
            artworkExecutor.execute(() -> {
                if (!isCurrentArtworkGeneration(generation)) {
                    return;
                }
                Bitmap bitmap = artworkLoader.decode(track.albumArtUri);
                if (bitmap == null || !isCurrentArtworkGeneration(generation)) {
                    return;
                }
                byte[] artworkData = artworkEncoder.encode(bitmap);
                publishArtworkResult(generation, key, bitmap, artworkData);
            });
        } catch (RejectedExecutionException ignored) {
            artworkMisses.remove(key);
        }
    }

    private void publishArtworkResult(int generation, String key, Bitmap bitmap, byte[] artworkData) {
        try {
            mainExecutor.execute(() -> {
                if (!isCurrentArtworkGeneration(generation)) {
                    return;
                }
                artworkCache.put(key, bitmap);
                if (artworkData != null) {
                    artworkDataCache.put(key, artworkData);
                }
                Track current = stateProvider.currentTrack();
                if (current == null || !key.equals(notificationArtworkKey(current))) {
                    return;
                }
                notificationBridge.refreshPlaybackSession();
                notificationBridge.updateMediaNotification();
            });
        } catch (RejectedExecutionException ignored) {
            artworkMisses.remove(key);
        }
    }

    private boolean isCurrentArtworkGeneration(int generation) {
        return !released && artworkGeneration.get() == generation;
    }

    private static ExecutorService newArtworkExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                runnable -> new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    runnable.run();
                }, "YukineNotificationArtwork"),
                new ThreadPoolExecutor.AbortPolicy()
        );
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
