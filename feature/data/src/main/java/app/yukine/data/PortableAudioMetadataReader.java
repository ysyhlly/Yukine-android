package app.yukine.data;

import android.content.Context;
import android.net.Uri;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Reads portable tags that Android's MediaMetadataRetriever may ignore for some containers. */
final class PortableAudioMetadataReader {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "flac", "m4a", "mp4", "ogg", "opus", "wav", "aif", "aiff", "wma"
    );

    private final Context context;

    PortableAudioMetadataReader(Context context) {
        this.context = context.getApplicationContext();
    }

    Metadata read(Uri uri, String displayName) {
        String extension = extension(displayName);
        if (uri == null || !SUPPORTED_EXTENSIONS.contains(extension)) {
            return Metadata.EMPTY;
        }
        File temporary = null;
        try {
            File source;
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                source = new File(uri.getPath() == null ? "" : uri.getPath());
            } else {
                File directory = new File(context.getCacheDir(), "portable-audio-metadata");
                if (!directory.exists() && !directory.mkdirs()) {
                    return Metadata.EMPTY;
                }
                temporary = File.createTempFile("track-", "." + extension, directory);
                try (InputStream input = context.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) {
                        return Metadata.EMPTY;
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        output.write(buffer, 0, read);
                    }
                }
                source = temporary;
            }
            if (!source.isFile() || source.length() == 0L) {
                return Metadata.EMPTY;
            }
            AudioFile audioFile = AudioFileIO.read(source);
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return Metadata.EMPTY;
            }
            Artwork artwork = tag.getFirstArtwork();
            byte[] artworkBytes = artwork == null ? null : artwork.getBinaryData();
            return new Metadata(
                    text(tag.getFirst(FieldKey.TITLE)),
                    text(tag.getFirst(FieldKey.ARTIST)),
                    text(tag.getFirst(FieldKey.ALBUM)),
                    artworkBytes == null ? null : Arrays.copyOf(artworkBytes, artworkBytes.length)
            );
        } catch (Exception ignored) {
            return Metadata.EMPTY;
        } finally {
            if (temporary != null) {
                temporary.delete();
            }
        }
    }

    private static String extension(String displayName) {
        if (displayName == null) {
            return "";
        }
        int dot = displayName.lastIndexOf('.');
        return dot < 0 ? "" : displayName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Metadata {
        static final Metadata EMPTY = new Metadata("", "", "", null);

        final String title;
        final String artist;
        final String album;
        final byte[] artwork;

        Metadata(String title, String artist, String album, byte[] artwork) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artwork = artwork;
        }
    }
}
