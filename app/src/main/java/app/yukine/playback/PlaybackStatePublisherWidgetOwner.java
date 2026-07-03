package app.yukine.playback;

import android.content.Context;
import android.graphics.Bitmap;

final class PlaybackStatePublisherWidgetOwner implements PlaybackStatePublisher.WidgetUpdater {
    interface WidgetOperations {
        void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork);
    }

    private final Context context;
    private final WidgetOperations widgetOperations;

    PlaybackStatePublisherWidgetOwner(
            Context context,
            WidgetOperations widgetOperations
    ) {
        this.context = context;
        this.widgetOperations = widgetOperations;
    }

    static PlaybackStatePublisherWidgetOwner fromContext(Context context) {
        return new PlaybackStatePublisherWidgetOwner(
                context,
                EchoPlaybackWidgetProvider::update
        );
    }

    @Override
    public void update(PlaybackStateSnapshot snapshot, Bitmap artwork) {
        if (context != null && widgetOperations != null) {
            widgetOperations.update(context, snapshot, artwork);
        }
    }
}
