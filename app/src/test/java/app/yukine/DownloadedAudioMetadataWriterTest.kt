package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

class DownloadedAudioMetadataWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesPortableTextAndCoverTagsWithoutChangingSourceFile() {
        val source = temporaryFolder.newFile("track.part")
        writeSilentWav(source)
        val originalBytes = source.readBytes()
        val cover = Base64.getDecoder().decode(ONE_PIXEL_PNG_BASE64)

        val tagged = DownloadedAudioMetadataWriter().prepareTaggedCopy(
            source = source,
            track = Track(
                1L,
                "歌曲标题",
                "歌曲作者",
                "歌曲专辑",
                100L,
                Uri.EMPTY,
                ""
            ),
            extension = "wav",
            artworkBytes = cover,
            artworkMimeType = "image/png"
        )

        assertNotEquals(source, tagged)
        assertArrayEquals(originalBytes, source.readBytes())
        val tag = AudioFileIO.read(tagged).tag
        assertEquals("歌曲标题", tag.getFirst(FieldKey.TITLE))
        assertEquals("歌曲作者", tag.getFirst(FieldKey.ARTIST))
        assertEquals("歌曲专辑", tag.getFirst(FieldKey.ALBUM))
        assertArrayEquals(cover, tag.firstArtwork.binaryData)
    }

    @Test
    fun malformedAudioFallsBackToOriginalDownload() {
        val source = temporaryFolder.newFile("track.part").apply {
            writeText("not an audio file")
        }

        val result = DownloadedAudioMetadataWriter().prepareTaggedCopy(
            source = source,
            track = Track(1L, "Title", "Artist", "Album", 0L, Uri.EMPTY, ""),
            extension = "mp3",
            artworkBytes = byteArrayOf(1, 2, 3),
            artworkMimeType = "image/jpeg"
        )

        assertEquals(source, result)
        assertEquals("not an audio file", source.readText())
    }

    private fun writeSilentWav(target: File) {
        val sampleRate = 8_000
        val sampleCount = 800
        val dataSize = sampleCount * 2
        DataOutputStream(FileOutputStream(target)).use { output ->
            output.writeBytes("RIFF")
            output.writeIntLE(36 + dataSize)
            output.writeBytes("WAVEfmt ")
            output.writeIntLE(16)
            output.writeShortLE(1)
            output.writeShortLE(1)
            output.writeIntLE(sampleRate)
            output.writeIntLE(sampleRate * 2)
            output.writeShortLE(2)
            output.writeShortLE(16)
            output.writeBytes("data")
            output.writeIntLE(dataSize)
            repeat(sampleCount) { output.writeShortLE(0) }
        }
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        writeInt(Integer.reverseBytes(value))
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
    }

    companion object {
        private const val ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
