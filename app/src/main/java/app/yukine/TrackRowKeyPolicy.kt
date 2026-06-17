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
}
