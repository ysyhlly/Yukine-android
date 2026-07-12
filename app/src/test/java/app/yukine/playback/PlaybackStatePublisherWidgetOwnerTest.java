package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PlaybackStatePublisherWidgetOwnerTest {
    @Test
    public void delegatesWidgetUpdateWithContextSnapshotAndArtwork() {
        Context context = RuntimeEnvironment.getApplication();
        PlaybackStateSnapshot snapshot = PlaybackStateSnapshot.empty();
        FakeWidgetOperations widgetOperations = new FakeWidgetOperations();
        PlaybackStatePublisherWidgetOwner owner = new PlaybackStatePublisherWidgetOwner(
                () -> context,
                () -> widgetOperations
        );

        owner.update(snapshot, null);

        assertEquals(1, widgetOperations.updateCalls);
        assertSame(context, widgetOperations.lastContext);
        assertSame(snapshot, widgetOperations.lastSnapshot);
    }

    @Test
    public void ignoresMissingContextOrWidgetOperations() {
        FakeWidgetOperations widgetOperations = new FakeWidgetOperations();
        PlaybackStatePublisherWidgetOwner missingContextOwner = new PlaybackStatePublisherWidgetOwner(
                () -> null,
                () -> widgetOperations
        );
        PlaybackStatePublisherWidgetOwner missingOperationsOwner = new PlaybackStatePublisherWidgetOwner(
                RuntimeEnvironment::getApplication,
                () -> null
        );
        PlaybackStatePublisherWidgetOwner nullProvidersOwner =
                new PlaybackStatePublisherWidgetOwner(null, null);

        missingContextOwner.update(PlaybackStateSnapshot.empty(), null);
        missingOperationsOwner.update(PlaybackStateSnapshot.empty(), null);
        nullProvidersOwner.update(PlaybackStateSnapshot.empty(), null);

        assertEquals(0, widgetOperations.updateCalls);
    }

    @Test
    public void skipsProgressOnlyWidgetUpdatesButPublishesVisibleChanges() {
        Context context = RuntimeEnvironment.getApplication();
        FakeWidgetOperations widgetOperations = new FakeWidgetOperations();
        PlaybackStatePublisherWidgetOwner owner = new PlaybackStatePublisherWidgetOwner(
                () -> context,
                () -> widgetOperations
        );
        PlaybackStateSnapshot first = snapshot(1000L, false);
        PlaybackStateSnapshot laterProgress = snapshot(3000L, false);
        PlaybackStateSnapshot playing = snapshot(3000L, true);

        owner.update(first, null);
        owner.update(laterProgress, null);
        owner.update(playing, null);

        assertEquals(2, widgetOperations.updateCalls);
        assertSame(playing, widgetOperations.lastSnapshot);
    }

    @Test
    public void publishesArtworkWhenAsyncArtworkArrivesForSameTrack() {
        Context context = RuntimeEnvironment.getApplication();
        FakeWidgetOperations widgetOperations = new FakeWidgetOperations();
        PlaybackStatePublisherWidgetOwner owner = new PlaybackStatePublisherWidgetOwner(
                () -> context,
                () -> widgetOperations
        );
        PlaybackStateSnapshot snapshot = snapshot(1000L, true);
        Bitmap artwork = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);

        owner.update(snapshot, null);
        owner.update(snapshot, artwork);
        owner.update(snapshot, artwork);

        assertEquals(2, widgetOperations.updateCalls);
    }

    private static PlaybackStateSnapshot snapshot(long positionMs, boolean playing) {
        app.yukine.model.Track track = new app.yukine.model.Track(
                7L,
                "Track",
                "Artist",
                "Album",
                180000L,
                android.net.Uri.parse("content://track/7"),
                "content://track/7"
        );
        return new PlaybackStateSnapshot(
                track,
                0,
                1,
                positionMs,
                track.durationMs,
                playing,
                false,
                "",
                false,
                0,
                1.0f,
                1.0f,
                0L
        );
    }

    private static final class FakeWidgetOperations
            implements PlaybackStatePublisherWidgetOwner.WidgetOperations {
        private int updateCalls;
        private Context lastContext;
        private PlaybackStateSnapshot lastSnapshot;

        @Override
        public void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork) {
            updateCalls++;
            lastContext = context;
            lastSnapshot = snapshot;
        }
    }
}
