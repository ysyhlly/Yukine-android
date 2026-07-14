package app.yukine

/** Typed network destination; [route] remains the stable saved-state wire value. */
enum class NetworkPage(val route: String) {
    Home("network_home"),
    Streaming("network_streaming"),
    StreamingHub("network_streaming_hub"),
    StreamList("network_stream_list"),
    WebDav("network_webdav"),
    WebDavTracks("network_webdav_tracks"),
    WebDavSourceTracks("network_webdav_source_tracks"),
    Sources("network_sources");

    companion object {
        @JvmStatic
        fun fromRoute(route: String?): NetworkPage =
            entries.firstOrNull { it.route == route } ?: Home
    }
}
