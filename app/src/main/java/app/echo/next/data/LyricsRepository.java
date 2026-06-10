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
import java.util.List;
import java.util.Map;
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
        providerTrackId = providerTrackId == null ? "" : providerTrackId.trim();
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

    private JSONObject requestNeteaseLyrics(String providerTrackId) throws Exception {
        String url = NETEASE_LYRIC_URL
                + "?id=" + encode(providerTrackId)
                + "&lv=1&kv=1&tv=-1";
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
        return cutAtAny(rest.substring(separator + 1), ':', '|', '?', '#').trim();
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
