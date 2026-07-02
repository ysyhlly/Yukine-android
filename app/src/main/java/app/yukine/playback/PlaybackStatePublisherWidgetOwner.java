package app.yukine.playback;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.function.Supplier;

final class PlaybackStatePublisherWidgetOwner implements PlaybackStatePublisher.WidgetUpdater {
    interface WidgetOperations {
        void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork);
    }

    private final Supplier<Context> contextProvider;
    private final WidgetOperations widgetOperations;

    PlaybackStatePublisherWidgetOwner(
            Supplier<Context> contextProvider,
            WidgetOperations widgetOperations
    ) {
        this.contextProvider = contextProvider;
        this.widgetOperations = widgetOperations;
    }

    static PlaybackStatePublisherWidgetOwner fromContextProvider(Supplier<Context> contextProvider) {
        return new PlaybackStatePublisherWidgetOwner(
                contextProvider,
                EchoPlaybackWidgetProvider::update
        );
    }

    @Override
    public void update(PlaybackStateSnapshot snapshot, Bitmap artwork) {
        Context context = contextProvider == null ? null : contextProvider.get();
        if (context != null && widgetOperations != null) {
            widgetOperations.update(context, snapshot, artwork);
        }
    }
}
