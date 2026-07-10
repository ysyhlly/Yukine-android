package app.yukine.streaming

enum class StreamingProviderName(val wireName: String) {
    MOCK("mock"),
    NETEASE("netease"),
    QQ_MUSIC("qqmusic"),
    KUGOU("kugou"),
    BILIBILI("bilibili"),
    YOUTUBE("youtube"),
    SOUNDCLOUD("soundcloud"),
    SPOTIFY("spotify"),
    TIDAL("tidal"),
    M3U8("m3u8"),
    LUOXUE("luoxue"),
    PLUGIN("plugin");

    companion object {
        fun fromWireName(value: String): StreamingProviderName? {
            val normalized = value.trim()
                .lowercase()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
            return when (normalized) {
                "lx",
                "lxmusic",
                "lxsource",
                "lxplugin",
                "lxmusicplugin",
                "lxmusicsource",
                "luoxue",
                "luoxuemusic",
                "luoxuesource",
                "luoxueplugin",
                "luoxuemusicsource",
                "luoxuemusicplugin",
                "洛雪",
                "洛雪音乐",
                "洛雪音源",
                "酷我",
                "酷我音乐",
                "咪咕",
                "咪咕音乐" -> LUOXUE
                "qq", "qqmusic", "tx", "tencent", "tencentmusic" -> QQ_MUSIC
                "kg", "kugou" -> KUGOU
                "kw", "kuwo", "mg", "migu", "migu_music", "migumusic" -> LUOXUE
                "wy", "netease", "neteasecloud", "neteasemusic", "163", "163music" -> NETEASE
                else -> entries.firstOrNull {
                    it.wireName.replace("_", "") == normalized
                }
            }
        }
    }
}

enum class StreamingMediaType(val wireName: String) {
    TRACK("track"),
    ALBUM("album"),
    ARTIST("artist"),
    PLAYLIST("playlist"),
    MV("mv");

    companion object {
        fun fromWireName(value: String): StreamingMediaType? {
            return entries.firstOrNull { it.wireName == value.trim().lowercase() }
        }
    }
}

enum class StreamingAudioQuality(val wireName: String) {
    STANDARD("standard"),
    HIGH("high"),
    LOSSLESS("lossless"),
    HIRES("hires");

    companion object {
        fun fromWireName(value: String): StreamingAudioQuality? {
            return entries.firstOrNull { it.wireName == value.trim().lowercase() }
        }
    }
}

enum class StreamingProviderStatus {
    READY,
    NEEDS_ACCOUNT,
    DISABLED,
    ERROR
}

enum class StreamingErrorCode(val wireName: String) {
    AUTH_REQUIRED("AUTH_REQUIRED"),
    RATE_LIMITED("RATE_LIMITED"),
    REGION_BLOCKED("REGION_BLOCKED"),
    SOURCE_UNAVAILABLE("SOURCE_UNAVAILABLE"),
    UNSUPPORTED_OPERATION("UNSUPPORTED_OPERATION"),
    GATEWAY_UNAVAILABLE("GATEWAY_UNAVAILABLE"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWireName(value: String?): StreamingErrorCode {
            val normalized = value.orEmpty().trim().uppercase()
            return entries.firstOrNull { it.wireName == normalized } ?: UNKNOWN
        }
    }
}

enum class StreamingAuthKind(val wireName: String) {
    NONE("none"),
    CUSTOM_TABS_APP_LINK("custom_tabs_app_link"),
    ISOLATED_WEB_VIEW_COOKIE("isolated_web_view_cookie"),
    REMOTE_GATEWAY("remote_gateway")
}

/**
 * The locally persisted lifecycle of a provider credential.
 *
 * This intentionally describes the credential rather than the current network state: a transient
 * network failure moves a cookie to [PENDING_VERIFICATION], while only an explicit platform
 * rejection moves it to [INVALID].
 */
enum class StreamingCredentialState(val wireName: String) {
    NOT_LOGGED_IN("not_logged_in"),
    PENDING_VERIFICATION("pending_verification"),
    VALID("valid"),
    INVALID("invalid");

    companion object {
        fun fromWireName(value: String?): StreamingCredentialState? {
            return entries.firstOrNull { it.wireName == value.orEmpty().trim().lowercase() }
        }
    }
}
