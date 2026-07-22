package app.yukine.data;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Parses ID3v2 tags and MPEG frame headers from a bounded MP3 prefix fetched during WebDAV scan. */
final class Mp3MetadataParser {
    private static final int MAX_ARTWORK_BYTES = 8 * 1024 * 1024;
    private static final int[] SAMPLE_RATES = {
            44100, 48000, 32000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private static final int[] BITRATES_V1_L3 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    };

    private Mp3MetadataParser() {
    }

    static FlacMetadataParser.Result parse(byte[] prefix) {
        FlacMetadataParser.Result result = new FlacMetadataParser.Result();
        if (prefix == null || prefix.length < 10) {
            return result;
        }
        int tagEnd = 0;
        if (prefix[0] == 'I' && prefix[1] == 'D' && prefix[2] == '3') {
            result.recognized = true;
            int version = prefix[3] & 0xff;
            int flags = prefix[5] & 0xff;
            int tagSize = syncsafe(prefix, 6);
            tagEnd = 10 + tagSize;
            if (tagEnd > prefix.length) {
                result.requiredPrefixBytes = tagEnd;
                // Parse what we have
                parseFrames(prefix, 10, prefix.length, version, flags, result);
                return result;
            }
            boolean hasExtendedHeader = (flags & 0x40) != 0;
            int frameStart = 10;
            if (hasExtendedHeader && frameStart + 4 <= prefix.length) {
                int extSize;
                if (version >= 4) {
                    extSize = syncsafe(prefix, frameStart);
                    frameStart += extSize;
                } else {
                    extSize = readBigEndianInt(prefix, frameStart);
                    frameStart += 4 + extSize;
                }
            }
            parseFrames(prefix, frameStart, tagEnd, version, flags, result);
        }
        // Parse MPEG frame header for audio specs
        int searchStart = tagEnd > 0 ? tagEnd : 0;
        parseMpegHeader(prefix, searchStart, result);
        if (!result.recognized && result.sampleRateHz > 0) {
            result.recognized = true;
        }
        return result;
    }

    private static void parseFrames(
            byte[] data, int start, int end, int version, int tagFlags,
            FlacMetadataParser.Result result
    ) {
        boolean unsync = (tagFlags & 0x80) != 0;
        int cursor = start;
        int frameHeaderSize = version >= 3 ? 10 : 6;
        while (cursor + frameHeaderSize <= end) {
            // Check for padding
            if (data[cursor] == 0) {
                break;
            }
            String frameId;
            int frameSize;
            int frameFlags = 0;
            if (version >= 3) {
                frameId = new String(data, cursor, 4, StandardCharsets.ISO_8859_1);
                if (version == 4) {
                    frameSize = syncsafe(data, cursor + 4);
                } else {
                    frameSize = readBigEndianInt(data, cursor + 4);
                }
                frameFlags = ((data[cursor + 8] & 0xff) << 8) | (data[cursor + 9] & 0xff);
            } else {
                frameId = new String(data, cursor, 3, StandardCharsets.ISO_8859_1);
                frameSize = (data[cursor + 3] & 0xff) << 16
                        | (data[cursor + 4] & 0xff) << 8
                        | (data[cursor + 5] & 0xff);
            }
            cursor += frameHeaderSize;
            if (frameSize <= 0 || cursor + frameSize > end) {
                if (frameSize > 0 && cursor + frameSize > end) {
                    // Frame extends beyond our prefix; request more if worthwhile
                    if (result.artwork == null && "APIC".equals(frameId)) {
                        result.requiredPrefixBytes = Math.max(
                                result.requiredPrefixBytes, cursor + frameSize);
                    }
                }
                break;
            }
            int frameEnd = cursor + frameSize;
            // Skip compressed or encrypted frames
            if (version >= 3 && (frameFlags & 0x0080) != 0) {
                cursor = frameEnd;
                continue;
            }
            if (version >= 3 && (frameFlags & 0x0040) != 0) {
                cursor = frameEnd;
                continue;
            }
            applyFrame(frameId, data, cursor, frameEnd, unsync, result);
            cursor = frameEnd;
        }
    }

