package app.yukine

import app.yukine.model.Track

internal object TrackRowKeyPolicy {
    @JvmStatic
    fun occurrenceKey(tracks: List<Track>?, index: Int): String {
        if (tracks == null || index < 0 || index >= tracks.size) {
            return "missing:$index"
        }

        val trackId = tracks[index].id
        var occurrence = 0
        for (i in 0..index) {
            if (tracks[i].id == trackId) {
                occurrence++
            }
        }
        return "$trackId:$occurrence"
    }

    @JvmStatic
    fun occurrenceKeys(tracks: List<Track>?): List<String> {
        if (tracks.isNullOrEmpty()) {
            return emptyList()
        }
        val occurrences = HashMap<Long, Int>(tracks.size)
        return tracks.map { track ->
            val occurrence = (occurrences[track.id] ?: 0) + 1
            occurrences[track.id] = occurrence
            "${track.id}:$occurrence"
        }
    }
}
