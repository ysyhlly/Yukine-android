package app.yukine.data;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;
import java.util.Locale;

import app.yukine.model.Track;

final class AudioSpecParser {
    private final Context context;

    AudioSpecParser(Context context) {
        this.context = context.getApplicationContext();
    }

    Track enrich(Track track) {
        if (track == null || !track.needsAudioSpecParsing()) {
            return track;
        }
        Spec spec = read(track.contentUri);
        if (!spec.hasValue()) {
            spec = inferFromPath(track.dataPath);
        }
        if (!spec.hasValue()) {
            return track;
        }
        return new Track(
                track.id,
                track.title,
                track.artist,
                track.album,
                track.durationMs,
                track.contentUri,
                track.dataPath,
                track.albumId,
                track.albumArtUri,
                spec.codec,
                spec.bitrateKbps,
                spec.sampleRateHz,
                spec.bitsPerSample,
                spec.channelCount
        );
    }

    private Spec read(Uri uri) {
        Spec spec = new Spec();
        if (uri == null || Uri.EMPTY.equals(uri)) {
            return spec;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = stringValue(format, MediaFormat.KEY_MIME);
                if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("audio/")) {
                    continue;
                }
                spec.codec = codecFromMime(mime);
                spec.bitrateKbps = bitrateKbps(intValue(format, MediaFormat.KEY_BIT_RATE));
                spec.sampleRateHz = intValue(format, MediaFormat.KEY_SAMPLE_RATE);
                spec.channelCount = intValue(format, MediaFormat.KEY_CHANNEL_COUNT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    spec.bitsPerSample = intValue(format, MediaFormat.KEY_PCM_ENCODING);
                    spec.bitsPerSample = pcmBitsPerSample(spec.bitsPerSample);
                }
                break;
            }
        } catch (IOException | RuntimeException ignored) {
            // Some SAF providers/codecs cannot be inspected by MediaExtractor.
        } finally {
            try {
                extractor.release();
            } catch (RuntimeException ignored) {
                // release can throw on a few platform extractor implementations.
            }
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            if (spec.bitrateKbps <= 0) {
                spec.bitrateKbps = bitrateKbps(parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)));
            }
            if (spec.sampleRateHz <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                spec.sampleRateHz = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE));
            }
            if (spec.bitsPerSample <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                spec.bitsPerSample = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE));
            }
        } catch (RuntimeException ignored) {
            // Metadata parsing is best-effort.
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // Release can throw on some platform codecs.
            }
        }
        return spec;
    }

    private Spec inferFromPath(String path) {
        Spec spec = new Spec();
        if (path == null) {
            return spec;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) {
            return spec;
        }
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        if ("m4a".equals(extension)) {
            spec.codec = "aac";
        } else if ("oga".equals(extension)) {
            spec.codec = "ogg";
        } else {
            spec.codec = extension;
        }
        return spec;
    }

    private String stringValue(MediaFormat format, String key) {
        if (format == null || !format.containsKey(key)) {
            return null;
        }
        try {
            return format.getString(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int intValue(MediaFormat format, String key) {
        if (format == null || !format.containsKey(key)) {
            return 0;
        }
        try {
            return Math.max(format.getInteger(key), 0);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private int parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value.trim()), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int bitrateKbps(int bitrateBitsPerSecond) {
        if (bitrateBitsPerSecond <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(bitrateBitsPerSecond / 1000.0f));
    }

    private int pcmBitsPerSample(int encoding) {
        if (encoding == 2) {
            return 16;
        }
        if (encoding == 3) {
            return 8;
        }
        if (encoding == 4) {
            return 32;
        }
        if (encoding == 0x10000000) {
            return 24;
        }
        return 0;
    }

    private String codecFromMime(String mime) {
        if (mime == null) {
            return "";
        }
        String value = mime.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("audio/")) {
            value = value.substring("audio/".length());
        }
        if ("mpeg".equals(value)) {
            return "mp3";
        }
        if ("mp4a-latm".equals(value)) {
            return "aac";
        }
        return value;
    }

    private static final class Spec {
        String codec = "";
        int bitrateKbps;
        int sampleRateHz;
        int bitsPerSample;
        int channelCount;

        boolean hasValue() {
            return !codec.isEmpty() || bitrateKbps > 0 || sampleRateHz > 0 || bitsPerSample > 0 || channelCount > 0;
        }
    }
}
