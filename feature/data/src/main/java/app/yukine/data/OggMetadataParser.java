package app.yukine.data;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Parses OGG Vorbis and Opus metadata from a bounded prefix fetched during WebDAV scan. */
final class OggMetadataParser {
    private static final int MAX_ARTWORK_BYTES = 8 * 1024 * 1024;
    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
    private static final byte[] VORBIS_ID_MAGIC = {0x01, 'v', 'o', 'r', 'b', 'i', 's'};
    private static final byte[] VORBIS_COMMENT_MAGIC = {0x03, 'v', 'o', 'r', 'b', 'i', 's'};
    private static final byte[] OPUS_HEAD_MAGIC = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
    private static final byte[] OPUS_TAGS_MAGIC = {'O', 'p', 'u', 's', 'T', 'a', 'g', 's'};

    private OggMetadataParser() {
    }

    static FlacMetadataParser.Result parse(byte[] prefix) {
        FlacMetadataParser.Result result = new FlacMetadataParser.Result();
        if (prefix == null || prefix.length < 27) {
            return result;
        }
        if (!isOggPage(prefix, 0)) {
            return result;
        }
        result.recognized = true;
        // Determine codec from first page payload
        int payloadStart = pagePayloadStart(prefix, 0);
        if (payloadStart < 0) {
            return result;
        }
        if (matches(prefix, payloadStart, VORBIS_ID_MAGIC)) {
            parseVorbisIdHeader(prefix, payloadStart, result);
            // Find Vorbis Comment in subsequent pages
            parseVorbisPages(prefix, result);
        } else if (matches(prefix, payloadStart, OPUS_HEAD_MAGIC)) {
            parseOpusHead(prefix, payloadStart, result);
            // Find OpusTags in subsequent pages
            parseOpusPages(prefix, result);
        }
        return result;
    }

    private static void parseVorbisIdHeader(byte[] data, int start, FlacMetadataParser.Result result) {
        // Vorbis identification header: magic(7) + version(4) + channels(1) + sampleRate(4) + ...
        int offset = start + 7;
        if (offset + 9 > data.length) {
            return;
        }
        // skip version (4 bytes)
        int channels = data[offset + 4] & 0xff;
        int sampleRate = readLittleEndianInt(data, offset + 5);
        if (channels > 0) {
            result.channelCount = channels;
        }
        if (sampleRate > 0) {
            result.sampleRateHz = sampleRate;
        }
    }

    private static void parseOpusHead(byte[] data, int start, FlacMetadataParser.Result result) {
        // OpusHead: magic(8) + version(1) + channels(1) + preSkip(2) + inputSampleRate(4)
        int offset = start + 8;
        if (offset + 8 > data.length) {
            return;
        }
        int channels = data[offset + 1] & 0xff;
        int inputSampleRate = readLittleEndianInt(data, offset + 4);
        if (channels > 0) {
            result.channelCount = channels;
        }
        if (inputSampleRate > 0) {
            result.sampleRateHz = inputSampleRate;
        }
    }

    private static void parseVorbisPages(byte[] data, FlacMetadataParser.Result result) {
        // Walk pages to find the Vorbis Comment header
        int pageOffset = nextPage(data, 0);
        int pagesVisited = 0;
        while (pageOffset >= 0 && pageOffset < data.length && pagesVisited < 10) {
            pagesVisited++;
            int payload = pagePayloadStart(data, pageOffset);
            if (payload < 0) {
                break;
            }
            if (matches(data, payload, VORBIS_COMMENT_MAGIC)) {
                int commentStart = payload + VORBIS_COMMENT_MAGIC.length;
                int pageEnd = pageDataEnd(data, pageOffset);
                VorbisCommentDecoder.decode(data, commentStart, pageEnd, result);
                // Check for METADATA_BLOCK_PICTURE in comments
                parseOggPicture(data, commentStart, pageEnd, result);
                return;
            }
            pageOffset = nextPage(data, pageOffset);
        }
        // If comment not found in available prefix, request more
        if (result.title.isEmpty() && result.artist.isEmpty()) {
            result.requiredPrefixBytes = Math.max(data.length + 1, 32 * 1024);
        }
    }

