package app.yukine.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OggMetadataParserTest {
    @Test
    public void parsesVorbisIdentificationAndComments() {
        byte[] ogg = vorbisFile();

        FlacMetadataParser.Result result = OggMetadataParser.parse(ogg);

        assertTrue(result.recognized);
        assertEquals("OGG Title", result.title);
        assertEquals("OGG Artist", result.artist);
        assertEquals("OGG Album", result.album);
        assertEquals("Album Artist OGG", result.albumArtist);
        assertEquals(48000, result.sampleRateHz);
        assertEquals(2, result.channelCount);
        assertEquals("Electronic", result.genre);
        assertEquals(2, result.discNumber);
        assertEquals(7, result.trackNumber);
    }

    @Test
    public void parsesOpusHeadAndTags() {
        byte[] ogg = opusFile();

        FlacMetadataParser.Result result = OggMetadataParser.parse(ogg);

        assertTrue(result.recognized);
        assertEquals("OGG Title", result.title);
        assertEquals("OGG Artist", result.artist);
        assertEquals(48000, result.sampleRateHz);
        assertEquals(2, result.channelCount);
    }

    @Test
    public void returnsEmptyForNullInput() {
        FlacMetadataParser.Result result = OggMetadataParser.parse(null);
        assertFalse(result.recognized);
    }

    @Test
    public void returnsEmptyForNonOggData() {
        FlacMetadataParser.Result result = OggMetadataParser.parse(
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28});
        assertFalse(result.recognized);
    }

    private byte[] vorbisFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Page 1: Vorbis identification header
        ByteArrayOutputStream idPayload = new ByteArrayOutputStream();
        idPayload.write(0x01); // packet type
        idPayload.write("vorbis".getBytes(StandardCharsets.US_ASCII), 0, 6);
        writeIntLE(idPayload, 0); // version
        idPayload.write(2); // channels
        writeIntLE(idPayload, 48000); // sample rate
        writeIntLE(idPayload, 0); // bitrate max
        writeIntLE(idPayload, 128000); // bitrate nominal
        writeIntLE(idPayload, 0); // bitrate min
        idPayload.write(0); // blocksize
        idPayload.write(1); // framing
        writeOggPage(out, idPayload.toByteArray(), 0);

        // Page 2: Vorbis comment header
        ByteArrayOutputStream commentPayload = new ByteArrayOutputStream();
        commentPayload.write(0x03); // packet type
        commentPayload.write("vorbis".getBytes(StandardCharsets.US_ASCII), 0, 6);
        writeVorbisComments(commentPayload);
        writeOggPage(out, commentPayload.toByteArray(), 1);

        return out.toByteArray();
    }

    private byte[] opusFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Page 1: OpusHead
        ByteArrayOutputStream headPayload = new ByteArrayOutputStream();
        headPayload.write("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, 8);
        headPayload.write(1); // version
        headPayload.write(2); // channels
        headPayload.write(0); headPayload.write(0); // pre-skip
        writeIntLE(headPayload, 48000); // input sample rate
        headPayload.write(0); headPayload.write(0); // output gain
        headPayload.write(0); // mapping family
        writeOggPage(out, headPayload.toByteArray(), 0);

        // Page 2: OpusTags
        ByteArrayOutputStream tagsPayload = new ByteArrayOutputStream();
        tagsPayload.write("OpusTags".getBytes(StandardCharsets.US_ASCII), 0, 8);
        writeVorbisComments(tagsPayload);
        writeOggPage(out, tagsPayload.toByteArray(), 1);

        return out.toByteArray();
    }

    private void writeVorbisComments(ByteArrayOutputStream out) {
        byte[] vendor = "test-vendor".getBytes(StandardCharsets.UTF_8);
        writeIntLE(out, vendor.length);
        out.write(vendor, 0, vendor.length);
        String[] comments = {
                "TITLE=OGG Title",
                "ARTIST=OGG Artist",
                "ALBUM=OGG Album",
                "ALBUMARTIST=Album Artist OGG",
                "GENRE=Electronic",
                "DISCNUMBER=2/2",
                "TRACKNUMBER=7/12"
        };
        // For Opus test, override with Opus-specific values
        writeIntLE(out, comments.length);
        for (String comment : comments) {
            byte[] bytes = comment.getBytes(StandardCharsets.UTF_8);
            writeIntLE(out, bytes.length);
            out.write(bytes, 0, bytes.length);
        }
    }

    private void writeOggPage(ByteArrayOutputStream out, byte[] payload, int sequence) {
        // OggS magic
        out.write('O'); out.write('g'); out.write('g'); out.write('S');
        out.write(0); // version
        out.write(sequence == 0 ? 0x02 : 0x00); // header type (BOS for first)
        // granule position (8 bytes)
        for (int i = 0; i < 8; i++) out.write(0);
        // serial number (4 bytes)
        writeIntLE(out, 12345);
        // sequence number
        writeIntLE(out, sequence);
        // CRC (4 bytes, not validated by parser)
        writeIntLE(out, 0);
        // segment table
        int segmentCount = (payload.length + 254) / 255;
        if (segmentCount == 0) segmentCount = 1;
        out.write(segmentCount);
        int remaining = payload.length;
        for (int i = 0; i < segmentCount; i++) {
            int segSize = Math.min(255, remaining);
            out.write(segSize);
            remaining -= segSize;
        }
        // payload
        out.write(payload, 0, payload.length);
    }

    private void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }
}
