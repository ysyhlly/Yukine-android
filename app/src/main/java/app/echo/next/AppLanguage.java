package app.echo.next;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import app.echo.next.ui.EchoTheme;

final class AppLanguage {
    static final String MODE_SYSTEM = "system";
    static final String MODE_CHINESE = "zh";
    static final String MODE_ENGLISH = "en";

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> ZH = new HashMap<>();

    static {
        put("tab.home", "Home", "\u4e3b\u9875");
        put("tab.library", "Library", "\u66f2\u5e93");
        put("tab.collections", "Collections", "\u6536\u85cf");
        put("tab.queue", "Queue", "\u961f\u5217");
        put("tab.now", "Now", "\u5f53\u524d");
        put("tab.network", "Network", "\u7f51\u7edc");
        put("tab.settings", "Settings", "\u8bbe\u7f6e");
        put("search.music", "Search music", "\u641c\u7d22\u97f3\u4e50");

        put("language.system", "Follow system", "\u8ddf\u968f\u7cfb\u7edf");
        put("language.chinese", "Chinese", "\u4e2d\u6587");
        put("language.english", "English", "\u82f1\u6587");
        put("language.applied", "Language: ", "\u8bed\u8a00\uff1a");

        put("theme", "Theme", "\u4e3b\u9898");
        put("accent", "Accent", "\u5f3a\u8c03\u8272");
        put("language", "Language", "\u8bed\u8a00");
        put("audio.permission", "Audio permission", "\u97f3\u9891\u6743\u9650");
        put("notification.permission", "Notification permission", "\u901a\u77e5\u6743\u9650");
        put("playback.service", "Playback service", "\u64ad\u653e\u670d\u52a1");
        put("streaming.gateway", "Streaming gateway", "\u4e32\u6d41\u7f51\u5173");
        put("endpoint", "Endpoint", "\u7aef\u70b9");
        put("granted", "Granted", "\u5df2\u6388\u6743");
        put("missing", "Missing", "\u672a\u6388\u6743");
        put("connected", "Connected", "\u5df2\u8fde\u63a5");
        put("disconnected", "Disconnected", "\u672a\u8fde\u63a5");
        put("appearance", "Appearance", "\u5916\u89c2");
        put("playback.speed", "Playback speed", "\u64ad\u653e\u901f\u5ea6");
        put("app.volume", "App volume", "\u5e94\u7528\u97f3\u91cf");
        put("streaming.audio.quality", "Streaming quality", "\u6d41\u5a92\u4f53\u97f3\u8d28");
        put("concurrent.playback", "Mix with other media", "\u4e0e\u5176\u4ed6\u5a92\u4f53\u540c\u65f6\u64ad\u653e");
        put("sleep.timer", "Sleep timer", "\u7761\u7720\u5b9a\u65f6");
        put("lyrics", "Lyrics", "\u6b4c\u8bcd");
        put("duration", "Duration", "\u65f6\u957f");
        put("library", "Library", "\u66f2\u5e93");
        put("songs", "Songs", "\u6b4c\u66f2");
        put("albums", "Albums", "\u4e13\u8f91");
        put("artists", "Artists", "\u827a\u4eba");
        put("scan.library", "Scan library", "\u626b\u63cf\u66f2\u5e93");
        put("import.audio.files", "Import audio files", "\u5bfc\u5165\u97f3\u9891\u6587\u4ef6");
        put("import.audio.folder", "Import audio folder", "\u5bfc\u5165\u97f3\u9891\u6587\u4ef6\u5939");
        put("offset", "Offset", "\u504f\u79fb");
        put("online.lyrics", "Online lyrics", "\u5728\u7ebf\u6b4c\u8bcd");
        put("provider", "Provider", "\u63d0\u4f9b\u65b9");
        put("local.lyrics", "Local lyrics", "\u672c\u5730\u6b4c\u8bcd");
        put("same.name.lrc", "Same-name .lrc files", "\u540c\u540d .lrc \u6587\u4ef6");
        put("disable.online.lyrics", "Disable online lyrics", "\u5173\u95ed\u5728\u7ebf\u6b4c\u8bcd");
        put("enable.online.lyrics", "Enable online lyrics", "\u5f00\u542f\u5728\u7ebf\u6b4c\u8bcd");
        put("reload.lyrics", "Reload lyrics", "\u91cd\u65b0\u52a0\u8f7d\u6b4c\u8bcd");
        put("description", "Description", "\u8bf4\u660e");
        put("sleep.timer.description", "Stop playback after the selected delay", "\u5230\u65f6\u540e\u505c\u6b62\u64ad\u653e");
        put("cancel.sleep.timer", "Cancel sleep timer", "\u53d6\u6d88\u7761\u7720\u5b9a\u65f6");
        put("speed.description", "Adjust playback speed", "\u8c03\u6574\u64ad\u653e\u901f\u5ea6");
        put("volume.description", "Adjust app playback volume", "\u8c03\u6574\u5e94\u7528\u64ad\u653e\u97f3\u91cf");
        put("streaming.quality.description", "Choose the maximum quality for online playback. Lower quality can reduce buffering.", "\u9009\u62e9\u5728\u7ebf\u64ad\u653e\u7684\u6700\u9ad8\u97f3\u8d28\u3002\u964d\u4f4e\u97f3\u8d28\u53ef\u51cf\u5c11\u7f13\u51b2\u3002");
        put("concurrent.playback.description", "Keep playing alongside other media apps instead of pausing them. ECHO will not request audio focus, so it won't pause other apps and won't be paused by them.", "\u4e0e\u5176\u4ed6\u5a92\u4f53\u5e94\u7528\u540c\u65f6\u51fa\u58f0\uff0c\u800c\u4e0d\u662f\u6682\u505c\u5b83\u4eec\u3002ECHO \u4e0d\u518d\u62a2\u5360\u97f3\u9891\u7126\u70b9\uff0c\u56e0\u6b64\u65e2\u4e0d\u4f1a\u6682\u505c\u5176\u4ed6\u5e94\u7528\uff0c\u4e5f\u4e0d\u4f1a\u88ab\u5b83\u4eec\u6682\u505c\u3002");
        put("enable.concurrent.playback", "Enable mixing", "\u5f00\u542f\u540c\u65f6\u64ad\u653e");
        put("disable.concurrent.playback", "Disable mixing", "\u5173\u95ed\u540c\u65f6\u64ad\u653e");
        put("options", "Options", "\u9009\u9879");
        put("back", "Back", "\u8fd4\u56de");
        put("disable", "Disable", "\u5173\u95ed");
        put("selected", " (selected)", "\uff08\u5df2\u9009\u62e9\uff09");
        put("off", "Off", "\u5173\u95ed");
        put("min.left", " min left", " \u5206\u949f\u540e");
        put("min", " min", " \u5206\u949f");
        put("enabled", "Enabled", "\u5df2\u5f00\u542f");
        put("disabled", "Disabled", "\u5df2\u5173\u95ed");
        put("grant.access", "Grant access", "\u6388\u6743\u8bbf\u95ee");
        put("audio.permission.required", "Audio permission required", "\u9700\u8981\u97f3\u9891\u6743\u9650");
        put("no.music", "No music found", "\u672a\u627e\u5230\u97f3\u4e50");

        put("theme.options", "System / Dark / Light / AMOLED / Contrast / Graphite", "\u7cfb\u7edf / \u6df1\u8272 / \u6d45\u8272 / \u7eaf\u9ed1 / \u9ad8\u5bf9\u6bd4 / \u77f3\u58a8");
        put("accent.options", "Blue / Teal / Rose / Violet / Amber / Emerald", "\u84dd\u8272 / \u9752\u7eff / \u73ab\u7ea2 / \u7d2b\u7f57\u5170 / \u7425\u73c0 / \u7fe0\u7eff");
        put("language.options", "System / Chinese / English", "\u8ddf\u968f\u7cfb\u7edf / \u4e2d\u6587 / English");
        put("streaming.gateway.description", "Android calls this gateway for provider search, auth, and playback URL resolution. Leave empty to use the in-app local login fallback.", "Android \u901a\u8fc7\u6b64\u7f51\u5173\u8c03\u7528\u6d41\u5a92\u4f53\u641c\u7d22\u3001\u767b\u5f55\u548c\u64ad\u653e URL \u89e3\u6790\u3002\u7559\u7a7a\u53ef\u4f7f\u7528\u672c\u5730\u767b\u5f55\u56de\u9000\u3002");
        put("streaming.gateway.emulator", "Android emulator host (10.0.2.2:43990)", "\u5b89\u5353\u6a21\u62df\u5668\u4e3b\u673a\uff0810.0.2.2:43990\uff09");
        put("streaming.gateway.localhost", "Local device host (127.0.0.1:43990)", "\u672c\u673a\u8bbe\u5907\u4e3b\u673a\uff08127.0.0.1:43990\uff09");
        put("quality.auto", "Auto", "\u81ea\u52a8");
        put("quality.standard", "Standard", "\u6807\u51c6");
        put("quality.high", "High", "\u9ad8\u97f3\u8d28");
        put("quality.lossless", "Lossless", "\u65e0\u635f");
        put("quality.hires", "Hi-Res", "Hi-Res");

        put("theme.applied", "Theme: ", "\u4e3b\u9898\uff1a");
        put("accent.applied", "Accent: ", "\u5f3a\u8c03\u8272\uff1a");
        put("speed.applied", "Playback speed: ", "\u64ad\u653e\u901f\u5ea6\uff1a");
        put("volume.applied", "App volume: ", "\u5e94\u7528\u97f3\u91cf\uff1a");
        put("streaming.quality.applied", "Streaming quality: ", "\u6d41\u5a92\u4f53\u97f3\u8d28\uff1a");
        put("streaming.quality.downgrading", "Buffering, switching stream to ", "\u7f13\u51b2\u5361\u987f\uff0c\u6b63\u5728\u5207\u6362\u5230 ");
        put("streaming.quality.downgraded", "Stream switched to ", "\u5df2\u5207\u6362\u5230 ");
        put("online.lyrics.enabled", "Online lyrics enabled", "\u5df2\u5f00\u542f\u5728\u7ebf\u6b4c\u8bcd");
        put("online.lyrics.disabled", "Online lyrics disabled", "\u5df2\u5173\u95ed\u5728\u7ebf\u6b4c\u8bcd");
        put("concurrent.playback.enabled", "Mixing with other media enabled", "\u5df2\u5f00\u542f\u4e0e\u5176\u4ed6\u5a92\u4f53\u540c\u65f6\u64ad\u653e");
        put("concurrent.playback.disabled", "Mixing with other media disabled", "\u5df2\u5173\u95ed\u4e0e\u5176\u4ed6\u5a92\u4f53\u540c\u65f6\u64ad\u653e");
        put("lyrics.offset.applied", "Lyrics offset: ", "\u6b4c\u8bcd\u504f\u79fb\uff1a");
        put("streaming.gateway.applied", "Streaming gateway: ", "\u4e32\u6d41\u7f51\u5173\uff1a");
        put("no.track.selected", "No track selected", "\u672a\u9009\u62e9\u6b4c\u66f2");
        put("reloading.lyrics", "Reloading lyrics", "\u6b63\u5728\u91cd\u65b0\u52a0\u8f7d\u6b4c\u8bcd");
        put("repeat.one", "Repeat one", "\u5355\u66f2\u5faa\u73af");
        put("repeat.off", "Repeat off", "\u5173\u95ed\u5faa\u73af");
        put("repeat.all", "Repeat all", "\u5217\u8868\u5faa\u73af");
        put("favorite", "Favorite", "\u6536\u85cf");
        put("favorited", "Favorited", "\u5df2\u6536\u85cf");
        put("shuffle", "Shuffle", "\u968f\u673a");
        put("in.order", "In order", "\u987a\u5e8f");

        put("favorites", "Favorites", "\u6536\u85cf");
        put("recent", "Recent", "\u6700\u8fd1");
        put("playlists", "Playlists", "\u64ad\u653e\u5217\u8868");
        put("most.played", "Most played", "\u64ad\u653e\u6700\u591a");
        put("new.playlist", "New playlist", "\u65b0\u5efa\u64ad\u653e\u5217\u8868");
        put("import.playlist.m3u", "Import playlist M3U/M3U8", "\u5bfc\u5165\u64ad\u653e\u5217\u8868 M3U/M3U8");
        put("clear.play.history", "Clear play history", "\u6e05\u7a7a\u64ad\u653e\u5386\u53f2");
        put("no.favorites", "No favorites", "\u6682\u65e0\u6536\u85cf");
        put("no.recent.tracks", "No recent tracks", "\u6682\u65e0\u6700\u8fd1\u64ad\u653e");
        put("no.play.history", "No play history", "\u6682\u65e0\u64ad\u653e\u5386\u53f2");
        put("play.favorites", "Play favorites", "\u64ad\u653e\u6536\u85cf");
        put("play.recent", "Play recent", "\u64ad\u653e\u6700\u8fd1");
        put("play.most.played", "Play most played", "\u64ad\u653e\u6700\u591a");
        put("play.playlist", "Play playlist", "\u64ad\u653e\u5217\u8868");
        put("export.playlist", "Export playlist", "\u5bfc\u51fa\u64ad\u653e\u5217\u8868");
        put("no.playlists", "No playlists", "\u6682\u65e0\u64ad\u653e\u5217\u8868");
        put("no.tracks.in.playlist", "No tracks in this playlist", "\u6b64\u64ad\u653e\u5217\u8868\u6682\u65e0\u66f2\u76ee");
        put("playlist", "Playlist", "\u64ad\u653e\u5217\u8868");
        put("played.at", "Played at ", "\u64ad\u653e\u4e8e ");
        put("played.once", "Played 1 time", "\u64ad\u653e 1 \u6b21");
        put("played.times.prefix", "Played ", "\u64ad\u653e ");
        put("played.times.suffix", " times", " \u6b21");
        put("remove.favorite", "Remove favorite", "\u53d6\u6d88\u6536\u85cf");
        put("add.to.playlist", "Add to playlist", "\u6dfb\u52a0\u5230\u64ad\u653e\u5217\u8868");
        put("rename", "Rename", "\u91cd\u547d\u540d");
        put("delete", "Delete", "\u5220\u9664");
        put("edit", "Edit", "\u7f16\u8f91");
        put("remove", "Remove", "\u79fb\u9664");
        put("up", "Up", "\u4e0a\u79fb");
        put("down", "Down", "\u4e0b\u79fb");

        put("remote.sources", "Remote sources", "\u8fdc\u7a0b\u6765\u6e90");
        put("streams", "Streams", "\u4e32\u6d41");
        put("webdav.sources", "WebDAV sources", "WebDAV \u6765\u6e90");
        put("streaming", "Streaming", "\u4e32\u6d41");
        put("webdav", "WebDAV", "WebDAV");
        put("source", "Source", "\u6765\u6e90");
        put("direct.url.m3u", "Direct URL / M3U", "\u76f4\u94fe / M3U");
        put("sources", "Sources", "\u6765\u6e90");
        put("tracks", "Tracks", "\u66f2\u76ee");
        put("sync.mode", "Sync mode", "\u540c\u6b65\u6a21\u5f0f");
        put("sync.mode.library", "Library", "\u66f2\u5e93");
        put("add.stream.url", "Add stream URL", "\u6dfb\u52a0\u4e32\u6d41 URL");
        put("import.m3u.url", "Import M3U URL", "\u5bfc\u5165 M3U URL");
        put("import.m3u.file", "Import M3U file", "\u5bfc\u5165 M3U \u6587\u4ef6");
        put("play.streams", "Play streams", "\u64ad\u653e\u4e32\u6d41");
        put("browse.streams", "Browse streams", "\u6d4f\u89c8\u4e32\u6d41");
        put("delete.streams", "Delete streams", "\u5220\u9664\u4e32\u6d41");
        put("add.webdav", "Add WebDAV", "\u6dfb\u52a0 WebDAV");
        put("sync.all", "Sync all", "\u5168\u90e8\u540c\u6b65");
        put("play.webdav", "Play WebDAV", "\u64ad\u653e WebDAV");
        put("browse.tracks", "Browse tracks", "\u6d4f\u89c8\u66f2\u76ee");
        put("manage.sources", "Manage sources", "\u7ba1\u7406\u6765\u6e90");
        put("back.to.network", "Back to network", "\u8fd4\u56de\u7f51\u7edc");
        put("remote.music.sources", "Remote music sources", "\u8fdc\u7a0b\u97f3\u4e50\u6765\u6e90");
        put("no.remote.sources", "No remote sources yet. Add a WebDAV folder or direct audio URL.", "\u6682\u65e0\u8fdc\u7a0b\u6765\u6e90\u3002\u53ef\u6dfb\u52a0 WebDAV \u6587\u4ef6\u5939\u6216\u97f3\u9891\u76f4\u94fe\u3002");
        put("test", "Test", "\u6d4b\u8bd5");
        put("sync", "Sync", "\u540c\u6b65");
        put("play", "Play", "\u64ad\u653e");
        put("back.to.webdav", "Back to WebDAV", "\u8fd4\u56de WebDAV");
        put("back.to.sources", "Back to sources", "\u8fd4\u56de\u6765\u6e90");
        put("sync.source", "Sync source", "\u540c\u6b65\u6765\u6e90");
        put("play.source", "Play source", "\u64ad\u653e\u6765\u6e90");
        put("no.streams", "No streams", "\u6682\u65e0\u4e32\u6d41");
        put("no.matching.streams", "No matching streams", "\u6ca1\u6709\u5339\u914d\u7684\u4e32\u6d41");
        put("no.webdav.tracks", "No WebDAV tracks", "\u6682\u65e0 WebDAV \u66f2\u76ee");
        put("no.matching.webdav.tracks", "No matching WebDAV tracks", "\u6ca1\u6709\u5339\u914d\u7684 WebDAV \u66f2\u76ee");
        put("source.not.found", "Source not found", "\u672a\u627e\u5230\u6765\u6e90");
        put("no.tracks.from.source", "No tracks from this source", "\u6b64\u6765\u6e90\u6682\u65e0\u66f2\u76ee");
        put("no.matching.source.tracks", "No matching source tracks", "\u6ca1\u6709\u5339\u914d\u7684\u6765\u6e90\u66f2\u76ee");
        put("synced", "synced", "\u5df2\u540c\u6b65");
        put("remote.source.default", "Remote source", "\u8fdc\u7a0b\u6765\u6e90");
        put("clearing.play.history", "Clearing play history", "\u6b63\u5728\u6e05\u7a7a\u64ad\u653e\u5386\u53f2");
        put("saving.webdav.source", "Saving WebDAV source", "\u6b63\u5728\u4fdd\u5b58 WebDAV \u6765\u6e90");
        put("syncing", "Syncing: ", "\u6b63\u5728\u540c\u6b65\uff1a");
        put("no.webdav.sources", "No WebDAV sources", "\u6682\u65e0 WebDAV \u6765\u6e90");
        put("syncing.webdav.sources", "Syncing WebDAV sources", "\u6b63\u5728\u540c\u6b65 WebDAV \u6765\u6e90");
        put("cancel", "Cancel", "\u53d6\u6d88");
        put("ok", "OK", "\u786e\u5b9a");
        put("input", "Input", "\u8f93\u5165");
        put("name", "Name", "\u540d\u79f0");
        put("username", "Username", "\u7528\u6237\u540d");
        put("password", "Password", "\u5bc6\u7801");
        put("create.playlist", "Create playlist", "\u65b0\u5efa\u64ad\u653e\u5217\u8868");
        put("rename.playlist", "Rename playlist", "\u91cd\u547d\u540d\u64ad\u653e\u5217\u8868");
        put("delete.playlist", "Delete playlist", "\u5220\u9664\u64ad\u653e\u5217\u8868");
        put("delete.playlist.message.prefix", "Delete playlist \"", "\u5220\u9664\u64ad\u653e\u5217\u8868\u201c");
        put("delete.message.suffix", "\"?", "\u201d\uff1f");
        put("choose.playlist", "Choose playlist", "\u9009\u62e9\u64ad\u653e\u5217\u8868");
        put("add.stream.url.title", "Add stream URL", "\u6dfb\u52a0\u4e32\u6d41 URL");
        put("import.m3u.title", "Import M3U playlist", "\u5bfc\u5165 M3U \u64ad\u653e\u5217\u8868");
        put("edit.stream", "Edit stream", "\u7f16\u8f91\u4e32\u6d41");
        put("add.webdav.source", "Add WebDAV source", "\u6dfb\u52a0 WebDAV \u6765\u6e90");
        put("edit.webdav.source", "Edit WebDAV source", "\u7f16\u8f91 WebDAV \u6765\u6e90");
        put("clear.play.history.title", "Clear play history", "\u6e05\u7a7a\u64ad\u653e\u5386\u53f2");
        put("clear.play.history.message", "Clear all recent and most-played records?", "\u6e05\u7a7a\u6700\u8fd1\u64ad\u653e\u548c\u64ad\u653e\u6700\u591a\u8bb0\u5f55\uff1f");
        put("clear.queue.title", "Clear queue", "\u6e05\u7a7a\u961f\u5217");
        put("clear.queue.message", "Clear the current queue?", "\u6e05\u7a7a\u5f53\u524d\u961f\u5217\uff1f");
        put("queue.empty", "Queue is empty", "\u961f\u5217\u6682\u65e0\u6b4c\u66f2");
        put("delete.all.streams.title", "Delete streams", "\u5220\u9664\u4e32\u6d41");
        put("delete.all.streams.message", "Delete all stream entries?", "\u5220\u9664\u5168\u90e8\u4e32\u6d41\u6761\u76ee\uff1f");
        put("delete.stream.title", "Delete stream", "\u5220\u9664\u4e32\u6d41");
        put("delete.stream.message.prefix", "Delete \"", "\u5220\u9664\u201c");
        put("deleted.stream", "Deleted stream", "\u5df2\u5220\u9664\u4e32\u6d41");
        put("delete.source.title", "Delete source", "\u5220\u9664\u6765\u6e90");
        put("delete.source.message.prefix", "Delete \"", "\u5220\u9664\u201c");
        put("added.to.playlist", "Added to playlist", "\u5df2\u6dfb\u52a0\u5230\u64ad\u653e\u5217\u8868");
        put("could.not.add.to.playlist", "Could not add to playlist", "\u65e0\u6cd5\u6dfb\u52a0\u5230\u64ad\u653e\u5217\u8868");
        put("playlist.created", "Playlist created", "\u5df2\u65b0\u5efa\u64ad\u653e\u5217\u8868");
        put("playlist.renamed", "Playlist renamed", "\u5df2\u91cd\u547d\u540d\u64ad\u653e\u5217\u8868");
        put("playlist.rename.failed", "Could not rename playlist", "\u65e0\u6cd5\u91cd\u547d\u540d\u64ad\u653e\u5217\u8868");
        put("deleted.playlist.prefix", "Deleted playlist: ", "\u5df2\u5220\u9664\u64ad\u653e\u5217\u8868\uff1a");
        put("could.not.delete.playlist", "Could not delete playlist", "\u65e0\u6cd5\u5220\u9664\u64ad\u653e\u5217\u8868");
        put("removed.from.playlist.prefix", "Removed from playlist: ", "\u5df2\u4ece\u64ad\u653e\u5217\u8868\u79fb\u9664\uff1a");
        put("moved.up.prefix", "Moved up: ", "\u5df2\u4e0a\u79fb\uff1a");
        put("moved.down.prefix", "Moved down: ", "\u5df2\u4e0b\u79fb\uff1a");
        put("move.failed", "Could not move track", "\u65e0\u6cd5\u79fb\u52a8\u66f2\u76ee");
        put("cleared.play.history.prefix", "Cleared play history: ", "\u5df2\u6e05\u7a7a\u64ad\u653e\u5386\u53f2\uff1a");
        put("playlist.exported", "Playlist exported", "\u64ad\u653e\u5217\u8868\u5df2\u5bfc\u51fa");
        put("playlist.export.failed", "Export failed", "\u5bfc\u51fa\u5931\u8d25");
        put("import.playlist.to.streaming", "Import to streaming", "\u5bfc\u5165\u5230\u6d41\u5a92\u4f53");
        put("import.favorites.to.streaming", "Import favorites to streaming", "\u5bfc\u5165\u6536\u85cf\u5230\u6d41\u5a92\u4f53");
        put("choose.streaming.provider", "Choose streaming provider", "\u9009\u62e9\u6d41\u5a92\u4f53\u6e90");
        put("streaming.import.matched.prefix", "Matched ", "\u5df2\u5339\u914d ");
        put("streaming.import.unresolved.suffix", " unresolved", " \u672a\u5339\u914d");
        put("streaming.import.failed", "Streaming import failed", "\u6d41\u5a92\u4f53\u5bfc\u5165\u5931\u8d25");
        put("streaming.login.opening", "Opening streaming login", "\u6b63\u5728\u6253\u5f00\u6d41\u5a92\u4f53\u767b\u5f55");
        put("streaming.login.unavailable", "Streaming login unavailable", "\u6d41\u5a92\u4f53\u767b\u5f55\u4e0d\u53ef\u7528");
        put("streaming.login.complete", "Streaming login complete", "\u6d41\u5a92\u4f53\u767b\u5f55\u5b8c\u6210");
        put("streaming.signed.out", "Signed out of streaming", "\u5df2\u9000\u51fa\u6d41\u5a92\u4f53");
        put("streaming.no.tracks.to.import", "No tracks to import", "\u6ca1\u6709\u53ef\u5bfc\u5165\u7684\u66f2\u76ee");
        put("streaming.no.providers", "No streaming providers", "\u6682\u65e0\u6d41\u5a92\u4f53\u6e90");
        put("streaming.my.playlist.prefix", "My ", "\u6211\u7684");
        put("streaming.my.playlist.suffix", " Playlist", "\u6b4c\u5355");
        put("streaming.playlist.created", "Created streaming playlist", "\u5df2\u521b\u5efa\u6d41\u5a92\u4f53\u6b4c\u5355");
        put("streaming.sync.started", "Syncing streaming playlists", "\u6b63\u5728\u540c\u6b65\u6d41\u5a92\u4f53\u6b4c\u5355");
        put("streaming.sync.complete", "Streaming playlist synced", "\u6d41\u5a92\u4f53\u6b4c\u5355\u5df2\u540c\u6b65");
        put("sync.streaming.playlist", "Sync from streaming", "\u4ece\u6d41\u5a92\u4f53\u540c\u6b65");
        put("streaming.not.linked", "Playlist not linked to streaming", "\u6b4c\u5355\u672a\u5173\u8054\u6d41\u5a92\u4f53");
        put("streaming.import.playlist.from", "Import playlist from streaming", "\u4ece\u6d41\u5a92\u4f53\u5bfc\u5165\u6b4c\u5355");
        put("streaming.paste.playlist.link", "Paste playlist link or ID", "\u7c98\u8d34\u6b4c\u5355\u94fe\u63a5\u6216 ID");
        put("streaming.account.playlists", "Account playlists", "\u8d26\u6237\u6b4c\u5355");
        put("streaming.no.account.playlists", "No account playlists (gateway required)", "\u6682\u65e0\u8d26\u6237\u6b4c\u5355\uff08\u9700\u7f51\u5173\u652f\u6301\uff09");
        put("streaming.load.account.playlists", "Load account playlists", "\u52a0\u8f7d\u8d26\u6237\u6b4c\u5355");
        put("streaming.import.this.playlist", "Import this playlist", "\u5bfc\u5165\u6b64\u6b4c\u5355");
        put("streaming.import.liked", "Import streaming favorites", "\u5bfc\u5165\u6d41\u5a92\u4f53\u6536\u85cf");
        put("streaming.liked.playlist.prefix", "", "\u6765\u81ea");
        put("streaming.liked.playlist.suffix", " Favorites", "\u7684\u6536\u85cf");
        put("streaming.liked.empty", "No favorites found on this account", "\u8be5\u8d26\u6237\u6682\u65e0\u6536\u85cf\u6b4c\u66f2");
        put("streaming.liked.imported.prefix", "Imported favorites: ", "\u5df2\u5bfc\u5165\u6536\u85cf\uff1a");
        put("streaming.recommend.daily", "Daily recommendations", "\u6bcf\u65e5\u63a8\u8350");
        put("streaming.recommend.heartbeat", "Heartbeat recommendations", "\u5fc3\u52a8\u63a8\u8350");
        put("streaming.recommend.daily.loading", "Loading daily recommendations", "\u6b63\u5728\u52a0\u8f7d\u6bcf\u65e5\u63a8\u8350");
        put("streaming.recommend.daily.empty", "No daily recommendations (login required?)", "\u6682\u65e0\u6bcf\u65e5\u63a8\u8350\uff08\u9700\u767b\u5f55\uff1f\uff09");
        put("streaming.recommend.daily.playing", "Playing daily recommendations", "\u6b63\u5728\u64ad\u653e\u6bcf\u65e5\u63a8\u8350");
        put("streaming.recommend.heartbeat.loading", "Loading heartbeat recommendations", "\u6b63\u5728\u52a0\u8f7d\u5fc3\u52a8\u63a8\u8350");
        put("streaming.recommend.heartbeat.empty", "No NetEase track in the current playback queue", "\u5f53\u524d\u64ad\u653e\u961f\u5217\u6ca1\u6709\u7f51\u6613\u4e91\u6b4c\u66f2");
        put("streaming.recommend.heartbeat.result.empty", "No heartbeat recommendations for this track", "\u5f53\u524d\u6b4c\u66f2\u6682\u65e0\u5fc3\u52a8\u63a8\u8350\u7ed3\u679c");
        put("streaming.recommend.heartbeat.playing", "Playing heartbeat recommendations", "\u6b63\u5728\u64ad\u653e\u5fc3\u52a8\u63a8\u8350");
        put("streaming.playlist.link.invalid", "Could not recognize playlist link", "\u65e0\u6cd5\u8bc6\u522b\u6b4c\u5355\u94fe\u63a5");
        put("streaming.playlist.load.success.title", "Load complete", "\u52a0\u8f7d\u6210\u529f");
        put("streaming.playlist.imported.prefix", "Imported playlist: ", "\u5df2\u5bfc\u5165\u6b4c\u5355\uff1a");
        put("streaming.playlist.empty", "Playlist is empty or unavailable", "\u6b4c\u5355\u4e3a\u7a7a\u6216\u4e0d\u53ef\u7528");
        put("streaming.resolving", "Resolving streaming track", "\u6b63\u5728\u89e3\u6790\u6d41\u5a92\u4f53\u6b4c\u66f2");
        put("streaming.resolve.failed", "Could not resolve streaming track (login required?)", "\u65e0\u6cd5\u89e3\u6790\u6d41\u5a92\u4f53\u6b4c\u66f2\uff08\u9700\u767b\u5f55\uff1f\uff09");
        put("adding.stream", "Adding stream", "\u6b63\u5728\u6dfb\u52a0\u4e32\u6d41");
        put("updating.stream", "Updating stream", "\u6b63\u5728\u66f4\u65b0\u4e32\u6d41");
        put("importing.m3u.playlist", "Importing M3U playlist", "\u6b63\u5728\u5bfc\u5165 M3U \u64ad\u653e\u5217\u8868");
        put("no.streams.to.play", "No streams to play", "\u6ca1\u6709\u53ef\u64ad\u653e\u7684\u4e32\u6d41");
        put("no.webdav.tracks.to.play", "No WebDAV tracks to play", "\u6ca1\u6709\u53ef\u64ad\u653e\u7684 WebDAV \u66f2\u76ee");
        put("no.source.tracks.to.play", "No tracks from this source to play", "\u6b64\u6765\u6e90\u6ca1\u6709\u53ef\u64ad\u653e\u7684\u66f2\u76ee");
        put("no.streams.to.delete", "No streams to delete", "\u6ca1\u6709\u53ef\u5220\u9664\u7684\u4e32\u6d41");
        put("deleting.streams", "Deleting streams", "\u6b63\u5728\u5220\u9664\u4e32\u6d41");
        put("deleting.stream", "Deleting stream", "\u6b63\u5728\u5220\u9664\u4e32\u6d41");
        put("deleting.source", "Deleting source", "\u6b63\u5728\u5220\u9664\u6765\u6e90");
        put("library.updated", "Library updated", "\u66f2\u5e93\u5df2\u66f4\u65b0");
        put("audio.specs.updated", "Audio specs parsed", "\u97f3\u9891\u89c4\u683c\u5df2\u89e3\u6790");
        put("could.not.add.stream.url", "Could not add stream URL", "\u65e0\u6cd5\u6dfb\u52a0\u4e32\u6d41 URL");
        put("could.not.update.stream.url", "Could not update stream URL", "\u65e0\u6cd5\u66f4\u65b0\u4e32\u6d41 URL");
        put("no.streams.imported", "No streams imported", "\u672a\u5bfc\u5165\u4e32\u6d41");
        put("local.m3u.import.failed", "Local M3U import failed", "\u672c\u5730 M3U \u5bfc\u5165\u5931\u8d25");
        put("playlist.import.failed", "Playlist import failed", "\u64ad\u653e\u5217\u8868\u5bfc\u5165\u5931\u8d25");
        put("no.playlist.entries.imported", "No playlist entries imported", "\u672a\u5bfc\u5165\u64ad\u653e\u5217\u8868\u6761\u76ee");
        put("imported.streams", "Imported streams", "\u5df2\u5bfc\u5165\u4e32\u6d41");
        put("imported.playlist", "Imported playlist", "\u5df2\u5bfc\u5165\u64ad\u653e\u5217\u8868");
        put("updated.webdav.source", "Updated WebDAV source", "\u5df2\u66f4\u65b0 WebDAV \u6765\u6e90");
        put("added.webdav.source", "Added WebDAV source", "\u5df2\u6dfb\u52a0 WebDAV \u6765\u6e90");
        put("could.not.save.webdav.source", "Could not save WebDAV source", "\u65e0\u6cd5\u4fdd\u5b58 WebDAV \u6765\u6e90");
        put("webdav.sync.finished", "WebDAV sync finished", "WebDAV \u540c\u6b65\u5b8c\u6210");
        put("webdav.sync.failed.prefix", "WebDAV sync failed: ", "WebDAV \u540c\u6b65\u5931\u8d25\uff1a");
        put("webdav.sync.prefix", "WebDAV sync: ", "WebDAV \u540c\u6b65\uff1a");
        put("added.count.prefix", "added ", "\u65b0\u589e ");
        put("removed.count.prefix", "removed ", "\u5220\u9664 ");
        put("kept.count.prefix", "kept ", "\u4fdd\u7559 ");
        put("tracks.count.prefix", "tracks ", "\u66f2\u76ee ");
        put("ok.count.prefix", "ok ", "\u6210\u529f ");
        put("failed.count.prefix", "failed ", "\u5931\u8d25 ");
        put("skipped.count.prefix", "skipped ", "\u8df3\u8fc7 ");
        put("parsed.count.prefix", "parsed ", "\u89e3\u6790 ");
        put("playlist.tracks.count.prefix", "playlist tracks ", "\u64ad\u653e\u5217\u8868\u66f2\u76ee ");
        put("streams.count.prefix", "streams ", "\u4e32\u6d41 ");
        put("none", "none", "\u65e0");
    }

