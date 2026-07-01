package app.yukine.playback;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.function.Supplier;

final class PlaybackStatePublisherWidgetOwner implements PlaybackStatePublisher.WidgetUpdater {
    interface WidgetOperations {
        void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork);
    }

    private final Supplier<Context> contextProvider;
    private final Supplier<WidgetOperations> widgetOperationsProvider;

    PlaybackStatePublisherWidgetOwner(
            Supplier<Context> contextProvider,
            Supplier<WidgetOperations> widgetOperationsProvider
    ) {
        this.contextProvider = contextProvider;
        this.widgetOperationsProvider = widgetOperationsProvider;
    }

    static PlaybackStatePublisherWidgetOwner fromContextProvider(Supplier<Context> contextProvider) {
        return new PlaybackStatePublisherWidgetOwner(
                contextProvider,
                EchoPlaybackWidgetOperations::new
        );
    }

    @Override
    public void update(PlaybackStateSnapshot snapshot, Bitmap artwork) {
        Context context = contextProvider == null ? null : contextProvider.get();
        WidgetOperations widgetOperations = widgetOperationsProvider == null
                ? null
                : widgetOperationsProvider.get();
        if (context != null && widgetOperations != null) {
            widgetOperations.update(context, snapshot, artwork);
        }
    }

    private static final class EchoPlaybackWidgetOperations implements WidgetOperations {
        @Override
        public void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork) {
            EchoPlaybackWidgetProvider.update(context, snapshot, artwork);
        }
    }
}
