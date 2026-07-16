package app.yukine.data;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses the bounded FLAC metadata prefix fetched during a WebDAV scan. */
final class FlacMetadataParser {
    private static final int STREAM_INFO = 0;
    private static final int PADDING = 1;
    private static final int VORBIS_COMMENT = 4;
    private static final int PICTURE = 6;
    private static final int FRONT_COVER = 3;
    private static final int MAX_ARTWORK_BYTES = 8 * 1024 * 1024;

    private FlacMetadataParser() {
    }

    static Result parse(byte[] prefix) {
        Result result = new Result();
        if (prefix == null || prefix.length < 4
                || prefix[0] != 'f' || prefix[1] != 'L'
                || prefix[2] != 'a' || prefix[3] != 'C') {
            return result;
        }
        result.recognized = true;
        int offset = 4;
        while (offset + 4 <= prefix.length) {
            int header = unsigned(prefix[offset]);
            boolean last = (header & 0x80) != 0;
            int type = header & 0x7f;
            int blockLength = readUInt24(prefix, offset + 1);
            int blockStart = offset + 4;
            long blockEndLong = (long) blockStart + blockLength;
            if (blockEndLong > prefix.length) {
                if (type != PADDING && blockEndLong <= Integer.MAX_VALUE) {
                    result.requiredPrefixBytes = (int) blockEndLong;
                }
                return result;
            }
            int blockEnd = (int) blockEndLong;
            if (type == STREAM_INFO) {
                parseStreamInfo(prefix, blockStart, blockLength, result);
            } else if (type == VORBIS_COMMENT) {
                parseVorbisComment(prefix, blockStart, blockEnd, result);
            } else if (type == PICTURE) {
                parsePicture(prefix, blockStart, blockEnd, result);
            }
            offset = blockEnd;
            if (last) {
                break;
            }
        }
        if (offset < prefix.length && offset + 4 > prefix.length) {
            result.requiredPrefixBytes = offset + 4;
        }
        return result;
    }

    private static void parseStreamInfo(byte[] data, int start, int length, Result result) {
        if (length < 34) {
            return;
        }
        int packed = start + 10;
        int sampleRate = (unsigned(data[packed]) << 12)
                | (unsigned(data[packed + 1]) << 4)
                | ((unsigned(data[packed + 2]) & 0xf0) >>> 4);
        int channels = ((unsigned(data[packed + 2]) & 0x0e) >>> 1) + 1;
        int bitsPerSample = (((unsigned(data[packed + 2]) & 0x01) << 4)
                | ((unsigned(data[packed + 3]) & 0xf0) >>> 4)) + 1;
        long totalSamples = ((long) (unsigned(data[packed + 3]) & 0x0f) << 32)
                | ((long) unsigned(data[packed + 4]) << 24)
                | ((long) unsigned(data[packed + 5]) << 16)
                | ((long) unsigned(data[packed + 6]) << 8)
                | unsigned(data[packed + 7]);
        result.sampleRateHz = sampleRate;
        result.channelCount = channels;
        result.bitsPerSample = bitsPerSample;
        if (sampleRate > 0 && totalSamples > 0L) {
            result.durationMs = Math.round(totalSamples * 1000.0d / sampleRate);
        }
    }

