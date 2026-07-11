package app.yukine;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import app.yukine.ui.EchoTheme;

public final class AppLanguage {
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
        put("tab.playing", "Playing", "\u64ad\u653e");
        put("tab.now", "Now", "\u5f53\u524d");
        put("tab.network", "Network", "\u7f51\u7edc");
        put("tab.downloads", "Downloads", "\u4e0b\u8f7d");
        put("tab.search", "Search", "\u641c\u7d22");
        put("tab.settings", "Settings", "\u8bbe\u7f6e");
        put("search.music", "Search music", "\u641c\u7d22\u97f3\u4e50");
        put("library.search", "Search library", "\u641c\u7d22\u66f2\u5e93");
        put("library.sort", "Sort", "\u6392\u5e8f");
        put("library.filter", "Filter", "\u7b5b\u9009");
        put("library.filter.all", "All", "\u5168\u90e8");
        put("library.filter.local", "Local", "\u672c\u5730");
        put("library.filter.network", "Network", "\u7f51\u7edc");
        put("library.select.all", "Select all", "\u5168\u9009");
        put("library.selected.suffix", " selected", " \u9879\u5df2\u9009\u62e9");
        put("library.sort.title.asc", "Title A-Z", "\u6807\u9898 A-Z");
        put("library.sort.title.desc", "Title Z-A", "\u6807\u9898 Z-A");
        put("library.sort.artist", "Artist", "\u6b4c\u624b");
        put("library.sort.album", "Album", "\u4e13\u8f91");
        put("library.sort.duration.asc", "Duration: shortest", "\u65f6\u957f\u5347\u5e8f");
        put("library.sort.duration.desc", "Duration: longest", "\u65f6\u957f\u964d\u5e8f");
        put("library.delete.choose.title", "Remove songs", "\u79fb\u9664\u6b4c\u66f2");
        put("library.delete.choose.message", "Choose how to handle %d selected songs. Hiding keeps the files; deleting files cannot be undone.", "\u8bf7\u9009\u62e9\u5982\u4f55\u5904\u7406\u5df2\u9009\u7684 %d \u9996\u6b4c\u66f2\u3002\u4ece\u66f2\u5e93\u9690\u85cf\u4f1a\u4fdd\u7559\u6587\u4ef6\uff1b\u5220\u9664\u6587\u4ef6\u65e0\u6cd5\u64a4\u9500\u3002");
        put("library.hide.action", "Hide from library", "\u4ece\u66f2\u5e93\u9690\u85cf");
        put("library.delete.file.action", "Delete files permanently", "\u6c38\u4e45\u5220\u9664\u6587\u4ef6");
        put("library.delete.records.title", "Delete library records", "\u5220\u9664\u66f2\u5e93\u8bb0\u5f55");
        put("library.delete.records.message", "Delete %d selected network records? Remote files will not be changed.", "\u5220\u9664\u5df2\u9009\u7684 %d \u6761\u7f51\u7edc\u8bb0\u5f55\uff1f\u8fdc\u7a0b\u6587\u4ef6\u4e0d\u4f1a\u88ab\u5220\u9664\u3002");
        put("library.remove.playlist.title", "Remove from playlist", "\u4ece\u6b4c\u5355\u79fb\u9664");
        put("library.remove.playlist.message", "Remove %d selected songs from this playlist?", "\u4ece\u5f53\u524d\u6b4c\u5355\u79fb\u9664\u5df2\u9009\u7684 %d \u9996\u6b4c\u66f2\uff1f");
        put("library.delete.result", "Removed %d, failed %f, skipped %s", "\u5df2\u79fb\u9664 %d \u9879\uff0c\u5931\u8d25 %f \u9879\uff0c\u8df3\u8fc7 %s \u9879");
        put("library.hidden.restore", "Restore hidden song", "\u6062\u590d\u5df2\u9690\u85cf\u6b4c\u66f2");
        put("library.hidden.restore.all", "Restore all hidden songs", "\u6062\u590d\u5168\u90e8\u5df2\u9690\u85cf\u6b4c\u66f2");
        put("search.no.results", "No matching songs found", "\u6ca1\u6709\u627e\u5230\u76f8\u5173\u6b4c\u66f2");
        put("app.name", "YUKINE", "YUKINE");
        put("onboarding.enter", "Enter YUKINE", "\u8fdb\u5165 YUKINE");
        put("onboarding.action.grant", "Grant", "\u53bb\u6388\u6743");
        put("onboarding.missing.separator", ", ", "\u3001");
        put("onboarding.missing.prefix", "You cannot enter yet. Finish: ", "\u8fd8\u4e0d\u80fd\u8fdb\u5165\u3002\u8bf7\u5148\u5b8c\u6210\uff1a");
        put("home.hero.subtitle.track.prefix", "Pick up ", "\u63a5\u4e0a ");
        put("home.hero.subtitle.track.middle", "'s \"", " \u7684\u300c");
        put("home.hero.subtitle.track.suffix", "\", or start from a cover recently added to the library.", "\u300d\uff0c\u6216\u8005\u4ece\u6700\u8fd1\u5165\u5e93\u91cc\u6311\u4e00\u5f20\u5c01\u9762\u5f00\u59cb\u3002");
        put("home.continue.playing", "Continue playing", "\u7ee7\u7eed\u64ad\u653e");

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
        put("now.playing", "Now playing", "\u6b63\u5728\u64ad\u653e");
        put("streaming.gateway", "Streaming gateway", "\u5728\u7ebf\u97f3\u4e50\u7f51\u5173");
        put("endpoint", "Endpoint", "\u7aef\u70b9");
        put("granted", "Granted", "\u5df2\u6388\u6743");
        put("missing", "Missing", "\u672a\u6388\u6743");
        put("connected", "Connected", "\u5df2\u8fde\u63a5");
        put("disconnected", "Disconnected", "\u672a\u8fde\u63a5");
        put("close", "Close", "\u5173\u95ed");
        put("loading", "Loading", "\u52a0\u8f7d\u4e2d");
        put("results", "Results", "\u7ed3\u679c");
        put("videos", "Videos", "\u89c6\u9891");
        put("appearance", "Appearance", "\u5916\u89c2");
        put("page.background", "Page background", "\u9875\u9762\u80cc\u666f");
        put("page.background.hint", "Use the same image everywhere or customize each main page.", "\u53ef\u5168\u90e8\u4f7f\u7528\u540c\u4e00\u5f20\u56fe\uff0c\u4e5f\u53ef\u4e3a\u5404\u4e3b\u9875\u5355\u72ec\u8bbe\u7f6e\u3002");
        put("page.background.description", "The shared background is used by default. A page-specific background overrides it for that page.", "\u5168\u5c40\u80cc\u666f\u4f5c\u4e3a\u9ed8\u8ba4\u80cc\u666f\uff0c\u5355\u72ec\u8bbe\u7f6e\u7684\u9875\u9762\u4f1a\u4f18\u5148\u4f7f\u7528\u81ea\u5df1\u7684\u80cc\u666f\u3002");
        put("page.background.all", "All pages", "\u5168\u90e8\u9875\u9762");
        put("page.background.all.description", "Apply one image to Home, Library, Playing, and Settings.", "\u5c06\u540c\u4e00\u5f20\u56fe\u5e94\u7528\u5230\u4e3b\u9875\u3001\u66f2\u5e93\u3001\u64ad\u653e\u548c\u8bbe\u7f6e\u3002");
        put("page.background.single.description", "Override only this page while keeping other pages unchanged.", "\u4ec5\u8986\u76d6\u8fd9\u4e00\u9875\uff0c\u5176\u4ed6\u9875\u9762\u4fdd\u6301\u4e0d\u53d8\u3002");
        put("page.background.custom.count", " custom pages", " \u4e2a\u9875\u9762\u5df2\u5355\u72ec\u8bbe\u7f6e");
        put("choose.page.background", "Choose background", "\u9009\u62e9\u80cc\u666f");
        put("clear.page.background", "Clear background", "\u6e05\u9664\u80cc\u666f");
        put("page.background.preview.title", "Adjust background", "\u8c03\u6574\u80cc\u666f");
        put("page.background.preview.hint", "View the full original image, then pinch to zoom and drag to choose the final display area", "\u5b8c\u6574\u663e\u793a\u539f\u56fe\uff0c\u53cc\u6307\u7f29\u653e\u5e76\u62d6\u52a8\u9009\u62e9\u6700\u7ec8\u663e\u793a\u533a\u57df");
        put("page.background.preview.apply", "Apply", "\u5e94\u7528");
        put("page.background.preview.reset", "Reset", "\u91cd\u7f6e");
        put("page.background.preview.cancel", "Cancel", "\u53d6\u6d88");
        put("page.background.preview.sample", "Sample card", "\u793a\u4f8b\u5361\u7247");
        put("page.background.applied", "Background updated: ", "\u80cc\u666f\u5df2\u66f4\u65b0\uff1a");
        put("page.background.cleared", "Background cleared: ", "\u80cc\u666f\u5df2\u6e05\u9664\uff1a");
        put("page.background.copy.failed", "Background copy failed", "\u80cc\u666f\u590d\u5236\u5931\u8d25");
        put("settings.group.appearance", "Appearance", "\u5916\u89c2");
        put("settings.group.playback", "Playback", "\u64ad\u653e");
        put("settings.group.library", "Library", "\u66f2\u5e93");
        put("settings.group.lyrics", "Lyrics", "\u6b4c\u8bcd");
        put("settings.group.sources", "Sources & network", "\u97f3\u6e90\u4e0e\u7f51\u7edc");
        put("settings.group.about", "About", "\u5173\u4e8e");
        put("settings.group.appearance.description", "Theme, accent, and language.", "\u4e3b\u9898\u3001\u5f3a\u8c03\u8272\u548c\u8bed\u8a00\u3002");
        put("settings.group.playback.description", "Speed, volume, sleep timer, and audio behavior.", "\u901f\u5ea6\u3001\u97f3\u91cf\u3001\u7761\u7720\u5b9a\u65f6\u548c\u64ad\u653e\u884c\u4e3a\u3002");
        put("settings.group.library.description", "Scan and import local music.", "\u626b\u63cf\u548c\u5bfc\u5165\u672c\u5730\u97f3\u4e50\u3002");
        put("settings.group.lyrics.description", "Online lyrics, reload, and timing offset.", "\u5728\u7ebf\u6b4c\u8bcd\u3001\u91cd\u8f7d\u548c\u65f6\u95f4\u504f\u79fb\u3002");
        put("settings.group.sources.description", "Streaming accounts, remote sources, quality, and gateway.", "\u6d41\u5a92\u4f53\u8d26\u53f7\u3001\u8fdc\u7a0b\u6765\u6e90\u3001\u97f3\u8d28\u548c\u7f51\u5173\u3002");
        put("settings.group.about.description", "App status and version.", "\u5e94\u7528\u72b6\u6001\u548c\u7248\u672c\u4fe1\u606f\u3002");
        put("settings.start", "Getting started", "\u5f00\u59cb\u8bbe\u7f6e");
        put("settings.start.hint", "Add your music first; you can change the rest later.", "\u5148\u6dfb\u52a0\u97f3\u4e50\uff0c\u5176\u4ed6\u9009\u9879\u53ef\u968f\u65f6\u518d\u8c03\u6574\u3002");
        put("settings.section.start", "Start here", "\u4ece\u8fd9\u91cc\u5f00\u59cb");
        put("settings.section.more", "More & maintenance", "\u66f4\u591a\u4e0e\u7ef4\u62a4");
        put("settings.choose.hint", "Tap to choose", "\u70b9\u51fb\u9009\u62e9");
        put("settings.grant.music.access", "Grant music access", "\u6388\u6743\u8bbf\u95ee\u97f3\u4e50");
        put("settings.grant.music.access.hint", "Allow Yukine to scan and play music stored on this device.", "\u5141\u8bb8 Yukine \u626b\u63cf\u5e76\u64ad\u653e\u6b64\u8bbe\u5907\u4e0a\u7684\u97f3\u4e50\u3002");
        put("advanced", "Advanced", "\u9ad8\u7ea7");
        put("version", "Version", "\u7248\u672c");
        put("backup.export", "Export backup", "\u5bfc\u51fa\u5907\u4efd");
        put("backup.import", "Import backup", "\u5bfc\u5165\u5907\u4efd");
        put("backup.import.description", "Replaces the current library, history, and settings.", "\u5c06\u8986\u76d6\u5f53\u524d\u66f2\u5e93\u3001\u64ad\u653e\u8bb0\u5f55\u548c\u8bbe\u7f6e\u3002");
        put("qq.group", "Yukine QQ group", "Yukine QQ \u7fa4");
        put("qq.group.hint", "Tap to view the group QR code", "\u70b9\u51fb\u67e5\u770b\u7fa4\u4e8c\u7ef4\u7801");
        put("qq.group.number", "Group number: 1013122077", "\u7fa4\u53f7\uff1a1013122077");
        put("qq.group.qr.description", "QQ group QR code for 1013122077", "QQ \u7fa4 1013122077 \u4e8c\u7ef4\u7801");
        put("backup.export.success", "Backup exported", "\u5907\u4efd\u5df2\u5bfc\u51fa");
        put("backup.export.failed", "Export failed", "\u5bfc\u51fa\u5931\u8d25");
        put("backup.import.success", "Backup imported, restart to apply", "\u5907\u4efd\u5df2\u5bfc\u5165\uff0c\u8bf7\u91cd\u542f\u5e94\u7528");
        put("backup.import.failed", "Import failed", "\u5bfc\u5165\u5931\u8d25");
        put("recently.added", "Recently added", "\u6700\u8fd1\u6dfb\u52a0");
        put("no.recently.added", "No recently added tracks", "\u6682\u65e0\u6700\u8fd1\u6dfb\u52a0");
        put("no.recently.added.description", "Newly imported or scanned tracks appear here.", "\u65b0\u5bfc\u5165\u6216\u626b\u63cf\u7684\u66f2\u76ee\u5c06\u51fa\u73b0\u5728\u6b64\u3002");
        put("play.recently.added", "Play recently added", "\u64ad\u653e\u6700\u8fd1\u6dfb\u52a0");
        put("long.unplayed", "Long unplayed", "\u5f88\u4e45\u6ca1\u542c");
        put("no.long.unplayed", "Nothing unplayed", "\u6ca1\u6709\u957f\u671f\u672a\u64ad\u653e\u7684\u66f2\u76ee");
        put("no.long.unplayed.description", "Tracks not played in the last 7 days appear here.", "\u8d85\u8fc7 7 \u5929\u672a\u64ad\u653e\u7684\u66f2\u76ee\u5c06\u51fa\u73b0\u5728\u6b64\u3002");
        put("play.long.unplayed", "Play long unplayed", "\u64ad\u653e\u5f88\u4e45\u6ca1\u542c");
        put("playback.speed", "Playback speed", "\u64ad\u653e\u901f\u5ea6");
        put("app.volume", "App volume", "\u5e94\u7528\u97f3\u91cf");
        put("audio.effects", "Audio effects", "\u97f3\u6548");
        put("audio.effects.description", "System equalizer, bass, virtualizer, and loudness enhancement. Availability depends on the device audio stack.", "\u7cfb\u7edf\u5747\u8861\u5668\u3001\u4f4e\u97f3\u3001\u73af\u7ed5\u548c\u54cd\u5ea6\u589e\u5f3a\u3002\u53ef\u7528\u6027\u53d6\u51b3\u4e8e\u8bbe\u5907\u97f3\u9891\u6808\u3002");
        put("enable.audio.effects", "Enable audio effects", "\u5f00\u542f\u97f3\u6548");
        put("disable.audio.effects", "Disable audio effects", "\u5173\u95ed\u97f3\u6548");
        put("audio.effects.applied", "Audio effects updated", "\u97f3\u6548\u5df2\u66f4\u65b0");
        put("status.bar.lyrics", "Status bar lyrics", "\u72b6\u6001\u680f\u6b4c\u8bcd");
        put("status.bar.lyrics.description", "Show the current lyric line in the playback notification so the status bar, lock screen, and supported floating-island panels can display it.", "\u628a\u5f53\u524d\u6b4c\u8bcd\u5199\u5230\u64ad\u653e\u901a\u77e5\u4e2d\uff0c\u72b6\u6001\u680f\u3001\u9501\u5c4f\u548c\u652f\u6301\u7684\u6d41\u4f53\u4e91\u9762\u677f\u53ef\u4ee5\u663e\u793a\u3002");
        put("enable.status.bar.lyrics", "Show lyrics in notification", "\u5f00\u542f\u901a\u77e5\u6b4c\u8bcd");
        put("disable.status.bar.lyrics", "Hide lyrics from notification", "\u5173\u95ed\u901a\u77e5\u6b4c\u8bcd");
        put("status.bar.lyrics.enabled", "Notification lyrics enabled", "\u5df2\u5f00\u542f\u901a\u77e5\u6b4c\u8bcd");
        put("status.bar.lyrics.disabled", "Notification lyrics disabled", "\u5df2\u5173\u95ed\u901a\u77e5\u6b4c\u8bcd");
        put("system.media.lyrics.title", "System media lyric-title mode", "\u7cfb\u7edf\u5a92\u4f53\u6b4c\u8bcd\u6807\u9898\u517c\u5bb9\u6a21\u5f0f");
        put("system.media.lyrics.title.description", "For car head units or media panels that only show a title. The current lyric temporarily replaces the system media title while the song title and artist remain in the subtitle and metadata.", "\u7528\u4e8e\u53ea\u663e\u793a\u6807\u9898\u7684\u8f66\u673a\u6216\u5a92\u4f53\u9762\u677f\u3002\u5f53\u524d\u6b4c\u8bcd\u4f1a\u6682\u65f6\u66ff\u6362\u7cfb\u7edf\u5a92\u4f53\u6807\u9898\uff0c\u6b4c\u540d\u548c\u6b4c\u624b\u4ecd\u4f1a\u4fdd\u7559\u5728\u526f\u6807\u9898\u548c\u5a92\u4f53\u5143\u6570\u636e\u4e2d\u3002");
        put("enable.system.media.lyrics.title", "Enable lyric-title mode", "\u5f00\u542f\u6b4c\u8bcd\u6807\u9898\u517c\u5bb9\u6a21\u5f0f");
        put("disable.system.media.lyrics.title", "Disable lyric-title mode", "\u5173\u95ed\u6b4c\u8bcd\u6807\u9898\u517c\u5bb9\u6a21\u5f0f");
        put("system.media.lyrics.title.enabled", "System media lyric-title mode enabled", "\u5df2\u5f00\u542f\u7cfb\u7edf\u5a92\u4f53\u6b4c\u8bcd\u6807\u9898\u517c\u5bb9\u6a21\u5f0f");
        put("system.media.lyrics.title.disabled", "System media lyric-title mode disabled", "\u5df2\u5173\u95ed\u7cfb\u7edf\u5a92\u4f53\u6b4c\u8bcd\u6807\u9898\u517c\u5bb9\u6a21\u5f0f");
        put("floating.lyrics", "Floating lyrics", "\u60ac\u6d6e\u7a97\u6b4c\u8bcd");
        put("floating.lyrics.description", "Show synced lyrics in a movable overlay above other apps. Overlay permission is required.", "\u5728\u5176\u4ed6\u5e94\u7528\u4e0a\u65b9\u663e\u793a\u53ef\u79fb\u52a8\u7684\u540c\u6b65\u6b4c\u8bcd\uff0c\u9700\u8981\u60ac\u6d6e\u7a97\u6743\u9650\u3002");
        put("enable.floating.lyrics", "Enable floating lyrics", "\u5f00\u542f\u60ac\u6d6e\u7a97\u6b4c\u8bcd");
        put("disable.floating.lyrics", "Disable floating lyrics", "\u5173\u95ed\u60ac\u6d6e\u7a97\u6b4c\u8bcd");
        put("floating.lyrics.enabled", "Floating lyrics enabled", "\u5df2\u5f00\u542f\u60ac\u6d6e\u7a97\u6b4c\u8bcd");
        put("floating.lyrics.disabled", "Floating lyrics disabled", "\u5df2\u5173\u95ed\u60ac\u6d6e\u7a97\u6b4c\u8bcd");
        put("floating.lyrics.permission.required", "Overlay permission is required for floating lyrics", "\u60ac\u6d6e\u7a97\u6b4c\u8bcd\u9700\u8981\u5148\u6388\u4e88\u60ac\u6d6e\u7a97\u6743\u9650");
        put("overlay.permission", "Overlay permission", "\u60ac\u6d6e\u7a97\u6743\u9650");
        put("grant.overlay.permission", "Grant overlay permission", "\u53bb\u6388\u4e88\u60ac\u6d6e\u7a97\u6743\u9650");
        put("now.playing.gestures", "Now playing gestures", "\u64ad\u653e\u9875\u624b\u52bf");
        put("now.playing.gestures.description", "On the Now Playing page: swipe left or right on the cover to switch tracks, swipe down to close the page.", "\u5728\u201c\u6b63\u5728\u64ad\u653e\u201d\u9875\uff1a\u5728\u5c01\u9762\u4e0a\u5de6\u53f3\u6ed1\u5207\u6362\u6b4c\u66f2\uff0c\u4e0b\u6ed1\u6536\u8d77\u9875\u9762\u3002");
        put("enable.now.playing.gestures", "Enable gestures", "\u5f00\u542f\u624b\u52bf");
        put("disable.now.playing.gestures", "Disable gestures", "\u5173\u95ed\u624b\u52bf");
        put("now.playing.gestures.enabled", "Now Playing gestures enabled", "\u5df2\u5f00\u542f\u64ad\u653e\u9875\u624b\u52bf");
        put("now.playing.gestures.disabled", "Now Playing gestures disabled", "\u5df2\u5173\u95ed\u64ad\u653e\u9875\u624b\u52bf");
        put("playback.restore", "Restore last queue", "\u542f\u52a8\u6062\u590d\u961f\u5217");
        put("playback.restore.description", "When Yukine starts again or the phone reboots, restore the last queue, current song, and saved position.", "\u91cd\u65b0\u6253\u5f00 Yukine \u6216\u624b\u673a\u91cd\u542f\u540e\uff0c\u6062\u590d\u4e0a\u6b21\u961f\u5217\u3001\u5f53\u524d\u6b4c\u66f2\u548c\u5df2\u4fdd\u5b58\u8fdb\u5ea6\u3002");
        put("enable.playback.restore", "Enable queue restore", "\u5f00\u542f\u961f\u5217\u6062\u590d");
        put("disable.playback.restore", "Disable queue restore", "\u5173\u95ed\u961f\u5217\u6062\u590d");
        put("playback.restore.enabled", "Queue restore enabled", "\u5df2\u5f00\u542f\u961f\u5217\u6062\u590d");
        put("playback.restore.disabled", "Queue restore disabled", "\u5df2\u5173\u95ed\u961f\u5217\u6062\u590d");
        put("replay.gain", "Volume leveling", "\u97f3\u91cf\u5747\u8861");
        put("audio.effects.hint", "Equalizer, bass, surround, and loudness", "\u5747\u8861\u5668\u3001\u4f4e\u97f3\u3001\u73af\u7ed5\u548c\u54cd\u5ea6\u589e\u5f3a");
        put("replay.gain.hint", "Auto-balance volume differences between songs", "\u81ea\u52a8\u5e73\u8861\u4e0d\u540c\u6b4c\u66f2\u4e4b\u95f4\u7684\u97f3\u91cf\u5dee\u5f02");
        put("now.playing.gestures.hint", "Swipe left or right to switch songs; swipe down to close the player", "\u5728\u64ad\u653e\u9875\u5de6\u53f3\u6ed1\u52a8\u5207\u6b4c\uff0c\u4e0b\u6ed1\u5173\u95ed\u64ad\u653e\u9875");
        put("playback.restore.hint", "Restore the last queue when the app reopens", "\u91cd\u65b0\u6253\u5f00\u5e94\u7528\u65f6\u6062\u590d\u4e0a\u6b21\u7684\u64ad\u653e\u961f\u5217");
        put("concurrent.playback.hint", "Play alongside videos or calls without pausing them", "\u4e0e\u89c6\u9891\u3001\u901a\u8bdd\u7b49\u540c\u65f6\u51fa\u58f0\uff0c\u4e92\u4e0d\u6253\u65ad");
        put("audio.exclusive.hint", "Request audio focus while playing so other media usually pauses or mutes", "\u64ad\u653e\u65f6\u8bf7\u6c42\u7cfb\u7edf\u97f3\u9891\u7126\u70b9\uff0c\u5176\u4ed6\u5a92\u4f53\u901a\u5e38\u4f1a\u6682\u505c\u6216\u9759\u97f3");
        put("remote.music.sources.hint", "Add WebDAV or other network music sources", "\u6dfb\u52a0 WebDAV \u7b49\u7f51\u7edc\u97f3\u4e50\u6765\u6e90");
        put("streaming.audio.quality.hint", "Audio quality for online streaming playback", "\u5728\u7ebf\u64ad\u653e\u7684\u97f3\u8d28\u9009\u62e9");
        put("streaming.gateway.hint", "For advanced users; normal users can skip this", "\u9ad8\u7ea7\u9009\u9879\uff0c\u666e\u901a\u7528\u6237\u65e0\u9700\u8bbe\u7f6e");
        put("replay.gain.description", "Use ReplayGain tags in local tracks to reduce loudness jumps between songs.", "\u8bfb\u53d6\u672c\u5730\u6b4c\u66f2\u7684 ReplayGain \u6807\u7b7e\uff0c\u51cf\u5c11\u4e0d\u540c\u6b4c\u66f2\u4e4b\u95f4\u7684\u97f3\u91cf\u5ffd\u5927\u5ffd\u5c0f\u3002");
        put("enable.replay.gain", "Enable volume leveling", "\u5f00\u542f\u97f3\u91cf\u5747\u8861");
        put("disable.replay.gain", "Disable volume leveling", "\u5173\u95ed\u97f3\u91cf\u5747\u8861");
        put("replay.gain.enabled", "Volume leveling enabled", "\u5df2\u5f00\u542f\u97f3\u91cf\u5747\u8861");
        put("replay.gain.disabled", "Volume leveling disabled", "\u5df2\u5173\u95ed\u97f3\u91cf\u5747\u8861");
        put("equalizer.preset", "EQ preset", "\u5747\u8861\u5668\u9884\u8bbe");
        put("bass.boost", "Bass", "\u4f4e\u97f3");
        put("virtualizer", "Virtualizer", "\u73af\u7ed5");
        put("loudness", "Loudness", "\u54cd\u5ea6");
        put("eq.custom", "Custom EQ", "\u81ea\u5b9a\u4e49\u5747\u8861");
        put("eq.normal", "Normal", "\u6807\u51c6");
        put("eq.classical", "Classical", "\u53e4\u5178");
        put("eq.dance", "Dance", "\u821e\u66f2");
        put("eq.preset", "Preset", "\u9884\u8bbe");
        put("streaming.audio.quality", "Streaming quality", "\u6d41\u5a92\u4f53\u97f3\u8d28");
        put("concurrent.playback", "Mix with other media", "\u4e0e\u5176\u4ed6\u5a92\u4f53\u540c\u65f6\u64ad\u653e");
        put("audio.exclusive", "Audio exclusive", "\u97f3\u9891\u72ec\u5360");
        put("sleep.timer", "Sleep timer", "\u7761\u7720\u5b9a\u65f6");
        put("lyrics", "Lyrics", "\u6b4c\u8bcd");
        put("duration", "Duration", "\u65f6\u957f");
        put("library", "Library", "\u66f2\u5e93");
        put("songs", "Songs", "\u6b4c\u66f2");
        put("albums", "Albums", "\u4e13\u8f91");
        put("artists", "Artists", "\u827a\u4eba");
        put("folders", "Folders", "\u6587\u4ef6\u5939");
        put("unknown", "Unknown", "\u672a\u77e5");
        put("unknown.album", "Unknown album", "\u672a\u77e5\u4e13\u8f91");
        put("unknown.artist", "Unknown artist", "\u672a\u77e5\u827a\u4eba");
        put("unknown.folder", "Unknown folder", "\u672a\u77e5\u6587\u4ef6\u5939");
        put("unknown.track", "Unknown track", "\u672a\u77e5\u6b4c\u66f2");
        put("back", "Back", "\u8fd4\u56de");
        put("play.group", "Play group", "\u64ad\u653e\u5206\u7ec4");
        put("play.artist", "Play artist", "\u64ad\u653e\u8be5\u6b4c\u624b");
        put("artist.info", "Artist info", "\u6b4c\u624b\u4ecb\u7ecd");
        put("all.albums", "All albums", "\u5168\u90e8\u4e13\u8f91");
        put("artist.albums", "All albums", "\u5168\u90e8\u4e13\u8f91");
        put("artist.albums.empty", "No album information in local library yet.", "\u672c\u5730\u66f2\u5e93\u6682\u65e0\u4e13\u8f91\u4fe1\u606f\u3002");
        put("data.source", "Source", "\u8d44\u6599\u6765\u6e90");
        put("online.info.not.found", "No online info found", "\u672a\u627e\u5230\u5728\u7ebf\u8d44\u6599");
        put("no.library.groups", "No %s available", "\u6682\u65e0%s");
        put("track.count.one", "1 track", "1 \u9996\u6b4c\u66f2");
        put("track.count.prefix", "", "");
        put("track.count.suffix", " tracks", " \u9996\u6b4c\u66f2");
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
        put("concurrent.playback.description", "Keep playing alongside other media apps instead of pausing them. Yukine will not request audio focus, so it won't pause other apps and won't be paused by them.", "\u4e0e\u5176\u4ed6\u5a92\u4f53\u5e94\u7528\u540c\u65f6\u51fa\u58f0\uff0c\u800c\u4e0d\u662f\u6682\u505c\u5b83\u4eec\u3002Yukine \u4e0d\u518d\u62a2\u5360\u97f3\u9891\u7126\u70b9\uff0c\u56e0\u6b64\u65e2\u4e0d\u4f1a\u6682\u505c\u5176\u4ed6\u5e94\u7528\uff0c\u4e5f\u4e0d\u4f1a\u88ab\u5b83\u4eec\u6682\u505c\u3002");
        put("enable.concurrent.playback", "Enable mixing", "\u5f00\u542f\u540c\u65f6\u64ad\u653e");
        put("disable.concurrent.playback", "Disable mixing", "\u5173\u95ed\u540c\u65f6\u64ad\u653e");
        put("audio.exclusive.description", "Yukine requests system media focus while playing, so compatible media apps pause or mute. Android cannot force every app to stop.", "Yukine \u64ad\u653e\u65f6\u4f1a\u8bf7\u6c42\u7cfb\u7edf\u5a92\u4f53\u7126\u70b9\uff0c\u517c\u5bb9\u7684\u5a92\u4f53\u5e94\u7528\u4f1a\u6682\u505c\u6216\u9759\u97f3\u3002Android \u65e0\u6cd5\u5f3a\u5236\u6240\u6709\u5e94\u7528\u505c\u6b62\u64ad\u653e\u3002");
        put("enable.audio.exclusive", "Enable audio exclusive", "\u5f00\u542f\u97f3\u9891\u72ec\u5360");
        put("disable.audio.exclusive", "Disable audio exclusive", "\u5173\u95ed\u97f3\u9891\u72ec\u5360");
        put("options", "Options", "\u9009\u9879");
        put("back", "Back", "\u8fd4\u56de");
        put("disable", "Disable", "\u5173\u95ed");
        put("selected", " (selected)", "\uff08\u5df2\u9009\u62e9\uff09");
        put("off", "Off", "\u5173\u95ed");
        put("min.left", " min left", " \u5206\u949f\u540e");
        put("min", " min", " \u5206\u949f");
        put("enabled", "Enabled", "\u5df2\u5f00\u542f");
        put("disabled", "Disabled", "\u5df2\u5173\u95ed");
        put("status", "Status", "\u72b6\u6001");
        put("loading.library", "Loading library", "\u6b63\u5728\u52a0\u8f7d\u66f2\u5e93");
        put("importing.audio.files", "Importing audio files", "\u6b63\u5728\u5bfc\u5165\u97f3\u9891\u6587\u4ef6");
        put("importing.audio.folder", "Importing audio folder", "\u6b63\u5728\u5bfc\u5165\u97f3\u9891\u6587\u4ef6\u5939");
        put("no.audio.files.selected", "No audio files selected", "\u672a\u9009\u62e9\u97f3\u9891\u6587\u4ef6");
        put("no.tracks.to.play", "No tracks to play", "\u6ca1\u6709\u53ef\u64ad\u653e\u7684\u66f2\u76ee");
        put("queue.not.connected", "Queue is not ready", "\u961f\u5217\u6682\u672a\u5c31\u7eea");
        put("playback.service.not.connected", "Playback is not ready", "\u64ad\u653e\u670d\u52a1\u6682\u672a\u5c31\u7eea");
        put("playback.error.title", "Playback failed", "\u64ad\u653e\u5931\u8d25");
        put("playback.error.unable.to.play", "Unable to play this track.", "\u65e0\u6cd5\u64ad\u653e\u8fd9\u9996\u6b4c\u66f2\u3002");
        put("playback.error.not.ready", "Playback is not ready.", "\u64ad\u653e\u5c1a\u672a\u5c31\u7eea\u3002");
        put("playback.error.streaming.not.resolved", "Streaming track is still resolving. Tap the track again to play.", "\u6d41\u5a92\u4f53\u6b4c\u66f2\u5c1a\u672a\u89e3\u6790\uff0c\u8bf7\u91cd\u65b0\u70b9\u51fb\u6b4c\u66f2\u64ad\u653e\u3002");
        put("playback.error.unable.to.open", "Unable to open this track.", "\u65e0\u6cd5\u6253\u5f00\u8fd9\u9996\u6b4c\u66f2\u3002");
        put("retry.playback", "Retry", "\u91cd\u8bd5\u64ad\u653e");
        put("grant.access", "Grant access", "\u6388\u6743\u8bbf\u95ee");
        put("audio.permission.required", "Audio permission required", "\u9700\u8981\u97f3\u9891\u6743\u9650");
        put("audio.permission.description", "Allow audio access so Yukine can scan and play your local library.", "\u6388\u6743\u97f3\u9891\u8bbf\u95ee\u540e\uff0cYukine \u624d\u80fd\u626b\u63cf\u5e76\u64ad\u653e\u672c\u5730\u66f2\u5e93\u3002");
        put("no.music", "No music found", "\u672a\u627e\u5230\u97f3\u4e50");
        put("no.music.description", "Scan the device again or import files/folders to start building your library.", "\u53ef\u91cd\u65b0\u626b\u63cf\u8bbe\u5907\uff0c\u6216\u5bfc\u5165\u6587\u4ef6\u548c\u6587\u4ef6\u5939\u6765\u5efa\u7acb\u66f2\u5e93\u3002");
        put("library.scan.found.prefix", "Found ", "\u5df2\u627e\u5230 ");
        put("library.scan.found.suffix", " tracks", " \u9996\u6b4c\u66f2");
        put("library.scan.checking", "Checking library changes…", "\u6b63\u5728\u68c0\u67e5\u66f2\u5e93\u53d8\u66f4\u2026");
        put("library.scan.scanning", "Scanning music files…", "\u6b63\u5728\u626b\u63cf\u97f3\u4e50\u6587\u4ef6\u2026");
        put("library.scan.replacing", "Updating your library…", "\u6b63\u5728\u66f4\u65b0\u66f2\u5e93\u2026");
        put("library.scan.reloading", "Loading updated library…", "\u6b63\u5728\u52a0\u8f7d\u66f4\u65b0\u540e\u7684\u66f2\u5e93\u2026");
        put("library.scan.slow", "Library scan is taking longer than usual. You can scan again to retry.", "\u66f2\u5e93\u626b\u63cf\u65f6\u95f4\u8f83\u957f\uff0c\u53ef\u518d\u6b21\u70b9\u51fb\u201c\u626b\u63cf\u66f2\u5e93\u201d\u91cd\u8bd5\u3002");
        put("library.scan.failed", "Library scan failed. Please retry.", "\u66f2\u5e93\u626b\u63cf\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5\u3002");
        put("library.scan.timeout", "Library scan timed out. Please retry.", "\u66f2\u5e93\u626b\u63cf\u8d85\u65f6\uff0c\u8bf7\u91cd\u8bd5\u3002");
        put("playback.service.unavailable", "Playback is not ready", "\u64ad\u653e\u670d\u52a1\u6682\u672a\u5c31\u7eea");
        put("playback.service.unavailable.description", "Return to the library and start a track to initialize playback controls.", "\u8fd4\u56de\u66f2\u5e93\u5e76\u64ad\u653e\u4e00\u9996\u6b4c\uff0c\u5373\u53ef\u542f\u7528\u64ad\u653e\u63a7\u5236\u3002");

