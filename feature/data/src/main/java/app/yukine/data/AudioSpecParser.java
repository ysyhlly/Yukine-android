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
import app.yukine.model.TrackIdentityTags;

final class AudioSpecParser {
    private final Context context;
    private final ReplayGainParser replayGainParser;
    private final PortableAudioMetadataReader portableMetadataReader;

    AudioSpecParser(Context context) {
        this.context = context.getApplicationContext();
        this.replayGainParser = new ReplayGainParser(this.context);
        this.portableMetadataReader = new PortableAudioMetadataReader(this.context);
    }

    Track enrich(Track track) {
        if (track == null) {
            return track;
        }
        if (isStreamingTrack(track)) {
            return track;
        }
        Spec spec = track.needsAudioSpecParsing() ? read(track.contentUri) : Spec.fromTrack(track);
        if (!spec.hasAudioSpec()) {
            Spec inferred = inferFromPath(track.dataPath);
            if (inferred.hasAudioSpec()) {
                spec.codec = inferred.codec;
            }
        }
        ReplayGainParser.ReplayGain replayGain = ReplayGainParser.hasGain(track.replayGainTrackDb)
                || ReplayGainParser.hasGain(track.replayGainAlbumDb)
                ? new ReplayGainParser.ReplayGain(track.replayGainTrackDb, track.replayGainAlbumDb)
                : replayGainParser.read(track.contentUri);
        PortableAudioMetadataReader.Metadata portable = portableMetadataReader.read(
                track.contentUri,
                displayName(track.dataPath),
                track.dataPath,
                false
        );
        TrackIdentityTags identityTags = track.identityTags;
        if (identityTags == null || identityTags.isEmpty()) {
            identityTags = portable.identityTags;
        }
        String albumArtist = firstNonBlank(track.albumArtist, portable.albumArtist);
        String composer = firstNonBlank(track.composer, portable.composer);
        String releaseType = firstNonBlank(track.releaseType, portable.releaseType);
        int year = track.year > 0 ? track.year : portable.year;
        String genre = firstNonBlank(track.genre, portable.genre);
        int discNumber = track.discNumber > 0 ? track.discNumber : portable.discNumber;
        int trackNumber = track.trackNumber > 0 ? track.trackNumber : portable.trackNumber;
        int bpm = track.bpm > 0 ? track.bpm : portable.bpm;
        String lyrics = track.lyrics.isEmpty() ? portable.lyrics : track.lyrics;
        boolean metadataChanged = !albumArtist.equals(track.albumArtist)
                || !composer.equals(track.composer)
                || !releaseType.equals(track.releaseType)
                || year != track.year
                || !genre.equals(track.genre)
                || discNumber != track.discNumber
                || trackNumber != track.trackNumber
                || bpm != track.bpm
                || !lyrics.equals(track.lyrics);
        if (!spec.hasAudioSpec() && !replayGain.hasValue() && identityTags.isEmpty()
                && !metadataChanged) {
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
                spec.channelCount,
                replayGain.trackDb,
                replayGain.albumDb,
                identityTags,
                albumArtist,
                composer,
                releaseType,
                year,
                track.updatedAt,
                genre,
                discNumber,
                trackNumber,
                bpm,
                lyrics
        );
    }

    private static String displayName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty()
                ? fallback == null ? "" : fallback.trim()
                : preferred.trim();
    }

    private boolean isStreamingTrack(Track track) {
        return track != null
                && track.dataPath != null
                && track.dataPath.startsWith("streaming:");
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
        } else if ("dsf".equals(extension) || "dff".equals(extension)) {
            spec.codec = "dsd";
        } else if ("wv".equals(extension)) {
            spec.codec = "wavpack";
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

        static Spec fromTrack(Track track) {
            Spec spec = new Spec();
            if (track == null) {
                return spec;
            }
            spec.codec = track.codec;
            spec.bitrateKbps = track.bitrateKbps;
            spec.sampleRateHz = track.sampleRateHz;
            spec.bitsPerSample = track.bitsPerSample;
            spec.channelCount = track.channelCount;
            return spec;
        }

        boolean hasAudioSpec() {
            return !codec.isEmpty() || bitrateKbps > 0 || sampleRateHz > 0 || bitsPerSample > 0 || channelCount > 0;
        }
    }
}
