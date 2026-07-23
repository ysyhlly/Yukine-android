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
            put(JSONObject()
                .put("id", item.stableId)
                .put("title", item.title)
                .put("artist", item.artist)
                .put("uri", item.sourceUri)
                .put("size", item.sizeBytes)
                .put("root", item.contentRoot))
        }
    }.toString()

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
