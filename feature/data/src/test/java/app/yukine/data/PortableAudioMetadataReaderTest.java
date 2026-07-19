package app.yukine.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.AndroidArtwork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Base64;

@RunWith(RobolectricTestRunner.class)
public class PortableAudioMetadataReaderTest {
    @Test
    public void readsTextAndArtworkFromTaggedWavFallback() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File source = new File(context.getCacheDir(), "portable-reader-test.wav");
        writeSilentWav(source);
        byte[] cover = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
        AudioFile audioFile = AudioFileIO.read(source);
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.TITLE, "歌曲标题");
        tag.setField(FieldKey.ARTIST, "歌曲作者");
        tag.setField(FieldKey.ALBUM, "歌曲专辑");
        tag.setField(FieldKey.ALBUM_ARTIST, "专辑艺术家");
        tag.setField(FieldKey.COMPOSER, "作曲者");
        tag.setField(FieldKey.YEAR, "2023-09-10");
        tag.setField(FieldKey.MUSICBRAINZ_RELEASE_TYPE, "single");
        tag.setField(FieldKey.MUSICBRAINZ_TRACK_ID, "123e4567-e89b-12d3-a456-426614174000");
        tag.setField(FieldKey.MUSICBRAINZ_RECORDING_WORK_ID, "123e4567-e89b-12d3-a456-426614174001");
        tag.setField(FieldKey.ISRC, "JP-ABC-12-34567");
        tag.setField(FieldKey.ACOUSTID_ID, "123e4567-e89b-12d3-a456-426614174002");
        tag.setField(FieldKey.MUSICBRAINZ_ARTISTID, "123e4567-e89b-12d3-a456-426614174003");
        AndroidArtwork artwork = new AndroidArtwork();
        artwork.setBinaryData(cover);
        artwork.setMimeType("image/png");
        artwork.setPictureType(3);
        tag.setField(artwork);
        audioFile.commit();

        PortableAudioMetadataReader.Metadata metadata =
                new PortableAudioMetadataReader(context).read(Uri.fromFile(source), source.getName());

        assertEquals("歌曲标题", metadata.title);
        assertEquals("歌曲作者", metadata.artist);
        assertEquals("歌曲专辑", metadata.album);
        assertEquals("专辑艺术家", metadata.albumArtist);
        assertEquals("作曲者", metadata.composer);
        assertEquals("single", metadata.releaseType);
        assertEquals(2023, metadata.year);
        assertEquals("123e4567-e89b-12d3-a456-426614174000", metadata.identityTags.recordingMusicBrainzId);
        assertEquals("123e4567-e89b-12d3-a456-426614174001", metadata.identityTags.workMusicBrainzId);
        assertEquals("JPABC1234567", metadata.identityTags.isrc);
        assertEquals("123e4567-e89b-12d3-a456-426614174002", metadata.identityTags.acoustId);
        assertEquals(
                "123e4567-e89b-12d3-a456-426614174003",
                metadata.identityTags.artistMusicBrainzIds.get(0)
        );
        assertArrayEquals(cover, metadata.artwork);
        Files.deleteIfExists(source.toPath());
    }

    private static void writeSilentWav(File target) throws Exception {
        int sampleRate = 8_000;
        int sampleCount = 800;
        int dataSize = sampleCount * 2;
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(target))) {
            output.writeBytes("RIFF");
            writeIntLE(output, 36 + dataSize);
            output.writeBytes("WAVEfmt ");
            writeIntLE(output, 16);
            writeShortLE(output, 1);
            writeShortLE(output, 1);
            writeIntLE(output, sampleRate);
            writeIntLE(output, sampleRate * 2);
            writeShortLE(output, 2);
            writeShortLE(output, 16);
            output.writeBytes("data");
            writeIntLE(output, dataSize);
            for (int i = 0; i < sampleCount; i++) {
                writeShortLE(output, 0);
            }
        }
    }

    private static void writeIntLE(DataOutputStream output, int value) throws Exception {
        output.writeInt(Integer.reverseBytes(value));
    }

    private static void writeShortLE(DataOutputStream output, int value) throws Exception {
        output.writeShort(Short.reverseBytes((short) value));
    }
}
