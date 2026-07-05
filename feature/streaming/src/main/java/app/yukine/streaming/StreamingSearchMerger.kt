package app.yukine.streaming

private const val CROSS_SOURCE_DURATION_TOLERANCE_MS = 3_000L

/**
 * 把不同音源里「同作者 + 同曲名」（时长在容差内）的曲目合并为一条，列表只保留一条代表项，
 * 其余音源折叠进代表项的 [StreamingTrack.playbackCandidates]，供播放解析失败时自动回退、
 * 以及在播放页手动切换音源使用。仅在多音源聚合搜索结果上调用。
 */
public fun StreamingSearchResult.mergeCrossSourceDuplicates(): StreamingSearchResult {
    if (tracks.size <= 1) {
        return this
    }
    val itemsByKey = unifiedItems
        .filter { it.type == StreamingMediaType.TRACK && it.track != null }
        .associateBy { "${it.provider.wireName}:${it.id}" }
    // 保持原有排序：按首次出现的代表项顺序聚类。
    val clusters = LinkedHashMap<String, MutableList<StreamingTrack>>()
    tracks.forEach { track ->
        val key = track.crossSourceMergeKey()
        val bucket = clusters.getOrPut(key) { mutableListOf() }
        val sibling = bucket.firstOrNull { it.isSameSongAcrossSource(track) }
        if (sibling != null || bucket.isEmpty()) {
            bucket.add(track)
        } else {
            // 同作者+同曲名但时长差异过大（疑似同名不同曲），归入带序号的独立簇避免误合并。
            clusters.getOrPut("$key#${clusters.size}") { mutableListOf() }.add(track)
        }
    }
    val mergedTracks = clusters.values
        .filter { it.isNotEmpty() }
        .map { group -> group.mergeSourcesIntoRepresentative() }
    if (mergedTracks.size == tracks.size) {
        return this
    }
    val mergedItems = mergedTracks.map { track ->
        itemsByKey["${track.provider.wireName}:${track.providerTrackId}"]
            ?.copy(track = track)
            ?: StreamingSearchItem.fromTrack(track)
    }
    return copy(
        tracks = mergedTracks,
        total = mergedTracks.size,
        items = mergedItems
    )
}

/** 归一化「作者 + 曲名」作为合并主键；作者多人时排序 token 以兼容不同音源的拼接顺序。 */
internal fun StreamingTrack.crossSourceMergeKey(): String {
    val normalizedTitle = title.rankText()
    val normalizedArtist = artist.rankText()
        .split(' ')
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString(" ")
    return "$normalizedArtist$normalizedTitle"
}

/** 作者名与曲名归一化相同，且时长缺失或落在 ±3 秒容差内时，判定为同一首歌。 */
internal fun StreamingTrack.isSameSongAcrossSource(other: StreamingTrack): Boolean {
    if (crossSourceMergeKey() != other.crossSourceMergeKey()) {
        return false
    }
    val left = durationMs
    val right = other.durationMs
    if (left == null || right == null || left <= 0L || right <= 0L) {
        return true
    }
    return kotlin.math.abs(left - right) <= CROSS_SOURCE_DURATION_TOLERANCE_MS
}

/** 选可播放的首项作代表，其余音源折叠为备用候选；代表项已有候选保留在前。 */
internal fun List<StreamingTrack>.mergeSourcesIntoRepresentative(): StreamingTrack {
    if (size == 1) {
        return first()
    }
    val representative = firstOrNull { it.playable } ?: first()
    val seen = linkedSetOf("${representative.provider.wireName}:${representative.providerTrackId}")
    val candidates = representative.playbackCandidates.toMutableList()
    forEach { track ->
        val identity = "${track.provider.wireName}:${track.providerTrackId}"
        if (!seen.add(identity)) {
            return@forEach
        }
        candidates += StreamingPlaybackCandidate(
            provider = track.provider,
            quality = null,
            label = track.provider.wireName,
            providerTrackId = track.providerTrackId,
            available = track.playable
        )
    }
    return representative.copy(playbackCandidates = candidates)
}

public fun StreamingSearchResult.trackOnlySearchResult(): StreamingSearchResult {
    val trackItems = unifiedItems
        .filter { it.type == StreamingMediaType.TRACK && it.track != null }
        .distinctBy { "${it.provider.wireName}:${it.id}" }
    val trackItemsByKey = trackItems.associateBy { "${it.provider.wireName}:${it.id}" }
    val normalizedTracks = (tracks + trackItems.mapNotNull { it.track })
        .distinctBy { "${it.provider.wireName}:${it.providerTrackId}" }
    val normalizedItems = normalizedTracks.map { track ->
        trackItemsByKey["${track.provider.wireName}:${track.providerTrackId}"]
            ?: StreamingSearchItem.fromTrack(track)
    }
    return copy(
        tracks = normalizedTracks,
        albums = emptyList(),
        artists = emptyList(),
        playlists = emptyList(),
        mvs = emptyList(),
        total = normalizedTracks.size,
        items = normalizedItems
    )
}

public fun mergeStreamingSearchResults(
    query: String,
    pageSize: Int,
    results: List<StreamingSearchResult>
): StreamingSearchResult {
    val tracks = results
        .flatMap { it.tracks }
        .distinctBy { "${it.provider.wireName}:${it.providerTrackId}" }
        .rankBySearchSimilarity(query)
    val albums = results
        .flatMap { it.albums }
        .distinctBy { "${it.provider.wireName}:${it.providerAlbumId}" }
    val artists = results
        .flatMap { it.artists }
        .distinctBy { "${it.provider.wireName}:${it.providerArtistId}" }
    val playlists = results
        .flatMap { it.playlists }
        .distinctBy { "${it.provider.wireName}:${it.providerPlaylistId}" }
    val mvs = results
        .flatMap { it.mvs }
        .distinctBy { "${it.provider.wireName}:${it.providerMvId}" }
    return StreamingSearchResult(
        provider = results.firstOrNull { it.tracks.isNotEmpty() }?.provider
            ?: results.first().provider,
        query = query,
        page = 1,
        pageSize = pageSize,
        total = results.mapNotNull { it.total }.takeIf { it.isNotEmpty() }?.sum(),
        hasMore = false,
        tracks = tracks,
        albums = albums,
        artists = artists,
        playlists = playlists,
        mvs = mvs,
        cached = results.all { it.cached },
        items = results.flatMap { it.unifiedItems }
            .distinctBy { "${it.provider.wireName}:${it.type.wireName}:${it.id}" }
            .rankItemsBySearchSimilarity(query)
    )
}
