package app.yukine.playback;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.graphics.Bitmap;
import android.net.Uri;

import app.yukine.model.Track;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PlaybackNotificationArtworkManagerTest {
    @Test
    public void queuedArtworkResultAfterReleaseDoesNotWriteCacheOrNotify() {
        List<Runnable> pending = new ArrayList<>();
        FakeNotificationBridge notificationBridge = new FakeNotificationBridge();
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        byte[] artworkData = new byte[] {1, 2, 3};
        Track track = track(1L);
        FakeArtworkLoader artworkLoader = new FakeArtworkLoader(bitmap);
        PlaybackNotificationArtworkManager manager = manager(
                track,
                notificationBridge,
                pending,
                artworkLoader,
                ignored -> artworkData
        );

        assertNull(manager.notificationArtworkFor(track));
        manager.release();

        pending.get(0).run();

        assertEquals(0, artworkLoader.decodeCalls);
        assertNull(manager.notificationArtworkDataFor(track));
        assertEquals(0, notificationBridge.refreshCalls);
        assertEquals(0, notificationBridge.updateCalls);
    }

    @Test
    public void releaseDuringArtworkDecodeDoesNotWriteCacheOrNotify() {
        List<Runnable> pending = new ArrayList<>();
        FakeNotificationBridge notificationBridge = new FakeNotificationBridge();
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        byte[] artworkData = new byte[] {4, 5, 6};
        Track track = track(2L);
        final PlaybackNotificationArtworkManager[] holder = new PlaybackNotificationArtworkManager[1];
        holder[0] = manager(
                track,
                notificationBridge,
                pending,
                ignored -> {
                    holder[0].release();
                    return bitmap;
                },
                ignored -> artworkData
        );

        assertNull(holder[0].notificationArtworkFor(track));

        pending.get(0).run();

        assertNull(holder[0].notificationArtworkDataFor(track));
        assertEquals(0, notificationBridge.refreshCalls);
        assertEquals(0, notificationBridge.updateCalls);
    }

    @Test
    public void releasePreventsFutureArtworkLoad() {
        List<Runnable> pending = new ArrayList<>();
        FakeNotificationBridge notificationBridge = new FakeNotificationBridge();
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Track track = track(3L);
        FakeArtworkLoader artworkLoader = new FakeArtworkLoader(bitmap);
        PlaybackNotificationArtworkManager manager = manager(
                track,
                notificationBridge,
                pending,
                artworkLoader,
                ignored -> new byte[] {7, 8, 9}
        );

        manager.release();

        assertNull(manager.notificationArtworkFor(track));
        assertNull(manager.notificationArtworkDataFor(track));
        assertEquals(0, pending.size());
        assertEquals(0, artworkLoader.decodeCalls);
        assertEquals(0, notificationBridge.refreshCalls);
        assertEquals(0, notificationBridge.updateCalls);
    }

    @Test
    public void releaseIsIdempotentAfterQueuedArtworkInvalidation() throws Exception {
        List<Runnable> pending = new ArrayList<>();
        FakeNotificationBridge notificationBridge = new FakeNotificationBridge();
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Track track = track(5L);
        FakeArtworkLoader artworkLoader = new FakeArtworkLoader(bitmap);
        PlaybackNotificationArtworkManager manager = manager(
                track,
                notificationBridge,
                pending,
                artworkLoader,
                ignored -> new byte[] {10, 11, 12}
        );

        assertNull(manager.notificationArtworkFor(track));
        int generationBeforeRelease = artworkGeneration(manager);
        manager.release();
        int generationAfterFirstRelease = artworkGeneration(manager);
        manager.release();
        pending.get(0).run();

        assertEquals(generationBeforeRelease + 1, generationAfterFirstRelease);
        assertEquals(generationAfterFirstRelease, artworkGeneration(manager));
        assertEquals(0, artworkLoader.decodeCalls);
        assertNull(manager.notificationArtworkDataFor(track));
        assertEquals(0, notificationBridge.refreshCalls);
        assertEquals(0, notificationBridge.updateCalls);
    }

    @Test
    public void queuedArtworkResultCachesAndRefreshesForCurrentTrack() {
        List<Runnable> pending = new ArrayList<>();
        FakeNotificationBridge notificationBridge = new FakeNotificationBridge();
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        byte[] artworkData = new byte[] {7, 8, 9};
        Track track = track(4L);
        PlaybackNotificationArtworkManager manager = manager(
                track,
                notificationBridge,
                pending,
                ignored -> bitmap,
                ignored -> artworkData
        );

        assertNull(manager.notificationArtworkFor(track));
        pending.get(0).run();

        assertSame(bitmap, manager.notificationArtworkFor(track));
        assertArrayEquals(artworkData, manager.notificationArtworkDataFor(track));
        assertEquals(1, notificationBridge.refreshCalls);
        assertEquals(1, notificationBridge.updateCalls);
    }

    private static PlaybackNotificationArtworkManager manager(
            Track currentTrack,
            PlaybackNotificationArtworkManager.NotificationBridge notificationBridge,
            List<Runnable> pending,
            PlaybackNotificationArtworkManager.ArtworkLoader artworkLoader,
            PlaybackNotificationArtworkManager.ArtworkEncoder artworkEncoder
    ) {
        return new PlaybackNotificationArtworkManager(
                RuntimeEnvironment.getApplication(),
                () -> currentTrack,
                notificationBridge,
                pending::add,
                artworkLoader,
                artworkEncoder
        );
    }

    private static int artworkGeneration(PlaybackNotificationArtworkManager manager) throws Exception {
        Field field = PlaybackNotificationArtworkManager.class.getDeclaredField("artworkGeneration");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(manager)).get();
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Title",
                "Artist",
                "Album",
                180_000L,
                Uri.parse("https://example.com/audio-" + id + ".mp3"),
                "audio-" + id,
                0L,
                Uri.parse("content://artwork/" + id)
        );
    }

    private static final class FakeNotificationBridge implements PlaybackNotificationArtworkManager.NotificationBridge {
        private int refreshCalls;
        private int updateCalls;

        @Override
        public void refreshPlaybackSession() {
            refreshCalls++;
        }

        @Override
        public void updateMediaNotification() {
            updateCalls++;
        }
    }

    private static final class FakeArtworkLoader implements PlaybackNotificationArtworkManager.ArtworkLoader {
        private final Bitmap bitmap;
        private int decodeCalls;

        private FakeArtworkLoader(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        public Bitmap decode(Uri uri) {
            decodeCalls++;
            return bitmap;
        }
    }
}
