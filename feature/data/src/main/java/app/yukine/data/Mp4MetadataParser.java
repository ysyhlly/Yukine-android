package app.yukine.data;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Parses MP4/M4A/AAC atom structure from a bounded prefix fetched during WebDAV scan. */
final class Mp4MetadataParser {
    private static final int MAX_ARTWORK_BYTES = 8 * 1024 * 1024;

    private Mp4MetadataParser() {
    }

    static FlacMetadataParser.Result parse(byte[] prefix) {
        FlacMetadataParser.Result result = new FlacMetadataParser.Result();
        if (prefix == null || prefix.length < 12) {
            return result;
        }
        // Verify ftyp box
        int ftypSize = readInt(prefix, 0);
        if (ftypSize < 8 || !isAtomType(prefix, 4, "ftyp")) {
            return result;
        }
        result.recognized = true;
        // Walk top-level atoms to find moov
        int cursor = 0;
        boolean foundMoov = false;
        while (cursor + 8 <= prefix.length) {
            int atomSize = readInt(prefix, cursor);
            if (atomSize < 8) {
                break;
            }
            if (isAtomType(prefix, cursor + 4, "moov")) {
                int moovEnd = cursor + atomSize;
                if (moovEnd > prefix.length) {
                    // moov extends beyond prefix
                    result.requiredPrefixBytes = moovEnd;
                    parseMoov(prefix, cursor + 8, prefix.length, result);
                } else {
                    parseMoov(prefix, cursor + 8, moovEnd, result);
                }
                foundMoov = true;
                break;
            }
            if (isAtomType(prefix, cursor + 4, "mdat")) {
                // moov might be after mdat (not faststart)
                long mdatEnd = (long) cursor + atomSize;
                if (mdatEnd > prefix.length && !foundMoov) {
                    // moov likely at file end; cannot fetch with prefix-only strategy
                    // Return what we have (best-effort)
                    return result;
                }
            }
            cursor += atomSize;
        }
        return result;
    }

