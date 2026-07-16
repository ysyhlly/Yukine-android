package app.yukine

import app.yukine.model.Track
import app.yukine.streaming.RecordingMatchEvaluatorV2
import app.yukine.streaming.RecordingVersionClassifier
import app.yukine.streaming.StreamingTrackMatchPolicy

/**
 * Produces the logical library list without changing stored rows or source files.
 *
 * A song can be available from a device scan, document import, WebDAV, or network stream. When
 * complete metadata and duration agree, those rows are one logical song with interchangeable
 * sources. Remote catalog copies may disagree on album labels, while two local album copies stay
 * separate. Remix/version, unknown metadata, and materially different duration never collapse.
 */
object LibraryTrackMergePolicy {
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

    fun merge(
        tracks: List<Track>,
        canonicalIdentity: (Track) -> String?
    ): List<Track> = snapshot(tracks, canonicalIdentity).mergedTracks

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
    fun snapshot(tracks: List<Track>): Snapshot = snapshot(tracks) { null }

    fun snapshot(
        tracks: List<Track>,
        canonicalIdentity: (Track) -> String?
    ): Snapshot {
        return snapshotOf(clusters(tracks, canonicalIdentity))
    }

    /**
     * Fast display path for a library whose recording identities were already persisted during
     * scan/sync. Missing identities stay as single rows; fuzzy scoring belongs to ingestion and
     * background correction, never to opening or searching the library.
     */
    fun persistedSnapshot(
        tracks: List<Track>,
        canonicalIdentity: (Track) -> String?
    ): Snapshot = persistedSnapshotBy(tracks, canonicalIdentity)

    /** Integer-keyed variant used by the Room-backed library hot path. */
    fun persistedRecordingSnapshot(
        tracks: List<Track>,
        recordingIdentity: (Track) -> Long?
    ): Snapshot = persistedSnapshotBy(tracks, recordingIdentity)

    private fun <Identity : Any> persistedSnapshotBy(
        tracks: List<Track>,
        identityProvider: (Track) -> Identity?
    ): Snapshot {
        val clusters = ArrayList<TrackCluster>(tracks.size)
        val clustersByIdentity = HashMap<Identity, TrackCluster>()
        val clustersByTrackId = HashMap<Long, TrackCluster>()
        tracks.forEach { track ->
            val identity = identityProvider(track)
            val cluster = identity?.let(clustersByIdentity::get) ?: clustersByTrackId[track.id]
            if (cluster == null) {
                val created = TrackCluster(mutableListOf(track), null)
                clusters += created
                clustersByTrackId[track.id] = created
                identity?.let { clustersByIdentity[it] = created }
            } else if (cluster.tracks.none { it.id == track.id }) {
                cluster.tracks += track
            }
        }
        return snapshotOf(clusters)
    }