        put("theme.options", "Follow system / Fresh light / Soft dark / AMOLED", "\u8ddf\u968f\u7cfb\u7edf / \u6e05\u65b0\u6d45\u8272 / \u67d4\u548c\u6df1\u8272 / AMOLED");
        put("advanced.themes", "Advanced themes", "\u9ad8\u7ea7\u4e3b\u9898");
        put("advanced.themes.description", "Extra palettes for advanced customization.", "\u66f4\u591a\u9ad8\u7ea7\u81ea\u5b9a\u4e49\u914d\u8272\u3002");
        put("accent.options", "Blue / Teal / Rose / Violet / Amber / Emerald", "\u84dd\u8272 / \u9752\u7eff / \u73ab\u7ea2 / \u7d2b\u7f57\u5170 / \u7425\u73c0 / \u7fe0\u7eff");
        put("language.options", "System / Chinese / English", "\u8ddf\u968f\u7cfb\u7edf / \u4e2d\u6587 / English");
        put("streaming.gateway.description", "Android calls this gateway for provider search, auth, and playback URL resolution. Leave empty to use the in-app local login fallback.", "Android \u901a\u8fc7\u6b64\u7f51\u5173\u8c03\u7528\u6d41\u5a92\u4f53\u641c\u7d22\u3001\u767b\u5f55\u548c\u64ad\u653e URL \u89e3\u6790\u3002\u7559\u7a7a\u53ef\u4f7f\u7528\u672c\u5730\u767b\u5f55\u56de\u9000\u3002");
        put("share.style", "Share style", "\u5206\u4eab\u6837\u5f0f");
        put("share.style.hint", "Choose platform link card, plain text, or image card.", "\u9009\u62e9\u5e73\u53f0\u94fe\u63a5\u5361\u7247\u3001\u7eaf\u6587\u672c\u6216\u56fe\u7247\u5361\u7247\u3002");
        put("share.style.description", "Platform link card shares the original music URL so QQ/WeChat can recognize it as a playable music card when supported.", "\u5e73\u53f0\u94fe\u63a5\u5361\u7247\u4f1a\u5206\u4eab\u539f\u59cb\u97f3\u4e50\u94fe\u63a5\uff0cQQ/\u5fae\u4fe1\u652f\u6301\u65f6\u4f1a\u8bc6\u522b\u4e3a\u53ef\u64ad\u653e\u97f3\u4e50\u5361\u7247\u3002");
        put("share.style.platform.card", "Platform link card", "\u5e73\u53f0\u94fe\u63a5\u5361\u7247");
        put("share.style.text", "Plain text", "\u7eaf\u6587\u672c");
        put("share.style.card", "Image card", "\u56fe\u7247\u5361\u7247");
        put("share.style.applied", "Share style: ", "\u5206\u4eab\u6837\u5f0f\uff1a");
        put("streaming.gateway.emulator", "Android emulator host (10.0.2.2:43990)", "\u5b89\u5353\u6a21\u62df\u5668\u4e3b\u673a\uff0810.0.2.2:43990\uff09");
        put("streaming.gateway.localhost", "Local device host (127.0.0.1:43990)", "\u672c\u673a\u8bbe\u5907\u4e3b\u673a\uff08127.0.0.1:43990\uff09");
        put("quality.auto", "Auto", "\u81ea\u52a8");
        put("quality.standard", "Standard", "\u6807\u51c6");
        put("quality.high", "High", "\u9ad8\u97f3\u8d28");
        put("quality.lossless", "Lossless", "\u65e0\u635f");
        put("quality.hires", "Hi-Res", "Hi-Res");
        put("quality.platform.mapping", "Platform mapping", "\u5e73\u53f0\u6620\u5c04");
        put("quality.platform.mapping.summary", "Mapped to the closest available format on each source. Availability depends on source, account, membership, and region.", "\u4f1a\u6620\u5c04\u5230\u5404\u97f3\u6e90\u6700\u63a5\u8fd1\u7684\u771f\u5b9e\u683c\u5f0f\uff0c\u53ef\u7528\u6027\u53d7\u97f3\u6e90\u3001\u8d26\u53f7\u3001\u4f1a\u5458\u548c\u5730\u533a\u5f71\u54cd\u3002");

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
        put("audio.exclusive.enabled", "Audio exclusive enabled", "\u5df2\u5f00\u542f\u97f3\u9891\u72ec\u5360");
        put("audio.exclusive.disabled", "Audio exclusive disabled; mixing is allowed", "\u5df2\u5173\u95ed\u97f3\u9891\u72ec\u5360\uff0c\u53ef\u4e0e\u5176\u4ed6\u5a92\u4f53\u540c\u65f6\u64ad\u653e");
        put("lyrics.offset.applied", "Lyrics offset: ", "\u6b4c\u8bcd\u504f\u79fb\uff1a");
        put("streaming.gateway.applied", "Streaming gateway: ", "\u5728\u7ebf\u97f3\u4e50\u7f51\u5173\uff1a");
        put("no.track.selected", "No track selected", "\u672a\u9009\u62e9\u6b4c\u66f2");
        put("reloading.lyrics", "Reloading lyrics", "\u6b63\u5728\u91cd\u65b0\u52a0\u8f7d\u6b4c\u8bcd");
        put("show.lyrics", "Show lyrics", "\u663e\u793a\u6b4c\u8bcd");
        put("show.artwork", "Show artwork", "\u663e\u793a\u5c01\u9762");
        put("no.lyrics.found", "No lyrics found", "\u672a\u627e\u5230\u6b4c\u8bcd");
        put("lyrics.not.loaded", "Lyrics not loaded", "\u6b4c\u8bcd\u5c1a\u672a\u52a0\u8f7d");
        put("loading.lyrics", "Loading lyrics from multiple sources", "\u6b63\u5728\u4ece\u591a\u4e2a\u6765\u6e90\u52a0\u8f7d\u6b4c\u8bcd");
        put("loading.local.lyrics", "Loading local lyrics", "\u6b63\u5728\u52a0\u8f7d\u672c\u5730\u6b4c\u8bcd");
        put("no.local.lyrics.found", "No local lyrics found", "\u672a\u627e\u5230\u672c\u5730\u6b4c\u8bcd");
        put("loaded.lyrics.prefix", "Loaded lyrics: ", "\u5df2\u52a0\u8f7d\u6b4c\u8bcd\uff1a");
        put("loaded.lyrics.suffix", " lines", " \u884c");
        put("repeat.one", "Repeat one", "\u5355\u66f2\u5faa\u73af");
        put("repeat.off", "Repeat off", "\u5173\u95ed\u5faa\u73af");
        put("repeat.all", "Repeat all", "\u5217\u8868\u5faa\u73af");
        put("favorite", "Favorite", "\u6536\u85cf");
        put("favorited", "Favorited", "\u5df2\u6536\u85cf");
        put("shuffle", "Shuffle", "\u968f\u673a");
        put("play.all", "Play all", "\u64ad\u653e\u5168\u90e8");
        put("in.order", "In order", "\u987a\u5e8f");