    private static void parseMoov(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "mvhd")) {
                parseMvhd(data, cursor + 8, cursor + atomSize, result);
            } else if (isAtomType(data, cursor + 4, "trak")) {
                parseTrak(data, cursor + 8, cursor + atomSize, result);
            } else if (isAtomType(data, cursor + 4, "udta")) {
                parseUdta(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseMvhd(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        if (start + 4 > end) {
            return;
        }
        int version = data[start] & 0xff;
        try {
            if (version == 1) {
                if (start + 28 > end) return;
                int timescale = readInt(data, start + 20);
                long duration = readLong(data, start + 24);
                if (timescale > 0 && duration > 0) {
                    result.durationMs = duration * 1000L / timescale;
                }
            } else {
                if (start + 20 > end) return;
                int timescale = readInt(data, start + 12);
                long duration = readInt(data, start + 16) & 0xFFFFFFFFL;
                if (timescale > 0 && duration > 0) {
                    result.durationMs = duration * 1000L / timescale;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void parseTrak(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "mdia")) {
                parseMdia(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseMdia(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "mdhd")) {
                parseMdhd(data, cursor + 8, cursor + atomSize, result);
            } else if (isAtomType(data, cursor + 4, "minf")) {
                parseMinf(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseMdhd(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        if (start + 4 > end) {
            return;
        }
        int version = data[start] & 0xff;
        try {
            if (version == 1) {
                if (start + 28 > end) return;
                int timescale = readInt(data, start + 20);
                long duration = readLong(data, start + 24);
                if (timescale > 0 && duration > 0 && result.durationMs == 0) {
                    result.durationMs = duration * 1000L / timescale;
                }
            } else {
                if (start + 20 > end) return;
                int timescale = readInt(data, start + 12);
                long duration = readInt(data, start + 16) & 0xFFFFFFFFL;
                if (timescale > 0 && duration > 0 && result.durationMs == 0) {
                    result.durationMs = duration * 1000L / timescale;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void parseMinf(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "stbl")) {
                parseStbl(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseStbl(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "stsd")) {
                parseStsd(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseStsd(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        // stsd: version(4) + entry_count(4) + entries
        if (start + 16 > end) {
            return;
        }
        int entryStart = start + 8; // skip version + entry_count
        if (entryStart + 8 > end) {
            return;
        }
        // Audio sample entry: size(4) + format(4) + reserved(6) + data_ref_index(2) + ...
        // Then: version(2) + revision(2) + vendor(4) + channels(4) + sampleSize(2) + ...
        //       + sampleRate(4 as 16.16 fixed point)
        int audioEntry = entryStart + 8; // skip size + format
        if (audioEntry + 28 > end) {
            return;
        }
        // channels at offset 16 from audio sample entry start (after 6 reserved + 2 data_ref)
        int channels = readShort(data, audioEntry + 16);
        int sampleSize = readShort(data, audioEntry + 18);
        // sample rate at offset 24 as 16.16 fixed point
        int sampleRateFixed = readInt(data, audioEntry + 24);
        int sampleRate = sampleRateFixed >> 16;
        if (channels > 0) {
            result.channelCount = channels;
        }
        if (sampleRate > 0) {
            result.sampleRateHz = sampleRate;
        }
        if (sampleSize > 0) {
            result.bitsPerSample = sampleSize;
        }
    }

    private static void parseUdta(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "meta")) {
                parseMeta(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseMeta(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        // meta has a 4-byte version/flags field before children
        int cursor = start + 4;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "ilst")) {
                parseIlst(data, cursor + 8, cursor + atomSize, result);
            }
            cursor += atomSize;
        }
    }

    private static void parseIlst(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        int cursor = start;
        while (cursor + 8 <= end) {
            int atomSize = readInt(data, cursor);
            if (atomSize < 8 || cursor + atomSize > end) {
                break;
            }
            String type = atomTypeName(data, cursor + 4);
            int dataStart = cursor + 8;
            int dataEnd = cursor + atomSize;
            applyIlstAtom(type, data, dataStart, dataEnd, result);
            cursor += atomSize;
        }
    }

    private static void applyIlstAtom(
            String type, byte[] data, int start, int end, FlacMetadataParser.Result result
    ) {
        // Each ilst child contains a "data" atom: size(4) + "data"(4) + type(4) + locale(4) + value
        int cursor = start;
        while (cursor + 16 <= end) {
            int dataSize = readInt(data, cursor);
            if (dataSize < 16 || cursor + dataSize > end) {
                break;
            }
            if (isAtomType(data, cursor + 4, "data")) {
                int dataType = readInt(data, cursor + 8);
                int valueStart = cursor + 16;
                int valueEnd = cursor + dataSize;
                if (valueStart > valueEnd) {
                    break;
                }
                applyValue(type, dataType, data, valueStart, valueEnd, result);
            }
            cursor += dataSize;
        }
    }

    private static void applyValue(
            String type, int dataType, byte[] data, int start, int end,
            FlacMetadataParser.Result result
    ) {
        switch (type) {
            case "\u00A9nam": // ©nam - title
                if (result.title.isEmpty()) result.title = utf8(data, start, end);
                break;
            case "\u00A9ART": // ©ART - artist
                if (result.artist.isEmpty()) result.artist = utf8(data, start, end);
                break;
            case "\u00A9alb": // ©alb - album
                if (result.album.isEmpty()) result.album = utf8(data, start, end);
                break;
            case "aART": // album artist
                if (result.albumArtist.isEmpty()) result.albumArtist = utf8(data, start, end);
                break;
            case "\u00A9wrt": // ©wrt - composer
                if (result.composer.isEmpty()) result.composer = utf8(data, start, end);
                break;
            case "\u00A9day": // ©day - year
                if (result.year == 0) result.year = parseYear(utf8(data, start, end));
                break;
            case "\u00A9gen": // ©gen - genre
                if (result.genre.isEmpty()) result.genre = utf8(data, start, end);
                break;
            case "\u00A9lyr": // ©lyr - lyrics
                if (result.lyrics.isEmpty()) result.lyrics = utf8(data, start, end);
                break;
            case "disk": // disc number
                if (result.discNumber == 0 && start + 4 <= end) {
                    result.discNumber = readShort(data, start + 2);
                }
                break;
            case "trkn": // track number
                if (result.trackNumber == 0 && start + 4 <= end) {
                    result.trackNumber = readShort(data, start + 2);
                }
                break;
            case "tmpo": // BPM
                if (result.bpm == 0 && start + 2 <= end) {
                    result.bpm = readShort(data, start);
                }
                break;
            case "covr": // cover artwork
                if (result.artwork == null) {
                    int length = end - start;
                    if (length > 0 && length <= MAX_ARTWORK_BYTES) {
                        result.artwork = Arrays.copyOfRange(data, start, end);
                    }
                }
                break;
            default:
                break;
        }
    }

    private static String utf8(byte[] data, int start, int end) {
        if (start >= end) {
            return "";
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8).trim();
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

    private static boolean isAtomType(byte[] data, int offset, String type) {
        if (offset + 4 > data.length) {
            return false;
        }
        return data[offset] == type.charAt(0)
                && data[offset + 1] == type.charAt(1)
                && data[offset + 2] == type.charAt(2)
                && data[offset + 3] == type.charAt(3);
    }

    private static String atomTypeName(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return "";
        }
        return new String(data, offset, 4, StandardCharsets.ISO_8859_1);
    }

    private static int readInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static long readLong(byte[] data, int offset) {
        if (offset + 8 > data.length) {
            return 0;
        }
        return ((long) readInt(data, offset) << 32) | (readInt(data, offset + 4) & 0xFFFFFFFFL);
    }

    private static int readShort(byte[] data, int offset) {
        if (offset + 2 > data.length) {
            return 0;
        }
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }
}
