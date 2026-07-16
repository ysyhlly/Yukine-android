package app.yukine.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FlacMetadataParserTest {
    @Test
    public void parsesHiResStreamInfoUtf8TagsReplayGainAndFrontCover() throws Exception {
        byte[] artwork = new byte[]{1, 2, 3, 4, 5};
        byte[] flac = flacFile(artwork);

        FlacMetadataParser.Result result = FlacMetadataParser.parse(flac);

        assertTrue(result.recognized);
        assertEquals("17さいのうた。", result.title);
        assertEquals("『ユイカ』", result.artist);
        assertEquals("17さいのうた。", result.album);
        assertEquals(309_663L, result.durationMs);
        assertEquals(96_000, result.sampleRateHz);
        assertEquals(24, result.bitsPerSample);
        assertEquals(2, result.channelCount);
        assertEquals(-6.25f, result.replayGainTrackDb, 0.001f);
        assertEquals("123e4567-e89b-12d3-a456-426614174000", result.recordingMusicBrainzId);
        assertEquals("123e4567-e89b-12d3-a456-426614174001", result.workMusicBrainzId);
        assertEquals("JP-ABC-12-34567", result.isrc);
        assertEquals("123e4567-e89b-12d3-a456-426614174002", result.acoustId);
        assertEquals(
                Arrays.asList(
                        "123e4567-e89b-12d3-a456-426614174003",
                        "123e4567-e89b-12d3-a456-426614174004"
                ),
                result.artistMusicBrainzIds
        );
        assertArrayEquals(artwork, result.artwork);
    }

    @Test
    public void reportsRequiredPrefixForTruncatedCommentBlock() throws Exception {
        byte[] flac = flacFile(new byte[]{9, 8, 7});
        byte[] truncated = Arrays.copyOf(flac, 48);

        FlacMetadataParser.Result result = FlacMetadataParser.parse(truncated);

        assertTrue(result.recognized);
        assertTrue(result.requiredPrefixBytes > truncated.length);
        assertEquals(96_000, result.sampleRateHz);
        assertEquals(24, result.bitsPerSample);
    }

    private byte[] flacFile(byte[] artwork) throws Exception {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write("fLaC".getBytes(StandardCharsets.US_ASCII));
        writeBlock(file, 0, false, streamInfo());
        writeBlock(file, 4, false, comments());
        writeBlock(file, 6, true, picture(artwork));
        return file.toByteArray();
    }

    private byte[] streamInfo() {
        byte[] value = new byte[34];
        int sampleRate = 96_000;
        int channelsMinusOne = 1;
        int bitsMinusOne = 23;
        long totalSamples = 29_727_648L;
        int packed = 10;
        value[packed] = (byte) (sampleRate >>> 12);
        value[packed + 1] = (byte) (sampleRate >>> 4);
        value[packed + 2] = (byte) (((sampleRate & 0x0f) << 4)
                | (channelsMinusOne << 1)
                | (bitsMinusOne >>> 4));
        value[packed + 3] = (byte) (((bitsMinusOne & 0x0f) << 4)
                | ((totalSamples >>> 32) & 0x0f));
        value[packed + 4] = (byte) (totalSamples >>> 24);
        value[packed + 5] = (byte) (totalSamples >>> 16);
        value[packed + 6] = (byte) (totalSamples >>> 8);
        value[packed + 7] = (byte) totalSamples;
        return value;
    }

    private byte[] comments() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeLittleEndian(output, 4);
        output.write("test".getBytes(StandardCharsets.UTF_8));
        String[] comments = {
                "title=17さいのうた。",
                "artist=『ユイカ』",
                "album=17さいのうた。",
                "REPLAYGAIN_TRACK_GAIN=-6.25 dB",
                "MUSICBRAINZ_TRACKID=123e4567-e89b-12d3-a456-426614174000",
                "MUSICBRAINZ_WORKID=123e4567-e89b-12d3-a456-426614174001",
                "ISRC=JP-ABC-12-34567",
                "ACOUSTID_ID=123e4567-e89b-12d3-a456-426614174002",
                "MUSICBRAINZ_ARTISTID=123e4567-e89b-12d3-a456-426614174003;123e4567-e89b-12d3-a456-426614174004"
        };
        writeLittleEndian(output, comments.length);
        for (String comment : comments) {
            byte[] encoded = comment.getBytes(StandardCharsets.UTF_8);
            writeLittleEndian(output, encoded.length);
            output.write(encoded);
        }
        return output.toByteArray();
    }

    private byte[] picture(byte[] artwork) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        byte[] mime = "image/jpeg".getBytes(StandardCharsets.US_ASCII);
        data.writeInt(3);
        data.writeInt(mime.length);
        data.write(mime);
        data.writeInt(0);
        data.writeInt(600);
        data.writeInt(600);
        data.writeInt(24);
        data.writeInt(0);
        data.writeInt(artwork.length);
        data.write(artwork);
        data.flush();
        return output.toByteArray();
    }

    private void writeBlock(ByteArrayOutputStream output, int type, boolean last, byte[] body) throws Exception {
        output.write((last ? 0x80 : 0) | type);
        output.write((body.length >>> 16) & 0xff);
        output.write((body.length >>> 8) & 0xff);
        output.write(body.length & 0xff);
        output.write(body);
    }

    private void writeLittleEndian(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }
}
