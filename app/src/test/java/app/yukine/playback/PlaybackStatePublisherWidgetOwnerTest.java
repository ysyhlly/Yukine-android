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
                context,
                widgetOperations
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
                null,
                widgetOperations
        );
        PlaybackStatePublisherWidgetOwner missingOperationsOwner = new PlaybackStatePublisherWidgetOwner(
                RuntimeEnvironment.getApplication(),
                null
        );
        PlaybackStatePublisherWidgetOwner nullProvidersOwner =
                new PlaybackStatePublisherWidgetOwner(null, null);

        missingContextOwner.update(PlaybackStateSnapshot.empty(), null);
        missingOperationsOwner.update(PlaybackStateSnapshot.empty(), null);
        nullProvidersOwner.update(PlaybackStateSnapshot.empty(), null);

        assertEquals(0, widgetOperations.updateCalls);
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
