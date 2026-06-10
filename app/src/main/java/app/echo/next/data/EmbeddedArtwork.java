package app.echo.next.data;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;

public final class EmbeddedArtwork {
    private static final String SCHEME = "echo-embedded-artwork";
    private static final String AUTHORITY = "track";
    private static final String SOURCE_QUERY = "source";

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
        return readEmbeddedPicture(context, audioUri);
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
}
