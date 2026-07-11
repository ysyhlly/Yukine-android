package app.yukine

import app.yukine.model.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File
import java.util.Locale

/** Writes portable tags into a private working copy before the download is published. */
internal class DownloadedAudioMetadataWriter {
    fun prepareTaggedCopy(
        source: File,
        track: Track,
        extension: String,
        artworkBytes: ByteArray?,
        artworkMimeType: String?
    ): File {
        val normalizedExtension = extension.lowercase(Locale.ROOT)
        if (normalizedExtension !in SUPPORTED_EXTENSIONS) {
            return source
        }

        val taggedCopy = File(source.parentFile, "tagged.$normalizedExtension")
        return runCatching {
            source.copyTo(taggedCopy, overwrite = true)
            val audioFile = AudioFileIO.read(taggedCopy)
            val tag = audioFile.tagOrCreateAndSetDefault
            setTextField(tag, FieldKey.TITLE, track.title)
            setTextField(tag, FieldKey.ARTIST, track.artist)
            setTextField(tag, FieldKey.ALBUM, track.album)
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                val artwork = AndroidArtwork().apply {
                    binaryData = artworkBytes
                    mimeType = normalizedArtworkMimeType(artworkMimeType)
                    description = "Cover"
                    pictureType = FRONT_COVER_PICTURE_TYPE
                }
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audioFile.commit()
            taggedCopy
        }.getOrElse {
            taggedCopy.delete()
            source
        }
    }

    private fun setTextField(tag: org.jaudiotagger.tag.Tag, key: FieldKey, value: String?) {
        value?.trim()?.takeIf(String::isNotBlank)?.let { tag.setField(key, it) }
    }

    private fun normalizedArtworkMimeType(mimeType: String?): String {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return normalized.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    }

    companion object {
        private const val FRONT_COVER_PICTURE_TYPE = 3
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg", "opus", "wav", "aif", "aiff", "wma")
    }
}
