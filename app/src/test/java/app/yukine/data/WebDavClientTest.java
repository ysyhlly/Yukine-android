package app.yukine.data;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import android.net.Uri;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WebDavClientTest {
    @Test
    public void directoryUrlJoinsBaseAndRootWithSingleSlash() throws Exception {
        RemoteSource source = new RemoteSource(
                1L,
                RemoteSource.TYPE_WEBDAV,
                "nas",
                "https://192.168.3.52:5005/",
                "",
                "",
                "/vlomes1/",
                "",
                0L
        );
        WebDavClient client = new WebDavClient();
        Method directoryUrl = WebDavClient.class.getDeclaredMethod("directoryUrl", RemoteSource.class);
        directoryUrl.setAccessible(true);

        assertEquals(
                "https://192.168.3.52:5005/vlomes1/",
                directoryUrl.invoke(client, source)
        );
    }

    @Test
    public void safeMessageFormatsAndroidConnectExceptionTarget() throws Exception {
        WebDavClient client = new WebDavClient();
        Method safeMessage = WebDavClient.class.getDeclaredMethod("safeMessage", Exception.class);
        safeMessage.setAccessible(true);

        String message = (String) safeMessage.invoke(
                client,
                new ConnectException("Failed to connect to /198.44.178.36:5006")
        );

        assertTrue(message.contains("198.44.178.36:5006"));
        assertTrue(message.contains("端口拒绝连接"));
        assertFalse(message.contains("/198.44.178.36"));
    }

    @Test
    public void parseHtmlEntriesAcceptsIndexDirectoryListing() throws Exception {
        WebDavClient client = new WebDavClient();
        Method parseHtmlEntries = WebDavClient.class.getDeclaredMethod(
                "parseHtmlEntries",
                RemoteSource.class,
                String.class,
                String.class,
                byte[].class
        );
        parseHtmlEntries.setAccessible(true);
        RemoteSource source = new RemoteSource(
                7L,
                RemoteSource.TYPE_WEBDAV,
                "nas",
                "http://127.0.0.1:5005",
                "",
                "",
                "/music",
                "",
                0L
        );
        String html = "<html><body><h1>Index of /music</h1>"
                + "<a href=\"?C=N;O=D\">Name</a>"
                + "<a href=\"../\">Parent Directory</a>"
                + "<a href=\"9%E3%82%92%E7%9C%BA%E3%82%81%E3%81%9F%E9%AD%9A%E9%81%94.flac\">9を眺めた魚達.flac</a>"
                + "<a href=\"cover.jpg\">cover.jpg</a>"
                + "<a href=\"sub/\">sub/</a>"
                + "</body></html>";

        List<?> entries = (List<?>) parseHtmlEntries.invoke(
                client,
                source,
                "http://127.0.0.1:5005/music/",
                "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(3, entries.size());
        assertEquals("9%E3%82%92%E7%9C%BA%E3%82%81%E3%81%9F%E9%AD%9A%E9%81%94.flac", field(entries.get(0), "href"));
        assertEquals("http://127.0.0.1:5005/music/9%E3%82%92%E7%9C%BA%E3%82%81%E3%81%9F%E9%AD%9A%E9%81%94.flac", field(entries.get(0), "url"));
        assertEquals(false, field(entries.get(0), "directory"));
        assertEquals("cover.jpg", field(entries.get(1), "href"));
        assertEquals("http://127.0.0.1:5005/music/cover.jpg", field(entries.get(1), "url"));
        assertEquals(false, field(entries.get(1), "directory"));
        assertEquals("sub/", field(entries.get(2), "href"));
        assertEquals("http://127.0.0.1:5005/music/sub/", field(entries.get(2), "url"));
        assertEquals(true, field(entries.get(2), "directory"));
    }

    @Test
    public void artworkEntryPrefersAudioNameThenConventionalCover() throws Exception {
        WebDavClient client = new WebDavClient();
        Method parseHtmlEntries = WebDavClient.class.getDeclaredMethod(
                "parseHtmlEntries",
                RemoteSource.class,
                String.class,
                String.class,
                byte[].class
        );
        parseHtmlEntries.setAccessible(true);
        Method artworkEntryFor = WebDavClient.class.getDeclaredMethod(
                "artworkEntryFor",
                List.class,
                String.class
        );
        artworkEntryFor.setAccessible(true);
        RemoteSource source = source("http://127.0.0.1:5005");
        String html = "<a href=\"song.flac\">song.flac</a>"
                + "<a href=\"song.png\">song.png</a>"
                + "<a href=\"other.mp3\">other.mp3</a>"
                + "<a href=\"cover.jpg\">cover.jpg</a>"
                + "<a href=\"artist.webp\">artist.webp</a>";

        List<?> entries = (List<?>) parseHtmlEntries.invoke(
                client,
                source,
                "http://127.0.0.1:5005/music/",
                "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8)
        );

        Object songArtwork = artworkEntryFor.invoke(client, entries, "song.flac");
        Object otherArtwork = artworkEntryFor.invoke(client, entries, "other.mp3");
        assertEquals("song.png", field(songArtwork, "href"));
        assertEquals("cover.jpg", field(otherArtwork, "href"));
    }

    @Test
    public void remoteArtworkDownloadReadsImageBytes() throws Exception {
        byte[] expected = new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2, 3, (byte) 0xff, (byte) 0xd9};
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5_000);
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (Socket socket = server.accept();
                     BufferedReader input = new BufferedReader(new InputStreamReader(
                             socket.getInputStream(),
                             StandardCharsets.ISO_8859_1
                     ));
                     OutputStream output = socket.getOutputStream()) {
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) {
                        // Consume the complete HTTP request before writing the response.
                    }
                    String headers = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: image/jpeg\r\n"
                            + "Content-Length: " + expected.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
                    output.write(expected);
                    output.flush();
                } catch (Throwable error) {
                    serverFailure.set(error);
                }
            }, "webdav-artwork-test-server");
            responder.start();

            String url = "http://127.0.0.1:" + server.getLocalPort() + "/cover.jpg";
            Class<?> entryType = Class.forName("app.yukine.data.WebDavClient$WebDavEntry");
            Constructor<?> constructor = entryType.getDeclaredConstructor(
                    String.class,
                    String.class,
                    boolean.class,
                    long.class,
                    String.class,
                    String.class
            );
            constructor.setAccessible(true);
            Object entry = constructor.newInstance("cover.jpg", url, false, (long) expected.length, "", "");
            Method readRemoteArtwork = WebDavClient.class.getDeclaredMethod(
                    "readRemoteArtwork",
                    RemoteSource.class,
                    entryType
            );
            readRemoteArtwork.setAccessible(true);

            byte[] actual;
            try {
                actual = (byte[]) readRemoteArtwork.invoke(
                        new WebDavClient(),
                        source("http://127.0.0.1:" + server.getLocalPort()),
                        entry
                );
            } finally {
                responder.join(5_000);
            }
            assertArrayEquals(expected, actual);
            assertFalse("Test HTTP server did not stop", responder.isAlive());
            if (serverFailure.get() != null) {
                throw new AssertionError(serverFailure.get());
            }
        }
    }

    @Test
    public void incrementalReuseRequiresMatchingNonEmptyFingerprint() throws Exception {
        WebDavClient client = new WebDavClient();
        Method canReuse = WebDavClient.class.getDeclaredMethod(
                "canReuseCached",
                boolean.class,
                String.class,
                String.class
        );
        canReuse.setAccessible(true);

        assertEquals(true, canReuse.invoke(client, true, "etag:abc", "etag:abc"));
        assertEquals(false, canReuse.invoke(client, true, "etag:new", "etag:old"));
        assertEquals(false, canReuse.invoke(client, true, "", ""));
        assertEquals(false, canReuse.invoke(client, false, "etag:abc", "etag:abc"));

        Method metadataFingerprint = WebDavClient.class.getDeclaredMethod("metadataFingerprint", String.class);
        metadataFingerprint.setAccessible(true);
        assertEquals("metadata-v3|etag:abc", metadataFingerprint.invoke(client, "etag:abc"));
        assertEquals("", metadataFingerprint.invoke(client, ""));

        Method retryWithoutRange = WebDavClient.class.getDeclaredMethod(
                "shouldRetryPrefixWithoutRange",
                int.class
        );
        retryWithoutRange.setAccessible(true);
        assertEquals(true, retryWithoutRange.invoke(client, 416));
        assertEquals(true, retryWithoutRange.invoke(client, 405));
        assertEquals(false, retryWithoutRange.invoke(client, 401));

        Method revisionFragment = WebDavClient.class.getDeclaredMethod("revisionFragment", String.class);
        revisionFragment.setAccessible(true);
        String first = (String) revisionFragment.invoke(client, "metadata-v2|etag:first");
        String second = (String) revisionFragment.invoke(client, "metadata-v2|etag:second");
        assertTrue(first.startsWith("echoRevision="));
        assertEquals(37, first.length());
        assertFalse(first.equals(second));
    }

    @Test
    public void relocationFingerprintRequiresUniqueEtagAndKeepsTrackIdentity() throws Exception {
        WebDavClient client = new WebDavClient();
        Method candidates = WebDavClient.class.getDeclaredMethod(
                "relocatableTracksByFingerprint",
                Map.class,
                Map.class
        );
        candidates.setAccessible(true);
        Track original = track(41L, "webdav:7:https://dav/old.flac");
        Map<String, Track> cached = new LinkedHashMap<>();
        cached.put(original.dataPath, original);
        Map<String, String> manifest = new LinkedHashMap<>();
        manifest.put(original.dataPath, "metadata-v3|etag:content-41");

        @SuppressWarnings("unchecked")
        Map<String, Track> unique = (Map<String, Track>) candidates.invoke(client, cached, manifest);

        assertEquals(original.id, unique.get("metadata-v3|etag:content-41").id);

        Track duplicate = track(42L, "webdav:7:https://dav/copy.flac");
        cached.put(duplicate.dataPath, duplicate);
        manifest.put(duplicate.dataPath, "metadata-v3|etag:content-41");
        @SuppressWarnings("unchecked")
        Map<String, Track> ambiguous = (Map<String, Track>) candidates.invoke(client, cached, manifest);
        assertTrue(ambiguous.isEmpty());

        Method relocatedTrack = WebDavClient.class.getDeclaredMethod(
                "relocatedTrack",
                Track.class,
                Uri.class,
                String.class
        );
        relocatedTrack.setAccessible(true);
        Track moved = (Track) relocatedTrack.invoke(
                client,
                original,
                Uri.parse("https://dav/new.flac"),
                "webdav:7:https://dav/new.flac"
        );
        assertEquals(original.id, moved.id);
        assertEquals(original.title, moved.title);
        assertEquals("webdav:7:https://dav/new.flac", moved.dataPath);
    }

    private Track track(long id, String dataPath) {
        return new Track(
                id,
                "Same recording",
                "Artist",
                "Album",
                180_000L,
                Uri.parse(dataPath.substring(dataPath.indexOf("https://"))),
                dataPath
        );
    }

    private RemoteSource source(String baseUrl) {
        return new RemoteSource(
                7L,
                RemoteSource.TYPE_WEBDAV,
                "nas",
                baseUrl,
                "",
                "",
                "/music",
                "",
                0L
        );
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
