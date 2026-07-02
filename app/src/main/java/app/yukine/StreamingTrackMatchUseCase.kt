package app.yukine

import app.yukine.common.StreamingDataPathMetadata
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.playback.PlaybackStateSnapshot
import app.yukine.streaming.StreamingProviderName
import java.util.Locale

internal interface StreamingTrackMatchOperations {
    fun loadStreamingTrackMatch(track: Track, provider: String): String

    fun saveStreamingTrackMatch(track: Track, provider: String, providerTrackId: String)
}

internal class MusicLibraryStreamingTrackMatchOperations(
    private val repository: MusicLibraryRepository
) : StreamingTrackMatchOperations {
    override fun loadStreamingTrackMatch(track: Track, provider: String): String =
        repository.loadStreamingTrackMatch(track, provider)

    override fun saveStreamingTrackMatch(track: Track, provider: String, providerTrackId: String) {
        repository.saveStreamingTrackMatch(track, provider, providerTrackId)
    }
}

internal class StreamingTrackMatchUseCase(
    private val operations: StreamingTrackMatchOperations
) : StreamingTrackMatchStore {
    override fun directProviderTrackId(track: Track, provider: StreamingProviderName): String {
        return directProviderTrackIdOrEmpty(track, provider)
    }

    private fun directProviderTrackIdOrEmpty(track: Track?, provider: StreamingProviderName?): String {
        if (track == null || provider == null) {
            return ""
        }
        val trackProvider = StreamingDataPathMetadata.provider(track.dataPath)
        if (trackProvider != provider) {
            return providerTrackIdFromLocation(track, provider)
        }
        val directTrackId = StreamingDataPathMetadata.providerTrackId(track.dataPath).trim()
        if (directTrackId.isNotEmpty()) {
            return directTrackId
        }
        return providerTrackIdFromLocation(track, provider)
    }

    fun addHeartbeatSeedCandidate(
        candidates: MutableList<Track>,
        seen: MutableSet<String>,
        track: Track?
    ) {
        val key = heartbeatSeedCandidateKey(track)
        if (key.isEmpty() || !seen.add(key)) {
            return
        }
        candidates.add(track!!)
    }

    override fun providerTrackIdFromCandidates(
        candidates: List<Track?>?,
        provider: StreamingProviderName?
    ): String {
        if (provider == null) {
            return ""
        }
        return candidates
            .orEmpty()
            .firstNotNullOfOrNull { track ->
                directProviderTrackIdOrEmpty(track, provider).takeIf { it.isNotEmpty() }
            }
            .orEmpty()
    }

    override fun heartbeatSeedCandidates(
        serviceSnapshot: PlaybackStateSnapshot?,
        serviceQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?,
        viewModelQueue: List<Track?>?
    ): List<Track> {
        val candidates = mutableListOf<Track>()
        val seen = mutableSetOf<String>()
        serviceQueue.orEmpty().forEach { addHeartbeatSeedCandidate(candidates, seen, it) }
        viewModelQueue.orEmpty().forEach { addHeartbeatSeedCandidate(candidates, seen, it) }
        addHeartbeatSnapshotCandidates(candidates, seen, serviceSnapshot, serviceQueue)
        if (storeSnapshot !== serviceSnapshot) {
            addHeartbeatSnapshotCandidates(candidates, seen, storeSnapshot, viewModelQueue)
        }
        return candidates
    }

    override fun snapshotQueueForHeartbeat(
        serviceQueue: List<Track?>?,
        viewModelQueue: List<Track?>?,
        storeSnapshot: PlaybackStateSnapshot?
    ): List<Track> {
        val queue = mutableListOf<Track>()
        val seen = mutableSetOf<String>()
        serviceQueue.orEmpty().forEach { addHeartbeatSeedCandidate(queue, seen, it) }
        viewModelQueue.orEmpty().forEach { addHeartbeatSeedCandidate(queue, seen, it) }
        addHeartbeatSeedCandidate(queue, seen, storeSnapshot?.currentTrack)
        return queue
    }

    override fun heartbeatSeedMissMessage(
        provider: StreamingProviderName?,
        snapshot: PlaybackStateSnapshot?,
        storeSnapshot: PlaybackStateSnapshot?,
        queue: List<Track?>?
    ): String {
        val builder = StringBuilder()
        builder.append("Heartbeat seed missing provider=")
            .append(provider?.wireName ?: "null")
        builder.append(", currentIndex=")
            .append(snapshot?.currentIndex ?: -1)
        builder.append(", queueSize=")
            .append(queue?.size ?: 0)
        snapshot?.currentTrack?.let { track ->
            builder.append(", snapshotDataPath=").append(track.dataPath)
            builder.append(", snapshotTitle=").append(track.title)
        }
        if (storeSnapshot != null && storeSnapshot.currentTrack != null && storeSnapshot !== snapshot) {
            builder.append(", storeDataPath=").append(storeSnapshot.currentTrack.dataPath)
            builder.append(", storeTitle=").append(storeSnapshot.currentTrack.title)
        }
        queue.orEmpty().take(5).forEachIndexed { index, track ->
            builder.append(", q").append(index).append("=")
            if (track == null) {
                builder.append("null")
            } else {
                builder.append(track.dataPath)
                    .append("|")
                    .append(track.title)
                    .append("|")
                    .append(track.artist)
            }
        }
        return builder.toString()
    }

    override fun providerTrackIdFor(track: Track, provider: StreamingProviderName): String {
        val directTrackId = directProviderTrackIdOrEmpty(track, provider)
        if (directTrackId.isNotEmpty()) {
            return directTrackId
        }
        return operations.loadStreamingTrackMatch(track, provider.wireName).trim()
    }

    override fun saveProviderTrackId(track: Track, provider: StreamingProviderName, providerTrackId: String) {
        val cleanTrackId = providerTrackId.trim()
        if (cleanTrackId.isEmpty()) {
            return
        }
        operations.saveStreamingTrackMatch(track, provider.wireName, cleanTrackId)
    }

    private fun addHeartbeatSnapshotCandidates(
        candidates: MutableList<Track>,
        seen: MutableSet<String>,
        snapshot: PlaybackStateSnapshot?,
        queue: List<Track?>?
    ) {
        if (snapshot == null) {
            return
        }
        addHeartbeatSeedCandidate(candidates, seen, snapshot.currentTrack)
        if (!queue.isNullOrEmpty() && snapshot.currentIndex >= 0 && snapshot.currentIndex < queue.size) {
            addHeartbeatSeedCandidate(candidates, seen, queue[snapshot.currentIndex])
        }
    }

    private fun heartbeatSeedCandidateKey(track: Track?): String {
        if (track == null) {
            return ""
        }
        if (track.dataPath.isNotEmpty()) {
            return "path:${track.dataPath}"
        }
        val contentUri = track.contentUri?.toString().orEmpty()
        if (contentUri.isNotEmpty()) {
            return "uri:$contentUri"
        }
        return "id:${track.id}|${track.title}|${track.artist}|${track.durationMs}"
    }

    private fun providerTrackIdFromLocation(track: Track?, provider: StreamingProviderName): String {
        if (track == null || provider != StreamingProviderName.NETEASE) {
            return ""
        }
        val dataPathId = neteaseTrackIdFromText(track.dataPath)
        if (dataPathId.isNotEmpty()) {
            return dataPathId
        }
        return neteaseTrackIdFromText(track.contentUri?.toString().orEmpty())
    }

    private fun neteaseTrackIdFromText(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        val lowerValue = value.lowercase(Locale.ROOT)
        for (marker in listOf("streaming:netease:", "stream:netease:", "netease:")) {
            val markerId = neteaseIdAfterMarker(value, lowerValue, marker)
            if (markerId.isNotEmpty()) {
                return markerId
            }
        }
        if (!lowerValue.contains("music.163.com") && !lowerValue.contains("music.126.net")) {
            return ""
        }
        for (key in listOf("id=", "songid=")) {
            val queryId = neteaseQueryValue(value, key)
            if (queryId.isNotEmpty()) {
                return queryId
            }
        }
        val songStart = lowerValue.indexOf("/song/")
        if (songStart < 0) {
            return ""
        }
        val digitStart = (songStart + "/song/".length until value.length)
            .firstOrNull { index -> value[index].isDigit() }
            ?: return ""
        val digitEnd = (digitStart until value.length)
            .firstOrNull { index -> !value[index].isDigit() }
            ?: value.length
        return value.substring(digitStart, digitEnd)
    }

    private fun neteaseIdAfterMarker(value: String, lowerValue: String, marker: String): String {
        val markerStart = lowerValue.indexOf(marker)
        if (markerStart < 0) {
            return ""
        }
        var start = markerStart + marker.length
        while (start < value.length && !value[start].isDigit()) {
            start++
        }
        var end = start
        while (end < value.length && value[end].isDigit()) {
            end++
        }
        return if (end > start) value.substring(start, end) else ""
    }

    private fun neteaseQueryValue(value: String, key: String): String {
        val lowerValue = value.lowercase(Locale.ROOT)
        val lowerKey = key.lowercase(Locale.ROOT)
        var start = lowerValue.indexOf(lowerKey)
        if (start < 0) {
            return ""
        }
        start += lowerKey.length
        while (start < value.length && !value[start].isDigit()) {
            start++
        }
        var end = start
        while (end < value.length && value[end].isDigit()) {
            end++
        }
        return if (end > start) value.substring(start, end) else ""
    }
}