    private static void parseVorbisComment(byte[] data, int start, int end, Result result) {
        int cursor = start;
        if (cursor + 4 > end) {
            return;
        }
        int vendorLength = readLittleEndianInt(data, cursor);
        cursor += 4;
        if (vendorLength < 0 || cursor + (long) vendorLength + 4L > end) {
            return;
        }
        cursor += vendorLength;
        int commentCount = readLittleEndianInt(data, cursor);
        cursor += 4;
        if (commentCount < 0) {
            return;
        }
        for (int i = 0; i < commentCount && cursor + 4 <= end; i++) {
            int commentLength = readLittleEndianInt(data, cursor);
            cursor += 4;
            if (commentLength < 0 || cursor + (long) commentLength > end) {
                return;
            }
            String comment = new String(data, cursor, commentLength, StandardCharsets.UTF_8);
            cursor += commentLength;
            int separator = comment.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = comment.substring(0, separator).trim().toUpperCase(Locale.ROOT);
            String value = comment.substring(separator + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            if ("TITLE".equals(key) && result.title.isEmpty()) {
                result.title = value;
            } else if ("ARTIST".equals(key) && result.artist.isEmpty()) {
                result.artist = value;
            } else if (("ALBUMARTIST".equals(key) || "ALBUM_ARTIST".equals(key))
                    && result.albumArtist.isEmpty()) {
                result.albumArtist = value;
            } else if ("ALBUM".equals(key) && result.album.isEmpty()) {
                result.album = value;
            } else if (("MUSICBRAINZ_TRACKID".equals(key)
                    || "MUSICBRAINZ_RECORDINGID".equals(key))
                    && result.recordingMusicBrainzId.isEmpty()) {
                result.recordingMusicBrainzId = value;
            } else if (("MUSICBRAINZ_WORKID".equals(key)
                    || "MUSICBRAINZ_RECORDING_WORK_ID".equals(key))
                    && result.workMusicBrainzId.isEmpty()) {
                result.workMusicBrainzId = value;
            } else if ("ISRC".equals(key) && result.isrc.isEmpty()) {
                result.isrc = value;
            } else if (("ACOUSTID_ID".equals(key) || "ACOUSTID".equals(key))
                    && result.acoustId.isEmpty()) {
                result.acoustId = value;
            } else if ("MUSICBRAINZ_ARTISTID".equals(key)) {
                addValues(result.artistMusicBrainzIds, value);
            } else if ("REPLAYGAIN_TRACK_GAIN".equals(key)) {
                result.replayGainTrackDb = parseGain(value);
            } else if ("REPLAYGAIN_ALBUM_GAIN".equals(key)) {
                result.replayGainAlbumDb = parseGain(value);
            }
        }
        if (result.artist.isEmpty()) {
            result.artist = result.albumArtist;
        }
    }

    private static void parsePicture(byte[] data, int start, int end, Result result) {
        int cursor = start;
        if (cursor + 8 > end) {
            return;
        }
        int pictureType = readBigEndianInt(data, cursor);
        cursor += 4;
        int mimeLength = readBigEndianInt(data, cursor);
        cursor += 4;
        if (!advanceWithin(cursor, mimeLength, end)) {
            return;
        }
        cursor += mimeLength;
        if (cursor + 4 > end) {
            return;
        }
        int descriptionLength = readBigEndianInt(data, cursor);
        cursor += 4;
        if (!advanceWithin(cursor, descriptionLength, end)) {
            return;
        }
        cursor += descriptionLength;
        if (cursor + 20 > end) {
            return;
        }
        cursor += 16;
        int pictureLength = readBigEndianInt(data, cursor);
        cursor += 4;
        if (pictureLength <= 0 || pictureLength > MAX_ARTWORK_BYTES
                || !advanceWithin(cursor, pictureLength, end)) {
            return;
        }
        if (result.artwork == null || pictureType == FRONT_COVER) {
            result.artwork = Arrays.copyOfRange(data, cursor, cursor + pictureLength);
        }
    }

    private static boolean advanceWithin(int cursor, int length, int end) {
        return length >= 0 && cursor + (long) length <= end;
    }

    private static float parseGain(String value) {
        String clean = value.toLowerCase(Locale.ROOT).replace("db", "").trim();
        try {
            float parsed = Float.parseFloat(clean);
            return Float.isFinite(parsed) ? parsed : 0.0f;
        } catch (NumberFormatException ignored) {
            return 0.0f;
        }
    }

    private static void addValues(List<String> target, String raw) {
        if (target == null || raw == null) {
            return;
        }
        for (String value : raw.split("[;,\\s]+")) {
            String clean = value.trim();
            if (!clean.isEmpty() && !target.contains(clean)) {
                target.add(clean);
            }
        }
    }

    private static int readUInt24(byte[] data, int offset) {
        return (unsigned(data[offset]) << 16)
                | (unsigned(data[offset + 1]) << 8)
                | unsigned(data[offset + 2]);
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return unsigned(data[offset])
                | (unsigned(data[offset + 1]) << 8)
                | (unsigned(data[offset + 2]) << 16)
                | (unsigned(data[offset + 3]) << 24);
    }

    private static int readBigEndianInt(byte[] data, int offset) {
        return (unsigned(data[offset]) << 24)
                | (unsigned(data[offset + 1]) << 16)
                | (unsigned(data[offset + 2]) << 8)
                | unsigned(data[offset + 3]);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    static final class Result {
        boolean recognized;
        String title = "";
        String artist = "";
        String albumArtist = "";
        String album = "";
        String recordingMusicBrainzId = "";
        String workMusicBrainzId = "";
        String isrc = "";
        String acoustId = "";
        final ArrayList<String> artistMusicBrainzIds = new ArrayList<>();
        long durationMs;
        int sampleRateHz;
        int bitsPerSample;
        int channelCount;
        float replayGainTrackDb;
        float replayGainAlbumDb;
        byte[] artwork;
        int requiredPrefixBytes;
    }
}
