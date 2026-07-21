package app.yukine.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class Mp3MetadataParserTest {
    @Test
    public void parsesId3v24TextFramesAndMpegHeader() {
        byte[] mp3 = mp3WithId3v24();

        FlacMetadataParser.Result result = Mp3MetadataParser.parse(mp3);

        assertTrue(result.recognized);
        assertEquals("Test Title", result.title);
        assertEquals("Test Artist", result.artist);
        assertEquals("Test Album", result.album);
        assertEquals("Album Artist", result.albumArtist);
        assertEquals("Composer Name", result.composer);
        assertEquals(2023, result.year);
        assertEquals("Rock", result.genre);
        assertEquals(1, result.discNumber);
        assertEquals(5, result.trackNumber);
        assertEquals(120, result.bpm);
        assertEquals(44100, result.sampleRateHz);
        assertEquals(2, result.channelCount);
    }

    @Test
    public void returnsEmptyForNullInput() {
        FlacMetadataParser.Result result = Mp3MetadataParser.parse(null);
        assertFalse(result.recognized);
    }

    @Test
    public void returnsEmptyForNonMp3Data() {
        FlacMetadataParser.Result result = Mp3MetadataParser.parse(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        assertFalse(result.recognized);
    }

    @Test
    public void requestsMoreBytesWhenTagExceedsPrefix() {
        // ID3v2 header claiming 200 bytes but only 20 provided
        byte[] data = new byte[20];
        data[0] = 'I'; data[1] = 'D'; data[2] = '3';
        data[3] = 4; data[4] = 0; data[5] = 0;
        // syncsafe size = 200
        data[6] = 0; data[7] = 0; data[8] = 1; data[9] = (byte) 0xC8;

        FlacMetadataParser.Result result = Mp3MetadataParser.parse(data);

        assertTrue(result.recognized);
        assertTrue(result.requiredPrefixBytes > data.length);
    }

    private byte[] mp3WithId3v24() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Build frames
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        writeFrame(frames, "TIT2", "Test Title");
        writeFrame(frames, "TPE1", "Test Artist");
        writeFrame(frames, "TALB", "Test Album");
        writeFrame(frames, "TPE2", "Album Artist");
        writeFrame(frames, "TCOM", "Composer Name");
        writeFrame(frames, "TDRC", "2023");
        writeFrame(frames, "TCON", "Rock");
        writeFrame(frames, "TPOS", "1/1");
        writeFrame(frames, "TRCK", "5/12");
        writeFrame(frames, "TBPM", "120");
        byte[] frameData = frames.toByteArray();

        // ID3v2.4 header
        int tagSize = frameData.length;
        out.write('I'); out.write('D'); out.write('3');
        out.write(4); out.write(0); // version 2.4
        out.write(0); // flags
        // syncsafe size
        out.write((tagSize >> 21) & 0x7f);
        out.write((tagSize >> 14) & 0x7f);
        out.write((tagSize >> 7) & 0x7f);
        out.write(tagSize & 0x7f);
        out.write(frameData, 0, frameData.length);

        // MPEG1 Layer3 frame header: 0xFFFB9004
        // 0xFF 0xFB = sync + MPEG1 Layer3 no CRC
        // 0x90 = bitrate index 9 (128kbps) + samplerate index 0 (44100)
        // 0x04 = stereo, no emphasis
        out.write(0xFF); out.write(0xFB); out.write(0x90); out.write(0x04);
        // Some dummy frame data
        for (int i = 0; i < 100; i++) out.write(0);

        return out.toByteArray();
    }

    private void writeFrame(ByteArrayOutputStream out, String id, String text) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int frameSize = 1 + textBytes.length; // encoding byte + text
        out.write(id.charAt(0)); out.write(id.charAt(1));
        out.write(id.charAt(2)); out.write(id.charAt(3));
        // syncsafe size (ID3v2.4)
        out.write((frameSize >> 21) & 0x7f);
        out.write((frameSize >> 14) & 0x7f);
        out.write((frameSize >> 7) & 0x7f);
        out.write(frameSize & 0x7f);
        out.write(0); out.write(0); // frame flags
        out.write(3); // encoding = UTF-8
        out.write(textBytes, 0, textBytes.length);
    }
}
