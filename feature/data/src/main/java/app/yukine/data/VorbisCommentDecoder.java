package app.yukine.data;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Shared Vorbis Comment block decoder used by FLAC and OGG/Opus remote metadata parsers.
 * Parses the standard Vorbis Comment format: vendor string + key=value comment list.
 */
final class VorbisCommentDecoder {
    private VorbisCommentDecoder() {
    }

    static void decode(byte[] data, int start, int end, FlacMetadataParser.Result result) {
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
            applyComment(key, value, result);
        }
        if (result.artist.isEmpty()) {
            result.artist = result.albumArtist;
        }
    }

    private static void applyComment(String key, String value, FlacMetadataParser.Result result) {
        if ("TITLE".equals(key) && result.title.isEmpty()) {
            result.title = value;
        } else if ("ARTIST".equals(key) && result.artist.isEmpty()) {
            result.artist = value;
        } else if (("ALBUMARTIST".equals(key) || "ALBUM_ARTIST".equals(key))
                && result.albumArtist.isEmpty()) {
            result.albumArtist = value;
        } else if ("ALBUM".equals(key) && result.album.isEmpty()) {
            result.album = value;
        } else if ("COMPOSER".equals(key) && result.composer.isEmpty()) {
            result.composer = value;
        } else if (("RELEASETYPE".equals(key)
                || "RELEASE_TYPE".equals(key)
                || "MUSICBRAINZ_ALBUMTYPE".equals(key))
                && result.releaseType.isEmpty()) {
            result.releaseType = value;
        } else if (("DATE".equals(key) || "YEAR".equals(key)) && result.year == 0) {
            result.year = parseYear(value);
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
        } else if ("GENRE".equals(key) && result.genre.isEmpty()) {
            result.genre = value;
        } else if (("DISCNUMBER".equals(key) || "DISC_NUMBER".equals(key))
                && result.discNumber == 0) {
            result.discNumber = parseLeadingInt(value);
        } else if (("TRACKNUMBER".equals(key) || "TRACK_NUMBER".equals(key))
                && result.trackNumber == 0) {
            result.trackNumber = parseLeadingInt(value);
        } else if ("BPM".equals(key) && result.bpm == 0) {
            result.bpm = parseLeadingInt(value);
        } else if (("LYRICS".equals(key) || "UNSYNCEDLYRICS".equals(key))
                && result.lyrics.isEmpty()) {
            result.lyrics = value;
        }
    }

    private static int parseLeadingInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        // Handle "1/2" format (e.g., disc 1 of 2)
        int slash = value.indexOf('/');
        String numeric = slash > 0 ? value.substring(0, slash).trim() : value;
        try {
            return Math.max(Integer.parseInt(numeric), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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

    private static int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static int parseYear(String value) {
        if (value == null || value.length() < 4) {
            return 0;
        }
        try {
            int year = Integer.parseInt(value.substring(0, 4));
            return year >= 1000 && year <= 9999 ? year : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