    private static void applyFrame(
            String frameId, byte[] data, int start, int end,
            boolean globalUnsync, FlacMetadataParser.Result result
    ) {
        switch (frameId) {
            case "TIT2": case "TT2":
                if (result.title.isEmpty()) result.title = decodeText(data, start, end);
                break;
            case "TPE1": case "TP1":
                if (result.artist.isEmpty()) result.artist = decodeText(data, start, end);
                break;
            case "TPE2": case "TP2":
                if (result.albumArtist.isEmpty()) result.albumArtist = decodeText(data, start, end);
                break;
            case "TALB": case "TAL":
                if (result.album.isEmpty()) result.album = decodeText(data, start, end);
                break;
            case "TCOM": case "TCM":
                if (result.composer.isEmpty()) result.composer = decodeText(data, start, end);
                break;
            case "TDRC": case "TYER": case "TYE":
                if (result.year == 0) result.year = parseYear(decodeText(data, start, end));
                break;
            case "TCON": case "TCO":
                if (result.genre.isEmpty()) result.genre = decodeGenre(decodeText(data, start, end));
                break;
            case "TPOS": case "TPA":
                if (result.discNumber == 0) result.discNumber = parseLeadingInt(decodeText(data, start, end));
                break;
            case "TRCK": case "TRK":
                if (result.trackNumber == 0) result.trackNumber = parseLeadingInt(decodeText(data, start, end));
                break;
            case "TBPM": case "TBP":
                if (result.bpm == 0) result.bpm = parseLeadingInt(decodeText(data, start, end));
                break;
            case "USLT": case "ULT":
                if (result.lyrics.isEmpty()) result.lyrics = decodeLyrics(data, start, end);
                break;
            case "APIC": case "PIC":
                parseApic(data, start, end, result);
                break;
            default:
                break;
        }
    }

