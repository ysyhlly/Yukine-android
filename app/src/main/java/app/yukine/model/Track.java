package app.yukine.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;

public final class Track implements Parcelable {
    private static final String UNKNOWN_TITLE = "\u672a\u77e5\u6b4c\u66f2";
    private static final String UNKNOWN_ARTIST = "\u672a\u77e5\u827a\u4eba";
    private static final String UNKNOWN_ALBUM = "\u672a\u77e5\u4e13\u8f91";

    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final Uri contentUri;
    public final String dataPath;
    public final long albumId;
    public final Uri albumArtUri;
    public final String codec;
    public final int bitrateKbps;
    public final int sampleRateHz;
    public final int bitsPerSample;
    public final int channelCount;
    public final float replayGainTrackDb;
    public final float replayGainAlbumDb;

    public Track(
            long id,
            String title,
            String artist,
            String album,
            long durationMs,
            Uri contentUri,
            String dataPath
    ) {
        this(id, title, artist, album, durationMs, contentUri, dataPath, 0L, null);
    }

    public Track(
            long id,
            String title,
            String artist,
            String album,
            long durationMs,
            Uri contentUri,
            String dataPath,
            long albumId,
            Uri albumArtUri
    ) {
        this(id, title, artist, album, durationMs, contentUri, dataPath, albumId, albumArtUri, "", 0, 0, 0, 0);
    }

    public Track(
            long id,
            String title,
            String artist,
            String album,
            long durationMs,
            Uri contentUri,
            String dataPath,
            long albumId,
            Uri albumArtUri,
            String codec,
            int bitrateKbps,
            int sampleRateHz,
            int bitsPerSample,
            int channelCount
    ) {
        this(
                id,
                title,
                artist,
                album,
                durationMs,
                contentUri,
                dataPath,
                albumId,
                albumArtUri,
                codec,
                bitrateKbps,
                sampleRateHz,
                bitsPerSample,
                channelCount,
                0.0f,
                0.0f
        );
    }

    public Track(
            long id,
            String title,
            String artist,
            String album,
            long durationMs,
            Uri contentUri,
            String dataPath,
            long albumId,
            Uri albumArtUri,
            String codec,
            int bitrateKbps,
            int sampleRateHz,
            int bitsPerSample,
            int channelCount,
            float replayGainTrackDb,
            float replayGainAlbumDb
    ) {
        this.id = id;
        this.title = clean(title, UNKNOWN_TITLE);
        this.artist = clean(artist, UNKNOWN_ARTIST);
        this.album = clean(album, UNKNOWN_ALBUM);
        this.durationMs = Math.max(durationMs, 0L);
        this.contentUri = contentUri == null ? Uri.EMPTY : contentUri;
        this.dataPath = dataPath == null ? "" : dataPath;
        this.albumId = Math.max(albumId, 0L);
        this.albumArtUri = sanitizeAlbumArtUri(albumArtUri);
        this.codec = cleanCodec(codec);
        this.bitrateKbps = Math.max(bitrateKbps, 0);
        this.sampleRateHz = Math.max(sampleRateHz, 0);
        this.bitsPerSample = Math.max(bitsPerSample, 0);
        this.channelCount = Math.max(channelCount, 0);
        this.replayGainTrackDb = sanitizeReplayGain(replayGainTrackDb);
        this.replayGainAlbumDb = sanitizeReplayGain(replayGainAlbumDb);
    }

    private Track(Parcel in) {
        id = in.readLong();
        title = clean(in.readString(), UNKNOWN_TITLE);
        artist = clean(in.readString(), UNKNOWN_ARTIST);
        album = clean(in.readString(), UNKNOWN_ALBUM);
        durationMs = Math.max(in.readLong(), 0L);
        String contentUriValue = in.readString();
        contentUri = contentUriValue == null || contentUriValue.isEmpty() ? Uri.EMPTY : Uri.parse(contentUriValue);
        String dataPathValue = in.readString();
        dataPath = dataPathValue == null ? "" : dataPathValue;
        albumId = Math.max(in.readLong(), 0L);
        String albumArtValue = in.readString();
        albumArtUri = albumArtValue == null || albumArtValue.isEmpty() ? null : Uri.parse(albumArtValue);
        codec = cleanCodec(in.readString());
        bitrateKbps = Math.max(in.readInt(), 0);
        sampleRateHz = Math.max(in.readInt(), 0);
        bitsPerSample = Math.max(in.readInt(), 0);
        channelCount = Math.max(in.readInt(), 0);
        replayGainTrackDb = sanitizeReplayGain(in.readFloat());
        replayGainAlbumDb = sanitizeReplayGain(in.readFloat());
    }