    private static void parseOpusPages(byte[] data, FlacMetadataParser.Result result) {
        // Walk pages to find OpusTags
        int pageOffset = nextPage(data, 0);
        int pagesVisited = 0;
        while (pageOffset >= 0 && pageOffset < data.length && pagesVisited < 10) {
            pagesVisited++;
            int payload = pagePayloadStart(data, pageOffset);
            if (payload < 0) {
                break;
            }
            if (matches(data, payload, OPUS_TAGS_MAGIC)) {
                // OpusTags: magic(8) + vendor_length(4) + vendor + comment_count(4) + comments
                // This is the same structure as Vorbis Comment after the magic
                int commentStart = payload + OPUS_TAGS_MAGIC.length;
                int pageEnd = pageDataEnd(data, pageOffset);
                VorbisCommentDecoder.decode(data, commentStart, pageEnd, result);
                parseOggPicture(data, commentStart, pageEnd, result);
                return;
            }
            pageOffset = nextPage(data, pageOffset);
        }
        if (result.title.isEmpty() && result.artist.isEmpty()) {
            result.requiredPrefixBytes = Math.max(data.length + 1, 32 * 1024);
        }
    }

    private static void parseOggPicture(byte[] data, int start, int end, FlacMetadataParser.Result result) {
        // METADATA_BLOCK_PICTURE is base64-encoded in Vorbis Comments
        // We look for it as a raw comment key; the VorbisCommentDecoder handles the key=value
        // but the picture data is base64. We need to scan for it separately.
        // For simplicity, we skip embedded picture parsing in the initial implementation
        // since artwork from external files is already handled by WebDavClient.
    }

    // --- OGG page structure helpers ---

    private static boolean isOggPage(byte[] data, int offset) {
        return matches(data, offset, OGG_MAGIC);
    }

    private static int pagePayloadStart(byte[] data, int pageOffset) {
        // OGG page: magic(4) + version(1) + headerType(1) + granule(8) + serial(4) + seq(4) + crc(4) + segments(1)
        if (pageOffset + 27 > data.length) {
            return -1;
        }
        int segmentCount = data[pageOffset + 26] & 0xff;
        int headerSize = 27 + segmentCount;
        if (pageOffset + headerSize > data.length) {
            return -1;
        }
        return pageOffset + headerSize;
    }

    private static int pageDataEnd(byte[] data, int pageOffset) {
        if (pageOffset + 27 > data.length) {
            return data.length;
        }
        int segmentCount = data[pageOffset + 26] & 0xff;
        int totalPayload = 0;
        for (int i = 0; i < segmentCount && pageOffset + 27 + i < data.length; i++) {
            totalPayload += data[pageOffset + 27 + i] & 0xff;
        }
        int payloadStart = pageOffset + 27 + segmentCount;
        return Math.min(payloadStart + totalPayload, data.length);
    }

    private static int nextPage(byte[] data, int currentPageOffset) {
        if (currentPageOffset + 27 > data.length) {
            return -1;
        }
        int segmentCount = data[currentPageOffset + 26] & 0xff;
        int totalPayload = 0;
        for (int i = 0; i < segmentCount && currentPageOffset + 27 + i < data.length; i++) {
            totalPayload += data[currentPageOffset + 27 + i] & 0xff;
        }
        int nextOffset = currentPageOffset + 27 + segmentCount + totalPayload;
        if (nextOffset + 4 > data.length) {
            return -1;
        }
        if (isOggPage(data, nextOffset)) {
            return nextOffset;
        }
        return -1;
    }

    private static boolean matches(byte[] data, int offset, byte[] magic) {
        if (offset + magic.length > data.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return 0;
        }
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }
}
