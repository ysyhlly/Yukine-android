package app.echo.next.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

import app.echo.next.model.LyricsLine;
import app.echo.next.model.Track;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LyricsRepository {
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final String LRCLIB_API_ROOT = "https://lrclib.net/api";
    private static final String NETEASE_LYRIC_URL = "https://music.163.com/api/song/lyric";
    private static final String NETEASE_SEARCH_URL = "https://music.163.com/api/cloudsearch/pc";
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
        if (!track.dataPath.isEmpty()) {
            File audioFile = new File(track.dataPath);
            File lyricsFile = findSidecarLyrics(audioFile);
            if (lyricsFile != null && lyricsFile.isFile()) {
                try {
                    List<LyricsLine> localLines = parseLrc(lyricsFile);
                    if (!localLines.isEmpty()) {
                        return localLines;
                    }
                } catch (IOException ignored) {
                    // Fall back to online lyrics when enabled.
                }
            }
        }
        List<LyricsLine> neteaseLines = fetchNeteaseLyrics(neteaseProviderTrackId);
        if (neteaseLines.isEmpty()) {
            neteaseLines = fetchNeteaseStreamingLyrics(track);
        }
        if (!neteaseLines.isEmpty()) {
            return neteaseLines;
        }
        if (!onlineEnabled) {
            return Collections.emptyList();
        }
        neteaseLines = fetchNeteaseSearchLyrics(track);
        if (!neteaseLines.isEmpty()) {
            return neteaseLines;
        }
        return fetchOnlineLyrics(track);
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
            return cached;
        }
        try {
            JSONObject record = firstLrclibRecord(track);
            if (record == null) {
                return Collections.emptyList();
            }
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

    private List<LyricsLine> fetchNeteaseStreamingLyrics(Track track) {
        String providerTrackId = neteaseProviderTrackId(track);
        return fetchNeteaseLyrics(providerTrackId);
    }

    private List<LyricsLine> fetchNeteaseLyrics(String providerTrackId) {
        providerTrackId = neteaseSongId(providerTrackId);
        if (providerTrackId.isEmpty()) {
            return Collections.emptyList();
        }
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
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 ECHO-NEXT-Android");
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
        connection.setRequestProperty("User-Agent", "ECHO-NEXT-Android");
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
