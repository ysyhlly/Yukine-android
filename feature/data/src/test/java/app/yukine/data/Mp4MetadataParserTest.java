package app.yukine.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class Mp4MetadataParserTest {
    @Test
    public void parsesM4aIlstTagsAndAudioSpecs() {
        byte[] m4a = m4aFile();

        FlacMetadataParser.Result result = Mp4MetadataParser.parse(m4a);

        assertTrue(result.recognized);
        assertEquals("M4A Title", result.title);
        assertEquals("M4A Artist", result.artist);
        assertEquals("M4A Album", result.album);
        assertEquals("Album Artist M4A", result.albumArtist);
        assertEquals("Composer M4A", result.composer);
        assertEquals(2022, result.year);
        assertEquals("Pop", result.genre);
        assertEquals(1, result.discNumber);
        assertEquals(3, result.trackNumber);
        assertEquals(128, result.bpm);
    }

    @Test
    public void returnsEmptyForNullInput() {
        FlacMetadataParser.Result result = Mp4MetadataParser.parse(null);
        assertFalse(result.recognized);
    }

    @Test
    public void returnsEmptyForNonMp4Data() {
        FlacMetadataParser.Result result = Mp4MetadataParser.parse(
                new byte[]{0, 0, 0, 8, 'f', 'L', 'a', 'C', 0, 0, 0, 0});
        assertFalse(result.recognized);
    }

    private byte[] m4aFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ftyp atom
        byte[] ftypPayload = "M4A ".getBytes(StandardCharsets.US_ASCII);
        writeAtom(out, "ftyp", ftypPayload);
        // moov atom with udta/meta/ilst
        ByteArrayOutputStream moov = new ByteArrayOutputStream();
        writeMvhd(moov);
        writeUdta(moov);
        writeAtom(out, "moov", moov.toByteArray());
        return out.toByteArray();
    }

    private void writeMvhd(ByteArrayOutputStream out) {
        ByteArrayOutputStream mvhd = new ByteArrayOutputStream();
        // version 0, flags 0
        mvhd.write(0); mvhd.write(0); mvhd.write(0); mvhd.write(0);
        // creation_time(4) + modification_time(4)
        writeInt(mvhd, 0); writeInt(mvhd, 0);
        // timescale = 1000
        writeInt(mvhd, 1000);
        // duration = 180000 (180 seconds)
        writeInt(mvhd, 180000);
        // rest of mvhd (not parsed, just filler)
        for (int i = 0; i < 80; i++) mvhd.write(0);
        writeAtom(out, "mvhd", mvhd.toByteArray());
    }

    private void writeUdta(ByteArrayOutputStream out) {
        ByteArrayOutputStream udta = new ByteArrayOutputStream();
        ByteArrayOutputStream meta = new ByteArrayOutputStream();
        // meta version/flags
        meta.write(0); meta.write(0); meta.write(0); meta.write(0);
        ByteArrayOutputStream ilst = new ByteArrayOutputStream();
        writeDataAtom(ilst, "\u00A9nam", "M4A Title");
        writeDataAtom(ilst, "\u00A9ART", "M4A Artist");
        writeDataAtom(ilst, "\u00A9alb", "M4A Album");
        writeDataAtom(ilst, "aART", "Album Artist M4A");
        writeDataAtom(ilst, "\u00A9wrt", "Composer M4A");
        writeDataAtom(ilst, "\u00A9day", "2022");
        writeDataAtom(ilst, "\u00A9gen", "Pop");
        writeDataAtom(ilst, "\u00A9lyr", "Some lyrics here");
        // disk: type=0, data = 00 01 00 02 (disc 1 of 2)
        writeBinaryDataAtom(ilst, "disk", new byte[]{0, 0, 0, 1, 0, 2});
        // trkn: type=0, data = 00 03 00 0A (track 3 of 10)
        writeBinaryDataAtom(ilst, "trkn", new byte[]{0, 0, 0, 3, 0, 10});
        // tmpo: type=21 (signed int), data = 00 80 (128 BPM)
        writeBinaryDataAtom(ilst, "tmpo", new byte[]{0, (byte) 128});
        writeAtom(meta, "ilst", ilst.toByteArray());
        writeAtom(udta, "meta", meta.toByteArray());
        writeAtom(out, "udta", udta.toByteArray());
    }

    private void writeDataAtom(ByteArrayOutputStream out, String type, String value) {
        ByteArrayOutputStream atom = new ByteArrayOutputStream();
        byte[] textBytes = value.getBytes(StandardCharsets.UTF_8);
        // data atom: size(4) + "data"(4) + type(4) + locale(4) + value
        int dataSize = 16 + textBytes.length;
        writeInt(atom, dataSize);
        atom.write('d'); atom.write('a'); atom.write('t'); atom.write('a');
        writeInt(atom, 1); // type = UTF-8 text
        writeInt(atom, 0); // locale
        atom.write(textBytes, 0, textBytes.length);
        // Wrap in parent atom with type name
        byte[] atomBytes = atom.toByteArray();
        int totalSize = 8 + atomBytes.length;
        writeInt(out, totalSize);
        writeType(out, type);
        out.write(atomBytes, 0, atomBytes.length);
    }

    private void writeBinaryDataAtom(ByteArrayOutputStream out, String type, byte[] value) {
        ByteArrayOutputStream atom = new ByteArrayOutputStream();
        int dataSize = 16 + value.length;
        writeInt(atom, dataSize);
        atom.write('d'); atom.write('a'); atom.write('t'); atom.write('a');
        writeInt(atom, 0); // type = binary
        writeInt(atom, 0); // locale
        atom.write(value, 0, value.length);
        byte[] atomBytes = atom.toByteArray();
        int totalSize = 8 + atomBytes.length;
        writeInt(out, totalSize);
        writeType(out, type);
        out.write(atomBytes, 0, atomBytes.length);
    }

    private void writeAtom(ByteArrayOutputStream out, String type, byte[] payload) {
        writeInt(out, 8 + payload.length);
        writeType(out, type);
        out.write(payload, 0, payload.length);
    }

    private void writeType(ByteArrayOutputStream out, String type) {
        for (int i = 0; i < 4; i++) {
            out.write(i < type.length() ? type.charAt(i) : 0);
        }
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 8) & 0xff);
        out.write(value & 0xff);
    }
}