    public String subtitle() {
        if (album.isEmpty() || UNKNOWN_ALBUM.equals(album)) {
            return artist;
        }
        return artist + " - " + album;
    }

    public static String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10L ? "0" : "") + seconds;
    }

    public String albumArtUriString() {
        return albumArtUri == null ? "" : albumArtUri.toString();
    }

    public boolean hasAudioSpec() {
        return !codec.isEmpty() || bitrateKbps > 0 || sampleRateHz > 0 || bitsPerSample > 0 || channelCount > 0;
    }

    public boolean needsAudioSpecParsing() {
        if (hasAudioSpec()) {
            return false;
        }
        return !dataPath.startsWith("stream:")
                && !dataPath.startsWith("streaming:")
                && !dataPath.startsWith("webdav:");
    }

    public String audioSpecSummary() {
        StringBuilder builder = new StringBuilder();
        if (!codec.isEmpty()) {
            builder.append(codec.toUpperCase(Locale.ROOT));
        }
        if (sampleRateHz > 0) {
            appendSeparator(builder);
            builder.append(formatSampleRate(sampleRateHz));
        }
        if (bitsPerSample > 0) {
            appendSeparator(builder);
            builder.append(bitsPerSample).append("bit");
        }
        if (bitrateKbps > 0) {
            appendSeparator(builder);
            builder.append(bitrateKbps).append("kbps");
        }
        if (channelCount > 0) {
            appendSeparator(builder);
            builder.append(channelLabel(channelCount));
        }
        return builder.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(album);
        dest.writeLong(durationMs);
        dest.writeString(contentUri.toString());
        dest.writeString(dataPath);
        dest.writeLong(albumId);
        dest.writeString(albumArtUriString());
        dest.writeString(codec);
        dest.writeInt(bitrateKbps);
        dest.writeInt(sampleRateHz);
        dest.writeInt(bitsPerSample);
        dest.writeInt(channelCount);
        dest.writeFloat(replayGainTrackDb);
        dest.writeFloat(replayGainAlbumDb);
    }

    public static final Creator<Track> CREATOR = new Creator<Track>() {
        @Override
        public Track createFromParcel(Parcel in) {
            return new Track(in);
        }

        @Override
        public Track[] newArray(int size) {
            return new Track[size];
        }
    };

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || isUnknownPlaceholder(trimmed)) {
            return fallback;
        }
        if ("Call Recordings".equalsIgnoreCase(trimmed)) {
            return "\u901a\u8bdd\u5f55\u97f3";
        }
        return trimmed;
    }

    private static String cleanCodec(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            trimmed = trimmed.substring("audio/".length());
        }
        return trimmed;
    }

    private static float sanitizeReplayGain(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        if (value < -30.0f) {
            return -30.0f;
        }
        if (value > 30.0f) {
            return 30.0f;
        }
        return value;
    }

    private static Uri sanitizeAlbumArtUri(Uri value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().toLowerCase(Locale.ROOT);
        if (text.startsWith("content://media/") && text.contains("/audio/albumart")) {
            return null;
        }
        return value;
    }

    private static void appendSeparator(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append(" / ");
        }
    }

    private static String formatSampleRate(int sampleRateHz) {
        if (sampleRateHz % 1000 == 0) {
            return (sampleRateHz / 1000) + "kHz";
        }
        return String.format(Locale.ROOT, "%.1fkHz", sampleRateHz / 1000.0);
    }

    private static String channelLabel(int channelCount) {
        if (channelCount == 1) {
            return "Mono";
        }
        if (channelCount == 2) {
            return "Stereo";
        }
        return channelCount + "ch";
    }

    private static boolean isUnknownPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "<unknown>".equals(normalized)
                || "unknown".equals(normalized)
                || "unknown artist".equals(normalized)
                || "unknown album".equals(normalized)
                || "null".equals(normalized);
    }
}