        put("favorites", "Favorites", "\u6536\u85cf");
        put("favorite.playlist", "Favorite playlist", "\u6536\u85cf\u6b4c\u5355");
        put("library.favorites.playlist.title", "Favorites playlist", "\u6536\u85cf\u6b4c\u5355");
        put("library.favorites.playlist.description", "Open collected tracks", "\u67e5\u770b\u5df2\u6536\u85cf\u7684\u6b4c\u66f2");
        put("play.history.playlist", "Play history", "\u64ad\u653e\u5386\u53f2");
        put("recent", "Recent", "\u6700\u8fd1");
        put("playlists", "Playlists", "\u64ad\u653e\u5217\u8868");
        put("most.played", "Most played", "\u64ad\u653e\u6700\u591a");
        put("new.playlist", "New playlist", "\u65b0\u5efa\u64ad\u653e\u5217\u8868");
        put("import.playlist.m3u", "Import playlist M3U/M3U8", "\u5bfc\u5165\u64ad\u653e\u5217\u8868 M3U/M3U8");
        put("clear.play.history", "Clear play history", "\u6e05\u7a7a\u64ad\u653e\u5386\u53f2");
        put("no.favorites", "No favorites", "\u6682\u65e0\u6536\u85cf");
        put("no.favorites.description", "Tap the heart on tracks you like; they will gather here.", "\u70b9\u51fb\u6b4c\u66f2\u4e0a\u7684\u7231\u5fc3\uff0c\u559c\u6b22\u7684\u97f3\u4e50\u4f1a\u6536\u5728\u8fd9\u91cc\u3002");
        put("no.recent.tracks", "No recent tracks", "\u6682\u65e0\u6700\u8fd1\u64ad\u653e");
        put("no.recent.tracks.description", "Recent plays will appear after you listen for a while.", "\u5f00\u59cb\u542c\u6b4c\u540e\uff0c\u6700\u8fd1\u64ad\u653e\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc\u3002");
        put("no.play.history", "No play history", "\u6682\u65e0\u64ad\u653e\u5386\u53f2");
        put("no.play.history.description", "Your most-played tracks will build up naturally as you listen.", "\u64ad\u653e\u6b21\u6570\u591a\u7684\u6b4c\u4f1a\u968f\u7740\u542c\u6b4c\u81ea\u7136\u7d2f\u79ef\u3002");
        put("play.favorites", "Play favorites", "\u64ad\u653e\u6536\u85cf");
        put("play.recent", "Play recent", "\u64ad\u653e\u6700\u8fd1");
        put("play.most.played", "Play most played", "\u64ad\u653e\u6700\u591a");
        put("play.playlist", "Play playlist", "\u64ad\u653e\u6b4c\u5355");
        put("download", "Download", "\u4e0b\u8f7d");
        put("download.playlist", "Download playlist", "\u4e0b\u8f7d\u6b4c\u5355");
        put("download.current.list", "Download current list", "\u4e0b\u8f7d\u5f53\u524d\u5217\u8868");
        put("download.manager", "Download manager", "\u4e0b\u8f7d\u7ba1\u7406");
        put("download.manager.hint", "View pending, progress, and downloaded songs", "\u67e5\u770b\u5f85\u4e0b\u8f7d\u3001\u8fdb\u5ea6\u548c\u5df2\u4e0b\u8f7d\u6b4c\u66f2");
        put("download.directory.picker.unavailable", "Directory picker is unavailable", "\u76ee\u5f55\u9009\u62e9\u6682\u4e0d\u53ef\u7528");
        put("back.to.playlists", "Back to playlists", "\u8fd4\u56de\u6b4c\u5355");
        put("export.playlist", "Export playlist", "\u5bfc\u51fa\u64ad\u653e\u5217\u8868");
        put("no.playlists", "No playlists", "\u6682\u65e0\u64ad\u653e\u5217\u8868");
        put("no.playlists.description", "Create a playlist or import M3U/M3U8 to organize music your way.", "\u65b0\u5efa\u64ad\u653e\u5217\u8868\uff0c\u6216\u5bfc\u5165 M3U/M3U8 \u6765\u6574\u7406\u4f60\u7684\u97f3\u4e50\u3002");
        put("no.tracks.in.playlist", "No tracks in this playlist", "\u6b64\u64ad\u653e\u5217\u8868\u6682\u65e0\u66f2\u76ee");
        put("no.tracks.in.playlist.description", "Add tracks from the library or sync this playlist from streaming.", "\u53ef\u4ece\u66f2\u5e93\u6dfb\u52a0\u6b4c\u66f2\uff0c\u6216\u4ece\u6d41\u5a92\u4f53\u540c\u6b65\u6b64\u5217\u8868\u3002");
        put("playlist", "Playlist", "\u64ad\u653e\u5217\u8868");
        put("played.at", "Played at ", "\u64ad\u653e\u4e8e ");
        put("played.once", "Played 1 time", "\u64ad\u653e 1 \u6b21");
        put("played.times.prefix", "Played ", "\u64ad\u653e ");
        put("played.times.suffix", " times", " \u6b21");
        put("remove.favorite", "Remove favorite", "\u53d6\u6d88\u6536\u85cf");
        put("add.to.playlist", "Add to playlist", "\u6dfb\u52a0\u5230\u64ad\u653e\u5217\u8868");
        put("rename", "Rename", "\u91cd\u547d\u540d");
        put("delete", "Delete", "\u5220\u9664");
        put("delete.group.message.prefix", "Remove \"", "\u4ece\u66f2\u5e93\u79fb\u9664\u201c");
        put("delete.group.message.middle", "\" and its ", "\u201d\u4e2d\u7684 ");
        put("delete.group.message.suffix", " tracks from the library?", " \u9996\u6b4c\uff1f");
        put("deleted.group.prefix", "Removed tracks: ", "\u5df2\u79fb\u9664\u6b4c\u66f2\uff1a");
        put("edit", "Edit", "\u7f16\u8f91");
        put("remove", "Remove", "\u79fb\u9664");
        put("up", "Up", "\u4e0a\u79fb");
        put("down", "Down", "\u4e0b\u79fb");

