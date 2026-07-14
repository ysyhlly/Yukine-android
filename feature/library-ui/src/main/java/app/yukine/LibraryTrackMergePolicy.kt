package app.yukine

import app.yukine.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Produces the logical library list without changing stored rows or source files.
 *
 * A song can be available from a device scan, document import, WebDAV, or network stream. When
 * complete metadata and duration agree, those rows are one logical song with interchangeable
 * sources. Remix/version, album, unknown metadata, and materially different duration still remain
 * separate so they cannot be switched accidentally.
 */
object LibraryTrackMergePolicy {
    private const val LOCAL_DURATION_TOLERANCE_MS = 3_000L
    private val whitespace = Regex("\\s+")
    private val bracketedQualifier = Regex(
        """(?:\(|\[|（|【)\s*([^()\[\]（）【】]*)\s*(?:\)|\]|）|】)"""
    )
    private val featuredArtistSuffix = Regex(
        """(?i)\s*[-–—]?\s*(?:(?:\(|\[|（|【)\s*)?(?:feat(?:uring)?\.?|ft\.?)\s+[^()\[\]（）【】]+(?:\s*(?:\)|\]|）|】))?\s*$"""
    )
    private val translatedAliasMarker = Regex(
        """(?i)(?:\b(?:translation|translated|chinese)\b|中文|中译|中譯|翻译|翻譯|译名|譯名|[的了们們鱼])"""
    )
    private val versionQualifier = Regex(
        """(?i)\b(?:remix|mix|version|ver\.?|live|acoustic|instrumental|demo|edit|cover|karaoke|radio|extended|rework|remaster(?:ed)?|remake|original|alternate|alt\.?|part|pt\.?|chapter|episode|vol\.?|volume|disc|tv|movie|anime|game|op|ed|opening|ending|intro|outro|interlude)\b|(?:伴奏|纯音乐|純音樂|翻唱|现场|現場|演唱会|演唱會|混音|原唱|原曲|版本|重制|重製|剧场版|劇場版|リミックス|ライブ|バージョン|アコースティック|インスト|デモ|カバー)"""
    )
    private val unknownMetadata = setOf(
        "<unknown>",
        "unknown",
        "unknown artist",
        "unknown album",
        "未知歌曲",
        "未知艺人",
        "未知专辑"
    )

    data class Snapshot(
        val mergedTracks: List<Track>,
        val sourceCandidatesByTrackId: Map<Long, List<Track>>
    )

    fun merge(tracks: List<Track>): List<Track> = snapshot(tracks).mergedTracks

    /** Returns every interchangeable source for [track], in the same cluster used by [merge]. */
    fun sourceCandidatesFor(track: Track?, tracks: List<Track>): List<Track> {
        val selected = track ?: return emptyList()
        return sourceCandidateIndex(tracks)[selected.id].orEmpty()
    }

    /**
     * Builds the lookup used by the playback page. Only duplicated groups are indexed, keeping
     * singleton songs out of both memory and Now Playing recomposition work.
     */
    fun sourceCandidateIndex(tracks: List<Track>): Map<Long, List<Track>> {
        return snapshot(tracks).sourceCandidatesByTrackId
    }

    /** Builds the library display list and duplicate-source lookup in one metadata pass. */
    fun snapshot(tracks: List<Track>): Snapshot {
        val clusters = clusters(tracks)
        val index = HashMap<Long, List<Track>>()
        clusters
            .asSequence()
            .map { cluster -> cluster.tracks }
            .filter { candidates -> candidates.size > 1 }
            .forEach { candidates ->
                val immutableCandidates = candidates.toList()
                candidates.forEach { candidate -> index[candidate.id] = immutableCandidates }
            }
        return Snapshot(
            mergedTracks = clusters.map { cluster -> cluster.tracks.first() },
            sourceCandidatesByTrackId = index
        )
    }

    private fun clusters(tracks: List<Track>): List<TrackCluster> {
        if (tracks.isEmpty()) {
            return emptyList()
        }

        val result = ArrayList<TrackCluster>(tracks.size)
        val clustersByMetadata = HashMap<LocalTrackMetadata, MutableList<DurationCluster>>()
        tracks.forEach { track ->
            val metadata = track.mergeMetadataOrNull()
            if (metadata == null) {
                result += TrackCluster(mutableListOf(track))
                return@forEach
            }

            val clusters = clustersByMetadata.getOrPut(metadata) { ArrayList() }
            val matchingCluster = clusters.firstOrNull { cluster ->
                abs(cluster.anchorDurationMs - track.durationMs) <= LOCAL_DURATION_TOLERANCE_MS
            }
            if (matchingCluster == null) {
                val cluster = DurationCluster(track.durationMs, mutableListOf(track))
                clusters += cluster
                result += TrackCluster(cluster.tracks)
            } else {
                matchingCluster.tracks += track
            }
        }
        return result
    }

    private fun Track.mergeMetadataOrNull(): LocalTrackMetadata? {
        if (durationMs <= 0L) {
            return null
        }

        val normalizedTitle = title.normalizedTitleMetadata()
        val normalizedArtist = artist.normalizedArtistMetadata()
        val normalizedAlbum = album.normalizedAlbumMetadata()
        if (
            normalizedTitle.isUnknownMetadata() ||
            normalizedArtist.isUnknownMetadata() ||
            normalizedAlbum.isUnknownMetadata()
        ) {
            return null
        }
        return LocalTrackMetadata(normalizedTitle, normalizedArtist, normalizedAlbum)
    }

    private fun String.normalizedTitleMetadata(): String =
        normalizedMetadata()
            .withoutFeaturedArtistSuffix()
            .withoutTranslatedAliasQualifier()
            .compactMetadataWhitespace()

    private fun String.normalizedArtistMetadata(): String =
        normalizedMetadata()
            // A translated alias in an artist field is descriptive noise, but a featured artist is
            // a real performer difference and stays part of the strict artist key.
            .withoutTranslatedAliasQualifier()
            .compactMetadataWhitespace()

    private fun String.normalizedAlbumMetadata(): String =
        normalizedMetadata()
            .withoutFeaturedArtistSuffix()
            .withoutTranslatedAliasQualifier()
            .compactMetadataWhitespace()

    private fun String.normalizedMetadata(): String =
        Normalizer.normalize(this, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()

    private fun String.withoutFeaturedArtistSuffix(): String =
        featuredArtistSuffix.replace(this, " ")

    /**
     * Importers sometimes inject a translated alias in parentheses. Only explicit translation
     * labels or conservative Chinese-language signals are removable, so ordinary Japanese Kanji
     * qualifiers remain part of the strict key. Known version labels (Remix, Live, Ver., etc.) are
     * always retained so different recordings cannot collapse into one logical song.
     */
    private fun String.withoutTranslatedAliasQualifier(): String =
        bracketedQualifier.replace(this) { match ->
            val qualifier = match.groupValues[1].trim()
            if (
                translatedAliasMarker.containsMatchIn(qualifier) &&
                !versionQualifier.containsMatchIn(qualifier)
            ) {
                " "
            } else {
                match.value
            }
        }

    private fun String.compactMetadataWhitespace(): String =
        replace(whitespace, " ").trim()

    private fun String.isUnknownMetadata(): Boolean = isBlank() || this in unknownMetadata

    private data class LocalTrackMetadata(
        val title: String,
        val artist: String,
        val album: String
    )

    private data class DurationCluster(
        val anchorDurationMs: Long,
        val tracks: MutableList<Track>
    )

    private data class TrackCluster(val tracks: MutableList<Track>)
}