    private static String decodeText(byte[] data, int start, int end) {
        if (start >= end) {
            return "";
        }
        int encoding = data[start] & 0xff;
        int textStart = start + 1;
        if (textStart >= end) {
            return "";
        }
        try {
            String raw;
            switch (encoding) {
                case 0: // ISO-8859-1
                    raw = new String(data, textStart, end - textStart, StandardCharsets.ISO_8859_1);
                    break;
                case 1: // UTF-16 with BOM
                    raw = decodeUtf16WithBom(data, textStart, end);
                    break;
                case 2: // UTF-16BE without BOM
                    raw = new String(data, textStart, end - textStart, StandardCharsets.UTF_16BE);
                    break;
                case 3: // UTF-8
                    raw = new String(data, textStart, end - textStart, StandardCharsets.UTF_8);
                    break;
                default:
                    raw = new String(data, textStart, end - textStart, StandardCharsets.ISO_8859_1);
                    break;
            }
            // Strip null terminators
            int nullIndex = raw.indexOf('\0');
            if (nullIndex >= 0) {
                raw = raw.substring(0, nullIndex);
            }
            return raw.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String decodeUtf16WithBom(byte[] data, int start, int end) {
        int length = end - start;
        if (length < 2) {
            return "";
        }
        // Check BOM
        if ((data[start] & 0xff) == 0xFF && (data[start + 1] & 0xff) == 0xFE) {
            return new String(data, start + 2, length - 2, StandardCharsets.UTF_16LE);
        }
        if ((data[start] & 0xff) == 0xFE && (data[start + 1] & 0xff) == 0xFF) {
            return new String(data, start + 2, length - 2, StandardCharsets.UTF_16BE);
        }
        // No BOM, assume UTF-16LE (most common in ID3v2.3)
        return new String(data, start, length, StandardCharsets.UTF_16LE);
    }

    private static String decodeLyrics(byte[] data, int start, int end) {
        if (start + 4 >= end) {
            return "";
        }
        int encoding = data[start] & 0xff;
        // Skip encoding(1) + language(3) + content descriptor (null-terminated)
        int cursor = start + 4;
        // Skip content descriptor
        cursor = skipNullTerminator(data, cursor, end, encoding);
        if (cursor >= end) {
            return "";
        }
        try {
            String raw;
            switch (encoding) {
                case 0:
                    raw = new String(data, cursor, end - cursor, StandardCharsets.ISO_8859_1);
                    break;
                case 1:
                    raw = decodeUtf16WithBom(data, cursor, end);
                    break;
                case 3:
                    raw = new String(data, cursor, end - cursor, StandardCharsets.UTF_8);
                    break;
                default:
                    raw = new String(data, cursor, end - cursor, StandardCharsets.ISO_8859_1);
                    break;
            }
            int nullIndex = raw.indexOf('\0');
            if (nullIndex >= 0) {
                raw = raw.substring(0, nullIndex);
            }
            return raw.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int skipNullTerminator(byte[] data, int cursor, int end, int encoding) {
        if (encoding == 1 || encoding == 2) {
            // UTF-16: null terminator is 2 bytes
            while (cursor + 1 < end) {
                if (data[cursor] == 0 && data[cursor + 1] == 0) {
                    return cursor + 2;
                }
                cursor += 2;
            }
        } else {
            while (cursor < end) {
                if (data[cursor] == 0) {
                    return cursor + 1;
                }
                cursor++;
            }
        }
        return end;
    }

    private static void parseApic(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        if (start >= end) {
            return;
        }
        int cursor = start;
        // int encoding = data[cursor] & 0xff;
        cursor++;
        // Skip MIME type (null-terminated ISO-8859-1)
        while (cursor < end && data[cursor] != 0) {
            cursor++;
        }
        cursor++; // skip null
        if (cursor >= end) {
            return;
        }
        int pictureType = data[cursor] & 0xff;
        cursor++;
        // Skip description (null-terminated, encoding-dependent)
        int encoding = data[start] & 0xff;
        cursor = skipNullTerminator(data, cursor, end, encoding);
        if (cursor >= end) {
            return;
        }
        int pictureLength = end - cursor;
        if (pictureLength <= 0 || pictureLength > MAX_ARTWORK_BYTES) {
            return;
        }
        if (result.artwork == null || pictureType == 3) {
            result.artwork = Arrays.copyOfRange(data, cursor, end);
        }
    }

    private static void parseMpegHeader(byte[] data, int searchStart, FlacMetadataParser.Result result) {
        // Search for MPEG frame sync (0xFF 0xEx) within first 4KB after ID3 tag
        int searchEnd = Math.min(data.length, searchStart + 4096);
        for (int i = searchStart; i + 4 <= searchEnd; i++) {
            if ((data[i] & 0xff) != 0xFF) {
                continue;
            }
            int header = readBigEndianInt(data, i);
            if ((header & 0xFFE00000) != 0xFFE00000) {
                continue;
            }
            int versionBits = (header >> 19) & 0x03;
            int layerBits = (header >> 17) & 0x03;
            int bitrateIndex = (header >> 12) & 0x0F;
            int sampleRateIndex = (header >> 10) & 0x03;
            int channelMode = (header >> 6) & 0x03;
            if (layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
                continue;
            }
            // MPEG1 Layer3
            if (versionBits == 3 && layerBits == 1) {
                result.sampleRateHz = SAMPLE_RATES[sampleRateIndex];
                int bitrateKbps = BITRATES_V1_L3[bitrateIndex];
                if (bitrateKbps > 0 && result.durationMs == 0) {
                    // Estimate from bitrate if no Xing header found
                }
                result.channelCount = channelMode == 3 ? 1 : 2;
                // Look for Xing/Info header for duration
                parseXingHeader(data, i, versionBits, channelMode, result);
                return;
            }
            // MPEG2/2.5 Layer3
            if ((versionBits == 2 || versionBits == 0) && layerBits == 1) {
                int sr = SAMPLE_RATES[sampleRateIndex];
                result.sampleRateHz = versionBits == 0 ? sr / 4 : sr / 2;
                result.channelCount = channelMode == 3 ? 1 : 2;
                parseXingHeader(data, i, versionBits, channelMode, result);
                return;
            }
        }
    }

    private static void parseXingHeader(
            byte[] data, int frameStart, int versionBits, int channelMode,
            FlacMetadataParser.Result result
    ) {
        // Xing/Info header offset depends on MPEG version and channel mode
        int offset;
        if (versionBits == 3) { // MPEG1
            offset = channelMode == 3 ? 21 : 36;
        } else { // MPEG2/2.5
            offset = channelMode == 3 ? 13 : 21;
        }
        int xingStart = frameStart + 4 + offset;
        if (xingStart + 16 > data.length) {
            return;
        }
        // Check for "Xing" or "Info" magic
        if (data[xingStart] == 'X' && data[xingStart + 1] == 'i'
                && data[xingStart + 2] == 'n' && data[xingStart + 3] == 'g'
                || data[xingStart] == 'I' && data[xingStart + 1] == 'n'
                && data[xingStart + 2] == 'f' && data[xingStart + 3] == 'o') {
            int flags = readBigEndianInt(data, xingStart + 4);
            int cursor = xingStart + 8;
            if ((flags & 0x01) != 0 && cursor + 4 <= data.length) {
                int totalFrames = readBigEndianInt(data, cursor);
                cursor += 4;
                // Calculate duration: frames * samples_per_frame / sample_rate
                if (result.sampleRateHz > 0 && totalFrames > 0) {
                    int samplesPerFrame = versionBits == 3 ? 1152 : 576;
                    result.durationMs = (long) totalFrames * samplesPerFrame * 1000L / result.sampleRateHz;
                }
            }
        }
    }

    private static String decodeGenre(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // ID3v1 genre reference format: "(123)" or "(123)Genre Name"
        if (raw.startsWith("(")) {
            int close = raw.indexOf(')');
            if (close > 0) {
                String after = raw.substring(close + 1).trim();
                if (!after.isEmpty()) {
                    return after;
                }
                // Just a number reference; return raw without parens
                return raw.substring(1, close).trim();
            }
        }
        return raw;
    }

    private static int parseLeadingInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int slash = value.indexOf('/');
        String numeric = slash > 0 ? value.substring(0, slash).trim() : value;
        try {
            return Math.max(Integer.parseInt(numeric), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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

    private static int syncsafe(byte[] data, int offset) {
        return ((data[offset] & 0x7f) << 21)
                | ((data[offset + 1] & 0x7f) << 14)
                | ((data[offset + 2] & 0x7f) << 7)
                | (data[offset + 3] & 0x7f);
    }

    private static int readBigEndianInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }
}
