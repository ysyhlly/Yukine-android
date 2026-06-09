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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.echo.next.model.LyricsLine;
import app.echo.next.model.Track;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LyricsRepository {
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final String LRCLIB_API_ROOT = "https://lrclib.net/api";

    public List<LyricsLine> loadForTrack(Track track) {
        return loadForTrack(track, false);
    }

    public List<LyricsLine> loadForTrack(Track track, boolean onlineEnabled) {
        if (track == null || track.dataPath.isEmpty()) {
            return Collections.emptyList();
        }
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
        try {
            JSONObject record = firstLrclibRecord(track);
            if (record == null) {
                return Collections.emptyList();
            }
            String syncedLyrics = record.optString("syncedLyrics", "").trim();
            if (!syncedLyrics.isEmpty()) {
                return parseLrcText(syncedLyrics);
            }
            String plainLyrics = record.optString("plainLyrics", "").trim();
            if (!plainLyrics.isEmpty()) {
                return parsePlainLyrics(plainLyrics);
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private JSONObject firstLrclibRecord(Track track) throws Exception {
        JSONObject exact = requestJsonObject(exactUrl(track));
        if (hasLyrics(exact)) {
            return exact;
        }
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
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
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
