package app.yukine.together

import org.json.JSONArray
import org.json.JSONObject

internal object TogetherJson {
    fun options(options: TogetherConnectOptions): String = JSONObject()
        .put("v", 1)
        .put("nickname", options.nickname.trim())
        .put("relays", JSONArray(options.relays))
        .put("turn_url", options.turnUrl)
        .put("turn_username", options.turnUsername)
        .put("turn_password", options.turnPassword)
        .put("cache_directory", options.cacheDirectory)
        .toString()

    fun queue(items: List<TogetherQueueItem>): String = JSONArray().apply {
        items.forEach { item ->
            val json = JSONObject()
                .put("id", item.stableId)
                .put("title", item.title)
                .put("artist", item.artist)
                .put("uri", item.sourceUri)
                .put("size", item.sizeBytes)
                .put("root", item.contentRoot)
                .put("album", item.album)
                .put("artwork_uri", publicArtworkUri(item.artworkUri))
                .put("duration_ms", item.durationMs)
            when (val source = item.source) {
                is TogetherQueueSource.Local -> json.put("kind", "local")
                is TogetherQueueSource.Streaming -> json
                    .put("kind", "streaming")
                    .put("provider", source.provider)
                    .put("track_id", source.providerTrackId)
                    .put("quality", source.quality)
                    .put("duration_ms", source.durationMs)
                    .put("url", source.resolvedUrl)
                    .put("headers", JSONObject(source.headers))
                    .put("expires_at_ms", source.expiresAtEpochMs)
                    .put("mime", source.mimeType)
                    .put("supports_range", source.supportsRange)
            }
            put(json)
        }
    }.toString()

    private fun publicArtworkUri(value: String): String =
        value.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }.orEmpty()

    fun playback(event: TogetherPlaybackEvent): String {
        val json = JSONObject().put("v", 1)
        when (event) {
            is TogetherPlaybackEvent.PauseChanged ->
                json.put("type", "pause").put("paused", event.paused)
            is TogetherPlaybackEvent.Seeked ->
                json.put("type", "seek").put("position_ms", event.positionMs)
            is TogetherPlaybackEvent.SpeedChanged ->
                json.put("type", "speed").put("speed", event.speed.toDouble())
            is TogetherPlaybackEvent.QueueIndexChanged ->
                json.put("type", "index").put("index", event.index)
            is TogetherPlaybackEvent.BufferingChanged ->
                json.put("type", "buffering").put("buffering", event.buffering)
            TogetherPlaybackEvent.PlaybackRestarted ->
                json.put("type", "playback_restart")
        }
        return json.toString()
    }
}
