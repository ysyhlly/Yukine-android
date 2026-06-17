package app.yukine.data;

import android.net.Uri;
import android.util.Base64;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import app.yukine.model.RemoteSource;
import app.yukine.model.Track;

public final class WebDavClient {
    private static final int MAX_DIRECTORIES = 200;
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".opus", ".alac"
    };
    private static final Pattern HTML_HREF_PATTERN = Pattern.compile(
            "(?is)<a\\s+[^>]*href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"
    );
    private static final TrustManager[] TRUST_ALL_WEB_DAV_CERTS = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };
    private static final HostnameVerifier TRUST_ALL_WEB_DAV_HOSTS = (hostname, session) -> true;

    public List<Track> listAudioTracks(RemoteSource source) {
        ArrayList<Track> tracks = new ArrayList<>();
        HashSet<String> visitedDirectories = new HashSet<>();
        try {
            scanDirectory(source, directoryUrl(source), tracks, visitedDirectories);
        } catch (Exception error) {
            throw new IllegalStateException(safeMessage(error), error);
        }
        return tracks;
    }

    public String test(RemoteSource source) {
        try {
            ArrayList<Track> tracks = new ArrayList<>();
            HashSet<String> visitedDirectories = new HashSet<>();
            ScanStats stats = scanDirectory(source, directoryUrl(source), tracks, visitedDirectories);
            return "连接成功，目录：" + stats.directoryCount + "，子目录：" + stats.childDirectoryCount + "，音频：" + tracks.size();
        } catch (Exception error) {
            return "连接失败：" + safeMessage(error);
        }
    }

    private ScanStats scanDirectory(
            RemoteSource source,
            String directoryUrl,
            ArrayList<Track> tracks,
            Set<String> visitedDirectories
    ) throws Exception {
        String normalizedDirectory = ensureTrailingSlash(directoryUrl);
        if (!visitedDirectories.add(normalizedDirectory)) {
            return new ScanStats();
        }
        if (visitedDirectories.size() > MAX_DIRECTORIES) {
            throw new IllegalStateException("目录过多，已停止扫描");
        }

        ArrayList<WebDavEntry> entries = readDirectoryEntries(source, normalizedDirectory);
        ScanStats stats = new ScanStats();
        stats.directoryCount = 1;
        for (WebDavEntry entry : entries) {
            if (entry.directory) {
                stats.childDirectoryCount++;
                ScanStats childStats = scanDirectory(source, entry.url, tracks, visitedDirectories);
                stats.add(childStats);
                continue;
            }
            String title = displayName(entry.href);
            if (!isAudio(title)) {
                continue;
            }
            tracks.add(new Track(
                    stableId(source.id, entry.url),
                    stripExtension(title),
                    source.name,
                    "WebDAV",
                    0L,
                    Uri.parse(entry.url),
                    "webdav:" + source.id + ":" + entry.url,
                    0L,
                    null
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
            byte[] body = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>"
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
            if (!directory && !isAudio(name)) {
                continue;
            }
            entries.add(new WebDavEntry(href, entryUrl, directory));
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
            entries.add(new WebDavEntry(href, entryUrl, isDirectory(response, href)));
        }
        return entries;
    }

    private HttpURLConnection open(RemoteSource source, String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (connection instanceof HttpsURLConnection) {
            configureWebDavTls((HttpsURLConnection) connection);
        }
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        if (source.hasAuth()) {
            String auth = source.username + ":" + source.password;
            String encoded = Base64.encodeToString(auth.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }
        return connection;
    }

    private void configureWebDavTls(HttpsURLConnection connection) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, TRUST_ALL_WEB_DAV_CERTS, new SecureRandom());
        connection.setSSLSocketFactory(context.getSocketFactory());
        connection.setHostnameVerifier(TRUST_ALL_WEB_DAV_HOSTS);
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
        return -Math.abs(hash);
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

        WebDavEntry(String href, String url, boolean directory) {
            this.href = href;
            this.url = url;
            this.directory = directory;
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