    private fun snapshotOf(clusters: List<TrackCluster>): Snapshot {
        clusters.forEach { cluster -> cluster.tracks.sortWith(sourcePreference) }
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

    private fun clusters(
        tracks: List<Track>,
        canonicalIdentity: (Track) -> String?
    ): List<TrackCluster> {
        if (tracks.isEmpty()) {
            return emptyList()
        }

        val result = ArrayList<TrackCluster>(tracks.size)
        val clustersByTitle = HashMap<String, MutableList<TrackCluster>>()
        val clustersByCanonicalIdentity = HashMap<String, MutableList<TrackCluster>>()
        tracks.forEach { track ->
            val identity = canonicalIdentity(track)?.trim()?.takeIf { it.isNotBlank() }
            val anchoredCluster = identity?.let { canonicalId ->
                clustersByCanonicalIdentity[canonicalId]
                    .orEmpty()
                    .map { cluster -> cluster to clusterMatch(cluster, track) }
                    .filterNot { (_, match) -> match.hardConflict }
                    .maxByOrNull { (_, match) -> match.score }
                    ?.first
            }
            if (anchoredCluster != null) {
                anchoredCluster.tracks += track
                return@forEach
            }
            val metadata = track.mergeMetadataOrNull()
            if (metadata == null) {
                val cluster = TrackCluster(mutableListOf(track), identity)
                result += cluster
                identity?.let { canonicalId ->
                    clustersByCanonicalIdentity.getOrPut(canonicalId) { ArrayList() } += cluster
                }
                return@forEach
            }

            val clusters = clustersByTitle.getOrPut(metadata.title) { ArrayList() }
            val rankedClusters = clusters.asSequence()
                .filter { cluster -> identity == null || cluster.canonicalIdentity == null }
                .map { cluster ->
                    cluster to clusterMatch(cluster, track)
                }
                .sortedByDescending { (_, evaluation) -> evaluation.score }
                .toList()
            val best = rankedClusters.firstOrNull()
            val runnerUpScore = rankedClusters.getOrNull(1)?.second?.score ?: 0.0
            val matchingCluster = best?.takeIf { (_, evaluation) ->
                !evaluation.hardConflict &&
                    evaluation.score >= RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_SCORE &&
                    evaluation.score - runnerUpScore >=
                    RecordingMatchEvaluatorV2.AUTO_MERGE_MINIMUM_MARGIN
            }?.first
            if (matchingCluster == null) {
                val cluster = TrackCluster(mutableListOf(track), identity)
                clusters += cluster
                result += cluster
                identity?.let { canonicalId ->
                    clustersByCanonicalIdentity.getOrPut(canonicalId) { ArrayList() } += cluster
                }
            } else {
                matchingCluster.tracks += track
                if (identity != null && matchingCluster.canonicalIdentity == null) {
                    matchingCluster.canonicalIdentity = identity
                    clustersByCanonicalIdentity.getOrPut(identity) { ArrayList() } += matchingCluster
                }
            }
        }
        return result
    }

    /** Complete-link comparison prevents A≈B and B≈C from silently producing A=B=C. */
    private fun clusterMatch(cluster: TrackCluster, candidate: Track): ClusterMatch {
        var minimumScore = 1.0
        cluster.tracks.forEach { member ->
            val evaluation = RecordingMatchEvaluatorV2.evaluate(
                StreamingTrackMatchPolicy.reference(member),
                StreamingTrackMatchPolicy.reference(candidate)
            )
            if (evaluation.hardConflicts.isNotEmpty()) {
                return ClusterMatch(0.0, hardConflict = true)
            }
            minimumScore = minOf(minimumScore, evaluation.sameRecordingProbability)
        }
        return ClusterMatch(minimumScore, hardConflict = false)
    }

    private fun Track.mergeMetadataOrNull(): LocalTrackMetadata? {
        if (durationMs <= 0L) {
            return null
        }

        val normalizedTitle = RecordingVersionClassifier.coreTitle(title)
        val normalizedArtist = StreamingTrackMatchPolicy.canonicalArtistKey(listOf(artist))
        val normalizedAlbum = StreamingTrackMatchPolicy.canonicalAlbum(album)
        if (
            normalizedTitle.isUnknownMetadata() ||
            normalizedArtist.isUnknownMetadata() ||
            normalizedAlbum.isUnknownMetadata()
        ) {
            return null
        }
        return LocalTrackMetadata(normalizedTitle)
    }

    private val sourcePreference = compareBy<Track> { track ->
        val path = track.dataPath.orEmpty().lowercase()
        when {
            path.startsWith("document:") -> 0
            !path.startsWith("webdav:") && !path.startsWith("streaming:") && !path.startsWith("stream:") -> 0
            path.startsWith("webdav:") -> 1
            path.contains(":netease:") -> 2
            path.contains(":qqmusic:") || path.contains(":qq:") -> 3
            path.contains(":luoxue:") || path.contains(":lx:") -> 4
            else -> 5
        }
    }

    private fun String.isUnknownMetadata(): Boolean = isBlank() || this in unknownMetadata

    private data class LocalTrackMetadata(val title: String)

    private data class TrackCluster(
        val tracks: MutableList<Track>,
        var canonicalIdentity: String?
    )

    private data class ClusterMatch(
        val score: Double,
        val hardConflict: Boolean
    )
}