    private AppLanguage() {
    }

    static String normalizeMode(String mode) {
        if (MODE_CHINESE.equals(mode)) {
            return MODE_CHINESE;
        }
        if (MODE_ENGLISH.equals(mode)) {
            return MODE_ENGLISH;
        }
        return MODE_SYSTEM;
    }

    static boolean isChinese(String languageMode) {
        String mode = normalizeMode(languageMode);
        if (MODE_CHINESE.equals(mode)) {
            return true;
        }
        if (MODE_ENGLISH.equals(mode)) {
            return false;
        }
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    static String text(String languageMode, String key) {
        Map<String, String> table = isChinese(languageMode) ? ZH : EN;
        String value = table.get(key);
        return value == null ? key : value;
    }

    static String labelFor(String languageMode) {
        String mode = normalizeMode(languageMode);
        if (MODE_CHINESE.equals(mode)) {
            return text(languageMode, "language.chinese");
        }
        if (MODE_ENGLISH.equals(mode)) {
            return text(languageMode, "language.english");
        }
        return text(languageMode, "language.system");
    }

    static String tabLabel(String languageMode, String tabKey) {
        if (MainRoutes.TAB_HOME.equals(tabKey)) {
            return text(languageMode, "tab.home");
        }
        if (MainRoutes.TAB_COLLECTIONS.equals(tabKey)) {
            return text(languageMode, "tab.collections");
        }
        if (MainRoutes.TAB_QUEUE.equals(tabKey)) {
            return text(languageMode, "tab.queue");
        }
        if (MainRoutes.TAB_NOW.equals(tabKey)) {
            return text(languageMode, "tab.now");
        }
        if (MainRoutes.TAB_NETWORK.equals(tabKey)) {
            return text(languageMode, "tab.network");
        }
        if (MainRoutes.TAB_SETTINGS.equals(tabKey)) {
            return text(languageMode, "tab.settings");
        }
        return text(languageMode, "tab.library");
    }

    static String themeLabel(String themeMode, String languageMode) {
        if (!isChinese(languageMode)) {
            return themeLabelEn(themeMode);
        }
        String presetLabel = EchoTheme.presetLabelFor(themeMode);
        if (presetLabel != null) {
            return presetLabel;
        }
        switch (EchoTheme.normalizeMode(themeMode)) {
            case EchoTheme.MODE_DARK:
                return "\u6df1\u8272";
            case EchoTheme.MODE_LIGHT:
                return "\u6d45\u8272";
            case EchoTheme.MODE_AMOLED:
                return "\u7eaf\u9ed1";
            case EchoTheme.MODE_CONTRAST:
                return "\u9ad8\u5bf9\u6bd4";
            case EchoTheme.MODE_GRAPHITE:
                return "\u77f3\u58a8";
            case EchoTheme.MODE_MIST:
                return "\u96fe\u767d";
            case EchoTheme.MODE_MIDNIGHT:
                return "\u5348\u591c";
            case EchoTheme.MODE_FOREST:
                return "\u68ee\u6797";
            case EchoTheme.MODE_OCEAN:
                return "\u6d77\u6d0b";
            case EchoTheme.MODE_DAYLIGHT:
                return "\u65e5\u5149";
            default:
                return "\u8ddf\u968f\u7cfb\u7edf";
        }
    }

    static String accentLabel(String accentMode, String languageMode) {
        if (!isChinese(languageMode)) {
            return accentLabelEn(accentMode);
        }
        switch (EchoTheme.normalizeAccent(accentMode)) {
            case EchoTheme.ACCENT_TEAL:
                return "\u9752\u7eff";
            case EchoTheme.ACCENT_ROSE:
                return "\u73ab\u7ea2";
            case EchoTheme.ACCENT_VIOLET:
                return "\u7d2b\u7f57\u5170";
            case EchoTheme.ACCENT_AMBER:
                return "\u7425\u73c0";
            case EchoTheme.ACCENT_EMERALD:
                return "\u7fe0\u7eff";
            case EchoTheme.ACCENT_CYAN:
                return "\u5929\u9752";
            case EchoTheme.ACCENT_LIME:
                return "\u9752\u67e0";
            case EchoTheme.ACCENT_RED:
                return "\u7ea2\u8272";
            case EchoTheme.ACCENT_INDIGO:
                return "\u975b\u84dd";
            case EchoTheme.ACCENT_PINE:
                return "\u677e\u7eff";
            case EchoTheme.ACCENT_PEACH:
                return "\u7c89\u6843";
            default:
                return "\u84dd\u8272";
        }
    }

    private static String themeLabelEn(String themeMode) {
        String presetLabel = EchoTheme.presetLabelFor(themeMode);
        if (presetLabel != null) {
            return presetLabel;
        }
        switch (EchoTheme.normalizeMode(themeMode)) {
            case EchoTheme.MODE_DARK:
                return "Dark";
            case EchoTheme.MODE_LIGHT:
                return "Light";
            case EchoTheme.MODE_AMOLED:
                return "AMOLED";
            case EchoTheme.MODE_CONTRAST:
                return "Contrast";
            case EchoTheme.MODE_GRAPHITE:
                return "Graphite";
            case EchoTheme.MODE_MIST:
                return "Mist";
            case EchoTheme.MODE_MIDNIGHT:
                return "Midnight";
            case EchoTheme.MODE_FOREST:
                return "Forest";
            case EchoTheme.MODE_OCEAN:
                return "Ocean";
            case EchoTheme.MODE_DAYLIGHT:
                return "Daylight";
            default:
                return "Follow system";
        }
    }

    private static String accentLabelEn(String accentMode) {
        switch (EchoTheme.normalizeAccent(accentMode)) {
            case EchoTheme.ACCENT_TEAL:
                return "Teal";
            case EchoTheme.ACCENT_ROSE:
                return "Rose";
            case EchoTheme.ACCENT_VIOLET:
                return "Violet";
            case EchoTheme.ACCENT_AMBER:
                return "Amber";
            case EchoTheme.ACCENT_EMERALD:
                return "Emerald";
            case EchoTheme.ACCENT_CYAN:
                return "Cyan";
            case EchoTheme.ACCENT_LIME:
                return "Lime";
            case EchoTheme.ACCENT_RED:
                return "Red";
            case EchoTheme.ACCENT_INDIGO:
                return "Indigo";
            case EchoTheme.ACCENT_PINE:
                return "Pine";
            case EchoTheme.ACCENT_PEACH:
                return "Peach";
            default:
                return "Blue";
        }
    }

    private static void put(String key, String english, String chinese) {
        EN.put(key, english);
        ZH.put(key, chinese);
    }
}
