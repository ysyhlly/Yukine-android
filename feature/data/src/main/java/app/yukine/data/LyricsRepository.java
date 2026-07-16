package app.yukine.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.yukine.model.LyricsLine;
import app.yukine.model.Track;
import app.yukine.identity.LyricSourceBinding;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LyricsRepository {
    public interface BindingStore {
        List<LyricSourceBinding> load(long trackId);
        void save(long trackId, LyricSourceBinding binding);
    }

    private static final Pattern LRC_TIME_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final String LRCLIB_API_ROOT = "https://lrclib.net/api";
    private static final String NETEASE_LYRIC_URL = "https://music.163.com/api/song/lyric";
    private static final String NETEASE_SEARCH_URL = "https://music.163.com/api/cloudsearch/pc";
    private static final String QQ_SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String QQ_LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String KUGOU_SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song";
    private static final String KUGOU_LYRIC_SEARCH_URL = "https://lyrics.kugou.com/search";
    private static final String KUGOU_LYRIC_DOWNLOAD_URL = "https://lyrics.kugou.com/download";
    private static final String KUWO_SEARCH_URL = "https://search.kuwo.cn/r.s";
    private static final String KUWO_LYRIC_URL = "https://m.kuwo.cn/newh5/singles/songinfoandlrc";
    private static final String STREAMING_PREFIX = "streaming:";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final long ONLINE_RACE_TIMEOUT_MS = 5500L;
    private static final int ONLINE_CACHE_LIMIT = 48;
    private static final Map<String, List<LyricsLine>> ONLINE_CACHE =
            new LinkedHashMap<String, List<LyricsLine>>(ONLINE_CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<LyricsLine>> eldest) {
                    return size() > ONLINE_CACHE_LIMIT;
                }
            };
    private static final Map<String, String> NETEASE_SEARCH_CACHE =
            new LinkedHashMap<String, String>(ONLINE_CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > ONLINE_CACHE_LIMIT;
                }
            };
    private final BindingStore bindingStore;
    private final ThreadLocal<ResolvedBinding> resolvedBinding = new ThreadLocal<>();

    public LyricsRepository() {
        this(null);
    }

    public LyricsRepository(BindingStore bindingStore) {
        this.bindingStore = bindingStore;
    }

    public List<LyricsLine> loadForTrack(Track track) {
        return loadForTrack(track, false);
    }

    public List<LyricsLine> loadForTrack(Track track, boolean onlineEnabled) {
        return loadForTrack(track, onlineEnabled, "");
    }

    public List<LyricsLine> loadForTrack(Track track, boolean onlineEnabled, String neteaseProviderTrackId) {
        if (track == null) {
            return Collections.emptyList();
        }
        resolvedBinding.remove();
        if (!track.dataPath.isEmpty()) {
            File audioFile = new File(track.dataPath);
            File lyricsFile = findSidecarLyrics(audioFile);
            if (lyricsFile != null && lyricsFile.isFile()) {
                try {
                    List<LyricsLine> localLines = parseLrc(lyricsFile);
                    if (!localLines.isEmpty()) {
                        rememberBinding("local", lyricsFile.getAbsolutePath());
                        return finish(track, localLines);
                    }
                } catch (IOException ignored) {
                    // Fall back to online lyrics when enabled.
                }
            }
        }
        List<LyricsLine> boundLines = fetchBoundLyrics(track);
        if (!boundLines.isEmpty()) {
            return finish(track, boundLines);
        }
        List<LyricsLine> neteaseLines = fetchNeteaseLyrics(neteaseProviderTrackId);
        if (neteaseLines.isEmpty()) {
            neteaseLines = fetchNeteaseStreamingLyrics(track);
        }
        if (!neteaseLines.isEmpty()) {
            return finish(track, neteaseLines);
        }
        List<LyricsLine> streamingProviderLines = fetchDirectStreamingProviderLyrics(track);
        if (!streamingProviderLines.isEmpty()) {
            return finish(track, streamingProviderLines);
        }
        if (!onlineEnabled) {
            return Collections.emptyList();
        }
        List<LyricsLine> lrclibLines = fetchOnlineLyrics(track);
        if (!lrclibLines.isEmpty()) {
            return finish(track, lrclibLines);
        }
        neteaseLines = fetchNeteaseSearchLyrics(track);
        if (!neteaseLines.isEmpty()) {
            return finish(track, neteaseLines);
        }
        List<LyricsLine> qqLines = fetchQqSearchLyrics(track);
        if (!qqLines.isEmpty()) {
            return finish(track, qqLines);
        }
        List<LyricsLine> kugouLines = fetchKugouSearchLyrics(track);
        if (!kugouLines.isEmpty()) {
            return finish(track, kugouLines);
        }
        return finish(track, fetchKuwoSearchLyrics(track));
    }

    private List<LyricsLine> fetchBoundLyrics(Track track) {
        if (bindingStore == null) {
            return Collections.emptyList();
        }
        List<LyricSourceBinding> bindings;
        try {
            bindings = bindingStore.load(track.id);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
        if (bindings == null) {
            return Collections.emptyList();
        }
        for (LyricSourceBinding binding : bindings) {
            String provider = binding.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
            String id = binding.getProviderLyricId().trim();
            List<LyricsLine> lines;
            if ("netease".equals(provider)) {
                lines = fetchNeteaseLyrics(id);
            } else if ("qq".equals(provider) || "qqmusic".equals(provider)) {
                lines = fetchQqLyricsByMid(id, track);
            } else if ("kuwo".equals(provider)) {
                lines = fetchKuwoLyricsByRid(id, track);
            } else if ("kugou".equals(provider)) {
                lines = fetchKugouLyricsByBinding(id);
            } else {
                continue;
            }
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        return Collections.emptyList();
    }

    private List<LyricsLine> finish(Track track, List<LyricsLine> lines) {
        try {
            ResolvedBinding binding = resolvedBinding.get();
            if (bindingStore != null && binding != null && lines != null && !lines.isEmpty()) {
                bindingStore.save(track.id, new LyricSourceBinding(
                        0L,
                        binding.provider,
                        binding.providerLyricId,
                        lines.size() > 1,
                        Math.max(0L, track.durationMs),
                        lyricsChecksum(lines),
                        System.currentTimeMillis()
                ));
            }
            return lines == null ? Collections.emptyList() : lines;
        } finally {
            resolvedBinding.remove();
        }
    }

    private void rememberBinding(String provider, String providerLyricId) {
        if (provider != null && providerLyricId != null
                && !provider.trim().isEmpty() && !providerLyricId.trim().isEmpty()) {
            resolvedBinding.set(new ResolvedBinding(provider.trim(), providerLyricId.trim()));
        }
    }

    private String lyricsChecksum(List<LyricsLine> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (LyricsLine line : lines) {
                digest.update((line.timeMs + "\u0000" + line.text + "\n").getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder value = new StringBuilder();
            for (byte part : digest.digest()) {
                value.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            }
            return value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class ResolvedBinding {
        final String provider;
        final String providerLyricId;

        ResolvedBinding(String provider, String providerLyricId) {
            this.provider = provider;
            this.providerLyricId = providerLyricId;
        }
    }

    private File findSidecarLyrics(File audioFile) {
        File parent = audioFile.getParentFile();
        if (parent == null) {
            return null;
        }
        String fileName = audioFile.getName();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        File sameName = new File(parent, baseName + ".lrc");
        if (sameName.isFile()) {
            return sameName;
        }
        File titleName = new File(parent, baseName.trim() + ".LRC");
        if (titleName.isFile()) {
            return titleName;
        }
        return null;
    }

    private List<LyricsLine> parseLrc(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseLrc(reader);
        }
    }

    private List<LyricsLine> parseLrcText(String text) throws IOException {
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text))) {
            return parseLrc(reader);
        }
    }

    private List<LyricsLine> parseLrc(BufferedReader reader) throws IOException {
        ArrayList<LyricsLine> lines = new ArrayList<>();
        String rawLine;
        while ((rawLine = reader.readLine()) != null) {
            Matcher matcher = LRC_TIME_PATTERN.matcher(rawLine);
            ArrayList<Long> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                times.add(parseTime(matcher));
                textStart = matcher.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = rawLine.substring(textStart).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (Long time : times) {
                lines.add(new LyricsLine(time, text));
            }
        }
        Collections.sort(lines, new Comparator<LyricsLine>() {
            @Override
            public int compare(LyricsLine left, LyricsLine right) {
                return Long.compare(left.timeMs, right.timeMs);
            }
        });
        return lines;
    }

    private List<LyricsLine> fetchOnlineLyrics(Track track) {
        String cacheKey = onlineCacheKey(track);
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            rememberBinding("lrclib", cacheKey);
            return cached;
        }
        try {
            JSONObject record = firstLrclibRecord(track);
            if (record == null) {
                return Collections.emptyList();
            }
            rememberBinding("lrclib", record.optString("id", cacheKey));
            String syncedLyrics = record.optString("syncedLyrics", "").trim();
            if (!syncedLyrics.isEmpty()) {
                return cacheOnlineLyrics(cacheKey, parseLrcText(syncedLyrics));
            }
            String plainLyrics = record.optString("plainLyrics", "").trim();
            if (!plainLyrics.isEmpty()) {
                return cacheOnlineLyrics(cacheKey, parsePlainLyrics(plainLyrics));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private List<LyricsLine> fetchDirectStreamingProviderLyrics(Track track) {
        StreamingIdentity identity = streamingIdentity(track);
        if (identity == null || identity.providerTrackId.isEmpty()) {
            return Collections.emptyList();
        }
        if (isQqProvider(identity.provider)) {
            return fetchQqLyricsByMid(identity.providerTrackId, track);
        }
        if (isKuwoProvider(identity.provider)) {
            return fetchKuwoLyricsByRid(identity.providerTrackId, track);
        }
        return Collections.emptyList();
    }

    private List<LyricsLine> fetchQqSearchLyrics(Track track) {
        String cacheKey = "qq-search\n" + onlineCacheKey(track);
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            JSONArray songs = requestQqSearchSongs(track);
            JSONObject best = bestProviderSong(track, songs, "qq");
            if (best == null) {
                return Collections.emptyList();
            }
            String mid = best.optString("mid", best.optString("songmid", "")).trim();
            if (mid.isEmpty()) {
                return Collections.emptyList();
            }
            return cacheOnlineLyrics(cacheKey, fetchQqLyricsByMid(mid, track));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private JSONArray requestQqSearchSongs(Track track) throws Exception {
        JSONArray result = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String query : providerSearchQueries(track)) {
            JSONArray musicuSongs = requestQqMusicuSearchSongs(query);
            for (int i = 0; i < musicuSongs.length(); i++) {
                JSONObject mapped = mapQqSong(musicuSongs.optJSONObject(i));
                String mid = mapped.optString("mid", "");
                if (!mid.isEmpty() && seen.add(mid)) {
                    result.put(mapped);
                }
            }
            if (result.length() > 0) {
                continue;
            }
            String url = QQ_SEARCH_URL
                    + "?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song"
                    + "&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=1&n=5"
                    + "&w=" + encode(query)
                    + "&format=json";
            JSONObject body = requestProviderJson(url, "https://y.qq.com/");
            JSONObject data = body.optJSONObject("data");
            JSONObject song = data == null ? null : data.optJSONObject("song");
            JSONArray list = song == null ? null : song.optJSONArray("list");
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject raw = list.optJSONObject(i);
                JSONObject mapped = mapQqSong(raw);
                String mid = mapped.optString("mid", "");
                if (!mid.isEmpty() && seen.add(mid)) {
                    result.put(mapped);
                }
            }
        }
        return result;
    }

    private JSONArray requestQqMusicuSearchSongs(String query) {
        try {
            JSONObject body = new JSONObject()
                    .put("comm", new JSONObject()
                            .put("ct", "19")
                            .put("cv", "1859")
                            .put("uin", "0"))
                    .put("req_1", new JSONObject()
                            .put("module", "music.search.SearchCgiService")
                            .put("method", "DoSearchForQQMusicDesktop")
                            .put("param", new JSONObject()
                                    .put("query", query)
                                    .put("page_num", 1)
                                    .put("num_per_page", 5)
                                    .put("search_type", 0)));
            JSONObject data = requestProviderJsonPost("https://u.y.qq.com/cgi-bin/musicu.fcg", body.toString(), "https://y.qq.com/");
            JSONObject req = data.optJSONObject("req_1");
            JSONObject reqData = req == null ? null : req.optJSONObject("data");
            JSONObject reqBody = reqData == null ? null : reqData.optJSONObject("body");
            JSONObject song = reqBody == null ? null : reqBody.optJSONObject("song");
            JSONArray list = song == null ? null : song.optJSONArray("list");
            return list == null ? new JSONArray() : list;
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private JSONObject mapQqSong(JSONObject song) throws Exception {
        if (song == null) {
            return new JSONObject();
        }
        String mid = firstString(song, "songmid", "mid", "media_mid");
        String title = firstString(song, "name", "title", "songname", "songorig");
        String album = firstString(song.optJSONObject("album"), "name", "title");
        if (album.isEmpty()) {
            album = firstString(song, "albumname", "albumtitle");
        }
        StringBuilder artist = new StringBuilder();
        JSONArray singers = song.optJSONArray("singer");
        if (singers != null) {
            for (int i = 0; i < singers.length(); i++) {
                JSONObject singer = singers.optJSONObject(i);
                String name = firstString(singer, "name", "title");
                if (name.isEmpty()) {
                    continue;
                }
                if (artist.length() > 0) {
                    artist.append(' ');
                }
                artist.append(name);
            }
        }
        return new JSONObject()
                .put("mid", mid)
                .put("title", title)
                .put("artist", artist.toString())
                .put("album", album)
                .put("durationMs", Math.max(0L, song.optLong("interval", 0L) * 1000L));
    }

    private List<LyricsLine> fetchQqLyricsByMid(String rawMid, Track fallback) {
        String mid = qqSongMid(rawMid);
        if (mid.isEmpty()) {
            return Collections.emptyList();
        }
        rememberBinding("qqmusic", mid);
        String cacheKey = "qq\n" + mid;
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            String url = QQ_LYRIC_URL
                    + "?songmid=" + encode(mid)
                    + "&pcachetime=" + System.currentTimeMillis()
                    + "&g_tk=5381&loginUin=0&hostUin=0&format=json"
                    + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq"
                    + "&needNewCode=0&nobase64=1";
            JSONObject body = requestProviderJson(url, "https://y.qq.com/");
            String primary = maybeDecodeBase64(body.optString("lyric", "")).trim();
            String translation = maybeDecodeBase64(body.optString("trans", "")).trim();
            List<LyricsLine> lines = parseProviderLyrics(primary, translation);
            return cacheOnlineLyrics(cacheKey, lines);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<LyricsLine> fetchKugouSearchLyrics(Track track) {
        String cacheKey = "kugou-search\n" + onlineCacheKey(track);
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            JSONArray songs = requestKugouSearchSongs(track);
            JSONObject best = bestProviderSong(track, songs, "kugou");
            if (best == null) {
                return Collections.emptyList();
            }
            return cacheOnlineLyrics(cacheKey, fetchKugouLyrics(best, track));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private JSONArray requestKugouSearchSongs(Track track) throws Exception {
        JSONArray result = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String query : providerSearchQueries(track)) {
            String url = KUGOU_SEARCH_URL
                    + "?format=json&keyword=" + encode(query)
                    + "&page=1&pagesize=5&showtype=1";
            JSONObject body = requestProviderJson(url, "https://www.kugou.com/");
            JSONObject data = body.optJSONObject("data");
            JSONArray list = data == null ? null : data.optJSONArray("info");
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject raw = list.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                String hash = firstString(raw, "hash", "Hash", "FileHash", "SQFileHash", "HQFileHash");
                String title = firstString(raw, "SongName", "songname", "FileName");
                String artist = firstString(raw, "SingerName", "singername");
                String id = hash.isEmpty() ? title + "|" + artist : hash;
                if (id.trim().isEmpty() || !seen.add(id)) {
                    continue;
                }
                result.put(new JSONObject()
                        .put("hash", hash)
                        .put("title", title)
                        .put("artist", artist)
                        .put("album", firstString(raw, "AlbumName", "album_name"))
                        .put("durationMs", providerDurationMs(raw.opt("Duration"), raw.opt("duration"))));
            }
        }
        return result;
    }

    private List<LyricsLine> fetchKugouLyrics(JSONObject song, Track fallback) {
        try {
            String keyword = (song.optString("title") + " " + song.optString("artist")).trim();
            String url = KUGOU_LYRIC_SEARCH_URL
                    + "?ver=1&man=yes&client=pc"
                    + "&keyword=" + encode(keyword.isEmpty() ? fallback.title + " " + fallback.artist : keyword)
                    + "&duration=" + Math.max(0L, song.optLong("durationMs", fallback.durationMs));
            String hash = song.optString("hash", "");
            if (!hash.isEmpty()) {
                url += "&hash=" + encode(hash);
            }
            JSONObject search = requestProviderJson(url, "https://www.kugou.com/");
            JSONArray candidates = search.optJSONArray("candidates");
            if (candidates == null && search.optJSONObject("data") != null) {
                candidates = search.optJSONObject("data").optJSONArray("candidates");
            }
            if (candidates == null || candidates.length() == 0) {
                return Collections.emptyList();
            }
            JSONObject candidate = bestKugouLyricCandidate(fallback, candidates);
            if (candidate == null) {
                return Collections.emptyList();
            }
            String id = candidate.optString("id", "").trim();
            String accessKey = candidate.optString("accesskey", candidate.optString("accessKey", "")).trim();
            if (id.isEmpty() || accessKey.isEmpty()) {
                return Collections.emptyList();
            }
            rememberBinding("kugou", id + "\n" + accessKey);
            String downloadUrl = KUGOU_LYRIC_DOWNLOAD_URL
                    + "?ver=1&client=pc&id=" + encode(id)
                    + "&accesskey=" + encode(accessKey)
                    + "&fmt=lrc&charset=utf8";
            JSONObject body = requestProviderJson(downloadUrl, "https://www.kugou.com/");
            String lyrics = maybeDecodeBase64(body.optString("content", "")).trim();
            return parseProviderLyrics(lyrics, "");
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<LyricsLine> fetchKugouLyricsByBinding(String bindingId) {
        if (bindingId == null) {
            return Collections.emptyList();
        }
        String[] parts = bindingId.split("\\n", 2);
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            return Collections.emptyList();
        }
        String id = parts[0].trim();
        String accessKey = parts[1].trim();
        rememberBinding("kugou", id + "\n" + accessKey);
        try {
            String downloadUrl = KUGOU_LYRIC_DOWNLOAD_URL
                    + "?ver=1&client=pc&id=" + encode(id)
                    + "&accesskey=" + encode(accessKey)
                    + "&fmt=lrc&charset=utf8";
            JSONObject body = requestProviderJson(downloadUrl, "https://www.kugou.com/");
            return parseProviderLyrics(maybeDecodeBase64(body.optString("content", "")).trim(), "");
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private JSONObject bestKugouLyricCandidate(Track track, JSONArray candidates) {
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) {
                continue;
            }
            int score = providerMatchScore(
                    track,
                    firstString(candidate, "song", "title", "filename"),
                    firstString(candidate, "singer", "artist"),
                    "",
                    providerDurationMs(candidate.opt("duration"))
            );
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= 28 ? best : candidates.optJSONObject(0);
    }

    private List<LyricsLine> fetchKuwoSearchLyrics(Track track) {
        String cacheKey = "kuwo-search\n" + onlineCacheKey(track);
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            JSONArray songs = requestKuwoSearchSongs(track);
            JSONObject best = bestProviderSong(track, songs, "kuwo");
            if (best == null) {
                return Collections.emptyList();
            }
            return cacheOnlineLyrics(cacheKey, fetchKuwoLyricsByRid(best.optString("rid", ""), track));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private JSONArray requestKuwoSearchSongs(Track track) throws Exception {
        JSONArray result = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String query : providerSearchQueries(track)) {
            String url = KUWO_SEARCH_URL
                    + "?all=" + encode(query)
                    + "&ft=music&itemset=web_2013&client=kt&pn=0&rn=5&rformat=json&encoding=utf8";
            JSONObject body = requestProviderJson(url, "https://www.kuwo.cn/");
            JSONArray list = body.optJSONArray("abslist");
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject raw = list.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                String rid = kuwoRid(firstString(raw, "MUSICRID", "musicrid", "rid"));
                if (rid.isEmpty() || !seen.add(rid)) {
                    continue;
                }
                result.put(new JSONObject()
                        .put("rid", rid)
                        .put("title", firstString(raw, "SONGNAME", "songname", "name"))
                        .put("artist", firstString(raw, "ARTIST", "artist"))
                        .put("album", firstString(raw, "ALBUM", "album"))
                        .put("durationMs", providerDurationMs(raw.opt("DURATION"), raw.opt("duration"))));
            }
        }
        return result;
    }

    private List<LyricsLine> fetchKuwoLyricsByRid(String rawRid, Track fallback) {
        String rid = kuwoRid(rawRid);
        if (rid.isEmpty()) {
            return Collections.emptyList();
        }
        rememberBinding("kuwo", rid);
        String cacheKey = "kuwo\n" + rid;
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            JSONObject body = requestProviderJson(KUWO_LYRIC_URL + "?musicId=" + encode(rid), "https://www.kuwo.cn/");
            JSONObject data = body.optJSONObject("data");
            if (data == null) {
                return Collections.emptyList();
            }
            String synced = buildKuwoSyncedLyrics(data.optJSONArray("lrclist"));
            String plain = data.optString("lyrics", "");
            List<LyricsLine> lines = parseProviderLyrics(synced.isEmpty() ? plain : synced, "");
            return cacheOnlineLyrics(cacheKey, lines);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private JSONObject bestProviderSong(Track track, JSONArray songs, String provider) {
        if (songs == null || songs.length() == 0) {
            return null;
        }
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song == null) {
                continue;
            }
            int score = providerMatchScore(
                    track,
                    song.optString("title"),
                    song.optString("artist"),
                    song.optString("album"),
                    song.optLong("durationMs", 0L)
            );
            if (score > bestScore) {
                bestScore = score;
                best = song;
            }
        }
        int threshold = "lrclib".equals(provider) ? 35 : 30;
        return bestScore >= threshold ? best : null;
    }

    private int providerMatchScore(Track track, String candidateTitle, String candidateArtist, String candidateAlbum, long durationMs) {
        int score = 0;
        String title = cleanMatchText(stripVersionText(track.title));
        String rawTitle = cleanMatchText(track.title);
        String artist = cleanMatchText(track.artist);
        String album = cleanMatchText(track.album);
        String otherTitle = cleanMatchText(candidateTitle);
        String otherArtist = cleanMatchText(candidateArtist);
        String otherAlbum = cleanMatchText(candidateAlbum);
        if (!title.isEmpty() && !otherTitle.isEmpty()) {
            if (title.equals(otherTitle)) {
                score += 70;
            } else if (otherTitle.contains(title) || title.contains(otherTitle)) {
                score += 42;
            } else if (!rawTitle.isEmpty() && rawTitle.equals(otherTitle)) {
                score += 55;
            }
        }
        if (!artist.isEmpty() && !otherArtist.isEmpty()) {
            if (artist.equals(otherArtist) || otherArtist.contains(artist) || artist.contains(otherArtist)) {
                score += 25;
            }
        }
        if (!album.isEmpty() && !otherAlbum.isEmpty()) {
            if (album.equals(otherAlbum) || otherAlbum.contains(album) || album.contains(otherAlbum)) {
                score += 8;
            }
        }
        if (track.durationMs > 0L && durationMs > 0L) {
            long delta = Math.abs(track.durationMs - durationMs);
            if (delta <= 2500L) {
                score += 12;
            } else if (delta <= 8000L) {
                score += 5;
            }
        }
        return score;
    }

    private List<String> providerSearchQueries(Track track) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String title = normalizeSearchText(track.title);
        String artist = normalizeSearchText(track.artist);
        addSearchQuery(queries, title + " " + artist);
        addSearchQuery(queries, stripVersionText(title) + " " + artist);
        addSearchQuery(queries, title);
        addSearchQuery(queries, stripVersionText(title));
        return new ArrayList<>(queries);
    }

    /**
     * Parses a provider-style LRC payload and merges time-aligned translations using the same
     * rules as the built-in online lyric sources. External source adapters call this after their
     * own transport succeeds so parsing behavior remains consistent across providers.
     */
    public List<LyricsLine> parseProviderLyrics(String primary, String translation) throws IOException {
        String clean = primary == null ? "" : primary.trim();
        if (clean.isEmpty() || isInstrumentalLyricsText(clean)) {
            return Collections.emptyList();
        }
        List<LyricsLine> lines = parseLrcText(clean);
        if (lines.isEmpty()) {
            lines = parsePlainLyrics(clean);
        }
        if (translation != null && !translation.trim().isEmpty()) {
            List<LyricsLine> translated = parseLrcText(translation.trim());
            if (!translated.isEmpty()) {
                lines = mergeTranslatedLyrics(lines, translated);
            }
        }
        return lines;
    }

    private boolean isInstrumentalLyricsText(String text) {
        if (text == null) {
            return false;
        }
        String clean = text.replaceAll("\\[[^]]*]", "").trim().toLowerCase(java.util.Locale.ROOT);
        return clean.equals("纯音乐，请欣赏")
                || clean.equals("此歌曲为没有填词的纯音乐，请您欣赏")
                || clean.equals("instrumental")
                || clean.contains("没有填词的纯音乐");
    }

    private List<LyricsLine> fetchNeteaseStreamingLyrics(Track track) {
        String providerTrackId = neteaseProviderTrackId(track);
        return fetchNeteaseLyrics(providerTrackId);
    }

    private List<LyricsLine> fetchNeteaseLyrics(String providerTrackId) {
        providerTrackId = neteaseSongId(providerTrackId);
        if (providerTrackId.isEmpty()) {
            return Collections.emptyList();
        }
        rememberBinding("netease", providerTrackId);
        String cacheKey = "netease\n" + providerTrackId;
        List<LyricsLine> cached = cachedOnlineLyrics(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            JSONObject body = requestNeteaseLyrics(providerTrackId);
            String syncedLyrics = lyricText(body.optJSONObject("lrc")).trim();
            if (syncedLyrics.isEmpty()) {
                syncedLyrics = lyricText(body.optJSONObject("klyric")).trim();
            }
            if (syncedLyrics.isEmpty()) {
                return Collections.emptyList();
            }
            List<LyricsLine> lines = parseLrcText(syncedLyrics);
            List<LyricsLine> translated = parseLrcText(lyricText(body.optJSONObject("tlyric")).trim());
            if (!translated.isEmpty()) {
                lines = mergeTranslatedLyrics(lines, translated);
            }
            return cacheOnlineLyrics(cacheKey, lines);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<LyricsLine> fetchNeteaseSearchLyrics(Track track) {
        String providerTrackId = firstNeteaseSearchTrackId(track);
        if (providerTrackId.isEmpty()) {
            return Collections.emptyList();
        }
        return fetchNeteaseLyrics(providerTrackId);
    }

    private String firstNeteaseSearchTrackId(Track track) {
        if (track == null || track.title.trim().isEmpty()) {
            return "";
        }
        String cacheKey = "netease-search\n" + onlineCacheKey(track);
        String cached = cachedNeteaseSearchTrackId(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            JSONArray candidates = new JSONArray();
            Set<String> seenIds = new LinkedHashSet<>();
            for (String query : neteaseSearchQueries(track)) {
                JSONObject body = requestNeteaseSearch(query);
                JSONArray songs = neteaseSearchSongs(body);
                if (songs == null) {
                    continue;
                }
                for (int i = 0; i < songs.length(); i++) {
                    JSONObject song = songs.optJSONObject(i);
                    if (song == null) {
                        continue;
                    }
                    String id = songId(song);
                    if (id.isEmpty() || !seenIds.add(id)) {
                        continue;
                    }
                    candidates.put(song);
                }
            }
            String matched = bestNeteaseSearchTrackId(track, candidates);
            return cacheNeteaseSearchTrackId(cacheKey, matched);
        } catch (Exception ignored) {
            return "";
        }
    }

    private JSONObject requestNeteaseSearch(String query) throws Exception {
        String url = NETEASE_SEARCH_URL
                + "?s=" + encode(query)
                + "&type=1&limit=12&offset=0&total=false";
        return requestNeteaseJson(url);
    }

    private JSONArray neteaseSearchSongs(JSONObject body) {
        if (body == null) {
            return null;
        }
        JSONObject result = body.optJSONObject("result");
        if (result == null) {
            result = body.optJSONObject("data");
        }
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        if (songs == null && result != null) {
            songs = result.optJSONArray("song");
        }
        return songs == null ? body.optJSONArray("songs") : songs;
    }

    private List<String> neteaseSearchQueries(Track track) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String title = normalizeSearchText(track.title);
        String artist = normalizeSearchText(track.artist);
        String album = normalizeSearchText(track.album);
        addSearchQuery(queries, title + " " + artist);
        addSearchQuery(queries, title);
        addSearchQuery(queries, stripVersionText(title) + " " + artist);
        addSearchQuery(queries, stripVersionText(title));
        addSearchQuery(queries, title + " " + album);
        if (!artist.isEmpty()) {
            addSearchQuery(queries, artist + " " + title);
        }
        return new ArrayList<>(queries);
    }

    private void addSearchQuery(Set<String> queries, String query) {
        String clean = normalizeSearchText(query);
        if (!clean.isEmpty()) {
            queries.add(clean);
        }
    }

    private String bestNeteaseSearchTrackId(Track track, JSONArray songs) {
        if (songs == null || songs.length() == 0) {
            return "";
        }
        String bestId = "";
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song == null) {
                continue;
            }
            String id = songId(song);
            if (id.isEmpty()) {
                continue;
            }
            int score = neteaseMatchScore(track, song);
            if (score > bestScore) {
                bestScore = score;
                bestId = id;
            }
        }
        return bestScore >= 30 ? bestId : "";
    }

    private int neteaseMatchScore(Track track, JSONObject song) {
        int score = 0;
        String title = cleanMatchText(stripVersionText(track.title));
        String rawTitle = cleanMatchText(track.title);
        String artist = cleanMatchText(track.artist);
        String album = cleanMatchText(track.album);
        String songName = cleanMatchText(song.optString("name"));
        String songAlias = cleanMatchText(neteaseAliases(song));
        String songArtists = cleanMatchText(neteaseArtists(song));
        String songAlbum = cleanMatchText(neteaseAlbum(song));
        if (!title.isEmpty() && (title.equals(songName) || title.equals(songAlias))) {
            score += 70;
        } else if (!title.isEmpty() && (songName.contains(title) || title.contains(songName) || songAlias.contains(title))) {
            score += 40;
        } else if (!rawTitle.isEmpty() && rawTitle.equals(songName)) {
            score += 55;
        }
        if (!artist.isEmpty() && !songArtists.isEmpty()) {
            if (artist.equals(songArtists) || songArtists.contains(artist) || artist.contains(songArtists)) {
                score += 25;
            }
        }
        if (!album.isEmpty() && !songAlbum.isEmpty()) {
            if (album.equals(songAlbum) || songAlbum.contains(album) || album.contains(songAlbum)) {
                score += 8;
            }
        }
        long durationMs = song.optLong("dt", song.optLong("duration", 0L));
        if (track.durationMs > 0L && durationMs > 0L) {
            long delta = Math.abs(track.durationMs - durationMs);
            if (delta <= 2500L) {
                score += 12;
            } else if (delta <= 8000L) {
                score += 5;
            }
        }
        return score;
    }

    private String songId(JSONObject song) {
        Object id = song.opt("id");
        if (id instanceof Number) {
            return String.valueOf(((Number) id).longValue());
        }
        return id == null ? "" : String.valueOf(id).trim();
    }

    private String neteaseArtists(JSONObject song) {
        JSONArray artists = song.optJSONArray("ar");
        if (artists == null) {
            artists = song.optJSONArray("artists");
        }
        if (artists == null) {
            return song.optString("artist", "");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < artists.length(); i++) {
            JSONObject artist = artists.optJSONObject(i);
            if (artist == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(artist.optString("name", ""));
        }
        return builder.toString();
    }

    private String neteaseAliases(JSONObject song) {
        JSONArray aliases = song.optJSONArray("alia");
        if (aliases == null) {
            aliases = song.optJSONArray("alias");
        }
        if (aliases == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < aliases.length(); i++) {
            String alias = aliases.optString(i, "");
            if (alias.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(alias);
        }
        return builder.toString();
    }

    private String neteaseAlbum(JSONObject song) {
        JSONObject album = song.optJSONObject("al");
        if (album == null) {
            album = song.optJSONObject("album");
        }
        return album == null ? song.optString("album", "") : album.optString("name", "");
    }

    private String cleanMatchText(String value) {
        if (value == null) {
            return "";
        }
        return normalizeSearchText(value)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", "")
                .trim();
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u3000', ' ')
                .replace('（', '(')
                .replace('）', ')')
                .replace('【', '[')
                .replace('】', ']')
                .replace('「', ' ')
                .replace('」', ' ')
                .replace('《', ' ')
                .replace('》', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripVersionText(String value) {
        String clean = normalizeSearchText(value);
        return clean
                .replaceAll("(?i)\\s*[-–—]?\\s*\\((?:[^)]*(?:version|ver\\.?|remix|mix|cover|live|伴奏|纯音乐|翻自|feat\\.?|ft\\.)[^)]*)\\)", "")
                .replaceAll("(?i)\\s*[-–—]?\\s*\\[(?:[^]]*(?:version|ver\\.?|remix|mix|cover|live|伴奏|纯音乐|翻自|feat\\.?|ft\\.)[^]]*)\\]", "")
                .replaceAll("(?i)\\s+(?:feat\\.?|ft\\.)\\s+.+$", "")
                .trim();
    }

    private JSONObject requestNeteaseLyrics(String providerTrackId) throws Exception {
        String url = NETEASE_LYRIC_URL
                + "?id=" + encode(providerTrackId)
                + "&lv=1&kv=1&tv=-1";
        return requestNeteaseJson(url);
    }

    private JSONObject requestNeteaseJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Yukine-Android");
        connection.setRequestProperty("Referer", "https://music.163.com/");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new JSONObject();
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            String text = body.toString().trim();
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    private List<LyricsLine> mergeTranslatedLyrics(List<LyricsLine> original, List<LyricsLine> translated) {
        if (original == null || original.isEmpty()) {
            return translated == null ? Collections.emptyList() : translated;
        }
        if (translated == null || translated.isEmpty()) {
            return original;
        }
        ArrayList<LyricsLine> merged = new ArrayList<>();
        for (LyricsLine line : original) {
            String translation = translationAt(translated, line.timeMs);
            if (translation.isEmpty() || translation.equals(line.text)) {
                merged.add(line);
            } else {
                merged.add(new LyricsLine(line.timeMs, line.text + "\n" + translation));
            }
        }
        return merged;
    }

    private String translationAt(List<LyricsLine> translated, long timeMs) {
        for (LyricsLine line : translated) {
            if (Math.abs(line.timeMs - timeMs) <= 500L) {
                return line.text;
            }
        }
        return "";
    }

    private String lyricText(JSONObject object) {
        return object == null ? "" : object.optString("lyric", "");
    }

    private String neteaseProviderTrackId(Track track) {
        if (track == null || track.dataPath == null) {
            return "";
        }
        String dataPath = track.dataPath.trim();
        if (!dataPath.startsWith(STREAMING_PREFIX)) {
            return "";
        }
        String rest = dataPath.substring(STREAMING_PREFIX.length());
        int separator = rest.indexOf(':');
        if (separator <= 0 || separator >= rest.length() - 1) {
            return "";
        }
        String provider = rest.substring(0, separator)
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        if (!"netease".equals(provider)
                && !"neteasecloud".equals(provider)
                && !"neteasemusic".equals(provider)
                && !"163".equals(provider)
                && !"163music".equals(provider)) {
            return "";
        }
        return neteaseSongId(cutAtAny(rest.substring(separator + 1), ':', '|', '?', '#'));
    }

    private String cutAtAny(String value, char... markers) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        for (char marker : markers) {
            int index = value.indexOf(marker);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return value.substring(0, end);
    }

    private String neteaseSongId(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.matches("\\d+")) {
            return text;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String key : new String[]{"songid=", "songId=", "id="}) {
            String lowerKey = key.toLowerCase(java.util.Locale.ROOT);
            int keyStart = lower.indexOf(lowerKey);
            if (keyStart < 0) {
                continue;
            }
            int start = keyStart + key.length();
            while (start < text.length() && !Character.isDigit(text.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < text.length() && Character.isDigit(text.charAt(end))) {
                end++;
            }
            if (end > start) {
                return text.substring(start, end);
            }
        }
        return "";
    }

    private StreamingIdentity streamingIdentity(Track track) {
        if (track == null || track.dataPath == null) {
            return null;
        }
        String dataPath = track.dataPath.trim();
        if (!dataPath.startsWith(STREAMING_PREFIX)) {
            return null;
        }
        String rest = dataPath.substring(STREAMING_PREFIX.length());
        int separator = rest.indexOf(':');
        if (separator <= 0 || separator >= rest.length() - 1) {
            return null;
        }
        String provider = rest.substring(0, separator)
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        String providerTrackId = cutAtAny(rest.substring(separator + 1), ':', '?', '#').trim();
        return new StreamingIdentity(provider, providerTrackId);
    }

    private boolean isQqProvider(String provider) {
        return "qq".equals(provider)
                || "qqmusic".equals(provider)
                || "tencentmusic".equals(provider);
    }

    private boolean isKuwoProvider(String provider) {
        return "kuwo".equals(provider)
                || "kw".equals(provider)
                || "luoxue".equals(provider)
                || "lx".equals(provider)
                || "lxmusic".equals(provider);
    }

    private String qqSongMid(String value) {
        if (value == null) {
            return "";
        }
        String text = cutAtAny(value.trim(), '?', '#');
        if (text.contains("|")) {
            text = text.substring(0, text.indexOf('|'));
        }
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon < text.length() - 1) {
            text = text.substring(colon + 1);
        }
        return text.trim();
    }

    private String kuwoRid(String value) {
        if (value == null) {
            return "";
        }
        String text = cutAtAny(value.trim(), '?', '#');
        if (text.startsWith("kw:")) {
            text = text.substring(3);
        }
        if (text.toUpperCase(java.util.Locale.ROOT).startsWith("MUSIC_")) {
            text = text.substring("MUSIC_".length());
        }
        int pipe = text.indexOf('|');
        if (pipe >= 0) {
            text = text.substring(0, pipe);
        }
        return text.replaceAll("[^0-9]", "").trim();
    }

    private String maybeDecodeBase64(String value) {
        if (value == null) {
            return "";
        }
        String raw = value.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.contains("[") || raw.contains("\n") || raw.contains("\r")) {
            return raw;
        }
        try {
            byte[] decoded = android.util.Base64.decode(raw, android.util.Base64.DEFAULT);
            String text = new String(decoded, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? raw : text;
        } catch (IllegalArgumentException ignored) {
            return raw;
        }
    }

    private String buildKuwoSyncedLyrics(JSONArray array) {
        if (array == null || array.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String text = firstString(item, "lineLyric", "lyric", "text");
            if (text.isEmpty()) {
                continue;
            }
            long timeMs = kuwoTimeMs(firstString(item, "time", "startTime"));
            if (timeMs < 0L) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(formatLrcTime(timeMs)).append(text);
        }
        return builder.toString();
    }

    private long kuwoTimeMs(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1L;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            return Math.max(0L, Math.round(seconds * 1000.0));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private String formatLrcTime(long timeMs) {
        long minutes = timeMs / 60_000L;
        long seconds = (timeMs % 60_000L) / 1000L;
        long hundredths = (timeMs % 1000L) / 10L;
        return String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]", minutes, seconds, hundredths);
    }

    private long providerDurationMs(Object... values) {
        for (Object value : values) {
            if (value == null || JSONObject.NULL.equals(value)) {
                continue;
            }
            try {
                double number = value instanceof Number
                        ? ((Number) value).doubleValue()
                        : Double.parseDouble(String.valueOf(value).trim());
                if (number <= 0.0) {
                    continue;
                }
                return number > 1000.0 ? Math.round(number) : Math.round(number * 1000.0);
            } catch (NumberFormatException ignored) {
                // Try the next value.
            }
        }
        return 0L;
    }

    private String firstString(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private JSONObject requestProviderJson(String url, String referer) throws Exception {
        String text = requestText(url, referer);
        return parseProviderJson(text);
    }

    private JSONObject requestProviderJsonPost(String url, String body, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Yukine-Android"
        );
        if (referer != null && !referer.isEmpty()) {
            connection.setRequestProperty("Referer", referer);
        }
        try {
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new JSONObject();
            }
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line);
                }
            }
            return parseProviderJson(text.toString());
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject parseProviderJson(String text) throws Exception {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(clean);
        } catch (Exception firstError) {
            String objectLiteral = clean
                    .replaceAll("(?s)^\\s*[\\w.$]+\\((.*)\\)\\s*;?\\s*$", "$1")
                    .replaceAll("'((?:\\\\.|[^'\\\\])*)'", "\"$1\"");
            return new JSONObject(objectLiteral);
        }
    }

    private String requestText(String url, String referer) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Yukine-Android"
        );
        if (referer != null && !referer.isEmpty()) {
            connection.setRequestProperty("Referer", referer);
        }
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return "";
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return body.toString().trim();
        } finally {
            connection.disconnect();
        }
    }

    private static final class StreamingIdentity {
        final String provider;
        final String providerTrackId;

        StreamingIdentity(String provider, String providerTrackId) {
            this.provider = provider == null ? "" : provider;
            this.providerTrackId = providerTrackId == null ? "" : providerTrackId;
        }
    }

    private JSONObject firstLrclibRecord(Track track) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletionService<JSONObject> completion = new ExecutorCompletionService<>(executor);
        ArrayList<Future<JSONObject>> futures = new ArrayList<>(2);
        futures.add(completion.submit(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                JSONObject exact = requestJsonObject(exactUrl(track));
                return hasLyrics(exact) ? exact : null;
            }
        }));
        futures.add(completion.submit(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return firstSearchRecord(track);
            }
        }));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ONLINE_RACE_TIMEOUT_MS);
        try {
            for (int remaining = futures.size(); remaining > 0; remaining--) {
                long waitNs = deadline - System.nanoTime();
                if (waitNs <= 0L) {
                    break;
                }
                Future<JSONObject> future = completion.poll(waitNs, TimeUnit.NANOSECONDS);
                if (future == null) {
                    break;
                }
                try {
                    JSONObject record = future.get();
                    if (hasLyrics(record)) {
                        return record;
                    }
                } catch (Exception ignored) {
                    // Try the other request before giving up.
                }
            }
        } finally {
            for (Future<JSONObject> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
        }
        return null;
    }

    private JSONObject firstSearchRecord(Track track) throws Exception {
        JSONArray results = requestJsonArray(searchUrl(track));
        if (results == null) {
            return null;
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (hasLyrics(item)) {
                return item;
            }
        }
        return null;
    }

    private JSONObject requestJsonObject(String url) throws Exception {
        String body = requestText(url);
        if (body.isEmpty() || body.charAt(0) != '{') {
            return null;
        }
        return new JSONObject(body);
    }

    private JSONArray requestJsonArray(String url) throws Exception {
        String body = requestText(url);
        if (body.isEmpty() || body.charAt(0) != '[') {
            return null;
        }
        return new JSONArray(body);
    }

    private String requestText(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Yukine-Android");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return "";
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return body.toString().trim();
        } finally {
            connection.disconnect();
        }
    }

    private boolean hasLyrics(JSONObject record) {
        return record != null
                && (!record.optString("syncedLyrics", "").trim().isEmpty()
                || !record.optString("plainLyrics", "").trim().isEmpty());
    }

    private List<LyricsLine> parsePlainLyrics(String text) throws IOException {
        ArrayList<LyricsLine> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text))) {
            String rawLine;
            long timeMs = 0L;
            while ((rawLine = reader.readLine()) != null) {
                String clean = rawLine.trim();
                if (clean.isEmpty()) {
                    continue;
                }
                lines.add(new LyricsLine(timeMs, clean));
                timeMs += 3000L;
            }
        }
        return lines;
    }

    private String exactUrl(Track track) {
        StringBuilder url = new StringBuilder(LRCLIB_API_ROOT)
                .append("/get?track_name=").append(encode(track.title))
                .append("&artist_name=").append(encode(track.artist));
        if (!track.album.isEmpty()) {
            url.append("&album_name=").append(encode(track.album));
        }
        if (track.durationMs > 0L) {
            url.append("&duration=").append(Math.round(track.durationMs / 1000.0));
        }
        return url.toString();
    }

    private String searchUrl(Track track) {
        String query = (track.title + " " + track.artist).trim();
        return LRCLIB_API_ROOT + "/search?q=" + encode(query.isEmpty() ? track.title : query);
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<LyricsLine> cachedOnlineLyrics(String cacheKey) {
        synchronized (ONLINE_CACHE) {
            List<LyricsLine> cached = ONLINE_CACHE.get(cacheKey);
            return cached == null ? null : new ArrayList<>(cached);
        }
    }

    private List<LyricsLine> cacheOnlineLyrics(String cacheKey, List<LyricsLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<LyricsLine> snapshot = new ArrayList<>(lines);
        synchronized (ONLINE_CACHE) {
            ONLINE_CACHE.put(cacheKey, snapshot);
        }
        return new ArrayList<>(snapshot);
    }

    private String cachedNeteaseSearchTrackId(String cacheKey) {
        synchronized (NETEASE_SEARCH_CACHE) {
            return NETEASE_SEARCH_CACHE.get(cacheKey);
        }
    }

    private String cacheNeteaseSearchTrackId(String cacheKey, String providerTrackId) {
        String clean = providerTrackId == null ? "" : providerTrackId.trim();
        synchronized (NETEASE_SEARCH_CACHE) {
            NETEASE_SEARCH_CACHE.put(cacheKey, clean);
        }
        return clean;
    }

    private String onlineCacheKey(Track track) {
        return cleanKey(track.title)
                + "\n" + cleanKey(track.artist)
                + "\n" + cleanKey(track.album)
                + "\n" + Math.max(0L, Math.round(track.durationMs / 1000.0));
    }

    private String cleanKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private long parseTime(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long millis = 0L;
        if (fraction != null && !fraction.isEmpty()) {
            if (fraction.length() == 1) {
                millis = Long.parseLong(fraction) * 100L;
            } else if (fraction.length() == 2) {
                millis = Long.parseLong(fraction) * 10L;
            } else {
                millis = Long.parseLong(fraction.substring(0, 3));
            }
        }
        return minutes * 60_000L + seconds * 1000L + millis;
    }
}
