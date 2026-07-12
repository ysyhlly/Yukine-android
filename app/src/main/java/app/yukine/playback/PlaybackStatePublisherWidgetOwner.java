package app.yukine.playback;

import android.content.Context;
import android.graphics.Bitmap;

import app.yukine.model.Track;

import java.util.Objects;
import java.util.function.Supplier;

final class PlaybackStatePublisherWidgetOwner implements PlaybackStatePublisher.WidgetUpdater {
    interface WidgetOperations {
        void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork);
    }

    private final Supplier<Context> contextProvider;
    private final Supplier<WidgetOperations> widgetOperationsProvider;
    private WidgetSignature lastPublishedSignature;

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
        WidgetSignature signature = WidgetSignature.from(snapshot, artwork);
        if (signature.equals(lastPublishedSignature)) {
            return;
        }
        Context context = contextProvider == null ? null : contextProvider.get();
        WidgetOperations widgetOperations = widgetOperationsProvider == null
                ? null
                : widgetOperationsProvider.get();
        if (context != null && widgetOperations != null) {
            widgetOperations.update(context, snapshot, artwork);
            lastPublishedSignature = signature;
        }
    }

    private static final class WidgetSignature {
        private final long trackId;
        private final String title;
        private final String artist;
        private final String album;
        private final String artworkUri;
        private final boolean playing;
        private final Bitmap artwork;
        private final boolean artworkRecycled;

        private WidgetSignature(
                long trackId,
                String title,
                String artist,
                String album,
                String artworkUri,
                boolean playing,
                Bitmap artwork,
                boolean artworkRecycled
        ) {
            this.trackId = trackId;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artworkUri = artworkUri;
            this.playing = playing;
            this.artwork = artwork;
            this.artworkRecycled = artworkRecycled;
        }

        static WidgetSignature from(PlaybackStateSnapshot snapshot, Bitmap artwork) {
            Track track = snapshot == null ? null : snapshot.currentTrack;
            return new WidgetSignature(
                    track == null ? -1L : track.id,
                    track == null ? "" : track.title,
                    track == null ? "" : track.artist,
                    track == null ? "" : track.album,
                    track == null || track.albumArtUri == null ? "" : track.albumArtUri.toString(),
                    snapshot != null && snapshot.playing,
                    artwork,
                    artwork != null && artwork.isRecycled()
            );
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WidgetSignature)) {
                return false;
            }
            WidgetSignature that = (WidgetSignature) other;
            return trackId == that.trackId
                    && playing == that.playing
                    && artwork == that.artwork
                    && artworkRecycled == that.artworkRecycled
                    && Objects.equals(title, that.title)
                    && Objects.equals(artist, that.artist)
                    && Objects.equals(album, that.album)
                    && Objects.equals(artworkUri, that.artworkUri);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(trackId, title, artist, album, artworkUri, playing, artworkRecycled);
            return 31 * result + System.identityHashCode(artwork);
        }
    }

    private static final class EchoPlaybackWidgetOperations implements WidgetOperations {
        @Override
        public void update(Context context, PlaybackStateSnapshot snapshot, Bitmap artwork) {
            EchoPlaybackWidgetProvider.update(context, snapshot, artwork);
        }
    }
}
