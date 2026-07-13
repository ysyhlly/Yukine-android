package app.yukine;

public final class PlaybackErrorMessageLocalizer {
    private PlaybackErrorMessageLocalizer() {
    }

    public static String localize(String message, String languageMode) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        String value = message.trim();
        String key = keyFor(value);
        if (key == null) {
            return value;
        }
        return AppLanguage.text(languageMode, key);
    }

    private static String keyFor(String value) {
        if (value.equals("\u65e0\u6cd5\u64ad\u653e\u8fd9\u9996\u6b4c\u66f2\u3002")
                || value.equals("Unable to play this track.")) {
            return "playback.error.unable.to.play";
        }
        if (value.equals("\u64ad\u653e\u5c1a\u672a\u5c31\u7eea\u3002")
                || value.equals("Playback is not ready.")) {
            return "playback.error.not.ready";
        }
        if (value.equals("\u6d41\u5a92\u4f53\u6b4c\u66f2\u5c1a\u672a\u89e3\u6790\uff0c\u8bf7\u91cd\u65b0\u70b9\u51fb\u6b4c\u66f2\u64ad\u653e\u3002")
                || value.equals("Streaming track is still resolving. Tap the track again to play.")) {
            return "playback.error.streaming.not.resolved";
        }
        if (value.equals("\u65e0\u6cd5\u6253\u5f00\u8fd9\u9996\u6b4c\u66f2\u3002")
                || value.equals("Unable to open this track.")) {
            return "playback.error.unable.to.open";
        }
        return null;
    }
}
