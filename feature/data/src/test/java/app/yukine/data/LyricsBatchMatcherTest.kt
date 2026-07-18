package app.yukine.data

import android.net.Uri
import app.yukine.model.LyricLine
import app.yukine.model.LyricsDocument
import app.yukine.model.LyricsTrack
import app.yukine.model.LyricsTrackRole
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsBatchMatcherTest {
    @Test
    fun exactAudioBasenameProducesUniqueCanonicalMatch() {
        val match = LyricsBatchMatcher.match(
            LyricsImportCandidate("Artist - Song.lrc", document()),
            listOf(
                LyricsBatchTrack(
                    11L,
                    track(1L, "Song", "Artist", "Album", "/music/Artist - Song.flac")
                )
            )
        )

        assertTrue(match is LyricsBatchMatch.Unique)
        assertEquals(11L, (match as LyricsBatchMatch.Unique).track.recordingId)
    }

    @Test
    fun titleArtistAcrossTwoRecordingsIsAmbiguous() {
        val candidate = LyricsImportCandidate(
            "lyrics.lrc",
            document(title = "Song", artist = "Artist")
        )
        val match = LyricsBatchMatcher.match(
            candidate,
            listOf(
                LyricsBatchTrack(11L, track(1L, "Song", "Artist", "A", "/a.flac")),
                LyricsBatchTrack(12L, track(2L, "Song", "Artist", "B", "/b.flac"))
            )
        )

        assertTrue(match is LyricsBatchMatch.Ambiguous)
        assertEquals(2, (match as LyricsBatchMatch.Ambiguous).candidates.size)
    }

    @Test
    fun titleAlbumHonorsFiveSecondOrTwoPercentDurationBoundary() {
        val candidate = LyricsImportCandidate(
            "lyrics.lrc",
            document(title = "Song", album = "Album", endMs = 180_000L)
        )
        val inside = LyricsBatchMatcher.match(
            candidate,
            listOf(
                LyricsBatchTrack(
                    11L,
                    track(1L, "Song", "Other", "Album", "/inside.flac", 185_000L)
                )
            )
        )
        val outside = LyricsBatchMatcher.match(
            candidate,
            listOf(
                LyricsBatchTrack(
                    12L,
                    track(2L, "Song", "Other", "Album", "/outside.flac", 185_001L)
                )
            )
        )

        assertTrue(inside is LyricsBatchMatch.Unique)
        assertTrue(outside is LyricsBatchMatch.Unmatched)
    }

    @Test
    fun similarButNonExactNamesRemainUnmatched() {
        val match = LyricsBatchMatcher.match(
            LyricsImportCandidate("Artst-Sng.lrc", document()),
            listOf(
                LyricsBatchTrack(11L, track(1L, "Song", "Artist", "Album", "/different.flac"))
            )
        )

        assertTrue(match is LyricsBatchMatch.Unmatched)
    }

    private fun document(
        title: String = "",
        artist: String = "",
        album: String = "",
        endMs: Long = 3_000L
    ) = LyricsDocument(
        title = title,
        artist = artist,
        album = album,
        tracks = listOf(
            LyricsTrack(
                LyricsTrackRole.PRIMARY,
                lines = listOf(LyricLine(0L, endMs, "line"))
            )
        )
    )

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        path: String,
        durationMs: Long = 180_000L
    ) = Track(id, title, artist, album, durationMs, Uri.EMPTY, path)
}