        put("remote.sources", "Remote sources", "\u8fdc\u7a0b\u6765\u6e90");
        put("streams", "Streams", "\u5728\u7ebf\u97f3\u4e50");
        put("webdav.sources", "WebDAV sources", "WebDAV \u6765\u6e90");
        put("streaming", "Streaming", "\u5728\u7ebf\u97f3\u4e50");
        put("webdav", "WebDAV", "WebDAV");
        put("source", "Source", "\u6765\u6e90");
        put("direct.url.m3u", "Direct URL / M3U", "\u76f4\u94fe / M3U");
        put("sources", "Sources", "\u6765\u6e90");
        put("tracks", "Tracks", "\u66f2\u76ee");
        put("sync.mode", "Sync mode", "\u540c\u6b65\u6a21\u5f0f");
        put("sync.mode.library", "Library", "\u66f2\u5e93");
        put("add.stream.url", "Add stream URL", "\u6dfb\u52a0\u5728\u7ebf\u97f3\u4e50 URL");
        put("import.m3u.url", "Import M3U URL", "\u5bfc\u5165 M3U URL");
        put("import.m3u.file", "Import M3U file", "\u5bfc\u5165 M3U \u6587\u4ef6");
        put("play.streams", "Play streams", "\u64ad\u653e\u5728\u7ebf\u97f3\u4e50");
        put("browse.streams", "Browse streams", "\u6d4f\u89c8\u5728\u7ebf\u97f3\u4e50");
        put("delete.streams", "Delete streams", "\u5220\u9664\u5728\u7ebf\u97f3\u4e50");
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
        put("more", "More", "\u66f4\u591a");
        put("pause", "Pause", "\u6682\u505c");
        put("previous", "Previous", "\u4e0a\u4e00\u9996");
        put("next", "Next", "\u4e0b\u4e00\u9996");
        put("playback.progress", "Playback progress", "\u64ad\u653e\u8fdb\u5ea6");
        put("expand.playback.waveform", "Expand playback waveform", "\u5c55\u5f00\u64ad\u653e\u6ce2\u5f62");
        put("back.to.webdav", "Back to WebDAV", "\u8fd4\u56de WebDAV");
        put("back.to.sources", "Back to sources", "\u8fd4\u56de\u6765\u6e90");
        put("sync.source", "Sync source", "\u540c\u6b65\u6765\u6e90");
        put("play.source", "Play source", "\u64ad\u653e\u6765\u6e90");
        put("no.streams", "No streams", "\u6682\u65e0\u5728\u7ebf\u97f3\u4e50");
        put("no.matching.streams", "No matching streams", "\u6ca1\u6709\u5339\u914d\u7684\u5728\u7ebf\u97f3\u4e50");
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
        put("add.stream.url.title", "Add stream URL", "\u6dfb\u52a0\u5728\u7ebf\u97f3\u4e50 URL");
        put("import.m3u.title", "Import M3U playlist", "\u5bfc\u5165 M3U \u64ad\u653e\u5217\u8868");
        put("edit.stream", "Edit stream", "\u7f16\u8f91\u5728\u7ebf\u97f3\u4e50");
        put("add.webdav.source", "Add WebDAV source", "\u6dfb\u52a0 WebDAV \u6765\u6e90");
        put("edit.webdav.source", "Edit WebDAV source", "\u7f16\u8f91 WebDAV \u6765\u6e90");
        put("clear.play.history.title", "Clear play history", "\u6e05\u7a7a\u64ad\u653e\u5386\u53f2");
        put("clear.play.history.message", "Clear all recent and most-played records?", "\u6e05\u7a7a\u6700\u8fd1\u64ad\u653e\u548c\u64ad\u653e\u6700\u591a\u8bb0\u5f55\uff1f");
        put("clear.queue.title", "Clear queue", "\u6e05\u7a7a\u961f\u5217");
        put("clear.queue.message", "Clear the current queue?", "\u6e05\u7a7a\u5f53\u524d\u961f\u5217\uff1f");
        put("queue.empty", "Queue is empty", "\u961f\u5217\u6682\u65e0\u6b4c\u66f2");
        put("queue.empty.description", "Play a track or add music to build the current queue.", "\u64ad\u653e\u4e00\u9996\u6b4c\uff0c\u6216\u6dfb\u52a0\u6b4c\u66f2\u6765\u5efa\u7acb\u5f53\u524d\u961f\u5217\u3002");
        put("queue.drag.reorder", "Drag to reorder", "\u62d6\u52a8\u91cd\u65b0\u6392\u5e8f");
        put("delete.all.streams.title", "Delete streams", "\u5220\u9664\u5728\u7ebf\u97f3\u4e50");
        put("delete.all.streams.message", "Delete all stream entries?", "\u5220\u9664\u5168\u90e8\u5728\u7ebf\u97f3\u4e50\u6761\u76ee\uff1f");
        put("delete.stream.title", "Delete stream", "\u5220\u9664\u5728\u7ebf\u97f3\u4e50");
        put("delete.stream.message.prefix", "Delete \"", "\u5220\u9664\u201c");
        put("deleted.stream", "Deleted stream", "\u5df2\u5220\u9664\u5728\u7ebf\u97f3\u4e50");
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
        put("streaming.title", "Streaming", "\u6d41\u5a92\u4f53");
        put("streaming.source.default", "Source", "\u97f3\u6e90");
        put("streaming.search.prefix", "Search \"", "\u641c\u7d22\u201c");
        put("streaming.search.suffix", "\"", "\u201d");
        put("streaming.search.unavailable.suffix", " is not searchable", "\u6682\u4e0d\u53ef\u641c\u7d22");
        put("streaming.search.unavailable", " is not searchable", " \u6682\u4e0d\u53ef\u641c\u7d22");
        put("streaming.search.types.unavailable", " has no searchable content type", " \u6682\u65e0\u53ef\u641c\u7d22\u7c7b\u578b");
        put("streaming.auth.unsupported", " does not support sign-in", " \u6682\u4e0d\u652f\u6301\u767b\u5f55");
        put("streaming.playback.unsupported", " is not playable", " \u6682\u4e0d\u53ef\u64ad\u653e");
        put("streaming.track.unavailable", "Streaming song is unavailable", "\u6d41\u5a92\u4f53\u6b4c\u66f2\u6682\u4e0d\u53ef\u7528");
        put("streaming.usage.notice.title", "Streaming and account notice", "\u6d41\u5a92\u4f53\u4e0e\u8d26\u53f7\u8bf4\u660e");
        put("streaming.usage.notice.body", "Yukine is intended for personal learning and local music management. Streaming search, playback, downloads, lyrics, and artwork may be subject to copyright and platform terms; do not use it as a way to obtain paid music for free. Account cookies are stored only on this device and are not uploaded to third-party servers. Third-party sources may fail because of region, membership, risk control, or interface changes. This build is a beta test package; please use fixed release channels and report issues with source, song, action, and logs.", "\u672c\u5e94\u7528\u4ec5\u7528\u4e8e\u4e2a\u4eba\u5b66\u4e60\u4e0e\u672c\u5730\u97f3\u4e50\u7ba1\u7406\u3002\u6d41\u5a92\u4f53\u641c\u7d22\u3001\u64ad\u653e\u3001\u4e0b\u8f7d\u3001\u6b4c\u8bcd\u548c\u5c01\u9762\u53ef\u80fd\u53d7\u7248\u6743\u4e0e\u5e73\u53f0\u534f\u8bae\u7ea6\u675f\uff0c\u4e0d\u5e94\u7528\u4e8e\u514d\u8d39\u83b7\u53d6\u4ed8\u8d39\u97f3\u4e50\u3002\u8d26\u53f7 Cookie \u53ea\u4fdd\u5b58\u5728\u672c\u673a\uff0c\u4e0d\u4e0a\u4f20\u5230\u7b2c\u4e09\u65b9\u670d\u52a1\u5668\u3002\u7b2c\u4e09\u65b9\u97f3\u6e90\u53ef\u80fd\u56e0\u5730\u533a\u3001\u4f1a\u5458\u3001\u98ce\u63a7\u6216\u63a5\u53e3\u53d8\u66f4\u800c\u5931\u6548\u3002\u5f53\u524d\u4e3a Beta \u6d4b\u8bd5\u5305\uff0c\u8bf7\u4ec5\u4ece\u56fa\u5b9a\u53d1\u5e03\u6e20\u9053\u83b7\u53d6\uff0c\u53cd\u9988\u65f6\u5efa\u8bae\u9644\u4e0a\u97f3\u6e90\u3001\u6b4c\u66f2\u3001\u64cd\u4f5c\u548c\u65e5\u5fd7\u3002");
        put("streaming.account.connect.backup", "Backup account connection", "\u5907\u7528\u8d26\u53f7\u8fde\u63a5");
        put("streaming.account.actions", "Account music", "\u8d26\u53f7\u97f3\u4e50");
        put("streaming.discover.music", "Discover", "\u53d1\u73b0\u97f3\u4e50");
        put("streaming.advanced.tools", "Advanced tools", "\u9ad8\u7ea7\u5de5\u5177");
        put("streaming.account.playlists.loading", "Loading account playlists", "\u6b63\u5728\u52a0\u8f7d\u8d26\u6237\u6b4c\u5355");
        put("streaming.open.login.prefix", "Open ", "\u6253\u5f00 ");
        put("streaming.open.login.suffix", " login", " \u767b\u5f55");
        put("streaming.login.primary", "Sign in to music account", "\u767b\u5f55\u6d41\u5a92\u4f53\u8d26\u53f7");
        put("streaming.matching.local.tracks", "Matching local songs to streaming", "\u6b63\u5728\u5339\u914d\u672c\u5730\u6b4c\u66f2\u5230\u6d41\u5a92\u4f53");
        put("streaming.playlist.import.title.prefix", "Playlist import: ", "\u6b4c\u5355\u5bfc\u5165\uff1a");
        put("streaming.matched.tracks", "Matched streaming songs", "\u5df2\u5339\u914d\u7684\u6d41\u5a92\u4f53\u6b4c\u66f2");
        put("streaming.no.results", "No streaming results found", "\u6ca1\u6709\u627e\u5230\u6d41\u5a92\u4f53\u7ed3\u679c");
        put("streaming.load.more", "Load more", "\u52a0\u8f7d\u66f4\u591a");
        put("streaming.play.resolved.track", "Play resolved song", "\u64ad\u653e\u5df2\u89e3\u6790\u6b4c\u66f2");
        put("streaming.status.signed.in", "Signed in", "\u5df2\u767b\u5f55");
        put("streaming.status.session.verified", "Verified", "\u5df2\u9a8c\u8bc1");
        put("streaming.status.session.pending.verification", "Session pending verification", "\u767b\u5f55\u5f85\u9a8c\u8bc1");
        put("streaming.status.session.invalid", "Session expired; sign in again", "\u767b\u5f55\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55");
        put("streaming.status.online.authenticated", "Online, authenticated", "\u5728\u7ebf\uff0c\u5df2\u8ba4\u8bc1");
        put("streaming.status.online", "Online", "\u5728\u7ebf");
        put("streaming.status.unavailable", "Unavailable", "\u4e0d\u53ef\u7528");
        put("streaming.status.ready", "Ready", "\u5c31\u7eea");
        put("streaming.status.needs.account", "Needs sign-in", "\u9700\u8981\u767b\u5f55");
        put("streaming.status.disabled", "Disabled", "\u5df2\u505c\u7528");
        put("streaming.status.error", "Error", "\u5f02\u5e38");
        put("streaming.track.count.suffix", " tracks", " \u9996");
        put("streaming.import.matched.prefix", "Matched ", "\u5df2\u5339\u914d ");
        put("streaming.import.unresolved.suffix", " unresolved", " \u672a\u5339\u914d");
        put("streaming.import.failed", "Streaming import failed", "\u6d41\u5a92\u4f53\u5bfc\u5165\u5931\u8d25");
        put("streaming.request.failed", "Streaming request failed", "\u6d41\u5a92\u4f53\u8bf7\u6c42\u5931\u8d25");
        put("streaming.account.playlists.failed", "Could not load account playlists", "\u65e0\u6cd5\u52a0\u8f7d\u8d26\u6237\u6b4c\u5355");
        put("streaming.account.playlists.import.title", "Sync account playlists", "\u540c\u6b65\u8d26\u6237\u6b4c\u5355");
        put("streaming.account.playlists.import.message", "Choose playlists to import or refresh in the local library.", "\u9009\u62e9\u8981\u5bfc\u5165\u6216\u5237\u65b0\u5230\u672c\u5730\u66f2\u5e93\u7684\u6b4c\u5355\u3002");
        put("streaming.account.playlists.import.confirm", "Sync selected", "\u540c\u6b65\u5df2\u9009");
        put("streaming.account.playlists.imported", "Synced account playlists: ", "\u5df2\u540c\u6b65\u8d26\u6237\u6b4c\u5355\uff1a");
        put("streaming.account.playlists.failed.count", "Failed ", "\u5931\u8d25 ");
        put("streaming.login.opening", "Opening streaming login", "\u6b63\u5728\u6253\u5f00\u6d41\u5a92\u4f53\u767b\u5f55");
        put("streaming.login.unavailable", "Streaming login unavailable", "\u6d41\u5a92\u4f53\u767b\u5f55\u4e0d\u53ef\u7528");
        put("streaming.login.complete", "Streaming login complete", "\u6d41\u5a92\u4f53\u767b\u5f55\u5b8c\u6210");
        put("streaming.web.auth.hint", "After signing in on this page, tap Done to save the session to Yukine.", "\u5728\u6b64\u9875\u9762\u5b8c\u6210\u767b\u5f55\u540e\uff0c\u70b9\u51fb\u5b8c\u6210\u4fdd\u5b58\u5230 Yukine\u3002");
        put("streaming.web.auth.qq.hint", "QQ Music web sign-in often does not return a usable session to third-party WebViews. If scan or password sign-in does not show as signed in, use manual account info import.", "QQ \u97f3\u4e50\u7f51\u9875\u767b\u5f55\u901a\u5e38\u4e0d\u4f1a\u628a\u53ef\u7528\u767b\u5f55\u6001\u5199\u56de\u7b2c\u4e09\u65b9 WebView\u3002\u5982\u679c\u626b\u7801\u6216\u5bc6\u7801\u767b\u5f55\u540e\u4ecd\u672a\u663e\u793a\u5df2\u767b\u5f55\uff0c\u8bf7\u4f7f\u7528\u624b\u52a8\u8d26\u53f7\u4fe1\u606f\u5bfc\u5165\u3002");
        put("streaming.web.auth.qq.risk.title", "QQ Music sign-in risk notice", "QQ \u97f3\u4e50\u767b\u5f55\u98ce\u9669\u63d0\u793a");
        put("streaming.web.auth.qq.risk.message", "Third-party WebView sign-in and subsequent resolving may trigger QQ Music risk controls, and access from the current network may be restricted. Use only your own account and follow QQ Music rules. If sign-in fails, sign out and back in through the official QQ Music client, or contact official support.", "\u4f7f\u7528 QQ \u97f3\u4e50\u7f51\u9875\u4f1a\u8bdd\u767b\u5f55\u7b2c\u4e09\u65b9\u5ba2\u6237\u7aef\uff0c\u53ef\u80fd\u89e6\u53d1\u5e73\u53f0\u98ce\u63a7\uff0c\u5bfc\u81f4\u767b\u5f55\u6001\u5931\u6548\u3001\u65e0\u6cd5\u89e3\u6790\u6216\u5f53\u524d\u7f51\u7edc\u8bbf\u95ee\u53d7\u9650\u3002\u8bf7\u4ec5\u4f7f\u7528\u81ea\u5df1\u7684\u8d26\u53f7\u5e76\u9075\u5b88 QQ \u97f3\u4e50\u89c4\u5219\u3002\u767b\u5f55\u5f02\u5e38\u65f6\uff0c\u8bf7\u5728 QQ \u97f3\u4e50\u5b98\u65b9\u5ba2\u6237\u7aef\u9000\u51fa\u540e\u91cd\u65b0\u767b\u5f55\uff0c\u6216\u8054\u7cfb\u5b98\u65b9\u652f\u6301\u3002");
        put("streaming.web.auth.qq.risk.countdown", "Please wait %d s", "\u8bf7\u7b49\u5f85 %d \u79d2");
        put("streaming.web.auth.qq.risk.continue", "I understand, continue", "\u6211\u5df2\u4e86\u89e3\uff0c\u7ee7\u7eed");
        put("streaming.web.auth.open.browser", "Browser", "\u6d4f\u89c8\u5668");
        put("streaming.web.auth.done", "Done", "\u5b8c\u6210");
        put("streaming.web.auth.manual.cookie", "Manual import", "\u624b\u52a8\u5bfc\u5165");
        put("streaming.web.auth.browser.failed", "No browser can open this login page.", "\u6ca1\u6709\u53ef\u6253\u5f00\u6b64\u767b\u5f55\u9875\u7684\u6d4f\u89c8\u5668\u3002");
        put("streaming.signed.out", "Signed out of streaming", "\u5df2\u9000\u51fa\u6d41\u5a92\u4f53");
        put("streaming.manual.cookie", "Enter account info manually", "\u624b\u52a8\u586b\u5199\u8d26\u53f7\u4fe1\u606f");
        put("streaming.cookie.hint.default", "Paste the provider Cookie here, for example MUSIC_U=...; os=pc; appver=...", "\u5728\u8fd9\u91cc\u7c98\u8d34\u97f3\u6e90 Cookie\uff0c\u4f8b\u5982 MUSIC_U=...; os=pc; appver=...");
        put("streaming.cookie.hint.qq", "Paste QQ Music Cookie here. It usually contains qqmusic_key/qm_keyst and uin/p_uin.", "\u5728\u8fd9\u91cc\u7c98\u8d34 QQ \u97f3\u4e50 Cookie\uff0c\u901a\u5e38\u9700\u8981\u5305\u542b qqmusic_key/qm_keyst \u548c uin/p_uin\u3002");
        put("streaming.cookie.empty", "Account info is empty", "\u8d26\u53f7\u4fe1\u606f\u4e3a\u7a7a");
        put("streaming.cookie.saved", "Account info saved", "\u8d26\u53f7\u4fe1\u606f\u5df2\u4fdd\u5b58");
        put("streaming.choose.login.provider", "Choose a streaming source to sign in", "\u8bf7\u5148\u9009\u62e9\u8981\u767b\u5f55\u7684\u6d41\u5a92\u4f53\u97f3\u6e90");
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
        put("streaming.lx.import.source", "Import LX source", "\u5bfc\u5165 LX \u97f3\u6e90");
        put("streaming.lx.import.hint", "Import an LX custom source by selecting a local .js file or pasting one or more source script URLs.", "\u901a\u8fc7\u9009\u62e9\u672c\u5730 .js \u6587\u4ef6\uff0c\u6216\u7c98\u8d34\u4e00\u4e2a/\u591a\u4e2a\u97f3\u6e90\u811a\u672c\u94fe\u63a5\u6765\u5bfc\u5165 LX \u81ea\u5b9a\u4e49\u97f3\u6e90\u3002");
        put("streaming.lx.source.file", "Choose local JS file", "\u9009\u62e9\u672c\u5730 JS \u6587\u4ef6");
        put("streaming.lx.source.url", "Import from network link", "\u4ece\u7f51\u7edc\u94fe\u63a5\u5bfc\u5165");
        put("streaming.lx.source.url.hint", "Paste one or more LX source .js links, one per line", "\u7c98\u8d34\u4e00\u4e2a\u6216\u591a\u4e2a LX \u97f3\u6e90 .js \u94fe\u63a5\uff0c\u6bcf\u884c\u4e00\u4e2a");
        put("streaming.lx.source.url.empty", "No source link entered", "\u672a\u8f93\u5165\u97f3\u6e90\u94fe\u63a5");
        put("streaming.lx.importing", "Importing LX source", "\u6b63\u5728\u5bfc\u5165 LX \u97f3\u6e90");
        put("streaming.lx.import.failed", "LX source import is unavailable", "LX \u97f3\u6e90\u5bfc\u5165\u6682\u4e0d\u53ef\u7528");
        put("streaming.lx.source.imported", "Imported LX sources: ", "\u5df2\u5bfc\u5165 LX \u97f3\u6e90\uff1a");
        put("streaming.lx.source.failed", "Failed: ", "\u5931\u8d25\uff1a");
        put("streaming.lx.source.none", "No valid LX source script found", "\u672a\u627e\u5230\u6709\u6548\u7684 LX \u97f3\u6e90\u811a\u672c");
        put("streaming.lx.source.manage", "Manage imported sources", "\u7ba1\u7406\u5df2\u5bfc\u5165\u97f3\u6e90");
        put("streaming.lx.source.manager", "LX source manager", "LX \u97f3\u6e90\u7ba1\u7406");
        put("streaming.lx.source.empty", "No imported LX sources", "\u5c1a\u672a\u5bfc\u5165 LX \u97f3\u6e90");
        put("streaming.lx.source.enabled", "Enabled", "\u5df2\u542f\u7528");
        put("streaming.lx.source.disabled", "Disabled", "\u5df2\u505c\u7528");
        put("streaming.lx.source.enable", "Enable source", "\u542f\u7528\u97f3\u6e90");
        put("streaming.lx.source.disable", "Disable source", "\u505c\u7528\u97f3\u6e90");
        put("streaming.lx.source.move.up", "Move up", "\u4e0a\u79fb");
        put("streaming.lx.source.move.down", "Move down", "\u4e0b\u79fb");
        put("streaming.lx.source.remove", "Remove source", "\u5220\u9664\u97f3\u6e90");
        put("streaming.lx.source.remove.confirm", "Remove this LX source?", "\u5220\u9664\u8fd9\u4e2a LX \u97f3\u6e90\uff1f");
        put("streaming.lx.source.updated", "LX source settings updated", "LX \u97f3\u6e90\u8bbe\u7f6e\u5df2\u66f4\u65b0");
        put("streaming.lx.source.update.failed", "Could not update LX source", "\u65e0\u6cd5\u66f4\u65b0 LX \u97f3\u6e90");
        put("streaming.account.playlists", "Account playlists", "\u8d26\u6237\u6b4c\u5355");
        put("streaming.no.account.playlists", "No account playlists found", "\u6682\u672a\u627e\u5230\u8d26\u6237\u6b4c\u5355");
        put("streaming.load.account.playlists", "Sync account playlists", "\u540c\u6b65\u8d26\u6237\u6b4c\u5355");
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
        put("adding.stream", "Adding stream", "\u6b63\u5728\u6dfb\u52a0\u5728\u7ebf\u97f3\u4e50");
        put("updating.stream", "Updating stream", "\u6b63\u5728\u66f4\u65b0\u5728\u7ebf\u97f3\u4e50");
        put("importing.m3u.playlist", "Importing M3U playlist", "\u6b63\u5728\u5bfc\u5165 M3U \u64ad\u653e\u5217\u8868");
        put("no.streams.to.play", "No streams to play", "\u6ca1\u6709\u53ef\u64ad\u653e\u7684\u5728\u7ebf\u97f3\u4e50");
        put("no.webdav.tracks.to.play", "No WebDAV tracks to play", "\u6ca1\u6709\u53ef\u64ad\u653e\u7684 WebDAV \u66f2\u76ee");
        put("no.source.tracks.to.play", "No tracks from this source to play", "\u6b64\u6765\u6e90\u6ca1\u6709\u53ef\u64ad\u653e\u7684\u66f2\u76ee");
        put("no.streams.to.delete", "No streams to delete", "\u6ca1\u6709\u53ef\u5220\u9664\u7684\u5728\u7ebf\u97f3\u4e50");
        put("deleting.streams", "Deleting streams", "\u6b63\u5728\u5220\u9664\u5728\u7ebf\u97f3\u4e50");
        put("deleting.stream", "Deleting stream", "\u6b63\u5728\u5220\u9664\u5728\u7ebf\u97f3\u4e50");
        put("deleting.source", "Deleting source", "\u6b63\u5728\u5220\u9664\u6765\u6e90");
        put("library.updated", "Library updated", "\u66f2\u5e93\u5df2\u66f4\u65b0");
        put("audio.specs.updated", "Audio specs parsed", "\u97f3\u9891\u89c4\u683c\u5df2\u89e3\u6790");
        put("could.not.add.stream.url", "Could not add stream URL", "\u65e0\u6cd5\u6dfb\u52a0\u5728\u7ebf\u97f3\u4e50 URL");
        put("could.not.update.stream.url", "Could not update stream URL", "\u65e0\u6cd5\u66f4\u65b0\u5728\u7ebf\u97f3\u4e50 URL");
        put("no.streams.imported", "No streams imported", "\u672a\u5bfc\u5165\u5728\u7ebf\u97f3\u4e50");
        put("local.m3u.import.failed", "Local M3U import failed", "\u672c\u5730 M3U \u5bfc\u5165\u5931\u8d25");
        put("playlist.import.failed", "Playlist import failed", "\u64ad\u653e\u5217\u8868\u5bfc\u5165\u5931\u8d25");
        put("no.playlist.entries.imported", "No playlist entries imported", "\u672a\u5bfc\u5165\u64ad\u653e\u5217\u8868\u6761\u76ee");
        put("imported.streams", "Imported streams", "\u5df2\u5bfc\u5165\u5728\u7ebf\u97f3\u4e50");
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
        put("streams.count.prefix", "streams ", "\u5728\u7ebf\u97f3\u4e50 ");
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

    public static String text(String languageMode, String key) {
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
        if (MainRoutes.TAB_DOWNLOADS.equals(tabKey)) {
            return text(languageMode, "tab.downloads");
        }
        if (MainRoutes.TAB_SEARCH.equals(tabKey)) {
            return text(languageMode, "tab.search");
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
                return "\u67d4\u548c\u6df1\u8272";
            case EchoTheme.MODE_LIGHT:
                return "\u6e05\u65b0\u6d45\u8272";
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
            case EchoTheme.MODE_DYNAMIC:
                return "\u52a8\u6001\u53d6\u8272";
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
                return "Soft dark";
            case EchoTheme.MODE_LIGHT:
                return "Fresh light";
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
            case EchoTheme.MODE_DYNAMIC:
                return "Material You";
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
