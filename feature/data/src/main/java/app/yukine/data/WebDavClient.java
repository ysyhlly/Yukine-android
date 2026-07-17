package app.yukine.data;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;
import javax.xml.parsers.DocumentBuilderFactory;

import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;
import app.yukine.model.TrackIdentity;

public final class WebDavClient {
    private static final int MAX_DIRECTORIES = 200;
    private static final int INITIAL_FLAC_PREFIX_BYTES = 64 * 1024;
    private static final int MAX_FLAC_PREFIX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ARTWORK_BYTES = 16 * 1024 * 1024;
    private static final String METADATA_CACHE_VERSION = "metadata-v3";
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".opus", ".alac"
    };
    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
    };
    private static final Pattern HTML_HREF_PATTERN = Pattern.compile(
            "(?is)<a\\s+[^>]*href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"
    );
    private final File artworkDirectory;

    public WebDavClient() {
        artworkDirectory = null;
    }

    public WebDavClient(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        artworkDirectory = appContext == null
                ? null
                : new File(appContext.getCacheDir(), "webdav-artwork");
    }

    public List<Track> listAudioTracks(RemoteSource source) {
        return listAudioTracksIncremental(source, Collections.emptyList(), "").tracks;
    }

    public IncrementalResult listAudioTracksIncremental(
            RemoteSource source,
            List<Track> cachedTracks,
            String previousManifest
    ) {
        ArrayList<Track> tracks = new ArrayList<>();
        HashSet<String> visitedDirectories = new HashSet<>();
        Map<String, Track> cachedByPath = tracksByDataPath(cachedTracks);
        Map<String, String> previousFingerprints = decodeManifest(previousManifest);
        Map<String, Track> relocatableByFingerprint = relocatableTracksByFingerprint(
                cachedByPath,
                previousFingerprints
        );
        HashSet<String> claimedRelocationFingerprints = new HashSet<>();
        LinkedHashMap<String, String> nextFingerprints = new LinkedHashMap<>();
        try {
            scanDirectory(
                    source,
                    directoryUrl(source),
                    tracks,
                    visitedDirectories,
                    cachedByPath,
                    previousFingerprints,
                    relocatableByFingerprint,
                    claimedRelocationFingerprints,
                    nextFingerprints
            );
        } catch (Exception error) {
            throw new IllegalStateException(safeMessage(error), error);
        }
        return new IncrementalResult(tracks, encodeManifest(nextFingerprints));
    }

    public String test(RemoteSource source) {
        try {
            ArrayList<Track> tracks = new ArrayList<>();
            HashSet<String> visitedDirectories = new HashSet<>();
            ScanStats stats = scanDirectory(
                    source,
                    directoryUrl(source),
                    tracks,
                    visitedDirectories,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    new HashSet<>(),
                    new LinkedHashMap<>()
            );
            return "连接成功，目录：" + stats.directoryCount + "，子目录：" + stats.childDirectoryCount + "，音频：" + tracks.size();
        } catch (Exception error) {
            return "连接失败：" + safeMessage(error);
        }
    }

    private ScanStats scanDirectory(
            RemoteSource source,
            String directoryUrl,
            ArrayList<Track> tracks,
            Set<String> visitedDirectories,
            Map<String, Track> cachedByPath,
            Map<String, String> previousFingerprints,
            Map<String, Track> relocatableByFingerprint,
            Set<String> claimedRelocationFingerprints,
            Map<String, String> nextFingerprints
    ) throws Exception {
        String normalizedDirectory = ensureTrailingSlash(directoryUrl);
        if (!visitedDirectories.add(normalizedDirectory)) {
            return new ScanStats();
        }
        if (visitedDirectories.size() > MAX_DIRECTORIES) {
            throw new IllegalStateException("目录过多，已停止扫描");
        }

        ArrayList<WebDavEntry> entries = readDirectoryEntries(source, normalizedDirectory);
        Map<String, Uri> remoteArtworkCache = new LinkedHashMap<>();
        Set<String> attemptedArtworkUrls = new HashSet<>();
        ScanStats stats = new ScanStats();
        stats.directoryCount = 1;
        for (WebDavEntry entry : entries) {
            if (entry.directory) {
                stats.childDirectoryCount++;
                ScanStats childStats = scanDirectory(
                        source,
                        entry.url,
                        tracks,
                        visitedDirectories,
                        cachedByPath,
                        previousFingerprints,
                        relocatableByFingerprint,
                        claimedRelocationFingerprints,
                        nextFingerprints
                );
                stats.add(childStats);
                continue;
            }
            String title = displayName(entry.href);
            if (!isAudio(title)) {
                continue;
            }
            WebDavEntry siblingArtwork = artworkEntryFor(entries, title);
            String dataPath = "webdav:" + source.id + ":" + entry.url;
            String fingerprint = metadataFingerprint(entry.fingerprint(), siblingArtwork);
            Uri contentUri = revisionedContentUri(entry.url, fingerprint);
            nextFingerprints.put(dataPath, fingerprint);
            Track cached = cachedByPath.get(dataPath);
            if (canReuseCached(cached != null, fingerprint, previousFingerprints.get(dataPath))
                    && hasReusableArtwork(cached)) {
                tracks.add(cached);
                continue;
            }
            Track relocated = relocatableByFingerprint.get(fingerprint);
            if (relocated != null
                    && hasReusableArtwork(relocated)
                    && isStrongRelocationFingerprint(fingerprint)
                    && claimedRelocationFingerprints.add(fingerprint)) {
                tracks.add(relocatedTrack(relocated, contentUri, dataPath));
                continue;
            }
            long trackId = stableId(source.id, entry.url);
            FlacMetadataParser.Result metadata = readFlacMetadata(source, entry.url, title);
            Uri artworkUri = storeArtwork(trackId, metadata.artwork);
            if (artworkUri == null) {
                artworkUri = loadExternalArtwork(
                        source,
                        siblingArtwork,
                        remoteArtworkCache,
                        attemptedArtworkUrls
                );
            }
            tracks.add(new Track(
                    trackId,
                    firstNonBlank(metadata.title, stripExtension(title)),
                    firstNonBlank(metadata.artist, source.name),
                    firstNonBlank(metadata.album, "WebDAV"),
                    metadata.durationMs,
                    contentUri,
                    dataPath,
                    0L,
                    artworkUri,
                    extensionCodec(title),
                    estimatedBitrateKbps(entry.contentLength, metadata.durationMs),
                    metadata.sampleRateHz,
                    metadata.bitsPerSample,
                    metadata.channelCount,
                    metadata.replayGainTrackDb,
                    metadata.replayGainAlbumDb,
                    new TrackIdentityTags(
                            metadata.recordingMusicBrainzId,
                            metadata.workMusicBrainzId,
                            metadata.isrc,
                            metadata.acoustId,
                            metadata.artistMusicBrainzIds
                    )
            ));
        }
        return stats;
    }

    private ArrayList<WebDavEntry> readDirectoryEntries(RemoteSource source, String directoryUrl) throws Exception {
        HttpURLConnection connection = open(source, directoryUrl);
        try {
            setRequestMethod(connection, "PROPFIND");
            connection.setRequestProperty("Depth", "1");
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            connection.setDoOutput(true);
            byte[] body = ("<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>"
                    + "<d:resourcetype/><d:getcontentlength/><d:getcontenttype/>"
                    + "<d:getetag/><d:getlastmodified/>"
                    + "</d:prop></d:propfind>")
                    .getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int code = connection.getResponseCode();
            byte[] responseBody = readResponseBody(connection, code >= 200 && code < 300);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("WebDAV HTTP " + code + responseMessage(connection)
                        + responseHint(connection.getContentType(), responseBody));
            }
            if (responseBody.length == 0) {
                throw new IllegalStateException("WebDAV returned an empty response. Check that the URL points to a WebDAV directory.");
            }
            if (isHtmlResponse(connection.getContentType(), responseBody)) {
                ArrayList<WebDavEntry> htmlEntries = parseHtmlEntries(source, directoryUrl, connection.getContentType(), responseBody);
                if (!htmlEntries.isEmpty()) {
                    return htmlEntries;
                }
                throw new IllegalStateException("WebDAV returned an HTML page but no audio files or folders were found. Check URL, account, port, and reverse proxy path."
                        + responseHint(connection.getContentType(), responseBody));
            }
            try (InputStream input = new ByteArrayInputStream(responseBody)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Document document = factory.newDocumentBuilder().parse(input);
                return parseEntries(source, directoryUrl, document);
            } catch (Exception error) {
                throw new IllegalStateException("WebDAV directory XML parse failed: " + cleanMessage(error)
                        + responseHint(connection.getContentType(), responseBody), error);
            }
        } finally {
            connection.disconnect();
        }
    }

    private ArrayList<WebDavEntry> parseHtmlEntries(
            RemoteSource source,
            String currentDirectoryUrl,
            String contentType,
            byte[] responseBody
    ) {
        ArrayList<WebDavEntry> entries = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        String html = decodeBody(contentType, responseBody);
        String current = trimRight(currentDirectoryUrl, "/");
        Matcher matcher = HTML_HREF_PATTERN.matcher(html);
        while (matcher.find()) {
            String rawHref = firstNonEmpty(matcher.group(1), matcher.group(2), matcher.group(3));
            String href = decodeHtmlAttribute(rawHref).trim();
            if (shouldSkipHtmlHref(href)) {
                continue;
            }
            String entryUrl = absoluteUrl(source, currentDirectoryUrl, href);
            String normalizedEntry = trimRight(entryUrl, "/");
            if (normalizedEntry.equals(current) || !seen.add(normalizedEntry)) {
                continue;
            }
            String name = displayName(href);
            boolean directory = href.endsWith("/") || entryUrl.endsWith("/");
            if (!directory && !isAudio(name) && !isImage(name)) {
                continue;
            }
            entries.add(new WebDavEntry(href, entryUrl, directory, -1L, "", ""));
        }
        return entries;
    }

    private String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return third == null ? "" : third;
    }

    private boolean shouldSkipHtmlHref(String href) {
        if (href == null || href.trim().isEmpty()) {
            return true;
        }
        String clean = href.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        return clean.equals(".")
                || clean.equals("./")
                || clean.equals("..")
                || clean.equals("../")
                || clean.startsWith("?")
                || clean.startsWith("#")
                || lower.startsWith("javascript:")
                || lower.startsWith("mailto:");
    }

    private String decodeHtmlAttribute(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private byte[] readResponseBody(HttpURLConnection connection, boolean success) throws Exception {
        InputStream stream = success ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String responseMessage(HttpURLConnection connection) {
        try {
            String message = connection.getResponseMessage();
            return message == null || message.trim().isEmpty() ? "" : " " + message.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isHtmlResponse(String contentType, byte[] body) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerContentType.contains("text/html")) {
            return true;
        }
        String preview = decodeBody(contentType, body).trim().toLowerCase(Locale.ROOT);
        return preview.startsWith("<!doctype html")
                || preview.startsWith("<html")
                || preview.contains("<head")
                || preview.contains("<body");
    }

    private String responseHint(String contentType, byte[] body) {
        String snippet = bodySnippet(contentType, body);
        return snippet.isEmpty() ? "" : " (server returned: " + snippet + ")";
    }

    private String bodySnippet(String contentType, byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String value = decodeBody(contentType, body)
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.length() > 140) {
            return value.substring(0, 140) + "...";
        }
        return value;
    }

    private String decodeBody(String contentType, byte[] body) {
        Charset charset = charsetFromContentType(contentType);
        try {
            return new String(body, charset);
        } catch (Exception ignored) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private Charset charsetFromContentType(String contentType) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            int index = lower.indexOf("charset=");
            if (index >= 0) {
                String charset = contentType.substring(index + "charset=".length()).trim();
                int semicolon = charset.indexOf(';');
                if (semicolon >= 0) {
                    charset = charset.substring(0, semicolon);
                }
                charset = charset.replace("\"", "").trim();
                if (!charset.isEmpty()) {
                    try {
                        return Charset.forName(charset);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private ArrayList<WebDavEntry> parseEntries(RemoteSource source, String currentDirectoryUrl, Document document) {
        ArrayList<WebDavEntry> entries = new ArrayList<>();
        String current = trimRight(currentDirectoryUrl, "/");
        NodeList responses = document.getElementsByTagNameNS("*", "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Node node = responses.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element response = (Element) node;
            String href = firstText(response, "href");
            if (href.isEmpty()) {
                continue;
            }
            String entryUrl = absoluteUrl(source, currentDirectoryUrl, href);
            String normalizedEntry = trimRight(entryUrl, "/");
            if (normalizedEntry.equals(current)) {
                continue;
            }
            entries.add(new WebDavEntry(
                    href,
                    entryUrl,
                    isDirectory(response, href),
                    parseLong(firstText(response, "getcontentlength")),
                    firstText(response, "getetag"),
                    firstText(response, "getlastmodified")
            ));
        }
        return entries;
    }

    private Map<String, Track> tracksByDataPath(List<Track> tracks) {
        LinkedHashMap<String, Track> values = new LinkedHashMap<>();
        if (tracks == null) return values;
        for (Track track : tracks) {
            if (track != null && track.dataPath != null && !track.dataPath.isEmpty()) {
                values.put(track.dataPath, track);
            }
        }
        return values;
    }

    private boolean canReuseCached(boolean cached, String fingerprint, String previousFingerprint) {
        return cached
                && fingerprint != null
                && !fingerprint.isEmpty()
                && fingerprint.equals(previousFingerprint);
    }

    private Map<String, Track> relocatableTracksByFingerprint(
            Map<String, Track> cachedByPath,
            Map<String, String> previousFingerprints
    ) {
        LinkedHashMap<String, Track> unique = new LinkedHashMap<>();
        HashSet<String> duplicates = new HashSet<>();
        if (cachedByPath == null || previousFingerprints == null) {
            return unique;
        }
        for (Map.Entry<String, String> entry : previousFingerprints.entrySet()) {
            String fingerprint = entry.getValue();
            Track cached = cachedByPath.get(entry.getKey());
            if (cached == null || !isStrongRelocationFingerprint(fingerprint)) {
                continue;
            }
            if (unique.containsKey(fingerprint)) {
                unique.remove(fingerprint);
                duplicates.add(fingerprint);
            } else if (!duplicates.contains(fingerprint)) {
                unique.put(fingerprint, cached);
            }
        }
        return unique;
    }

    private boolean isStrongRelocationFingerprint(String fingerprint) {
        if (fingerprint == null) {
            return false;
        }
        int etag = fingerprint.indexOf("|etag:");
        return etag >= 0 && etag + "|etag:".length() < fingerprint.length();
    }

    private Track relocatedTrack(Track cached, Uri contentUri, String dataPath) {
        return new Track(
                cached.id,
                cached.title,
                cached.artist,
                cached.album,
                cached.durationMs,
                contentUri,
                dataPath,
                cached.albumId,
                cached.albumArtUri,
                cached.codec,
                cached.bitrateKbps,
                cached.sampleRateHz,
                cached.bitsPerSample,
                cached.channelCount,
                cached.replayGainTrackDb,
                cached.replayGainAlbumDb,
                cached.identityTags
        );
    }

    /** Invalidates unchanged remote rows when the best-effort metadata parser gains new fields. */
    private String metadataFingerprint(String serverFingerprint) {
        return metadataFingerprint(serverFingerprint, null);
    }

    private String metadataFingerprint(String serverFingerprint, WebDavEntry artworkEntry) {
        if (serverFingerprint == null || serverFingerprint.isEmpty()) {
            return "";
        }
        StringBuilder fingerprint = new StringBuilder(METADATA_CACHE_VERSION)
                .append('|')
                .append(serverFingerprint);
        if (artworkEntry != null) {
            fingerprint.append("|artwork-url:")
                    .append(sha256(artworkEntry.url).substring(0, 24));
            String artworkFingerprint = artworkEntry.fingerprint();
            if (!artworkFingerprint.isEmpty()) {
                fingerprint.append("|artwork-").append(artworkFingerprint);
            }
        }
        return fingerprint.toString();
    }

    /**
     * Keep the canonical WebDAV dataPath stable while making media/audio cache keys revision-aware.
     * URL fragments are not sent in HTTP requests, so this token only participates in local identity.
     */
    private Uri revisionedContentUri(String url, String fingerprint) {
        Uri base = Uri.parse(url == null ? "" : url);
        if (fingerprint == null || fingerprint.isEmpty()) return base;
        return base.buildUpon()
                .fragment(revisionFragment(fingerprint))
                .build();
    }

    private String revisionFragment(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return "";
        return "echoRevision=" + sha256(fingerprint).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) encoded.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private Map<String, String> decodeManifest(String encoded) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.trim().isEmpty()) return values;
        try {
            JSONObject object = new JSONObject(encoded);
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                values.put(key, object.optString(key, ""));
            }
        } catch (Exception ignored) {
            values.clear();
        }
        return values;
    }

    private String encodeManifest(Map<String, String> values) {
        JSONObject object = new JSONObject();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                try {
                    object.put(entry.getKey(), entry.getValue());
                } catch (Exception ignored) {
                    // A malformed server path must not abort the library sync.
                }
            }
        }
        return object.toString();
    }

    private FlacMetadataParser.Result readFlacMetadata(RemoteSource source, String url, String title) {
        if (!title.toLowerCase(Locale.ROOT).endsWith(".flac")) {
            return new FlacMetadataParser.Result();
        }
        try {
            byte[] prefix = readPrefix(source, url, INITIAL_FLAC_PREFIX_BYTES);
            FlacMetadataParser.Result metadata = FlacMetadataParser.parse(prefix);
            int required = metadata.requiredPrefixBytes;
            if (required > prefix.length && required <= MAX_FLAC_PREFIX_BYTES) {
                byte[] expanded = readPrefix(source, url, required);
                if (expanded.length > prefix.length) {
                    metadata = FlacMetadataParser.parse(expanded);
                }
            }
            return metadata;
        } catch (Exception ignored) {
            // Remote metadata is best-effort; an audio file remains playable when its tags fail.
            return new FlacMetadataParser.Result();
        }
    }

    private byte[] readPrefix(RemoteSource source, String url, int requestedBytes) throws Exception {
        int limit = Math.max(4, Math.min(requestedBytes, MAX_FLAC_PREFIX_BYTES));
        byte[] ranged = readPrefixRequest(source, url, limit, true);
        return ranged == null ? readPrefixRequest(source, url, limit, false) : ranged;
    }

    private byte[] readPrefixRequest(
            RemoteSource source,
            String url,
            int limit,
            boolean requestRange
    ) throws Exception {
        HttpURLConnection connection = open(source, url);
        try {
            if (requestRange) {
                connection.setRequestProperty("Range", "bytes=0-" + (limit - 1));
            }
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                if (requestRange && shouldRetryPrefixWithoutRange(code)) {
                    return null;
                }
                return new byte[0];
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 64 * 1024))) {
                byte[] buffer = new byte[8192];
                while (output.size() < limit) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        int value = input.read();
                        if (value < 0) {
                            break;
                        }
                        output.write(value);
                    } else {
                        output.write(buffer, 0, read);
                    }
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private boolean shouldRetryPrefixWithoutRange(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_BAD_REQUEST
                || responseCode == HttpURLConnection.HTTP_BAD_METHOD
                || responseCode == 416
                || responseCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED;
    }

    private Uri loadExternalArtwork(
            RemoteSource source,
            WebDavEntry entry,
            Map<String, Uri> cachedByUrl,
            Set<String> attemptedUrls
    ) {
        if (artworkDirectory == null || entry == null || entry.url.isEmpty()) {
            return null;
        }
        Uri cached = cachedByUrl.get(entry.url);
        if (cached != null) {
            return cached;
        }
        if (!attemptedUrls.add(entry.url)) {
            return null;
        }
        try {
            byte[] bytes = readRemoteArtwork(source, entry);
            Uri stored = storeArtwork(stableId(source.id, entry.url), bytes);
            if (stored != null) {
                cachedByUrl.put(entry.url, stored);
            }
            return stored;
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readRemoteArtwork(RemoteSource source, WebDavEntry entry) throws Exception {
        if (entry.contentLength > MAX_ARTWORK_BYTES) {
            return new byte[0];
        }
        HttpURLConnection connection = open(source, entry.url);
        try {
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new byte[0];
            }
            long declaredLength = connection.getContentLengthLong();
            if (declaredLength > MAX_ARTWORK_BYTES) {
                return new byte[0];
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(
                         declaredLength > 0L ? (int) Math.min(declaredLength, 64 * 1024L) : 8192
                 )) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (output.size() + read > MAX_ARTWORK_BYTES) {
                        return new byte[0];
                    }
                    output.write(buffer, 0, read);
                }
                byte[] bytes = output.toByteArray();
                return isHtmlResponse(connection.getContentType(), bytes) ? new byte[0] : bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    private Uri storeArtwork(long trackId, byte[] artwork) {
        if (artworkDirectory == null || artwork == null || artwork.length == 0) {
            return null;
        }
        try {
            if (!artworkDirectory.exists() && !artworkDirectory.mkdirs()) {
                return null;
            }
            File target = new File(artworkDirectory, Long.toUnsignedString(trackId) + ".img");
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write(artwork);
            }
            return Uri.fromFile(target);
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean hasReusableArtwork(Track track) {
        if (track == null || track.albumArtUri == null) {
            return true;
        }
        if (!"file".equalsIgnoreCase(track.albumArtUri.getScheme())) {
            return true;
        }
        String path = track.albumArtUri.getPath();
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred.trim();
    }

    private String extensionCodec(String title) {
        int dot = title == null ? -1 : title.lastIndexOf('.');
        return dot < 0 || dot >= title.length() - 1
                ? ""
                : title.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private int estimatedBitrateKbps(long contentLength, long durationMs) {
        if (contentLength <= 0L || durationMs <= 0L) {
            return 0;
        }
        return (int) Math.max(1L, Math.round(contentLength * 8.0d / durationMs));
    }

    private long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private HttpURLConnection open(RemoteSource source, String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        if (source.hasAuth()) {
            String auth = source.username + ":" + source.password;
            String encoded = Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }
        return connection;
    }

    private void setRequestMethod(HttpURLConnection connection, String method) throws Exception {
        try {
            connection.setRequestMethod(method);
        } catch (ProtocolException error) {
            Field methodField = HttpURLConnection.class.getDeclaredField("method");
            methodField.setAccessible(true);
            methodField.set(connection, method);
        }
    }

    private String directoryUrl(RemoteSource source) {
        String base = trimRight(source.baseUrl, "/");
        String root = trimLeft(source.rootPath, "/");
        if (root.isEmpty()) {
            return ensureTrailingSlash(base);
        }
        return ensureTrailingSlash(base + "/" + root);
    }

    private String absoluteUrl(RemoteSource source, String currentDirectoryUrl, String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("/")) {
            try {
                URL base = new URL(source.baseUrl);
                return base.getProtocol() + "://" + base.getAuthority() + href;
            } catch (Exception ignored) {
                return href;
            }
        }
        try {
            return new URL(new URL(ensureTrailingSlash(currentDirectoryUrl)), href).toString();
        } catch (Exception ignored) {
            return trimRight(currentDirectoryUrl, "/") + "/" + href;
        }
    }

    private String firstText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        String value = nodes.item(0).getTextContent();
        return value == null ? "" : value.trim();
    }

    private boolean isDirectory(Element response, String href) {
        if (href.endsWith("/")) {
            return true;
        }
        return response.getElementsByTagNameNS("*", "collection").getLength() > 0;
    }

    private boolean isAudio(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isImage(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String extension : IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private WebDavEntry artworkEntryFor(List<WebDavEntry> entries, String audioTitle) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        String audioBase = stripExtension(audioTitle == null ? "" : audioTitle)
                .trim()
                .toLowerCase(Locale.ROOT);
        WebDavEntry firstImage = null;
        WebDavEntry preferred = null;
        int preferredRank = Integer.MAX_VALUE;
        int imageCount = 0;
        for (WebDavEntry entry : entries) {
            if (entry == null || entry.directory) {
                continue;
            }
            String imageName = displayName(entry.href);
            if (!isImage(imageName)) {
                continue;
            }
            imageCount++;
            if (firstImage == null) {
                firstImage = entry;
            }
            String imageBase = stripExtension(imageName).trim().toLowerCase(Locale.ROOT);
            if (!audioBase.isEmpty() && audioBase.equals(imageBase)) {
                return entry;
            }
            int rank = preferredArtworkRank(imageBase);
            if (rank < preferredRank) {
                preferred = entry;
                preferredRank = rank;
            }
        }
        if (preferred != null) {
            return preferred;
        }
        return imageCount == 1 ? firstImage : null;
    }

    private int preferredArtworkRank(String baseName) {
        if ("cover".equals(baseName) || "\u5c01\u9762".equals(baseName)) return 0;
        if ("folder".equals(baseName)) return 1;
        if ("front".equals(baseName)) return 2;
        if ("album".equals(baseName) || "albumart".equals(baseName) || "album_art".equals(baseName)) return 3;
        return Integer.MAX_VALUE;
    }

    private String displayName(String href) {
        String clean = href;
        int queryIndex = clean.indexOf('?');
        if (queryIndex >= 0) {
            clean = clean.substring(0, queryIndex);
        }
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        return urlDecode(name == null || name.isEmpty() ? clean : name);
    }

    private String urlDecode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private long stableId(long sourceId, String href) {
        long hash = 1125899906842597L;
        String value = sourceId + ":" + href;
        for (int i = 0; i < value.length(); i++) {
            hash = 31L * hash + value.charAt(i);
        }
        return TrackIdentity.stableNegative(-Math.abs(hash));
    }

    private String trimRight(String value, String suffix) {
        String current = value == null ? "" : value.trim();
        while (current.endsWith(suffix)) {
            current = current.substring(0, current.length() - suffix.length());
        }
        return current;
    }

    private String trimLeft(String value, String prefix) {
        String current = value == null ? "" : value.trim();
        while (current.startsWith(prefix)) {
            current = current.substring(prefix.length());
        }
        return current;
    }

    private String ensureTrailingSlash(String value) {
        String current = value == null ? "" : value.trim();
        return current.endsWith("/") ? current : current + "/";
    }

    private String safeMessage(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                String host = cleanMessage(current);
                return host.isEmpty()
                        ? "无法解析 WebDAV 主机，请检查地址"
                        : "无法解析 WebDAV 主机：" + host;
            }
            if (current instanceof SocketTimeoutException) {
                return "连接 WebDAV 超时，请检查地址、端口、网络或端口转发";
            }
            if (current instanceof ConnectException) {
                String target = connectTarget(cleanMessage(current));
                if (!target.isEmpty()) {
                    return "无法连接到 " + target + "：端口拒绝连接，请检查 WebDAV 服务、端口、协议和端口转发";
                }
                return "无法连接到 WebDAV 服务：端口拒绝连接，请检查端口、协议和端口转发";
            }
            if (current instanceof SSLException) {
                return "HTTPS 连接 WebDAV 失败，请检查证书；如果服务器未启用 HTTPS，请改用 http:// 地址";
            }
            current = current.getCause();
        }
        String message = cleanMessage(error);
        return message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private String cleanMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? "" : message.trim();
    }

    private String connectTarget(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        String target = message.trim();
        String lower = target.toLowerCase(Locale.ROOT);
        String prefix = "failed to connect to ";
        if (lower.startsWith(prefix)) {
            target = target.substring(prefix.length()).trim();
        }
        while (target.startsWith("/")) {
            target = target.substring(1);
        }
        int space = target.indexOf(' ');
        if (space > 0) {
            target = target.substring(0, space);
        }
        return target.trim();
    }

    private static final class WebDavEntry {
        final String href;
        final String url;
        final boolean directory;
        final long contentLength;
        final String etag;
        final String lastModified;

        WebDavEntry(
                String href,
                String url,
                boolean directory,
                long contentLength,
                String etag,
                String lastModified
        ) {
            this.href = href;
            this.url = url;
            this.directory = directory;
            this.contentLength = contentLength;
            this.etag = etag == null ? "" : etag.trim();
            this.lastModified = lastModified == null ? "" : lastModified.trim();
        }

        String fingerprint() {
            if (!etag.isEmpty()) return "etag:" + etag;
            if (!lastModified.isEmpty()) return "modified:" + lastModified + ":" + contentLength;
            return contentLength >= 0L ? "length:" + contentLength : "";
        }
    }

    public static final class IncrementalResult {
        public final List<Track> tracks;
        public final String manifest;

        IncrementalResult(List<Track> tracks, String manifest) {
            this.tracks = tracks == null ? Collections.emptyList() : tracks;
            this.manifest = manifest == null ? "" : manifest;
        }
    }

    private static final class ScanStats {
        int directoryCount;
        int childDirectoryCount;

        void add(ScanStats other) {
            directoryCount += other.directoryCount;
            childDirectoryCount += other.childDirectoryCount;
        }
    }
}
