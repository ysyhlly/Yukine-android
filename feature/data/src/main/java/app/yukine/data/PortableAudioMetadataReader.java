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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.yukine.model.TrackIdentityTags;

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
        return read(uri, displayName, "", true);
    }

    Metadata read(Uri uri, String displayName, String directPath, boolean allowTemporaryCopy) {
        String extension = extension(displayName);
        if (uri == null || !SUPPORTED_EXTENSIONS.contains(extension)) {
            return Metadata.EMPTY;
        }
        File temporary = null;
        try {
            File source;
            File direct = directPath == null ? null : new File(directPath);
            if (direct != null && direct.isFile()) {
                source = direct;
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                source = new File(uri.getPath() == null ? "" : uri.getPath());
            } else {
                if (!allowTemporaryCopy) {
                    return Metadata.EMPTY;
                }
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
                    artworkBytes == null ? null : Arrays.copyOf(artworkBytes, artworkBytes.length),
                    new TrackIdentityTags(
                            text(tag.getFirst(FieldKey.MUSICBRAINZ_TRACK_ID)),
                            firstText(
                                    text(tag.getFirst(FieldKey.MUSICBRAINZ_RECORDING_WORK_ID)),
                                    text(tag.getFirst(FieldKey.MUSICBRAINZ_WORK_ID))
                            ),
                            text(tag.getFirst(FieldKey.ISRC)),
                            text(tag.getFirst(FieldKey.ACOUSTID_ID)),
                            values(tag, FieldKey.MUSICBRAINZ_ARTISTID)
                    )
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

    private static String firstText(String first, String second) {
        return first == null || first.isEmpty() ? text(second) : text(first);
    }

    private static List<String> values(Tag tag, FieldKey key) {
        if (tag == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        try {
            for (String raw : tag.getAll(key)) {
                if (raw == null) {
                    continue;
                }
                for (String value : raw.split("[;,\\s]+")) {
                    if (!value.trim().isEmpty()) {
                        values.add(value.trim());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    static final class Metadata {
        static final Metadata EMPTY = new Metadata(
                "",
                "",
                "",
                null,
                TrackIdentityTags.EMPTY
        );

        final String title;
        final String artist;
        final String album;
        final byte[] artwork;
        final TrackIdentityTags identityTags;

        Metadata(
                String title,
                String artist,
                String album,
                byte[] artwork,
                TrackIdentityTags identityTags
        ) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.artwork = artwork;
            this.identityTags = identityTags == null ? TrackIdentityTags.EMPTY : identityTags;
        }
    }
}
