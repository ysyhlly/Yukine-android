package app.yukine.streaming

import kotlin.math.max

internal fun String.rankText(): String {
    return lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

public fun List<StreamingTrack>.rankBySearchSimilarity(query: String): List<StreamingTrack> {
    val normalizedQuery = query.rankText()
    if (normalizedQuery.isBlank() || size <= 1) {
        return this
    }
    return withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<StreamingTrack>> { (_, track) ->
                track.searchSimilarityScore(normalizedQuery)
            }.thenBy { it.index }
        )
        .map { it.value }
}

public fun List<StreamingSearchItem>.rankItemsBySearchSimilarity(query: String): List<StreamingSearchItem> {
    val normalizedQuery = query.rankText()
    if (normalizedQuery.isBlank() || size <= 1) {
        return this
    }
    return withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<StreamingSearchItem>> { (_, item) ->
                item.track?.searchSimilarityScore(normalizedQuery) ?: 0
            }.thenBy { it.index }
        )
        .map { it.value }
}

internal fun StreamingTrack.searchSimilarityScore(normalizedQuery: String): Int {
    val title = title.rankText()
    val artist = artist.rankText()
    val album = album.orEmpty().rankText()
    return similarityScore(normalizedQuery, title) * 6 +
        similarityScore(normalizedQuery, "$title $artist") * 3 +
        similarityScore(normalizedQuery, artist) * 2 +
        similarityScore(normalizedQuery, album)
}

internal fun similarityScore(query: String, candidate: String): Int {
    if (query.isBlank() || candidate.isBlank()) {
        return 0
    }
    if (query == candidate) {
        return 1_000
    }
    if (candidate.startsWith(query)) {
        return 850
    }
    if (candidate.contains(query)) {
        return 720
    }
    val queryTokens = query.split(' ').filter { it.isNotBlank() }
    val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }
    val tokenHits = queryTokens.count { token ->
        candidateTokens.any { it == token || it.startsWith(token) || it.contains(token) }
    }
    val tokenScore = if (queryTokens.isNotEmpty()) tokenHits * 500 / queryTokens.size else 0
    val distance = levenshteinDistance(query, candidate)
    val maxLength = max(query.length, candidate.length).coerceAtLeast(1)
    val fuzzyScore = ((maxLength - distance).coerceAtLeast(0) * 360) / maxLength
    return max(tokenScore, fuzzyScore)
}

internal fun levenshteinDistance(first: String, second: String): Int {
    if (first == second) {
        return 0
    }
    if (first.isEmpty()) {
        return second.length
    }
    if (second.isEmpty()) {
        return first.length
    }
    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)
    for (i in first.indices) {
        current[0] = i + 1
        for (j in second.indices) {
            val cost = if (first[i] == second[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[second.length]
}
